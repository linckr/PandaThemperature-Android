package com.example.pandatemperature.data.device.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RealtimeDataParserTest {

    private val parser = RealtimeDataParserV2()

    @Test
    fun parsesLegacySixByteRealtimePacketWithoutVoltage() {
        val packet = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(2534.toShort()) // 25.34 C
            .putShort(4567.toShort()) // 45.67 %RH
            .putShort(10132.toShort()) // 1013.2 hPa
            .array()

        val result = parser.parse(packet)

        assertEquals(25.34f, result?.temperature ?: error("temperature missing"), 0.001f)
        assertEquals(45.67f, result?.humidity ?: error("humidity missing"), 0.001f)
        assertEquals(1013.2f, result?.pressure ?: error("pressure missing"), 0.001f)
        assertNull(result?.batteryVoltage)
    }

    @Test
    fun parsesOptionalEightByteRealtimePacketVoltageInVolts() {
        val packet = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(2534.toShort())
            .putShort(4567.toShort())
            .putShort(10132.toShort())
            .putShort(2900.toShort())
            .array()

        val result = parser.parse(packet)

        assertEquals(2.9f, result?.batteryVoltage ?: error("voltage missing"), 0.001f)
    }
}
