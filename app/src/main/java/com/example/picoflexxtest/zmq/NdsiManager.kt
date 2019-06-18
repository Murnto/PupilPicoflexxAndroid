package com.example.picoflexxtest.zmq

import android.util.Log
import com.example.picoflexxtest.ndsi.NdsiSensor
import com.example.picoflexxtest.ndsi.SensorDetach
import com.example.picoflexxtest.recentEvents
import com.example.picoflexxtest.shoutJson
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.rotol.pupil.timesync.TimeSync
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.zyre.Zyre
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KMutableProperty0


private const val GROUP = "pupil-picoflexx-v1"
val mapper = jacksonObjectMapper().also {
    it.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
    it.setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
}

class NdsiManager {
    private var network: Zyre
    private lateinit var timeSync: TimeSync
    private val TAG = NdsiManager::class.java.simpleName
    private val zContext = ZContext()
    val sensors: MutableMap<String, NdsiSensor> = ConcurrentHashMap()
    private val periodicShouter = Executors.newSingleThreadScheduledExecutor()
    private val sleepLock = ReentrantLock()
    private val sleepCondition = this.sleepLock.newCondition()
    var currentListenAddress: String? = null
        set(value) {
            if (field != null && field != value) {
                field = value

                this.resetNetwork()
            } else {
                field = value
            }
        }
    private val hostname = "test-hostname"

    init {
        ZmqUtils.nativeZyreHack()

        this.network = Zyre(hostname)
        this.network.setVerbose()
    }

    fun start() {
        Thread {
            startServiceLoop()
        }.start()
    }

    fun resetNetwork(soft: Boolean = false) {
        Log.i(TAG, "resetNetwork()")
        this.notifyAllSensorsDetached()
        this.network.leave(GROUP)
        this.timeSync.stop()

        if (!soft) {
            // Completely replace the Zyre network
            this.network.stop()
            this.network.close()

            this.network = Zyre(hostname)
            this.network.start()
        }

        this.timeSync.restartDiscovery(this.network)
        this.network.join(GROUP)

        this.sensors.forEach {
            it.value.setupSockets()
        }

        this.notifySensorsAttached()
    }

    fun removeAllSensors() {
        this.sensors.values.forEach {
            this.removeSensor(it)
        }
    }

    fun addSensor(sensor: NdsiSensor) {
        Log.i(TAG, "Adding sensor $sensor")
        sensor.setupSockets()

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

    fun removeSensor(sensor: NdsiSensor) {
        Log.i(TAG, "Removing sensor $sensor")

        this.sensors.remove(sensor.sensorUuid)
        this.notifySensorDetached(sensor)
        sensor.unlink()
    }

    private fun startServiceLoop() {
        this.network.start()
        this.network.join(GROUP)

        this.timeSync = TimeSync(existingNetwork = this.network)

        Log.d(TAG, "Bridging under ${this.network.name()}")

        loop()
    }

    fun loop() {
        Log.d(TAG, "Entering bridging loop...")

        this.periodicShouter.scheduleAtFixedRate({
            this.notifySensorsAttached()
        }, 0, 10, TimeUnit.SECONDS)

        try {
            while (true) {
                val loopStart = System.currentTimeMillis()

                this.timeSync.pollNetwork()
                this.pollNetwork()

                // FIXME this won't perform well if we ever use multiple sensors
                sensors.values.forEach {
                    it.pollCmdSocket()
                    while (it.hasFrame()) {
                        it.publishFrame()
                    }
                    it.sendUpdatedControls()
                }

                // Iterate at most once a second without new data
                val took = System.currentTimeMillis() - loopStart
                if (took < 1000) {
                    this.sleepLock.withLock {
                        this.sleepCondition.await(1000 - took, TimeUnit.MILLISECONDS)
                    }
                }
            }
        } finally {
            Log.d(TAG, "Leaving bridging loop...")
            this.notifyAllSensorsDetached()
        }
    }

    /**
     * Ping every sensor to check they're still present and alive
     */
    fun checkAllSensors() {
        Log.i(TAG, "NdsiManager.checkAllSensors()")
        val failed = ArrayList<NdsiSensor>()

        this.sensors.values.forEach {
            if (!it.ping()) {
                failed.add(it)
            }
        }

        // Detach all sensors that failed the check
        failed.forEach(this::removeSensor)
    }

    /**
     * Indicate that a sensor has new data available
     */
    fun notifySensorReady() {
        this.sleepLock.withLock {
            this.sleepCondition.signalAll()
        }
    }

    private fun pollNetwork() {
        this.network.recentEvents().forEach {
            Log.d(TAG, "type=${it.type()}, group=${it.group()}, peer=${it.peerName()}|${it.peerUuid()}")
            if (it.type() == "JOIN" && it.group() == GROUP) {
                this.notifySensorsAttached()
            }
        }
    }

    private fun notifySensorsAttached() =
        sensors.values.forEach {
            network.shoutJson(GROUP, it.sensorAttachJson())
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
