package me.markerra.rtcbridge.browser;

import java.nio.file.Path;

/**
 * Version 0.4 executable: starts a visible user-controlled Nekto browser session.
 * It deliberately does not automate captcha or any site interaction.
 */
public final class NektoBrowserApp {
    private static final String DEFAULT_URL = "https://nekto.me/";
    private static final Path PROFILE_DIRECTORY = Path.of("data", "nekto-profile");

    private NektoBrowserApp() {
    }

    public static void main(String[] args) throws Exception {
        String targetUrl = args.length == 0 ? DEFAULT_URL : args[0];
        try (var session = new PlaywrightBrowserSession(PROFILE_DIRECTORY)) {
            Runtime.getRuntime().addShutdownHook(new Thread(session::close, "browser-shutdown"));
            session.open(targetUrl);

            System.out.println("Browser is ready. Complete any captcha manually in its window.");
            System.out.println("The profile is stored in data/nekto-profile. Close the browser or press Ctrl+C to stop.");
            session.awaitClose();
        }
    }
}
