package com.example.picoflexxtest

import android.content.ComponentName
import android.content.ServiceConnection
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.ConditionVariable
import android.os.IBinder
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.example.picoflexxtest.zmq.NdsiService
import kotlinx.android.synthetic.main.activity_main2.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.intentFor


class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var mService: NdsiService
    private var mBound: Boolean = false
    private val boundCondition = ConditionVariable()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as NdsiService.TestBindServiceBinder
            mService = binder.getService()
            mBound = true
            boundCondition.open()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
            boundCondition.close()
        }
    }

    private fun waitService(block: (NdsiService) -> Unit) {
        doAsync {
            if (this@MainActivity.boundCondition.block(5_000)) {
                block(this@MainActivity.mService)
            } else {
                Log.e(TAG, "Timed out waiting for NdsiService to bind!")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        setSupportActionBar(toolbar)
        setupNotificationChannels()

        checkUsbIntent()

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
        btnReset.setOnClickListener {
            waitService { it.restartManager() }
        }
        btnDetachAll.setOnClickListener {
            waitService { it.detachAll() }
        }
        btnAttach.setOnClickListener {
            waitService { it.attach() }
        }

        Log.i(TAG, "Will start+bind NdsiService")
        val intent = intentFor<NdsiService>("type" to "start")
        startForegroundService(intent)
        bindService(intent, connection, 0)
    }

    override fun onResume() {
        super.onResume()

        checkUsbIntent()
    }

    private fun checkUsbIntent() {
        if (intent != null) {
            Log.d("onResume", "intent: $intent")
            if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                val usbDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (usbDevice != null) {
                    Log.d(TAG, "USB device attached: name: ${usbDevice.deviceName} ${usbDevice.vendorId}")

                    waitService { it.attach() }
                }
            }
        }
    }

}
