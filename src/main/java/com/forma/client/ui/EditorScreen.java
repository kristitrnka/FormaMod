package com.forma.client.ui;

import com.forma.client.BlockCatalog;
import com.forma.client.EditorController;
import com.forma.client.Gizmo;
import com.forma.client.Placement;
import com.forma.client.Raycast;
import com.forma.client.SchematicIO;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import java.io.File;
import com.forma.client.tools.PasteTool;
import com.forma.client.tools.Tool;
import com.forma.common.Mask;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;

/** The editor overlay: menu bar, docked panels left/right, status bar; the rest is the 3D viewport. */
public class EditorScreen extends GuiScreen {
    private static final int LEFT_W = 132;
    private static final int RIGHT_W = 150;
    private static final int GAP = 2;
    private static final int TOOL_BTN = 22;

    private final EditorController c;
    private final Ui ui = new Ui();
    private int uiScale = 2;
    private boolean rotating;
    private int grabX, grabY;
    private boolean toolDown;
    private String blockSearch = "";
    private int maskAddType;
    private String saveName = "my_build";
    private List<File> schematicFiles;

    // gizmo drag state
    private int dragAxis = -1;
    private boolean dragBoxOnly;
    private Vec3d dragCenter;
    private double dragT0;
    private BlockPos dragStartOrigin, dragStartA, dragStartB;

