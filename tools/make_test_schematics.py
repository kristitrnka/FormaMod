"""Generates a few 1.12 .schematic files for testing Forma's schematic loading.

Usage: python3 tools/make_test_schematics.py [output_dir]
"""
import gzip
import math
import os
import random
import struct
import sys

# ---------------------------------------------------------------- minimal NBT writer

def _name(n):
    b = n.encode("utf-8")
    return struct.pack(">H", len(b)) + b


class Compound(dict):
    pass


class List(list):
    def __init__(self, tag_type, items=()):
        super().__init__(items)
        self.tag_type = tag_type


class Byte(int):
    pass


class Short(int):
    pass


class ByteArray(bytes):
    pass


def _type(v):
    if isinstance(v, Compound):
        return 10
    if isinstance(v, List):
        return 9
    if isinstance(v, ByteArray):
        return 7
    if isinstance(v, Byte):
        return 1
    if isinstance(v, Short):
        return 2
    if isinstance(v, int):
        return 3
    if isinstance(v, str):
        return 8
    raise TypeError(type(v))


def _payload(v):
    t = _type(v)
    if t == 1:
        return struct.pack(">b", v)
    if t == 2:
        return struct.pack(">h", v)
    if t == 3:
        return struct.pack(">i", v)
    if t == 7:
        return struct.pack(">i", len(v)) + bytes(v)
    if t == 8:
        return _name(v)
    if t == 9:
        return struct.pack(">bi", v.tag_type, len(v)) + b"".join(_payload(i) for i in v)
    out = b""
    for k, item in v.items():
        out += struct.pack(">b", _type(item)) + _name(k) + _payload(item)
    return out + b"\x00"


def write_nbt(path, root_name, root):
    with gzip.open(path, "wb") as f:
        f.write(b"\x0a" + _name(root_name) + _payload(root))


# ---------------------------------------------------------------- schematic builder

class Schem:
    def __init__(self, w, h, l):
        self.w, self.h, self.l = w, h, l
        self.blocks = bytearray(w * h * l)
        self.data = bytearray(w * h * l)
        self.tiles = []

    def set(self, x, y, z, block, meta=0):
        if 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.l:
            i = (y * self.l + z) * self.w + x
            self.blocks[i] = block
            self.data[i] = meta

    def get(self, x, y, z):
        if 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.l:
            return self.blocks[(y * self.l + z) * self.w + x]
        return 0

    def box(self, x0, y0, z0, x1, y1, z1, block, meta=0):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, block, meta)

    def chest(self, x, y, z, facing_meta, items):
        self.set(x, y, z, CHEST, facing_meta)
        self.tiles.append(Compound(
            id="minecraft:chest", x=x, y=y, z=z,
            Items=List(10, [Compound(Slot=Byte(s), id=i, Count=Byte(c), Damage=Short(0)) for s, (i, c) in enumerate(items)]),
        ))

    def save(self, path):
        root = Compound(
            Width=Short(self.w), Height=Short(self.h), Length=Short(self.l),
            Materials="Alpha",
            Blocks=ByteArray(self.blocks), Data=ByteArray(self.data),
            TileEntities=List(10, self.tiles), Entities=List(10),
        )
        write_nbt(path, "Schematic", root)
        n = sum(1 for b in self.blocks if b)
        print(f"{os.path.basename(path)}: {self.w}x{self.h}x{self.l}, {n} blocks")


# 1.12 numeric ids
STONE, GRASS, DIRT, COBBLE, PLANKS, LOG, LEAVES, GLASS = 1, 2, 3, 4, 5, 17, 18, 20
WOOL, BRICK, BOOKSHELF, TORCH, OAK_STAIRS, CHEST, FENCE = 35, 45, 47, 50, 53, 54, 85
GLOWSTONE, STONEBRICK, GLASS_PANE, WATER, RED_FLOWER, LANTERN_SEA = 89, 98, 102, 9, 38, 169


def cottage():
    s = Schem(11, 10, 9)
    s.box(0, 0, 0, 10, 0, 8, COBBLE)                       # floor
    s.box(0, 1, 0, 10, 4, 8, PLANKS, 1)                    # spruce walls
    s.box(1, 1, 1, 9, 4, 7, 0)                             # hollow inside
    for x, z in ((0, 0), (10, 0), (0, 8), (10, 8)):
        s.box(x, 1, z, x, 4, z, LOG)                       # oak log corners
    for x in (2, 3, 7, 8):                                 # windows front/back
        s.box(x, 2, 0, x, 3, 0, GLASS_PANE)
        s.box(x, 2, 8, x, 3, 8, GLASS_PANE)
    s.box(0, 2, 3, 0, 3, 5, GLASS_PANE)
    s.box(5, 1, 0, 5, 2, 0, 0)                             # doorway
    # gable roof along x: stairs facing south (meta 2) and north (meta 3)
    for step in range(5):
        y = 5 + step
        s.box(0, y, step - 0 if step else 0, 10, y, step, OAK_STAIRS, 2)
        s.box(0, y, 8 - step, 10, y, 8 - step, OAK_STAIRS, 3)
        if 0 < step < 4:
            s.box(0, y, step + 1, 0, y, 7 - step, PLANKS, 1)
            s.box(10, y, step + 1, 10, y, 7 - step, PLANKS, 1)
    s.box(0, 9, 4, 10, 9, 4, PLANKS, 5)                    # ridge
    s.box(1, 1, 7, 3, 2, 7, BOOKSHELF)
    s.set(9, 3, 4, TORCH, 2)                               # wall torch
    s.set(1, 3, 4, TORCH, 1)
    s.chest(8, 1, 7, 2, [("minecraft:diamond", 5), ("minecraft:bread", 16), ("minecraft:oak_sapling", 4)])
    s.set(5, 0, 4, GLOWSTONE)
    for x in (2, 8):
        s.set(x, 1, -1, 0)
    return s


