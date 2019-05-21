package com.example.picoflexxtest


import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Point
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import com.example.picoflexxtest.royale.AmplitudeListener
import com.example.picoflexxtest.royale.RoyaleCamera
import com.example.picoflexxtest.zmq.NdsiService
import org.jetbrains.anko.startService
import kotlin.experimental.or

class SampleActivity : Activity() {

    private var mUSBManager: UsbManager? = null
    private var mUSBConnection: UsbDeviceConnection? = null

    private var mBitmap: Bitmap? = null
    private var mAmplitudeView: ImageView? = null

    private var mOpened: Boolean = false

    private var mScaleFactor: Int = 0
    private var mResolution: IntArray? = null

    /**
     * broadcast receiver for user usb permission dialog
     */
    private val mUsbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "SampleActivity.onReceive context = [$context], intent = [$intent]")

            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null) {
                        performUsbPermissionCallback(device)
                        RoyaleCamera.registerIrListener {
                            this@SampleActivity.onAmplitudes(it)
                        }
                        createBitmap()
                    }
                } else {
                    println("permission denied for device" + device!!)
                }
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "SampleActivity.onCreate savedInstanceState = [$savedInstanceState]")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)

        Log.d(TAG, "onCreate()")

        val btnStart: Button = findViewById(R.id.buttonStart)
        mAmplitudeView = findViewById(R.id.imageViewAmplitude)
        btnStart.setOnClickListener { openCamera() }
    }

    /**
     * Will be invoked on a new frame captured by the camera.
     *
     * @see com.pmdtec.sample.NativeCamera.AmplitudeListener
     */
    fun onAmplitudes(irData: IntArray) {
        if (!mOpened) {
            Log.d(TAG, "Device in Java not initialized")
            return
        }

        val pixels = irData
            .map {
                it or (it shl 8) or (it shl 16) or (255 shl 24)
            }
            .toIntArray()
        mBitmap!!.setPixels(pixels, 0, mResolution!![0], 0, 0, mResolution!![0], mResolution!![1])

        runOnUiThread {
            mAmplitudeView!!.setImageBitmap(
                Bitmap.createScaledBitmap(
                    mBitmap!!,
                    mResolution!![0] * mScaleFactor,
                    mResolution!![1] * mScaleFactor, false
                )
            )
        }
    }

    fun openCamera() {
        Log.i(TAG, "SampleActivity.openCamera")

        //check permission and request if not granted yet
        mUSBManager = getSystemService(Context.USB_SERVICE) as UsbManager

        if (mUSBManager != null) {
            Log.d(TAG, "Manager valid")
        }

        val deviceList = mUSBManager!!.deviceList

        Log.d(TAG, "USB Devices : " + deviceList.size)

        val iterator = deviceList.values.iterator()
        var device: UsbDevice
        var found = false
        while (iterator.hasNext()) {
            device = iterator.next()
            if (device.vendorId == 0x1C28 ||
                device.vendorId == 0x058B ||
                device.vendorId == 0x1f46
            ) {
                Log.d(TAG, "royale device found")
                found = true
                if (!mUSBManager!!.hasPermission(device)) {
                    val intent = Intent(ACTION_USB_PERMISSION)
                    intent.action = ACTION_USB_PERMISSION
                    val mUsbPi = PendingIntent.getBroadcast(this, 0, intent, 0)
                    mUSBManager!!.requestPermission(device, mUsbPi)
                } else {
                    performUsbPermissionCallback(device)
                    RoyaleCamera.registerIrListener {
                        this.onAmplitudes(it)
                    }
                    createBitmap()

                    startService<NdsiService>("type" to "start")
                }
                break
            }
        }

        if (!found) {
            Log.e(TAG, "No royale device found!!!")
        }
    }

    private fun performUsbPermissionCallback(device: UsbDevice) {
        Log.i(TAG, "SampleActivity.performUsbPermissionCallback device = [$device]")

        mUSBConnection = mUSBManager!!.openDevice(device)
        Log.i(TAG, "permission granted for: ${device.deviceName}, fileDesc: ${mUSBConnection!!.fileDescriptor}")

        val fd = mUSBConnection!!.fileDescriptor

        mResolution = RoyaleCamera.openCameraNative(fd, device.vendorId, device.productId)

        if (mResolution!![0] > 0) {
            mOpened = true
        }
    }

    private fun createBitmap() {
        // calculate scale factor, which scales the bitmap relative to the display mResolution
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val displayWidth = size.x * 0.9
        mScaleFactor = displayWidth.toInt() / mResolution!![0]

        if (mBitmap == null) {
            mBitmap = Bitmap.createBitmap(mResolution!![0], mResolution!![1], Bitmap.Config.ARGB_8888)
        }
    }

    override fun onPause() {
        Log.i(TAG, "SampleActivity.onPause")
        super.onPause()

        if (mOpened) {
            RoyaleCamera.closeCameraNative()
            mOpened = false
        }

        unregisterReceiver(mUsbReceiver)
    }

    override fun onResume() {
        Log.i(TAG, "SampleActivity.onResume")
        super.onResume()

        registerReceiver(mUsbReceiver, IntentFilter(ACTION_USB_PERMISSION))
    }

    override fun onDestroy() {
        Log.i(TAG, "SampleActivity.onDestroy")
        super.onDestroy()

        Log.d(TAG, "onDestroy()")
        unregisterReceiver(mUsbReceiver)

        if (mUSBConnection != null) {
            mUSBConnection!!.close()
        }
    }

    companion object {
        private val TAG = "RoyaleAndroidSampleV3"
        private val ACTION_USB_PERMISSION = "ACTION_ROYALE_USB_PERMISSION"
    }
}
