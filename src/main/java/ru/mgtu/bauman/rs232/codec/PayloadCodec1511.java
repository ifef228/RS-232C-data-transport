package ru.mgtu.bauman.rs232.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Упаковка потока байтов в блоки по 11 бит, кодирование (15,11) и обратно.
 */
public final class PayloadCodec1511 {

    private PayloadCodec1511() {
    }

    public static byte[] encodeBytes(byte[] plain) {
        if (plain.length == 0) {
            return new byte[0];
        }
        BitWriter w = new BitWriter();
        BitReader br = new BitReader(plain);
        int totalBits = plain.length * 8;
        int consumed = 0;
        while (consumed + 11 <= totalBits) {
            int chunk = br.readBits(11);
            consumed += 11;
            int cw = CyclicCode1511.encode(chunk);
            w.writeBits(cw, 15);
        }
        int remaining = totalBits - consumed;
        if (remaining > 0) {
            int last = br.readBits(remaining);
            int padded = last << (11 - remaining);
            int cw = CyclicCode1511.encode(padded & 0x7FF);
            w.writeBits(cw, 15);
        }
        return w.toByteArray();
    }

    public static byte[] decodeBytes(byte[] encoded, int originalByteLength) throws IOException {
        if (originalByteLength == 0) {
            return new byte[0];
        }
        int bitsNeeded = originalByteLength * 8;
        BitReader br = new BitReader(encoded);
        BitWriter outBits = new BitWriter();
        int bitsWritten = 0;
        while (bitsWritten < bitsNeeded) {
            int cw = br.readBits(15);
            if (CyclicCode1511.syndrome(cw) != 0) {
                throw new IOException("Ошибка [15,11]: ненулевой синдром");
            }
            int eleven = CyclicCode1511.dataBits(cw);
            int take = Math.min(11, bitsNeeded - bitsWritten);
            int val = (eleven >> (11 - take)) & ((1 << take) - 1);
            outBits.writeBits(val, take);
            bitsWritten += take;
        }
        byte[] raw = outBits.toByteArray();
        return Arrays.copyOf(raw, originalByteLength);
    }
}

final class BitWriter {
    private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    private int buf;
    private int n;

    void writeBits(int value, int bits) {
        for (int i = bits - 1; i >= 0; i--) {
            buf = (buf << 1) | ((value >> i) & 1);
            n++;
            if (n == 8) {
                bos.write(buf);
                buf = 0;
                n = 0;
            }
        }
    }

    byte[] toByteArray() {
        if (n > 0) {
            buf <<= (8 - n);
            bos.write(buf);
        }
        return bos.toByteArray();
    }
}

final class BitReader {
    private final byte[] data;
    private int bytePos;
    private int bitBuf;
    private int bitsInBuf;

    BitReader(byte[] data) {
        this.data = data;
    }

    int readBits(int need) {
        int v = 0;
        for (int i = 0; i < need; i++) {
            if (bitsInBuf == 0) {
                if (bytePos >= data.length) {
                    throw new IllegalStateException("Недостаточно данных для чтения бит");
                }
                bitBuf = data[bytePos++] & 0xFF;
                bitsInBuf = 8;
            }
            v = (v << 1) | ((bitBuf >> (bitsInBuf - 1)) & 1);
            bitsInBuf--;
        }
        return v;
    }
}
