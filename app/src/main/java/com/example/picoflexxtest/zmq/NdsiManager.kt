package com.example.picoflexxtest.zmq

import android.content.Context
import android.util.Log
import com.example.picoflexxtest.ndsi.NdsiSensor
import com.example.picoflexxtest.ndsi.PicoflexxSensor
import com.example.picoflexxtest.ndsi.SensorDetach
import com.example.picoflexxtest.recentEvents
import com.example.picoflexxtest.royale.RoyaleCameraDevice
import com.example.picoflexxtest.shoutJson
import com.example.picoflexxtest.whisperJson
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.rotol.pupil.timesync.TimeSync
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.zyre.Zyre
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KMutableProperty0


private const val GROUP = "pupil-picoflexx-v1"
val mapper = jacksonObjectMapper().also {
    it.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
    it.setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
}

class NdsiManager(
    val service: NdsiService
) {
    private lateinit var timeSync: TimeSync
    private val TAG = NdsiManager::class.java.simpleName
    private val zContext = ZContext()
    private lateinit var network: Zyre
    private var connected: Boolean = false
    private val sensors: MutableMap<String, NdsiSensor> = hashMapOf()
    private val periodicShouter = Executors.newSingleThreadScheduledExecutor()

    fun start() {
        Thread {
            startServiceLoop()
        }.start()
    }

    fun connect() {
        Log.d(TAG, "hello")

        if (!connected) {
            Thread {
                connect(service)
            }.start()
        }
    }

    private lateinit var fooCtx: Context

    private fun connect(context: Context) {
        this.fooCtx = context
        RoyaleCameraDevice.openCamera(context) {
            Log.i(TAG, "openCamera returned $it")

            if (it != null) {
                this.addSensor(PicoflexxSensor(this, it))
            }
        }
    }

    private fun addSensor(sensor: NdsiSensor) {
        Log.i(TAG, "Adding sensor $sensor")

        val existing = this.sensors[sensor.sensorUuid]
        if (existing != null) {
            if (existing == sensor) {
                return
            }

            this.removeSensor(existing)
        }

        this.sensors[sensor.sensorUuid] = sensor
        this.notifySensorsAttached()
    }

    private fun removeSensor(sensor: NdsiSensor) {
        Log.i(TAG, "Removing sensor $sensor")

        sensor.unlink()
        this.sensors.remove(sensor.sensorUuid)
        this.notifySensorDetached(sensor)
    }

    private fun startServiceLoop() {
        this.network = Zyre("test-hostname")
        this.network.join(GROUP)
//        this.network.setInterface("swlan0")
//        this.network.setEndpoint("192.168.43.1")
        this.network.setVerbose()
        this.network.start()
        this.network.socket().join(GROUP)

        this.timeSync = TimeSync(existingNetwork = this.network)

        Log.d(TAG, "Bridging under ${this.network.name()}")

//        val publicEndpoint = this.network.socket().endpoint()
        Thread.sleep(1000)
        this.network.print()
//        val publicEndpoint = ZyreShim.getZyreEndpoint(network)

        connected = true // FIXME race condition

        loop()
    }

    fun loop() {
        Log.d(TAG, "Entering bridging loop...")

        this.periodicShouter.scheduleAtFixedRate({
            this.notifySensorsAttached()
        }, 0, 10, TimeUnit.SECONDS)

        try {
            while (true) {
                this.timeSync.pollNetwork()
                this.pollNetwork()

                // FIXME this won't perform well if we ever use multiple sensors
                sensors.values.forEach {
                    it.pollCmdSocket()
                    it.publishFrame()
                }
            }
        } finally {
            Log.d(TAG, "Leaving bridging loop...")
            this.notifyAllSensorsDetached()
        }
    }

    private fun pollNetwork() {
        this.network.recentEvents().forEach {
            Log.d(TAG, "type=${it.type()}, group=${it.group()}, peer=${it.peerName()}|${it.peerUuid()}")
            if (it.type() == "JOIN" && it.group() == GROUP) {
                this.notifySensorsAttached(it.peerUuid())
            }
        }
    }

    private fun notifySensorsAttached(peerUuid: String? = null) =
        sensors.forEach { (k, v) ->
            if (peerUuid == null) {
                network.shoutJson(GROUP, v.sensorAttachJson())
            } else {
                network.whisperJson(peerUuid, v.sensorAttachJson())
            }
        }

    private fun notifySensorDetached(sensor: NdsiSensor) =
        this.network.shoutJson(GROUP, SensorDetach(sensor.sensorUuid))

    private fun notifyAllSensorsDetached() =
        this.sensors.values.forEach {
            this.network.shoutJson(GROUP, SensorDetach(it.sensorUuid))
        }

    fun bind(
        type: SocketType,
        bindUrl: String,
        publicEndpoint: String,
        socket: KMutableProperty0<ZMQ.Socket>,
        address: KMutableProperty0<String>,
        setHwm: Int? = 50 // 10 seconds at min frame rate
    ) {
        val zSock = zContext.createSocket(type)
        if (setHwm != null) {
            zSock.hwm = setHwm
        }
        zSock.bind(bindUrl)

        val socketEndpoint = zSock.lastEndpoint
        val port = socketEndpoint.split(":").last().toInt()
        Log.i(TAG, "original endpoint=$socketEndpoint")

        socket.set(zSock)
        address.set("tcp://$publicEndpoint:$port")

        Log.i(TAG, "Bound ${socket.name} to ${address.get()}")
    }
}
