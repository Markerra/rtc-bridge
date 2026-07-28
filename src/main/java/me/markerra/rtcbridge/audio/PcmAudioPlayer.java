package me.markerra.rtcbridge.audio;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.sound.sampled.*;

public final class PcmAudioPlayer implements AutoCloseable {

    // === LOW LATENCY CONFIG ===
    // Уменьшаем системный буфер аудиокарты с 400 мс до ~60 мс
    private static final int LINE_BUFFER_FRAMES = 3;
    // Кольцевой буфер в ОЗУ сохраняем на ~1 сек на случай всплесков сети
    private static final int RING_BUFFER_FRAMES = 50;
    // Начинаем воспроизведение сразу же, как появился 1 кадр (~20 мс)
    private static final int MIN_WRITE_FRAMES = 1;
    // Пишем в драйвер маленькими порциями (~40 мс), чтобы не раздувать очередь
    private static final int MAX_WRITE_FRAMES = 2;

    private final AudioFormat bridgeFormat;
    private final SourceDataLine line;
    private final ByteRingBuffer ring;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong droppedFrames = new AtomicLong();
    private final AtomicLong underruns = new AtomicLong();

    private final Thread playbackThread;

    public PcmAudioPlayer(AudioFormat bridgeFormat) throws LineUnavailableException {
        if (!bridgeFormat.isSupported()) {
            throw new IllegalArgumentException("Unsupported format");
        }

        this.bridgeFormat = bridgeFormat;

        javax.sound.sampled.AudioFormat javaFormat =
                new javax.sound.sampled.AudioFormat(
                        bridgeFormat.sampleRate(),
                        bridgeFormat.bitsPerSample(),
                        bridgeFormat.channels(),
                        true,
                        false
                );

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, javaFormat);

        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException();
        }

        line = (SourceDataLine) AudioSystem.getLine(info);

        // Маленький аппаратный буфер драйвера (~60 мс вместо 400 мс)
        line.open(javaFormat, bridgeFormat.expectedFrameBytes() * LINE_BUFFER_FRAMES);
        line.start();

        ring = new ByteRingBuffer(bridgeFormat.expectedFrameBytes() * RING_BUFFER_FRAMES);

        playbackThread = new Thread(this::playLoop, "pcm-player");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    public void enqueue(byte[] frame) {
        if (frame.length != bridgeFormat.expectedFrameBytes()) {
            return;
        }

        synchronized (ring) {
            if (!ring.write(frame)) {
                // Вытесняем старый кадр при переполнении
                ring.discard(frame.length);
                if (!ring.write(frame)) {
                    droppedFrames.incrementAndGet();
                }
                droppedFrames.incrementAndGet();
            }
            ring.notifyAll();
        }
    }

    private void playLoop() {
        int frameBytes = bridgeFormat.expectedFrameBytes();
        byte[] temp = new byte[frameBytes * MAX_WRITE_FRAMES];
        byte[] silence = new byte[frameBytes];

        while (running.get()) {
            int toRead = 0;

            // 1. КОПИРУЕМ ДАННЫЕ ИЗ БУФЕРА ПОД ЗАМКОМ (операция занимает микросекунды)
            synchronized (ring) {
                while (ring.available() < frameBytes * MIN_WRITE_FRAMES && running.get()) {
                    try {
                        ring.wait(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                if (!running.get()) {
                    break;
                }

                int available = ring.available();
                toRead = Math.min(available, temp.length);
                toRead -= toRead % frameBytes;

                if (toRead > 0) {
                    ring.read(temp, toRead);
                }
            } // <--- МОНИТОР ОСВОБОЖДЕН! Метод enqueue() больше не блокируется.

            // 2. БЛОКИРУЮЩИЙ ВЫВОД НА АУДИОКАРТУ ВЫПОЛНЯЕМ СНАРУЖИ SYNCHRONIZED
            if (toRead > 0) {
                line.write(temp, 0, toRead);
            } else {
                underruns.incrementAndGet();
                line.write(silence, 0, silence.length);
            }
        }
    }

    public long getDroppedFrames() {
        return droppedFrames.get();
    }

    public long getUnderruns() {
        return underruns.get();
    }

    public void reset() {
        synchronized (ring) {
            ring.clear();
        }
        // Сброс аппаратной очереди вынесен из-под монитора
        line.flush();
    }

    @Override
    public void close() {
        if (!running.getAndSet(false)) {
            return;
        }

        synchronized (ring) {
            ring.notifyAll();
        }

        try {
            playbackThread.join();
        } catch (InterruptedException ignored) {}

        line.flush();
        line.stop();
        line.close();
    }

    private static final class ByteRingBuffer {
        private final byte[] buffer;
        private int read;
        private int write;
        private int size;

        ByteRingBuffer(int capacity) {
            buffer = new byte[capacity];
        }

        int available() {
            return size;
        }

        boolean write(byte[] src) {
            if (src.length > buffer.length - size) {
                return false;
            }
            int first = Math.min(src.length, buffer.length - write);
            System.arraycopy(src, 0, buffer, write, first);
            if (src.length > first) {
                System.arraycopy(src, first, buffer, 0, src.length - first);
            }
            write = (write + src.length) % buffer.length;
            size += src.length;
            return true;
        }

        void read(byte[] dst, int length) {
            int first = Math.min(length, buffer.length - read);
            System.arraycopy(buffer, read, dst, 0, first);
            if (length > first) {
                System.arraycopy(buffer, 0, dst, first, length - first);
            }
            read = (read + length) % buffer.length;
            size -= length;
        }

        void discard(int length) {
            if (length > size) {
                length = size;
            }
            read = (read + length) % buffer.length;
            size -= length;
        }

        void clear() {
            read = 0;
            write = 0;
            size = 0;
        }
    }
}
