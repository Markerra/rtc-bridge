package me.markerra.rtcbridge.browser;

import me.markerra.rtcbridge.config.AppConfig;
import me.markerra.rtcbridge.util.ResourceManager;

import com.google.gson.Gson;
import com.microsoft.playwright.Page;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class BrowserBindings {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("mm:ss.SSS");

    private final PlaywrightBrowserSession session;
    private final Gson gson;
    private final AppConfig config;

    private static final String STATE_SCRIPT = ResourceManager.loadResource("state-bridge.js");
    private static final String PCM_SCRIPT = ResourceManager.loadResource("pcm-bridge.js");
    private static final String INPUT_SCRIPT = ResourceManager.loadResource("browser-input.js");

    public BrowserBindings(PlaywrightBrowserSession session, AppConfig config) {
        this.session = session;
        this.config = config;
        this.gson = new Gson();
    }

    public void register(Page page) {
        page.addInitScript("window.__bridgeConfig = %s;".formatted(gson.toJson(config)));
        page.addInitScript("sessionStorage.removeItem('__rtc_bridge_state');");

        registerStateBinding(page);
        registerLogBinding(page);

        page.addInitScript(STATE_SCRIPT);

        if (config.browser().fakeStream()) {
            page.addInitScript(PCM_SCRIPT);
            page.addInitScript(INPUT_SCRIPT);
        }
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