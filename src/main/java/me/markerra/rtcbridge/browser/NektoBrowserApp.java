package me.markerra.rtcbridge.browser;

import me.markerra.rtcbridge.config.ConfigManager;


import java.nio.file.Path;

/**
 * Version 0.4 executable: starts a visible user-controlled Nekto browser session.
 */
public final class NektoBrowserApp {
    private NektoBrowserApp() {
    }

    public static void main(String[] args) throws Exception {
        ConfigManager.load();

        String targetUrl = ConfigManager.browser().defaultURL();
        boolean manualMode = ConfigManager.browser().manualMode();
        String profileDirectory = ConfigManager.browser().profileDirectory();
        Path profilePath = Path.of(profileDirectory);

        try (var session = new PlaywrightBrowserSession(profilePath, ConfigManager.get())) {
            Runtime.getRuntime().addShutdownHook(new Thread(session::close, "browser-shutdown"));

            session.toggleManualMode(manualMode);

            session.onStateChanged(state -> {
                if (state == BrowserState.CAPTCHA_REQUIRED) { System.out.println("CAPTCHA REQUIRED"); }
                if (state == BrowserState.PAGE_READY) { session.searchNext(); }
            });

            session.open(targetUrl);

            System.out.println("Browser is ready. Complete any captcha manually in its window.");
            System.out.println("The profile is stored in data/nekto-profile. Close the browser or press Ctrl+C to stop.");

            session.awaitClose();
        }
    }
}