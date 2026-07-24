package me.markerra.rtcbridge;

import me.markerra.rtcbridge.server.LocalBridgeServer;

/** Entry point for the local audio bridge. */
public final class Main {
    private static final int DEFAULT_PORT = 25_565;

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        int port = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
        var server = new LocalBridgeServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "bridge-shutdown"));
        server.start();
        System.out.printf("RTC bridge is listening on ws://127.0.0.1:%d%n", port);
        System.out.println("Press Ctrl+C to stop it.");

        // WebSocketServer uses its own threads. The main thread only keeps the process alive.
        Thread.currentThread().join();
    }
}
