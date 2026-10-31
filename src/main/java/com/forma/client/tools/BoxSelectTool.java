package com.forma.client.tools;

import com.forma.client.EditorController;
import com.forma.client.Selection;
import com.forma.client.ui.IconAtlas;
import com.forma.client.ui.Ui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

import java.text.NumberFormat;
import java.util.Locale;

/** Drag with RMB to select a box. Shift+RMB moves the second corner of the existing selection. */
public class BoxSelectTool extends Tool {
    private boolean dragging;

    public BoxSelectTool() {
        super("Box Select", IconAtlas.BOX_SELECT, "Drag to Select (Shift: extend)");
    }

    @Override
    public void onPress(EditorController c, RayTraceResult hit) {
        if (hit == null) return;
        BlockPos p = hit.getBlockPos();
        if (GuiScreen.isShiftKeyDown() && c.selection.isValid()) {
            c.selection.b = p;
        } else {
            c.selection.a = p;
            c.selection.b = p;
        }
        dragging = true;
    }

    @Override
    public void onDrag(EditorController c, RayTraceResult hit) {
        if (dragging && hit != null) c.selection.b = hit.getBlockPos();
    }

    @Override
    public void onRelease(EditorController c, RayTraceResult hit) {
        dragging = false;
    }

    @Override
    public void drawOptions(Ui ui, EditorController c) {
        Selection s = c.selection;
        if (!s.isValid()) {
            ui.text("RMB-drag in the world", Ui.TEXT_DIM);
            ui.text("to select a region.", Ui.TEXT_DIM);
            return;
        }
        BlockPos mn = s.min();
        ui.text("Min  " + mn.getX() + ", " + mn.getY() + ", " + mn.getZ());
        ui.text("Size " + s.sizeX() + " x " + s.sizeY() + " x " + s.sizeZ());
        ui.text(NumberFormat.getIntegerInstance(Locale.US).format(s.volume()) + " blocks", Ui.TEXT_DIM);
        int w = (ui.contentWidth() - Ui.SPACING * 2) / 3;
        int half = (ui.contentWidth() - Ui.SPACING) / 2;
        ui.separator("Expand");
        if (ui.button("Up", w)) grow(s, 1);
        ui.sameLine();
        if (ui.button("Down", w)) grow(s, -1);
        ui.sameLine();
        if (ui.button("Shrink", w)) shrink(s);
        ui.separator("Edit");
        if (ui.button("Fill (F)", half)) c.fillSelection();
        ui.sameLine();
        if (ui.button("Delete", half)) c.deleteSelection();
        if (ui.button("Copy", half)) c.copy(false);
        ui.sameLine();
        if (ui.button("Cut", half)) c.copy(true);
        ui.separator("Transform");
        if (ui.button("Rot 90", w)) c.transformSelection(1, false, false);
        ui.sameLine();
        if (ui.button("Flip X", w)) c.transformSelection(0, true, false);
        ui.sameLine();
        if (ui.button("Flip Z", w)) c.transformSelection(0, false, true);
        ui.separator("Replace");
        ui.text("From: " + c.replaceFrom.getBlock().getLocalizedName());
        ui.text("To: " + c.blockName(), Ui.TEXT_DIM);
        if (ui.button("From=Active", half)) c.replaceFrom = c.activeBlock;
        ui.sameLine();
        if (ui.button("Replace", half)) c.replaceInSelection();
        ui.space(2);
        if (ui.button("Clear Selection", ui.contentWidth())) s.clear();
    }

    private static void grow(Selection s, int dir) {
        BlockPos mn = s.min(), mx = s.max();
        if (dir > 0) mx = mx.up();
        else mn = mn.down();
        s.a = mn;
        s.b = mx;
    }

    private static void shrink(Selection s) {
        BlockPos mn = s.min(), mx = s.max();
        if (mx.getX() - mn.getX() >= 2 && mx.getY() - mn.getY() >= 2 && mx.getZ() - mn.getZ() >= 2) {
            s.a = mn.add(1, 1, 1);
            s.b = mx.add(-1, -1, -1);
        }
    }
}
