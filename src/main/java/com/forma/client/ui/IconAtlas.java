package com.forma.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/** Forma's own tool icons, painted in code at startup into a 128x128 atlas (8x8 grid of 16px icons). */
public final class IconAtlas {
    public static final int BOX_SELECT = 0;
    public static final int PENCIL = 1;
    public static final int ERASER = 2;
    public static final int SHAPE = 3;
    public static final int RULER = 4;
    public static final int PASTE = 5;
    public static final int SCULPT = 6;
    public static final int SMOOTH = 7;
    public static final int FLATTEN = 8;
    public static final int NOISE = 9;
    public static final int PAINT = 10;
    public static final int MELT = 11;
    public static final int MAGIC = 12;
    public static final int BUCKET = 13;
    public static final int LINE = 14;
    public static final int TEXT = 15;
    public static final int EXTRUDE = 16;
    public static final int SCATTER = 17;

    private static final int S = 16;
    private static final int N = 8;
    private static final int W = S * N;
    private static ResourceLocation texture;

    private IconAtlas() {}

    public static void draw(int icon, int x, int y, int size) {
        if (texture == null) build();
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        Gui.drawScaledCustomSizeModalRect(x, y, (icon % N) * S, (icon / N) * S, S, S, size, size, W, W);
    }

    private static void build() {
        DynamicTexture tex = new DynamicTexture(W, W);
        int[] d = tex.getTextureData();

        Painter p = new Painter(d, BOX_SELECT);
        p.rect(3, 3, 12, 12, 0x704FA3FF);
        for (int i = 1; i <= 14; i++) {
            int c = ((i - 1) / 2) % 2 == 0 ? 0xFFF2F4F8 : 0;
            if (c == 0) continue;
            p.set(i, 1, c);
            p.set(i, 14, c);
            p.set(1, i, c);
            p.set(14, i, c);
        }
        p.rect(12, 12, 14, 14, 0xFFFFD84A);

        p = new Painter(d, PENCIL);
        p.line(4, 11, 11.5, 3.5, 3.4, 0xFFF2C14E);
        p.line(4.3, 10.3, 11, 3.6, 1.0, 0xFFC99A2E);
        p.line(11.5, 3.5, 13.5, 1.5, 3.4, 0xFFF08CA8);
        p.line(10.6, 2.6, 12.4, 4.4, 1.2, 0xFFB8BEC8);
        p.line(2, 13, 4, 11, 2.6, 0xFFEAD7B0);
        p.set(1, 14, 0xFF2A2A2A);
        p.set(2, 13, 0xFF2A2A2A);

        p = new Painter(d, ERASER);
        p.line(6, 10, 11, 5, 5.2, 0xFFF08CA8);
        p.line(3, 13, 6, 10, 5.2, 0xFFF2F4F8);
        p.line(5.2, 9.2, 6.8, 10.8, 1.0, 0xFF9AA0AA);
        p.rect(1, 15, 14, 15, 0xFF9AA0AA);

        p = new Painter(d, SHAPE);
        p.disc(7.5, 8.0, 6.5, 0xFF3A7FD0);
        p.disc(7.0, 7.4, 5.4, 0xFF4FA3FF);
        p.disc(5.4, 5.6, 2.2, 0xFFBFE2FF);

        p = new Painter(d, RULER);
        p.line(2, 13, 13, 2, 4.6, 0xFFF2C14E);
        for (int k = 1; k <= 5; k++) {
            double t = k / 6.0;
            double cx = 2 + 11 * t, cy = 13 - 11 * t;
            double len = k % 2 == 0 ? 2.0 : 1.2;
            p.line(cx - 0.3, cy - 0.3, cx - 0.3 - len * 0.7, cy - 0.3 - len * 0.7, 1.0, 0xFF6B4A12);
        }

        p = new Painter(d, PASTE);
        p.rect(3, 2, 12, 14, 0xFF8A5A32);
        p.rect(4, 4, 11, 13, 0xFFF2F4F8);
        p.rect(6, 1, 9, 3, 0xFFB8BEC8);
        for (int y = 6; y <= 12; y += 2) p.rect(5, y, y == 12 ? 8 : 10, y, 0xFF7A8290);

        p = new Painter(d, SCULPT);
        for (int x = 0; x < 16; x++) {
            double h = 3 + 8 * Math.exp(-Math.pow((x + 0.5 - 8) / 3.6, 2));
            int top = 15 - (int) Math.round(h);
            p.rect(x, top + 2, x, 15, 0xFF7A5230);
            p.rect(x, top, x, top + 1, 0xFF5DBB4A);
        }
        p.line(12.5, 6, 12.5, 1.5, 1.2, 0xFFF2F4F8);
        p.line(12.5, 1.5, 10.8, 3.2, 1.2, 0xFFF2F4F8);
        p.line(12.5, 1.5, 14.2, 3.2, 1.2, 0xFFF2F4F8);

        p = new Painter(d, SMOOTH);
        for (int k = 0; k < 2; k++) {
            double prevY = 0;
            for (int x = 0; x < 16; x++) {
                double y = 5.5 + k * 5 + Math.sin(x / 15.0 * Math.PI * 2) * (k == 0 ? 2.5 : 1.0);
                if (x > 0) p.line(x - 0.5, prevY, x + 0.5, y, 1.6, k == 0 ? 0xFF4FA3FF : 0xFFBFE2FF);
                prevY = y;
            }
        }

        p = new Painter(d, FLATTEN);
        p.rect(1, 11, 14, 11, 0xFF5DBB4A);
        p.rect(1, 12, 14, 14, 0xFF7A5230);
        p.rect(7, 1, 8, 6, 0xFFF2F4F8);
        p.line(7.5, 9, 4.5, 6, 1.4, 0xFFF2F4F8);
        p.line(8.5, 9, 11.5, 6, 1.4, 0xFFF2F4F8);

        p = new Painter(d, NOISE);
        int[] greys = {0xFF5DBB4A, 0xFF4A9A3A, 0xFF7A5230, 0xFF8E8E8E};
        for (int y = 1; y < 15; y++)
            for (int x = 1; x < 15; x++) {
                double n = Math.sin(x * 1.7 + y * 0.6) + Math.cos(y * 1.3 - x * 0.9);
                if (n > -0.3) p.set(x, y, greys[(int) ((n + 2) * 1.0) % 4]);
            }

        p = new Painter(d, PAINT);
        p.line(12.5, 2.5, 7.5, 7.5, 2.2, 0xFFA87A4A);
        p.line(7.5, 7.5, 6.5, 8.5, 3.0, 0xFFB8BEC8);
        p.line(6, 9, 3, 12, 3.6, 0xFFE05252);
        p.disc(2.5, 13.5, 1.6, 0xFFE05252);

        p = new Painter(d, MELT);
        p.rect(1, 2, 14, 6, 0xFFE8873A);
        p.rect(1, 2, 14, 2, 0xFFF2C14E);
        int[] drips = {9, 4, 12, 7, 10, 5, 13, 8, 4, 11, 6, 9, 12, 7};
        for (int x = 1; x <= 14; x++) p.rect(x, 7, x, drips[x - 1], 0xFFE8873A);
        p.disc(4.5, 13.5, 1.2, 0xFFE8873A);
        p.disc(11.5, 14, 1.2, 0xFFE8873A);

        p = new Painter(d, MAGIC);
        p.line(2, 14, 10, 6, 2.0, 0xFF6B4A2A);
        p.line(9, 7, 11, 5, 2.4, 0xFFF2F4F8);
        for (int[] s : new int[][]{{12, 2}, {14, 6}, {9, 1}, {13, 9}}) {
            p.set(s[0], s[1], 0xFFFFD84A);
            p.set(s[0] - 1, s[1], 0xFFFFE89A);
            p.set(s[0] + 1, s[1], 0xFFFFE89A);
            p.set(s[0], s[1] - 1, 0xFFFFE89A);
            p.set(s[0], s[1] + 1, 0xFFFFE89A);
        }

        p = new Painter(d, BUCKET);
        for (int y = 5; y <= 14; y++) {
            int inset = (y - 5) / 3;
            p.rect(3 + inset, y, 12 - inset, y, 0xFF9AA0AA);
        }
        p.rect(4, 5, 11, 7, 0xFF4FA3FF);
        p.line(3, 5, 8, 1, 1.0, 0xFF7A8290);
        p.line(8, 1, 13, 5, 1.0, 0xFF7A8290);
        p.disc(13.5, 10.5, 1.5, 0xFF4FA3FF);

        p = new Painter(d, LINE);
        double prevX = 1, prevY = 13;
        for (int i = 1; i <= 12; i++) {
            double t = i / 12.0;
            double x = 1 + 13 * t, y = 13 - 11 * t + Math.sin(t * Math.PI * 2) * 3;
            p.line(prevX, prevY, x, y, 2.0, 0xFFF2C14E);
            prevX = x;
            prevY = y;
        }
        p.rect(0, 12, 2, 14, 0xFFF2F4F8);
        p.rect(13, 1, 15, 3, 0xFFF2F4F8);

        p = new Painter(d, TEXT);
        p.rect(2, 2, 13, 4, 0xFFF2F4F8);
        p.rect(6, 5, 9, 14, 0xFFF2F4F8);
        p.rect(5, 13, 10, 14, 0xFFF2F4F8);

        p = new Painter(d, EXTRUDE);
        p.rect(2, 9, 10, 14, 0xFF7A8290);
        p.rect(2, 9, 10, 9, 0xFFB8BEC8);
        p.rect(6, 4, 7, 9, 0xFF6BE05A);
        p.line(6.5, 1.5, 3.8, 4.5, 1.4, 0xFF6BE05A);
        p.line(6.5, 1.5, 9.2, 4.5, 1.4, 0xFF6BE05A);
        p.rect(11, 11, 14, 14, 0xFF4E5560);

        p = new Painter(d, SCATTER);
        int[] cols = {0xFF5DBB4A, 0xFF8E8E8E, 0xFF7A5230, 0xFFE8873A, 0xFF4FA3FF};
        for (int y = 1; y < 15; y += 3)
            for (int x = 1 + (y % 2); x < 15; x += 3) p.rect(x, y, x + 1, y + 1, cols[(x * 7 + y * 3) % cols.length]);

        tex.updateDynamicTexture();
        texture = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("forma_icons", tex);
    }

