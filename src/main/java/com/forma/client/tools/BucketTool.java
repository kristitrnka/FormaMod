package com.forma.client.tools;

import com.forma.client.EditorController;
import com.forma.client.WorldDraw;
import com.forma.client.ui.IconAtlas;
import com.forma.client.ui.Ui;
import com.forma.network.MsgEdit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Flood fill: replaces the connected region of the clicked block (or the air in front of it) with the active block. */
public class BucketTool extends Tool {
    private static final String[] MODES = {"Replace Clicked", "Fill Air In Front"};
    private int mode;
    private int radius = 16;

    public BucketTool() {
        super("Bucket Fill", IconAtlas.BUCKET, "Flood Fill");
    }

    private BlockPos start(RayTraceResult hit) {
        return mode == 0 ? hit.getBlockPos() : hit.getBlockPos().offset(hit.sideHit);
    }

    @Override
    public void onPress(EditorController c, RayTraceResult hit) {
        if (hit != null) c.sendEdit(MsgEdit.flood(start(hit), radius, c.activeStateId()));
    }

    @Override
    public void renderWorld(EditorController c, RayTraceResult hover, float partialTicks) {
        if (hover == null) return;
        BlockPos p = start(hover);
        WorldDraw.outline(WorldDraw.block(p), 0xE05AA8FF);
        WorldDraw.ring(new Vec3d(p).add(0.5, 0.5, 0.5), radius + 0.5, 1, 0x805AA8FF);
    }

    @Override
    public void drawOptions(Ui ui, EditorController c) {
        mode = ui.combo(MODES, mode, ui.contentWidth());
        ui.text("Max radius");
        radius = ui.slider("bucket.radius", radius, 1, 128, ui.contentWidth());
        ui.text("Fills with the Active Block", Ui.TEXT_DIM);
    }
}
