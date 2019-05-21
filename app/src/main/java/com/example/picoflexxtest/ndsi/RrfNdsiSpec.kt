package com.example.picoflexxtest.ndsi

val FLAG_IR = 1 shl 0
val FLAG_POINTCLOUD = 1 shl 1
val FLAG_NOISE = 1 shl 2
val FLAG_CONFIDENCE = 1 shl 3
val FLAG_ALL = FLAG_IR or FLAG_POINTCLOUD or FLAG_NOISE or FLAG_CONFIDENCE

val FLAG_STRIP = 1 shl 4
val FLAG_COMPRESSED = 1 shl 5
