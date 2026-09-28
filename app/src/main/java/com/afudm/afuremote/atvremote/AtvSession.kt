package com.afudm.afuremote.atvremote

import com.afudm.afuremote.atvremote.proto.RemoteProto
import com.afudm.afuremote.atvremote.protocol.AtvWireImeCounters
import com.afudm.afuremote.atvremote.protocol.AtvWireProtocol
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket

/** One persistent v2 TLS connection. A daemon worker owns all socket reads and writes. */
class AtvSession(
    private val host: String,
    private val port: Int,
    private val tls: AtvTlsClientFactory
) : AutoCloseable {
    enum class State { STOPPED, CONNECTING, READY, RECONNECTING }
    @Volatile var state: State = State.STOPPED
        private set
    private val queue = LinkedBlockingQueue<(AtvWireImeCounters) -> ByteArray>()
    @Volatile private var closed = false
    private var worker: Thread? = null

    @Synchronized fun start() {
        if (worker?.isAlive == true || closed) return
        worker = Thread(::runLoop, "AfuRemote-ATV-${host.takeLast(12)}").apply { isDaemon = true; start() }
    }

    fun send(message: (AtvWireImeCounters) -> RemoteProto.RemoteMessage): Boolean {
        // Never report success while the handshake/reconnect loop has no ready socket.
        // Commands are not buffered across reconnects because that can replay stale input.
        if (closed || state != State.READY) return false
        return queue.offer { counters -> message(counters).toByteArray() }
    }

    private fun runLoop() {
        var retryMs = 500L
        var firstAttempt = true
        while (!closed) {
            var socket: SSLSocket? = null
            var connectedAtNanos: Long? = null
            try {
                state = if (firstAttempt) State.CONNECTING else State.RECONNECTING
                firstAttempt = false
                val connected = tls.connect(host, port, true)
                socket = connected.first
                socket.soTimeout = IO_POLL_MS
                val input = socket.inputStream
                val output = socket.outputStream
                var counters = handshake(input, output)
                state = State.READY
                connectedAtNanos = System.nanoTime()
                while (!closed && !socket.isClosed) {
                    val command = queue.poll(IO_POLL_MS.toLong(), TimeUnit.MILLISECONDS)
                    if (command != null) write(output, command(counters))
                    try {
                        val message = RemoteProto.RemoteMessage.parseFrom(readFrame(input))
                        if (message.hasRemotePingRequest()) write(output, RemoteProto.RemoteMessage.newBuilder().setRemotePingResponse(
                            RemoteProto.RemotePingResponse.newBuilder().setVal1(message.remotePingRequest.val1)).build().toByteArray())
                        if (message.hasRemoteImeBatchEdit()) counters = AtvWireImeCounters(message.remoteImeBatchEdit.imeCounter, message.remoteImeBatchEdit.fieldCounter)
                        if (message.hasRemoteError()) error("TV kumanda oturumunu kapattı")
                    } catch (_: SocketTimeoutException) { /* poll again, leaving the persistent socket open */ }
                }
            } catch (_: InterruptedException) {
                if (closed) break
            } catch (_: Exception) {
                if (closed) break
            } finally {
                state = if (closed) State.STOPPED else State.RECONNECTING
                runCatching { socket?.close() }
            }
            if (!closed) {
                if (connectedAtNanos != null && System.nanoTime() - connectedAtNanos >= STABLE_SESSION_NANOS) retryMs = 500L
                else retryMs = (retryMs * 2).coerceAtMost(MAX_RETRY_MS)
                try { Thread.sleep(retryMs) } catch (_: InterruptedException) { if (closed) break }
            }
        }
        state = State.STOPPED
    }

    private fun handshake(input: InputStream, output: OutputStream): AtvWireImeCounters {
        var counters = AtvWireImeCounters()
        var active = CAPABILITIES
        repeat(HANDSHAKE_FRAMES) {
            val message = RemoteProto.RemoteMessage.parseFrom(readFrame(input))
            when {
                message.hasRemotePingRequest() -> write(output, RemoteProto.RemoteMessage.newBuilder().setRemotePingResponse(
                    RemoteProto.RemotePingResponse.newBuilder().setVal1(message.remotePingRequest.val1)).build().toByteArray())
                message.hasRemoteConfigure() -> {
                    active = active and message.remoteConfigure.code1
                    write(output, RemoteProto.RemoteMessage.newBuilder().setRemoteConfigure(
                        RemoteProto.RemoteConfigure.newBuilder().setCode1(active).setDeviceInfo(RemoteProto.RemoteDeviceInfo.newBuilder()
                            .setModel("AfuRemote").setVendor("AfuRemote").setUnknown1(1).setUnknown2("1").setPackageName("atvremote").setAppVersion("1.0.0"))).build().toByteArray())
                }
                message.hasRemoteSetActive() -> write(output, RemoteProto.RemoteMessage.newBuilder().setRemoteSetActive(
                    RemoteProto.RemoteSetActive.newBuilder().setActive(active)).build().toByteArray())
                message.hasRemoteImeBatchEdit() -> counters = AtvWireImeCounters(message.remoteImeBatchEdit.imeCounter, message.remoteImeBatchEdit.fieldCounter)
                message.hasRemoteStart() && message.remoteStart.started -> return counters
                message.hasRemoteError() -> error("TV kumanda oturumunu başlatmadı")
            }
        }
        error("TV kumanda oturumu başlamadı")
    }

    private fun write(output: OutputStream, bytes: ByteArray) { output.write(AtvWireProtocol.encodeFrame(bytes)); output.flush() }

    private fun readFrame(input: InputStream): ByteArray {
        var length = 0
        var shift = 0
        var count = 0
        while (count < 5) {
            val value = input.read()
            check(value >= 0) { "TV bağlantısı kapandı" }
            if (shift == 28) require((value and 0xf0) == 0) { "TV geçersiz çerçeve uzunluğu gönderdi" }
            length = length or ((value and 0x7f) shl shift)
            count++
            if (value and 0x80 == 0) break
            shift += 7
        }
        require(count <= 5 && length in 1..AtvWireProtocol.MAX_FRAME_SIZE) { "TV geçersiz çerçeve uzunluğu gönderdi" }
        return ByteArray(length).also { bytes ->
            var offset = 0
            while (offset < length) {
                val n = input.read(bytes, offset, length - offset)
                check(n > 0) { "TV eksik veri gönderdi" }
                offset += n
            }
        }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        state = State.STOPPED
        worker?.interrupt()
        worker = null
        queue.clear()
    }

    private companion object {
        const val IO_POLL_MS = 500
        const val MAX_RETRY_MS = 30_000L
        const val STABLE_SESSION_NANOS = 10_000_000_000L
        const val HANDSHAKE_FRAMES = 16
        const val CAPABILITIES = (1 shl 0) or (1 shl 1) or (1 shl 2) or (1 shl 5) or (1 shl 6) or (1 shl 9)
    }
}
