package com.example.picoflexxtest.ndsi

import android.util.Log
import com.example.picoflexxtest.royale.RoyaleCameraDevice
import com.example.picoflexxtest.zmq.NdsiManager
import com.github.luben.zstd.Zstd
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

data class LastCompressionInfo(
    var compressedSize: Int,
    var uncompressedSize: Int,
    var timeMicros: Long
) {
    val ratio get() = this.compressedSize * 100 / this.uncompressedSize
}

class PicoflexxSensor(
    manager: NdsiManager,
    private val camera: RoyaleCameraDevice
) : NdsiSensor(
    "royale_full",
    UUID.nameUUIDFromBytes(camera.getCameraId().toByteArray()).toString(),
    manager
) {
    private val TAG = PicoflexxSensor::class.java.simpleName
    private val dataQueue = ArrayBlockingQueue<ByteArray>(5)
    private val useCases = camera.getUseCases()
    private val cameraName = camera.getCameraName()
    private val cameraId = camera.getCameraId()
    private val width = camera.getMaxSensorWidth()
    private val height = camera.getMaxSensorHeight()
    private var priorExposure: Int = 0
    private var lastExposure: Int = 0
    val lastCompressionData = LastCompressionInfo(0, 0, 0)

    init {
        Log.i(TAG, "Camera getUseCases: ${this.useCases}")
        Log.i(TAG, "Camera getCameraName: ${this.cameraName}")
        Log.i(TAG, "Camera getCameraId: ${this.cameraId}")
        Log.i(TAG, "Camera getMaxSensorWidth: ${this.width}")
        Log.i(TAG, "Camera getMaxSensorHeight: ${this.height}")

        camera.startCapture()

        camera.addEncodedDepthDataCallback {
            try {
                dataQueue.add(it)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "$this: ${e.localizedMessage}")
                e.printStackTrace()
            }
        }
        camera.addExposureTimeCallback {
            this.priorExposure = this.lastExposure
            this.lastExposure = it[1]
        }

        registerControl("usecase", ::getUsecaseControl, ::setUsecaseControl)
        registerControl("autoexposure", ::getAutoExposureControl, ::setAutoExposureControl)
        registerControl("exposuretime", ::getExposureTimeControl, ::setExposureTimeControl)
    }

    protected fun getUsecaseControl(): ControlChanges {
        return ControlChanges(
            value = this.useCases.indexOf(this.camera.getCurrentUseCase()),
            def = 0,
            caption = "Use Case",
            map = this.useCases.mapIndexed { idx, uc ->
                ControlEnumOptions(idx, uc)
            }
        )
    }

    protected fun setUsecaseControl(value: Int) {
        if (value < 0 || value >= this.useCases.size) {
            Log.w(TAG, "Attempted to set an invalid index '$value'")
            return
        }

        this.camera.setUseCase(this.useCases[value])
    }

    protected fun getAutoExposureControl(): ControlChanges {
        return ControlChanges(
            value = this.camera.getExposureMode(),
            dtype = "bool",
            def = true,
            caption = "Auto exposure"
        )
    }

    protected fun setAutoExposureControl(value: Boolean) {
        this.camera.setExposureMode(value)

        this.sendControlState(this.controls["exposuretime"]!!)
    }

    protected fun getExposureTimeControl(): ControlChanges {
        return ControlChanges(
            value = this.lastExposure,
            readonly = this.camera.getExposureMode(),
            min = 0, // TODO
            max = 2000, // TODO
            def = 500, // TODO
            res = 1,
            dtype = "integer",
            caption = "Exposure time"
        )
    }

    protected fun setExposureTimeControl(value: Int) {
        this.camera.setExposureTime(value.toLong())
    }

    override fun hasFrame() = !this.dataQueue.isEmpty()

    override fun publishFrame() {
        val data = dataQueue.poll(200, TimeUnit.MILLISECONDS)

        if (data == null) {
            Log.w(TAG, "$this: Timed out waiting for data")
            return
        }

        lateinit var compressed: ByteArray
        val compressTime = measureNanoTime {
            compressed = Zstd.compress(data, 1)
        }
        this.lastCompressionData.apply {
            compressedSize = compressed.size
            uncompressedSize = data.size
            timeMicros = compressTime
        }

        this.sendFrame(
            NdsiHeader(
                FLAG_ALL,// or FLAG_COMPRESSED,
                this.width,
                this.height,
                0,
                System.currentTimeMillis() / 1000.0,
                -1 // FIXME
            ), compressed
        )

        if (this.priorExposure != this.lastExposure) {
            this.sendControlState(this.controls["exposuretime"]!!)
        }
    }

    override fun unlink() {
        super.unlink()

        this.camera.close()
    }
}
