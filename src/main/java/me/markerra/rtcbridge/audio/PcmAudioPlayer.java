package me.markerra.rtcbridge.audio;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Plays bridge PCM on a dedicated thread.
 *
 * The queue is deliberately short: retaining old voice frames creates noticeable delay,
 * so a full queue drops the oldest frame and preserves real-time playback.
 */
public final class PcmAudioPlayer implements AutoCloseable {
    private static final int QUEUE_CAPACITY_FRAMES = 10;

    private final AudioFormat bridgeFormat;
    private final SourceDataLine line;
    private final ArrayBlockingQueue<byte[]> frames = new ArrayBlockingQueue<>(QUEUE_CAPACITY_FRAMES);
    private final AtomicLong droppedFrames = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread playbackThread;

    public PcmAudioPlayer(AudioFormat bridgeFormat) throws LineUnavailableException {
        if (!bridgeFormat.isSupported()) {
            throw new IllegalArgumentException("Unsupported audio format: " + bridgeFormat);
        }
        this.bridgeFormat = bridgeFormat;

        var javaFormat = new javax.sound.sampled.AudioFormat(
                bridgeFormat.sampleRate(),
                bridgeFormat.bitsPerSample(),
                bridgeFormat.channels(),
                true,
                false
        );
        var info = new DataLine.Info(SourceDataLine.class, javaFormat);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("The default audio device does not support " + javaFormat);
        }

        line = (SourceDataLine) AudioSystem.getLine(info);
        // Five frames give the audio driver 100 ms to work without introducing a large delay.
        line.open(javaFormat, bridgeFormat.expectedFrameBytes() * 5);
        line.start();

        playbackThread = new Thread(this::playLoop, "pcm-playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    /** Adds one complete PCM frame. It returns immediately and never blocks the WebSocket callback. */
    public void enqueue(byte[] frame) {
        if (frame.length != bridgeFormat.expectedFrameBytes()) {
            throw new IllegalArgumentException("Expected a complete PCM frame of "
                    + bridgeFormat.expectedFrameBytes() + " bytes.");
        }

        byte[] copy = Arrays.copyOf(frame, frame.length);
        if (!frames.offer(copy)) {
            frames.poll();
            if (!frames.offer(copy)) {
                droppedFrames.incrementAndGet();
                return;
            }
            droppedFrames.incrementAndGet();
        }
    }

    public long getDroppedFrames() {
        return droppedFrames.get();
    }

    private void playLoop() {
        try {
            while (running.get() || !frames.isEmpty()) {
                byte[] frame = frames.take();
                line.write(frame, 0, frame.length);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (!running.getAndSet(false)) {
            return;
        }
        playbackThread.interrupt();
        line.drain();
        line.stop();
        line.close();
    }
}
