package ru.mgtu.bauman.rs232.app;

import ru.mgtu.bauman.rs232.codec.PayloadCodec1511;
import ru.mgtu.bauman.rs232.datalink.DataLinkLayer;
import ru.mgtu.bauman.rs232.datalink.Frame;
import ru.mgtu.bauman.rs232.datalink.ProtocolConstants;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Прикладной сценарий: передача текстового файла с кодированием [15,11] в поле данных кадров {@code FT_I}.
 */
public final class FileTransferService {

    /** Максимальный размер полезной нагрузки в одном кадре (после кодирования — куски файла). */
    public static final int MAX_CHUNK = 512;

    private FileTransferService() {
    }

    public static void sendTextFile(Path file, DataLinkLayer link, Consumer<String> log)
            throws IOException, InterruptedException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Не файл: " + file);
        }
        byte[] plain = Files.readAllBytes(file);
        String name = file.getFileName().toString();
        byte[] meta = buildMeta(name, plain.length);
        AtomicInteger seq = new AtomicInteger(1);

        Frame linkFrame = new Frame(link.getRemoteAddr(), link.getLocalAddr(), ProtocolConstants.FT_LINK, seq.getAndIncrement(), new byte[0]);
        log.accept("Установление логического соединения (Link)…");
        link.sendReliable(linkFrame);

        Frame metaFrame = new Frame(link.getRemoteAddr(), link.getLocalAddr(), ProtocolConstants.FT_META, seq.getAndIncrement(), meta);
        log.accept("Отправка метаданных (имя, размер)…");
        link.sendReliable(metaFrame);

        byte[] encoded = PayloadCodec1511.encodeBytes(plain);
        int off = 0;
        while (off < encoded.length) {
            int n = Math.min(MAX_CHUNK, encoded.length - off);
            byte[] chunk = new byte[n];
            System.arraycopy(encoded, off, chunk, 0, n);
            off += n;
            Frame data = new Frame(link.getRemoteAddr(), link.getLocalAddr(), ProtocolConstants.FT_I, seq.getAndIncrement(), chunk);
            link.sendReliable(data);
            log.accept("Передано " + off + " / " + encoded.length + " байт (закодированных)");
        }

        Frame end = new Frame(link.getRemoteAddr(), link.getLocalAddr(), ProtocolConstants.FT_END, seq.getAndIncrement(), new byte[0]);
        log.accept("Завершение передачи (END)…");
        link.sendReliable(end);

        Frame uplink = new Frame(link.getRemoteAddr(), link.getLocalAddr(), ProtocolConstants.FT_UPLINK, seq.getAndIncrement(), new byte[0]);
        log.accept("Разрыв логического соединения (Uplink)…");
        link.sendReliable(uplink);
        log.accept("Готово.");
    }

    private static byte[] buildMeta(String filename, int plainSize) {
        byte[] fn = filename.getBytes(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(fn.length + 1 + 4);
        bb.put(fn);
        bb.put((byte) 0);
        bb.putInt(plainSize);
        return bb.array();
    }

    public static void receiveLoop(DataLinkLayer link, Path outputDir, Consumer<String> log, Consumer<String> onPeerNotify)
            throws IOException, InterruptedException {
        Files.createDirectories(outputDir);
        ByteArrayOutputBuffer acc = new ByteArrayOutputBuffer();
        String pendingName = null;
        int pendingSize = -1;
        boolean inTransfer = false;

        while (true) {
            Frame f = link.pollAppFrame(60_000);
            if (f == null) {
                continue;
            }
            if (f.type == ProtocolConstants.FT_NOTIFY) {
                String msg = new String(f.payload, StandardCharsets.UTF_8);
                if (onPeerNotify != null) {
                    onPeerNotify.accept(msg);
                }
                link.sendAck(f.seq);
                continue;
            }
            if (f.type == ProtocolConstants.FT_LINK) {
                link.sendAck(f.seq);
                log.accept("Принят Link, логическое соединение установлено.");
                continue;
            }
            if (f.type == ProtocolConstants.FT_META) {
                byte[] p = f.payload;
                int z = 0;
                for (; z < p.length; z++) {
                    if (p[z] == 0) {
                        break;
                    }
                }
                if (z > p.length - 5) {
                    log.accept("Неверный кадр META");
                    link.sendAck(f.seq);
                    continue;
                }
                pendingName = new String(p, 0, z, StandardCharsets.UTF_8);
                ByteBuffer bb = ByteBuffer.wrap(p, z + 1, 4);
                pendingSize = bb.getInt();
                acc.reset();
                inTransfer = true;
                link.sendAck(f.seq);
                log.accept("Метаданные: файл \"" + pendingName + "\", размер " + pendingSize + " байт.");
                continue;
            }
            if (f.type == ProtocolConstants.FT_I && inTransfer) {
                acc.write(f.payload);
                link.sendAck(f.seq);
                continue;
            }
            if (f.type == ProtocolConstants.FT_END && inTransfer) {
                link.sendAck(f.seq);
                log.accept("Конец передачи, декодирование [15,11]…");
                byte[] encoded = acc.toByteArray();
                try {
                    byte[] plain = PayloadCodec1511.decodeBytes(encoded, pendingSize);
                    Path out = outputDir.resolve(pendingName);
                    Files.write(out, plain);
                    log.accept("Файл сохранён: " + out.toAbsolutePath());
                } catch (IOException e) {
                    log.accept("Ошибка декодирования: " + e.getMessage());
                    link.sendNotify("Ошибка декодирования на приёмнике: " + e.getMessage());
                }
                inTransfer = false;
                pendingName = null;
                pendingSize = -1;
                continue;
            }
            if (f.type == ProtocolConstants.FT_UPLINK) {
                link.sendAck(f.seq);
                log.accept("Принят Uplink, соединение разорвано.");
                return;
            }
            if (f.type == ProtocolConstants.FT_RET) {
                link.sendAck(f.seq);
                continue;
            }
            link.sendAck(f.seq);
        }
    }

    private static final class ByteArrayOutputBuffer {
        private byte[] buf = new byte[8192];
        private int len;

        void reset() {
            len = 0;
        }

        void write(byte[] data) {
            if (len + data.length > buf.length) {
                byte[] nb = new byte[(len + data.length) * 2];
                System.arraycopy(buf, 0, nb, 0, len);
                buf = nb;
            }
            System.arraycopy(data, 0, buf, len, data.length);
            len += data.length;
        }

        byte[] toByteArray() {
            byte[] r = new byte[len];
            System.arraycopy(buf, 0, r, 0, len);
            return r;
        }
    }
}
