package com.example.picoflexxtest.ndsi

import android.util.Log
import com.example.picoflexxtest.royale.RoyaleCameraDevice
import com.example.picoflexxtest.timeExec
import com.example.picoflexxtest.zmq.NdsiManager
import com.github.luben.zstd.Zstd
import java.util.concurrent.ArrayBlockingQueue

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
                e.printStackTrace()
            }
        }
    }

    override fun publishFrame() {
        val data = dataQueue.take()
        val compressed = timeExec(TAG, "Compressing frame data") {
            Zstd.compress(data, 1)
        }
        Log.d(TAG, "Compression: ${compressed.size}/${data.size} = ${compressed.size * 100 / data.size}")

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
    }
}