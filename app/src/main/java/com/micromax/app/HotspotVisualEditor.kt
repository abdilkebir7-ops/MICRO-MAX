package com.micromax.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

data class HotspotPreset(val name: String, val brand: String, val title: String, val subtitle: String, val button: String, val primary: String, val background: String)

private val hotspotPresets = listOf(
    HotspotPreset("نيون عصري", "بوابة الشبكة", "اتصال سريع وآمن", "تصميم حديث للشبكات العامة", "دخول إلى الإنترنت", "#22D3EE", "#07111F"),
    HotspotPreset("كلاسيكي فاخر", "شبكة الضيوف", "مرحباً بك", "خدمة Wi‑Fi موثوقة", "اتصال الآن", "#D4A84F", "#241B10"),
    HotspotPreset("بسيط تجاري", "WiFi", "سجّل الدخول", "أدخل بيانات الكرت للمتابعة", "تسجيل الدخول", "#2563EB", "#F1F5F9"),
    HotspotPreset("مقهى", "Coffee WiFi", "أهلاً بك في المقهى", "اتصل واستمتع بوقتك", "اتصال مجاني", "#F59E0B", "#2A1A13"),
    HotspotPreset("فندق", "Guest Network", "مرحباً بضيوفنا", "إنترنت سريع طوال إقامتك", "بدء الاتصال", "#66D9C0", "#102B2A")
)

@Composable
fun HotspotVisualEditorPage(api: Api, router: JSONObject?) {
    var brand by remember { mutableStateOf(hotspotPresets[0].brand) }
    var title by remember { mutableStateOf(hotspotPresets[0].title) }
    var subtitle by remember { mutableStateOf(hotspotPresets[0].subtitle) }
    var primary by remember { mutableStateOf(hotspotPresets[0].primary) }
    var background by remember { mutableStateOf(hotspotPresets[0].background) }
    var button by remember { mutableStateOf(hotspotPresets[0].button) }
    var saving by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val login = generateLoginHtml(brand, title, subtitle, primary, background, button)
    val status = generateStatusHtml(brand, primary, background)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("محرر HotSpot البصري", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("قوالب جاهزة • معاينة مباشرة • نشر login.html و status.html", fontSize = 12.sp, color = TextMuted) }
            Icon(Icons.Default.Web, null, tint = Accent, modifier = Modifier.size(32.dp))
        }
        if (router == null) {
            GlassCard { Icon(Icons.Default.Router, null, tint = Accent); Text("اختر راوتر MikroTik أولاً", fontWeight = FontWeight.Bold); Text("بعد اختيار الراوتر يمكنك تصميم الصفحات ونشرها مع نسخة احتياطية.", color = TextMuted) }
        } else {
            Surface(color = Accent.copy(alpha = .08f), shape = RoundedCornerShape(14.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Router, null, tint = Accent); Spacer(Modifier.width(8.dp)); Text(router.optString("name", "Router"), fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Text(router.optString("host", ""), fontSize = 11.sp, color = TextMuted) } }
            GlassCard {
                Text("قوالب جاهزة", fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text("اختر تصميمًا ثم عدّل النصوص والألوان بصريًا.", color = TextMuted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    hotspotPresets.forEach { preset ->
                        Card(onClick = { brand = preset.brand; title = preset.title; subtitle = preset.subtitle; button = preset.button; primary = preset.primary; background = preset.background }, modifier = Modifier.width(132.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = parseHotspotColor(preset.background, Color.DarkGray))) {
                            Column(Modifier.padding(10.dp)) { Box(Modifier.fillMaxWidth().height(30.dp).background(parseHotspotColor(preset.primary, Accent), RoundedCornerShape(8.dp))); Text(preset.name, color = if (preset.background == "#F1F5F9") Color.DarkGray else Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp); Text("تطبيق القالب", color = if (preset.background == "#F1F5F9") Color.DarkGray else Color.White, fontSize = 9.sp) }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(tab == 0, { tab = 0 }, { Text("login.html") }, leadingIcon = { Icon(Icons.Default.Login, null) }); FilterChip(tab == 1, { tab = 1 }, { Text("status.html") }, leadingIcon = { Icon(Icons.Default.Speed, null) }) }
            GlassCard { Text("المعاينة المباشرة", fontWeight = FontWeight.Black, fontSize = 20.sp); if (tab == 0) LoginPreview(brand, title, subtitle, primary, background, button) else StatusPreview(brand, primary, background) }
            GlassCard {
                Text("التحرير البصري", fontWeight = FontWeight.Black, fontSize = 19.sp)
                AppField(brand, { brand = it }, "اسم الشبكة", Icons.Default.Business)
                AppField(title, { title = it }, "العنوان", Icons.Default.Title)
                AppField(subtitle, { subtitle = it }, "الوصف", Icons.Default.Description)
                AppField(button, { button = it }, "نص الزر", Icons.Default.TouchApp)
                Text("اللون الرئيسي", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Swatches(primary) { primary = it }
                Text("لون الخلفية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Swatches(background) { background = it }
            }
            GlassCard {
                Text("النشر إلى MikroTik", fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text("سيتم التحقق من المتغيرات المطلوبة وإنشاء Backup قبل النشر.", fontSize = 11.sp, color = TextMuted)
                msg?.let { Text(it, fontSize = 11.sp, color = if (it.startsWith("تم")) Green else Red) }
                Button(enabled = !saving, onClick = { scope.launch { saving = true; msg = null; try { val body = JSONObject().put("loginHtml", login).put("statusHtml", status); api.post("/api/routers/${router.optString("id")}/hotspot-design/publish", body.toString()); msg = "تم نشر login.html و status.html مع Backup تلقائي" } catch (e: Exception) { msg = e.message ?: "فشل النشر" } finally { saving = false } } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.width(7.dp)); Text(if (saving) "جاري النشر…" else "نشر التصميم للراوتر") }
            }
        }
    }
}

@Composable private fun Swatches(selected: String, onSelect: (String) -> Unit) {
    val colors = listOf("#2563EB", "#22D3EE", "#8B5CF6", "#D4A84F", "#F59E0B", "#EF476F", "#102B2A", "#F1F5F9", "#07111F")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) { colors.forEach { value -> Box(Modifier.size(38.dp).background(parseHotspotColor(value, Color.Gray), RoundedCornerShape(50)).clickable { onSelect(value) }, contentAlignment = Alignment.Center) { if (selected.equals(value, true)) Icon(Icons.Default.Check, null, tint = if (value == "#F1F5F9") Color.Black else Color.White) } } }
}

private fun parseHotspotColor(v: String, fallback: Color) = try { Color(android.graphics.Color.parseColor(v)) } catch (_: Exception) { fallback }

@Composable private fun LoginPreview(brand: String, title: String, subtitle: String, primary: String, bg: String, button: String) { Box(Modifier.fillMaxWidth().height(340.dp).background(parseHotspotColor(bg, Color(0xFF07111F)), RoundedCornerShape(24.dp)).padding(18.dp), contentAlignment = Alignment.Center) { Column(Modifier.widthIn(max = 390.dp).fillMaxWidth().background(Color.White.copy(alpha = .10f), RoundedCornerShape(24.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(brand, fontSize = 26.sp, fontWeight = FontWeight.Black); Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = .65f)); Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Black.copy(alpha = .25f), RoundedCornerShape(13.dp))); Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Black.copy(alpha = .25f), RoundedCornerShape(13.dp))); Button(onClick = {}, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = parseHotspotColor(primary, Accent))) { Text(button, color = Color.Black, fontWeight = FontWeight.Black) } } } }

