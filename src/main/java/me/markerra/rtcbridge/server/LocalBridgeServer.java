package me.markerra.rtcbridge.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import me.markerra.rtcbridge.audio.AudioFormat;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local-only relay. One SOURCE sends PCM frames; every CONSUMER receives them.
 * The browser adapter will be a SOURCE, and the Minecraft mod will be a CONSUMER.
 */
public final class LocalBridgeServer extends WebSocketServer {
    private static final Gson GSON = new Gson();
    private static final AudioFormat FORMAT = AudioFormat.VOICE_CHAT;

    private final Map<WebSocket, ClientRole> clients = new ConcurrentHashMap<>();

    public LocalBridgeServer(int port) {
        // Binding explicitly to loopback prevents other devices on the network from connecting.
        super(new InetSocketAddress("127.0.0.1", port));
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        clients.put(connection, ClientRole.UNKNOWN);
        sendJson(connection, stateMessage("connected"));
        System.out.printf("Client connected: %s%n", connection.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket connection, String text) {
        try {
            JsonObject message = GSON.fromJson(text, JsonObject.class);
            if (message == null || !message.has("type") || !"hello".equals(message.get("type").getAsString())) {
                reject(connection, "Expected a hello message.");
                return;
            }

            String role = message.has("role") ? message.get("role").getAsString() : "";
            if ("source".equals(role)) {
                clients.put(connection, ClientRole.SOURCE);
            } else if ("consumer".equals(role)) {
                clients.put(connection, ClientRole.CONSUMER);
            } else {
                reject(connection, "Role must be source or consumer.");
                return;
            }

            sendJson(connection, stateMessage("ready"));
        } catch (JsonParseException | IllegalStateException exception) {
            reject(connection, "Malformed JSON hello message.");
        }
    }

    @Override
    public void onMessage(WebSocket connection, ByteBuffer frame) {
        if (clients.getOrDefault(connection, ClientRole.UNKNOWN) != ClientRole.SOURCE) {
            reject(connection, "Only a source may send binary audio.");
            return;
        }
        if (frame.remaining() != FORMAT.expectedFrameBytes()) {
            reject(connection, "Expected %d bytes per PCM frame, got %d."
                    .formatted(FORMAT.expectedFrameBytes(), frame.remaining()));
            return;
        }

        // Copy before broadcasting: ByteBuffer position is mutable and belongs to the library callback.
        byte[] pcm = new byte[frame.remaining()];
        frame.get(pcm);

        // Send current frame to all consumers
        clients.forEach((client, role) -> {
            if (role == ClientRole.CONSUMER && client.isOpen()) {
                client.send(pcm);
            }
        });
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        clients.remove(connection);
        System.out.printf("Client disconnected: %s (%s)%n", connection.getRemoteSocketAddress(), reason);
    }

    @Override
    public void onError(WebSocket connection, Exception exception) {
        System.err.printf("WebSocket error%s: %s%n",
                connection == null ? "" : " for " + connection.getRemoteSocketAddress(), exception.getMessage());
    }

    @Override
    public void onStart() {
        setConnectionLostTimeout(30);
    }

    private static JsonObject stateMessage(String state) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "state");
        message.addProperty("state", state);
        message.addProperty("sampleRate", FORMAT.sampleRate());
        message.addProperty("channels", FORMAT.channels());
        message.addProperty("bitsPerSample", FORMAT.bitsPerSample());
        message.addProperty("frameDurationMs", FORMAT.frameDurationMs());
        return message;
    }

    private static void sendJson(WebSocket connection, JsonObject message) {
        connection.send(GSON.toJson(message));
    }

    private static void reject(WebSocket connection, String reason) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "error");
        message.addProperty("message", reason);
        sendJson(connection, message);
    }
}
