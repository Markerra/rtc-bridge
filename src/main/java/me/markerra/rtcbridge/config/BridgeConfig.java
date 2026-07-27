package me.markerra.rtcbridge.config;

public record BridgeConfig(String host, int port, String browserEndpoint, String gameEndpoint) {
    public BridgeConfig(String host, int port, String browserEndpoint, String gameEndpoint) {
        this.host = host;
        this.port = port;
        this.browserEndpoint = browserEndpoint;
        this.gameEndpoint = gameEndpoint;
    }

    public String getFullUrl() {
        return String.format("http://%s:%d/", host, port);
    }
}