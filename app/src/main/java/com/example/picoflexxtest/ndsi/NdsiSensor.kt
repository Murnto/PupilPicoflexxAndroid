package com.example.picoflexxtest.ndsi

import android.util.Log
import com.example.picoflexxtest.getWifiIpAddress
import com.example.picoflexxtest.recentEvents
import com.example.picoflexxtest.sendMultiPart
import com.example.picoflexxtest.zmq.NdsiManager
import com.example.picoflexxtest.zmq.mapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.zeromq.SocketType
import org.zeromq.ZMQ
import zmq.Msg
import zmq.msg.MsgAllocator
import java.nio.ByteBuffer
import kotlin.reflect.KClass
import kotlin.reflect.KFunction0
import kotlin.reflect.KFunction1

data class ControlInfo(
    val controlId: String,
    val type: KClass<*>,
    val getter: KFunction0<ControlChanges>,
    val setter: KFunction1<*, Unit>
)


abstract class NdsiSensor(
    val sensorType: String,
    val sensorUuid: String,
    protected val manager: NdsiManager
) {
    private val TAG = NdsiSensor::class.java.simpleName

    private lateinit var data: ZMQ.Socket
    private lateinit var dataUrl: String
    private lateinit var note: ZMQ.Socket
    private lateinit var noteUrl: String
    private var noteSequence = 0
    private lateinit var cmd: ZMQ.Socket
    private lateinit var cmdUrl: String

    protected val controls: MutableMap<String, ControlInfo> = hashMapOf()

    init {
        val publicEndpoint = getWifiIpAddress(manager.service)!! // FIXME
        val genericUrl = "tcp://*:*"
        Log.i(TAG, "publicEndpoint=$publicEndpoint")

        this.manager.bind(SocketType.PUB, genericUrl, publicEndpoint, ::data, ::dataUrl)
        this.manager.bind(SocketType.PUB, genericUrl, publicEndpoint, ::note, ::noteUrl)
        this.manager.bind(SocketType.PULL, genericUrl, publicEndpoint, ::cmd, ::cmdUrl)

        data.base().setSocketOpt(zmq.ZMQ.ZMQ_MSG_ALLOCATOR, object : MsgAllocator {
            override fun allocate(size: Int): Msg {
                Log.d(TAG, "(data) Allocating buffer of size $size")
                return Msg(ByteBuffer.allocateDirect(size))
            }
        })
    }

    open fun unlink() {
        this.data.close()
        this.note.close()
        this.cmd.close()
    }

    open fun pollCmdSocket() {
        this.cmd.recentEvents().forEach {
            Log.d(TAG, "cmdSocket recent event = $it")

            val command = mapper.readValue<SensorCommand>(it[1])
            when (command.action) {
                "refresh_controls" -> refreshControls()
                "set_control_value" -> setControlValue(command.controlId!!, command.value!!)
                else -> Log.w(TAG, "Unknown command on cmd socket: $command")
            }
        }
    }

    abstract fun publishFrame()

    protected fun sendFrame(header: NdsiHeader, data: ByteArray) {
        header.dataLength = data.size

        this.data.sendMultiPart(
            this.sensorUuid.toByteArray(),
            header.encode(),
            data
        )
    }

    fun handleNotification() {
        TODO("Implement handleNotification")
    }

    fun refreshControls() {
        controls.values.forEach {
            this.sendControlState(it)
        }
    }

    fun resetAllControlValues() {
        TODO("Implement resetAllControlValues")
    }

    fun resetControlValue(controlId: String) {
        TODO("Implement resetControlValue")
    }

    fun setControlValue(controlId: String, value: Any) {
        val control = this.controls[controlId]
        if (control == null) {
            Log.w(TAG, "Tried to set value on unknown control! control=$controlId value=$value")
            return
        }

        control.setter.call(value)
        this.sendControlState(control)
    }

    open fun sensorAttachJson() = SensorAttach(
        "sensorName",
        this.sensorUuid,
        this.sensorType,
        this.noteUrl,
        this.cmdUrl,
        this.dataUrl
    )

    protected inline fun <reified T> registerControl(
        controlId: String,
        getter: KFunction0<ControlChanges>,
        setter: KFunction1<T, Unit>
    ) {
        controls[controlId] = ControlInfo(controlId, T::class, getter, setter)
    }

    protected fun sendControlState(control: ControlInfo) {
        note.sendMultiPart(
            this.sensorUuid.toByteArray(),
            mapper.writeValueAsBytes(
                UpdateControlMessage(
                    subject = "update",
                    controlId = control.controlId,
                    seq = this.noteSequence,
                    changes = control.getter()
                )
            )
        )

        this.noteSequence += 1
    }
}
