package com.example.picoflexxtest.royale

data class RoyaleDepthData(
    val version: Int,
    val timestamp: Long,
    val streamId: Int,
    val width: Int,
    val height: Int,
    val exposureTimes: IntArray,  // length=2 or 3
    val pointCloud: Array<FloatArray>,  // [width * height][3]
    val noise: FloatArray,  // [width * height]
    val confidence: IntArray,  // [width * height]
    val grayValue: IntArray,  // [width * height]
    val encoded: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RoyaleDepthData

        if (version != other.version) return false
        if (timestamp != other.timestamp) return false
        if (streamId != other.streamId) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (!exposureTimes.contentEquals(other.exposureTimes)) return false
        if (!pointCloud.contentDeepEquals(other.pointCloud)) return false
        if (!noise.contentEquals(other.noise)) return false
        if (!confidence.contentEquals(other.confidence)) return false
        if (!grayValue.contentEquals(other.grayValue)) return false
        if (!encoded.contentEquals(other.encoded)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + streamId
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + exposureTimes.contentHashCode()
        result = 31 * result + pointCloud.contentDeepHashCode()
        result = 31 * result + noise.contentHashCode()
        result = 31 * result + confidence.contentHashCode()
        result = 31 * result + grayValue.contentHashCode()
        result = 31 * result + encoded.contentHashCode()
        return result
    }
}