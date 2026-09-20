package com.walkietalkie.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class AudioFrame {
    public static final int HEADER_BYTES = 13;
    private AudioFrame() {}

    public static boolean isValid(byte[] frame, int maxBytes) {
        return frame.length >= HEADER_BYTES && frame.length <= maxBytes && (frame[0] & 0xff) == Protocol.VERSION;
    }

    public static long sequence(byte[] frame) { return Integer.toUnsignedLong(ByteBuffer.wrap(frame, 1, 4).order(ByteOrder.BIG_ENDIAN).getInt()); }
    public static long timestampMillis(byte[] frame) { return ByteBuffer.wrap(frame, 5, 8).order(ByteOrder.BIG_ENDIAN).getLong(); }
}
