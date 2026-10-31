package com.forma.client.tools;

import com.forma.client.EditorController;
import com.forma.client.WorldDraw;
import com.forma.client.ui.IconAtlas;
import com.forma.client.ui.Ui;
import com.forma.network.MsgEdit;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Keyboard;

/**
 * Terrain brush: while RMB is held, sends a "dab" to the server every {@link #INTERVAL_MS}.
 * All dabs of one press share a stroke id, so the whole stroke is a single undo step.
 */
public class BrushTool extends Tool {
    private static final long INTERVAL_MS = 90;
    private static int nextStroke = (int) (System.nanoTime() & 0x3fffffff) | 1;

    private final byte brush;
    private final String[] modes;
    private final boolean usesStrength;
    private final String paramName;
    private final String help;

    private int mode;
    private int radius = 6;
    private int strength = 50;
    private int param = 8;
    private int paramMin = 2, paramMax = 32;

    private int strokeId, seq;
    private long lastDab;
    private boolean down;

    private BrushTool(String name, int icon, byte brush, String[] modes, boolean usesStrength, String paramName, String help) {
        super(name, icon, "Brush (hold)");
        this.brush = brush;
        this.modes = modes;
        this.usesStrength = usesStrength;
        this.paramName = paramName;
        this.help = help;
        this.category = "Terrain";
    }

    public static BrushTool sculpt() {
        return new BrushTool("Sculpt", IconAtlas.SCULPT, MsgEdit.BRUSH_SCULPT, new String[]{"Raise", "Lower"}, true, null,
                "Raises/lowers the surface");
    }

    public static BrushTool smooth() {
        return new BrushTool("Smooth", IconAtlas.SMOOTH, MsgEdit.BRUSH_SMOOTH, null, true, null,
                "Rounds off bumps and holes");
    }

    public static BrushTool flatten() {
        return new BrushTool("Flatten", IconAtlas.FLATTEN, MsgEdit.BRUSH_FLATTEN, new String[]{"Cut & Fill", "Cut Only", "Fill Only"}, false, null,
                "Levels to the clicked height");
    }

    public static BrushTool noise() {
        return new BrushTool("Noise", IconAtlas.NOISE, MsgEdit.BRUSH_NOISE, null, true, "Scale",
                "Adds rough natural detail");
    }

    public static BrushTool paint() {
        return new BrushTool("Paint", IconAtlas.PAINT, MsgEdit.BRUSH_PAINT, new String[]{"All Surfaces", "Top Only"}, true, null,
                "Paints surface with Active Block");
    }

    public static BrushTool scatter() {
        BrushTool t = new BrushTool("Scatter", IconAtlas.SCATTER, MsgEdit.BRUSH_SCATTER, new String[]{"All Surfaces", "Top Only"}, true, "Palette (recent blocks)",
                "Random mix of recent blocks");
        t.param = 3;
        t.paramMin = 1;
        t.paramMax = 12;
        return t;
    }

    public static BrushTool melt() {
        return new BrushTool("Melt", IconAtlas.MELT, MsgEdit.BRUSH_MELT, new String[]{"Melt", "Grow"}, true, null,
                "Erodes or grows edges");
    }

    @Override
    public void onPress(EditorController c, RayTraceResult hit) {
        down = true;
        strokeId = nextStroke++;
        seq = 0;
        lastDab = 0;
        onDrag(c, hit);
    }

    @Override
    public void onDrag(EditorController c, RayTraceResult hit) {
        long now = System.currentTimeMillis();
        if (!down || hit == null || now - lastDab < INTERVAL_MS) return;
        lastDab = now;
        MsgEdit msg = MsgEdit.brush(brush, hit.getBlockPos(), radius, strength, mode, param, c.activeStateId(), strokeId, seq++);
        if (brush == MsgEdit.BRUSH_SCATTER) msg.palette = c.recentStateIds(param);
        c.sendEdit(msg);
    }

    @Override
    public void onRelease(EditorController c, RayTraceResult hit) {
        down = false;
    }

    @Override
    public boolean onKey(EditorController c, int keyCode) {
        if (keyCode == Keyboard.KEY_LBRACKET) radius = Math.max(1, radius - 1);
        else if (keyCode == Keyboard.KEY_RBRACKET) radius = Math.min(32, radius + 1);
        else return false;
        return true;
    }

    @Override
    public void renderWorld(EditorController c, RayTraceResult hover, float partialTicks) {
        if (hover == null) return;
        Vec3d center = new Vec3d(hover.getBlockPos()).add(0.5, 0.5, 0.5);
        int color = down ? 0xFFFFD84A : 0xD05AA8FF;
        if (brush == MsgEdit.BRUSH_FLATTEN) {
            WorldDraw.ring(center.add(0, 0.51, 0), radius + 0.5, 1, color);
        } else {
            WorldDraw.sphere(center, radius + 0.5, color);
        }
        WorldDraw.outline(WorldDraw.block(hover.getBlockPos()), 0x90FFFFFF);
    }

    @Override
    public void drawOptions(Ui ui, EditorController c) {
        ui.text(help, Ui.TEXT_DIM);
        if (modes != null) {
            ui.text("Mode");
            mode = ui.combo(modes, mode, ui.contentWidth());
        }
        ui.text("Radius  [ ]");
        radius = ui.slider(name + ".radius", radius, 1, 32, ui.contentWidth());
        if (usesStrength) {
            ui.text("Strength");
            strength = ui.slider(name + ".strength", strength, 1, 100, ui.contentWidth());
        }
        if (paramName != null) {
            ui.text(paramName);
            param = ui.slider(name + ".param", param, paramMin, paramMax, ui.contentWidth());
        }
        if (brush == MsgEdit.BRUSH_SCATTER) {
            for (int i = 0; i < Math.min(param, c.recent.size()); i++) {
                if (i % 6 != 0) ui.sameLine();
                ui.itemButton(c.recent.get(i), false, c.recent.get(i).getDisplayName());
            }
        }
    }
}
