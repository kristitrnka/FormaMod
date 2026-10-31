package com.forma.server;

import com.forma.common.Noise;
import com.forma.network.MsgEdit;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Terrain brushes. Each call is one "dab" of a stroke; the client sends dabs repeatedly while RMB is held.
 * Voxel brushes compute all decisions from a snapshot before writing, so a dab never reacts to its own changes.
 */
final class Brushes {
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();
    /** Fixed so repeated dabs of the noise brush agree with each other. */
    private static final int NOISE_SEED = 0x5EED;

    private Brushes() {}

    static String name(byte brush) {
        switch (brush) {
            case MsgEdit.BRUSH_SCULPT: return "Sculpt";
            case MsgEdit.BRUSH_SMOOTH: return "Smooth";
            case MsgEdit.BRUSH_FLATTEN: return "Flatten";
            case MsgEdit.BRUSH_NOISE: return "Noise";
            case MsgEdit.BRUSH_PAINT: return "Paint";
            case MsgEdit.BRUSH_MELT: return "Melt";
            case MsgEdit.BRUSH_SCATTER: return "Scatter";
            default: return "Brush";
        }
    }

    static void apply(EditSession s, MsgEdit m, IBlockState active) {
        BlockPos c = m.a;
        int r = EditOperations.clamp(m.radius, 1, EditOperations.MAX_BRUSH_RADIUS);
        float strength = EditOperations.clamp(m.strength, 1, 100) / 100f;
        int seed = m.strokeId * 31 + m.seq;
        switch (m.brush) {
            case MsgEdit.BRUSH_SCULPT:
                sculpt(s, c, r, strength, m.mode == 1, seed);
                break;
            case MsgEdit.BRUSH_SMOOTH:
                smooth(s, c, r, strength);
                break;
            case MsgEdit.BRUSH_FLATTEN:
                flatten(s, c, r, m.mode);
                break;
            case MsgEdit.BRUSH_NOISE:
                noise(s, c, r, strength, Math.max(1, m.param));
                break;
            case MsgEdit.BRUSH_PAINT:
                paint(s, c, r, strength, m.mode == 1, active, seed);
                break;
            case MsgEdit.BRUSH_MELT:
                melt(s, c, r, strength, m.mode == 1, seed);
                break;
            case MsgEdit.BRUSH_SCATTER: {
                List<IBlockState> palette = new ArrayList<>();
                for (int id : m.palette) {
                    IBlockState st = EditOperations.stateOf(id);
                    if (st.getMaterial() != Material.AIR) palette.add(st);
                }
                if (palette.isEmpty()) palette.add(active);
                scatter(s, c, r, strength, m.mode == 1, palette, seed);
                break;
            }
            default:
                break;
        }
    }

    // ------------------------------------------------------------ heightfield brushes

