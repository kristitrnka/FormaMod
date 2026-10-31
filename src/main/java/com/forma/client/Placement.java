package com.forma.client;

import com.forma.common.ClipboardData;
import com.forma.common.Transform;
import com.forma.network.MsgEdit;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/**
 * A floating block region waiting to be applied (Blender-style): drag the gizmo arrows to move it,
 * R / X / Z to rotate and flip, Enter to apply, Esc to cancel.
 */
public final class Placement {
    public enum Kind {
        /** Pastes the server clipboard (a copy or an uploaded schematic). */
        PASTE,
        /** Moves the blocks of {@link #sourceMin}..{@link #sourceMax}. */
        MOVE
    }

    public final Kind kind;
    public final ClipboardData data;
    public final String name;
    public final BlockPos sourceMin, sourceMax;
    public BlockPos origin;
    public int rotation;
    public boolean flipX, flipZ;
    public boolean keepExisting, pasteAir;
    private final Ghost ghost = new Ghost();

    private Placement(Kind kind, ClipboardData data, String name, BlockPos origin, BlockPos sourceMin, BlockPos sourceMax) {
        this.kind = kind;
        this.data = data;
        this.name = name;
        this.origin = origin;
        this.sourceMin = sourceMin;
        this.sourceMax = sourceMax;
        ghost.set(data, transform());
    }

    public static Placement paste(ClipboardData data, String name, BlockPos origin) {
        return new Placement(Kind.PASTE, data, name, origin, null, null);
    }

    public static Placement move(ClipboardData data, BlockPos min, BlockPos max) {
        return new Placement(Kind.MOVE, data, "Move selection", min, min, max);
    }

    public Transform transform() {
        return new Transform(rotation, flipX, flipZ);
    }

    public int outX() {
        return transform().outX(data.sx, data.sz);
    }

    public int outZ() {
        return transform().outZ(data.sx, data.sz);
    }

    public AxisAlignedBB box() {
        return new AxisAlignedBB(origin, origin.add(outX(), data.sy, outZ()));
    }

    /** Rotates around the footprint centre so the ghost doesn't jump. */
    public void rotate() {
        int cx = origin.getX() + outX() / 2, cz = origin.getZ() + outZ() / 2;
        rotation = (rotation + 1) % 4;
        origin = new BlockPos(cx - outX() / 2, origin.getY(), cz - outZ() / 2);
        ghost.set(data, transform());
    }

    public void flipX() {
        flipX = !flipX;
        ghost.set(data, transform());
    }

    public void flipZ() {
        flipZ = !flipZ;
        ghost.set(data, transform());
    }

    public void render() {
        if (kind == Kind.MOVE) {
            WorldDraw.outline(new AxisAlignedBB(sourceMin, sourceMax.add(1, 1, 1)), 0x80FFFFFF);
        }
        ghost.render(origin, kind == Kind.MOVE ? 0xFFFFD84A : 0xFF5AA8FF);
    }

    /** Sends the edit; the caller drops the placement afterwards. */
    public void commit(EditorController c) {
        if (kind == Kind.MOVE) {
            c.sendEdit(MsgEdit.move(sourceMin, sourceMax, origin, rotation, flipX, flipZ));
            c.selection.a = origin;
            c.selection.b = origin.add(outX() - 1, data.sy - 1, outZ() - 1);
        } else {
            BlockPos anchor = origin.add(outX() / 2, 0, outZ() / 2);
            c.sendEdit(MsgEdit.paste(anchor, rotation, flipX, flipZ, keepExisting, pasteAir));
        }
    }
}
