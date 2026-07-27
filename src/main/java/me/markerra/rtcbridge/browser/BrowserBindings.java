package me.markerra.rtcbridge.browser;

import com.google.gson.Gson;
import com.microsoft.playwright.Page;
import me.markerra.rtcbridge.config.AppConfig;
import me.markerra.rtcbridge.config.ConfigManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class BrowserBindings {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("mm:ss:SSS");

    private final PlaywrightBrowserSession session;
    private final Gson gson;
    private final AppConfig config;

    public BrowserBindings(PlaywrightBrowserSession session, Gson gson) {
        this.session = session;

        if (gson == null) this.gson = new Gson();
        else this.gson = gson;

        config = ConfigManager.get();
    }

    public void register(Page page, String stateScript, String pcmScript) {
        registerStateBinding(page);
        registerLogBinding(page);

        page.addInitScript("""
                window.__bridgeConfig = %s;
                """.formatted(gson.toJson(config)));

        page.addInitScript("sessionStorage.removeItem('__rtc_bridge_state');");

        page.addInitScript(stateScript);
        page.addInitScript(pcmScript);
    }

    private void registerStateBinding(Page page) {
        page.exposeBinding("reportStateToJava", (source, args) -> {

            if (args.length == 0)
                return null;

            try {
                session.changeState(BrowserState.valueOf(args[0].toString()));

            } catch (IllegalArgumentException ignored) {
                System.err.println("Unknown browser state: " + args[0]);
            }

            return null;
        });

    }

    private void registerLogBinding(Page page) {
        page.exposeBinding("bridgeLog", (source, args) -> {

            if (args.length < 2)
                return null;

            BrowserLogLevel level = BrowserLogLevel.valueOf(args[0].toString());

            if (level == BrowserLogLevel.DEBUG && !config.browser().debugMode())
                return null;

            String message = args[1].toString();

            System.out.printf(
                "[%s] [%s] %s%n",
                LocalTime.now().format(TIME_FORMAT),
                level,
                message
            );

            return null;
        });

    }

}