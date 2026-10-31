package com.forma.network;

import com.forma.Forma;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: the player's edit history plus an optional status line. */
public class MsgHistorySync implements IMessage {
    public List<String> entries = new ArrayList<>();
    public int cursor;
    public String message = "";

    public MsgHistorySync() {}

    public MsgHistorySync(List<String> entries, int cursor, String message) {
        this.entries = entries;
        this.cursor = cursor;
        this.message = message == null ? "" : message;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = buf.readInt();
        entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) entries.add(ByteBufUtils.readUTF8String(buf));
        cursor = buf.readInt();
        message = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entries.size());
        for (String s : entries) ByteBufUtils.writeUTF8String(buf, s);
        buf.writeInt(cursor);
        ByteBufUtils.writeUTF8String(buf, message);
    }

    public static class Handler implements IMessageHandler<MsgHistorySync, IMessage> {
        @Override
        public IMessage onMessage(MsgHistorySync msg, MessageContext ctx) {
            Forma.proxy.onHistorySync(msg);
            return null;
        }
    }
}
