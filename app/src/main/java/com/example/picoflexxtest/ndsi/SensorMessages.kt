package com.example.picoflexxtest.ndsi

open class SensorMessage (
    val subject: String,
    val sensorUUID: String
)

class SensorAttach (
    val sensorName: String,
    sensorUUID: String,
    val sensorType: String,
    val notifyEndpoint: String,
    val commandEndpoint: String,
    val dataEndpoint: String
) : SensorMessage(
    "attach",
    sensorUUID
)

class SensorDetach (
    sensorUUID: String
) : SensorMessage(
    "detach",
    sensorUUID
)
