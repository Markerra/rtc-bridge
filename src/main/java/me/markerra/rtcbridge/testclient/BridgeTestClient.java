package me.markerra.rtcbridge.testclient;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

abstract class BridgeTestClient extends WebSocketClient {
    private static final Gson GSON = new Gson();
    private final String role;
    private final CountDownLatch ready = new CountDownLatch(1);

    protected BridgeTestClient(URI serverUri, String role) {
        super(serverUri);
        this.role = role;
    }

    @Override
    public final void onOpen(ServerHandshake handshake) {
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("role", role);
        send(GSON.toJson(hello));
    }

    @Override
    public final void onMessage(String text) {
        System.out.printf("Server [%s]: %s%n", getURI().getPath(), text);
        JsonObject message = GSON.fromJson(text, JsonObject.class);
        if (message != null && message.has("type") && "state".equals(message.get("type").getAsString())
                && message.has("state") && "ready".equals(message.get("state").getAsString())) {
            ready.countDown();
        }
    }

    @Override
    public final void onMessage(ByteBuffer bytes) {
        onPcmFrame(bytes);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.printf("Disconnected [%s]: code=%d, reason=%s%n", getURI().getPath(), code, reason);
    }

    @Override
    public void onError(Exception exception) {
        System.err.println("WebSocket error [" + getURI().getPath() + "]: " + exception.getMessage());
    }

    public boolean awaitReady() throws InterruptedException {
        return ready.await(5, TimeUnit.SECONDS);
    }

    protected abstract void onPcmFrame(ByteBuffer bytes);
}