package me.markerra.rtcbridge.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserStateTest {
    @Test
    void parsesOnlyKnownStatesReportedByThePageMonitor() {
        assertEquals(BrowserState.SEARCHING, BrowserState.fromMonitorValue("SEARCHING").orElseThrow());
        assertTrue(BrowserState.fromMonitorValue("NOT_A_STATE").isEmpty());
    }
}
