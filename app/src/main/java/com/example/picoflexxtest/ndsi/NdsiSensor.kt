package com.example.picoflexxtest.ndsi

import android.util.Log
import com.example.picoflexxtest.getWifiIpAddress
import com.example.picoflexxtest.recentEvents
import com.example.picoflexxtest.sendMultiPart
import com.example.picoflexxtest.zmq.NdsiManager
import org.zeromq.SocketType
import org.zeromq.ZMQ
import zmq.Msg
import zmq.msg.MsgAllocator
import java.nio.ByteBuffer

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
    private lateinit var cmd: ZMQ.Socket
    private lateinit var cmdUrl: String

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

    fun unlink() {
        TODO("Implement unlink")
    }

    open fun pollCmdSocket() {
        this.cmd.recentEvents().forEach {
            Log.d(TAG, "cmdSocket recent event = $it")
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
        TODO("Implement refreshControls")
    }

    fun resetAllControlValues() {
        TODO("Implement resetAllControlValues")
    }

    fun resetControlValue(controlId: String) {
        TODO("Implement resetControlValue")
    }

    fun setControlValue(controlId: String, value: String) {
        TODO("Implement setControlValue")
    }

    open fun sensorAttachJson() = SensorAttach(
        "sensorName",
        this.sensorUuid,
        this.sensorType,
        this.noteUrl,
        this.cmdUrl,
        this.dataUrl
    )
}