def tower():
    r, h = 4, 28
    s = Schem(2 * r + 1, h + 4, 2 * r + 1)
    c = r
    for y in range(h):
        for x in range(s.w):
            for z in range(s.l):
                d = math.hypot(x - c, z - c)
                if d <= r + 0.5:
                    if y == 0 or (y % 7 == 0 and d < r - 0.5):
                        s.set(x, y, z, STONEBRICK)        # floors
                    elif d >= r - 0.5:
                        window = y % 7 in (3, 4) and (x == c or z == c)
                        s.set(x, y, z, GLASS if window else STONEBRICK, 0 if (x + y + z) % 5 else 2)  # some cracked
    for x in range(s.w):                                   # crenellations
        for z in range(s.l):
            d = math.hypot(x - c, z - c)
            if r - 0.5 <= d <= r + 0.5 and (x + z) % 2 == 0:
                s.set(x, h, z, STONEBRICK)
    s.set(c, h, c, GLOWSTONE)
    s.box(c, h + 1, c, c, h + 3, c, FENCE)                 # flag pole
    s.box(c + 1, h + 2, c, c + 3, h + 3, c, WOOL, 14)      # red flag
    for y in range(1, h):                                  # spiral ladder of slabs/stairs inside
        a = y * 0.9
        x, z = c + round(math.cos(a) * 2), c + round(math.sin(a) * 2)
        s.set(x, y, z, STONEBRICK)
    return s


def floating_island():
    rnd = random.Random(7)
    w, h, l = 41, 34, 41
    s = Schem(w, h, l)
    cx, cz, top = w // 2, l // 2, 16
    for x in range(w):
        for z in range(l):
            d = math.hypot(x - cx, z - cz) / 19.0
            if d >= 1:
                continue
            wobble = math.sin(x * 0.4) * math.cos(z * 0.35) * 1.5
            depth = int((1 - d) ** 1.4 * 15 + wobble + rnd.random() * 2)
            surf = top + int((1 - d) * 3 + wobble * 0.5)
            for y in range(surf - depth, surf + 1):
                if y < 0:
                    continue
                if y == surf:
                    s.set(x, y, z, GRASS)
                elif y > surf - 3:
                    s.set(x, y, z, DIRT)
                else:
                    s.set(x, y, z, STONE if rnd.random() > 0.15 else COBBLE)
            if d < 0.85 and rnd.random() < 0.03:
                s.set(x, surf + 1, z, RED_FLOWER, rnd.choice((0, 3, 4, 8)))
    # pond
    for x in range(cx + 3, cx + 9):
        for z in range(cz - 3, cz + 3):
            if math.hypot(x - cx - 6, z - cz) < 3:
                y = max(yy for yy in range(h) if s.get(x, yy, z) == GRASS) if any(s.get(x, yy, z) == GRASS for yy in range(h)) else None
                if y is not None:
                    s.set(x, y, z, WATER)
    # trees
    for tx, tz in ((cx - 7, cz - 5), (cx - 3, cz + 8), (cx + 6, cz - 9), (cx - 10, cz + 3)):
        ys = [yy for yy in range(h) if s.get(tx, yy, tz) == GRASS]
        if not ys:
            continue
        base = ys[-1] + 1
        trunk = rnd.randint(4, 6)
        s.box(tx, base, tz, tx, base + trunk, tz, LOG)
        for x in range(tx - 2, tx + 3):
            for z in range(tz - 2, tz + 3):
                for y in range(base + trunk - 2, base + trunk + 2):
                    if math.hypot(x - tx, z - tz, (y - base - trunk) * 1.3) <= 2.6 and s.get(x, y, z) == 0:
                        s.set(x, y, z, LEAVES, 4)          # no-decay oak leaves
    return s


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "schematics"
    os.makedirs(out, exist_ok=True)
    cottage().save(os.path.join(out, "test_cottage.schematic"))
    tower().save(os.path.join(out, "test_tower.schematic"))
    floating_island().save(os.path.join(out, "test_floating_island.schematic"))
