package com.example.picoflexxtest.ndsi

import android.util.Log
import com.example.picoflexxtest.ndsi.picoflexx.LastCompressionInfo
import com.example.picoflexxtest.royale.RoyaleCameraDevice
import com.example.picoflexxtest.royale.RoyaleCameraException
import com.example.picoflexxtest.zmq.NdsiManager
import com.github.luben.zstd.Zstd
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

private data class PicoflexxData(
    val encoded: ByteArray,
    val timestamp: Long
)

class PicoflexxSensor(
    manager: NdsiManager,
    private val camera: RoyaleCameraDevice
) : NdsiSensor(
    "royale_full",
    UUID.nameUUIDFromBytes(camera.getCameraId().toByteArray()).toString(),
    "${camera.getCameraName()} - ${camera.getCameraId()}",
    manager
) {
    companion object {
        private val sharedScheduler = Executors.newSingleThreadScheduledExecutor()
        private val TAG = PicoflexxSensor::class.java.simpleName
        private const val CONTROL_USE_CASE = "a__usecase"
        private const val CONTROL_AUTO_EXPOSURE = "b1__auto_exposure"
        private const val CONTROL_EXPOSURE_TIME = "b2__exposure_time"
        private const val CONTROL_FRAME_RATE = "frame_rate"
        private const val CONTROL_FORMAT_FLAGS = "format_flags"
        private const val CONTROL_FLAG_IR = "flag_ir"
        private const val CONTROL_FLAG_POINTCLOUD = "flag_pointcloud"
        private const val CONTROL_FLAG_NOISE = "flag_noise"
        private const val CONTROL_FLAG_CONFIDENCE = "flag_confidence"
        private const val CONTROL_FLAG_COMPRESSED_ZSTD = "flag_compressed_zstd"
    }

    private var futureExposure: ScheduledFuture<*>? = null
    private val dataQueue = ArrayBlockingQueue<PicoflexxData>(5)
    val useCases = camera.getUseCases()
    val cameraName = camera.getCameraName()
    val cameraId = camera.getCameraId()
    override val width = camera.getMaxSensorWidth()
    override val height = camera.getMaxSensorHeight()

    // We register controls with the registerControl delegate. Upon updating
    // any of the control's values, the control will be flagged as dirty and
    // will be updated next cycle in NdsiManager.
    private var currentExposure by registerIntControl(
        CONTROL_EXPOSURE_TIME, "Exposure time", 0,
        getter = {
            it.min = this.minExposure
            it.max = this.maxExposure
            it.readonly = this.autoExposure
            it.def = this.maxExposure
        },
        setter = {
            // We wrap the actual setting of the exposure in a scheduled future
            // so we can essentially rate limit how often it's changed. As it's
            // opaque as to when the previous operation actually completes.
            val futureExposure = this.futureExposure
            if (futureExposure != null) {
                futureExposure.cancel(false)
                this.futureExposure = null
            }
            this.futureExposure = sharedScheduler.schedule({
                this.camera.setExposureTime(it.toLong())
            }, 200, TimeUnit.MILLISECONDS)
        }
    )
    private var autoExposure by registerBooleanControl(
        CONTROL_AUTO_EXPOSURE, "Auto exposure",
        setter = {
            this.camera.setExposureMode(it)

            // Label exposure control as dirty to ensure the read only status is
            // correctly updated
            this.changedControls.add(CONTROL_EXPOSURE_TIME)
            this.updateControlState()
        }
    )
    // Pseudo-control to trigger the exposure control to be updated
    private var minExposure by registerControl(
        null, null, null, 0, updateKey = CONTROL_EXPOSURE_TIME
    )
    // Pseudo-control to trigger the exposure control to be updated
    private var maxExposure by registerControl(
        null, null, null, 2000, updateKey = CONTROL_EXPOSURE_TIME
    )
    private var frameRate by registerIntControl(
        CONTROL_FRAME_RATE, "Frame rate", 2, 1,
        getter = {
            it.min = 1
            it.max = this.maxFrameRate
            it.def = this.maxFrameRate
            it.readonly = true
        }
    )
    private var maxFrameRate by registerControl(
        null, null, null, 2, updateKey = CONTROL_FRAME_RATE
    )
    private var currentUseCase by registerStringMapControl(
        CONTROL_USE_CASE, "Use case", 0, this.useCases,
        setter = {
            this.camera.setUseCase(this.useCases[it])
            this.updateControlState()
        }
    )
    private var formatFlags by registerIntControl(
        CONTROL_FRAME_RATE, "Format flags", FLAG_ALL,
        getter = {
            it.readonly = true
        }
    )
    private var controlFlagIr by registerFlagControl(CONTROL_FLAG_IR, "Send ir", FLAG_IR)
    private var controlFlagPointcloud by registerFlagControl(
        CONTROL_FLAG_POINTCLOUD,
        "Send point cloud",
        FLAG_POINTCLOUD
    )
    private var controlFlagNoise by registerFlagControl(CONTROL_FLAG_NOISE, "Send noise", FLAG_NOISE)
    private var controlFlagConfidence by registerFlagControl(
        CONTROL_FLAG_CONFIDENCE,
        "Send confidence",
        FLAG_CONFIDENCE
    )
    private var controlFlagCompressZstd by registerFlagControl(
        CONTROL_FLAG_COMPRESSED_ZSTD,
        "Send compressed (Zstd)",
        FLAG_COMPRESSED_ZSTD
    )
    val lastCompressionData = LastCompressionInfo(0, 0, 0)
    val queueSize get() = this.dataQueue.size

    init {
        Log.i(TAG, "Camera getUseCases: ${this.useCases}")
        Log.i(TAG, "Camera getCameraName: ${this.cameraName}")
        Log.i(TAG, "Camera getCameraId: ${this.cameraId}")
        Log.i(TAG, "Camera getMaxSensorWidth: ${this.width}")
        Log.i(TAG, "Camera getMaxSensorHeight: ${this.height}")

        camera.startCapture()

        camera.addEncodedDepthDataCallback {
            try {
                dataQueue.add(PicoflexxData(it, System.currentTimeMillis())) // FIXME use timestamp from libroyale
                this.manager.notifySensorReady()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "$this: ${e.localizedMessage}")
                e.printStackTrace()
            }
        }
        camera.addExposureTimeCallback {
            this.currentExposure = it[1]
        }

        this.updateControlState()
    }

    override fun ping(): Boolean {
        try {
            val originalMode = this.autoExposure
            Log.i(TAG, "Set auto exposure=${this.camera.setExposureMode(false)}")
            Log.i(TAG, "Check same exp=${this.camera.setExposureTime(this.currentExposure.toLong())}")
            Log.i(TAG, "Set prev auto exposure=${this.camera.setExposureMode(originalMode)}")
        } catch (e: RoyaleCameraException) {
            if (e.code == 4100) { // DEVICE_IS_BUSY
                Log.w(TAG, "$this: Busy ${e.localizedMessage}")
                return true
            } else if (e.code == 1026 || e.code == 1028) { // DISCONNECTED or TIMEOUT
                Log.w(TAG, "$this: Disconnected ${e.localizedMessage}")
                return false
            }

            e.printStackTrace()
            return false
        }
        return true
    }

    private fun updateControlState() {
        this.currentUseCase = this.useCases.indexOf(this.camera.getCurrentUseCase())
        this.autoExposure = this.camera.getExposureMode()
        val limits = this.camera.getExposureLimits()
        this.frameRate = this.camera.getFrameRate()
        this.maxFrameRate = this.camera.getMaxFrameRate()
        this.minExposure = limits[0]
        this.maxExposure = limits[1]
    }

    override fun hasFrame() = !this.dataQueue.isEmpty()

    override fun publishFrame() {
        val data = dataQueue.poll(200, TimeUnit.MILLISECONDS)
        val flags = this.formatFlags

        if (data == null) {
            Log.w(TAG, "$this: Timed out waiting for data")
            return
        }

        lateinit var compressed: ByteArray
        val compressTime = measureNanoTime {
            compressed = if (flags and FLAG_COMPRESSED_ZSTD != 0) {
                Zstd.compress(data.encoded, 1)
            } else {
                data.encoded
            }
        }
        this.lastCompressionData.apply {
            compressedSize = compressed.size
            uncompressedSize = data.encoded.size
            timeMicros = compressTime / 1000
        }

        this.sendFrame(
            flags,
            this.manager.getAdjustedTime(data.timestamp),
            this.currentExposure,
            compressed
        )
    }

    override fun unlink() {
        super.unlink()

        this.camera.close()
    }

    private fun registerFlagControl(
        controlId: String,
        caption: String,
        flagMask: Int
    ) = registerControl(
        controlId,
        {
            ControlChanges(
                value = (this@PicoflexxSensor.formatFlags and flagMask) == flagMask,
                dtype = "bool",
                caption = caption
            )
        },
        {
            if (value) {
                this@PicoflexxSensor.formatFlags = this@PicoflexxSensor.formatFlags or flagMask
            } else {
                this@PicoflexxSensor.formatFlags = this@PicoflexxSensor.formatFlags and flagMask.inv()
            }
        },
        (this.formatFlags and flagMask) == flagMask,
        updateKey = CONTROL_FORMAT_FLAGS
    )
}
