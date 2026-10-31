package com.forma.server;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player undo/redo stacks, kept on the server. */
public final class EditHistory {
    private static final int MAX_ENTRIES = 50;
    private static final Map<UUID, EditHistory> BY_PLAYER = new HashMap<>();

    final List<Change> entries = new ArrayList<>();
    int cursor; // number of applied entries

    public static EditHistory of(UUID player) {
        return BY_PLAYER.computeIfAbsent(player, k -> new EditHistory());
    }

    public static void clear(UUID player) {
        BY_PLAYER.remove(player);
    }

    void push(Change change) {
        while (entries.size() > cursor) entries.remove(entries.size() - 1);
        entries.add(change);
        if (entries.size() > MAX_ENTRIES) entries.remove(0);
        cursor = entries.size();
    }

    /** The newest applied entry, if nothing has been undone since. */
    Change top() {
        return cursor > 0 && cursor == entries.size() ? entries.get(cursor - 1) : null;
    }

    List<String> descriptions() {
        List<String> out = new ArrayList<>(entries.size());
        for (Change c : entries) out.add(c.description);
        return out;
    }

    /**
     * One undoable edit: every touched position with its state before and after.
     * With an index, a position touched twice keeps its first "before" (needed for brush strokes and move-in-place).
     */
    static final class Change {
        final int dimension;
        final Long2IntOpenHashMap index;
        int strokeId;
        String description = "";
        long[] positions = new long[256];
        int[] before = new int[256];
        int[] after = new int[256];
        int size;

        Change(int dimension, boolean dedupe) {
            this.dimension = dimension;
            if (dedupe) {
                index = new Long2IntOpenHashMap();
                index.defaultReturnValue(-1);
            } else {
                index = null;
            }
        }

        void record(long pos, int beforeId, int afterId) {
            if (index != null) {
                int i = index.get(pos);
                if (i >= 0) {
                    after[i] = afterId;
                    return;
                }
                index.put(pos, size);
            }
            if (size == positions.length) {
                int n = size * 2;
                positions = Arrays.copyOf(positions, n);
                before = Arrays.copyOf(before, n);
                after = Arrays.copyOf(after, n);
            }
            positions[size] = pos;
            before[size] = beforeId;
            after[size] = afterId;
            size++;
        }
    }
}
