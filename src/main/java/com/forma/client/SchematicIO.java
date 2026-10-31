package com.forma.client;

import com.forma.common.ClipboardData;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads and writes schematics in the folder {@code .minecraft/schematics}.
 * .schematic (MCEdit / WorldEdit 6 / Schematica, incl. SchematicaMapping for modded ids) is fully supported;
 * .schem (Sponge v1-v3) loads blocks whose names exist in 1.12, others become air.
 */
public final class SchematicIO {
    private SchematicIO() {}

    public static File folder() {
        File dir = new File(Minecraft.getMinecraft().gameDir, "schematics");
        if (!dir.exists() && !dir.mkdirs()) return dir;
        return dir;
    }

    public static List<File> list() {
        File[] files = folder().listFiles((d, n) -> {
            String l = n.toLowerCase(Locale.ROOT);
            return l.endsWith(".schematic") || l.endsWith(".schem");
        });
        List<File> out = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    public static ClipboardData load(File file) throws IOException {
        NBTTagCompound root;
        try (InputStream in = new FileInputStream(file)) {
            root = CompressedStreamTools.readCompressed(in);
        }
        if (root.hasKey("Schematic", 10)) root = root.getCompoundTag("Schematic"); // Sponge v3
        if (root.hasKey("Palette", 10) || root.hasKey("Blocks", 10)) return loadSponge(root);
        return loadLegacy(root);
    }

    // ------------------------------------------------------------ .schematic

    @SuppressWarnings("deprecation")
    private static ClipboardData loadLegacy(NBTTagCompound root) throws IOException {
        int w = root.getShort("Width"), h = root.getShort("Height"), l = root.getShort("Length");
        if (w <= 0 || h <= 0 || l <= 0) throw new IOException("Not a schematic");
        byte[] blocks = root.getByteArray("Blocks");
        byte[] meta = root.getByteArray("Data");
        byte[] add = root.getByteArray("AddBlocks");
        int volume = w * h * l;
        if (blocks.length < volume) throw new IOException("Truncated block data");

        // Schematica stores its own id->name table so modded blocks survive between worlds.
        Map<Integer, Block> mapping = new HashMap<>();
        if (root.hasKey("SchematicaMapping", 10)) {
            NBTTagCompound m = root.getCompoundTag("SchematicaMapping");
            for (String name : m.getKeySet()) {
                Block b = Block.REGISTRY.getObject(new ResourceLocation(name));
                mapping.put((int) m.getShort(name), b);
            }
        }

        ClipboardData d = new ClipboardData(w, h, l);
        for (int i = 0; i < volume; i++) {
            int id = blocks[i] & 0xFF;
            if (add.length > i >> 1) id |= ((i & 1) == 0 ? (add[i >> 1] & 0x0F) << 8 : (add[i >> 1] & 0xF0) << 4);
            if (id == 0) continue;
            Block block = mapping.isEmpty() ? Block.getBlockById(id) : mapping.getOrDefault(id, Blocks.AIR);
            if (block == null || block == Blocks.AIR) continue;
            int m = i < meta.length ? meta[i] & 0x0F : 0;
            IBlockState state;
            try {
                state = block.getStateFromMeta(m);
            } catch (RuntimeException e) {
                state = block.getDefaultState();
            }
            d.states[i] = Block.getStateId(state);
            d.nonAir++;
        }
        readTiles(d, root.getTagList("TileEntities", 10), 0, 0, 0);
        return d;
    }

    /** Writes a .schematic with a SchematicaMapping table (portable for modded blocks). */
    public static void save(File file, ClipboardData d) throws IOException {
        int volume = d.states.length;
        byte[] blocks = new byte[volume], meta = new byte[volume], add = new byte[(volume >> 1) + 1];
        boolean needsAdd = false;
        NBTTagCompound mapping = new NBTTagCompound();
        for (int i = 0; i < volume; i++) {
            IBlockState s = Block.getStateById(d.states[i]);
            Block b = s.getBlock();
            int id = Block.getIdFromBlock(b);
            blocks[i] = (byte) id;
            meta[i] = (byte) b.getMetaFromState(s);
            if (id > 255) {
                needsAdd = true;
                add[i >> 1] |= (i & 1) == 0 ? (id >> 8) & 0x0F : ((id >> 8) & 0x0F) << 4;
            }
            if (b.getRegistryName() != null) mapping.setShort(b.getRegistryName().toString(), (short) id);
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setShort("Width", (short) d.sx);
        root.setShort("Height", (short) d.sy);
        root.setShort("Length", (short) d.sz);
        root.setString("Materials", "Alpha");
        root.setByteArray("Blocks", blocks);
        root.setByteArray("Data", meta);
        if (needsAdd) root.setByteArray("AddBlocks", add);
        root.setTag("SchematicaMapping", mapping);
        NBTTagList tiles = new NBTTagList();
        for (Map.Entry<Integer, NBTTagCompound> e : d.tiles.entrySet()) {
            int i = e.getKey();
            NBTTagCompound t = e.getValue().copy();
            t.setInteger("x", i % d.sx);
            t.setInteger("y", i / (d.sx * d.sz));
            t.setInteger("z", (i / d.sx) % d.sz);
            tiles.appendTag(t);
        }
        root.setTag("TileEntities", tiles);
        root.setTag("Entities", new NBTTagList());
        try (OutputStream out = new FileOutputStream(file)) {
            CompressedStreamTools.writeCompressed(root, out);
        }
    }

    // ------------------------------------------------------------ .schem (Sponge)

    private static ClipboardData loadSponge(NBTTagCompound root) throws IOException {
        int w = root.getShort("Width") & 0xFFFF, h = root.getShort("Height") & 0xFFFF, l = root.getShort("Length") & 0xFFFF;
        if (w <= 0 || h <= 0 || l <= 0) throw new IOException("Not a schematic");
        NBTTagCompound blocksTag = root.hasKey("Blocks", 10) ? root.getCompoundTag("Blocks") : root;
        NBTTagCompound paletteTag = blocksTag.getCompoundTag("Palette");
        byte[] data = blocksTag.hasKey("Data", 7) ? blocksTag.getByteArray("Data") : blocksTag.getByteArray("BlockData");

        Map<Integer, Integer> palette = new HashMap<>();
        int unknown = 0;
        for (String key : paletteTag.getKeySet()) {
            IBlockState s = parseState(key);
            if (s == null) {
                s = Blocks.AIR.getDefaultState();
                unknown++;
            }
            palette.put(paletteTag.getInteger(key), Block.getStateId(s));
        }
        ClipboardData d = new ClipboardData(w, h, l);
        int pos = 0;
        for (int i = 0; i < d.states.length && pos < data.length; i++) {
            int value = 0, shift = 0;
            byte b;
            do {
                b = data[pos++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && pos < data.length);
            int id = palette.getOrDefault(value, 0);
            d.states[i] = id;
            if (id != 0) d.nonAir++;
        }
        String tileKey = blocksTag.hasKey("BlockEntities", 9) ? "BlockEntities" : "TileEntities";
        NBTTagList tiles = blocksTag.getTagList(tileKey, 10);
        for (int i = 0; i < tiles.tagCount(); i++) {
            NBTTagCompound t = tiles.getCompoundTagAt(i);
            int[] p = t.getIntArray("Pos");
            if (p.length != 3) continue;
            NBTTagCompound copy = t.hasKey("Data", 10) ? t.getCompoundTag("Data").copy() : t.copy();
            if (t.hasKey("Id", 8)) copy.setString("id", t.getString("Id"));
            copy.setInteger("x", p[0]);
            copy.setInteger("y", p[1]);
            copy.setInteger("z", p[2]);
            putTile(d, copy, 0, 0, 0);
        }
        if (unknown > 0) EditorController.INSTANCE.status(unknown + " block types from a newer version were skipped");
        return d;
    }

    /** "minecraft:oak_stairs[facing=east,half=top]" -> 1.12 state, or null if the block doesn't exist here. */
    private static IBlockState parseState(String s) {
        int bracket = s.indexOf('[');
        String name = bracket < 0 ? s : s.substring(0, bracket);
        ResourceLocation id = new ResourceLocation(name);
        if (!Block.REGISTRY.containsKey(id)) return null;
        Block block = Block.REGISTRY.getObject(id);
        IBlockState state = block.getDefaultState();
        if (bracket >= 0 && s.endsWith("]")) {
            for (String kv : s.substring(bracket + 1, s.length() - 1).split(",")) {
                String[] parts = kv.split("=", 2);
                if (parts.length == 2) state = withProperty(state, parts[0], parts[1]);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> IBlockState withProperty(IBlockState state, String name, String value) {
        for (IProperty<?> raw : state.getPropertyKeys()) {
            if (!raw.getName().equals(name)) continue;
            @SuppressWarnings("unchecked")
            IProperty<T> prop = (IProperty<T>) raw;
            com.google.common.base.Optional<T> parsed = prop.parseValue(value);
            if (parsed.isPresent()) return state.withProperty(prop, parsed.get());
        }
        return state;
    }

    // ------------------------------------------------------------ tile entities

    private static void readTiles(ClipboardData d, NBTTagList list, int ox, int oy, int oz) {
        for (int i = 0; i < list.tagCount(); i++) putTile(d, list.getCompoundTagAt(i).copy(), ox, oy, oz);
    }

    private static void putTile(ClipboardData d, NBTTagCompound t, int ox, int oy, int oz) {
        int x = t.getInteger("x") - ox, y = t.getInteger("y") - oy, z = t.getInteger("z") - oz;
        if (x < 0 || y < 0 || z < 0 || x >= d.sx || y >= d.sy || z >= d.sz) return;
        d.tiles.put(d.index(x, y, z), t);
    }
}
