package com.forma.client;

import com.forma.common.ClipboardData;
import com.forma.common.Transform;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/** Translucent preview of a block region (clipboard, schematic or moving selection), cached in a display list. */
public final class Ghost {
    /** Above this many blocks only the bounding box is drawn. */
    private static final int PREVIEW_LIMIT = 300_000;

    private ClipboardData data;
    private Transform transform = new Transform(0, false, false);
    private int displayList = -1;
    private boolean dirty = true;

    public void set(ClipboardData data, Transform transform) {
        this.data = data;
        this.transform = transform;
        dirty = true;
    }

    /** Draws at the given min corner. Must be called between WorldDraw.begin/end. */
    public void render(BlockPos origin, int outline) {
        if (data == null) return;
        int ox = transform.outX(data.sx, data.sz), oz = transform.outZ(data.sx, data.sz);
        AxisAlignedBB box = new AxisAlignedBB(origin, origin.add(ox, data.sy, oz));
        WorldDraw.outline(box, outline);
        if (data.nonAir > PREVIEW_LIMIT) {
            WorldDraw.fill(box, (outline & 0x00FFFFFF) | 0x30000000);
            return;
        }
        if (dirty || displayList < 0) rebuild();
        GlStateManager.pushMatrix();
        GlStateManager.translate(origin.getX(), origin.getY(), origin.getZ());
        GlStateManager.callList(displayList);
        GlStateManager.popMatrix();
    }

    private void rebuild() {
        dirty = false;
        if (displayList < 0) displayList = GLAllocation.generateDisplayLists(1);
        GlStateManager.glNewList(displayList, GL11.GL_COMPILE);
        Transform t = transform;
        int ox = t.outX(data.sx, data.sz), oz = t.outZ(data.sx, data.sz), sy = data.sy;
        int[] dst = new int[ox * sy * oz];
        int[] out = new int[2];
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < data.sz; z++)
                for (int x = 0; x < data.sx; x++) {
                    t.map(x, z, data.sx, data.sz, out);
                    dst[(y * oz + out[1]) * ox + out[0]] = data.states[data.index(x, y, z)];
                }
        Map<Integer, Integer> colors = new HashMap<>();
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < oz; z++)
                for (int x = 0; x < ox; x++) {
                    int id = dst[(y * oz + z) * ox + x];
                    if (id == 0) continue;
                    int rgb = colors.computeIfAbsent(id, Ghost::colorOf);
                    if (air(dst, ox, sy, oz, x, y + 1, z)) face(buf, rgb, 1.0f, x, y + 1, z, x + 1, y + 1, z + 1);
                    if (air(dst, ox, sy, oz, x, y - 1, z)) face(buf, rgb, 0.5f, x, y, z, x + 1, y, z + 1);
                    if (air(dst, ox, sy, oz, x - 1, y, z)) face(buf, rgb, 0.7f, x, y, z, x, y + 1, z + 1);
                    if (air(dst, ox, sy, oz, x + 1, y, z)) face(buf, rgb, 0.7f, x + 1, y, z, x + 1, y + 1, z + 1);
                    if (air(dst, ox, sy, oz, x, y, z - 1)) face(buf, rgb, 0.85f, x, y, z, x + 1, y + 1, z);
                    if (air(dst, ox, sy, oz, x, y, z + 1)) face(buf, rgb, 0.85f, x, y, z + 1, x + 1, y + 1, z + 1);
                }
        Tessellator.getInstance().draw();
        GlStateManager.glEndList();
    }

    private static boolean air(int[] d, int sx, int sy, int sz, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return true;
        return d[(y * sz + z) * sx + x] == 0;
    }

    /** Axis-aligned quad; exactly one of the three extents is flat. */
    private static void face(BufferBuilder b, int rgb, float shade, double x0, double y0, double z0, double x1, double y1, double z1) {
        float r = ((rgb >> 16) & 255) / 255f * shade, g = ((rgb >> 8) & 255) / 255f * shade, bl = (rgb & 255) / 255f * shade, a = 0.55f;
        if (y0 == y1) {
            b.pos(x0, y0, z0).color(r, g, bl, a).endVertex();
            b.pos(x1, y0, z0).color(r, g, bl, a).endVertex();
            b.pos(x1, y0, z1).color(r, g, bl, a).endVertex();
            b.pos(x0, y0, z1).color(r, g, bl, a).endVertex();
        } else if (x0 == x1) {
            b.pos(x0, y0, z0).color(r, g, bl, a).endVertex();
            b.pos(x0, y1, z0).color(r, g, bl, a).endVertex();
            b.pos(x0, y1, z1).color(r, g, bl, a).endVertex();
            b.pos(x0, y0, z1).color(r, g, bl, a).endVertex();
        } else {
            b.pos(x0, y0, z0).color(r, g, bl, a).endVertex();
            b.pos(x1, y0, z0).color(r, g, bl, a).endVertex();
            b.pos(x1, y1, z0).color(r, g, bl, a).endVertex();
            b.pos(x0, y1, z0).color(r, g, bl, a).endVertex();
        }
    }

    private static int colorOf(int stateId) {
        try {
            IBlockState s = Block.getStateById(stateId);
            MapColor mc = s.getMapColor(Minecraft.getMinecraft().world, BlockPos.ORIGIN);
            if (mc != null && mc.colorValue != 0) return mc.colorValue;
        } catch (RuntimeException ignored) {
            // Some modded blocks need a real position for their map colour.
        }
        return 0x9A9A9A;
    }
}
