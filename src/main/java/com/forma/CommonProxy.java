package com.forma;

import com.forma.network.MsgHistorySync;

/** Server-side proxy. Client-only behaviour lives in {@link com.forma.client.ClientProxy}. */
public class CommonProxy {
    public void preInit() {}

    public void init() {}

    public void onServerHello() {}

    public void onHistorySync(MsgHistorySync msg) {}
}
