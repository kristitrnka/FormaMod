package com.forma.network;

import com.forma.server.EditOperations;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Undo / redo request. */
public class MsgHistoryAction implements IMessage {
    public static final byte UNDO = 0;
    public static final byte REDO = 1;

    public byte action;

    public MsgHistoryAction() {}

    public MsgHistoryAction(byte action) {
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
    }

    public static class Handler implements IMessageHandler<MsgHistoryAction, IMessage> {
        @Override
        public IMessage onMessage(MsgHistoryAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.action == UNDO) EditOperations.undo(player);
                else EditOperations.redo(player);
            });
            return null;
        }
    }
}
