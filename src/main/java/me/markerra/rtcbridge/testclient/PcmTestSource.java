package me.markerra.rtcbridge.testclient;

import me.markerra.rtcbridge.audio.AudioFormat;
import me.markerra.rtcbridge.audio.PcmSineGenerator;
import me.markerra.rtcbridge.config.ConfigManager;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Sends 440 Hz test audio: 50 PCM frames of 1,920 bytes every second. */
public final class PcmTestSource {

    private PcmTestSource() {
    }

    public static void main(String[] args) throws Exception {
        ConfigManager.load();

        String url = "ws://"
                + ConfigManager.bridge().host() + ":"
                + ConfigManager.bridge().port()
                + ConfigManager.bridge().gameEndpoint();
        URI serverUri = URI.create(url);

        var client = new BridgeTestClient(serverUri, "source") {
            @Override
            protected void onPcmFrame(ByteBuffer bytes) {
                // A source never expects incoming audio.
            }
        };

        if (!client.connectBlocking(5, TimeUnit.SECONDS) || !client.awaitReady()) {
            throw new IllegalStateException("Bridge did not become ready within five seconds.");
        }

        var generator = new PcmSineGenerator(AudioFormat.VOICE_CHAT, 440, 0.25);
        ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor();
        sender.scheduleAtFixedRate(() -> client.send(generator.nextFrame()), 0, 20, TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sender.shutdownNow();
            client.close();
        }, "test-source-shutdown"));
        System.out.println("Sending 440 Hz PCM test frames. Press Ctrl+C to stop.");
        Thread.currentThread().join();
    }
}
