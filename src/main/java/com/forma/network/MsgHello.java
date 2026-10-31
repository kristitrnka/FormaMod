package com.forma.network;

import com.forma.Forma;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Sent by the server on login so the client knows editing is supported. */
public class MsgHello implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<MsgHello, IMessage> {
        @Override
        public IMessage onMessage(MsgHello msg, MessageContext ctx) {
            Forma.proxy.onServerHello();
            return null;
        }
    }
}
