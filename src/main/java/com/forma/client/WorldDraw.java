package com.forma.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

/** Minimal immediate-mode world overlay drawing (boxes, lines). Colors are ARGB. */
public final class WorldDraw {
    private WorldDraw() {}

    public static void begin(float partialTicks) {
        Entity v = Minecraft.getMinecraft().getRenderViewEntity();
        double x = v.lastTickPosX + (v.posX - v.lastTickPosX) * partialTicks;
        double y = v.lastTickPosY + (v.posY - v.lastTickPosY) * partialTicks;
        double z = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * partialTicks;
        GlStateManager.pushMatrix();
        GlStateManager.translate(-x, -y, -z);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(2.0f);
    }

    public static void end() {
        GlStateManager.glLineWidth(1.0f);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    public static AxisAlignedBB block(BlockPos p) {
        return new AxisAlignedBB(p);
    }

    /** Outline drawn twice: faint through walls, bright where visible. */
    public static void outline(AxisAlignedBB box, int argb) {
        box = box.grow(0.003);
        GlStateManager.disableDepth();
        edges(box, (argb & 0x00FFFFFF) | ((((argb >>> 24) * 2) / 5) << 24));
        GlStateManager.enableDepth();
        edges(box, argb);
    }

    public static void fill(AxisAlignedBB b, int argb) {
        fillRaw(b.grow(0.002), argb);
    }

    public static void fillRaw(AxisAlignedBB b, int argb) {
        float[] c = rgba(argb);
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        boxQuads(buf, c, b);
        Tessellator.getInstance().draw();
    }

    /** Many unit blocks in one draw call (text / line previews). */
    public static void fillBlocks(Iterable<BlockPos> blocks, int argb) {
        float[] c = rgba(argb);
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (BlockPos p : blocks) boxQuads(buf, c, new AxisAlignedBB(p).grow(0.002));
        Tessellator.getInstance().draw();
    }

    private static void boxQuads(BufferBuilder buf, float[] c, AxisAlignedBB b) {
        quad(buf, c, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ);
        quad(buf, c, b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ);
        quad(buf, c, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.minY, b.minZ);
        quad(buf, c, b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ);
        quad(buf, c, b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ);
        quad(buf, c, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.minY, b.maxZ);
    }

    /** Line without touching depth state. */
    public static void lineRaw(Vec3d a, Vec3d b, int argb) {
        float[] c = rgba(argb);
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(a.x, a.y, a.z).color(c[0], c[1], c[2], c[3]).endVertex();
        buf.pos(b.x, b.y, b.z).color(c[0], c[1], c[2], c[3]).endVertex();
        Tessellator.getInstance().draw();
    }

    public static void line(Vec3d a, Vec3d b, int argb) {
        float[] c = rgba(argb);
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        GlStateManager.disableDepth();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(a.x, a.y, a.z).color(c[0], c[1], c[2], c[3]).endVertex();
        buf.pos(b.x, b.y, b.z).color(c[0], c[1], c[2], c[3]).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableDepth();
    }

    /** Three great circles: a cheap readable sphere for brush previews. */
    public static void sphere(Vec3d c, double r, int argb) {
        ring(c, r, 1, argb);
        ring(c, r, 0, argb);
        ring(c, r, 2, argb);
    }

    /** Circle around axis 0=X, 1=Y, 2=Z. Faint through walls, bright where visible. */
    public static void ring(Vec3d c, double r, int axis, int argb) {
        int faint = (argb & 0x00FFFFFF) | ((((argb >>> 24) * 2) / 5) << 24);
        GlStateManager.disableDepth();
        circle(c, r, axis, faint);
        GlStateManager.enableDepth();
        circle(c, r, axis, argb);
    }

    private static void circle(Vec3d c, double r, int axis, int argb) {
        float[] col = rgba(argb);
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        int n = 64;
        for (int i = 0; i < n; i++) {
            double t = i * Math.PI * 2 / n, u = Math.cos(t) * r, v = Math.sin(t) * r;
            double x = c.x, y = c.y, z = c.z;
            if (axis == 0) { y += u; z += v; }
            else if (axis == 1) { x += u; z += v; }
            else { x += u; y += v; }
            buf.pos(x, y, z).color(col[0], col[1], col[2], col[3]).endVertex();
        }
        Tessellator.getInstance().draw();
    }

    private static void edges(AxisAlignedBB b, int argb) {
        float[] c = rgba(argb);
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (double y : ys) for (double z : zs) seg(buf, c, b.minX, y, z, b.maxX, y, z);
        for (double x : xs) for (double z : zs) seg(buf, c, x, b.minY, z, x, b.maxY, z);
        for (double x : xs) for (double y : ys) seg(buf, c, x, y, b.minZ, x, y, b.maxZ);
        Tessellator.getInstance().draw();
    }

    private static void seg(BufferBuilder buf, float[] c, double x1, double y1, double z1, double x2, double y2, double z2) {
        buf.pos(x1, y1, z1).color(c[0], c[1], c[2], c[3]).endVertex();
        buf.pos(x2, y2, z2).color(c[0], c[1], c[2], c[3]).endVertex();
    }

    private static void quad(BufferBuilder buf, float[] c, double... v) {
        for (int i = 0; i < 12; i += 3) buf.pos(v[i], v[i + 1], v[i + 2]).color(c[0], c[1], c[2], c[3]).endVertex();
    }

    private static float[] rgba(int argb) {
        return new float[]{((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f};
    }
}
