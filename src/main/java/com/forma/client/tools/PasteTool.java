package com.forma.client.tools;

import com.forma.client.ClientClipboard;
import com.forma.client.EditorController;
import com.forma.client.ui.IconAtlas;
import com.forma.client.ui.Ui;
import com.forma.network.MsgEdit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.input.Keyboard;

/** Shows the clipboard as a ghost under the cursor; RMB pastes it. R rotates, X/Z flip. */
public class PasteTool extends Tool {
    public PasteTool() {
        super("Paste", IconAtlas.PASTE, "Paste");
    }

    private static BlockPos anchor(RayTraceResult hit) {
        return hit.getBlockPos().offset(hit.sideHit);
    }

    @Override
    public void onPress(EditorController c, RayTraceResult hit) {
        ClientClipboard cb = c.clipboard;
        if (cb.isEmpty()) {
            c.status("Clipboard is empty - select and press Ctrl+C.");
            return;
        }
        if (hit == null) return;
        c.sendEdit(MsgEdit.paste(anchor(hit), cb.rotation, cb.flipX, cb.flipZ, cb.keepExisting, cb.pasteAir));
    }

    @Override
    public void renderWorld(EditorController c, RayTraceResult hover, float partialTicks) {
        if (hover != null) c.clipboard.render(anchor(hover));
    }

    @Override
    public boolean onKey(EditorController c, int keyCode) {
        if (keyCode == Keyboard.KEY_R) c.clipboard.rotate();
        else if (keyCode == Keyboard.KEY_X) c.clipboard.flipX();
        else if (keyCode == Keyboard.KEY_Z) c.clipboard.flipZ();
        else return false;
        return true;
    }

    @Override
    public void drawOptions(Ui ui, EditorController c) {
        drawClipboardOptions(ui, c);
    }

    /** Shared with the Clipboard panel. */
    public static void drawClipboardOptions(Ui ui, EditorController c) {
        ClientClipboard cb = c.clipboard;
        if (cb.isEmpty()) {
            ui.text("Empty. Select a region", Ui.TEXT_DIM);
            ui.text("and press Ctrl+C.", Ui.TEXT_DIM);
            return;
        }
        ui.text(cb.data.sx + " x " + cb.data.sy + " x " + cb.data.sz);
        ui.text(EditorController.fmt(cb.data.nonAir) + " blocks", Ui.TEXT_DIM);
        ui.text("Rotation " + cb.rotation * 90 + "°" + (cb.flipX ? "  flip X" : "") + (cb.flipZ ? "  flip Z" : ""), Ui.TEXT_DIM);
        int w = (ui.contentWidth() - Ui.SPACING * 2) / 3;
        if (ui.button("Rot (R)", w)) cb.rotate();
        ui.sameLine();
        if (ui.button("Flip X", w)) cb.flipX();
        ui.sameLine();
        if (ui.button("Flip Z", w)) cb.flipZ();
        ui.separator("Placement");
        cb.keepExisting = ui.checkbox("Keep Existing", cb.keepExisting);
        cb.pasteAir = ui.checkbox("Paste Air", cb.pasteAir);
    }
}
