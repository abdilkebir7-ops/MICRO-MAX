package com.micromax.app

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// الأدمن الذي يظهر له زر «إضافة قوالب وتصاميم». هذا إخفاء واجهة فقط؛
// الحماية الحقيقية يجب أن تكون في الـ API (صلاحيات الحساب).
const val TPL_ADMIN_EMAIL = "abdilkebir7@gmail.com"
private const val TPL_PREFS_KEY = "login_templates"

data class LoginTpl(
    val id: String, val name: String,
    val brandA: String, val brandB: String, val tagline: String, val logo: String,
    val bg: String, val bg2: String, val tx: String, val p1: String, val p2: String, val p3: String, val gold: String,
    val dark: Boolean,
    val member: Boolean = true, val voucher: Boolean = true, val qr: Boolean = true, val trial: Boolean = true,
    val defTab: String = "member", val vpass: String = "same",
    val subAr: String = "اختر طريقة الدخول المناسبة لك", val subFr: String = "Choisissez votre méthode d’accès",
    val goAr: String = "دخول إلى الإنترنت", val goFr: String = "ACCÉDER À INTERNET",
    val builtin: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("brandA", brandA).put("brandB", brandB)
        .put("tagline", tagline).put("logo", logo).put("bg", bg).put("bg2", bg2).put("tx", tx).put("p1", p1).put("p2", p2)
        .put("p3", p3).put("gold", gold).put("dark", dark).put("member", member).put("voucher", voucher).put("qr", qr)
        .put("trial", trial).put("defTab", defTab).put("vpass", vpass).put("subAr", subAr).put("subFr", subFr)
        .put("goAr", goAr).put("goFr", goFr)

    companion object {
        fun fromJson(o: JSONObject) = LoginTpl(
            id = o.optString("id"), name = o.optString("name", "قالب"),
            brandA = o.optString("brandA", "MICROMAX"), brandB = o.optString("brandB", "MAX"),
            tagline = o.optString("tagline", "PREMIUM WI-FI"), logo = o.optString("logo", "🚀"),
            bg = o.optString("bg", "#eaf6ff"), bg2 = o.optString("bg2", "#efeaff"), tx = o.optString("tx", "#0b1530"),
            p1 = o.optString("p1", "#0891b2"), p2 = o.optString("p2", "#6366f1"), p3 = o.optString("p3", "#a855f7"),
            gold = o.optString("gold", "#b7860b"), dark = o.optBoolean("dark", false),
            member = o.optBoolean("member", true), voucher = o.optBoolean("voucher", true),
            qr = o.optBoolean("qr", true), trial = o.optBoolean("trial", true),
            defTab = o.optString("defTab", "member"), vpass = o.optString("vpass", "same"),
            subAr = o.optString("subAr", "اختر طريقة الدخول المناسبة لك"), subFr = o.optString("subFr", "Choisissez votre méthode d’accès"),
            goAr = o.optString("goAr", "دخول إلى الإنترنت"), goFr = o.optString("goFr", "ACCÉDER À INTERNET")
        )
    }
}

val BUILTIN_LOGIN_TEMPLATES: List<LoginTpl> = listOf(
    LoginTpl(id = "cyan-blue", name = "Cyan Blue", brandA = "MICROMAX", brandB = "MAX", tagline = "PREMIUM WI-FI", logo = "📶", bg = "#040b1c", bg2 = "#0a1a3d", tx = "#eaf2ff", p1 = "#1d9bff", p2 = "#2563eb", p3 = "#4f46e5", gold = "#60a5fa", dark = true, builtin = true),
    LoginTpl(id = "aurora-light", name = "Aurora Light", brandA = "MICROMAX", brandB = "MAX", tagline = "PREMIUM WI-FI", logo = "🚀", bg = "#eaf6ff", bg2 = "#efeaff", tx = "#0b1530", p1 = "#0891b2", p2 = "#6366f1", p3 = "#a855f7", gold = "#b7860b", dark = false, builtin = true),
    LoginTpl(id = "emerald", name = "Emerald", brandA = "MICROMAX", brandB = "MAX", tagline = "PREMIUM WI-FI", logo = "🌿", bg = "#ecfdf5", bg2 = "#d1fae5", tx = "#052e2b", p1 = "#0d9488", p2 = "#059669", p3 = "#16a34a", gold = "#d97706", dark = false, builtin = true),
    LoginTpl(id = "sunset", name = "Sunset", brandA = "MICROMAX", brandB = "MAX", tagline = "PREMIUM WI-FI", logo = "🌅", bg = "#fff7ed", bg2 = "#ffe4e6", tx = "#3b1d12", p1 = "#f97316", p2 = "#e11d48", p3 = "#db2777", gold = "#b45309", dark = false, builtin = true),
    LoginTpl(id = "midnight-glass", name = "Midnight Glass", brandA = "MICROMAX", brandB = "MAX", tagline = "PREMIUM WI-FI", logo = "🚀", bg = "#0b0720", bg2 = "#1a1040", tx = "#f1ecff", p1 = "#22d3ee", p2 = "#8b5cf6", p3 = "#d946ef", gold = "#fbbf24", dark = true, builtin = true),
    LoginTpl(id = "minimal", name = "Minimal", brandA = "MICROMAX", brandB = "MAX", tagline = "WI-FI", logo = "📡", bg = "#f8fafc", bg2 = "#eef2f7", tx = "#0f172a", p1 = "#111827", p2 = "#334155", p3 = "#475569", gold = "#64748b", dark = false, builtin = true)
)

