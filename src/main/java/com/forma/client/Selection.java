package com.forma.client;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/** A box selection between two corner blocks (inclusive). */
public class Selection {
    public BlockPos a;
    public BlockPos b;

    public boolean isValid() {
        return a != null && b != null;
    }

    public void clear() {
        a = b = null;
    }

    public BlockPos min() {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public BlockPos max() {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public int sizeX() {
        return Math.abs(a.getX() - b.getX()) + 1;
    }

    public int sizeY() {
        return Math.abs(a.getY() - b.getY()) + 1;
    }

    public int sizeZ() {
        return Math.abs(a.getZ() - b.getZ()) + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public AxisAlignedBB box() {
        BlockPos mn = min(), mx = max();
        return new AxisAlignedBB(mn.getX(), mn.getY(), mn.getZ(), mx.getX() + 1, mx.getY() + 1, mx.getZ() + 1);
    }
}
