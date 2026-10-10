package com.micromax.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun humanDuration(min: Int): String = when {
    min <= 0 -> "-"
    min % 1440 == 0 -> "${min / 1440} يوم"
    min % 60 == 0 -> "${min / 60} ساعة"
    else -> "$min دقيقة"
}

/** Button "جلب بروفايلات الراوتر": reads the real MikroTik profiles, then price them and generate cards in bulk. */
@Composable
fun RouterProfilesSection(api: Api, router: JSONObject, onChanged: () -> Unit) {
    val routerId = router.optString("id")
    val scope = rememberCoroutineScope()
    var items by remember(routerId) { mutableStateOf<JSONArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var pricing by remember { mutableStateOf<JSONObject?>(null) }
    var generating by remember { mutableStateOf<JSONObject?>(null) }

    fun fetch() = scope.launch {
        busy = true; err = null; msg = null
        try {
            val r = JSONObject(api.post("/api/routers/$routerId/hotspot-profiles/import"))
            items = r.optJSONArray("profiles") ?: JSONArray()
            msg = "تم الجلب: ${r.optInt("added")} جديد • ${r.optInt("updated")} محدّث"
        } catch (e: Exception) { err = friendlyImportError(e.message) }
        busy = false
    }

    GlassCard {
        Text("بروفايلات MikroTik الفعلية", fontWeight = FontWeight.Bold)
        Text("اجلب البروفايلات الموجودة على الراوتر، حدّد سعرها، ثم ولّد الكروت.", fontSize = 12.sp, color = TextMuted)
        Button({ fetch() }, Modifier.fillMaxWidth(), enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.CloudDownload, null)
            Spacer(Modifier.width(8.dp)); Text("جلب بروفايلات الراوتر")
        }
        err?.let { ErrorCard(it) }
        msg?.let { Text(it, fontSize = 12.sp, color = Accent) }
    }
    items?.let { arr ->
        if (arr.length() == 0) EmptyCard("لا توجد بروفايلات على الراوتر")
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val ready = p.optBoolean("readyForCards")
            GlassCard {
                Text(p.optString("name"), fontWeight = FontWeight.Bold)
                Text("السرعة: ${p.optString("rateLimit").ifBlank { "-" }} • المدة: ${humanDuration(p.optInt("durationMinutes"))} • أجهزة: ${p.optInt("sharedUsers", 1)}", fontSize = 12.sp, color = TextMuted)
                if (ready) {
                    Text("السعر: ${p.optDouble("planPrice")} XOF", color = Accent, fontSize = 13.sp)
                    Button({ generating = p }, Modifier.fillMaxWidth()) { Icon(MmIcons.Scan, null); Spacer(Modifier.width(8.dp)); Text("توليد كروت") }
                } else {
                    Text("يحتاج سعراً قبل التوليد", color = TextMuted, fontSize = 12.sp)
                    OutlinedButton({ pricing = p }, Modifier.fillMaxWidth()) { Icon(MmIcons.Money, null); Spacer(Modifier.width(8.dp)); Text("تحديد السعر") }
                }
            }
        }
    }
    pricing?.let { p ->
        PriceDialog(api, routerId, p.optString("name"), { pricing = null }, { pricing = null; fetch(); onChanged() })
    }
    generating?.let { p ->
        GenerateDialog(api, routerId, p, { generating = null }) { generating = null; onChanged() }
    }
}

private fun friendlyImportError(m: String?): String {
    val s = m.orEmpty()
    return when {
        "AGENT_NOT_CONNECTED" in s -> "الراوتر لم يتصل بعد. شغّل سكربت الـ Agent على الراوتر."
        "AGENT_PROFILES_NOT_REPORTED" in s -> "حدّث سكربت الـ Agent (Rotate) ليرسل قائمة البروفايلات."
        "ROUTER_CONNECTION_FAILED" in s -> "تعذر اتصال السيرفر بالراوتر. افحص الاتصال من شاشة الراوترات."
        else -> s.ifBlank { "تعذر جلب البروفايلات" }
    }
}

@Composable
private fun PriceDialog(api: Api, routerId: String, profile: String, cancel: () -> Unit, done: () -> Unit) {
    var price by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = cancel, title = { Text("سعر $profile") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(price, { price = it.filter { c -> c.isDigit() || c == '.' } }, "السعر XOF", MmIcons.Money)
            err?.let { Text(it, color = Red) }
        }
    }, confirmButton = {
        Button({
            val v = price.toDoubleOrNull()
            if (v == null || v <= 0) { err = "أدخل سعراً أكبر من صفر"; return@Button }
            scope.launch {
                busy = true; err = null
                try { api.post("/api/routers/$routerId/plans", JSONObject().put("profileName", profile).put("name", profile).put("price", v).put("currency", "XOF").toString()); done() }
                catch (e: Exception) { err = e.message }
                busy = false
            }
        }, enabled = !busy) { Text("حفظ") }
    }, dismissButton = { TextButton(cancel) { Text("إلغاء") } })
}