private fun tplLoadCustom(prefs: SharedPreferences): List<LoginTpl> = try {
    val a = JSONArray(prefs.getString(TPL_PREFS_KEY, "[]"))
    (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(LoginTpl::fromJson) }
} catch (_: Exception) { emptyList() }

private fun tplSaveCustom(prefs: SharedPreferences, list: List<LoginTpl>) {
    val a = JSONArray(); list.forEach { a.put(it.toJson()) }
    prefs.edit().putString(TPL_PREFS_KEY, a.toString()).apply()
}

// ---------- توليد login.html من القالب (يطابق gen.py) ----------
private fun tplRgb(h: String): String {
    val c = h.trim().removePrefix("#")
    if (c.length != 6) return "0,0,0"
    return try { "${c.substring(0, 2).toInt(16)},${c.substring(2, 4).toInt(16)},${c.substring(4, 6).toInt(16)}" } catch (_: Exception) { "0,0,0" }
}
private fun tplMix(h: String, w: Double): String {
    val p = tplRgb(h).split(",").map { it.toInt() }
    return "#" + p.joinToString("") { "%02x".format(Math.round(it + (255 - it) * w).toInt()) }
}
private fun tplEsc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("%%", "%")
private fun tplJs(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace("\n", " ")

fun renderLoginHtml(ctx: Context, t: LoginTpl, preview: Boolean): String {
    val base = ctx.assets.open("login_base.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
    val d = t.dark
    val tabs = listOf("member" to t.member, "voucher" to t.voucher, "qr" to t.qr).filter { it.second }.map { it.first }.ifEmpty { listOf("member") }
    val defTab = if (t.defTab in tabs) t.defTab else tabs[0]
    var css = ""
    listOf("member", "voucher", "qr").forEach { if (it !in tabs) css += ".seg button[data-m=$it]{display:none}" }
    if (tabs.size == 1) css += ".seg{display:none}"
    if (!t.trial) css += ".trial{display:none}"
    val lg = t.logo.trim()
    val logo = if (lg.startsWith("http://") || lg.startsWith("https://") || lg.startsWith("/")) "<img src=\"${tplEsc(lg)}\" alt=\"\">" else "<span class=\"rk\">${tplEsc(lg)}</span>"
    val v = mapOf(
        "BG" to t.bg, "BG2" to t.bg2, "TX" to t.tx, "P1" to t.p1, "P2" to t.p2, "P3" to t.p3, "GOLD" to t.gold, "GOLD2" to tplMix(t.gold, .35),
        "P1RGB" to tplRgb(t.p1), "P2RGB" to tplRgb(t.p2), "GOLDRGB" to tplRgb(t.gold),
        "CARD" to if (d) "rgba(255,255,255,.06)" else "rgba(255,255,255,.65)",
        "CS" to if (d) "rgba(${tplRgb(t.bg2)},.94)" else "rgba(255,255,255,.94)",
        "MUT" to if (d) "#9db0d0" else "#5b6b88",
        "LINE" to if (d) "rgba(150,180,255,.22)" else "rgba(30,60,120,.14)",
        "INP" to if (d) "rgba(0,0,0,.28)" else "#ffffff",
        "SHADOW" to if (d) "0 25px 60px rgba(0,0,0,.45)" else "0 22px 50px rgba(60,70,160,.18)",
        "BRANDA" to tplEsc(t.brandA), "BRANDB" to tplEsc(t.brandB), "TAGLINE" to tplEsc(t.tagline),
        "SUBAR" to tplJs(t.subAr), "SUBFR" to tplJs(t.subFr), "GOAR" to tplJs(t.goAr), "GOFR" to tplJs(t.goFr),
        "VPASS" to (if (t.vpass == "empty") "empty" else "same"), "LOGO" to logo, "DEFTAB" to defTab, "EXTRACSS" to css
    )
    var out = base
    v.forEach { (k, x) -> out = out.replace("%%$k%%", x) }
    if (preview) {
        val opt = RegexOption.DOT_MATCHES_ALL
        out = Regex("<!--\\$\\(if error\\)-->.*?<!--\\$\\(endif\\)-->", opt).replace(out, "")
        out = Regex("<!--\\$\\(if chap-id\\)-->.*?<!--\\$\\(endif\\)-->", opt).replace(out, "")
        out = out.replace("<!--\$(if trial == 'yes')-->", "").replace("<!--\$(endif)-->", "")
        listOf("\$(link-login-only)" to "#", "\$(link-orig-esc)" to "", "\$(link-orig)" to "", "\$(username)" to "", "\$(mac-esc)" to "aabbcc")
            .forEach { (a, b) -> out = out.replace(a, b) }
    }
    return out
}

private fun tplColor(h: String): Color = try { Color(android.graphics.Color.parseColor(h.trim())) } catch (_: Exception) { Color.Gray }
private val tplHex = Regex("^#[0-9a-fA-F]{6}$")

@Composable
private fun TplSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChange)
    }
}

