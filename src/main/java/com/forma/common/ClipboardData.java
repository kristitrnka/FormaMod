package com.forma.common;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/** A copied block region: state ids in x-fastest order, plus tile entity NBT (server only). */
public final class ClipboardData {
    public final int sx, sy, sz;
    public final int[] states;
    public final Map<Integer, NBTTagCompound> tiles = new HashMap<>();
    public int nonAir;

    public ClipboardData(int sx, int sy, int sz) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.states = new int[sx * sy * sz];
    }

    public int index(int x, int y, int z) {
        return (y * sz + z) * sx + x;
    }

    public long volume() {
        return (long) sx * sy * sz;
    }

    public static ClipboardData capture(World w, BlockPos min, BlockPos max, boolean withTiles) {
        ClipboardData d = new ClipboardData(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = 0; y < d.sy; y++)
            for (int z = 0; z < d.sz; z++)
                for (int x = 0; x < d.sx; x++) {
                    p.setPos(min.getX() + x, min.getY() + y, min.getZ() + z);
                    IBlockState s = w.getBlockState(p);
                    int id = Block.getStateId(s);
                    int i = d.index(x, y, z);
                    d.states[i] = id;
                    if (id != 0) d.nonAir++;
                    if (withTiles && s.getBlock().hasTileEntity(s)) {
                        TileEntity te = w.getTileEntity(p);
                        if (te != null) d.tiles.put(i, te.writeToNBT(new NBTTagCompound()));
                    }
                }
        return d;
    }
}
