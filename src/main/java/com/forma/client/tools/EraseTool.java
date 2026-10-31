package com.forma.client.tools;

import com.forma.client.EditorController;
import com.forma.client.WorldDraw;
import com.forma.client.ui.IconAtlas;
import com.forma.client.ui.Ui;
import com.forma.network.MsgEdit;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

/** Removes blocks under the cursor; hold RMB and drag to keep erasing. */
public class EraseTool extends Tool {
    private BlockPos last;
    private int size = 0;

    public EraseTool() {
        super("Eraser", IconAtlas.ERASER, "Erase (drag)");
    }

    private AxisAlignedBB area(BlockPos p) {
        return new AxisAlignedBB(p.add(-size, -size, -size), p.add(size + 1, size + 1, size + 1));
    }

    @Override
    public void onPress(EditorController c, RayTraceResult hit) {
        last = null;
        onDrag(c, hit);
    }

    @Override
    public void onDrag(EditorController c, RayTraceResult hit) {
        if (hit == null || hit.getBlockPos().equals(last)) return;
        last = hit.getBlockPos();
        c.sendEdit(MsgEdit.box(last.add(-size, -size, -size), last.add(size, size, size), 0));
    }

    @Override
    public void renderWorld(EditorController c, RayTraceResult hover, float partialTicks) {
        if (hover == null) return;
        WorldDraw.fill(area(hover.getBlockPos()), 0x40FF4040);
        WorldDraw.outline(area(hover.getBlockPos()), 0xE0FF6060);
    }

    @Override
    public void drawOptions(Ui ui, EditorController c) {
        ui.text("Brush radius");
        size = ui.slider("erase.size", size, 0, 8, ui.contentWidth());
    }
}
