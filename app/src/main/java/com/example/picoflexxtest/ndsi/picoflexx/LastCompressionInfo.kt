package com.example.picoflexxtest.ndsi.picoflexx

data class LastCompressionInfo(
    var compressedSize: Int,
    var uncompressedSize: Int,
    var timeMicros: Long
) {
    val ratio get() = this.compressedSize * 100 / this.uncompressedSize
}
