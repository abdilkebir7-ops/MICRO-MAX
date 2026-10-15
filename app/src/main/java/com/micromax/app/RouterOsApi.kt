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
import java.security.MessageDigest
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/** Minimal RouterOS API client (plain TCP 8728) used from the phone while it is on the router's own Wi-Fi/LAN. */
class RouterOsApi(private val host: String, private val port: Int = 8728, private val timeoutMs: Int = 15000, private val tls: Boolean = false) : AutoCloseable {
    class Trap(message: String) : Exception(message)
    companion object {
        /** Set once from MainActivity so sockets can be bound to the Wi-Fi network. */
        @Volatile var appContext: Context? = null
        fun wifiNetwork(): android.net.Network? {
            val c = appContext ?: return null
            return try {
                val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
            } catch (_: Exception) { null }
        }
    }

    private var socket: Socket? = null
    private lateinit var input: InputStream
    private lateinit var output: OutputStream

    private fun openSocket(bindWifi: Boolean): Socket {
        val raw = Socket()
        try {
            if (bindWifi) wifiNetwork()?.bindSocket(raw)
            raw.connect(InetSocketAddress(host, port), timeoutMs)
            raw.soTimeout = timeoutMs
            if (!tls) return raw
            // MikroTik API-SSL commonly uses a self-signed router certificate (encrypted, not verified here).
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            })
            val context = SSLContext.getInstance("TLS")
            context.init(null, trustAll, SecureRandom())
            val ssl = context.socketFactory.createSocket(raw, host, port, true) as SSLSocket
            ssl.soTimeout = timeoutMs
            ssl.startHandshake()
            return ssl
        } catch (e: Exception) {
            try { raw.close() } catch (_: Exception) { }
            throw e
        }
    }

    suspend fun connect(user: String, password: String) = withContext(Dispatchers.IO) {
        // Android may route a Wi-Fi network without Internet over mobile data. Then 90.50.50.1 (a LAN
        // address that is also a public IP) reaches a stranger and gets "connection refused". WinBox-like
        // apps bind the socket to the Wi-Fi network; do the same, and fall back to the default route.
        val wifi = wifiNetwork()
        val s: Socket = if (wifi != null) {
            try { openSocket(true) } catch (first: Exception) { try { openSocket(false) } catch (_: Exception) { throw first } }
        } else openSocket(false)
        socket = s; input = s.getInputStream(); output = s.getOutputStream()
        // RouterOS 6.43+ commonly returns a challenge on the first /login.
        // WinBox/API clients answer it with MD5(0x00 + password + challenge).
        fun trapText(r: List<Map<String, String>>): String? = r.firstOrNull { it["!type"] == "!trap" }?.let { it["message"] ?: "trap" }
        val first = send("/login", emptyMap())
        trapText(first)?.let { throw Trap("AUTH_FAILED: first=$it") }
        val challenge = first.firstOrNull { !it["ret"].isNullOrBlank() }?.get("ret")
        var challengeError: String? = null
        var ok = false
        if (!challenge.isNullOrBlank()) {
            val challengeBytes = challenge.hexBytesOrNull()
            if (challengeBytes != null) {
                val digest = MessageDigest.getInstance("MD5").digest(byteArrayOf(0) + password.toByteArray(Charsets.UTF_8) + challengeBytes)
                val response = "00" + digest.joinToString("") { "%02x".format(it) }
                val r = send("/login", mapOf("name" to user, "response" to response))
                challengeError = trapText(r)
                ok = challengeError == null
            }
        }
        if (!ok) {
            // RouterOS 6.43+ plain login (also the fallback if the legacy challenge login was refused).
            val r = send("/login", mapOf("name" to user, "password" to password))
            val plainError = trapText(r)
            if (plainError != null) throw Trap("AUTH_FAILED: router=" + plainError + (if (challengeError != null) " | challenge=" + challengeError else "") + " | method=" + (if (challenge.isNullOrBlank()) "plain-only" else "challenge+plain") + " | userLen=" + user.length + " passLen=" + password.length)
        }
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

private fun String.hexBytesOrNull(): ByteArray? {
    if (length % 2 != 0 || !matches(Regex("[0-9a-fA-F]+"))) return null
    return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
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


/** Plain-text connection report (no secrets) shown after a failed attempt so the cause is visible. */
suspend fun routerNetworkReport(host: String, port: Int): String = withContext(Dispatchers.IO) {
    val sb = StringBuilder("— تقرير الاتصال —\n")
    val ctx = RouterOsApi.appContext
    var wifi: android.net.Network? = null
    try {
        wifi = RouterOsApi.wifiNetwork()
        if (ctx != null) {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val active = cm.activeNetwork
            val ac = cm.getNetworkCapabilities(active)
            val kind = when { ac == null -> "لا شبكة"; ac.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"; ac.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "بيانات الجوال"; ac.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"; else -> "أخرى" }
            sb.append("الشبكة الافتراضية للهاتف: ").append(kind).append('\n')
            val hasVpn = cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
            if (hasVpn) sb.append("⚠ يوجد VPN أو DNS خاص مفعّل؛ أوقفه وأعد المحاولة.\n")
        }
        if (wifi == null) sb.append("Wi-Fi: غير متصل بشبكة واي فاي!\n")
        else {
            val lp = (ctx!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).getLinkProperties(wifi)
            val ip = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
            val gw = lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
            sb.append("عنوان الهاتف على الواي فاي: ").append(ip ?: "؟").append("\nبوابة الواي فاي (عنوان الراوتر الحقيقي): ").append(gw ?: "؟").append('\n')
            if (gw != null && gw != host) sb.append("⚠ العنوان المكتوب ").append(host).append(" يختلف عن بوابة الواي فاي ").append(gw).append(" — جرّب ").append(gw).append(".\n")
        }
    } catch (e: Exception) { sb.append("تعذر فحص الشبكة: ").append(e.javaClass.simpleName).append('\n') }
    fun probe(bind: Boolean): String {
        val sk = Socket()
        return try {
            if (bind) (wifi ?: return "لا توجد شبكة Wi-Fi للربط").bindSocket(sk)
            sk.connect(InetSocketAddress(host, port), 6000); "نجح فتح المنفذ"
        } catch (e: Exception) { e.javaClass.simpleName + ": " + (e.message ?: "") } finally { try { sk.close() } catch (_: Exception) { } }
    }
    sb.append("المنفذ ").append(port).append(" عبر Wi-Fi مباشرة: ").append(probe(true)).append('\n')
    sb.append("المنفذ ").append(port).append(" عبر المسار الافتراضي: ").append(probe(false))
    sb.toString()
}
