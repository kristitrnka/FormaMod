package com.forma.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Destination mask: an edit only touches positions where this mask passes. */
public final class Mask {
    public static final byte BLOCK = 0;
    public static final byte Y_RANGE = 1;
    public static final byte IN_SELECTION = 2;
    public static final byte ABOVE = 3;
    public static final byte BELOW = 4;
    public static final byte NEAR = 5;
    public static final byte SOLID = 6;
    public static final byte AIR = 7;
    public static final String[] NAMES = {"Block =", "Y =", "In Selection", "Above =", "Below =", "Near =", "Solid", "Air"};

    public static boolean usesBlock(byte type) {
        return type == BLOCK || type == ABOVE || type == BELOW || type == NEAR;
    }

    public static final class Cond {
        public byte type;
        public boolean not;
        public int block;
        public int minY = 0, maxY = 255;
        public long boxMin, boxMax;

        public Cond() {}

        public Cond(byte type, int block) {
            this.type = type;
            this.block = block;
        }
    }

    /** false: all conditions must pass. true: any condition may pass. */
    public boolean any;
    public final List<Cond> conds = new ArrayList<>();

    public boolean isEmpty() {
        return conds.isEmpty();
    }

    public boolean test(World w, BlockPos p) {
        if (conds.isEmpty()) return true;
        for (Cond c : conds) {
            boolean r = eval(c, w, p) != c.not;
            if (any && r) return true;
            if (!any && !r) return false;
        }
        return !any;
    }

    private static boolean eval(Cond c, World w, BlockPos p) {
        switch (c.type) {
            case BLOCK:
                return id(w, p) == c.block;
            case Y_RANGE:
                return p.getY() >= c.minY && p.getY() <= c.maxY;
            case IN_SELECTION: {
                BlockPos a = BlockPos.fromLong(c.boxMin), b = BlockPos.fromLong(c.boxMax);
                return p.getX() >= a.getX() && p.getX() <= b.getX() && p.getY() >= a.getY() && p.getY() <= b.getY()
                        && p.getZ() >= a.getZ() && p.getZ() <= b.getZ();
            }
            case ABOVE:
                return id(w, p.down()) == c.block;
            case BELOW:
                return id(w, p.up()) == c.block;
            case NEAR:
                for (EnumFacing f : EnumFacing.VALUES) if (id(w, p.offset(f)) == c.block) return true;
                return false;
            case SOLID:
                return w.getBlockState(p).getMaterial().isSolid();
            case AIR:
                return w.isAirBlock(p);
            default:
                return true;
        }
    }

    private static int id(World w, BlockPos p) {
        return Block.getIdFromBlock(w.getBlockState(p).getBlock());
    }

    public void write(ByteBuf buf) {
        buf.writeBoolean(any);
        buf.writeShort(conds.size());
        for (Cond c : conds) {
            buf.writeByte(c.type);
            buf.writeBoolean(c.not);
            buf.writeInt(c.block);
            buf.writeShort(c.minY);
            buf.writeShort(c.maxY);
            buf.writeLong(c.boxMin);
            buf.writeLong(c.boxMax);
        }
    }

    public static Mask read(ByteBuf buf) {
        Mask m = new Mask();
        m.any = buf.readBoolean();
        int n = Math.min(64, buf.readShort());
        for (int i = 0; i < n; i++) {
            Cond c = new Cond();
            c.type = buf.readByte();
            c.not = buf.readBoolean();
            c.block = buf.readInt();
            c.minY = buf.readShort();
            c.maxY = buf.readShort();
            c.boxMin = buf.readLong();
            c.boxMax = buf.readLong();
            m.conds.add(c);
        }
        return m;
    }
}
