package com.forma.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

/** Blender-style translate gizmo: three axis arrows from the centre of a box. LMB-drag an arrow to move along it. */
public final class Gizmo {
    public static final Vec3d[] AXES = {new Vec3d(1, 0, 0), new Vec3d(0, 1, 0), new Vec3d(0, 0, 1)};
    private static final int[] COLORS = {0xFFFF4E4E, 0xFF6BE05A, 0xFF4E8CFF};

    public int hoverAxis = -1;
    public int dragAxis = -1;

    public static Vec3d center(AxisAlignedBB box) {
        return new Vec3d((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
    }

    public static double length(AxisAlignedBB box) {
        double size = Math.max(box.maxX - box.minX, Math.max(box.maxY - box.minY, box.maxZ - box.minZ));
        return size / 2 + 2.5;
    }

    /** Which arrow the mouse ray is over, or -1. */
    public int pick(Vec3d rayO, Vec3d rayD, AxisAlignedBB box) {
        Vec3d c = center(box);
        double len = length(box);
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            double[] st = closest(rayO, rayD, c, AXES[i]);
            double t = Math.max(0, Math.min(len, st[1]));
            double s = Math.max(0, rayD.dotProduct(c.add(AXES[i].scale(t)).subtract(rayO)));
            Vec3d onRay = rayO.add(rayD.scale(s));
            Vec3d onAxis = c.add(AXES[i].scale(t));
            double dist = onRay.distanceTo(onAxis);
            double threshold = 0.025 * s + 0.15;
            if (dist < threshold && dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    /** Position of the mouse ray's closest approach along the given axis line through {@code c}. */
    public static double axisParam(Vec3d rayO, Vec3d rayD, Vec3d c, int axis) {
        return closest(rayO, rayD, c, AXES[axis])[1];
    }

    /** Closest-approach parameters (s on ray P+s*u, t on line Q+t*v). Unit direction vectors expected. */
    private static double[] closest(Vec3d p, Vec3d u, Vec3d q, Vec3d v) {
        Vec3d w0 = p.subtract(q);
        double b = u.dotProduct(v), d = u.dotProduct(w0), e = v.dotProduct(w0);
        double denom = 1 - b * b;
        if (Math.abs(denom) < 1e-6) return new double[]{0, e};
        double s = (b * e - d) / denom;
        double t = (e - b * d) / denom;
        return new double[]{s, t};
    }

    /** Must be called between WorldDraw.begin/end. */
    public void render(AxisAlignedBB box) {
        Vec3d c = center(box);
        double len = length(box);
        GlStateManager.disableDepth();
        for (int i = 0; i < 3; i++) {
            boolean hot = i == hoverAxis || i == dragAxis;
            int color = hot ? 0xFFFFFFA0 : COLORS[i];
            Vec3d tip = c.add(AXES[i].scale(len));
            GlStateManager.glLineWidth(hot ? 5f : 3f);
            WorldDraw.lineRaw(c, tip, color);
            double h = 0.3;
            WorldDraw.fillRaw(new AxisAlignedBB(tip.x - h, tip.y - h, tip.z - h, tip.x + h, tip.y + h, tip.z + h), color);
        }
        double h = 0.18;
        WorldDraw.fillRaw(new AxisAlignedBB(c.x - h, c.y - h, c.z - h, c.x + h, c.y + h, c.z + h), 0xFFFFFFFF);
        GlStateManager.glLineWidth(2f);
        GlStateManager.enableDepth();
    }
}
