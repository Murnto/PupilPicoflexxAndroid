package com.example.picoflexxtest

import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import android.util.Log
import com.example.picoflexxtest.ndsi.SensorMessage
import com.example.picoflexxtest.zmq.mapper
import org.zeromq.ZMQ
import org.zeromq.zyre.Zyre
import org.zeromq.zyre.ZyreEvent
import java.math.BigInteger
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteOrder
import java.util.ArrayList


fun getWifiIpAddress(context: Context): String? {
    val wifiManager = context.getSystemService(WIFI_SERVICE) as WifiManager
    var ipAddress = wifiManager.connectionInfo.ipAddress

    // Convert little-endian to big-endianif needed
    if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
        ipAddress = Integer.reverseBytes(ipAddress)
    }

    val ipByteArray = BigInteger.valueOf(ipAddress.toLong()).toByteArray()

    try {
        return InetAddress.getByAddress(ipByteArray).hostAddress
    } catch (ex: UnknownHostException) {
        Log.e("WIFIIP", "Unable to get host address.")
        return null
    }
}

fun Zyre.shoutJson(group: String, obj: SensorMessage) {
    this.shouts(group, mapper.writeValueAsString(obj).also {
        this.print()
        Log.d("Zyre.shoutJson", it)
    })
}

fun Zyre.whisperJson(peer: String, obj: SensorMessage) {
    this.whispers(peer, mapper.writeValueAsString(obj))
}

fun Zyre.recentEvents() = sequence {
    while (this@recentEvents.socket().events() and ZMQ.Poller.POLLIN != 0) {
        yield(ZyreEvent(this@recentEvents))
    }
}

fun ZMQ.Socket.recentEvents() = sequence {
    while (this@recentEvents.events and ZMQ.Poller.POLLIN != 0) {
        val multipart = this@recentEvents.recvMultipart()
        Log.d("Socket.recentEvents()", "multipart=$multipart")
        yield(multipart)
    }
}

fun ZMQ.Socket.recvMultipart(): ArrayList<String> {
    val data = arrayListOf(recvStr())

    while (this@recvMultipart.hasReceiveMore()) {
        data.add(recvStr())
    }

    return data
}

fun ZMQ.Socket.sendMultiPart(vararg data: Any) {
    data.forEachIndexed { i, s ->
        if (i == data.size - 1) {
            when (s) {
                is String -> this@sendMultiPart.send(s)
                is ByteArray -> this@sendMultiPart.send(s)
                else -> {
                    Log.e("ZMQ.Socket.sendMultiPart", "Bad argument type ${s.javaClass}")
                    throw IllegalArgumentException(s.javaClass.canonicalName)
                }
            }
        } else {
            when (s) {
                is String -> this@sendMultiPart.sendMore(s)
                is ByteArray -> this@sendMultiPart.sendMore(s)
                else -> {
                    Log.e("ZMQ.Socket.sendMultiPart", "Bad argument type ${s.javaClass}")
                    throw IllegalArgumentException(s.javaClass.canonicalName)
                }
            }
        }
    }

    Log.i("ZMQ.Socket.sendMultiPart", "Sent multipart: ${data.toList()}")
}
