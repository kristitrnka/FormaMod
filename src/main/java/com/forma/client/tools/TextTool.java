package com.forma.client.tools;

import com.forma.client.EditorController;
import com.forma.client.WorldDraw;
import com.forma.client.ui.IconAtlas;
import com.forma.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Writes text in blocks using the game's pixel font. Facing the camera (standing) or flat on the ground. */
public class TextTool extends Tool {
    private static final String[] ORIENT = {"Standing", "Flat"};
    private static BufferedImage font;

    private String text = "Hello";
    private int orient;
    private int scale = 1;
    private int depth = 1;
    private Set<BlockPos> preview = new LinkedHashSet<>();

    public TextTool() {
        super("Text", IconAtlas.TEXT, "Place Text");
    }

    @Override
    public void onPress(EditorController c, RayTraceResult hit) {
        if (hit == null || preview.isEmpty()) return;
        c.sendPositions(preview, c.activeStateId());
    }

    @Override
    public void renderWorld(EditorController c, RayTraceResult hover, float partialTicks) {
        if (hover == null) {
            preview.clear();
            return;
        }
        preview = build(hover.getBlockPos().offset(hover.sideHit), c);
        if (preview.size() < 60_000) WorldDraw.fillBlocks(preview, 0x605AA8FF);
    }

    private Set<BlockPos> build(BlockPos anchor, EditorController c) {
        Set<BlockPos> out = new LinkedHashSet<>();
        BufferedImage img = font();
        if (img == null || c.camera == null) return out;
        EnumFacing facing = EnumFacing.fromAngle(c.camera.rotationYaw);
        EnumFacing right = facing.rotateY();
        // Standing: rows go up; flat: rows go away from the camera.
        int cell = img.getWidth() / 16, px = Math.max(1, cell / 8);
        int cursor = 0;
        for (char ch : text.toCharArray()) {
            if (ch == ' ') {
                cursor += 4;
                continue;
            }
            if (ch > 255) ch = '?';
            int cx = (ch % 16) * cell, cy = (ch / 16) * cell;
            int width = 0;
            for (int gx = 0; gx < 8; gx++)
                for (int gy = 0; gy < 8; gy++) {
                    if ((img.getRGB(cx + gx * px, cy + gy * px) >>> 24) == 0) continue;
                    width = Math.max(width, gx + 1);
                    int row = 7 - gy;
                    for (int sx = 0; sx < scale; sx++)
                        for (int sy = 0; sy < scale; sy++)
                            for (int d = 0; d < depth; d++) {
                                int along = (cursor + gx) * scale + sx;
                                int up = row * scale + sy;
                                BlockPos p = anchor.offset(right, along);
                                p = orient == 0 ? p.up(up).offset(facing, d) : p.offset(facing, up).up(d);
                                out.add(p);
                            }
                }
            cursor += width + 1;
        }
        return out;
    }

    private static BufferedImage font() {
        if (font == null) {
            try {
                IResource res = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation("textures/font/ascii.png"));
                font = TextureUtil.readBufferedImage(res.getInputStream());
            } catch (IOException e) {
                return null;
            }
        }
        return font;
    }

    @Override
    public void drawOptions(Ui ui, EditorController c) {
        text = ui.textField("text.value", text, "Type text...", ui.contentWidth());
        ui.text("Orientation");
        orient = ui.combo(ORIENT, orient, ui.contentWidth());
        ui.text("Scale");
        scale = ui.slider("text.scale", scale, 1, 6, ui.contentWidth());
        ui.text("Depth");
        depth = ui.slider("text.depth", depth, 1, 4, ui.contentWidth());
        ui.text("Reads left-to-right from", Ui.TEXT_DIM);
        ui.text("the camera's view.", Ui.TEXT_DIM);
    }
}
