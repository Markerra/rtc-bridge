package me.markerra.rtcbridge.config;

public record BrowserConfig(
        boolean headlessMode,
        boolean manualMode,
        boolean muteOutput,
        boolean debugMode,
        String defaultURL,
        String profileDirectory
) {
    public  BrowserConfig(
            boolean headlessMode,
            boolean manualMode,
            boolean muteOutput,
            boolean debugMode,
            String defaultURL,
            String profileDirectory) {
        this.headlessMode = headlessMode;
        this.manualMode = manualMode;
        this.muteOutput = muteOutput;
        this.debugMode = debugMode;
        this.defaultURL = defaultURL;
        this.profileDirectory = profileDirectory;
    }
}
