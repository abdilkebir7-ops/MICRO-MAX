package com.micromax.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/** Minimal RouterOS API client (plain TCP 8728) used from the phone while it is on the router's own Wi-Fi/LAN. */
class RouterOsApi(private val host: String, private val port: Int = 8728, private val timeoutMs: Int = 15000) : AutoCloseable {
    class Trap(message: String) : Exception(message)

    private var socket: Socket? = null
    private lateinit var input: InputStream
    private lateinit var output: OutputStream

    suspend fun connect(user: String, password: String) = withContext(Dispatchers.IO) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), timeoutMs)
        s.soTimeout = timeoutMs
        socket = s; input = s.getInputStream(); output = s.getOutputStream()
        val r = send("/login", mapOf("name" to user, "password" to password))
        if (r.any { it["!type"] == "!trap" }) throw Trap("AUTH_FAILED")
    }

    /** Sends one command and returns every reply sentence as attribute maps ("!type" holds !re/!done/!trap). */
    suspend fun command(path: String, attrs: Map<String, String> = emptyMap(), queries: List<String> = emptyList(), longWaitMs: Int? = null): List<Map<String, String>> = withContext(Dispatchers.IO) {
        if (longWaitMs != null) socket?.soTimeout = longWaitMs
        try {
            val r = send(path, attrs, queries)
            r.firstOrNull { it["!type"] == "!trap" }?.let { throw Trap(it["message"] ?: "TRAP") }
            r
        } finally { socket?.soTimeout = timeoutMs }
    }

    private fun send(path: String, attrs: Map<String, String>, queries: List<String> = emptyList()): List<Map<String, String>> {
        val out = ByteArrayOutputStream()
        writeWord(out, path)
        attrs.forEach { (k, v) -> writeWord(out, "=$k=$v") }
        queries.forEach { writeWord(out, it) }
        writeWord(out, "")
        output.write(out.toByteArray()); output.flush()
        val replies = mutableListOf<Map<String, String>>()
        while (true) {
            val sentence = readSentence()
            if (sentence.isEmpty()) continue
            val m = LinkedHashMap<String, String>()
            m["!type"] = sentence[0]
            for (w in sentence.drop(1)) if (w.startsWith("=")) { val i = w.indexOf('=', 1); if (i > 0) m[w.substring(1, i)] = w.substring(i + 1) }
            replies.add(m)
            if (sentence[0] == "!done" || sentence[0] == "!fatal") break
        }
        return replies
    }

    private fun writeWord(o: ByteArrayOutputStream, w: String) {
        val b = w.toByteArray(Charsets.UTF_8); val n = b.size
        when {
            n < 0x80 -> o.write(n)
            n < 0x4000 -> { o.write((n shr 8) or 0x80); o.write(n and 0xFF) }
            n < 0x200000 -> { o.write((n shr 16) or 0xC0); o.write((n shr 8) and 0xFF); o.write(n and 0xFF) }
            else -> throw IllegalArgumentException("WORD_TOO_LONG")
        }
        o.write(b)
    }

    private fun readByte(): Int { val b = input.read(); if (b < 0) throw java.io.IOException("CONNECTION_CLOSED"); return b }
    private fun readLen(): Int {
        val b = readByte()
        return when {
            b < 0x80 -> b
            b < 0xC0 -> ((b and 0x3F) shl 8) or readByte()
            b < 0xE0 -> ((b and 0x1F) shl 16) or (readByte() shl 8) or readByte()
            b < 0xF0 -> ((b and 0x0F) shl 24) or (readByte() shl 16) or (readByte() shl 8) or readByte()
            else -> (readByte() shl 24) or (readByte() shl 16) or (readByte() shl 8) or readByte()
        }
    }
    private fun readSentence(): List<String> {
        val words = mutableListOf<String>()
        while (true) {
            val n = readLen()
            if (n == 0) return words
            val buf = ByteArray(n); var off = 0
            while (off < n) { val r = input.read(buf, off, n - off); if (r < 0) throw java.io.IOException("CONNECTION_CLOSED"); off += r }
            words.add(String(buf, Charsets.UTF_8))
        }
    }

    override fun close() { try { socket?.close() } catch (_: Exception) { } }
}

/** The default gateway of the Wi-Fi network the phone is on (the router's LAN address), or null. */
fun detectWifiGateway(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    for (n in cm.allNetworks) {
        val caps = cm.getNetworkCapabilities(n) ?: continue
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
        val lp = cm.getLinkProperties(n) ?: continue
        lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress?.let { return it }
    }
    return null
}
