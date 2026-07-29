package me.markerra.rtcbridge.browser;

import java.util.Arrays;
import java.util.Optional;

/** Actions that are useful to the bridge without controlling the site. */
public enum BrowserAction {
    START_DIALOG,
    END_DIALOG,
    SKIP_DIALOG;

    static Optional<BrowserAction> fromMonitorValue(String value) {
        return Arrays.stream(values())
                .filter(action -> action.name().equals(value))
                .findFirst();
    }
}
