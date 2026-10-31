package com.forma.network;

import com.forma.server.EditOperations;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** One chunk of a clipboard upload (schematics loaded on the client become the server-side clipboard). */
public class MsgUpload implements IMessage {
    public static final int CHUNK = 30_000;

    public int transferId;
    public int index;
    public int total;
    public byte[] data = new byte[0];

    public MsgUpload() {}

    public MsgUpload(int transferId, int index, int total, byte[] data) {
        this.transferId = transferId;
        this.index = index;
        this.total = total;
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        transferId = buf.readInt();
        index = buf.readInt();
        total = buf.readInt();
        int n = Math.min(CHUNK, Math.max(0, buf.readInt()));
        data = new byte[n];
        buf.readBytes(data);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(transferId);
        buf.writeInt(index);
        buf.writeInt(total);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    public static class Handler implements IMessageHandler<MsgUpload, IMessage> {
        @Override
        public IMessage onMessage(MsgUpload msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> EditOperations.receiveUpload(player, msg));
            return null;
        }
    }
}
