package com.forma.client;

import com.forma.client.tools.BoxSelectTool;
import com.forma.client.tools.BrushTool;
import com.forma.client.tools.BucketTool;
import com.forma.client.tools.EraseTool;
import com.forma.client.tools.ExtrudeTool;
import com.forma.client.tools.LineTool;
import com.forma.client.tools.MagicSelectTool;
import com.forma.client.tools.PasteTool;
import com.forma.client.tools.TextTool;
import com.forma.common.ClipboardCodec;
import com.forma.network.MsgUpload;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.Slot;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.input.Mouse;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import com.forma.client.tools.PencilTool;
import com.forma.client.tools.RulerTool;
import com.forma.client.tools.ShapeTool;
import com.forma.client.tools.Tool;
import com.forma.client.ui.EditorScreen;
import com.forma.common.ClipboardData;
import com.forma.common.Mask;
import com.forma.common.Transform;
import com.forma.network.FormaNetwork;
import com.forma.network.MsgEdit;
import com.forma.network.MsgHistoryAction;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Owns all client-side editor state: camera, tools, selection, clipboard, masks, active block, history view. */
public final class EditorController {
    public static final EditorController INSTANCE = new EditorController();
    private static final int MAX_RECENT = 12;

    public KeyBinding toggleKey;
    public volatile boolean serverSupported;
    public boolean active;
    public EditorCamera camera;
    private int savedThirdPerson;

    public final Selection selection = new Selection();
    public final ClientClipboard clipboard = new ClientClipboard();
    public final Mask mask = new Mask();
    public boolean maskEnabled = true;

    public IBlockState activeBlock = Blocks.STONE.getDefaultState();
    public ItemStack activeStack = new ItemStack(Blocks.STONE);
    public final List<ItemStack> recent = new ArrayList<>();
    /** Block that Box Select's "Replace" looks for. */
    public IBlockState replaceFrom = Blocks.GRASS.getDefaultState();

    public final List<Tool> tools = new ArrayList<>();
    public Tool tool;
    public PasteTool pasteTool;

    /** Floating block region (paste / schematic / move) waiting for Enter. */
    public Placement placement;
    public final Gizmo gizmo = new Gizmo();
    /** True while the creative inventory is open as a block chooser. */
    public boolean choosing;
    private EditorScreen screen;
    private int nextTransfer = (int) System.nanoTime();
    private int nextListStroke = (int) (System.nanoTime() >>> 3) | 1;

    public RayTraceResult hover;
    public List<String> history = new ArrayList<>();
    public int historyCursor;

    private String statusText = "";
    private long statusTime;

    private EditorController() {}

    void init() {
        toggleKey = new KeyBinding("key.forma.toggle", Keyboard.KEY_RSHIFT, "key.categories.forma");
        ClientRegistry.registerKeyBinding(toggleKey);
        pasteTool = new PasteTool();
        tools.addAll(Arrays.asList(new BoxSelectTool(), new MagicSelectTool(), new PencilTool(), new EraseTool(), new BucketTool(),
                new ShapeTool(), new LineTool(), new TextTool(), new ExtrudeTool(), pasteTool, new RulerTool(),
                BrushTool.sculpt(), BrushTool.smooth(), BrushTool.flatten(), BrushTool.noise(), BrushTool.paint(),
                BrushTool.scatter(), BrushTool.melt()));
        tool = tools.get(0);
        recent.add(activeStack);
    }

    // ---------------------------------------------------------------- open / close

    public void toggle() {
        if (active) close();
        else open();
    }

