package com.andrerinas.openheadunit.librehu

import org.junit.Assert.assertEquals
import org.junit.Test

class LibreHuPhoneNotificationsTest {

    @Test
    fun shapeKeepsFieldNumbersAndLengthsButNotTheText() {
        // 1: varint 4, 2: "abc", 3: fixed32
        val bytes = byteArrayOf(0x08, 0x04, 0x12, 0x03, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0x1d, 1, 2, 3, 4)
        assertEquals("1:v=4 2:len=3 3:f32", LibreHuPhoneNotifications.shape(bytes))
    }

    @Test
    fun shapeOfAnEmptyMessage() {
        assertEquals("-", LibreHuPhoneNotifications.shape(ByteArray(0)))
    }

    @Test
    fun shapeStopsWhereItIsNotProtobuf() {
        assertEquals("?", LibreHuPhoneNotifications.shape(byteArrayOf(0xff.toByte())))
        // A length past the end of the message
        assertEquals("2:len=9?", LibreHuPhoneNotifications.shape(byteArrayOf(0x12, 0x09, 0x01)))
    }
}
