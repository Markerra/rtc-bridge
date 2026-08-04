package me.markerra.rtcbridge;

import me.markerra.rtcbridge.audio.AudioFormat;
import me.markerra.rtcbridge.config.ConfigManager;
import me.markerra.rtcbridge.server.LocalBridgeServer;
import me.markerra.rtcbridge.browser.NektoBrowserApp;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            // 1. Загрузка конфигурации
            ConfigManager.load();

            // 2. Инициализация сервера
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

            // 3. Запуск сервера (работает в фоновых потоках библиотеки Java-WebSocket)
            server.start();
            System.out.printf("RTC bridge is listening on ws://%s:%d%n", url, port);

            // 4. Запуск браузера.
            // Обратите внимание: метод awaitClose() внутри заблокирует выполнение
            // и заменит собой старый Thread.currentThread().join()
            NektoBrowserApp.startBrowser(server);

        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            e.printStackTrace();
        }
    }
}