    public EditorScreen(EditorController controller) {
        this.c = controller;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** True while a text field has focus: movement keys and shortcuts are suspended. */
    public boolean isTyping() {
        return ui.isTyping();
    }

    @Override
    public void onGuiClosed() {
        if (rotating) Mouse.setGrabbed(false);
        rotating = false;
        toolDown = false;
        endDrag();
        if (!c.choosing) c.close();
    }

    private int uiW() {
        return mc.displayWidth / uiScale;
    }

    private int uiH() {
        return mc.displayHeight / uiScale;
    }

    // ------------------------------------------------------------ input

    @Override
    public void handleMouseInput() throws IOException {
        int button = Mouse.getEventButton();
        boolean pressed = Mouse.getEventButtonState();
        int wheel = Mouse.getEventDWheel();
        int mx = Mouse.getEventX() / uiScale;
        int my = (mc.displayHeight - Mouse.getEventY() - 1) / uiScale;

        if (wheel != 0 && !ui.isOverUi(mx, my) && c.camera != null) {
            float f = wheel > 0 ? 1.25f : 0.8f;
            c.camera.speed = Math.max(0.1f, Math.min(20f, c.camera.speed * f));
            c.status(String.format("Camera speed x%.1f", c.camera.speed));
        }
        if (button < 0) return;

        if (pressed) {
            if (rotating || toolDown || dragAxis >= 0) return;
            if (ui.isOverUi(mx, my) || ui.hasOpenMenu()) {
                if (button == 0) {
                    ui.mouseDown = true;
                    ui.queueClick();
                }
                return;
            }
            ui.blur();
            if (button == 0 && c.gizmo.hoverAxis >= 0 && beginDrag(c.gizmo.hoverAxis)) return;
            if (button == 0) {
                rotating = true;
                grabX = Mouse.getEventX();
                grabY = Mouse.getEventY();
                Mouse.setGrabbed(true);
                Mouse.getDX();
                Mouse.getDY();
            } else if (button == 1) {
                toolDown = true;
                c.tool.onPress(c, c.hover);
            } else if (button == 2) {
                c.pickBlock(c.hover);
            }
        } else {
            if (button == 0) {
                ui.mouseDown = false;
                if (dragAxis >= 0) {
                    endDrag();
                    if (c.placement != null) c.status("Enter = apply, Esc = cancel, R/X/Z = rotate/flip");
                }
                if (rotating) {
                    rotating = false;
                    Mouse.setGrabbed(false);
                    Mouse.setCursorPosition(grabX, grabY);
                }
            } else if (button == 1 && toolDown) {
                toolDown = false;
                c.tool.onRelease(c, c.hover);
            }
        }
    }

    /** Grabbing an arrow. On a bare selection: plain drag moves the blocks, Shift-drag moves only the box. */
    private boolean beginDrag(int axis) {
        AxisAlignedBB box = c.gizmoBox();
        if (box == null) return false;
        dragBoxOnly = false;
        if (c.placement == null) {
            dragBoxOnly = isShiftKeyDown() || !c.startMoveSelection();
        }
        Vec3d[] ray = Raycast.ray(c.camera, 1f);
        dragAxis = axis;
        c.gizmo.dragAxis = axis;
        dragCenter = Gizmo.center(box);
        dragT0 = Gizmo.axisParam(ray[0], ray[1], dragCenter, axis);
        dragStartOrigin = c.placement != null ? c.placement.origin : null;
        dragStartA = c.selection.a;
        dragStartB = c.selection.b;
        return true;
    }

    private void updateDrag(Vec3d[] ray) {
        int delta = (int) Math.round(Gizmo.axisParam(ray[0], ray[1], dragCenter, dragAxis) - dragT0);
        BlockPos off = axisOffset(dragAxis, delta);
        if (dragBoxOnly) {
            if (dragStartA == null) return;
            c.selection.a = dragStartA.add(off);
            c.selection.b = dragStartB.add(off);
        } else if (c.placement != null && dragStartOrigin != null) {
            c.placement.origin = dragStartOrigin.add(off);
        }
    }

    private void endDrag() {
        dragAxis = -1;
        c.gizmo.dragAxis = -1;
    }

    private static BlockPos axisOffset(int axis, int d) {
        return axis == 0 ? new BlockPos(d, 0, 0) : axis == 1 ? new BlockPos(0, d, 0) : new BlockPos(0, 0, d);
    }

    /** Arrow keys / PgUp / PgDn nudge the placement (or the selection box) by one block. */
    private boolean nudge(int keyCode) {
        BlockPos off;
        switch (keyCode) {
            case Keyboard.KEY_LEFT: off = new BlockPos(-1, 0, 0); break;
            case Keyboard.KEY_RIGHT: off = new BlockPos(1, 0, 0); break;
            case Keyboard.KEY_UP: off = new BlockPos(0, 0, -1); break;
            case Keyboard.KEY_DOWN: off = new BlockPos(0, 0, 1); break;
            case Keyboard.KEY_PRIOR: off = new BlockPos(0, 1, 0); break;
            case Keyboard.KEY_NEXT: off = new BlockPos(0, -1, 0); break;
            default: return false;
        }
        if (c.placement != null) {
            c.placement.origin = c.placement.origin.add(off);
        } else if (c.selection.isValid()) {
            c.selection.a = c.selection.a.add(off);
            c.selection.b = c.selection.b.add(off);
        } else {
            return false;
        }
        return true;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (ui.isTyping()) {
            ui.typeKey(typedChar, keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (c.placement != null) c.cancelPlacement();
            else if (!c.tool.onCancel(c)) c.close();
            return;
        }
        if (keyCode == c.toggleKey.getKeyCode()) {
            c.close();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (c.placement != null) c.commitPlacement();
            else c.tool.onEnter(c);
            return;
        }
        if (nudge(keyCode)) return;
        if (c.placement != null && !isCtrlKeyDown()) {
            if (keyCode == Keyboard.KEY_R) {
                c.placement.rotate();
                return;
            }
            if (keyCode == Keyboard.KEY_X) {
                c.placement.flipX();
                return;
            }
            if (keyCode == Keyboard.KEY_Z) {
                c.placement.flipZ();
                return;
            }
        }
        if (isCtrlKeyDown()) {
            switch (keyCode) {
                case Keyboard.KEY_Z:
                    if (isShiftKeyDown()) c.redo();
                    else c.undo();
                    break;
                case Keyboard.KEY_Y: c.redo(); break;
                case Keyboard.KEY_D: c.selection.clear(); break;
                case Keyboard.KEY_C: c.copy(false); break;
                case Keyboard.KEY_X: c.copy(true); break;
                case Keyboard.KEY_V: c.startPaste(); break;
                default: break;
            }
            return;
        }
        if (c.tool.onKey(c, keyCode)) return;
        if (keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) {
            c.deleteSelection();
        } else if (keyCode == Keyboard.KEY_F) {
            c.fillSelection();
        } else if (keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_9) {
            c.selectTool(keyCode - Keyboard.KEY_1);
        }
    }

    // ------------------------------------------------------------ frame

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        uiScale = Math.max(1, Math.round(mc.displayHeight / 540f));
        if (c.camera == null) return;

        if (rotating) {
            c.camera.rotate(Mouse.getDX(), Mouse.getDY());
        } else {
            c.hover = Raycast.underCursor(c.camera, partialTicks);
        }
        if (toolDown) c.tool.onDrag(c, c.hover);

        Vec3d[] ray = Raycast.ray(c.camera, partialTicks);
        AxisAlignedBB gizmoBox = c.gizmoBox();
        if (dragAxis >= 0) {
            updateDrag(ray);
        } else if (!rotating) {
            int mxRaw = Mouse.getX() / uiScale, myRaw = (mc.displayHeight - Mouse.getY() - 1) / uiScale;
            boolean overUi = ui.isOverUi(mxRaw, myRaw);
            c.gizmo.hoverAxis = gizmoBox == null || overUi ? -1 : c.gizmo.pick(ray[0], ray[1], gizmoBox);
        }

        int mx = Mouse.getX() / uiScale;
        int my = (mc.displayHeight - Mouse.getY() - 1) / uiScale;

        ScaledResolution sr = new ScaledResolution(mc);
        float s = uiScale / (float) sr.getScaleFactor();
        GlStateManager.pushMatrix();
        GlStateManager.scale(s, s, 1f);
        GlStateManager.disableDepth();

        ui.beginFrame(mx, my);
        if (rotating) ui.mouseDown = false;
        drawLeftColumn();
        drawRightColumn();
        drawStatusBar();
        drawMenuBar();
        ui.endFrame();

        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void drawLeftColumn() {
        int y = Ui.HEADER_H + GAP;
        if (ui.begin("Tools", 0, y, LEFT_W)) {
            int per = (ui.contentWidth() + Ui.SPACING) / (TOOL_BTN + Ui.SPACING);
            String category = null;
            int inRow = 0;
            for (int i = 0; i < c.tools.size(); i++) {
                Tool t = c.tools.get(i);
                if (!t.category.equals(category)) {
                    category = t.category;
                    ui.separator(category);
                    inRow = 0;
                }
                if (inRow % per != 0) ui.sameLine();
                inRow++;
                String key = i < 9 ? "  (" + (i + 1) + ")" : "";
                if (ui.iconButton(t.icon, t == c.tool, t.name + key, TOOL_BTN)) c.tool = t;
            }
        }
        y = ui.end() + GAP;

        Placement p = c.placement;
        if (p != null) {
            if (ui.begin("Placement", 0, y, LEFT_W)) {
                ui.text(p.name);
                ui.text(p.outX() + " x " + p.data.sy + " x " + p.outZ(), Ui.TEXT_DIM);
                ui.text("At " + p.origin.getX() + ", " + p.origin.getY() + ", " + p.origin.getZ(), Ui.TEXT_DIM);
                int w = (ui.contentWidth() - Ui.SPACING * 2) / 3;
                if (ui.button("Rot (R)", w)) p.rotate();
                ui.sameLine();
                if (ui.button("Flip X", w)) p.flipX();
                ui.sameLine();
                if (ui.button("Flip Z", w)) p.flipZ();
                if (p.kind == Placement.Kind.PASTE) {
                    p.keepExisting = ui.checkbox("Keep Existing", p.keepExisting);
                    p.pasteAir = ui.checkbox("Paste Air", p.pasteAir);
                }
                ui.text("Drag arrows / arrow keys", Ui.TEXT_DIM);
                int half = (ui.contentWidth() - Ui.SPACING) / 2;
                if (ui.button("Apply (Enter)", half)) c.commitPlacement();
                ui.sameLine();
                if (ui.button("Cancel (Esc)", half)) c.cancelPlacement();
            }
            y = ui.end() + GAP;
        }

        if (ui.begin("Tool Options", 0, y, LEFT_W)) {
            ui.separator(c.tool.name);
            c.tool.drawOptions(ui, c);
        }
        ui.end();
    }

    private void drawRightColumn() {
        int x = uiW() - RIGHT_W;
        int y = Ui.HEADER_H + GAP;

        if (ui.begin("Target Info", x, y, RIGHT_W)) {
            RayTraceResult h = c.hover;
            if (h == null) {
                ui.text("Nothing under cursor", Ui.TEXT_DIM);
            } else {
                BlockPos p = h.getBlockPos();
                IBlockState st = mc.world.getBlockState(p);
                ui.text(st.getBlock().getLocalizedName());
                ui.text(p.getX() + ", " + p.getY() + ", " + p.getZ() + "  " + h.sideHit.getName(), Ui.TEXT_DIM);
            }
        }
        y = ui.end() + GAP;

        if (ui.begin("History", x, y, RIGHT_W)) {
            List<String> hist = c.history;
            int from = Math.max(0, hist.size() - 6);
            if (hist.isEmpty()) ui.text("No edits yet", Ui.TEXT_DIM);
            for (int i = from; i < hist.size(); i++) {
                boolean applied = i < c.historyCursor;
                ui.selectable("(" + i + ") " + hist.get(i), i == c.historyCursor - 1, applied ? Ui.TEXT : Ui.TEXT_DIM);
            }
            ui.space(2);
            int half = (ui.contentWidth() - Ui.SPACING) / 2;
            if (ui.button("Undo", half)) c.undo();
            ui.sameLine();
            if (ui.button("Redo", half)) c.redo();
        }
        y = ui.end() + GAP;

        if (ui.begin("Clipboard", x, y, RIGHT_W, true)) {
            PasteTool.drawClipboardOptions(ui, c);
            if (!c.clipboard.isEmpty() && ui.button("Paste Mode (Ctrl+V)", ui.contentWidth())) c.startPaste();
        }
        y = ui.end() + GAP;

        if (ui.begin("Active Block", x, y, RIGHT_W)) drawActiveBlock();
        y = ui.end() + GAP;

        if (ui.begin("Tool Masks", x, y, RIGHT_W, true)) drawMasks();
        y = ui.end() + GAP;

        if (ui.begin("Schematics", x, y, RIGHT_W, true)) drawSchematics();
        ui.end();
    }

    private void drawSchematics() {
        if (schematicFiles == null) schematicFiles = SchematicIO.list();
        if (schematicFiles.isEmpty()) {
            ui.text("No files in schematics/", Ui.TEXT_DIM);
        }
        for (int i = 0; i < Math.min(14, schematicFiles.size()); i++) {
            File f = schematicFiles.get(i);
            if (ui.selectable(f.getName(), false, Ui.TEXT)) c.loadSchematic(f);
        }
        if (schematicFiles.size() > 14) ui.text("+" + (schematicFiles.size() - 14) + " more", Ui.TEXT_DIM);
        int half = (ui.contentWidth() - Ui.SPACING) / 2;
        if (ui.button("Refresh", half)) schematicFiles = SchematicIO.list();
        ui.sameLine();
        if (ui.button("Open Folder", half)) OpenGlHelper.openFile(SchematicIO.folder());
        ui.separator("Save Selection");
        saveName = ui.textField("schem.name", saveName, "file name", ui.contentWidth());
        if (ui.button("Save .schematic", ui.contentWidth())) {
            c.saveSchematic(saveName);
            schematicFiles = SchematicIO.list();
        }
    }

    private void drawActiveBlock() {
        int[] at = ui.reserve(20, 20);
        Gui.drawRect(at[0], at[1], at[0] + 20, at[1] + 20, Ui.FRAME);
        Ui.drawItem(c.activeStack, at[0] + 2, at[1] + 2);
        ui.sameLine();
        int tw = RIGHT_W - 40;
        int[] tx = ui.reserve(tw, 20);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(c.blockName(), tw), tx[0], tx[1] + 1, Ui.TEXT);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(String.valueOf(c.activeBlock.getBlock().getRegistryName()), tw), tx[0], tx[1] + 11, Ui.TEXT_DIM);

        blockSearch = ui.textField("block.search", blockSearch, "Search blocks...", ui.contentWidth());
        List<ItemStack> results = BlockCatalog.search(blockSearch, 18);
        if (!blockSearch.trim().isEmpty() && results.isEmpty()) ui.text("No matches", Ui.TEXT_DIM);
        itemGrid(results);
        if (blockSearch.trim().isEmpty()) {
            ui.separator("Recent");
            itemGrid(c.recent);
        }
        int half = (ui.contentWidth() - Ui.SPACING) / 2;
        if (ui.button("Choose...", half)) c.openBlockChooser();
        ui.sameLine();
        if (ui.button("From Hand", half)) c.pickFromHand();
    }

