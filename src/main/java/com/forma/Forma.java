package com.forma;

import com.forma.network.FormaNetwork;
import com.forma.server.ServerEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Forma.MODID, name = Forma.NAME, version = Forma.VERSION,
        acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*")
public class Forma {
    public static final String MODID = "forma";
    public static final String NAME = "Forma";
    public static final String VERSION = "0.3.0";
    public static final Logger LOG = LogManager.getLogger(NAME);

    @SidedProxy(clientSide = "com.forma.client.ClientProxy", serverSide = "com.forma.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FormaNetwork.init();
        MinecraftForge.EVENT_BUS.register(new ServerEvents());
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
