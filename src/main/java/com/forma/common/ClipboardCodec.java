package com.forma.common;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Compact compressed form of a {@link ClipboardData}, used to upload schematics to the server. */
public final class ClipboardCodec {
    private ClipboardCodec() {}

    public static byte[] encode(ClipboardData d) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeInt(d.sx);
            out.writeInt(d.sy);
            out.writeInt(d.sz);
            for (int s : d.states) out.writeInt(s);
            out.writeInt(d.tiles.size());
            for (Map.Entry<Integer, NBTTagCompound> e : d.tiles.entrySet()) {
                out.writeInt(e.getKey());
                CompressedStreamTools.write(e.getValue(), out);
            }
        }
        return bytes.toByteArray();
    }

    /** Throws if the data is malformed or larger than maxVolume. */
    public static ClipboardData decode(byte[] data, long maxVolume) throws IOException {
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            int sx = in.readInt(), sy = in.readInt(), sz = in.readInt();
            if (sx <= 0 || sy <= 0 || sz <= 0 || (long) sx * sy * sz > maxVolume) throw new IOException("bad size");
            ClipboardData d = new ClipboardData(sx, sy, sz);
            for (int i = 0; i < d.states.length; i++) {
                d.states[i] = in.readInt();
                if (d.states[i] != 0) d.nonAir++;
            }
            int tiles = in.readInt();
            if (tiles < 0 || tiles > d.states.length) throw new IOException("bad tile count");
            for (int i = 0; i < tiles; i++) {
                int index = in.readInt();
                NBTTagCompound nbt = CompressedStreamTools.read(in, new NBTSizeTracker(2_097_152L));
                if (index >= 0 && index < d.states.length) d.tiles.put(index, nbt);
            }
            return d;
        }
    }
}