    private void itemGrid(List<ItemStack> stacks) {
        int per = Math.max(1, (ui.contentWidth() + Ui.SPACING) / (18 + Ui.SPACING));
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack st = stacks.get(i);
            if (i % per != 0) ui.sameLine();
            boolean selected = ItemStack.areItemsEqual(st, c.activeStack);
            if (ui.itemButton(st, selected, st.getDisplayName())) c.setActiveFromStack(st);
        }
    }

    private void drawMasks() {
        c.maskEnabled = ui.checkbox("Enabled", c.maskEnabled);
        ui.text("Logic");
        ui.sameLine();
        int logic = ui.combo(new String[]{"All (&)", "Any (|)"}, c.mask.any ? 1 : 0, 80);
        c.mask.any = logic == 1;

        Mask.Cond remove = null;
        for (int i = 0; i < c.mask.conds.size(); i++) {
            Mask.Cond cond = c.mask.conds.get(i);
            if (ui.button(cond.not ? "!" : "=", 12)) cond.not = !cond.not;
            ui.sameLine();
            if (ui.button("x", 12)) remove = cond;
            ui.sameLine();
            ui.text(condLabel(cond), cond.not ? 0xFFFF9A6A : Ui.TEXT);
            if (cond.type == Mask.Y_RANGE) {
                int half = (ui.contentWidth() - Ui.SPACING) / 2;
                cond.minY = ui.slider("mask.min." + i, cond.minY, 0, 255, half);
                ui.sameLine();
                cond.maxY = ui.slider("mask.max." + i, cond.maxY, 0, 255, half);
            }
        }
        if (remove != null) c.mask.conds.remove(remove);
        if (c.mask.conds.isEmpty()) ui.text("No masks: edits affect all", Ui.TEXT_DIM);

        ui.separator("Add");
        maskAddType = ui.combo(Mask.NAMES, maskAddType, ui.contentWidth() - 36);
        ui.sameLine();
        if (ui.button("Add", 33)) {
            Mask.Cond cond = new Mask.Cond((byte) maskAddType, Block.getIdFromBlock(c.activeBlock.getBlock()));
            if (cond.type == Mask.Y_RANGE && c.hover != null) {
                cond.minY = c.hover.getBlockPos().getY();
                cond.maxY = 255;
            }
            c.mask.conds.add(cond);
        }
        ui.text("Block masks use Active Block", Ui.TEXT_DIM);
    }

    private static String condLabel(Mask.Cond cond) {
        String s = (cond.not ? "Not " : "") + Mask.NAMES[cond.type];
        if (Mask.usesBlock(cond.type)) s += " " + Block.getBlockById(cond.block).getLocalizedName();
        if (cond.type == Mask.Y_RANGE) s += " " + cond.minY + ".." + cond.maxY;
        return s;
    }

    private void drawStatusBar() {
        int h = 12, y = uiH() - h, w = uiW();
        Gui.drawRect(0, y, w, uiH(), Ui.BAR);
        String hint = "LMB - Rotate Camera    MMB - Pick Block    RMB - " + c.tool.hint
                + "    Scroll - Speed x" + String.format("%.1f", c.camera.speed);
        fontRenderer.drawString(hint, 4, y + 2, Ui.TEXT_DIM);
        String status = c.status();
        if (!status.isEmpty()) fontRenderer.drawString(status, w - 6 - fontRenderer.getStringWidth(status), y + 2, Ui.ACCENT);
    }

    private void drawMenuBar() {
        ui.beginMenuBar(uiW());
        if (ui.menu("Edit")) {
            if (ui.menuItem("Undo", "Ctrl+Z")) c.undo();
            if (ui.menuItem("Redo", "Ctrl+Y")) c.redo();
            if (ui.menuItem("Copy", "Ctrl+C")) c.copy(false);
            if (ui.menuItem("Cut", "Ctrl+X")) c.copy(true);
            if (ui.menuItem("Paste", "Ctrl+V")) c.startPaste();
            if (ui.menuItem("Fill Selection", "F")) c.fillSelection();
            if (ui.menuItem("Delete Selection", "Del")) c.deleteSelection();
            if (ui.menuItem("Replace in Selection", "")) c.replaceInSelection();
            ui.endMenu();
        }
        if (ui.menu("Select")) {
            if (ui.menuItem("Clear Selection", "Ctrl+D")) c.selection.clear();
            if (ui.menuItem("Select Target Block", "")) {
                if (c.hover != null) c.selection.a = c.selection.b = c.hover.getBlockPos();
            }
            ui.endMenu();
        }
        if (ui.menu("Operations")) {
            if (ui.menuItem("Rotate Selection 90", "")) c.transformSelection(1, false, false);
            if (ui.menuItem("Rotate Selection 180", "")) c.transformSelection(2, false, false);
            if (ui.menuItem("Flip Selection X", "")) c.transformSelection(0, true, false);
            if (ui.menuItem("Flip Selection Z", "")) c.transformSelection(0, false, true);
            ui.endMenu();
        }
        if (ui.menu("Tools")) {
            for (int i = 0; i < c.tools.size(); i++) {
                if (ui.menuItem(c.tools.get(i).name, i < 9 ? String.valueOf(i + 1) : "")) c.selectTool(i);
            }
            ui.endMenu();
        }
        if (ui.menu("View")) {
            if (ui.menuItem("Reset Camera Speed", "")) c.camera.speed = 1f;
            if (ui.menuItem("Exit Editor", "Esc")) c.close();
            ui.endMenu();
        }
    }
}
