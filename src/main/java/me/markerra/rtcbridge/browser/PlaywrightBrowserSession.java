package me.markerra.rtcbridge.browser;
import me.markerra.rtcbridge.config.AppConfig;
import me.markerra.rtcbridge.util.ResourceManager.*;

import com.microsoft.playwright.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
                    "--disable-features=BlockInsecurePrivateNetworkRequests,PrivateNetworkAccessSendPreflights",
                    "--enable-features=LocalNetworkAccess"
                ))
        );

        page = context.pages().isEmpty() ? context.newPage() : context.pages().getFirst();

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

    public void searchNext() {
        // TODO: add search params
        Locator searchButton = page.locator("#searchCompanyBtn, .callScreen__findBtn");

        try {
            searchButton.click();
        } catch (com.microsoft.playwright.TimeoutError e) {

        }
    }

    public void toggleManualMode(boolean manual) {
        if (manual) state.set(BrowserState.MANUAL_MODE);
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
}