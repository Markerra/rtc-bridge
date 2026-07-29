package me.markerra.rtcbridge.browser;
import me.markerra.rtcbridge.config.AppConfig;
import me.markerra.rtcbridge.util.ResourceManager.*;

import com.microsoft.playwright.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * One visible, persistent browser session.
 *
 * Persistent means cookies, local storage and site preferences are written to a project-local
 * profile directory. This is necessary because captcha and login must be completed by the user,
 * not bypassed by the program.
 */
public final class PlaywrightBrowserSession implements AutoCloseable {
    private final Path profileDirectory;

    private final AtomicReference<BrowserState> state =
            new AtomicReference<>(BrowserState.STARTING);

    private final AtomicBoolean autoSearchEnabled =
            new AtomicBoolean(false);

    private final AppConfig config;

    private Playwright playwright;
    private BrowserContext context;
    private Page page;

    private final List<Consumer<BrowserState>> listeners =
            new CopyOnWriteArrayList<>();

    public PlaywrightBrowserSession(Path profileDirectory, AppConfig config) {
        this.profileDirectory = profileDirectory;
        this.config = config;
    }

    public void open(String targetUrl) throws IOException {
        Files.createDirectories(profileDirectory);
        playwright = Playwright.create();

        context = playwright.chromium().launchPersistentContext(
            profileDirectory,
            new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(config.browser().headlessMode())
                .setArgs(List.of(
                    "--allow-running-insecure-content",
                    "--disable-web-security",
                    "--disable-features=BlockInsecurePrivateNetworkRequests,PrivateNetworkAccessSendPreflights,CalculateNativeWinOcclusion,IntensiveWakeUpThrottling,AudioServiceOutOfProcess,HighEfficiencyModeAvailable,BatterySaverModeAvailable",
                    "--enable-features=LocalNetworkAccess",
                    "--disable-background-timer-throttling",
                    "--disable-renderer-backgrounding",
                    "--disable-backgrounding-occluded-windows",
                    "--disable-background-media-suspend",
                    "--autoplay-policy=no-user-gesture-required"
                ))
        );

        page = context.pages().isEmpty() ? context.newPage() : context.pages().getFirst();

        // whitelist
        if (config.browser().whitelist()) {
            context.route("**/*", route -> {
                String url = route.request().url().toLowerCase();

                boolean isAllowed = url.contains("nekto.me") ||
                        url.contains("127.0.0.1") ||
                        url.contains("localhost") ||
                        url.startsWith("data:") ||
                        url.startsWith("blob:");

                if (isAllowed) {
                    route.resume();
                } else {
                    route.abort();
                }
            });
        }

        BrowserBindings bindings = new BrowserBindings(this, config);
        bindings.register(page);

        context.onClose(ctx -> changeState(BrowserState.CLOSED));

        System.out.printf("Opening %s%n", targetUrl);
        changeState(BrowserState.PAGE_LOADING);
        page.navigate(targetUrl);
    }


    public void awaitClose() {
        while (state.get() != BrowserState.CLOSED) {
            page.waitForTimeout(100);
        }
    }

    @Override
    public void close() {
        changeState(BrowserState.CLOSED);

        if (context != null) {
            context.close();
            context = null;
        }

        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

    public void toggleManualMode(boolean manual) {
        if (manual) state.set(BrowserState.MANUAL_MODE);
    }

    public boolean isAutoSearchEnabled() {
        return autoSearchEnabled.get();
    }

    public void setAutoSearchEnabled(boolean enabled) {
        this.autoSearchEnabled.set(enabled);
    }

    public void onStateChanged(Consumer<BrowserState> listener) {
        listeners.add(listener);
    }

    void changeState(BrowserState nextState) {
        if (state.get() == BrowserState.MANUAL_MODE) return;

        BrowserState previousState = state.getAndSet(nextState);
        if (previousState != nextState) {
            System.out.printf("Browser state: %s -> %s%n", previousState, nextState);
            listeners.forEach(listener -> {
                try {
                    listener.accept(nextState);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    void handleBrowserAction(BrowserAction action) {
         switch (action) {
            case BrowserAction.START_DIALOG -> {
                autoSearchEnabled.set(true);
                searchNext();
            }
            case BrowserAction.END_DIALOG -> {
                autoSearchEnabled.set(false);
                endDialog();
            }
            case BrowserAction.SKIP_DIALOG -> {
                autoSearchEnabled.set(true);
                if (state.get() == BrowserState.CONNECTED) {
                    endDialog();
                } else if (state.get() == BrowserState.PAGE_READY) {
                    searchNext();
                }
            }
        }
    }

    public void searchNext()
    {
        if (state.get() != BrowserState.PAGE_READY) return;

        Locator searchButton = page.locator("#searchCompanyBtn, .callScreen__findBtn");

        try {
            searchButton.click();
        } catch (com.microsoft.playwright.TimeoutError e) {
            System.out.println("Search Next button search timed out");
        }
    }

    public void endDialog()
    {
        if (state.get() != BrowserState.CONNECTED) return;

        Locator endButton = page.locator(".callScreen__cancelCallBtn");
        Locator confirmButton = page.locator(".swal2-confirm");

        try {
            endButton.click();
            confirmButton.click();
        } catch (com.microsoft.playwright.TimeoutError e) {
            System.out.println("End Dialog button search timed out");
        }

    }
}