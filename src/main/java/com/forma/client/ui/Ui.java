package com.forma.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny immediate-mode UI: call widgets every frame in draw order, they return what the user did.
 * Coordinates are in UI units (see {@link EditorScreen#uiScale}).
 */
public final class Ui {
    public static final int BG = 0xF0161719;
    public static final int HEADER = 0xFF1F3E66;
    public static final int HEADER_HOVER = 0xFF2A5185;
    public static final int BORDER = 0xFF2C2F36;
    public static final int TEXT = 0xFFE6E8EC;
    public static final int TEXT_DIM = 0xFF878C96;
    public static final int BUTTON = 0xFF27466F;
    public static final int BUTTON_HOVER = 0xFF33609A;
    public static final int BUTTON_ACTIVE = 0xFF4380CC;
    public static final int FRAME = 0xFF23272F;
    public static final int FRAME_HOVER = 0xFF2E3440;
    public static final int ACCENT = 0xFF5AA8FF;
    public static final int BAR = 0xFF101113;

    public static final int HEADER_H = 13;
    public static final int PAD = 5;
    public static final int SPACING = 3;
    public static final int ROW_H = 13;

    private final FontRenderer font = Minecraft.getMinecraft().fontRenderer;

    public int mouseX, mouseY;
    public boolean mouseDown;
    private boolean clickPending;
    private boolean click;
    private String activeWidget;

    private List<int[]> rects = new ArrayList<>();
    private List<int[]> lastRects = new ArrayList<>();
    private final Map<String, int[]> panelHeights = new HashMap<>();
    private final Map<String, Boolean> collapsed = new HashMap<>();

    private String panelTitle;
    private int panelX, panelY, contentX, contentW;
    private int cursorX, cursorY, lastRight, lastTop;

    private String openMenu;
    private int[] menuDrop;      // dropdown rect from last frame
    private int menuBarX, menuItemY, menuItemX, menuDropW, menuDropH;
    private boolean hoverBlocked;
    private String tooltip;
    private String focusId;
    private String focusText = "";

    // ------------------------------------------------------------ frame

    public void queueClick() {
        clickPending = true;
    }

    public void beginFrame(int mx, int my) {
        mouseX = mx;
        mouseY = my;
        List<int[]> t = lastRects;
        lastRects = rects;
        rects = t;
        rects.clear();
        click = clickPending;
        clickPending = false;
        tooltip = null;
        boolean inDrop = openMenu != null && menuDrop != null && contains(menuDrop, mx, my);
        hoverBlocked = inDrop;
        if (click && openMenu != null && !inDrop && my >= HEADER_H) {
            openMenu = null; // click outside closes the menu and is swallowed
            click = false;
        }
        if (!mouseDown) activeWidget = null;
        if (click) focusId = null; // a text field re-focuses itself if it was the one clicked
    }

    public void endFrame() {
        if (tooltip != null) {
            int w = font.getStringWidth(tooltip) + 8;
            int x = mouseX + 10, y = mouseY + 4;
            Gui.drawRect(x, y, x + w, y + 13, 0xF0202226);
            outline(x, y, w, 13, BORDER);
            font.drawString(tooltip, x + 4, y + 3, TEXT);
        }
    }

    public boolean isOverUi(int mx, int my) {
        for (int[] r : lastRects) if (contains(r, mx, my)) return true;
        return openMenu != null && menuDrop != null && contains(menuDrop, mx, my);
    }

    public boolean hasOpenMenu() {
        return openMenu != null;
    }

    public boolean isDragging() {
        return activeWidget != null;
    }

    // ------------------------------------------------------------ panels

    public boolean begin(String title, int x, int y, int w) {
        return begin(title, x, y, w, false);
    }

    /** Starts a panel. Always call {@link #end()} afterwards; returns false when collapsed. */
    public boolean begin(String title, int x, int y, int w, boolean startCollapsed) {
        panelTitle = title;
        panelX = x;
        panelY = y;
        boolean isCollapsed = collapsed.getOrDefault(title, startCollapsed);
        int h = isCollapsed ? HEADER_H : panelHeights.getOrDefault(title, new int[]{HEADER_H + 20})[0];
        Gui.drawRect(x, y, x + w, y + h, BG);
        outline(x, y, w, h, BORDER);
        boolean hh = hover(x, y, w, HEADER_H);
        Gui.drawRect(x, y, x + w, y + HEADER_H, hh ? HEADER_HOVER : HEADER);
        arrow(x + 4, y + 4, isCollapsed);
        font.drawString(title, x + 13, y + 3, TEXT);
        if (hh && consumeClick()) {
            isCollapsed = !isCollapsed;
            collapsed.put(title, isCollapsed);
        }
        rects.add(new int[]{x, y, w, h});
        contentX = x + PAD;
        contentW = w - PAD * 2;
        cursorX = contentX;
        cursorY = y + HEADER_H + PAD;
        if (isCollapsed) cursorY = Integer.MIN_VALUE;
        return !isCollapsed;
    }

    /** Ends the current panel and returns its bottom edge (for stacking). */
    public int end() {
        if (cursorY == Integer.MIN_VALUE) return panelY + HEADER_H;
        int h = cursorY - SPACING + PAD - panelY;
        panelHeights.put(panelTitle, new int[]{h});
        return panelY + h;
    }

    public int contentWidth() {
        return contentW;
    }

    // ------------------------------------------------------------ widgets

    private void advance(int x, int y, int w, int h) {
        lastRight = x + w;
        lastTop = y;
        cursorX = contentX;
        cursorY = y + h + SPACING;
    }

    public void sameLine() {
        cursorX = lastRight + SPACING;
        cursorY = lastTop;
    }

    public void space(int h) {
        cursorY += h;
    }

    public void text(String s) {
        text(s, TEXT);
    }

    public void text(String s, int color) {
        s = font.trimStringToWidth(s, contentX + contentW - cursorX);
        font.drawString(s, cursorX, cursorY + 2, color);
        advance(cursorX, cursorY, font.getStringWidth(s), 10);
    }

    public void separator(String label) {
        int y = cursorY + 5;
        int tw = label.isEmpty() ? 0 : font.getStringWidth(label) + 4;
        Gui.drawRect(contentX, y, contentX + 4, y + 1, BORDER);
        if (tw > 0) font.drawString(label, contentX + 6, y - 4, TEXT_DIM);
        Gui.drawRect(contentX + 6 + tw, y, contentX + contentW, y + 1, BORDER);
        advance(contentX, cursorY, contentW, 10);
    }

    public boolean button(String label) {
        return button(label, font.getStringWidth(label) + 10);
    }

    public boolean button(String label, int w) {
        int x = cursorX, y = cursorY;
        boolean hov = hover(x, y, w, ROW_H);
        Gui.drawRect(x, y, x + w, y + ROW_H, hov ? (mouseDown ? BUTTON_ACTIVE : BUTTON_HOVER) : BUTTON);
        font.drawString(label, x + (w - font.getStringWidth(label)) / 2, y + 3, TEXT);
        advance(x, y, w, ROW_H);
        return hov && consumeClick();
    }

    public boolean checkbox(String label, boolean value) {
        int x = cursorX, y = cursorY;
        int w = 13 + font.getStringWidth(label);
        boolean hov = hover(x, y, w, ROW_H);
        Gui.drawRect(x, y + 1, x + 11, y + 12, hov ? FRAME_HOVER : FRAME);
        if (value) Gui.drawRect(x + 3, y + 4, x + 8, y + 9, ACCENT);
        font.drawString(label, x + 14, y + 3, TEXT);
        advance(x, y, w, ROW_H);
        return hov && consumeClick() ? !value : value;
    }

    public int slider(String id, int value, int min, int max, int w) {
        int x = cursorX, y = cursorY;
        boolean hov = hover(x, y, w, ROW_H);
        if (hov && consumeClick()) activeWidget = id;
        if (id.equals(activeWidget) && mouseDown) {
            float f = (mouseX - x) / (float) Math.max(1, w - 1);
            value = Math.round(min + Math.max(0f, Math.min(1f, f)) * (max - min));
        }
        Gui.drawRect(x, y, x + w, y + ROW_H, hov || id.equals(activeWidget) ? FRAME_HOVER : FRAME);
        int gx = x + Math.round((value - min) / (float) Math.max(1, max - min) * (w - 6));
        Gui.drawRect(gx, y + 1, gx + 6, y + ROW_H - 1, id.equals(activeWidget) ? BUTTON_ACTIVE : ACCENT);
        String s = String.valueOf(value);
        font.drawString(s, x + (w - font.getStringWidth(s)) / 2, y + 3, TEXT);
        advance(x, y, w, ROW_H);
        return value;
    }

    /** A combo box that cycles through its options on click (right side arrow like a dropdown). */
    public int combo(String[] options, int index, int w) {
        int x = cursorX, y = cursorY;
        boolean hov = hover(x, y, w, ROW_H);
        Gui.drawRect(x, y, x + w, y + ROW_H, hov ? FRAME_HOVER : FRAME);
        Gui.drawRect(x + w - ROW_H, y, x + w, y + ROW_H, hov ? BUTTON_HOVER : BUTTON);
        arrow(x + w - ROW_H + 3, y + 4, false);
        font.drawString(options[index], x + 4, y + 3, TEXT);
        advance(x, y, w, ROW_H);
        if (hov && consumeClick()) return (index + 1) % options.length;
        return index;
    }

    public boolean selectable(String label, boolean selected, int color) {
        int x = contentX, y = cursorY, w = contentW;
        boolean hov = hover(x, y, w, 11);
        if (selected || hov) Gui.drawRect(x, y, x + w, y + 11, selected ? BUTTON : FRAME_HOVER);
        font.drawString(font.trimStringToWidth(label, w - 4), x + 2, y + 2, color);
        advance(x, y, w, 11);
        return hov && consumeClick();
    }

    public boolean iconButton(int icon, boolean selected, String tip, int size) {
        int x = cursorX, y = cursorY;
        boolean hov = hover(x, y, size, size);
        int bg = selected ? BUTTON_ACTIVE : hov ? BUTTON_HOVER : BUTTON;
        Gui.drawRect(x, y, x + size, y + size, bg);
        IconAtlas.draw(icon, x + (size - 16) / 2, y + (size - 16) / 2, 16);
        if (hov) tooltip = tip;
        advance(x, y, size, size);
        return hov && consumeClick();
    }

    /** Single-line text input. Returns the current text; keys arrive through {@link #typeKey}. */
    public String textField(String id, String value, String placeholder, int w) {
        int x = cursorX, y = cursorY;
        boolean hov = hover(x, y, w, ROW_H);
        if (hov && consumeClick()) {
            focusId = id;
            focusText = value;
        }
        boolean focused = id.equals(focusId);
        String v = focused ? focusText : value;
        Gui.drawRect(x, y, x + w, y + ROW_H, focused ? FRAME_HOVER : hov ? FRAME_HOVER : FRAME);
        if (focused) outline(x, y, w, ROW_H, ACCENT);
        if (v.isEmpty() && !focused) {
            font.drawString(placeholder, x + 4, y + 3, TEXT_DIM);
        } else {
            String shown = font.trimStringToWidth(v, w - 10, true);
            font.drawString(shown, x + 4, y + 3, TEXT);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = x + 4 + font.getStringWidth(shown);
                Gui.drawRect(cx, y + 2, cx + 1, y + 11, TEXT);
            }
        }
        advance(x, y, w, ROW_H);
        return v;
    }

    public boolean isTyping() {
        return focusId != null;
    }

    public void blur() {
        focusId = null;
    }

    public void typeKey(char ch, int keyCode) {
        if (keyCode == Keyboard.KEY_BACK) {
            if (!focusText.isEmpty()) focusText = focusText.substring(0, focusText.length() - 1);
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_ESCAPE) {
            focusId = null;
        } else if (ChatAllowedCharacters.isAllowedCharacter(ch) && focusText.length() < 40) {
            focusText += ch;
        }
    }

    /** 18x18 slot showing an item. */
    public boolean itemButton(ItemStack stack, boolean selected, String tip) {
        int x = cursorX, y = cursorY, size = 18;
        boolean hov = hover(x, y, size, size);
        Gui.drawRect(x, y, x + size, y + size, selected ? BUTTON_ACTIVE : hov ? FRAME_HOVER : FRAME);
        drawItem(stack, x + 1, y + 1);
        if (hov) tooltip = tip;
        advance(x, y, size, size);
        return hov && consumeClick();
    }

    public static void drawItem(ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
    }

    /** Reserves a box in the layout and returns its top-left corner for custom drawing. */
    public int[] reserve(int w, int h) {
        int[] at = {cursorX, cursorY};
        advance(cursorX, cursorY, w, h);
        return at;
    }

    // ------------------------------------------------------------ menu bar (draw last, so dropdowns sit on top)

    public void beginMenuBar(int screenW) {
        Gui.drawRect(0, 0, screenW, HEADER_H, BAR);
        rects.add(new int[]{0, 0, screenW, HEADER_H});
        menuBarX = 4;
    }

    public boolean menu(String title) {
        int w = font.getStringWidth(title) + 10;
        int x = menuBarX;
        menuBarX += w;
        boolean hov = hoverRaw(x, 0, w, HEADER_H);
        boolean open = title.equals(openMenu);
        if (open || hov) Gui.drawRect(x, 0, x + w, HEADER_H, open ? BUTTON : FRAME_HOVER);
        font.drawString(title, x + 5, 3, TEXT);
        if (hov && click) {
            click = false;
            openMenu = open ? null : title;
            open = !open;
        } else if (hov && openMenu != null && !open) {
            openMenu = title; // hover-switch between menus like a desktop menu bar
            open = true;
        }
        if (open) {
            menuItemX = x;
            menuItemY = HEADER_H;
            menuDropW = 150;
            int h = menuDrop != null ? menuDrop[3] : 0;
            Gui.drawRect(x, HEADER_H, x + menuDropW, HEADER_H + h, 0xF8191B1F);
            outline(x, HEADER_H, menuDropW, h, BORDER);
            menuDropH = 0;
        }
        return open;
    }

    public boolean menuItem(String label, String shortcut) {
        int x = menuItemX, y = menuItemY + 2 + menuDropH;
        boolean hov = hoverRaw(x + 1, y, menuDropW - 2, 12);
        if (hov) Gui.drawRect(x + 1, y, x + menuDropW - 1, y + 12, BUTTON_HOVER);
        font.drawString(label, x + 6, y + 2, TEXT);
        if (!shortcut.isEmpty()) font.drawString(shortcut, x + menuDropW - 6 - font.getStringWidth(shortcut), y + 2, TEXT_DIM);
        menuDropH += 12;
        if (hov && click) {
            click = false;
            openMenu = null;
            return true;
        }
        return false;
    }

    public void endMenu() {
        menuDrop = new int[]{menuItemX, HEADER_H, menuDropW, menuDropH + 4};
    }

    // ------------------------------------------------------------ helpers

    private boolean consumeClick() {
        if (!click) return false;
        click = false;
        return true;
    }

    private boolean hover(int x, int y, int w, int h) {
        return !hoverBlocked && activeWidget == null && hoverRaw(x, y, w, h);
    }

    private boolean hoverRaw(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static boolean contains(int[] r, int x, int y) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    public static void outline(int x, int y, int w, int h, int color) {
        Gui.drawRect(x, y, x + w, y + 1, color);
        Gui.drawRect(x, y + h - 1, x + w, y + h, color);
        Gui.drawRect(x, y, x + 1, y + h, color);
        Gui.drawRect(x + w - 1, y, x + w, y + h, color);
    }

    /** 5px triangle: pointing down when open, right when collapsed. */
    private static void arrow(int x, int y, boolean right) {
        for (int i = 0; i < 3; i++) {
            if (right) Gui.drawRect(x + i, y + i - 1, x + i + 1, y + 5 - i, TEXT);
            else Gui.drawRect(x + i - 1, y + i, x + 5 - i, y + i + 1, TEXT);
        }
    }
}
