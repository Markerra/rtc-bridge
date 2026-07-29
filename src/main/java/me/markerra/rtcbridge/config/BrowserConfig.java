package me.markerra.rtcbridge.config;

public record BrowserConfig(
        boolean headlessMode,
        boolean manualMode,
        boolean muteOutput,
        boolean muteConnect,
        boolean debugMode,
        boolean fakeStreamInput,
        boolean fakeStreamOutput,
        String defaultURL,
        String profileDirectory
) {
    public BrowserConfig(
            boolean headlessMode,
            boolean manualMode,
            boolean muteOutput,
            boolean muteConnect,
            boolean debugMode,
            boolean fakeStreamInput,
            boolean fakeStreamOutput,
            String defaultURL,
            String profileDirectory) {
        this.headlessMode = headlessMode;
        this.manualMode = manualMode;
        this.muteOutput = muteOutput;
        this.muteConnect = muteConnect;
        this.fakeStreamInput = fakeStreamInput;
        this.fakeStreamOutput = fakeStreamOutput;
        this.debugMode = debugMode;
        this.defaultURL = defaultURL;
        this.profileDirectory = profileDirectory;
    }
}
