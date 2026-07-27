package me.markerra.rtcbridge.browser;

import java.util.Arrays;
import java.util.Optional;

/** States that are useful to the bridge without controlling the site. */
public enum BrowserState {
    STARTING,
    PAGE_LOADING,
    PAGE_READY,
    CAPTCHA_REQUIRED,
    SEARCHING,
    CONNECTED,
    MANUAL_MODE,
    CLOSED;

    static Optional<BrowserState> fromMonitorValue(String value) {
        return Arrays.stream(values())
                .filter(state -> state.name().equals(value))
                .findFirst();
    }
}
