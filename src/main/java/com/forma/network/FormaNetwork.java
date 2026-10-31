package com.forma.network;

import com.forma.Forma;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class FormaNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Forma.MODID);

    private FormaNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(MsgEdit.Handler.class, MsgEdit.class, 0, Side.SERVER);
        CHANNEL.registerMessage(MsgHistoryAction.Handler.class, MsgHistoryAction.class, 1, Side.SERVER);
        CHANNEL.registerMessage(MsgHello.Handler.class, MsgHello.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(MsgHistorySync.Handler.class, MsgHistorySync.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(MsgUpload.Handler.class, MsgUpload.class, 4, Side.SERVER);
    }
}
