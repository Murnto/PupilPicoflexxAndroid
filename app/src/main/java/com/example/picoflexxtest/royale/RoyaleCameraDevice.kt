package com.example.picoflexxtest.royale

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

class RoyaleCameraDevice {
    companion object {
        private val TAG = RoyaleCameraDevice::class.java.simpleName
        private val ACTION_USB_PERMISSION = "ACTION_ROYALE_USB_PERMISSION"

        init {
            System.loadLibrary("usb_android")
            System.loadLibrary("royale")
            System.loadLibrary("native-lib")
        }

        fun openCamera(context: Context, block: (RoyaleCameraDevice?) -> Unit) {
            Log.i(TAG, "SampleActivity.openCamera")

            //check permission and request if not granted yet
            val usbManager: UsbManager? = context.getSystemService(Context.USB_SERVICE) as UsbManager?
            if (usbManager == null) {
                Log.e(TAG, "Manager not valid!")
                block(null)
                return
            }

            val deviceList = usbManager.deviceList
            Log.d(TAG, "USB Devices : " + deviceList.size)

            val device = deviceList.values
                .firstOrNull { it.vendorId == 0x1C28 || it.vendorId == 0x058B || it.vendorId == 0x1f46 }
            if (device == null) {
                Log.e(TAG, "No royale device found!!!")
                block(null)
                return
            }

            Log.d(TAG, "royale device found")
            if (!usbManager.hasPermission(device)) {
                val intent = Intent(ACTION_USB_PERMISSION)
                intent.action = ACTION_USB_PERMISSION
                val mUsbPi = PendingIntent.getBroadcast(context, 0, intent, 0)

                registerReceiverFor(context, block)
                usbManager.requestPermission(device, mUsbPi)
            } else {
                val camera = performUsbPermissionCallback(context, device)

                block(camera)
            }

        }

        private fun registerReceiverFor(context: Context, block: (RoyaleCameraDevice?) -> Unit) {
            val usbReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    Log.i(TAG, "mUsbReceiver.onReceive context = [$context], intent = [$intent]")

                    val action = intent.action
                    if (ACTION_USB_PERMISSION == action) {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            if (device != null) {
                                val camera = performUsbPermissionCallback(context, device)

                                block(camera)
                            }
                        } else {
                            println("permission denied for device" + device!!)
                        }

                        context.unregisterReceiver(this)
                    }
                }
            }

            context.registerReceiver(usbReceiver, IntentFilter(ACTION_USB_PERMISSION))
        }

        private fun performUsbPermissionCallback(context: Context, device: UsbDevice): RoyaleCameraDevice? {
            Log.i(TAG, "SampleActivity.performUsbPermissionCallback device = [$device]")

            val usbManager: UsbManager? = context.getSystemService(Context.USB_SERVICE) as UsbManager?
            if (usbManager == null) {
                Log.e(TAG, "Manager not valid!")
                return null
            }

            val usbConnection = usbManager.openDevice(device)
            Log.i(TAG, "permission granted for: ${device.deviceName}, fileDesc: ${usbConnection.fileDescriptor}")

            val fd = usbConnection.fileDescriptor

            val camera = RoyaleCameraDevice()
            camera.openCameraNative(fd, device.vendorId, device.productId)
            return camera
        }
    }

    @JvmField
    var __ptr: Long = 0

    private external fun init()
    private external fun deinit()

    init {
        init()
    }

    external fun openCameraNative(fd: Int, vid: Int, pid: Int)

    external fun getUseCases(): List<String>
    external fun getCurrentUseCase(): String
    external fun setUseCase(usecase: String)
    external fun startCapture()
    external fun stopCapture()
    external fun getCameraName(): String
    external fun getCameraId(): String
    external fun getMaxSensorWidth(): Int
    external fun getMaxSensorHeight(): Int
    external fun getExposureMode(): Boolean
    external fun setExposureMode(autoExposure: Boolean)
    external fun setExposureTime(exposureTime: Long)

    private val fullDepthDataCallbacks: ArrayList<(RoyaleDepthData) -> Unit> = arrayListOf()
    private fun onFullDepthData(data: RoyaleDepthData) =
        fullDepthDataCallbacks.forEach {
            it(data)
        }

    fun addFullDepthDataCallback(block: (RoyaleDepthData) -> Unit) =
        fullDepthDataCallbacks.add(block)

    private val encodedDepthDataCallbacks: ArrayList<(ByteArray) -> Unit> = arrayListOf()
    private fun onEncodedDepthData(data: ByteArray) =
        encodedDepthDataCallbacks.forEach {
            it(data)
        }

    fun addEncodedDepthDataCallback(block: (ByteArray) -> Unit) =
        encodedDepthDataCallbacks.add(block)

    private val exposureTimeCallbacks: ArrayList<(IntArray) -> Unit> = arrayListOf()
    private fun onExposureTime(data: IntArray) =
        exposureTimeCallbacks.forEach {
            it(data)
        }

    fun addExposureTimeCallback(block: (IntArray) -> Unit) =
        exposureTimeCallbacks.add(block)
}
