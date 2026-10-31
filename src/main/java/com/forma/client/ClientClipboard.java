package com.forma.client;

import com.forma.common.ClipboardData;
import com.forma.common.Transform;
import net.minecraft.util.math.BlockPos;

/** The client's copy of the clipboard, used for the paste preview. The server keeps the authoritative copy. */
public final class ClientClipboard {
    public ClipboardData data;
    /** Shown in the Clipboard panel: "Copied selection" or the schematic file name. */
    public String name = "";
    public int rotation;
    public boolean flipX, flipZ;
    public boolean keepExisting, pasteAir;

    private final Ghost ghost = new Ghost();

    public boolean isEmpty() {
        return data == null;
    }

    public void set(ClipboardData d, String name) {
        data = d;
        this.name = name;
        rotation = 0;
        flipX = flipZ = false;
        changed();
    }

    public void clear() {
        data = null;
    }

    public void rotate() {
        rotation = (rotation + 1) % 4;
        changed();
    }

    public void flipX() {
        flipX = !flipX;
        changed();
    }

    public void flipZ() {
        flipZ = !flipZ;
        changed();
    }

    private void changed() {
        if (data != null) ghost.set(data, transform());
    }

    public Transform transform() {
        return new Transform(rotation, flipX, flipZ);
    }

    public BlockPos origin(BlockPos anchor) {
        Transform t = transform();
        return Transform.origin(anchor, t.outX(data.sx, data.sz), t.outZ(data.sx, data.sz));
    }

    /** Draws the ghost at the given paste anchor. Must be called between WorldDraw.begin/end. */
    public void render(BlockPos anchor) {
        if (data != null) ghost.render(origin(anchor), 0xFF5AA8FF);
    }
}