    private static final class Painter {
        final int[] d;
        final int ox, oy;

        Painter(int[] d, int icon) {
            this.d = d;
            this.ox = (icon % N) * S;
            this.oy = (icon / N) * S;
        }

        void set(int x, int y, int c) {
            if (x < 0 || y < 0 || x >= S || y >= S) return;
            d[(oy + y) * W + ox + x] = c;
        }

        void rect(int x0, int y0, int x1, int y1, int c) {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) set(x, y, c);
        }

        void disc(double cx, double cy, double r, int c) {
            for (int y = 0; y < S; y++)
                for (int x = 0; x < S; x++) {
                    double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                    if (dx * dx + dy * dy <= r * r) set(x, y, c);
                }
        }

        /** Thick segment: every pixel whose centre is within width/2 of the segment. */
        void line(double x0, double y0, double x1, double y1, double width, int c) {
            double vx = x1 - x0, vy = y1 - y0, len2 = vx * vx + vy * vy;
            for (int y = 0; y < S; y++)
                for (int x = 0; x < S; x++) {
                    double px = x + 0.5 - x0, py = y + 0.5 - y0;
                    double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, (px * vx + py * vy) / len2));
                    double dx = px - vx * t, dy = py - vy * t;
                    if (dx * dx + dy * dy <= width * width / 4) set(x, y, c);
                }
        }
    }
}
