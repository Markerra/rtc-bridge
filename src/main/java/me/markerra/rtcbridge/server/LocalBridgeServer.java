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
    private static AudioFormat audioFormat;

    // record-helper for storing client state
    public record ClientSession(ClientRole role, ClientChannel channel) {}

    private final Map<WebSocket, ClientSession> clients = new ConcurrentHashMap<>();

    public LocalBridgeServer(String host, int port, AudioFormat format) {
        super(new InetSocketAddress(host, port));
        audioFormat = format;
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        // extract connection endpoint, e.g. "/browser" or "/game"
        String resourceDescriptor = handshake.getResourceDescriptor();
        ClientChannel channel = parseChannel(resourceDescriptor);

        if (channel == null) {
            reject(connection, "Invalid endpoint. Connect to /game or /browser.");
            connection.close();
            return;
        }

        // register client with unknown role on the specific channel
        clients.put(connection, new ClientSession(ClientRole.UNKNOWN, channel));
        sendJson(connection, stateMessage("connected"));
        System.out.printf("[SERVER] Client connected to [%s]: %s%n", channel, connection.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket connection, String text) {
        try {
            JsonObject message = GSON.fromJson(text, JsonObject.class);
            if (message == null) return;

            ClientSession currentSession = clients.get(connection);
            if (currentSession == null) return;

            // handshake
            if (message.has("type") && "hello".equals(message.get("type").getAsString())) {
                String roleStr = message.has("role") ? message.get("role").getAsString() : "";
                ClientRole newRole;

                if ("source".equals(roleStr)) {
                    newRole = ClientRole.SOURCE;
                } else if ("consumer".equals(roleStr)) {
                    newRole = ClientRole.CONSUMER;
                } else {
                    reject(connection, "Role must be source or consumer.");
                    return;
                }

                // update the session and set new role
                clients.put(connection, new ClientSession(newRole, currentSession.channel()));
                sendJson(connection, stateMessage("ready"));
                return;
            }

            // actions
            if (message.has("action")) {
                if (currentSession.role() == ClientRole.UNKNOWN) {
                    reject(connection, "You must send a hello message first.");
                    return;
                }

                clients.forEach((client, session) -> {
                    if (session.channel() == ClientChannel.BROWSER && client.isOpen()) {
                        client.send(text);
                    }
                });
                return;
            }

            reject(connection, "Unknown message format.");

        } catch (JsonParseException | IllegalStateException exception) {
            reject(connection, "Malformed JSON message.");
        }
    }

    @Override
    public void onMessage(WebSocket connection, ByteBuffer frame) {
        ClientSession senderSession = clients.get(connection);
        if (senderSession == null || senderSession.role() != ClientRole.SOURCE) {
            reject(connection, "Only a source may send binary audio.");
            return;
        }
        if (frame.remaining() != audioFormat.expectedFrameBytes()) {
            reject(connection, "Expected %d bytes per PCM frame, got %d."
                    .formatted(audioFormat.expectedFrameBytes(), frame.remaining()));
            return;
        }

        byte[] pcm = new byte[frame.remaining()];
        frame.get(pcm);

        ClientChannel targetChannel = senderSession.channel();

        // send to all consumer in the same channel
        clients.forEach((client, session) -> {
            if (session.role() == ClientRole.CONSUMER
                    && session.channel() == targetChannel
                    && client.isOpen()) {
                client.send(pcm);
            }
        });
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        clients.remove(connection);
        System.out.printf("[SERVER] Client disconnected: %s (%s)%n", connection.getRemoteSocketAddress(), reason);
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

    private ClientChannel parseChannel(String path) {
        if (path == null) return null;
        if (path.startsWith("/game")) return ClientChannel.GAME;
        if (path.startsWith("/browser")) return ClientChannel.BROWSER;
        return null;
    }

    private static JsonObject stateMessage(String state) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "state");
        message.addProperty("state", state);
        message.addProperty("sampleRate", audioFormat.sampleRate());
        message.addProperty("channels", audioFormat.channels());
        message.addProperty("bitsPerSample", audioFormat.bitsPerSample());
        message.addProperty("frameDurationMs", audioFormat.frameDurationMs());
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

    public void notifyDialogState(boolean active, int timeSeconds) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "dialog_state");
        json.addProperty("active", active);
        json.addProperty("timeSeconds", timeSeconds);


        clients.forEach((client, session) -> {
            if (session.channel() == ClientChannel.BROWSER && client.isOpen()) {
                client.send(json.toString());
            }
        });
    }
}