package com.forma.client.tools;

import com.forma.client.EditorController;
import com.forma.client.ui.IconAtlas;
import com.forma.client.ui.Ui;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import java.util.ArrayDeque;

/** Click a block: selects the bounding box of everything connected to it (same block, or any non-air). */
public class MagicSelectTool extends Tool {
    private static final String[] MODES = {"Same Block", "Any Block"};
    private int mode;
    private boolean diagonal;
    private int limit = 200;

    public MagicSelectTool() {
        super("Magic Select", IconAtlas.MAGIC, "Select Connected");
    }

    @Override
    public void onPress(EditorController c, RayTraceResult hit) {
        if (hit == null) return;
        World w = Minecraft.getMinecraft().world;
        BlockPos start = hit.getBlockPos();
        Block block = w.getBlockState(start).getBlock();
        int minX = start.getX(), minY = start.getY(), minZ = start.getZ(), maxX = minX, maxY = minY, maxZ = minZ;
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.toLong());
        int max = limit * 1000, count = 0;
        long r2 = 256L * 256L;
        while (!queue.isEmpty() && count < max) {
            BlockPos p = queue.poll();
            count++;
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        int manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (manhattan == 0 || (!diagonal && manhattan > 1)) continue;
                        BlockPos n = p.add(dx, dy, dz);
                        if (n.getY() < 0 || n.getY() > 255 || n.distanceSq(start) > r2 || !w.isBlockLoaded(n)) continue;
                        if (!matches(w, n, block) || !seen.add(n.toLong())) continue;
                        queue.add(n);
                    }
        }
        c.selection.a = new BlockPos(minX, minY, minZ);
        c.selection.b = new BlockPos(maxX, maxY, maxZ);
        c.status("Selected " + EditorController.fmt(count) + " connected blocks" + (count >= max ? " (limit reached)" : ""));
    }

    private boolean matches(World w, BlockPos p, Block block) {
        return mode == 0 ? w.getBlockState(p).getBlock() == block : !w.isAirBlock(p);
    }

    @Override
    public void drawOptions(Ui ui, EditorController c) {
        ui.text("Match");
        mode = ui.combo(MODES, mode, ui.contentWidth());
        diagonal = ui.checkbox("Diagonal neighbours", diagonal);
        ui.text("Limit (thousands)");
        limit = ui.slider("magic.limit", limit, 10, 1000, ui.contentWidth());
        ui.text("Selects the bounding box", Ui.TEXT_DIM);
    }
}