    /** Raise or lower the surface with a smooth bump falloff, keeping the top material on top. */
    private static void sculpt(EditSession s, BlockPos c, int r, float strength, boolean lower, int seed) {
        World w = s.world;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) / r;
                if (d >= 1) continue;
                double fall = (1 - d * d) * (1 - d * d);
                double amount = strength * 3.0 * fall;
                int x = c.getX() + dx, z = c.getZ() + dz;
                int dh = (int) amount;
                if (Noise.rand(x, 0, z, seed) < amount - dh) dh++;
                if (dh == 0) continue;
                int top = findTop(w, x, z, c.getY() + r, c.getY() - r);
                if (top == Integer.MIN_VALUE) continue;
                BlockPos tp = new BlockPos(x, top, z);
                IBlockState topState = w.getBlockState(tp);
                IBlockState below = w.getBlockState(tp.down());
                IBlockState filler = solid(below) ? below : topState;
                if (!lower) {
                    for (int k = 1; k <= dh; k++) s.set(tp.up(k), k == dh ? topState : filler);
                    if (filler != topState) s.set(tp, filler);
                } else {
                    clearPlant(s, tp.up());
                    for (int k = 0; k < dh; k++) s.set(tp.down(k), AIR);
                    BlockPos newTop = tp.down(dh);
                    if (solid(w.getBlockState(newTop))) s.set(newTop, topState);
                }
            }
    }

    /** Level the area to the clicked block's height. mode 0: both, 1: cut only, 2: fill only. */
    private static void flatten(EditSession s, BlockPos c, int r, int mode) {
        World w = s.world;
        int py = c.getY();
        IBlockState refTop = w.getBlockState(c);
        if (!solid(refTop)) refTop = Blocks.GRASS.getDefaultState();
        IBlockState refBelow = w.getBlockState(c.down());
        IBlockState refFill = solid(refBelow) ? refBelow : refTop;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz >= (r + 0.5) * (r + 0.5)) continue;
                int x = c.getX() + dx, z = c.getZ() + dz;
                if (mode != 2) {
                    int colTop = findTop(w, x, z, py + r, py + 1);
                    IBlockState removedTop = colTop == Integer.MIN_VALUE ? null : w.getBlockState(new BlockPos(x, colTop, z));
                    for (int y = py + 1; y <= py + r; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (w.getBlockState(p).getMaterial() != Material.AIR) s.set(p, AIR);
                    }
                    // Keep grass (etc.) on the newly exposed surface.
                    BlockPos surface = new BlockPos(x, py, z);
                    if (removedTop != null && removedTop.getBlock() == Blocks.GRASS
                            && w.getBlockState(surface).getBlock() == Blocks.DIRT) s.set(surface, removedTop);
                }
                if (mode != 1) {
                    int colTop = findTop(w, x, z, py, py - r);
                    int from = colTop == Integer.MIN_VALUE ? py - r : colTop + 1;
                    for (int y = from; y <= py; y++) s.set(new BlockPos(x, y, z), y == py ? refTop : refFill);
                    if (colTop != Integer.MIN_VALUE && colTop < py) s.set(new BlockPos(x, colTop, z), refFill);
                }
            }
    }

    // ------------------------------------------------------------ voxel brushes

    /** Majority filter over the 3x3x3 neighbourhood; strength sets the number of passes. */
    private static void smooth(EditSession s, BlockPos c, int r, float strength) {
        World w = s.world;
        int passes = 1 + (int) (strength * 2.99f);
        for (int pass = 0; pass < passes; pass++) {
            List<BlockPos> remove = new ArrayList<>();
            List<BlockPos> add = new ArrayList<>();
            List<IBlockState> addStates = new ArrayList<>();
            for (BlockPos p : sphere(c, r)) {
                IBlockState here = w.getBlockState(p);
                boolean solidHere = solid(here);
                int count = 0;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++)
                            if (solid(w.getBlockState(p.add(dx, dy, dz)))) count++;
                if (solidHere && count <= 12) {
                    remove.add(p);
                } else if (!solidHere && count >= 15 && here.getMaterial().isReplaceable()) {
                    IBlockState fill = neighbourMaterial(w, p);
                    if (fill != null) {
                        add.add(p);
                        addStates.add(fill);
                    }
                }
            }
            for (BlockPos p : remove) s.set(p, AIR);
            for (int i = 0; i < add.size(); i++) s.set(add.get(i), addStates.get(i));
        }
    }

    /** Erode exposed blocks (melt) or grow into enclosed air (mode 1). */
    private static void melt(EditSession s, BlockPos c, int r, float strength, boolean grow, int seed) {
        World w = s.world;
        List<BlockPos> targets = new ArrayList<>();
        List<IBlockState> states = new ArrayList<>();
        double r2 = r * r;
        for (BlockPos p : sphere(c, r)) {
            double fall = 1 - p.distanceSq(c) / r2;
            if (Noise.rand(p.getX(), p.getY(), p.getZ(), seed) > strength * (0.4 + 0.6 * fall)) continue;
            IBlockState here = w.getBlockState(p);
            int solidN = 0;
            for (EnumFacing f : EnumFacing.VALUES) if (solid(w.getBlockState(p.offset(f)))) solidN++;
            if (!grow && solid(here) && solidN <= 3) {
                targets.add(p);
                states.add(AIR);
            } else if (grow && !solid(here) && here.getMaterial().isReplaceable() && solidN >= 3) {
                IBlockState fill = neighbourMaterial(w, p);
                if (fill != null) {
                    targets.add(p);
                    states.add(fill);
                }
            }
        }
        for (int i = 0; i < targets.size(); i++) s.set(targets.get(i), states.get(i));
    }

    /** Carves and adds lumps following 3D value noise. param = noise scale (wavelength in blocks). */
    private static void noise(EditSession s, BlockPos c, int r, float strength, int scale) {
        World w = s.world;
        double f = 1.0 / scale;
        double threshold = 0.55 * (1 - strength) + 0.08;
        double r2 = r * r;
        List<BlockPos> targets = new ArrayList<>();
        List<IBlockState> states = new ArrayList<>();
        for (BlockPos p : sphere(c, r)) {
            double fall = 1 - p.distanceSq(c) / r2;
            double n = Noise.fractal(p.getX() * f, p.getY() * f, p.getZ() * f, NOISE_SEED) * fall;
            IBlockState here = w.getBlockState(p);
            if (solid(here) && n < -threshold && exposed(w, p)) {
                targets.add(p);
                states.add(AIR);
            } else if (!solid(here) && here.getMaterial().isReplaceable() && n > threshold) {
                IBlockState fill = neighbourMaterial(w, p);
                if (fill != null) {
                    targets.add(p);
                    states.add(fill);
                }
            }
        }
        for (int i = 0; i < targets.size(); i++) s.set(targets.get(i), states.get(i));
    }

    /** Replace surface blocks with the active block. Strength = coverage (scatter). mode 1: top faces only. */
    private static void paint(EditSession s, BlockPos c, int r, float strength, boolean topOnly, IBlockState active, int seed) {
        World w = s.world;
        if (active.getMaterial() == Material.AIR) return;
        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos p : sphere(c, r)) {
            if (!solid(w.getBlockState(p))) continue;
            boolean surface = topOnly ? !solid(w.getBlockState(p.up())) : exposed(w, p);
            if (surface && Noise.rand(p.getX(), p.getY(), p.getZ(), seed) < strength) targets.add(p);
        }
        for (BlockPos p : targets) s.set(p, active);
    }

    /** Like paint, but each block gets a random pick from a palette (texture variation). */
    private static void scatter(EditSession s, BlockPos c, int r, float strength, boolean topOnly, List<IBlockState> palette, int seed) {
        World w = s.world;
        List<BlockPos> targets = new ArrayList<>();
        List<IBlockState> states = new ArrayList<>();
        for (BlockPos p : sphere(c, r)) {
            if (!solid(w.getBlockState(p))) continue;
            boolean surface = topOnly ? !solid(w.getBlockState(p.up())) : exposed(w, p);
            if (!surface || Noise.rand(p.getX(), p.getY(), p.getZ(), seed) >= strength) continue;
            int pick = (Noise.hash(p.getX(), p.getY(), p.getZ(), seed + 99) >>> 1) % palette.size();
            targets.add(p);
            states.add(palette.get(pick));
        }
        for (int i = 0; i < targets.size(); i++) s.set(targets.get(i), states.get(i));
    }

    // ------------------------------------------------------------ helpers

    private static boolean solid(IBlockState s) {
        return s.getMaterial().isSolid();
    }

    private static boolean exposed(World w, BlockPos p) {
        for (EnumFacing f : EnumFacing.VALUES) if (!solid(w.getBlockState(p.offset(f)))) return true;
        return false;
    }

    private static void clearPlant(EditSession s, BlockPos p) {
        IBlockState st = s.get(p);
        if (st.getMaterial() != Material.AIR && st.getMaterial().isReplaceable() && !st.getMaterial().isLiquid()) s.set(p, AIR);
    }

    /** Highest solid block in the column with a non-solid block above it, searching yHi down to yLo. */
    private static int findTop(World w, int x, int z, int yHi, int yLo) {
        yHi = Math.min(yHi, w.getHeight() - 2);
        yLo = Math.max(yLo, 0);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean aboveSolid = solid(w.getBlockState(p.setPos(x, yHi + 1, z)));
        for (int y = yHi; y >= yLo; y--) {
            boolean here = solid(w.getBlockState(p.setPos(x, y, z)));
            if (here && !aboveSolid) return y;
            aboveSolid = here;
        }
        return Integer.MIN_VALUE;
    }

    /** Material to fill a voxel with: the block below if solid, otherwise the most common solid neighbour. */
    private static IBlockState neighbourMaterial(World w, BlockPos p) {
        IBlockState below = w.getBlockState(p.down());
        if (solid(below)) return below;
        Map<IBlockState, Integer> counts = new HashMap<>();
        IBlockState best = null;
        int bestN = 0;
        for (EnumFacing f : EnumFacing.VALUES) {
            IBlockState st = w.getBlockState(p.offset(f));
            if (!solid(st)) continue;
            int n = counts.merge(st, 1, Integer::sum);
            if (n > bestN) {
                bestN = n;
                best = st;
            }
        }
        return best;
    }

    private static List<BlockPos> sphere(BlockPos c, int r) {
        List<BlockPos> out = new ArrayList<>();
        double lim = (r + 0.5) * (r + 0.5);
        for (int dy = -r; dy <= r; dy++) {
            int y = c.getY() + dy;
            if (y < 0 || y > 255) continue;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    if (dx * dx + dy * dy + dz * dz < lim) out.add(new BlockPos(c.getX() + dx, y, c.getZ() + dz));
        }
        return out;
    }
}
