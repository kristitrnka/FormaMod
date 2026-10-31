package com.forma.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Mouse;

/** Ray from the camera through the mouse cursor (not the screen centre). */
public final class Raycast {
    public static final double REACH = 512.0;

    private Raycast() {}

    public static RayTraceResult underCursor(Entity cam, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || cam == null) return null;
        Vec3d[] ray = ray(cam, partialTicks);
        Vec3d end = ray[0].add(ray[1].scale(REACH));
        RayTraceResult hit = mc.world.rayTraceBlocks(ray[0], end, false, false, false);
        return hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK ? hit : null;
    }

    /** {origin, unit direction} of the ray through the mouse cursor. */
    public static Vec3d[] ray(Entity cam, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3d eye = cam.getPositionEyes(partialTicks);
        Vec3d forward = Vec3d.fromPitchYaw(cam.rotationPitch, cam.rotationYaw);
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d up = right.crossProduct(forward).normalize();

        double w = mc.displayWidth, h = mc.displayHeight;
        double nx = 2.0 * (Mouse.getX() + 0.5) / w - 1.0;
        double ny = 2.0 * (Mouse.getY() + 0.5) / h - 1.0;
        double tan = Math.tan(Math.toRadians(mc.gameSettings.fovSetting) / 2.0);

        Vec3d dir = forward
                .add(right.scale(nx * tan * (w / h)))
                .add(up.scale(ny * tan))
                .normalize();
        return new Vec3d[]{eye, dir};
    }
}
