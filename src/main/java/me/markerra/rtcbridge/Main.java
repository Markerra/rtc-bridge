package me.markerra.rtcbridge;

import me.markerra.rtcbridge.audio.AudioFormat;
import me.markerra.rtcbridge.config.ConfigManager;
import me.markerra.rtcbridge.server.LocalBridgeServer;

/** Entry point for the local audio bridge. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigManager.load();

        String url = ConfigManager.bridge().host();
        int port = ConfigManager.bridge().port();
        AudioFormat audioFormat = ConfigManager.audio().format();

        var server = new LocalBridgeServer(url, port, audioFormat);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "bridge-shutdown"));
        server.start();
        System.out.printf("RTC bridge is listening on ws://%s:%d%n", url, port);

        // WebSocketServer uses its own threads. The main thread only keeps the process alive.
        Thread.currentThread().join();
    }
}
