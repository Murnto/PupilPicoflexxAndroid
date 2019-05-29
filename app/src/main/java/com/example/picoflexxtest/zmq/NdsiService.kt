package com.example.picoflexxtest.zmq

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.picoflexxtest.*
import com.example.picoflexxtest.ndsi.FLAG_ALL
import com.example.picoflexxtest.ndsi.SensorAttach
import com.example.picoflexxtest.ndsi.SensorDetach
import com.example.picoflexxtest.royale.RoyaleCamera
import com.example.picoflexxtest.royale.RoyaleDepthData
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.luben.zstd.Zstd
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.zyre.Zyre
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import kotlin.reflect.KMutableProperty0


private const val GROUP = "pupil-picoflexx-v1"
val mapper = jacksonObjectMapper().also {
    it.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
    it.setSerializationInclusion(JsonInclude.Include.ALWAYS)
}

class NdsiService : IntentService("NdsiService") {
    private val TAG = NdsiService::class.java.simpleName
    private val zContext = ZContext()
    private lateinit var network: Zyre
    private lateinit var data: ZMQ.Socket
    private lateinit var dataUrl: String
    private lateinit var note: ZMQ.Socket
    private lateinit var noteUrl: String
    private lateinit var cmd: ZMQ.Socket
    private lateinit var cmdUrl: String
    private var connected: Boolean = false
    private val irQueue = ArrayBlockingQueue<IntArray>(1)
    private val dataQueue = ArrayBlockingQueue<RoyaleDepthData>(1)


    override fun onHandleIntent(intent: Intent) {
        Log.d("NDSI wtf", "hello")
        Log.i(TAG, "$intent")
        Log.i(TAG, "${intent.action}")
        Log.i(TAG, "${intent.type}")
        Log.i(TAG, "${intent.data}")
        Log.i(TAG, "${intent.getStringExtra("type")}")

        if (!connected) {
            connect(this)
        }
    }

    private lateinit var fooCtx: Context

    fun connect(context: Context) {
        this.fooCtx = context
        RoyaleCamera.openCamera(fooCtx) {
            Log.i(TAG, "openCamera returned $it")

//            it?.registerIrListener {
//                try {
//                    irQueue.add(it)
//                } catch (e: IllegalStateException) {
//                    e.printStackTrace()
//                }
//            }

            it?.registerDataListener {
                try {
                    dataQueue.add(it)
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
            }

            startServiceLoop()
        }
    }

    private fun startServiceLoop() {
        network = Zyre("test-hostname")
        network.join(GROUP)
        network.setVerbose()
        network.start()
        network.socket().join(GROUP)

        Log.d(TAG, "Bridging under ${network.name()}")

//        val publicEndpoint = network.socket().endpoint()
        Thread.sleep(1000)
        network.print()
//        val publicEndpoint = ZyreShim.getZyreEndpoint(network)
        val publicEndpoint = getWifiIpAddress(fooCtx)!! // FIXME
//        val publicEndpoint = "0.0.0.0"
        val genericUrl = "tcp://*:*"
        Log.i(TAG, "publicEndpoint=$publicEndpoint")

        this.bind(SocketType.PUB, genericUrl, publicEndpoint, this::data, this::dataUrl)
        this.bind(SocketType.PUB, genericUrl, publicEndpoint, this::note, this::noteUrl)
        this.bind(SocketType.PULL, genericUrl, publicEndpoint, this::cmd, this::cmdUrl)

        connected = true // FIXME race condition

        loop()
    }

    fun loop() {
        Log.d(TAG, "Entering bridging loop...")

        this.network.shoutJson(GROUP, this.sensorAttachJson())

        try {
            while (true) {
                Log.d(TAG, "Loop")
                this.pollNetwork()
                this.pollCmdSocket()
                this.publishFrame()
            }
        } finally {
            Log.d(TAG, "Leaving bridging loop...")
            this.network.shoutJson(GROUP, SensorDetach(this.network.uuid()))
        }
    }

    @Deprecated("")
    private fun oldPublishFrame() {
        val irData = irQueue.take()
//        val rawData = irData.map { it.toByte() }.toByteArray()
//        val fooData = ByteBuffer.allocate(irData.size * (1 + 4*4 + 1))
        val rawData = ByteArray(irData.size * (1 + 4 * 4 + 1))

        (0 until 1 + 4 + 4 + 1).forEach { i ->
            (0 until irData.size).forEach { j ->
                rawData[i * irData.size + j] = irData[j].toByte()
            }
        }
//        val zeroArray = irData.map { it.toByte() }.toByteArray()
//        val rawData = fooData.array()

        val buf = ByteBuffer.allocate(8 * 4)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(FLAG_ALL) // Flags
        buf.putInt(224) // Width
        buf.putInt(171) // Height
        buf.putInt(0) // Index
        buf.putDouble(System.currentTimeMillis() / 1000.0) // Now
        buf.putInt(rawData.size) // Data length
        buf.putInt(0) // Lower

        this.data.sendMultiPart(
            this.network.uuid().toByteArray(),
            buf.array(),
            rawData
        )
    }

    private fun publishFrame() {
        val data = dataQueue.take()

        val timeA = System.nanoTime()

        val compressed = Zstd.compress(data.encoded, 1)

        val timeB = System.nanoTime()
        Log.i(
            TAG,
            "Compressed in ${timeB - timeA} nanos, ${(timeB - timeA) / 1000} micros, ${(timeB - timeA) / 1000000} millis"
        )
        val rawData = compressed

        val buf = ByteBuffer.allocate(8 * 4)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(FLAG_ALL) // Flags
        buf.putInt(224) // Width
        buf.putInt(171) // Height
        buf.putInt(0) // Index
        buf.putDouble(System.currentTimeMillis() / 1000.0) // Now
        buf.putInt(rawData.size) // Data length
        buf.putInt(0) // Lower

        val bufArray = buf.array()
        val diff = System.nanoTime() - timeB
        Log.i(TAG, "Encoded in $diff nanos, ${diff / 1000} micros, ${diff / 1000000} millis")

        this.data.sendMultiPart(
            this.network.uuid().toByteArray(),
            bufArray,
            rawData
        )
    }

    private fun pollNetwork() {
        this.network.recentEvents().forEach {
            if (it.type() == "JOIN" && it.group() == GROUP) {
                network.whisperJson(it.peerUuid(), this.sensorAttachJson())
            }
        }
    }

    private fun pollCmdSocket() {
        this.cmd.recentEvents().forEach {
            Log.d(TAG, "cmdSocket recent event = $it")
        }
    }

    private fun sensorAttachJson() = SensorAttach(
        "sensorName",
        this.network.uuid(),
        "royale_full",
        this.noteUrl,
        this.cmdUrl,
        this.dataUrl
    )

    private fun bind(
        type: SocketType,
        bindUrl: String,
        publicEndpoint: String,
        socket: KMutableProperty0<ZMQ.Socket>,
        address: KMutableProperty0<String>,
        setHwm: Int? = 45 * 1 // 1 minute at max frame rate
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