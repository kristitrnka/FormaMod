package com.forma.network;

import com.forma.common.Mask;
import com.forma.server.EditOperations;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client asks the server to change blocks. One message type for every edit; {@link #op} selects which fields matter. */
public class MsgEdit implements IMessage {
    public static final byte OP_BOX = 0;
    public static final byte OP_SHAPE = 1;
    public static final byte OP_COPY = 2;
    public static final byte OP_CUT = 3;
    public static final byte OP_PASTE = 4;
    public static final byte OP_TRANSFORM = 5;
    public static final byte OP_BRUSH = 6;
    public static final byte OP_MOVE = 7;
    public static final byte OP_LIST = 8;
    public static final byte OP_FLOOD = 9;
    public static final byte OP_EXTRUDE = 10;

    /** Max positions in one OP_LIST message (client->server packets are capped at 32 KB). */
    public static final int LIST_CHUNK = 3000;

    public static final byte SHAPE_SPHERE = 0;
    public static final byte SHAPE_CUBE = 1;
    public static final byte SHAPE_CYLINDER = 2;

    public static final byte BRUSH_SCULPT = 0;
    public static final byte BRUSH_SMOOTH = 1;
    public static final byte BRUSH_FLATTEN = 2;
    public static final byte BRUSH_NOISE = 3;
    public static final byte BRUSH_PAINT = 4;
    public static final byte BRUSH_MELT = 5;
    public static final byte BRUSH_SCATTER = 6;

    public byte op;
    public BlockPos a = BlockPos.ORIGIN;
    public BlockPos b = BlockPos.ORIGIN;
    public int stateId;
    // shapes
    public byte shape;
    public int radius;
    public boolean hollow;
    // paste / transform
    public int rotation;
    public boolean flipX, flipZ, keepExisting, pasteAir;
    // brushes
    public byte brush;
    public int strength, mode, param, strokeId, seq;
    /** OP_MOVE: new min corner. */
    public BlockPos dest = BlockPos.ORIGIN;
    /** Scatter brush: block states to pick from. */
    public int[] palette = new int[0];
    /** OP_LIST: packed BlockPos longs. */
    public long[] positions = new long[0];

    public Mask mask;

    public MsgEdit() {}

    public static MsgEdit box(BlockPos a, BlockPos b, int stateId) {
        MsgEdit m = new MsgEdit();
        m.op = OP_BOX;
        m.a = a;
        m.b = b;
        m.stateId = stateId;
        return m;
    }

    public static MsgEdit shape(BlockPos center, byte shape, int radius, boolean hollow, int stateId) {
        MsgEdit m = new MsgEdit();
        m.op = OP_SHAPE;
        m.a = m.b = center;
        m.shape = shape;
        m.radius = radius;
        m.hollow = hollow;
        m.stateId = stateId;
        return m;
    }

    public static MsgEdit region(byte op, BlockPos a, BlockPos b) {
        MsgEdit m = new MsgEdit();
        m.op = op;
        m.a = a;
        m.b = b;
        return m;
    }

    public static MsgEdit paste(BlockPos anchor, int rotation, boolean flipX, boolean flipZ, boolean keepExisting, boolean pasteAir) {
        MsgEdit m = new MsgEdit();
        m.op = OP_PASTE;
        m.a = m.b = anchor;
        m.rotation = rotation;
        m.flipX = flipX;
        m.flipZ = flipZ;
        m.keepExisting = keepExisting;
        m.pasteAir = pasteAir;
        return m;
    }

    public static MsgEdit transform(BlockPos a, BlockPos b, int rotation, boolean flipX, boolean flipZ) {
        MsgEdit m = region(OP_TRANSFORM, a, b);
        m.rotation = rotation;
        m.flipX = flipX;
        m.flipZ = flipZ;
        return m;
    }

    public static MsgEdit brush(byte brush, BlockPos center, int radius, int strength, int mode, int param, int stateId, int strokeId, int seq) {
        MsgEdit m = new MsgEdit();
        m.op = OP_BRUSH;
        m.a = m.b = center;
        m.brush = brush;
        m.radius = radius;
        m.strength = strength;
        m.mode = mode;
        m.param = param;
        m.stateId = stateId;
        m.strokeId = strokeId;
        m.seq = seq;
        return m;
    }

    public static MsgEdit move(BlockPos min, BlockPos max, BlockPos dest, int rotation, boolean flipX, boolean flipZ) {
        MsgEdit m = transform(min, max, rotation, flipX, flipZ);
        m.op = OP_MOVE;
        m.dest = dest;
        return m;
    }

    public static MsgEdit list(long[] positions, int stateId, int strokeId) {
        MsgEdit m = new MsgEdit();
        m.op = OP_LIST;
        m.positions = positions;
        m.stateId = stateId;
        m.strokeId = strokeId;
        return m;
    }

    public static MsgEdit flood(BlockPos start, int radius, int stateId) {
        MsgEdit m = new MsgEdit();
        m.op = OP_FLOOD;
        m.a = m.b = start;
        m.radius = radius;
        m.stateId = stateId;
        return m;
    }

    /** mode 0 = push out one layer, 1 = pull in one layer. */
    public static MsgEdit extrude(BlockPos start, int side, int radius, int mode) {
        MsgEdit m = new MsgEdit();
        m.op = OP_EXTRUDE;
        m.a = m.b = start;
        m.shape = (byte) side;
        m.radius = radius;
        m.mode = mode;
        return m;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        op = buf.readByte();
        a = BlockPos.fromLong(buf.readLong());
        b = BlockPos.fromLong(buf.readLong());
        stateId = buf.readInt();
        shape = buf.readByte();
        radius = buf.readInt();
        hollow = buf.readBoolean();
        rotation = buf.readByte();
        int flags = buf.readByte();
        flipX = (flags & 1) != 0;
        flipZ = (flags & 2) != 0;
        keepExisting = (flags & 4) != 0;
        pasteAir = (flags & 8) != 0;
        brush = buf.readByte();
        strength = buf.readInt();
        mode = buf.readInt();
        param = buf.readInt();
        strokeId = buf.readInt();
        seq = buf.readInt();
        dest = BlockPos.fromLong(buf.readLong());
        palette = new int[Math.min(64, buf.readShort())];
        for (int i = 0; i < palette.length; i++) palette[i] = buf.readInt();
        positions = new long[Math.min(LIST_CHUNK, buf.readShort())];
        for (int i = 0; i < positions.length; i++) positions[i] = buf.readLong();
        mask = buf.readBoolean() ? Mask.read(buf) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(op);
        buf.writeLong(a.toLong());
        buf.writeLong(b.toLong());
        buf.writeInt(stateId);
        buf.writeByte(shape);
        buf.writeInt(radius);
        buf.writeBoolean(hollow);
        buf.writeByte(rotation);
        buf.writeByte((flipX ? 1 : 0) | (flipZ ? 2 : 0) | (keepExisting ? 4 : 0) | (pasteAir ? 8 : 0));
        buf.writeByte(brush);
        buf.writeInt(strength);
        buf.writeInt(mode);
        buf.writeInt(param);
        buf.writeInt(strokeId);
        buf.writeInt(seq);
        buf.writeLong(dest.toLong());
        buf.writeShort(palette.length);
        for (int p : palette) buf.writeInt(p);
        buf.writeShort(positions.length);
        for (long p : positions) buf.writeLong(p);
        buf.writeBoolean(mask != null);
        if (mask != null) mask.write(buf);
    }

    public static class Handler implements IMessageHandler<MsgEdit, IMessage> {
        @Override
        public IMessage onMessage(MsgEdit msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> EditOperations.apply(player, msg));
            return null;
        }
    }
}
