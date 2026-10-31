package com.forma.client.tools;

import com.forma.client.EditorController;
import com.forma.client.WorldDraw;
import com.forma.client.ui.IconAtlas;
import com.forma.client.ui.Ui;
import com.forma.network.MsgEdit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

/** Places the active block against the clicked face. Drag paints along the surface you started on. */
public class PencilTool extends Tool {
    private BlockPos last;
    private boolean replace;

    public PencilTool() {
        super("Pencil", IconAtlas.PENCIL, "Place Block");
    }

    private BlockPos target(RayTraceResult hit) {
        return replace ? hit.getBlockPos() : hit.getBlockPos().offset(hit.sideHit);
    }

    @Override
    public void onPress(EditorController c, RayTraceResult hit) {
        last = null;
        onDrag(c, hit);
    }

    @Override
    public void onDrag(EditorController c, RayTraceResult hit) {
        if (hit == null) return;
        BlockPos p = target(hit);
        // While dragging, only paint onto surfaces next to the last block, so the stroke doesn't climb towards the camera.
        if (last != null && (p.equals(last) || p.distanceSq(last) > 3 || (!replace && hit.getBlockPos().equals(last)))) return;
        last = p;
        c.sendEdit(MsgEdit.box(p, p, c.activeStateId()));
    }

    @Override
    public void renderWorld(EditorController c, RayTraceResult hover, float partialTicks) {
        if (hover == null) return;
        WorldDraw.fill(WorldDraw.block(target(hover)), 0x4060B0FF);
        WorldDraw.outline(WorldDraw.block(target(hover)), 0xE0FFFFFF);
    }

    @Override
    public void drawOptions(Ui ui, EditorController c) {
        replace = ui.checkbox("Replace target", replace);
        ui.text("Uses the Active Block.", Ui.TEXT_DIM);
    }
}
