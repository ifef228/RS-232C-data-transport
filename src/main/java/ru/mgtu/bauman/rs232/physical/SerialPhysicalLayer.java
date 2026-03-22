package ru.mgtu.bauman.rs232.physical;

import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Физический уровень: параметры COM-порта, установление и разъединение канала, байтовый обмен.
 */
public class SerialPhysicalLayer implements AutoCloseable {

    private SerialPort port;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final BlockingQueue<Integer> byteQueue = new LinkedBlockingQueue<>(65536);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread readerThread;

    public void open(String portName, int baudRate, int dataBits, int stopBits, int parity) throws IOException {
        close();
        SerialPort p = SerialPort.getCommPort(portName);
        p.setBaudRate(baudRate);
        p.setNumDataBits(dataBits);
        p.setNumStopBits(stopBits);
        p.setParity(parity);
        p.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        p.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
        if (!p.openPort()) {
            throw new IOException("Не удалось открыть порт: " + portName);
        }
        this.port = p;
        this.inputStream = p.getInputStream();
        this.outputStream = p.getOutputStream();
        running.set(true);
        readerThread = new Thread(this::readLoop, "serial-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        byte[] buf = new byte[4096];
        while (running.get() && port != null && port.isOpen()) {
            try {
                int n = inputStream.read(buf);
                if (n > 0) {
                    for (int i = 0; i < n; i++) {
                        byteQueue.offer(buf[i] & 0xFF);
                    }
                }
            } catch (IOException e) {
                break;
            }
        }
    }

    public int readByte(long timeoutMs) throws InterruptedException {
        Integer b = byteQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (b == null) {
            return -1;
        }
        return b;
    }

    public void writeBytes(byte[] data) throws IOException {
        if (outputStream == null) {
            throw new IOException("Порт не открыт");
        }
        outputStream.write(data);
        outputStream.flush();
    }

    public boolean isOpen() {
        return port != null && port.isOpen();
    }

    @Override
    public void close() {
        running.set(false);
        if (port != null) {
            port.closePort();
            port = null;
        }
        inputStream = null;
        outputStream = null;
        byteQueue.clear();
    }

    public static int parityFromUi(int index) {
        return switch (index) {
            case 1 -> SerialPort.ODD_PARITY;
            case 2 -> SerialPort.EVEN_PARITY;
            case 3 -> SerialPort.MARK_PARITY;
            case 4 -> SerialPort.SPACE_PARITY;
            default -> SerialPort.NO_PARITY;
        };
    }

    public static int stopBitsFromUi(int index) {
        return switch (index) {
            case 1 -> SerialPort.TWO_STOP_BITS;
            default -> SerialPort.ONE_STOP_BIT;
        };
    }
}
