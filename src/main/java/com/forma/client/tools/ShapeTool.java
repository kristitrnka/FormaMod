package com.forma.client.tools;

import com.forma.client.EditorController;
import com.forma.client.WorldDraw;
import com.forma.client.ui.IconAtlas;
import com.forma.client.ui.Ui;
import com.forma.network.MsgEdit;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

/** Places a sphere / cube / cylinder of the active block centred on the clicked block. */
public class ShapeTool extends Tool {
    private static final String[] SHAPES = {"Sphere", "Cube", "Cylinder"};
    private int shape;
    private int radius = 5;
    private boolean hollow;
    private boolean onSurface = true;

    public ShapeTool() {
        super("Create Shape", IconAtlas.SHAPE, "Place Shape");
    }

    private BlockPos center(RayTraceResult hit) {
        BlockPos p = hit.getBlockPos();
        return onSurface ? p.offset(hit.sideHit, radius) : p;
    }

    @Override
    public void onPress(EditorController c, RayTraceResult hit) {
        if (hit == null) return;
        c.sendEdit(MsgEdit.shape(center(hit), (byte) shape, radius, hollow, c.activeStateId()));
    }

    @Override
    public void renderWorld(EditorController c, RayTraceResult hover, float partialTicks) {
        if (hover == null) return;
        BlockPos p = center(hover);
        AxisAlignedBB box = new AxisAlignedBB(p.add(-radius, -radius, -radius), p.add(radius + 1, radius + 1, radius + 1));
        WorldDraw.fill(box, 0x203C8CFF);
        WorldDraw.outline(box, 0xC05AA8FF);
        WorldDraw.fill(WorldDraw.block(p), 0x80FFFFFF);
    }

    @Override
    public void drawOptions(Ui ui, EditorController c) {
        ui.text("Shape");
        shape = ui.combo(SHAPES, shape, ui.contentWidth());
        ui.text("Radius");
        radius = ui.slider("shape.radius", radius, 1, 32, ui.contentWidth());
        hollow = ui.checkbox("Hollow", hollow);
        onSurface = ui.checkbox("Sit on surface", onSurface);
    }
}
