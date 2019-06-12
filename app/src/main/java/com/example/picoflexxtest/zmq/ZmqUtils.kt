package com.example.picoflexxtest.zmq

import org.zeromq.tools.ZmqNativeLoader

object ZmqUtils {
    /**
     * Modify ZmqNativeLoader's private state to mitigate library loading
     * errors in Zsock.
     *
     * Zsock's static init attempts to load native libraries not present in the
     * Android build. The resulting exception isn't properly handled on Android
     * leading to a crash.
     */
    fun nativeZyreHack() {
        ZmqNativeLoader.loadLibrary("czmqjni")
        ZmqNativeLoader.loadLibrary("zyrejni")

        val field = ZmqNativeLoader::class.java.getDeclaredField("loadedLibraries")
        field.isAccessible = true
        val loadedLibs = field.get(null) as MutableSet<String>
        loadedLibs.addAll(
            listOf(
                "uuid",
                "libsystemd",
                "lz4",
                "curl",
//            "czmq,",
                "microhttpd"
            )
        )
    }
}
