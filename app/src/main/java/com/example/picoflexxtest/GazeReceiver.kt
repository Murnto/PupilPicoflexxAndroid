package com.example.picoflexxtest

import com.example.picoflexxtest.GazeReceiver.State.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZPoller
import java.nio.channels.SelectableChannel
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class GazeReceiver(
    private val zContext: ZContext
) : AnkoLogger, ZPoller.EventsHandler {
    var state: State = UNINITIALIZED
        private set(value) {
            info("state transition $field -> $value")
            field = value
        }
    private val lock = ReentrantLock()
    private val poller = ZPoller(this.zContext)
    private val pollerThread = Thread {
        while (true) {
            this.poller.poll(-1)
        }
    }
    private var pupilRemoteSocket: ZMQ.Socket? = null
    private var backboneIpcSocket: ZMQ.Socket? = null
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val queued = ArrayDeque<ScheduledFuture<*>>()

    init {
        this.pollerThread.start()
    }

    fun connectPupilRemote(address: String, port: Int = 50020) {
        info("connectPupilRemote($address)")

        this.cancelAllQueued()
        this.disconnect()

        this.state = PUPIL_REMOTE_CONNECTING

        if (this.pupilRemoteSocket != null) {
            this.pupilRemoteSocket!!.close()
        }
        val socket = this.zContext.createSocket(SocketType.REQ)
        this.pupilRemoteSocket = socket
        socket.sendTimeOut = 100
        socket.receiveTimeOut = 100

        socket.connect("tcp://$address:$port")
        socket.send("SUB_PORT")

        this.schedule {
            val recvData: String? = socket.recvStr()
            if (recvData == null) {
                this.state = PUPIL_REMOTE_FAILED
                return@schedule
            }

            this.state = PUPIL_REMOTE_CONNECTED
            this.connectPupilIpc(address, recvData.toInt())
        }
    }

    fun disconnect() {
        info("disconnect()")
        state = DISCONNECTED

        this.pupilRemoteSocket?.close()
        this.pupilRemoteSocket = null
        this.backboneIpcSocket?.let {
            this.poller.unregister(it)
            it.close()
        }
        this.backboneIpcSocket = null
    }

    fun terminate() {
        this.disconnect()
        this.pollerThread.interrupt()
    }

    override fun events(socket: ZMQ.Socket, events: Int): Boolean {
        val data = socket.recv()
        info("events($socket): $events - ${data.size} bytes")
        return true
    }

    override fun events(channel: SelectableChannel, events: Int): Boolean {
        /* Ignored */
        return true
    }

    private fun connectPupilIpc(address: String, port: Int) {
        info("connectPupilRemote($address, $port)")
        this.state = PUPIL_IPC_CONNECTING

        this.backboneIpcSocket = this.zContext.createSocket(SocketType.SUB)
        this.backboneIpcSocket!!.connect("tcp://$address:$port")
        this.backboneIpcSocket!!.subscribe("gaze")
        this.poller.register(this.backboneIpcSocket, this)

        this.state = PUPIL_IPC_CONNECTED // unknown if this *actually* succeeded
    }

    private fun schedule(delay: Long = 0, unit: TimeUnit = TimeUnit.MILLISECONDS, block: () -> Unit) {
        this.queued.add(this.scheduler.schedule({
            try {
                block()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            this.clearCompleted()
        }, delay, unit))
    }

    /**
     * Cancel all scheduled tasks
     */
    private fun cancelAllQueued() {
        this.queued.iterator().also {
            while (it.hasNext()) {
                it.next().cancel(false)
                it.remove()
            }
        }
    }

    /**
     * Remove finished/cancelled tasks from the list
     */
    private fun clearCompleted() {
        this.queued.iterator().also {
            while (it.hasNext()) {
                if (it.next().let { it.isDone || it.isCancelled }) {
                    it.remove()
                }
            }
        }
    }

    enum class State {
        UNINITIALIZED,
        DISCONNECTED,
        PUPIL_REMOTE_CONNECTING,
        PUPIL_REMOTE_CONNECTED,
        PUPIL_REMOTE_FAILED,
        PUPIL_IPC_CONNECTING,
        PUPIL_IPC_CONNECTED,
        PUPIL_IPC_FAILED,
    }
}
