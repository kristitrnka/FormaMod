package com.forma.client.tools;

import com.forma.client.EditorController;
import com.forma.client.WorldDraw;
import com.forma.client.ui.Ui;
import net.minecraft.util.math.RayTraceResult;

/** An editor tool. RMB presses in the viewport are routed to the active tool. */
public abstract class Tool {
    public final String name;
    public final int icon;
    /** Shown in the status bar after "RMB - ". */
    public final String hint;
    /** Groups tools in the Tools panel. */
    public String category = "Build";

    protected Tool(String name, int icon, String hint) {
        this.name = name;
        this.icon = icon;
        this.hint = hint;
    }

    public void onPress(EditorController c, RayTraceResult hit) {}

    public void onDrag(EditorController c, RayTraceResult hit) {}

    public void onRelease(EditorController c, RayTraceResult hit) {}

    /** Plain (non-Ctrl) key presses while this tool is active. Return true if handled. */
    public boolean onKey(EditorController c, int keyCode) {
        return false;
    }

    /** Enter key. Return true if handled. */
    public boolean onEnter(EditorController c) {
        return false;
    }

    /** Esc key: drop pending work (points etc.). Return true if something was cancelled. */
    public boolean onCancel(EditorController c) {
        return false;
    }

    /** Called inside {@link WorldDraw#begin}/{@link WorldDraw#end}. Default: outline the hovered block. */
    public void renderWorld(EditorController c, RayTraceResult hover, float partialTicks) {
        if (hover != null) WorldDraw.outline(WorldDraw.block(hover.getBlockPos()), 0xC0FFFFFF);
    }

    public void drawOptions(Ui ui, EditorController c) {
        ui.text("No options", Ui.TEXT_DIM);
    }
}