@Composable
private fun GenerateDialog(api: Api, routerId: String, profile: JSONObject, cancel: () -> Unit, done: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("mm_prefs", Context.MODE_PRIVATE) }
    var count by remember { mutableStateOf("100") }
    var prefix by remember { mutableStateOf("MM") }
    var digits by remember { mutableStateOf("6") }
    var portal by remember { mutableStateOf(prefs.getString("portal_$routerId", "") ?: "") }
    var pin by remember { mutableStateOf(false) }
    var enforce by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var made by remember { mutableStateOf<JSONArray?>(null) }
    var sheet by remember { mutableStateOf(SheetLayout.NORMAL) }
    val scope = rememberCoroutineScope()
    val n = count.toIntOrNull() ?: 0

    AlertDialog(onDismissRequest = { if (!busy) cancel() }, title = { Text("توليد كروت: ${profile.optString("name")}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (result != null) {
                Text(result!!, color = Accent)
                if ((made?.length() ?: 0) > 0) {
                    Text("حجم الورقة (A4)", fontSize = 12.sp, color = TextMuted)
                    SheetLayout.values().forEach { l ->
                        if (l == sheet) Button({ sheet = l }, Modifier.fillMaxWidth()) { Text(l.label) }
                        else OutlinedButton({ sheet = l }, Modifier.fillMaxWidth()) { Text(l.label) }
                    }
                    Button({ made?.let { printCardSheet(ctx, it, sheet, "MICRO-MAX ${profile.optString("name")}") } }, Modifier.fillMaxWidth()) {
                        Icon(MmIcons.Print, null); Spacer(Modifier.width(8.dp)); Text("طباعة الكروت الآن")
                    }
                    Text("اختر طابعتك أو «حفظ كـ PDF». كل كرت يحمل QR دخول تلقائي.", fontSize = 11.sp, color = TextMuted)
                }
            } else {
                AppField(count, { count = it.filter(Char::isDigit).take(4) }, "العدد (1 - 5000)", MmIcons.Tag)
                AppField(prefix, { prefix = it.uppercase().filter(Char::isLetterOrDigit).take(6) }, "بادئة الكرت", Icons.Outlined.Tag)
                AppField(digits, { digits = it.filter(Char::isDigit).take(2) }, "عدد أرقام الكود", Icons.Outlined.Pin)
                AppField(portal, { portal = it.trim() }, "رابط HotSpot (مثال http://192.168.88.1/login)", MmIcons.Link)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("كروت PIN (كود فقط)"); Switch(pin, { pin = it }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("فرض المدة على الراوتر"); Switch(enforce, { enforce = it }) }
                err?.let { Text(it, color = Red) }
            }
        }
    }, confirmButton = {
        if (result != null) Button({ done() }) { Text("تم") } else Button({
            if (n !in 1..5000) { err = "العدد بين 1 و5000"; return@Button }
            if (!portal.startsWith("http")) { err = "أدخل رابط HotSpot يبدأ بـ http"; return@Button }
            scope.launch {
                busy = true; err = null
                try {
                    val body = JSONObject().put("count", n).put("planId", profile.optString("planId")).put("prefix", prefix.ifBlank { "MM" })
                        .put("usernameDigits", digits.toIntOrNull() ?: 6).put("usernameLetters", 0).put("passwordLength", 8)
                        .put("portalUrl", portal).put("passwordMode", if (pin) "pin" else "userpass").put("enforceDuration", enforce)
                    val r = JSONObject(api.post("/api/routers/$routerId/cards/generate", body.toString()))
                    prefs.edit().putString("portal_$routerId", portal).apply()
                    made = r.optJSONArray("cards")
                    result = "تم توليد ${r.optInt("count", n)} كرت." + if (r.optString("provisioning") == "pending") "\nسيُنشأ على الراوتر خلال دقائق، ولا تُباع الكروت قبل تأكيد الراوتر." else ""
                } catch (e: Exception) { err = e.message ?: "فشل التوليد" }
                busy = false
            }
        }, enabled = !busy) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("توليد $n كرت") }
    }, dismissButton = { if (result == null) TextButton({ if (!busy) cancel() }) { Text("إلغاء") } })
}
