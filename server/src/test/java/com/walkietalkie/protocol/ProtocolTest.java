package com.walkietalkie.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ProtocolTest {
    @Test void createsVersionedErrorWithoutInternalDetails() {
        var error = Protocol.error(new ObjectMapper(), "bad_input", "Message is invalid", "request-1");
        assertEquals(1, error.path("version").asInt());
        assertEquals("error", error.path("type").asText());
        assertEquals("bad_input", error.path("code").asText());
        assertEquals("request-1", error.path("requestId").asText());
    }
}
