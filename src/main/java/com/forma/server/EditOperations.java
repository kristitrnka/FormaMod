package com.forma.server;

import com.forma.common.ClipboardCodec;
import com.forma.common.ClipboardData;
import com.forma.common.Transform;
import com.forma.network.FormaNetwork;
import com.forma.network.MsgEdit;
import com.forma.network.MsgHistorySync;
import com.forma.network.MsgUpload;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Applies edit requests on the server thread and records them for undo. */
public final class EditOperations {
    public static final long MAX_VOLUME = 8_000_000L;
    public static final int MAX_RADIUS = 64;
    public static final int MAX_BRUSH_RADIUS = 32;
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();
    private static final Map<UUID, ClipboardData> CLIPBOARDS = new HashMap<>();
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static final int FLOOD_LIMIT = 1_000_000;
    /** ~64 MB compressed. */
    private static final int MAX_UPLOAD_CHUNKS = 2200;

    private interface ShapeTest {
        boolean contains(int dx, int dy, int dz);
    }

    private EditOperations() {}

    public static boolean canEdit(EntityPlayerMP player) {
        if (player.canUseCommand(2, "forma")) return true;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        // Singleplayer owner without cheats: allow in creative mode.
        return server != null && server.isSinglePlayer()
                && player.getName().equals(server.getServerOwner()) && player.isCreative();
    }

    public static void forget(UUID player) {
        EditHistory.clear(player);
        CLIPBOARDS.remove(player);
        UPLOADS.remove(player);
    }

    public static void apply(EntityPlayerMP player, MsgEdit msg) {
        if (!canEdit(player)) {
            sync(player, "You need operator permission (or creative in singleplayer) to edit.");
            return;
        }
        IBlockState state = stateOf(msg.stateId);
        BlockPos min = min(msg.a, msg.b), max = max(msg.a, msg.b);

        switch (msg.op) {
            case MsgEdit.OP_BOX: {
                if (!checkVolume(player, min, max)) return;
                EditSession s = EditSession.create(player, msg.mask, false);
                fillRegion(s, min, max, state, (x, y, z) -> true);
                s.finish(player, state == AIR ? "Deleted" : "Filled", false);
                break;
            }
            case MsgEdit.OP_SHAPE: {
                int r = clamp(msg.radius, 0, MAX_RADIUS);
                BlockPos c = msg.a;
                ShapeTest test = shapeTest(msg.shape, r, msg.hollow);
                EditSession s = EditSession.create(player, msg.mask, false);
                fillRegion(s, c.add(-r, -r, -r), c.add(r, r, r), state,
                        (x, y, z) -> test.contains(x - c.getX(), y - c.getY(), z - c.getZ()));
                String name = msg.shape == MsgEdit.SHAPE_CUBE ? "cube" : msg.shape == MsgEdit.SHAPE_CYLINDER ? "cylinder" : "sphere";
                s.finish(player, "Placed " + name + ",", false);
                break;
            }
            case MsgEdit.OP_COPY:
            case MsgEdit.OP_CUT: {
                if (!checkVolume(player, min, max)) return;
                ClipboardData data = ClipboardData.capture(player.world, min, max, true);
                CLIPBOARDS.put(player.getUniqueID(), data);
                if (msg.op == MsgEdit.OP_CUT) {
                    EditSession s = EditSession.create(player, msg.mask, false);
                    fillRegion(s, min, max, AIR, (x, y, z) -> true);
                    s.finish(player, "Cut", false);
                } else {
                    sync(player, "Copied " + fmt(data.nonAir) + " blocks");
                }
                break;
            }
            case MsgEdit.OP_PASTE: {
                ClipboardData data = CLIPBOARDS.get(player.getUniqueID());
                if (data == null) {
                    sync(player, "Clipboard is empty - copy something first.");
                    return;
                }
                EditSession s = EditSession.create(player, msg.mask, false);
                paste(s, data, msg.a, new Transform(msg.rotation, msg.flipX, msg.flipZ), msg.keepExisting, msg.pasteAir);
                s.finish(player, "Pasted", false);
                break;
            }
            case MsgEdit.OP_TRANSFORM: {
                if (!checkVolume(player, min, max)) return;
                ClipboardData data = ClipboardData.capture(player.world, min, max, true);
                // Removal and re-placement touch the same positions: dedupe so undo restores the original.
                EditSession s = EditSession.create(player, msg.mask, true);
                fillRegion(s, min, max, AIR, (x, y, z) -> true);
                BlockPos anchor = new BlockPos(min.getX() + data.sx / 2, min.getY(), min.getZ() + data.sz / 2);
                paste(s, data, anchor, new Transform(msg.rotation, msg.flipX, msg.flipZ), false, false);
                s.finish(player, "Transformed", false);
                break;
            }
            case MsgEdit.OP_MOVE: {
                if (!checkVolume(player, min, max)) return;
                ClipboardData data = ClipboardData.capture(player.world, min, max, true);
                EditSession s = EditSession.create(player, msg.mask, true);
                fillRegion(s, min, max, AIR, (x, y, z) -> true);
                Transform t = new Transform(msg.rotation, msg.flipX, msg.flipZ);
                int ox = t.outX(data.sx, data.sz), oz = t.outZ(data.sx, data.sz);
                BlockPos anchor = msg.dest.add(ox / 2, 0, oz / 2);
                paste(s, data, anchor, t, false, false);
                s.finish(player, "Moved", false);
                break;
            }
            case MsgEdit.OP_LIST: {
                EditSession s = EditSession.forStroke(player, msg.mask, msg.strokeId);
                for (long p : msg.positions) s.set(BlockPos.fromLong(p), state);
                s.finish(player, "Placed", false);
                break;
            }
            case MsgEdit.OP_FLOOD: {
                EditSession s = EditSession.create(player, msg.mask, false);
                for (BlockPos p : floodRegion(player.world, msg.a, clamp(msg.radius, 1, 128))) s.set(p, state);
                s.finish(player, "Bucket filled", false);
                break;
            }
            case MsgEdit.OP_EXTRUDE: {
                EditSession s = EditSession.create(player, msg.mask, false);
                extrude(s, msg.a, EnumFacing.byIndex(msg.shape % 6), clamp(msg.radius, 1, 128), msg.mode == 1);
                s.finish(player, msg.mode == 1 ? "Pulled" : "Extruded", false);
                break;
            }
            case MsgEdit.OP_BRUSH: {
                EditSession s = EditSession.forStroke(player, msg.mask, msg.strokeId);
                Brushes.apply(s, msg, state);
                s.finish(player, Brushes.name(msg.brush) + ":", true);
                break;
            }
            default:
                break;
        }
    }

