package com.forma.client.tools;

import com.forma.client.EditorController;
import com.forma.client.WorldDraw;
import com.forma.client.ui.IconAtlas;
import com.forma.client.ui.Ui;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Click two blocks to measure the distance between them. Client-only. */
public class RulerTool extends Tool {
    private BlockPos first, second;

    public RulerTool() {
        super("Ruler", IconAtlas.RULER, "Set Point");
    }

    @Override
    public void onPress(EditorController c, RayTraceResult hit) {
        if (hit == null) return;
        if (first == null || second != null) {
            first = hit.getBlockPos();
            second = null;
        } else {
            second = hit.getBlockPos();
        }
    }

    @Override
    public void renderWorld(EditorController c, RayTraceResult hover, float partialTicks) {
        super.renderWorld(c, hover, partialTicks);
        BlockPos end = second != null ? second : hover != null ? hover.getBlockPos() : null;
        if (first == null) return;
        WorldDraw.outline(WorldDraw.block(first), 0xFFFFD84A);
        if (end == null) return;
        WorldDraw.outline(WorldDraw.block(end), 0xFFFFD84A);
        WorldDraw.line(new Vec3d(first).add(0.5, 0.5, 0.5), new Vec3d(end).add(0.5, 0.5, 0.5), 0xFFFFD84A);
    }

    @Override
    public void drawOptions(Ui ui, EditorController c) {
        if (first == null) {
            ui.text("RMB two blocks", Ui.TEXT_DIM);
            return;
        }
        BlockPos end = second != null ? second : c.hover != null ? c.hover.getBlockPos() : first;
        int dx = Math.abs(end.getX() - first.getX());
        int dy = Math.abs(end.getY() - first.getY());
        int dz = Math.abs(end.getZ() - first.getZ());
        ui.text("dX " + dx + "  dY " + dy + "  dZ " + dz);
        ui.text(String.format("Distance %.2f", Math.sqrt(dx * dx + dy * dy + dz * dz)));
        ui.text("Blocks " + (dx + 1) + " x " + (dy + 1) + " x " + (dz + 1), Ui.TEXT_DIM);
        if (ui.button("Clear", ui.contentWidth())) first = second = null;
    }
}
