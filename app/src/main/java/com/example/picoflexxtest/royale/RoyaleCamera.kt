package com.example.picoflexxtest.royale

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * This class contains the native methods used in this sample.
 * This class is only used in a static context.
 */
object RoyaleCamera {
    private const val TAG = "RoyaleCamera"
    private var pIrListener: Long? = null
    private var pDataListener: Long? = null
    private val ACTION_USB_PERMISSION = "ACTION_ROYALE_USB_PERMISSION"

    // Used to load the 'royaleSample' library on application startup.
    init {
        System.loadLibrary("usb_android")
        System.loadLibrary("royale")
        System.loadLibrary("native-lib")
    }

    external fun openCameraNative(fd: Int, vid: Int, pid: Int): IntArray

    external fun closeCameraNative()

    external fun __registerDataListener(dataListener: DataListener): Long
    external fun __unregisterDataListener(pDataListener: Long)

    fun registerDataListener(irListener: DataListener) {
        if (pDataListener != null) {
            this.__unregisterDataListener(pDataListener!!)
        }

        this.pDataListener = __registerDataListener(irListener)
    }

    fun registerDataListener(block: (RoyaleDepthData) -> Unit) =
        RoyaleCamera.registerDataListener(object : DataListener {
            override fun onData(data: RoyaleDepthData) = block(data)
        })

    external fun __registerIrListener(irListener: IrListener): Long
    external fun __unregisterIrListener(pIrListener: Long)

    fun registerIrListener(irListener: IrListener) {
        if (pIrListener != null) {
            this.__unregisterIrListener(pIrListener!!)
        }

        this.pIrListener = __registerIrListener(irListener)
    }

    fun registerIrListener(block: (IntArray) -> Unit) =
        RoyaleCamera.registerIrListener(object : IrListener {
            override fun onIrData(amplitudes: IntArray) = block(amplitudes)
        })

    fun destroy() {
        // TODO
    }

    fun openCamera(context: Context, block: (RoyaleCamera?) -> Unit) {
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
            performUsbPermissionCallback(context, device)

            block(RoyaleCamera)
        }

    }

    private fun registerReceiverFor(context: Context, block: (RoyaleCamera?) -> Unit) {
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.i(TAG, "mUsbReceiver.onReceive context = [$context], intent = [$intent]")

                val action = intent.action
                if (ACTION_USB_PERMISSION == action) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            performUsbPermissionCallback(context, device)

                            block(RoyaleCamera)
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

    private fun performUsbPermissionCallback(context: Context, device: UsbDevice): Boolean {
        Log.i(TAG, "SampleActivity.performUsbPermissionCallback device = [$device]")

        val usbManager: UsbManager? = context.getSystemService(Context.USB_SERVICE) as UsbManager?
        if (usbManager == null) {
            Log.e(TAG, "Manager not valid!")
            return false
        }

        val usbConnection = usbManager.openDevice(device)
        Log.i(TAG, "permission granted for: ${device.deviceName}, fileDesc: ${usbConnection.fileDescriptor}")

        val fd = usbConnection.fileDescriptor

        val resolution = RoyaleCamera.openCameraNative(fd, device.vendorId, device.productId)

        return resolution.isNotEmpty() && resolution[0] > 0
    }
}
