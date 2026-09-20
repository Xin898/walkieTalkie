package com.walkietalkie.websocket;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.walkietalkie.channel.TalkCoordinator;
import com.walkietalkie.config.WalkieProperties;
import com.walkietalkie.protocol.AudioFrame;
import com.walkietalkie.protocol.Protocol;

@Component
public class WalkieWebSocketHandler extends BinaryWebSocketHandler {
    private final ObjectMapper mapper;
    private final WalkieProperties properties;
    private final TalkCoordinator coordinator;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public WalkieWebSocketHandler(ObjectMapper mapper, WalkieProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
        this.coordinator = new TalkCoordinator(Duration.ofSeconds(properties.maxTalkSeconds()));
    }

    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = queryValue(session.getUri(), "userId");
        if (userId == null || userId.isBlank() || userId.length() > 100) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.getAttributes().put("userId", userId);
        sessions.put(session.getId(), session);
    }

    @Override protected void handleTextMessage(WebSocketSession session, TextMessage text) {
        try {
            handleTextMessageInternal(session, text);
        } catch (IOException ignored) {
            closeAndRelease(session);
        }
    }

    private void handleTextMessageInternal(WebSocketSession session, TextMessage text) throws IOException {
        if (text.getPayloadLength() > properties.maxJsonBytes()) {
            sendError(session, "payload_too_large", "Control message is too large", null);
            return;
        }
        JsonNode message;
        try { message = mapper.readTree(text.getPayload()); }
        catch (JsonProcessingException ignored) { sendError(session, "invalid_json", "Control message is invalid", null); return; }
        String requestId = Protocol.requestId(message);
        if (message.path("version").asInt(-1) != Protocol.VERSION) { sendError(session, "unsupported_version", "Unsupported protocol version", requestId); return; }
        String type = message.path("type").asText("");
        if (!Protocol.CLIENT_TYPES.contains(type)) { sendError(session, "unknown_event", "Unsupported event type", requestId); return; }
        switch (type) {
            case "join_channel" -> join(session, message, requestId);
            case "leave_channel" -> leave(session, requestId);
            case "request_talk" -> requestTalk(session, requestId);
            case "release_talk" -> releaseTalk(session, requestId);
            case "ping" -> send(session, Protocol.event(mapper, "pong", requestId, coordinator.channelOf(session.getId()), null));
            default -> sendError(session, "unknown_event", "Unsupported event type", requestId);
        }
    }

    private void join(WebSocketSession session, JsonNode message, String requestId) throws IOException {
        String channelId = message.path("channelId").asText("");
        if (channelId.isBlank() || channelId.length() > 100) { sendError(session, "invalid_channel", "Channel is invalid", requestId); return; }
        coordinator.join(session.getId(), channelId);
        send(session, Protocol.event(mapper, "channel_joined", requestId, channelId, coordinator.speakerOf(channelId)));
        broadcast(channelId, Protocol.event(mapper, "speaker_changed", null, channelId, coordinator.speakerOf(channelId)), null);
    }

    private void leave(WebSocketSession session, String requestId) throws IOException {
        String channel = coordinator.channelOf(session.getId());
        coordinator.leave(session.getId());
        send(session, Protocol.event(mapper, "channel_left", requestId, channel, null));
        if (channel != null) broadcast(channel, Protocol.event(mapper, "speaker_changed", null, channel, coordinator.speakerOf(channel)), null);
    }

    private void requestTalk(WebSocketSession session, String requestId) throws IOException {
        String channel = coordinator.channelOf(session.getId());
        TalkCoordinator.Result result = coordinator.requestTalk(session.getId());
        switch (result) {
            case GRANTED -> {
                send(session, Protocol.event(mapper, "talk_granted", requestId, channel, userId(session)));
                broadcast(channel, Protocol.event(mapper, "speaker_changed", null, channel, userId(session)), null);
            }
            case DENIED -> send(session, Protocol.event(mapper, "talk_denied", requestId, channel, coordinator.speakerOf(channel)));
            case NOT_MEMBER -> sendError(session, "not_member", "Join a channel first", requestId);
            default -> sendError(session, "talk_unavailable", "Talk request was not accepted", requestId);
        }
    }

    private void releaseTalk(WebSocketSession session, String requestId) throws IOException {
        String channel = coordinator.channelOf(session.getId());
        coordinator.releaseTalk(session.getId());
        send(session, Protocol.event(mapper, "speaker_changed", requestId, channel, null));
        if (channel != null) broadcast(channel, Protocol.event(mapper, "speaker_changed", null, channel, coordinator.speakerOf(channel)), null);
    }

    @Override protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {
        var payload = message.getPayload().duplicate();
        byte[] frame = new byte[payload.remaining()];
        payload.get(frame);
        if (!AudioFrame.isValid(frame, properties.maxAudioBytes()) || !coordinator.canSendAudio(session.getId(), message.getPayloadLength())) {
            sendError(session, "audio_rejected", "Audio frame is not accepted", null);
            return;
        }
        String channel = coordinator.channelOf(session.getId());
        sessions.values().stream().filter(candidate -> candidate.isOpen() && !candidate.getId().equals(session.getId()) && channel.equals(coordinator.channelOf(candidate.getId())))
                .forEach(candidate -> { try { candidate.sendMessage(message); } catch (IOException ignored) { closeAndRelease(candidate); } });
    }

    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String channel = coordinator.channelOf(session.getId());
        coordinator.leave(session.getId());
        sessions.remove(session.getId());
        if (channel != null) broadcast(channel, Protocol.event(mapper, "speaker_changed", null, channel, coordinator.speakerOf(channel)), session.getId());
    }

    private void closeAndRelease(WebSocketSession session) { try { session.close(CloseStatus.SERVER_ERROR); } catch (IOException ignored) {} }
    private String userId(WebSocketSession session) { return (String) session.getAttributes().get("userId"); }
    private void sendError(WebSocketSession session, String code, String message, String requestId) throws IOException { send(session, Protocol.error(mapper, code, message, requestId)); }
    private void send(WebSocketSession session, ObjectNode message) throws IOException { if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(message))); }
    private void broadcast(String channel, ObjectNode message, String excludedId) throws IOException { for (WebSocketSession candidate : sessions.values()) if (candidate.isOpen() && channel != null && channel.equals(coordinator.channelOf(candidate.getId())) && !candidate.getId().equals(excludedId)) send(candidate, message); }
    private static String queryValue(URI uri, String key) { if (uri == null || uri.getQuery() == null) return null; for (String item : uri.getQuery().split("&")) { String[] pair = item.split("=", 2); if (pair.length == 2 && key.equals(pair[0])) return pair[1]; } return null; }
}
