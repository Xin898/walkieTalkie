package com.walkietalkie.app

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioFrame {
    private const val VERSION: Byte = 1
    private const val HEADER_BYTES = 13

    fun encode(sequence: Long, timestampMillis: Long, opusPacket: ByteArray): ByteArray {
        require(sequence in 0..0xffffffffL)
        require(opusPacket.isNotEmpty())
        return ByteBuffer.allocate(HEADER_BYTES + opusPacket.size).order(ByteOrder.BIG_ENDIAN)
            .put(VERSION).putInt(sequence.toInt()).putLong(timestampMillis).put(opusPacket).array()
    }
}
