package me.markerra.rtcbridge.config;

public record AppConfig(BridgeConfig bridge, AudioConfig audio, BrowserConfig browser
) {
    public AppConfig {
        if (bridge == null) bridge = new BridgeConfig(null, 25565, null, null);
        if (audio == null) audio = new AudioConfig(1.00, null);
        if (browser == null) browser = new BrowserConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null);
    }
}