@Composable private fun StatusPreview(brand: String, primary: String, bg: String) { Box(Modifier.fillMaxWidth().height(340.dp).background(parseHotspotColor(bg, Color(0xFF07111F)), RoundedCornerShape(24.dp)).padding(18.dp), contentAlignment = Alignment.Center) { Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().background(Color.White.copy(alpha = .10f), RoundedCornerShape(24.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(brand, fontSize = 26.sp, fontWeight = FontWeight.Black); Text("حالة الاتصال", color = Color.White.copy(alpha = .65f)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(2) { Box(Modifier.weight(1f).height(70.dp).background(Color.Black.copy(alpha = .25f), RoundedCornerShape(14.dp))) } }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(2) { Box(Modifier.weight(1f).height(70.dp).background(Color.Black.copy(alpha = .25f), RoundedCornerShape(14.dp))) } }; Text("تسجيل الخروج", color = parseHotspotColor(primary, Accent), fontWeight = FontWeight.Bold) } } }

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
private fun generateLoginHtml(brand: String, title: String, subtitle: String, primary: String, bg: String, button: String) = """<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)}</title><style>:root{--p:${esc(primary)};--bg:${esc(bg)}}*{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;font-family:Arial,sans-serif;background:linear-gradient(145deg,var(--bg),#0f172a);color:#f8fafc;padding:20px}.card{width:min(430px,100%);padding:28px;border:1px solid #ffffff22;border-radius:28px;background:#ffffff0d;backdrop-filter:blur(20px)}input,button{width:100%;padding:15px;margin-top:10px;border-radius:14px}input{border:1px solid #ffffff22;background:#0004;color:white}button{border:0;background:var(--p);font-weight:900}</style></head><body><main class="card"><h1>${esc(brand)}</h1><p>${esc(title)}</p><small>${esc(subtitle)}</small><div style="color:#fb7185;margin-top:8px">$(error)</div><form action="$(link-login-only)" method="post"><input type="hidden" name="dst" value="$(link-orig)"><input name="username" placeholder="اسم المستخدم" required><input type="password" name="password" placeholder="كلمة المرور" required><button type="submit">${esc(button)}</button></form></main><script>(function(){try{var q=new URLSearchParams(location.search),u=q.get('username'),p=q.get('password'),f=document.forms[0];if(!u||!p||!f)return;f.username.value=u;f.password.value=p;if(q.get('auto')==='1'&&!sessionStorage.getItem('mmauto')){sessionStorage.setItem('mmauto','1');f.submit();}}catch(e){}})();</script></body></html>"""
private fun generateStatusHtml(brand: String, primary: String, bg: String) = """<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(brand)} Status</title><style>:root{--p:${esc(primary)};--bg:${esc(bg)}}body{margin:0;min-height:100vh;display:grid;place-items:center;font-family:Arial,sans-serif;background:linear-gradient(145deg,var(--bg),#0f172a);color:#f8fafc;padding:20px}.card{width:min(500px,100%);padding:28px;border:1px solid #ffffff22;border-radius:28px;background:#ffffff0d}.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.item{padding:15px;border-radius:15px;background:#0004}.k{color:#94a3b8;font-size:12px}.v{font-weight:800;margin-top:5px}a{color:var(--p)}</style></head><body><main class="card"><h1>${esc(brand)}</h1><p>حالة الاتصال</p><div class="grid"><div class="item"><div class="k">المستخدم</div><div class="v">$(username)</div></div><div class="item"><div class="k">IP</div><div class="v">$(ip)</div></div><div class="item"><div class="k">التحميل</div><div class="v">$(bytes-out-nice)</div></div><div class="item"><div class="k">الرفع</div><div class="v">$(bytes-in-nice)</div></div></div><p>مدة الجلسة: $(uptime)</p><p>المتبقي: $(session-time-left)</p><a href="$(link-logout)">تسجيل الخروج</a></main></body></html>"""
