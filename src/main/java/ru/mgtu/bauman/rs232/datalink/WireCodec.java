package ru.mgtu.bauman.rs232.datalink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Кадр: 0xFF | DST | SRC | TYPE | SEQ | LEN_HI | LEN_LO | payload* | 0xFF
 * В payload экранирование: 0xFF → 0xFE 0x01, 0xFE → 0xFE 0x02.
 */
public final class WireCodec {

    public static final int START = 0xFF;
    public static final int ESC = 0xFE;
    public static final int ESC_FF = 0x01;
    public static final int ESC_FE = 0x02;

    private WireCodec() {
    }

    public static byte[] encode(Frame f) {
        byte[] raw = f.payload;
        ByteArrayOutputStream esc = new ByteArrayOutputStream(raw.length + 16);
        escape(raw, esc);
        byte[] body = esc.toByteArray();
        int len = raw.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 + body.length + 1);
        out.write(START);
        out.write(f.dst);
        out.write(f.src);
        out.write(f.type);
        out.write(f.seq);
        out.write((len >> 8) & 0xFF);
        out.write(len & 0xFF);
        try {
            out.write(body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        out.write(START);
        return out.toByteArray();
    }

    private static void escape(byte[] raw, ByteArrayOutputStream out) {
        for (byte b : raw) {
            int v = b & 0xFF;
            if (v == 0xFF) {
                out.write(ESC);
                out.write(ESC_FF);
            } else if (v == ESC) {
                out.write(ESC);
                out.write(ESC_FE);
            } else {
                out.write(v);
            }
        }
    }

    public static byte[] unescapeFromStream(FrameStreamReader r, int rawLen) throws IOException {
        ByteArrayOutputStream acc = new ByteArrayOutputStream(rawLen);
        while (acc.size() < rawLen) {
            int b = r.read();
            if (b < 0) {
                throw new IOException("Обрыв потока при разэкранировании");
            }
            if (b == ESC) {
                int b2 = r.read();
                if (b2 == ESC_FF) {
                    acc.write(0xFF);
                } else if (b2 == ESC_FE) {
                    acc.write(ESC);
                } else {
                    throw new IOException("Неверная escape-последовательность");
                }
            } else {
                acc.write(b);
            }
        }
        return acc.toByteArray();
    }

    /**
     * Поток байтов кадра после заголовка (до конечного 0xFF).
     */
    public interface FrameStreamReader {
        int read() throws IOException;
    }

    public static boolean samePayload(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }
}
