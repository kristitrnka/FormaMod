package com.forma.common;

/** Small deterministic hash + 3D value noise used by the terrain brushes. */
public final class Noise {
    private Noise() {}

    public static int hash(int x, int y, int z, int seed) {
        int h = seed * 0x27d4eb2d;
        h ^= x * 0x165667b1;
        h ^= y * 0x2c1b3c6d;
        h ^= z * 0x297a2d39;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    /** Uniform in [0, 1). */
    public static float rand(int x, int y, int z, int seed) {
        return (hash(x, y, z, seed) >>> 8) / (float) (1 << 24);
    }

    /** Smooth value noise in [-1, 1]. */
    public static double value(double x, double y, double z, int seed) {
        int x0 = floor(x), y0 = floor(y), z0 = floor(z);
        double fx = fade(x - x0), fy = fade(y - y0), fz = fade(z - z0);
        double c000 = lat(x0, y0, z0, seed), c100 = lat(x0 + 1, y0, z0, seed);
        double c010 = lat(x0, y0 + 1, z0, seed), c110 = lat(x0 + 1, y0 + 1, z0, seed);
        double c001 = lat(x0, y0, z0 + 1, seed), c101 = lat(x0 + 1, y0, z0 + 1, seed);
        double c011 = lat(x0, y0 + 1, z0 + 1, seed), c111 = lat(x0 + 1, y0 + 1, z0 + 1, seed);
        double x00 = lerp(c000, c100, fx), x10 = lerp(c010, c110, fx);
        double x01 = lerp(c001, c101, fx), x11 = lerp(c011, c111, fx);
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
    }

    /** Two octaves for a less blobby look. */
    public static double fractal(double x, double y, double z, int seed) {
        return value(x, y, z, seed) * 0.7 + value(x * 2.1, y * 2.1, z * 2.1, seed + 17) * 0.3;
    }

    private static double lat(int x, int y, int z, int seed) {
        return rand(x, y, z, seed) * 2.0 - 1.0;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double fade(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