    public void open() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (!serverSupported) {
            mc.player.sendStatusMessage(new TextComponentString("§cForma is not installed on this server."), true);
            return;
        }
        camera = new EditorCamera(mc.world, mc.player);
        savedThirdPerson = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 0;
        mc.setRenderViewEntity(camera);
        active = true;
        screen = new EditorScreen(this);
        mc.displayGuiScreen(screen);
    }

    public void close() {
        if (!active) return;
        active = false;
        choosing = false;
        placement = null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) mc.setRenderViewEntity(mc.player);
        mc.gameSettings.thirdPersonView = savedThirdPerson;
        camera = null;
        hover = null;
        if (mc.currentScreen instanceof EditorScreen) mc.displayGuiScreen(null);
    }

    // ---------------------------------------------------------------- actions

    public void status(String text) {
        statusText = text;
        statusTime = System.currentTimeMillis();
    }

    public String status() {
        return System.currentTimeMillis() - statusTime < 6000 ? statusText : "";
    }

    public static String fmt(long n) {
        return NumberFormat.getIntegerInstance(Locale.US).format(n);
    }

    /** Sends an edit with the user's Tool Masks attached. */
    public void sendEdit(MsgEdit msg) {
        msg.mask = maskEnabled ? resolvedMask() : null;
        FormaNetwork.CHANNEL.sendToServer(msg);
    }

    /** The mask with "In Selection" conditions filled in from the current selection. */
    private Mask resolvedMask() {
        if (mask.isEmpty()) return null;
        for (Mask.Cond c : mask.conds) {
            if (c.type != Mask.IN_SELECTION) continue;
            if (selection.isValid()) {
                c.boxMin = selection.min().toLong();
                c.boxMax = selection.max().toLong();
            } else {
                // Empty box: nothing is "in selection".
                c.boxMin = new BlockPos(1, 1, 1).toLong();
                c.boxMax = BlockPos.ORIGIN.toLong();
            }
        }
        return mask;
    }

    public int activeStateId() {
        return Block.getStateId(activeBlock);
    }

    public void undo() {
        FormaNetwork.CHANNEL.sendToServer(new MsgHistoryAction(MsgHistoryAction.UNDO));
    }

    public void redo() {
        FormaNetwork.CHANNEL.sendToServer(new MsgHistoryAction(MsgHistoryAction.REDO));
    }

    private boolean needSelection() {
        if (selection.isValid()) return true;
        status("Nothing selected.");
        return false;
    }

    public void fillSelection() {
        if (needSelection()) sendEdit(MsgEdit.box(selection.a, selection.b, activeStateId()));
    }

    public void deleteSelection() {
        if (needSelection()) sendEdit(MsgEdit.box(selection.a, selection.b, 0));
    }

    /** Fill the selection with the active block, but only where {@link #replaceFrom} is (plus any All-mode masks). */
    public void replaceInSelection() {
        if (!needSelection()) return;
        MsgEdit msg = MsgEdit.box(selection.a, selection.b, activeStateId());
        Mask m = new Mask();
        m.conds.add(new Mask.Cond(Mask.BLOCK, Block.getIdFromBlock(replaceFrom.getBlock())));
        Mask user = maskEnabled ? resolvedMask() : null;
        if (user != null && !user.any) m.conds.addAll(user.conds);
        msg.mask = m;
        FormaNetwork.CHANNEL.sendToServer(msg);
    }

    public void copy(boolean cut) {
        if (!needSelection()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (selection.volume() > 8_000_000L) {
            status("Selection too large to copy.");
            return;
        }
        clipboard.set(ClipboardData.capture(mc.world, selection.min(), selection.max(), false), "Copied selection");
        sendEdit(MsgEdit.region(cut ? MsgEdit.OP_CUT : MsgEdit.OP_COPY, selection.min(), selection.max()));
        status((cut ? "Cut " : "Copied ") + fmt(clipboard.data.nonAir) + " blocks - Ctrl+V to paste");
    }

    /** Ctrl+V: float the clipboard at the cursor; move it with the arrows, Enter to paste. */
    public void startPaste() {
        if (clipboard.isEmpty()) {
            status("Clipboard is empty - select and press Ctrl+C.");
            return;
        }
        placement = Placement.paste(clipboard.data, clipboard.name, spawnOrigin(clipboard.data.sx, clipboard.data.sz));
        placement.keepExisting = clipboard.keepExisting;
        placement.pasteAir = clipboard.pasteAir;
        status("Drag the arrows to move, R/X/Z rotate/flip, Enter to paste, Esc to cancel");
    }

    /** Min corner for a new floating placement: on the hovered surface, or in front of the camera. */
    private BlockPos spawnOrigin(int sx, int sz) {
        BlockPos anchor;
        if (hover != null) {
            anchor = hover.getBlockPos().offset(hover.sideHit);
        } else {
            Vec3d front = camera.getPositionVector().add(Vec3d.fromPitchYaw(0, camera.rotationYaw).scale(12));
            anchor = new BlockPos(front);
        }
        return anchor.add(-(sx / 2), 0, -(sz / 2));
    }

    /** Starts moving the selected blocks with the gizmo (the ghost follows; Enter applies). */
    public boolean startMoveSelection() {
        if (!needSelection()) return false;
        if (selection.volume() > 4_000_000L) {
            status("Too large to move with a preview - Shift-drag moves only the box.");
            return false;
        }
        ClipboardData data = ClipboardData.capture(Minecraft.getMinecraft().world, selection.min(), selection.max(), false);
        placement = Placement.move(data, selection.min(), selection.max());
        return true;
    }

    public void commitPlacement() {
        if (placement == null) return;
        placement.commit(this);
        placement = null;
    }

    public void cancelPlacement() {
        placement = null;
        status("Cancelled");
    }

    // ---------------------------------------------------------------- schematics

    public void loadSchematic(File file) {
        ClipboardData data;
        try {
            data = SchematicIO.load(file);
        } catch (IOException | RuntimeException e) {
            status("Could not load " + file.getName() + ": " + e.getMessage());
            return;
        }
        if (data.volume() > 8_000_000L) {
            status("Schematic too large (limit 8,000,000 blocks).");
            return;
        }
        clipboard.set(data, file.getName());
        if (!upload(data)) return;
        placement = Placement.paste(data, file.getName(), spawnOrigin(data.sx, data.sz));
        status("Loaded " + file.getName() + " - drag arrows to move, Enter to paste");
    }

    public void saveSchematic(String name) {
        if (!needSelection()) return;
        name = name.trim().replaceAll("[^A-Za-z0-9_\\- ]", "_");
        if (name.isEmpty()) name = "selection";
        File file = new File(SchematicIO.folder(), name + ".schematic");
        try {
            ClipboardData d = ClipboardData.capture(Minecraft.getMinecraft().world, selection.min(), selection.max(), true);
            SchematicIO.save(file, d);
            status("Saved " + file.getName());
        } catch (IOException | RuntimeException e) {
            status("Save failed: " + e.getMessage());
        }
    }

    /** Sends a clipboard to the server in 30 KB chunks; it becomes the server-side clipboard. */
    private boolean upload(ClipboardData data) {
        byte[] bytes;
        try {
            bytes = ClipboardCodec.encode(data);
        } catch (IOException e) {
            status("Upload failed: " + e.getMessage());
            return false;
        }
        int transfer = nextTransfer++;
        int total = Math.max(1, (bytes.length + MsgUpload.CHUNK - 1) / MsgUpload.CHUNK);
        for (int i = 0; i < total; i++) {
            int from = i * MsgUpload.CHUNK, to = Math.min(bytes.length, from + MsgUpload.CHUNK);
            FormaNetwork.CHANNEL.sendToServer(new MsgUpload(transfer, i, total, Arrays.copyOfRange(bytes, from, to)));
        }
        return true;
    }

    /** Places an arbitrary set of blocks (Path, Text) in chunks that merge into one undo step. */
    public void sendPositions(Collection<BlockPos> blocks, int stateId) {
        int stroke = nextListStroke++;
        long[] buf = new long[MsgEdit.LIST_CHUNK];
        int n = 0;
        for (BlockPos p : blocks) {
            buf[n++] = p.toLong();
            if (n == buf.length) {
                sendEdit(MsgEdit.list(buf.clone(), stateId, stroke));
                n = 0;
            }
        }
        if (n > 0) sendEdit(MsgEdit.list(Arrays.copyOf(buf, n), stateId, stroke));
    }

    public int[] recentStateIds(int count) {
        List<Integer> ids = new ArrayList<>();
        for (ItemStack s : recent) {
            if (ids.size() >= count || !(s.getItem() instanceof ItemBlock)) continue;
            @SuppressWarnings("deprecation")
            IBlockState st = ((ItemBlock) s.getItem()).getBlock().getStateFromMeta(s.getItem().getMetadata(s.getMetadata()));
            ids.add(Block.getStateId(st));
        }
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) out[i] = ids.get(i);
        return out;
    }

    // ---------------------------------------------------------------- block chooser (creative inventory)

    public void openBlockChooser() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.playerController.isInCreativeMode()) {
            status("The block chooser needs creative mode - use the search box instead.");
            return;
        }
        choosing = true;
        mc.displayGuiScreen(new GuiContainerCreative(mc.player));
    }

    private void returnFromChooser() {
        choosing = false;
        if (active && screen != null) Minecraft.getMinecraft().displayGuiScreen(screen);
    }

    @SubscribeEvent
    public void onChooserClick(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!choosing || !(event.getGui() instanceof GuiContainerCreative)) return;
        if (Mouse.getEventButton() != 0 || !Mouse.getEventButtonState()) return;
        Slot slot = ((GuiContainer) event.getGui()).getSlotUnderMouse();
        if (slot == null || !slot.getHasStack()) return;
        event.setCanceled(true);
        if (setActiveFromStack(slot.getStack())) returnFromChooser();
        else status("That's not a block.");
    }

    /** Rotate / flip the selected blocks in place; the selection follows the result. */
    public void transformSelection(int rotation, boolean flipX, boolean flipZ) {
        if (!needSelection()) return;
        BlockPos min = selection.min();
        int sx = selection.sizeX(), sy = selection.sizeY(), sz = selection.sizeZ();
        sendEdit(MsgEdit.transform(min, selection.max(), rotation, flipX, flipZ));
        Transform t = new Transform(rotation, flipX, flipZ);
        BlockPos anchor = new BlockPos(min.getX() + sx / 2, min.getY(), min.getZ() + sz / 2);
        int ox = t.outX(sx, sz), oz = t.outZ(sx, sz);
        BlockPos o = Transform.origin(anchor, ox, oz);
        selection.a = o;
        selection.b = o.add(ox - 1, sy - 1, oz - 1);
    }

    public void pickBlock(RayTraceResult hit) {
        Minecraft mc = Minecraft.getMinecraft();
        if (hit == null || mc.world == null) return;
        IBlockState state = mc.world.getBlockState(hit.getBlockPos());
        ItemStack stack = state.getBlock().getPickBlock(state, hit, mc.world, hit.getBlockPos(), mc.player);
        setActive(state, stack);
    }

    public void pickFromHand() {
        ItemStack held = Minecraft.getMinecraft().player.getHeldItemMainhand();
        if (!setActiveFromStack(held)) status("Hold a block in your main hand first.");
    }

    public boolean setActiveFromStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        @SuppressWarnings("deprecation")
        IBlockState state = block.getStateFromMeta(stack.getItem().getMetadata(stack.getMetadata()));
        setActive(state, stack.copy());
        return true;
    }

    private void setActive(IBlockState state, ItemStack stack) {
        activeBlock = state;
        activeStack = stack == null ? ItemStack.EMPTY : stack;
        if (!activeStack.isEmpty()) {
            recent.removeIf(s -> ItemStack.areItemsEqual(s, activeStack));
            recent.add(0, activeStack);
            while (recent.size() > MAX_RECENT) recent.remove(recent.size() - 1);
        }
        status("Active block: " + blockName());
    }

    public String blockName() {
        return activeStack.isEmpty() ? activeBlock.getBlock().getLocalizedName() : activeStack.getDisplayName();
    }

    public void selectTool(int index) {
        if (index >= 0 && index < tools.size()) tool = tools.get(index);
    }

    // ---------------------------------------------------------------- events

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (toggleKey.isPressed()) toggle();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || camera == null || camera.world != mc.world) {
            close();
            return;
        }
        if (choosing) {
            if (mc.currentScreen == null) returnFromChooser(); // closed with Esc / E
            else if (!(mc.currentScreen instanceof GuiContainerCreative)) close();
            return;
        }
        if (!(mc.currentScreen instanceof EditorScreen)) {
            close();
            return;
        }
        camera.tickMovement(!screen.isTyping());
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!active || camera == null) return;
        float pt = event.getPartialTicks();
        WorldDraw.begin(pt);
        if (selection.isValid()) {
            WorldDraw.fill(selection.box(), 0x283C8CFF);
            WorldDraw.outline(selection.box(), 0xFFFFD84A);
        }
        if (placement != null) placement.render();
        else tool.renderWorld(this, hover, pt);
        AxisAlignedBB target = gizmoBox();
        if (target != null) gizmo.render(target);
        WorldDraw.end();
    }

    /** The box the move arrows are attached to: the floating placement, else the selection. */
    public AxisAlignedBB gizmoBox() {
        if (placement != null) return placement.box();
        return selection.isValid() ? selection.box() : null;
    }

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        if (active) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Pre event) {
        if (active && event.getType() == RenderGameOverlayEvent.ElementType.ALL) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        serverSupported = false;
        Minecraft.getMinecraft().addScheduledTask(() -> {
            close();
            history = new ArrayList<>();
            historyCursor = 0;
            selection.clear();
            clipboard.clear();
            placement = null;
        });
    }
}
