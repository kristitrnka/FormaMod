package com.forma.client.tools;

import com.forma.client.EditorController;
import com.forma.client.WorldDraw;
import com.forma.client.ui.IconAtlas;
import com.forma.client.ui.Ui;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Click points to build a path (straight segments or a smooth curve) with thickness; Enter places it. */
public class LineTool extends Tool {
    private final List<BlockPos> points = new ArrayList<>();
    private int thickness = 0;
    private boolean curve = true;
    private Set<BlockPos> preview = new LinkedHashSet<>();

    public LineTool() {
        super("Path", IconAtlas.LINE, "Add Point (Enter = place)");
    }

    @Override
    public void onPress(EditorController c, RayTraceResult hit) {
        if (hit == null) return;
        points.add(hit.getBlockPos().offset(hit.sideHit));
        rebuild();
    }

    @Override
    public boolean onEnter(EditorController c) {
        if (points.size() < 2) {
            c.status("Add at least two points.");
            return true;
        }
        c.sendPositions(preview, c.activeStateId());
        points.clear();
        rebuild();
        return true;
    }

    @Override
    public boolean onCancel(EditorController c) {
        if (points.isEmpty()) return false;
        points.clear();
        rebuild();
        return true;
    }

    @Override
    public boolean onKey(EditorController c, int keyCode) {
        if (keyCode == Keyboard.KEY_BACK && !points.isEmpty()) {
            points.remove(points.size() - 1);
            rebuild();
            return true;
        }
        return false;
    }

    private void rebuild() {
        preview = new LinkedHashSet<>();
        if (points.size() < 2) return;
        List<Vec3d> path = new ArrayList<>();
        for (BlockPos p : points) path.add(new Vec3d(p).add(0.5, 0.5, 0.5));
        List<Vec3d> samples = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            Vec3d a = path.get(i), b = path.get(i + 1);
            int steps = (int) Math.ceil(a.distanceTo(b) * 3) + 1;
            for (int k = 0; k <= steps; k++) {
                double t = k / (double) steps;
                if (curve && path.size() > 2) {
                    Vec3d p0 = path.get(Math.max(0, i - 1)), p3 = path.get(Math.min(path.size() - 1, i + 2));
                    samples.add(catmullRom(p0, a, b, p3, t));
                } else {
                    samples.add(a.add(b.subtract(a).scale(t)));
                }
            }
        }
        int r = thickness;
        for (Vec3d s : samples) {
            BlockPos c = new BlockPos(s);
            for (int dx = -r; dx <= r; dx++)
                for (int dy = -r; dy <= r; dy++)
                    for (int dz = -r; dz <= r; dz++)
                        if (dx * dx + dy * dy + dz * dz <= r * r + r) preview.add(c.add(dx, dy, dz));
        }
    }

    private static Vec3d catmullRom(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return p0.scale(-0.5 * t3 + t2 - 0.5 * t)
                .add(p1.scale(1.5 * t3 - 2.5 * t2 + 1))
                .add(p2.scale(-1.5 * t3 + 2 * t2 + 0.5 * t))
                .add(p3.scale(0.5 * t3 - 0.5 * t2));
    }

    @Override
    public void renderWorld(EditorController c, RayTraceResult hover, float partialTicks) {
        if (hover != null) WorldDraw.outline(WorldDraw.block(hover.getBlockPos().offset(hover.sideHit)), 0xC0FFFFFF);
        if (preview.size() < 60_000) WorldDraw.fillBlocks(preview, 0x505AA8FF);
        for (int i = 0; i < points.size(); i++) {
            WorldDraw.outline(WorldDraw.block(points.get(i)), 0xFFFFD84A);
            if (i > 0) WorldDraw.line(new Vec3d(points.get(i - 1)).add(0.5, 0.5, 0.5), new Vec3d(points.get(i)).add(0.5, 0.5, 0.5), 0x80FFD84A);
        }
    }

    @Override
    public void drawOptions(Ui ui, EditorController c) {
        ui.text("Thickness (radius)");
        int t = ui.slider("line.thick", thickness, 0, 8, ui.contentWidth());
        boolean cv = ui.checkbox("Smooth curve", curve);
        if (t != thickness || cv != curve) {
            thickness = t;
            curve = cv;
            rebuild();
        }
        ui.text(points.size() + " points, " + EditorController.fmt(preview.size()) + " blocks", Ui.TEXT_DIM);
        ui.text("Enter = place, Bksp = undo pt", Ui.TEXT_DIM);
        int half = (ui.contentWidth() - Ui.SPACING) / 2;
        if (ui.button("Place", half)) onEnter(c);
        ui.sameLine();
        if (ui.button("Clear", half)) onCancel(c);
    }
}
