package com.micromax.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
private fun RouterChoice(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, detail: String, badge: String? = null, onClick: () -> Unit) {
    ItemSurface(onClick = onClick) {
        SymbolTile(icon, tint, size = 48.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, false))
                if (badge != null) { Spacer(Modifier.width(8.dp)); StatusPill(badge) }
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(MmIcons.ChevronL, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** First screen of "add router": three clear ways, each in one line of plain language. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRouterChooser(api: Api, onSaved: () -> Unit, onCancel: () -> Unit) {
    var choice by remember { mutableStateOf<String?>(null) }
    when (choice) {
        "auto" -> AutoLinkRouterDialog(api, onSaved, onCancel)
        "agent" -> AgentRouterDialog(api, onSaved, onCancel)
        "direct" -> AddRouterDialog(api, onSaved, onCancel, initialMode = "public")
        else -> ModalBottomSheet(onDismissRequest = onCancel, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PageHeading("راوتر جديد", "كيف تريد الربط؟", "اختر ما يناسب مكانك الآن. يمكنك إضافة راوترات أخرى بطرق مختلفة لاحقًا.")
                Spacer(Modifier.height(4.dp))
                RouterChoice(MmIcons.Sparkle, Signal, "ربط تلقائي", "أنت متصل بواي فاي الراوتر الآن؟ اضغط ربط وسنجهّز كل شيء بدون نسخ أي شيء.", "الأسهل") { choice = "auto" }
                RouterChoice(MmIcons.Cloud, Violet, "راوتر بعيد", "الراوتر في مكان آخر أو بدون IP ثابت. تلصق سكربتًا واحدًا في Terminal.") { choice = "agent" }
                RouterChoice(MmIcons.Network, DataBlue, "اتصال مباشر (IP عام)", "للراوتر الذي له عنوان عام ومنفذ API-SSL (8729) مفتوح.") { choice = "direct" }
                Text("الخادم على الإنترنت ولا يصل إلى عناوين مثل 192.168.88.1، لذلك الراوتر داخل شبكتك يُربط بالطريقة الأولى أو الثانية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AgentRouterDialog(api: Api, onSaved: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var routerId by remember { mutableStateOf<String?>(null) }
    var script by remember { mutableStateOf("") }
    var online by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var waitedSec by remember { mutableIntStateOf(0) }

    // Poll until the router phones home.
    LaunchedEffect(routerId) {
        val id = routerId ?: return@LaunchedEffect
        while (!online) {
            try {
                val o = JSONObject(api.get("/api/routers/$id/agent/status"))
                online = o.optBoolean("online"); info = o.optJSONObject("info")
            } catch (_: Exception) { }
            if (online) break
            delay(5000); waitedSec += 5
        }
    }

    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text(if (routerId == null) "راوتر عن بعد (Agent)" else "ألصق السكربت في الراوتر") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (routerId == null) {
                Text("سنُنشئ سكربتاً خاصاً بهذا الراوتر. بعد لصقه يتصل الراوتر بالخادم تلقائياً كل 30 ثانية.", fontSize = 12.sp, color = TextMuted)
                AppField(name, { name = it }, "اسم الراوتر، مثل الفرع الرئيسي", MmIcons.Tag)
                Text("شروط الراوتر: RouterOS 7.x (يفضّل)، إنترنت يعمل، DNS يعمل، والتاريخ والوقت صحيحان (NTP).", fontSize = 11.sp, color = TextMuted)
            } else {
                Text("1) انسخ السكربت   2) افتح WinBox ← New Terminal   3) الصق ثم Enter", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Button({
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("MICRO-MAX Agent", script))
                    Toast.makeText(ctx, "تم نسخ السكربت", Toast.LENGTH_SHORT).show()
                }, Modifier.fillMaxWidth()) { Icon(MmIcons.Copy, null); Spacer(Modifier.width(8.dp)); Text("نسخ السكربت") }
                SelectionContainer { Text(script.take(900) + if (script.length > 900) "\n…" else "", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextMuted) }
                HorizontalDivider()
                if (online) {
                    Text("✓ الراوتر متصل", color = Accent, fontWeight = FontWeight.Bold)
                    info?.let { Text("${it.optString("board")} • RouterOS ${it.optString("v")} • مستخدمون نشطون: ${it.optInt("active")}", fontSize = 12.sp, color = TextMuted) }
                    Text("الخطوة التالية: Profiles & Plans ← جلب بروفايلات الراوتر.", fontSize = 12.sp)
                } else {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("في انتظار اتصال الراوتر…", fontSize = 12.sp)
                    }
                    if (waitedSec >= 120) {
                        Text("تأخر الاتصال. افحص بالترتيب:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("• هل ظهر خطأ عند لصق السكربت؟ (أرسله لي)\n• اختبر على الراوتر: /ping google.com  و  /ping 8.8.8.8\n• إن نجح الثاني وفشل الأول فاضبط DNS: /ip dns set servers=8.8.8.8\n• اضبط الوقت: /system clock print  ثم  /system ntp client set enabled=yes\n• للإصدارات 6.x جرّب تحديث RouterOS إلى 7.x", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
            err?.let { ErrorCard(routerConnectionMessage(it)) }
        }
    }, confirmButton = {
        if (routerId == null) Button({
            scope.launch {
                busy = true; err = null
                try {
                    val r = JSONObject(api.post("/api/routers/agent/enroll", JSONObject().put("name", name.trim()).toString()))
                    routerId = r.optString("id"); script = r.optString("script")
                } catch (e: Exception) { err = e.message ?: "تعذر إنشاء السكربت" }
                busy = false
            }
        }, enabled = name.isNotBlank() && !busy) { Text(if (busy) "جاري الإنشاء…" else "إنشاء السكربت") }
        else Button({ onSaved() }, enabled = online) { Text("تم") }
    }, dismissButton = { TextButton({ if (routerId != null) onSaved() else onCancel() }) { Text(if (routerId != null) "إغلاق" else "إلغاء") } })
}


/** Zero-copy onboarding: the phone (on the router's LAN) installs the cloud Agent through the RouterOS API. */
@Composable
fun AutoLinkRouterDialog(api: Api, onSaved: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(detectWifiGateway(ctx) ?: "192.168.88.1") }
    var user by remember { mutableStateOf("admin") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var routerId by remember { mutableStateOf<String?>(null) }
    var online by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var imported by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(routerId) {
        val id = routerId ?: return@LaunchedEffect
        var tries = 0
        while (!online && tries < 60) {
            try { val o = JSONObject(api.get("/api/routers/$id/agent/status")); online = o.optBoolean("online"); info = o.optJSONObject("info") } catch (_: Exception) { }
            if (!online) { delay(3000); tries++ }
        }
        if (online) try { imported = JSONObject(api.post("/api/routers/$id/hotspot-profiles/import")).optJSONArray("profiles")?.length() } catch (_: Exception) { }
    }

    fun friendly(e: Exception): String = when {
        e is RouterOsApi.Trap && e.message == "AUTH_FAILED" -> "اسم المستخدم أو كلمة المرور غير صحيحة."
        e is java.net.SocketTimeoutException || e is java.net.ConnectException || e is java.net.NoRouteToHostException ->
            "تعذر الاتصال بالراوتر على $host:8728. تأكد أن الهاتف على واي فاي الراوتر، وأن خدمة API مفعلة (IP ← Services)."
        else -> e.message ?: "فشل الربط"
    }

    fun link() = scope.launch {
        busy = true; err = null
        try {
            step = "1/4 تجهيز الربط على الخادم…"
            val enroll = JSONObject(api.post("/api/routers/agent/enroll", JSONObject().put("name", name.trim()).toString()))
            val agent = enroll.getJSONObject("agent")
            val newId = enroll.optString("id")
            RouterOsApi(host.trim()).use { r ->
                step = "2/4 الاتصال بالراوتر…"
                r.connect(user.trim(), pass)
                step = "3/4 تثبيت الشهادات…"
                try { r.command("/tool/fetch", mapOf("url" to agent.optString("caUrl"), "mode" to "https", "check-certificate" to "no", "dst-path" to "mm-ca.pem"), longWaitMs = 90000) } catch (_: Exception) { }
                try { r.command("/certificate/import", mapOf("file-name" to "mm-ca.pem", "passphrase" to ""), longWaitMs = 120000) } catch (_: Exception) { }
                step = "4/4 تثبيت وكيل MICRO-MAX…"
                for (path in listOf("/system/scheduler", "/system/script")) {
                    r.command("$path/print", queries = listOf("?name=micromax-agent")).filter { it["!type"] == "!re" }.forEach { x ->
                        x[".id"]?.let { id -> try { r.command("$path/remove", mapOf(".id" to id)) } catch (_: Exception) { } }
                    }
                }
                val policy = agent.optString("policy")
                r.command("/system/script/add", mapOf("name" to "micromax-agent", "policy" to policy, "source" to agent.optString("source")))
                r.command("/system/scheduler/add", mapOf("name" to "micromax-agent", "interval" to agent.optString("interval", "30s"), "start-time" to "startup", "policy" to policy, "on-event" to "/system script run micromax-agent"))
                try { r.command("/system/script/run", mapOf("number" to "micromax-agent"), longWaitMs = 60000) } catch (_: Exception) { }
            }
            step = "في انتظار أول اتصال من الراوتر…"
            routerId = newId
        } catch (e: Exception) { err = friendly(e); step = "" }
        busy = false
    }

    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text("ربط تلقائي بدون نسخ") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (routerId == null) {
                Text("1) اتصل بواي فاي الراوتر   2) أدخل بيانات الدخول   3) اضغط ربط", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                AppField(name, { name = it }, "اسم الراوتر", MmIcons.Tag)
                OutlinedTextField(host, { host = it }, label = { Text("عنوان الراوتر (اكتُشف تلقائيًا)") }, leadingIcon = { Icon(MmIcons.Router, null) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors())
                OutlinedTextField(user, { user = it }, label = { Text("اسم المستخدم") }, leadingIcon = { Icon(MmIcons.Person, null) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors())
                OutlinedTextField(pass, { pass = it }, label = { Text("كلمة المرور (فارغة إن لم تُضبط)") }, leadingIcon = { Icon(MmIcons.Lock, null) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors())
                Text("بيانات الدخول تُستخدم من هاتفك إلى الراوتر مباشرة ولا تُرسل إلى الخادم. يلزم أن يكون الراوتر متصلاً بالإنترنت.", fontSize = 11.sp, color = TextMuted)
            } else {
                if (online) {
                    Text("✓ تم الربط. الراوتر متصل بالخادم.", color = Accent, fontWeight = FontWeight.Bold)
                    info?.let { Text("${it.optString("board")} • RouterOS ${it.optString("v")}", fontSize = 12.sp, color = TextMuted) }
                    imported?.let { Text("تم جلب $it بروفايل من الراوتر. اذهب إلى Profiles & Plans لتسعيرها وتوليد الكروت.", fontSize = 12.sp) }
                } else {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(step.ifBlank { "في انتظار اتصال الراوتر…" }, fontSize = 12.sp)
                    }
                    Text("يتصل الراوتر بالخادم خلال ثوانٍ. إن تأخر: تأكد أن لديه إنترنت وDNS ووقتاً صحيحاً.", fontSize = 11.sp, color = TextMuted)
                }
            }
            if (busy && routerId == null && step.isNotBlank()) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(step, fontSize = 12.sp) }
            err?.let { ErrorCard(it) }
        }
    }, confirmButton = {
        if (routerId == null) Button({ link() }, enabled = name.isNotBlank() && host.isNotBlank() && user.isNotBlank() && !busy) { Text(if (busy) "جاري الربط…" else "ربط الآن") }
        else Button({ onSaved() }, enabled = online) { Text("تم") }
    }, dismissButton = { TextButton({ if (!busy) { if (routerId != null) onSaved() else onCancel() } }) { Text(if (routerId != null) "إغلاق" else "إلغاء") } })
}
