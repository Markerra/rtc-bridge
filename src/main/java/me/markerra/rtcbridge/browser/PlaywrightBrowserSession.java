package me.markerra.rtcbridge.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * One visible, persistent browser session.
 *
 * Persistent means cookies, local storage and site preferences are written to a project-local
 * profile directory. This is necessary because captcha and login must be completed by the user,
 * not bypassed by the program.
 */
public final class PlaywrightBrowserSession implements AutoCloseable {
    private final Path profileDirectory;
    private final CountDownLatch pageClosed = new CountDownLatch(1);

    private Playwright playwright;
    private BrowserContext context;
    private Page page;

    public PlaywrightBrowserSession(Path profileDirectory) {
        this.profileDirectory = profileDirectory;
    }

    public void open(String targetUrl) throws IOException {
        Files.createDirectories(profileDirectory);
        playwright = Playwright.create();
        context = playwright.chromium().launchPersistentContext(
                profileDirectory,
                new BrowserType.LaunchPersistentContextOptions().setHeadless(false)
        );
        page = context.pages().isEmpty() ? context.newPage() : context.pages().getFirst();
        page.onLoad(loadedPage -> System.out.printf("Page loaded: %s%n", loadedPage.url()));
        page.onClose(closedPage -> pageClosed.countDown());

        System.out.printf("Opening %s%n", targetUrl);
        page.navigate(targetUrl);
    }

    /** Blocks until the user closes the only browser tab/window or Ctrl+C stops the process. */
    public void awaitClose() throws InterruptedException {
        pageClosed.await();
    }

    @Override
    public void close() {
        pageClosed.countDown();
        if (context != null) {
            context.close();
            context = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }
}
