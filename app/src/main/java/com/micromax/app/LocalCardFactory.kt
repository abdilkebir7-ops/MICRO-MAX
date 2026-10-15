package com.micromax.app

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

/** Pure local card-generation logic shared by simulator and real-router workflows. */
data class LocalCard(val username: String, val password: String, val profile: String, val qr: String)

object LocalCardFactory {
    private const val USER_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

    fun createBatch(
        host: String,
        prefix: String,
        usernameDigits: Int,
        passwordLength: Int,
        profile: String,
        count: Int,
        existingNames: Set<String> = emptySet(),
        random: SecureRandom = SecureRandom()
    ): List<LocalCard> {
        require(count in 1..200) { "عدد الكروت يجب أن يكون بين 1 و200" }
        require(usernameDigits in 4..10) { "عدد أرقام اسم المستخدم يجب أن يكون بين 4 و10" }
        require(passwordLength in 6..16) { "طول كلمة المرور يجب أن يكون بين 6 و16" }
        require(profile.isNotBlank()) { "اسم البروفايل مطلوب" }
        val safePrefix = prefix.filter(Char::isLetterOrDigit).take(8).ifBlank { "MM" }
        val used = existingNames.toMutableSet()
        val portalHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/').ifBlank { "192.168.88.1" }
        val result = ArrayList<LocalCard>(count)
        repeat(count) {
            var username = ""
            var attempts = 0
            do {
                username = safePrefix + buildString { repeat(usernameDigits) { append(random.nextInt(10)) } }
                attempts++
                require(attempts < 10000) { "تعذر توليد أسماء فريدة؛ زِد عدد الأرقام أو غيّر البادئة." }
            } while (!used.add(username))
            val password = buildString { repeat(passwordLength) { append(USER_CHARS[random.nextInt(USER_CHARS.length)]) } }
            val qr = "http://$portalHost/login?username=${URLEncoder.encode(username, StandardCharsets.UTF_8.name())}&password=${URLEncoder.encode(password, StandardCharsets.UTF_8.name())}"
            result.add(LocalCard(username, password, profile, qr))
        }
        return result
    }
}
