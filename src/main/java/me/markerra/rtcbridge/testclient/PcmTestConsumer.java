package me.markerra.rtcbridge.testclient;

import me.markerra.rtcbridge.audio.AudioFormat;
import me.markerra.rtcbridge.audio.PcmAudioPlayer;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Plays and reports PCM frames received from the bridge. */
public final class PcmTestConsumer {
    private static final URI DEFAULT_SERVER = URI.create("ws://127.0.0.1:25565");

    private PcmTestConsumer() {
    }

    public static void main(String[] args) throws Exception {
        URI serverUri = args.length == 0 ? DEFAULT_SERVER : URI.create(args[0]);
        AtomicLong frames = new AtomicLong();
        AtomicLong bytes = new AtomicLong();
        AtomicLong invalidFrames = new AtomicLong();
        var player = new PcmAudioPlayer(AudioFormat.VOICE_CHAT);

        var client = new BridgeTestClient(serverUri, "consumer") {
            @Override
            protected void onPcmFrame(ByteBuffer frame) {
                int size = frame.remaining();
                frames.incrementAndGet();
                bytes.addAndGet(size);
                if (size != AudioFormat.VOICE_CHAT.expectedFrameBytes()) {
                    invalidFrames.incrementAndGet();
                    return;
                }
                byte[] pcm = new byte[size];
                frame.get(pcm);
                player.enqueue(pcm);
            }
        };

        if (!client.connectBlocking(5, TimeUnit.SECONDS) || !client.awaitReady()) {
            throw new IllegalStateException("Bridge did not become ready within five seconds.");
        }

        var reporter = Executors.newSingleThreadScheduledExecutor();
        reporter.scheduleAtFixedRate(() -> System.out.printf(
                "Received: frames=%d, bytes=%d, invalid=%d, dropped=%d (expected about 50 frames/sec)%n",
                frames.getAndSet(0), bytes.getAndSet(0), invalidFrames.getAndSet(0), player.getDroppedFrames()),
                1, 1, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            reporter.shutdownNow();
            client.close();
            player.close();
        }, "test-consumer-shutdown"));
        System.out.println("Waiting for PCM test frames and playing them. Press Ctrl+C to stop.");
        Thread.currentThread().join();
    }
}