    /** Connected (6-way) blocks with exactly the clicked state, within a sphere of the given radius. */
    private static List<BlockPos> floodRegion(World w, BlockPos start, int radius) {
        List<BlockPos> out = new ArrayList<>();
        if (!w.isBlockLoaded(start)) return out;
        IBlockState target = w.getBlockState(start);
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.toLong());
        long r2 = (long) radius * radius;
        while (!queue.isEmpty() && out.size() < FLOOD_LIMIT) {
            BlockPos p = queue.poll();
            out.add(p);
            for (EnumFacing f : EnumFacing.VALUES) {
                BlockPos n = p.offset(f);
                if (n.getY() < 0 || n.getY() > 255 || n.distanceSq(start) > r2 || !seen.add(n.toLong())) continue;
                if (w.isBlockLoaded(n) && w.getBlockState(n) == target) queue.add(n);
            }
        }
        return out;
    }

    /** Push the connected face region one block outwards along {@code side}, or pull it one block in. */
    private static void extrude(EditSession s, BlockPos start, EnumFacing side, int radius, boolean pull) {
        World w = s.world;
        Block block = w.getBlockState(start).getBlock();
        if (block == Blocks.AIR) return;
        List<EnumFacing> planar = new ArrayList<>();
        for (EnumFacing f : EnumFacing.VALUES) if (f.getAxis() != side.getAxis()) planar.add(f);
        List<BlockPos> region = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.toLong());
        long r2 = (long) radius * radius;
        while (!queue.isEmpty() && region.size() < FLOOD_LIMIT) {
            BlockPos p = queue.poll();
            if (w.getBlockState(p).getBlock() != block || w.getBlockState(p.offset(side)).getMaterial().isSolid()) continue;
            region.add(p);
            for (EnumFacing f : planar) {
                BlockPos n = p.offset(f);
                if (n.distanceSq(start) <= r2 && seen.add(n.toLong()) && w.isBlockLoaded(n)) queue.add(n);
            }
        }
        List<IBlockState> states = new ArrayList<>();
        for (BlockPos p : region) states.add(w.getBlockState(p));
        for (int i = 0; i < region.size(); i++) {
            if (pull) s.set(region.get(i), AIR);
            else s.set(region.get(i).offset(side), states.get(i));
        }
    }

    public static void receiveUpload(EntityPlayerMP player, MsgUpload msg) {
        if (!canEdit(player)) return;
        UUID id = player.getUniqueID();
        if (msg.total <= 0 || msg.total > MAX_UPLOAD_CHUNKS || msg.index < 0 || msg.index >= msg.total) return;
        Upload up = UPLOADS.get(id);
        if (up == null || up.transferId != msg.transferId) {
            up = new Upload(msg.transferId, msg.total);
            UPLOADS.put(id, up);
        }
        if (up.chunks.length != msg.total || up.chunks[msg.index] != null) return;
        up.chunks[msg.index] = msg.data;
        if (++up.received < up.chunks.length) return;
        UPLOADS.remove(id);
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] c : up.chunks) all.write(c, 0, c.length);
        try {
            ClipboardData data = ClipboardCodec.decode(all.toByteArray(), MAX_VOLUME);
            CLIPBOARDS.put(id, data);
            sync(player, "Schematic ready: " + fmt(data.nonAir) + " blocks");
        } catch (IOException | RuntimeException e) {
            sync(player, "Schematic upload failed: " + e.getMessage());
        }
    }

    private static final class Upload {
        final int transferId;
        final byte[][] chunks;
        int received;

        Upload(int transferId, int total) {
            this.transferId = transferId;
            this.chunks = new byte[total][];
        }
    }

    private static void fillRegion(EditSession s, BlockPos min, BlockPos max, IBlockState state, ShapeTest test) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = Math.max(0, min.getY());
        int maxY = Math.min(s.world.getHeight() - 1, max.getY());
        for (int x = min.getX(); x <= max.getX(); x++)
            for (int z = min.getZ(); z <= max.getZ(); z++)
                for (int y = minY; y <= maxY; y++) {
                    if (!test.contains(x, y, z)) continue;
                    s.set(pos.setPos(x, y, z), state);
                }
    }

    private static void paste(EditSession s, ClipboardData d, BlockPos anchor, Transform t, boolean keepExisting, boolean pasteAir) {
        World world = s.world;
        BlockPos origin = Transform.origin(anchor, t.outX(d.sx, d.sz), t.outZ(d.sx, d.sz));
        int[] out = new int[2];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < d.sy; y++)
            for (int z = 0; z < d.sz; z++)
                for (int x = 0; x < d.sx; x++) {
                    int i = d.index(x, y, z);
                    int id = d.states[i];
                    if (id == 0 && !pasteAir) continue;
                    t.map(x, z, d.sx, d.sz, out);
                    pos.setPos(origin.getX() + out[0], origin.getY() + y, origin.getZ() + out[1]);
                    if (keepExisting && !world.isAirBlock(pos)) continue;
                    if (!s.set(pos, t.apply(stateOf(id)))) continue;
                    NBTTagCompound nbt = d.tiles.get(i);
                    if (nbt != null) {
                        TileEntity te = world.getTileEntity(pos);
                        if (te != null) {
                            NBTTagCompound copy = nbt.copy();
                            copy.setInteger("x", pos.getX());
                            copy.setInteger("y", pos.getY());
                            copy.setInteger("z", pos.getZ());
                            te.readFromNBT(copy);
                            te.markDirty();
                        }
                    }
                }
    }

    private static ShapeTest shapeTest(byte shape, int r, boolean hollow) {
        double outer = (r + 0.5) * (r + 0.5);
        double inner = (r - 0.5) * (r - 0.5);
        switch (shape) {
            case MsgEdit.SHAPE_CUBE:
                return (dx, dy, dz) -> !hollow || Math.abs(dx) == r || Math.abs(dy) == r || Math.abs(dz) == r;
            case MsgEdit.SHAPE_CYLINDER:
                return (dx, dy, dz) -> {
                    double d = dx * dx + dz * dz;
                    if (d >= outer) return false;
                    return !hollow || d >= inner || Math.abs(dy) == r;
                };
            default:
                return (dx, dy, dz) -> {
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d >= outer) return false;
                    return !hollow || d >= inner;
                };
        }
    }

    public static void undo(EntityPlayerMP player) {
        if (!canEdit(player)) return;
        EditHistory h = EditHistory.of(player.getUniqueID());
        if (h.cursor == 0) {
            sync(player, "Nothing to undo.");
            return;
        }
        EditHistory.Change c = h.entries.get(h.cursor - 1);
        if (!sameDimension(player, c)) return;
        h.cursor--;
        for (int i = c.size - 1; i >= 0; i--) set(player.world, c.positions[i], c.before[i]);
        sync(player, "Undo: " + c.description);
    }

    public static void redo(EntityPlayerMP player) {
        if (!canEdit(player)) return;
        EditHistory h = EditHistory.of(player.getUniqueID());
        if (h.cursor >= h.entries.size()) {
            sync(player, "Nothing to redo.");
            return;
        }
        EditHistory.Change c = h.entries.get(h.cursor);
        if (!sameDimension(player, c)) return;
        h.cursor++;
        for (int i = 0; i < c.size; i++) set(player.world, c.positions[i], c.after[i]);
        sync(player, "Redo: " + c.description);
    }

    private static boolean sameDimension(EntityPlayerMP player, EditHistory.Change c) {
        if (player.world.provider.getDimension() == c.dimension) return true;
        sync(player, "That edit was made in another dimension.");
        return false;
    }

    private static void set(World world, long packed, int stateId) {
        BlockPos pos = BlockPos.fromLong(packed);
        if (world.isBlockLoaded(pos)) world.setBlockState(pos, Block.getStateById(stateId), 2 | 16);
    }

    private static boolean checkVolume(EntityPlayerMP player, BlockPos min, BlockPos max) {
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (volume <= MAX_VOLUME) return true;
        sync(player, "Too large: " + fmt(volume) + " blocks (limit " + fmt(MAX_VOLUME) + ").");
        return false;
    }

    static void sync(EntityPlayerMP player, String message) {
        EditHistory h = EditHistory.of(player.getUniqueID());
        FormaNetwork.CHANNEL.sendTo(new MsgHistorySync(h.descriptions(), h.cursor, message), player);
    }

    static IBlockState stateOf(int id) {
        IBlockState s = Block.getStateById(id);
        return s == null ? AIR : s;
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static String fmt(long n) {
        return NumberFormat.getIntegerInstance(Locale.US).format(n);
    }

    private static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    private static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }
}
