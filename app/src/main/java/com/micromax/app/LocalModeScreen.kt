package com.micromax.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class LocalProfile(val name: String, val rate: String, val shared: String, val session: String)

/** Direct, phone-to-router management. No account, API server, or Render request is used on this screen. */
@Composable
fun LocalModeScreen(activity: MainActivity, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("micromax_local_router", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(prefs.getString("host", "192.168.88.1") ?: "192.168.88.1") }
    var port by remember { mutableStateOf(prefs.getString("port", "8728") ?: "8728") }
    var user by remember { mutableStateOf(prefs.getString("user", "admin") ?: "admin") }
    var password by remember { mutableStateOf("") }
    var tls by remember { mutableStateOf(port == "8729") }
    var simulation by remember { mutableStateOf(true) }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var profiles by remember { mutableStateOf(listOf(LocalProfile("default", "unlimited", "1", "none"), LocalProfile("3h2d", "2M/1M", "1", "3h"), LocalProfile("24h", "5M/2M", "1", "1d"), LocalProfile("7d", "5M/2M", "1", "7d"), LocalProfile("30d", "10M/5M", "1", "30d"))) }
    var users by remember { mutableStateOf(listOf<Map<String, String>>()) }
    var active by remember { mutableStateOf(listOf<Map<String, String>>()) }
    var tab by remember { mutableIntStateOf(0) }
    var profileName by remember { mutableStateOf("3h2d") }
    var rate by remember { mutableStateOf("2M/1M") }
    var shared by remember { mutableStateOf("1") }
    var session by remember { mutableStateOf("3h") }
    var count by remember { mutableStateOf("10") }
    var prefix by remember { mutableStateOf("MM") }
    var userDigits by remember { mutableStateOf("6") }
    var passLength by remember { mutableStateOf("8") }
    var cards by remember { mutableStateOf(listOf<LocalCard>()) }

    suspend fun withRouter(block: suspend (RouterOsApi) -> Unit) {
        val api = RouterOsApi(host.trim(), port.toIntOrNull() ?: if (tls) 8729 else 8728, tls = tls)
        try { api.connect(user.trim(), password); block(api) } finally { api.close() }
    }
    suspend fun refreshLive() {
        if (simulation) {
            info = mapOf("version" to "7.16.2 (SIMULATED)", "board-name" to "RB750Gr3 (simulator)", "architecture-name" to "arm", "uptime" to "2d 04:31:12", "cpu-load" to "12", "free-memory" to "128000000", "total-memory" to "256000000")
            users = users.ifEmpty { listOf(mapOf("name" to "MM100001", "profile" to "3h2d", "uptime" to "00:18:32", "comment" to "SIMULATED"), mapOf("name" to "MM100002", "profile" to "24h", "uptime" to "00:02:11", "comment" to "SIMULATED")) }
            active = users.take(1).map { mapOf("user" to it["name"].orEmpty(), "address" to "192.168.88.101", "uptime" to "00:18:32", "mac-address" to "02:00:00:00:00:01") }
            return
        }
        withRouter { r ->
            info = r.command("/system/resource/print").firstOrNull { it["!type"] == "!re" } ?: emptyMap()
            profiles = r.command("/ip/hotspot/user/profile/print").filter { it["!type"] == "!re" }.map { LocalProfile(it["name"].orEmpty(), it["rate-limit"].orEmpty().ifBlank { "غير محدد" }, it["shared-users"].orEmpty().ifBlank { "1" }, it["session-timeout"].orEmpty().ifBlank { "بدون" }) }
            if (profiles.none { it.name == profileName }) profileName = profiles.firstOrNull()?.name ?: "default"
            users = r.command("/ip/hotspot/user/print").filter { it["!type"] == "!re" }.map { it.filterKeys { k -> !k.startsWith("!") } }
            active = r.command("/ip/hotspot/active/print").filter { it["!type"] == "!re" }.map { it.filterKeys { k -> !k.startsWith("!") } }
        }
    }
    fun doAction(action: suspend () -> Unit) {
        scope.launch { busy = true; error = null; try { action() } catch (e: Exception) { error = localError(e) } finally { busy = false } }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.Outlined.ArrowBack, "رجوع") }
                Column(Modifier.weight(1f)) { Text("MICRO-MAX · الوضع المحلي", fontWeight = FontWeight.Black, fontSize = 19.sp); Text("اتصال مباشر من الهاتف إلى MikroTik", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                Surface(color = if (connected) Color(0xFF147D58) else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) { Text(if (connected) "متصل" else "غير متصل", modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), fontSize = 11.sp, color = if (connected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (!connected) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("لا يحتاج Render أو حسابًا", fontWeight = FontWeight.Bold); Text("الهاتف يتصل مباشرة بمنفذ RouterOS API. فعّل API على الراوتر واتصل بشبكة Wi-Fi نفسها. المحاكاة لا تغيّر الراوتر الحقيقي.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("وضع الاختبار المحاكى", Modifier.weight(1f)); Switch(simulation, { simulation = it }) }
                    if (!simulation) {
                        OutlinedTextField(host, { host = it }, label = { Text("IP الراوتر") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(port, { port = it.filter(Char::isDigit); tls = it == "8729" }, label = { Text("المنفذ (8728 أو 8729)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(user, { user = it }, label = { Text("اسم مستخدم RouterOS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(tls, { tls = it; port = if (it) "8729" else "8728" }); Text("API-SSL (8729)") }
                    } else Text("سيظهر راوتر افتراضي وبروفايلات وجلسات وهمية. لا يتم إرسال أي أمر إلى جهاز حقيقي.", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    Button(onClick = { doAction { refreshLive(); connected = true; prefs.edit().putString("host", host).putString("port", port).putString("user", user).apply() } }, enabled = !busy && (simulation || host.isNotBlank() && user.isNotBlank() && password.isNotEmpty()), modifier = Modifier.fillMaxWidth().height(52.dp)) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Router, null); Spacer(Modifier.width(8.dp)); Text(if (simulation) "فتح المحاكي والاختبار" else "اتصال مباشر بالراوتر") }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("الراوتر", "Profiles", "المستخدمون", "الجلسات", "إنشاء الكروت").forEachIndexed { i, label -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label, fontSize = 10.sp) }) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                when (tab) {
                    0 -> LazyColumn(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { LocalMetric("اللوحة", info["board-name"] ?: "—", "RouterOS ${info["version"] ?: "—"}") }
                        item { LocalMetric("مدة التشغيل", info["uptime"] ?: "—", "CPU ${info["cpu-load"] ?: "—"}%") }
                        item { LocalMetric("المستخدمون", users.size.toString(), "الجلسات النشطة ${active.size}") }
                        item { LocalMetric("طريقة الاتصال", if (simulation) "محاكاة" else "RouterOS API مباشر", if (simulation) "بيانات اختبار فقط" else "$host:$port") }
                        item { Button(onClick = { doAction { refreshLive() } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text("تحديث بيانات الراوتر") } }
                        item { OutlinedButton(onClick = { connected = false; password = "" }, modifier = Modifier.fillMaxWidth()) { Text("قطع الاتصال / تبديل الراوتر") } }
                    }
                    1 -> LazyColumn(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Text("بروفايلات HotSpot", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("إنشاء بروفايل", fontWeight = FontWeight.Bold)
                                    OutlinedTextField(profileName, { profileName = it }, label = { Text("اسم البروفايل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(rate, { rate = it }, label = { Text("Rate limit مثال 2M/1M") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(shared, { shared = it.filter(Char::isDigit) }, label = { Text("عدد الأجهزة المسموحة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(session, { session = it }, label = { Text("Session timeout مثال 3h أو 1d") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    Button(onClick = { doAction {
                                        val timeout = session.trim().takeIf { it.isNotBlank() && it != "none" } ?: "0s"
                                        val p = LocalProfile(profileName.trim(), rate.trim(), shared.ifBlank { "1" }, timeout)
                                        if (simulation) profiles = profiles.filterNot { it.name == p.name } + p
                                        else withRouter { r -> r.command("/ip/hotspot/user/profile/add", mapOf("name" to p.name, "rate-limit" to p.rate, "shared-users" to p.shared, "session-timeout" to p.session)) }
                                        if (!simulation) refreshLive()
                                    } }, enabled = !busy && profileName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("حفظ البروفايل ${if (simulation) "في المحاكاة" else "على الراوتر"}") }
                                }
                            }
                        }
                        items(profiles) { p -> Card { Column(Modifier.fillMaxWidth().clickable { profileName = p.name; rate = p.rate; shared = p.shared; session = p.session }.padding(14.dp)) { Text(p.name, fontWeight = FontWeight.Bold); Text("Rate: ${p.rate} · Shared: ${p.shared} · Timeout: ${p.session}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                        item { Text("اضغط على أي بروفايل لتحديده في نموذج التوليد أو تعديله في الأعلى.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    2 -> LazyColumn(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Text("مستخدمو HotSpot (${users.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                        items(users) { u -> Card { Column(Modifier.padding(13.dp)) { Text(u["name"] ?: "بدون اسم", fontWeight = FontWeight.Bold); Text("Profile: ${u["profile"] ?: "default"} · Uptime: ${u["uptime"] ?: "—"}", fontSize = 12.sp); if (!u["comment"].isNullOrBlank()) Text(u["comment"].orEmpty(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                    }
                    3 -> LazyColumn(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Text("الجلسات النشطة (${active.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                        items(active) { a -> Card { Column(Modifier.padding(13.dp)) { Text(a["user"] ?: "—", fontWeight = FontWeight.Bold); Text("IP: ${a["address"] ?: "—"} · المدة: ${a["uptime"] ?: "—"}", fontSize = 12.sp); Text("MAC: ${a["mac-address"] ?: "—"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                    }
                    else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("توليد الكروت على ${if (simulation) "الراوتر المحاكى" else "الراوتر الحقيقي"}", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        OutlinedTextField(profileName, { profileName = it }, label = { Text("اسم البروفايل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(count, { count = it.filter(Char::isDigit).take(3) }, label = { Text("عدد الكروت (1–200)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(prefix, { prefix = it.filter { c -> c.isLetterOrDigit() }.take(8) }, label = { Text("بادئة اسم المستخدم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(userDigits, { userDigits = it.filter(Char::isDigit).take(8) }, label = { Text("عدد أرقام المستخدم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(passLength, { passLength = it.filter(Char::isDigit).take(2) }, label = { Text("طول كلمة المرور") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { doAction {
                            val n = (count.toIntOrNull() ?: 0).coerceIn(1, 200)
                            val digits = (userDigits.toIntOrNull() ?: 6).coerceIn(4, 10)
                            val pwLen = (passLength.toIntOrNull() ?: 8).coerceIn(6, 16)
                            var made = emptyList<LocalCard>()
                            if (simulation) {
                                made = LocalCardFactory.createBatch(
                                    host = host, prefix = prefix, usernameDigits = digits, passwordLength = pwLen,
                                    profile = profileName, count = n, existingNames = users.mapNotNull { it["name"] }.toSet()
                                )
                                users = users + made.map { mapOf("name" to it.username, "profile" to it.profile, "comment" to "SIMULATED · جديد") }
                            } else {
                                withRouter { r ->
                                    val existing = r.command("/ip/hotspot/user/print").filter { it["!type"] == "!re" }.mapNotNull { it["name"] }.toSet()
                                    val generated = LocalCardFactory.createBatch(host, prefix, digits, pwLen, profileName, n, existing)
                                    val added = mutableListOf<LocalCard>()
                                    try {
                                        generated.forEach { card ->
                                            r.command("/ip/hotspot/user/add", mapOf("name" to card.username, "password" to card.password, "profile" to card.profile, "comment" to "MICRO-MAX local card"))
                                            added.add(card)
                                        }
                                    } catch (e: Exception) {
                                        made = added.toList()
                                        cards = made
                                        throw e
                                    }
                                    made = added.toList()
                                }
                                refreshLive()
                            }
                            cards = made
                        } }, enabled = !busy && profileName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.ConfirmationNumber, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "جارٍ الإنشاء…" else "توليد ${count.toIntOrNull()?.coerceIn(1, 200) ?: 10} كرت") }
                        if (simulation) Text("المحاكاة فقط: لن يتم إنشاء أي مستخدم على راوتر حقيقي.", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp)
                        if (cards.isNotEmpty()) Text("تم تجهيز ${cards.size} كرت — اضغط على الكرت لفتح الطباعة مع QR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        cards.forEach { c -> Card(Modifier.fillMaxWidth().clickable { activity.printBitmap(renderLocalCard(c)) }) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { LocalQr(c.qr, Modifier.size(62.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(c.username, fontWeight = FontWeight.Black); Text("كلمة المرور: ${c.password}", fontSize = 12.sp); Text("Profile: ${c.profile}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = { activity.printBitmap(renderLocalCard(c)) }) { Icon(Icons.Outlined.Print, "طباعة") } } } }
                    }
                }
            }
        }
    }
}

@Composable private fun LocalMetric(title: String, value: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Black, fontSize = 21.sp); Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) } }
}

@Composable private fun LocalQr(value: String, modifier: Modifier = Modifier) {
    val bitmap = remember(value) { try { val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 240, 240, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)); Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888).also { b -> for (x in 0 until 240) for (y in 0 until 240) b.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } } catch (_: Exception) { null } }
    if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), contentDescription = "QR للكرت", modifier = modifier)
}

private fun renderLocalCard(card: LocalCard): Bitmap {
    val b = Bitmap.createBitmap(1000, 560, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.rgb(12, 20, 34); c.drawRoundRect(RectF(0f, 0f, 1000f, 560f), 38f, 38f, p)
    p.color = android.graphics.Color.rgb(33, 150, 243); c.drawRoundRect(RectF(0f, 0f, 1000f, 18f), 8f, 8f, p)
    p.color = android.graphics.Color.WHITE; p.textSize = 48f; p.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD); c.drawText("MICRO-MAX HOTSPOT", 48f, 100f, p)
    p.textSize = 28f; p.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL); c.drawText("INTERNET ACCESS CARD", 50f, 145f, p)
    p.textSize = 25f; c.drawText("USERNAME", 55f, 230f, p); c.drawText("PASSWORD", 55f, 345f, p)
    p.textSize = 43f; p.typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD); c.drawText(card.username, 55f, 285f, p); c.drawText(card.password, 55f, 400f, p)
    p.textSize = 23f; p.typeface = android.graphics.Typeface.DEFAULT; c.drawText("PROFILE: ${card.profile}", 55f, 475f, p)
    val matrix = MultiFormatWriter().encode(card.qr, BarcodeFormat.QR_CODE, 300, 300, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    p.color = android.graphics.Color.WHITE; c.drawRoundRect(RectF(650f, 170f, 970f, 490f), 22f, 22f, p)
    for (x in 0 until 300) for (y in 0 until 300) if (matrix[x, y]) { p.color = android.graphics.Color.BLACK; c.drawRect(660f + x, 180f + y, 661f + x, 181f + y, p) }
    p.color = android.graphics.Color.LTGRAY; p.textSize = 18f; c.drawText("SCAN TO LOGIN", 700f, 520f, p)
    return b
}

private fun localError(e: Exception): String {
    val raw = e.message.orEmpty()
    return when {
        raw.contains("AUTH_FAILED", true) -> "فشل الدخول إلى الراوتر: تحقق من اسم المستخدم وكلمة المرور وصلاحية API."
        raw.contains("timeout", true) || raw.contains("timed out", true) -> "انتهت مهلة الاتصال. تأكد من Wi‑Fi وعنوان الراوتر وتفعيل API."
        raw.contains("refused", true) -> "الراوتر رفض الاتصال. فعّل /ip service api أو api-ssl وتحقق من المنفذ."
        else -> raw.ifBlank { "تعذر تنفيذ العملية على الراوتر." }
    }
}
