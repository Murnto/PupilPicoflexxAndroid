package com.example.picoflexxtest.zmq

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.picoflexxtest.FOREGROUND_NDSI_SERVICE
import com.example.picoflexxtest.MainActivity
import com.example.picoflexxtest.R
import com.example.picoflexxtest.connectivityManager
import com.example.picoflexxtest.ndsi.PicoflexxSensor
import com.example.picoflexxtest.royale.RoyaleCameraDevice
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicBoolean

class NdsiService : Service() {
    private val binder = TestBindServiceBinder()
    private val initialized = AtomicBoolean(false)
    private lateinit var manager: NdsiManager
    private val initializingDevice = AtomicBoolean(false)
    val sensors get() = manager.sensors

    inner class TestBindServiceBinder : Binder() {
        fun getService() = this@NdsiService
    }

    companion object {
        private val ONGOING_NOTIFICATION_ID = 1
        private const val TAG = "NdsiService"
    }

    fun restartManager() {
        this.manager.resetNetwork()
    }

    fun detachAll() {
        this.manager.removeAllSensors()
    }

    fun attach() {
        this.attemptConnectPicoflexx()
    }

    private fun setupForegroundNotificaiton() {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val notification: Notification = Notification.Builder(this, FOREGROUND_NDSI_SERVICE)
            .setContentTitle(getText(R.string.notification_title))
            .setContentText(getText(R.string.notification_message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setTicker(getText(R.string.ticker_text))
            .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.i(TAG, "onBind($intent)")
        return binder
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        Log.i(TAG, "onConfigurationChanged($newConfig)")
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.i(TAG, "onRebind($intent)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand($intent, $flags, $startId)")

        if (this.initialized.getAndSet(true)) {
            Log.w(TAG, "onStartCommand(): ${this.javaClass.simpleName} is already initialized!")
            return START_STICKY
        }

        this.manager = NdsiManager()
        this.manager.start()

        this.registerWifiStateReceiver()
        this.attemptConnectPicoflexx()

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate()")

        setupForegroundNotificaiton()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.i(TAG, "onLowMemory()")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved($rootIntent)")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.i(TAG, "onTrimMemory($level)")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind($intent)")
        return true
    }

    private fun attemptConnectPicoflexx() {
        Log.d(TAG, "initializeConnectedPicoflexx")
        if (initializingDevice.getAndSet(true)) {
            Log.w(TAG, "Already in the process of connecting existing device")
            return
        }

        Thread {
            try {
                RoyaleCameraDevice.openCamera(this) {
                    Log.i(TAG, "openCamera returned $it")

                    if (it != null) {
                        this.manager.addSensor(PicoflexxSensor(this.manager, it))
                    }

                    initializingDevice.set(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                initializingDevice.set(false)
            }
        }.start()
    }

    private fun registerWifiStateReceiver() {
        this.connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                Log.d(TAG, "onCapabilitiesChanged($network, $networkCapabilities)")
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "onLost($network)")
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                Log.d(TAG, "onLinkPropertiesChanged($network, $linkProperties)")

                val ip4Address = linkProperties.linkAddresses
                    .firstOrNull { it.address is Inet4Address }
                Log.d(TAG, "onLinkPropertiesChanged ip4Address=$ip4Address")
                if (ip4Address != null) {
                    this@NdsiService.manager.currentListenAddress = ip4Address.address.hostAddress
                }
            }

            override fun onAvailable(network: Network) {
                Log.d(TAG, "onAvailable($network)")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy()")

        stopForeground(true)
    }
}
