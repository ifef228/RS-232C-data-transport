package ru.mgtu.bauman.rs232.datalink;

import ru.mgtu.bauman.rs232.physical.SerialPhysicalLayer;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Канальный уровень: кадрирование, надёжная доставка (ACK/Ret), очереди кадров.
 */
public class DataLinkLayer implements AutoCloseable {

    private final SerialPhysicalLayer physical;
    private int localAddr = 0x01;
    private int remoteAddr = 0x02;
    private int ackTimeoutMs = 3000;
    private int maxRetries = 8;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread parserThread;

    private final BlockingQueue<Frame> appQueue = new LinkedBlockingQueue<>(1024);
    private final BlockingQueue<Frame> ackQueue = new LinkedBlockingQueue<>(256);

    public DataLinkLayer(SerialPhysicalLayer physical) {
        this.physical = physical;
    }

    public void setAddresses(int local, int remote) {
        this.localAddr = local & 0xFF;
        this.remoteAddr = remote & 0xFF;
    }

    public int getLocalAddr() {
        return localAddr;
    }

    public int getRemoteAddr() {
        return remoteAddr;
    }

    public void setAckTimeoutMs(int ackTimeoutMs) {
        this.ackTimeoutMs = ackTimeoutMs;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public void start() {
        if (running.get()) {
            return;
        }
        running.set(true);
        parserThread = new Thread(this::parseLoop, "frame-parser");
        parserThread.setDaemon(true);
        parserThread.start();
    }

    private void parseLoop() {
        while (running.get() && physical.isOpen()) {
            try {
                Frame f = readOneFrameFromWire();
                if (f == null) {
                    continue;
                }
                boolean forUs = f.dst == localAddr || f.dst == ProtocolConstants.ADDR_BROADCAST;
                if (!forUs) {
                    continue;
                }
                if (f.type == ProtocolConstants.FT_ACK) {
                    ackQueue.offer(f);
                } else {
                    appQueue.offer(f);
                }
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
            }
        }
    }

    private Frame readOneFrameFromWire() throws IOException, InterruptedException {
        while (true) {
            int b = physical.readByte(500);
            if (!running.get()) {
                return null;
            }
            if (b < 0) {
                continue;
            }
            if (b != WireCodec.START) {
                continue;
            }
            int dst = readByteBlocking();
            int src = readByteBlocking();
            int type = readByteBlocking();
            int seq = readByteBlocking();
            int lenHi = readByteBlocking();
            int lenLo = readByteBlocking();
            int len = ((lenHi & 0xFF) << 8) | (lenLo & 0xFF);
            if (len < 0 || len > 65535) {
                continue;
            }
            if (len > 64 * 1024) {
                continue;
            }
            byte[] payload = WireCodec.unescapeFromStream(this::readByteBlocking, len);
            int end = readByteBlocking();
            if (end != WireCodec.START) {
                continue;
            }
            return new Frame(dst, src, type, seq, payload);
        }
    }

    private int readByteBlocking() throws IOException {
        try {
            while (running.get()) {
                int b = physical.readByte(1000);
                if (b >= 0) {
                    return b;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new IOException("Чтение прервано");
    }

    public void sendRawFrame(Frame f) throws IOException {
        physical.writeBytes(WireCodec.encode(f));
    }

    /**
     * Отправка с ожиданием ACK (полезная нагрузка ACK — один байт с номером seq).
     */
    public void sendReliable(Frame f) throws IOException, InterruptedException {
        java.util.ArrayList<Frame> stray = new java.util.ArrayList<>();
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            physical.writeBytes(WireCodec.encode(f));
            long deadline = System.currentTimeMillis() + ackTimeoutMs;
            while (System.currentTimeMillis() < deadline) {
                Frame ack = ackQueue.poll(100, TimeUnit.MILLISECONDS);
                if (ack == null) {
                    continue;
                }
                if (ack.type == ProtocolConstants.FT_ACK && ack.payload.length >= 1
                        && (ack.payload[0] & 0xFF) == (f.seq & 0xFF)) {
                    for (Frame x : stray) {
                        ackQueue.offer(x);
                    }
                    return;
                }
                stray.add(ack);
            }
            for (Frame x : stray) {
                ackQueue.offer(x);
            }
            stray.clear();
        }
        throw new IOException("Нет подтверждения ACK для seq=" + f.seq);
    }

    public void sendAck(int seq) throws IOException {
        byte[] pl = new byte[]{(byte) (seq & 0xFF)};
        Frame ack = new Frame(remoteAddr, localAddr, ProtocolConstants.FT_ACK, seq, pl);
        sendRawFrame(ack);
    }

    public void sendRet(int seq) throws IOException {
        byte[] pl = new byte[]{(byte) (seq & 0xFF)};
        Frame ret = new Frame(remoteAddr, localAddr, ProtocolConstants.FT_RET, seq, pl);
        sendRawFrame(ret);
    }

    public void sendNotify(String message) throws IOException {
        byte[] utf8 = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Frame n = new Frame(remoteAddr, localAddr, ProtocolConstants.FT_NOTIFY, 0, utf8);
        sendRawFrame(n);
    }

    /**
     * Кадры приложения (без ACK, которые уходят в ackQueue).
     */
    public Frame pollAppFrame(long timeoutMs) throws InterruptedException {
        return appQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running.set(false);
        if (parserThread != null) {
            parserThread.interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
