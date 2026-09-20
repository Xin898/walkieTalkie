package com.walkietalkie.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AudioFrameTest {
    @Test void acceptsVersionedFrameAndReadsHeader() {
        byte[] frame = ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN).put((byte) 1).putInt(7).putLong(1234L).put((byte) 9).array();
        assertTrue(AudioFrame.isValid(frame, 4096));
        assertEquals(7, AudioFrame.sequence(frame));
        assertEquals(1234, AudioFrame.timestampMillis(frame));
    }

    @Test void rejectsWrongVersionAndShortFrames() {
        assertFalse(AudioFrame.isValid(new byte[13], 4096));
        byte[] wrongVersion = new byte[14];
        wrongVersion[0] = 2;
        assertFalse(AudioFrame.isValid(wrongVersion, 4096));
    }
}
