package me.markerra.rtcbridge.browser;

import me.markerra.rtcbridge.config.ConfigManager;
import me.markerra.rtcbridge.server.LocalBridgeServer;

import java.nio.file.Path;

public final class NektoBrowserApp {

    private NektoBrowserApp() {
    }

    public static void startBrowser(LocalBridgeServer server) throws Exception {
        String targetUrl = ConfigManager.browser().defaultURL();
        boolean manualMode = ConfigManager.browser().manualMode();
        String profileDirectory = ConfigManager.browser().profileDirectory();
        Path profilePath = Path.of(profileDirectory);

        try (var session = new PlaywrightBrowserSession(profilePath, ConfigManager.get())) {
            Runtime.getRuntime().addShutdownHook(new Thread(session::close, "browser-shutdown"));

            session.toggleManualMode(manualMode);

            session.onStateChanged(state -> {
                server.notifyDialogState(state == BrowserState.CONNECTED, session.getSecondsPassed());

                if (state == BrowserState.CAPTCHA_REQUIRED) {
                    System.out.println("CAPTCHA REQUIRED");
                }

                if (state == BrowserState.PAGE_READY && session.isAutoSearchEnabled()) {
                    session.searchNext();
                }

                if (state == BrowserState.CONNECTED) {
                    session.startDialogTimer(seconds -> {
                        server.notifyDialogState(true, session.getSecondsPassed());
                    });
                }
                else if (state == BrowserState.PAGE_READY) {
                    session.resetDialogTimer();
                }
            });

            session.open(targetUrl);

            System.out.println("Browser is ready. Complete any captcha manually in its window.");
            System.out.println("The profile is stored in data/nekto-profile. Close the browser or press Ctrl+C to stop.");

            session.awaitClose();
        }
    }
}