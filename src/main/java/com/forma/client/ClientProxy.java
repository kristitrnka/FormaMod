package com.forma.client;

import com.forma.CommonProxy;
import com.forma.network.MsgHistorySync;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        EditorController.INSTANCE.init();
        MinecraftForge.EVENT_BUS.register(EditorController.INSTANCE);
    }

    @Override
    public void onServerHello() {
        Minecraft.getMinecraft().addScheduledTask(() -> EditorController.INSTANCE.serverSupported = true);
    }

    @Override
    public void onHistorySync(MsgHistorySync msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            EditorController c = EditorController.INSTANCE;
            c.history = msg.entries;
            c.historyCursor = msg.cursor;
            if (!msg.message.isEmpty()) c.status(msg.message);
        });
    }
}
