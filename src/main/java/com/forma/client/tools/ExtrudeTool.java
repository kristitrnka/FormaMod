package com.forma.client.tools;

import com.forma.client.EditorController;
import com.forma.client.WorldDraw;
import com.forma.client.ui.IconAtlas;
import com.forma.client.ui.Ui;
import com.forma.network.MsgEdit;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;

/** Click a face: the connected face of same blocks is pushed out (or pulled in) by one layer. */
public class ExtrudeTool extends Tool {
    private static final String[] MODES = {"Push Out", "Pull In"};
    private int mode;
    private int radius = 32;

    public ExtrudeTool() {
        super("Extrude", IconAtlas.EXTRUDE, "Extrude Face");
    }

    @Override
    public void onPress(EditorController c, RayTraceResult hit) {
        if (hit != null) c.sendEdit(MsgEdit.extrude(hit.getBlockPos(), hit.sideHit.getIndex(), radius, mode));
    }

    @Override
    public void renderWorld(EditorController c, RayTraceResult hover, float partialTicks) {
        if (hover == null) return;
        AxisAlignedBB b = WorldDraw.block(hover.getBlockPos());
        double o = 0.5;
        AxisAlignedBB face = b.offset(hover.sideHit.getXOffset() * o, hover.sideHit.getYOffset() * o, hover.sideHit.getZOffset() * o)
                .intersect(b.grow(0.01));
        WorldDraw.fill(face, mode == 0 ? 0x6060FF60 : 0x60FF6060);
        WorldDraw.outline(b, 0xC0FFFFFF);
    }

    @Override
    public void drawOptions(Ui ui, EditorController c) {
        mode = ui.combo(MODES, mode, ui.contentWidth());
        ui.text("Max radius");
        radius = ui.slider("extrude.radius", radius, 1, 128, ui.contentWidth());
        ui.text("Click repeatedly to extrude", Ui.TEXT_DIM);
        ui.text("more layers.", Ui.TEXT_DIM);
    }
}
