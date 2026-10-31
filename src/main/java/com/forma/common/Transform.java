package com.forma.common;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;

/** Flip then rotate (clockwise seen from above) a clipboard around its vertical axis. Shared by client preview and server paste. */
public final class Transform {
    public final int rotation;
    public final boolean flipX, flipZ;

    public Transform(int rotation, boolean flipX, boolean flipZ) {
        this.rotation = ((rotation % 4) + 4) % 4;
        this.flipX = flipX;
        this.flipZ = flipZ;
    }

    public int outX(int sx, int sz) {
        return rotation % 2 == 0 ? sx : sz;
    }

    public int outZ(int sx, int sz) {
        return rotation % 2 == 0 ? sz : sx;
    }

    /** Maps local (x, z) in a sx*sz footprint to the transformed footprint; result in out[0], out[1]. */
    public void map(int x, int z, int sx, int sz, int[] out) {
        if (flipX) x = sx - 1 - x;
        if (flipZ) z = sz - 1 - z;
        int w = sx, d = sz;
        for (int i = 0; i < rotation; i++) {
            int nx = d - 1 - z;
            z = x;
            x = nx;
            int t = w;
            w = d;
            d = t;
        }
        out[0] = x;
        out[1] = z;
    }

    public IBlockState apply(IBlockState s) {
        if (flipX) s = s.withMirror(Mirror.FRONT_BACK);
        if (flipZ) s = s.withMirror(Mirror.LEFT_RIGHT);
        if (rotation != 0) s = s.withRotation(Rotation.values()[rotation]);
        return s;
    }

    /** Pastes sit with their bottom-centre on the anchor. */
    public static BlockPos origin(BlockPos anchor, int outX, int outZ) {
        return anchor.add(-(outX / 2), 0, -(outZ / 2));
    }
}
