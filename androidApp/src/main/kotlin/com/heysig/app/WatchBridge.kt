package com.heysig.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Development bootstrap accepts only shell/system callers holding Android's DUMP permission. */
class WatchBridgeSetup : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra("token") ?: return
        if(!token.matches(Regex("[0-9a-f]{64}"))) return
        val saved = context.getSharedPreferences("heysig-watch-dev",Context.MODE_PRIVATE).edit().putString("token",token).commit()
        resultCode = if(saved) 1 else 0
    }
}

/** Loopback only. An authenticated TLS relay reaches this through explicit ADB forwarding. */
class WatchBridge(
    private val context: Context,
    private val dispatch: (JSONObject, (JSONObject) -> Unit) -> Unit
) : AutoCloseable {
    private val active = AtomicBoolean(true)
    private val server = ServerSocket(18765,4,InetAddress.getByName("127.0.0.1"))
    private val slots = Semaphore(4)
    private val workers = Executors.newFixedThreadPool(4)
    init {
        Thread({
            while(active.get()) {
                val client = try { server.accept() } catch(e:Exception) { break }
                if(!slots.tryAcquire()) { client.close(); continue }
                workers.execute { try { serve(client) } finally { slots.release() } }
            }
        },"heysig-watch-loopback").start()
    }
    private fun serve(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 30000
            val input = socket.getInputStream()
            fun line(): String {
                val bytes = java.io.ByteArrayOutputStream()
                while(true) {
                    val value = input.read()
                    check(value != -1) { "Incomplete request" }
                    if(value == 10) break
                    check(bytes.size() < 2048) { "Header too large" }
                    if(value != 13) bytes.write(value)
                }
                return bytes.toString("US-ASCII")
            }
            var code = 400
            var reply = JSONObject().put("phase","FINAL").put("text","Invalid watch request")
            try {
                check(line() == "POST /command HTTP/1.1")
                val headers = mutableMapOf<String,String>()
                var headerCount = 0
                while(true) {
                    val header = line()
                    if(header.isEmpty()) break
                    check(++headerCount <= 16) { "Too many headers" }
                    val colon = header.indexOf(':'); check(colon > 0)
                    check(headers.put(header.substring(0,colon).lowercase(),header.substring(colon+1).trim()) == null)
                }
                val token = context.getSharedPreferences("heysig-watch-dev",Context.MODE_PRIVATE).getString("token",null)
                val supplied = headers["authorization"]?.removePrefix("Bearer ")
                if(token == null || supplied == null || !MessageDigest.isEqual(token.toByteArray(),supplied.toByteArray())) {
                    code = 401; reply.put("text","Watch bridge authentication failed")
                } else {
                    check(!headers.containsKey("transfer-encoding"))
                    val size = headers["content-length"]?.toIntOrNull() ?: error("Missing size")
                    check(size in 1..4096)
                    val body = ByteArray(size)
                    var offset = 0
                    while(offset < size) { val count = input.read(body,offset,size-offset); check(count > 0); offset += count }
                    val request = JSONObject(String(body,Charsets.UTF_8))
                    check(request.getString("id").matches(Regex("[0-9a-f-]{36}")))
                    check(request.getString("conversation").matches(Regex("[0-9a-f-]{36}")))
                    check(request.getString("text").length in 1..512)
                    val latch = CountDownLatch(1)
                    var response: JSONObject? = null
                    dispatch(request) { response = it; latch.countDown() }
                    reply = if(latch.await(25,TimeUnit.SECONDS)) response!! else JSONObject().put("phase","UNKNOWN").put("text","No final app result. Do not repeat the command automatically.")
                    code = 200
                }
            } catch(e:Exception) { reply.put("text","Watch bridge request failed") }
            val bytes = reply.toString().toByteArray(Charsets.UTF_8)
            runCatching {
                socket.getOutputStream().write(("HTTP/1.1 $code Response\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                socket.getOutputStream().write(bytes)
            }
        }
    }
    override fun close() { active.set(false); runCatching { server.close() }; workers.shutdownNow() }
}