@Composable
private fun TplText(v: String, label: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(v, onChange, label = { Text(label) }, singleLine = true, modifier = modifier, colors = fieldColors())
}

@Composable
private fun TplColorField(v: String, label: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(tplColor(v)))
        OutlinedTextField(v, onChange, label = { Text(label) }, singleLine = true, isError = !tplHex.matches(v.trim()), modifier = Modifier.weight(1f), colors = fieldColors())
    }
}

@Composable
fun LoginTemplatesPage(api: Api, router: JSONObject?, prefs: SharedPreferences) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val isAdmin = (prefs.getString("last_email", "") ?: "").trim().equals(TPL_ADMIN_EMAIL, ignoreCase = true)
    var custom by remember { mutableStateOf(tplLoadCustom(prefs)) }
    var draft by remember { mutableStateOf<LoginTpl?>(null) }
    var previewHtml by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<LoginTpl?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val all = BUILTIN_LOGIN_TEMPLATES + custom

    fun publish(t: LoginTpl) {
        val r = router ?: run { message = "اختر راوتر أولاً"; return }
        scope.launch {
            busy = true; message = null
            try {
                val html = renderLoginHtml(ctx, t, false)
                val id = r.optString("id")
                val body = JSONObject().put("content", html).toString()
                api.post("/api/routers/$id/hotspot-login/validate", body)
                api.post("/api/routers/$id/hotspot-login/publish", body)
                message = "تم نشر قالب «${t.name}» على ${r.optString("name", "الراوتر")} (يُنشأ Backup تلقائي)"
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "تعذر النشر" }
            finally { busy = false }
        }
    }

    val d = draft
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("قوالب Login", fontSize = 27.sp, fontWeight = FontWeight.Black)
        Text(router?.optString("name") ?: "اختر راوتر لنشر القالب", color = TextMuted)
        message?.let { GlassCard { Text(it, fontSize = 12.sp) } }
        if (d == null) {
            if (isAdmin) Button({ draft = BUILTIN_LOGIN_TEMPLATES[1].copy(id = "custom-${System.currentTimeMillis()}", name = "قالب جديد", builtin = false) }, Modifier.fillMaxWidth()) { Text("➕ إضافة قالب وتصميم") }
            else Text("عرض القوالب ونشرها متاح؛ إضافة وتعديل القوالب للأدمن فقط.", color = TextMuted, fontSize = 11.sp)
            all.forEach { t ->
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(t.bg, t.bg2, t.p1, t.p2, t.gold).forEach { Box(Modifier.size(22.dp).clip(CircleShape).background(tplColor(it))) }
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.Bold)
                            Text(if (t.builtin) "جاهز" else "مخصص", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ previewHtml = renderLoginHtml(ctx, t, true) }) { Text("معاينة") }
                        Button({ confirm = t }, enabled = !busy && router != null) { Text("نشر") }
                        if (isAdmin) {
                            OutlinedButton({ draft = t.copy(id = if (t.builtin) "custom-${System.currentTimeMillis()}" else t.id, name = if (t.builtin) t.name + " (نسخة)" else t.name, builtin = false) }) { Text(if (t.builtin) "نسخ وتعديل" else "تعديل") }
                            if (!t.builtin) TextButton({ custom = custom.filter { it.id != t.id }; tplSaveCustom(prefs, custom) }) { Text("حذف") }
                        }
                    }
                }
            }
        } else {
            Text(if (all.any { it.id == d.id }) "تعديل القالب" else "قالب جديد", style = MaterialTheme.typography.titleLarge)
            TplText(d.name, "اسم القالب", onChange = { draft = d.copy(name = it) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TplText(d.brandA, "الاسم (1)", { draft = d.copy(brandA = it) }, Modifier.weight(1f))
                TplText(d.brandB, "الاسم (2 ملوّن)", { draft = d.copy(brandB = it) }, Modifier.weight(1f))
            }
            TplText(d.tagline, "الشعار النصي الصغير", onChange = { draft = d.copy(tagline = it) })
            TplText(d.logo, "الشعار: إيموجي أو رابط صورة https://", onChange = { draft = d.copy(logo = it) })
            TplSwitch("وضع داكن", d.dark) { draft = d.copy(dark = it) }
            TplColorField(d.bg, "الخلفية 1") { draft = d.copy(bg = it) }
            TplColorField(d.bg2, "الخلفية 2") { draft = d.copy(bg2 = it) }
            TplColorField(d.tx, "لون النص") { draft = d.copy(tx = it) }
            TplColorField(d.p1, "اللون الأساسي") { draft = d.copy(p1 = it) }
            TplColorField(d.p2, "اللون الثانوي") { draft = d.copy(p2 = it) }
            TplColorField(d.p3, "نهاية تدرج الزر") { draft = d.copy(p3 = it) }
            TplColorField(d.gold, "لون التمييز") { draft = d.copy(gold = it) }
            Text("طرق الدخول", fontWeight = FontWeight.Bold)
            TplSwitch("Member (عضو)", d.member) { draft = d.copy(member = it) }
            TplSwitch("Voucher (قسيمة)", d.voucher) { draft = d.copy(voucher = it) }
            TplSwitch("QR Code", d.qr) { draft = d.copy(qr = it) }
            TplSwitch("زر التجربة المجانية", d.trial) { draft = d.copy(trial = it) }
            Text("التبويب الافتراضي", fontSize = 12.sp, color = TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("member" to "Member", "voucher" to "Voucher", "qr" to "QR").forEach { (k, n) -> FilterChip(d.defTab == k, { draft = d.copy(defTab = k) }, { Text(n) }) }
            }
            Text("كلمة مرور القسيمة", fontSize = 12.sp, color = TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(d.vpass == "same", { draft = d.copy(vpass = "same") }, { Text("= الكود") })
                FilterChip(d.vpass == "empty", { draft = d.copy(vpass = "empty") }, { Text("بدون") })
            }
            TplText(d.subAr, "النص التعريفي (عربي)", onChange = { draft = d.copy(subAr = it) })
            TplText(d.goAr, "نص الزر (عربي)", onChange = { draft = d.copy(goAr = it) })
            TplText(d.subFr, "Texte (FR)", onChange = { draft = d.copy(subFr = it) })
            TplText(d.goFr, "Bouton (FR)", onChange = { draft = d.copy(goFr = it) })
            val colorsOk = listOf(d.bg, d.bg2, d.tx, d.p1, d.p2, d.p3, d.gold).all { tplHex.matches(it.trim()) }
            if (!colorsOk) Text("الألوان بصيغة #RRGGBB فقط", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ previewHtml = renderLoginHtml(ctx, d, true) }, enabled = colorsOk) { Text("معاينة") }
                Button({
                    val t = d.copy(name = d.name.trim().ifBlank { "قالب" }, builtin = false)
                    custom = custom.filter { it.id != t.id } + t; tplSaveCustom(prefs, custom); draft = null; message = "تم حفظ القالب «${t.name}»"
                }, enabled = colorsOk && d.name.isNotBlank()) { Text("حفظ") }
                TextButton({ draft = null }) { Text("إلغاء") }
            }
        }
    }

    previewHtml?.let { html ->
        AlertDialog(onDismissRequest = { previewHtml = null }, confirmButton = { TextButton({ previewHtml = null }) { Text("إغلاق") } },
            text = {
                AndroidView(factory = { c -> WebView(c).apply { settings.javaScriptEnabled = true } },
                    update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) },
                    modifier = Modifier.fillMaxWidth().height(520.dp))
            })
    }
    confirm?.let { t ->
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text("نشر القالب؟") },
            text = { Text("سيُستبدل login.html الحالي على ${router?.optString("name") ?: "الراوتر"} بالقالب «${t.name}» ويُنشأ Backup تلقائي.") },
            confirmButton = { TextButton({ confirm = null; publish(t) }) { Text("نشر") } },
            dismissButton = { TextButton({ confirm = null }) { Text("إلغاء") } })
    }
}
