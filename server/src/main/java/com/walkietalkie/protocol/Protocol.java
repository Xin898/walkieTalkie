package com.walkietalkie.protocol;

import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class Protocol {
    public static final int VERSION = 1;
    public static final Set<String> CLIENT_TYPES = Set.of("join_channel", "leave_channel", "request_talk", "release_talk", "ping");
    private Protocol() {}

    public static ObjectNode event(ObjectMapper mapper, String type, String requestId, String channelId, String speakerId) {
        ObjectNode result = mapper.createObjectNode().put("version", VERSION).put("type", type);
        if (requestId != null) result.put("requestId", requestId);
        if (channelId != null) result.put("channelId", channelId);
        if (speakerId != null) result.put("speakerId", speakerId);
        return result;
    }

    public static ObjectNode error(ObjectMapper mapper, String code, String message, String requestId) {
        return event(mapper, "error", requestId, null, null).put("code", code).put("message", message);
    }

    public static String requestId(JsonNode message) {
        String value = message.path("requestId").asText(null);
        return value == null || value.length() > 100 ? UUID.randomUUID().toString() : value;
    }
}
