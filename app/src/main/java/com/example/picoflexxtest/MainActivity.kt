package com.example.picoflexxtest

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity;

import kotlinx.android.synthetic.main.activity_main2.*
import android.hardware.usb.UsbDevice.getDeviceName
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDevice
import android.content.Intent
import android.util.Log
import com.example.picoflexxtest.zmq.NdsiService
import org.jetbrains.anko.startService


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        checkUsbIntent()
//        startService<NdsiService>("type" to "start")
        Thread {
            val s = NdsiService()
            s.connect(this)
        }.start()
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
                    Log.d("onResume", "USB device attached: name: ${usbDevice.deviceName} ${usbDevice.vendorId}")
                }
            }
        }
    }

}
