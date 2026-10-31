package com.forma.server;

import com.forma.common.Mask;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Applies block changes for one request: honours the mask and records everything for undo. */
final class EditSession {
    /** Send to clients, skip neighbour updates (keeps sand/water from collapsing mid-edit). */
    private static final int FLAGS = 2 | 16;

    final World world;
    private final Mask mask;
    private final EditHistory.Change change;
    private final boolean continuing;

    private EditSession(EntityPlayerMP player, Mask mask, EditHistory.Change change, boolean continuing) {
        this.world = player.world;
        this.mask = mask == null || mask.isEmpty() ? null : mask;
        this.change = change;
        this.continuing = continuing;
    }

    static EditSession create(EntityPlayerMP player, Mask mask, boolean dedupe) {
        return new EditSession(player, mask, new EditHistory.Change(player.world.provider.getDimension(), dedupe), false);
    }

    /** Brush strokes: every application with the same stroke id merges into one history entry. */
    static EditSession forStroke(EntityPlayerMP player, Mask mask, int strokeId) {
        EditHistory.Change top = EditHistory.of(player.getUniqueID()).top();
        if (strokeId != 0 && top != null && top.strokeId == strokeId && top.index != null
                && top.dimension == player.world.provider.getDimension()) {
            return new EditSession(player, mask, top, true);
        }
        EditHistory.Change c = new EditHistory.Change(player.world.provider.getDimension(), true);
        c.strokeId = strokeId;
        return new EditSession(player, mask, c, false);
    }

    IBlockState get(BlockPos p) {
        return world.getBlockState(p);
    }

    /** Returns true when the position was writable (loaded, in height range, passed the mask). */
    boolean set(BlockPos pos, IBlockState state) {
        if (pos.getY() < 0 || pos.getY() >= world.getHeight() || !world.isBlockLoaded(pos)) return false;
        if (mask != null && !mask.test(world, pos)) return false;
        IBlockState old = world.getBlockState(pos);
        if (old != state) {
            change.record(pos.toLong(), Block.getStateId(old), Block.getStateId(state));
            world.setBlockState(pos, state, FLAGS);
        }
        return true;
    }

    int size() {
        return change.size;
    }

    /** Pushes the change into history and reports to the player. Quiet sessions don't spam the status line. */
    void finish(EntityPlayerMP player, String verb, boolean quiet) {
        if (change.size == 0) {
            if (!quiet) EditOperations.sync(player, "Nothing changed.");
            return;
        }
        change.description = verb + " " + EditOperations.fmt(change.size) + " blocks";
        if (!continuing) EditHistory.of(player.getUniqueID()).push(change);
        EditOperations.sync(player, quiet ? "" : change.description);
    }
}
