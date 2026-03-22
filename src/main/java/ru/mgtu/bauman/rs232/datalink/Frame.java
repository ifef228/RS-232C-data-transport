package ru.mgtu.bauman.rs232.datalink;

import java.util.Arrays;

public final class Frame {
    public final int dst;
    public final int src;
    public final int type;
    public final int seq;
    public final byte[] payload;

    public Frame(int dst, int src, int type, int seq, byte[] payload) {
        this.dst = dst & 0xFF;
        this.src = src & 0xFF;
        this.type = type & 0xFF;
        this.seq = seq & 0xFF;
        this.payload = payload != null ? payload : new byte[0];
    }

    @Override
    public String toString() {
        return "Frame{dst=" + dst + ", src=" + src + ", type=" + type + ", seq=" + seq + ", len=" + payload.length + "}";
    }

    public boolean contentEquals(Frame o) {
        if (o == null) {
            return false;
        }
        return dst == o.dst && src == o.src && type == o.type && seq == o.seq && Arrays.equals(payload, o.payload);
    }
}
