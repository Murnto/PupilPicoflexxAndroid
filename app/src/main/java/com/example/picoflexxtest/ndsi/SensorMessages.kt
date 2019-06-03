package com.example.picoflexxtest.ndsi

open class SensorMessage(
    val subject: String,
    val sensorUUID: String
)

class SensorAttach(
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

class SensorDetach(
    sensorUUID: String
) : SensorMessage(
    "detach",
    sensorUUID
)

data class ControlEnumOptions(
    val value: Any,
    val caption: String
)

data class ControlChanges(
    var value: Any,
    var dtype: String = "intmapping",
    var min: Any? = null,
    var max: Any? = null,
    var res: Any? = null, // step
    var def: Any? = null,
    var caption: String,
    var readonly: Boolean = false,
    var map: List<ControlEnumOptions>? = null
)

data class UpdateControlMessage(
    val subject: String,
    val controlId: String,
    var seq: Int,
    var changes: ControlChanges
)

data class SensorCommand(
    val action: String,
    val controlId: String? = null,
    val value: Any? = null
)
