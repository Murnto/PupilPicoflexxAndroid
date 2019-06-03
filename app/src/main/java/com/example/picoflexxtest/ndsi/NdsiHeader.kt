package com.example.picoflexxtest.ndsi

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class NdsiHeader(
    var flags: Int,
    var width: Int,
    var height: Int,
    var index: Int,
    var timestamp: Double,
    var exposure: Int,
    var dataLength: Int = -1
) {
    fun encode(): ByteArray {
        val buf = ByteBuffer.allocate(8 * 4)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(this.flags) // Flags
        buf.putInt(this.width) // Width
        buf.putInt(this.height) // Height
        buf.putInt(this.index) // Index
        buf.putDouble(this.timestamp) // Now
        buf.putInt(this.dataLength) // Data length
        buf.putInt(this.exposure) // Lower

        return buf.array()
    }
}