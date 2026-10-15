package com.micromax.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
    HotspotPreset("Wi‑Fi Neon", "بوابة الشبكة", "اتصال سريع وآمن", "اختر باقتك ثم أدخل بيانات الكرت", "دخول إلى الإنترنت", "#22D3EE", "#07111F"),
    HotspotPreset("Violet Zone", "WI‑FI ZONE", "مرحباً بك في شبكتنا", "سرعة مستقرة وتجربة مميزة", "Connexion", "#E879F9", "#17102C"),
    HotspotPreset("Cyber Blue", "CYBER WIFI", "شبكتك تحت السيطرة", "اتصال آمن وسريع طوال اليوم", "اتصل الآن", "#38BDF8", "#071A35"),
    HotspotPreset("Sunset Club", "SUNSET HOTSPOT", "استمتع بالاتصال", "باقات مرنة لكل احتياجاتك", "ابدأ التصفح", "#FB923C", "#2A130F"),
    HotspotPreset("Emerald Pro", "PRO NETWORK", "إنترنت احترافي", "حل موثوق للشركات والضيوف", "تسجيل الدخول", "#34D399", "#06251F"),
    HotspotPreset("Gold Lounge", "PREMIUM WIFI", "مرحباً بضيوفنا", "خدمة راقية واتصال مستقر", "اتصال VIP", "#F6C453", "#241A08"),
    HotspotPreset("Ocean Glass", "OCEAN NET", "ابقَ متصلاً", "إنترنت سريع في كل لحظة", "دخول آمن", "#60A5FA", "#071D2B"),
    HotspotPreset("Pink Pulse", "PULSE WIFI", "اتصل وانطلق", "تجربة نيون عصرية للشباب", "Connect", "#FB7185", "#2A0C22"),
    HotspotPreset("Coffee Dark", "COFFEE WIFI", "أهلاً بك في المقهى", "خذ استراحتك واتصل مجاناً", "اتصال مجاني", "#F59E0B", "#24150B"),
    HotspotPreset("Hotel Night", "GUEST NETWORK", "مرحباً بضيوفنا", "إنترنت سريع طوال إقامتك", "بدء الاتصال", "#66D9C0", "#102B2A"),
    HotspotPreset("Market Connect", "MARKET WIFI", "اتصال للزوار والعملاء", "اختر الباقة المناسبة لك", "شراء باقة", "#A78BFA", "#15112B"),
    HotspotPreset("Minimal Graphite", "NETWORK", "سجّل الدخول", "أدخل بيانات الكرت للمتابعة", "تسجيل الدخول", "#94A3B8", "#111827")
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
            Column(Modifier.weight(1f)) { Text("محرر HotSpot البصري",style=MaterialTheme.typography.headlineMedium); Text("قوالب جاهزة • معاينة مباشرة • نشر login.html و status.html", fontSize = 12.sp, color = TextMuted) }
            Icon(MmIcons.Web, null, tint = Accent, modifier = Modifier.size(32.dp))
        }
        if (router == null) {
            GlassCard { Icon(MmIcons.Router, null, tint = Accent); Text("اختر راوتر MikroTik أولاً", fontWeight = FontWeight.Bold); Text("بعد اختيار الراوتر يمكنك تصميم الصفحات ونشرها مع نسخة احتياطية.", color = TextMuted) }
        } else {
            Surface(color = Accent.copy(alpha = .08f), shape = RoundedCornerShape(14.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(MmIcons.Router, null, tint = Accent); Spacer(Modifier.width(8.dp)); Text(router.optString("name", "Router"), fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Text(router.optString("host", ""), fontSize = 11.sp, color = TextMuted) } }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(tab == 0, { tab = 0 }, { Text("login.html") }, leadingIcon = { Icon(Icons.Outlined.Login, null) }); FilterChip(tab == 1, { tab = 1 }, { Text("status.html") }, leadingIcon = { Icon(MmIcons.Gauge, null) }) }
            GlassCard { Text("المعاينة المباشرة", fontWeight = FontWeight.Black, fontSize = 20.sp); if (tab == 0) LoginPreview(brand, title, subtitle, primary, background, button) else StatusPreview(brand, primary, background) }
            GlassCard {
                Text("التحرير البصري", fontWeight = FontWeight.Black, fontSize = 19.sp)
                AppField(brand, { brand = it }, "اسم الشبكة", Icons.Outlined.Business)
                AppField(title, { title = it }, "العنوان", Icons.Outlined.Title)
                AppField(subtitle, { subtitle = it }, "الوصف", Icons.Outlined.Description)
                AppField(button, { button = it }, "نص الزر", Icons.Outlined.TouchApp)
                Text("اللون الرئيسي", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Swatches(primary) { primary = it }
                Text("لون الخلفية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Swatches(background) { background = it }
            }
            GlassCard {
                Text("النشر إلى MikroTik", fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text("سيتم التحقق من المتغيرات المطلوبة وإنشاء Backup قبل النشر.", fontSize = 11.sp, color = TextMuted)
                msg?.let { Text(it, fontSize = 11.sp, color = if (it.startsWith("تم")) Green else Red) }
                Button(enabled = !saving, onClick = { scope.launch { saving = true; msg = null; try { val body = JSONObject().put("loginHtml", login).put("statusHtml", status); api.post("/api/routers/${router.optString("id")}/hotspot-design/publish", body.toString()); msg = "تم نشر login.html و status.html مع Backup تلقائي" } catch (e: Exception) { msg = e.message ?: "فشل النشر" } finally { saving = false } } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CloudUpload, null); Spacer(Modifier.width(7.dp)); Text(if (saving) "جاري النشر…" else "نشر التصميم للراوتر") }
            }
        }
    }
}

@Composable private fun Swatches(selected: String, onSelect: (String) -> Unit) {
    val colors = listOf("#2563EB", "#22D3EE", "#8B5CF6", "#D4A84F", "#F59E0B", "#EF476F", "#102B2A", "#F1F5F9", "#07111F")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) { colors.forEach { value -> Box(Modifier.size(38.dp).background(parseHotspotColor(value, Color.Gray), RoundedCornerShape(50)).clickable { onSelect(value) }, contentAlignment = Alignment.Center) { if (selected.equals(value, true)) Icon(MmIcons.Check, null, tint = if (value == "#F1F5F9") Color.Black else Color.White) } } }
}

private fun parseHotspotColor(v: String, fallback: Color) = try { Color(android.graphics.Color.parseColor(v)) } catch (_: Exception) { fallback }

@Composable private fun LoginPreview(brand: String, title: String, subtitle: String, primary: String, bg: String, button: String) { Box(Modifier.fillMaxWidth().height(340.dp).background(parseHotspotColor(bg, Color(0xFF07111F)), RoundedCornerShape(24.dp)).padding(18.dp), contentAlignment = Alignment.Center) { Column(Modifier.widthIn(max = 390.dp).fillMaxWidth().background(Color.White.copy(alpha = .10f), RoundedCornerShape(24.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(brand, fontSize = 26.sp, fontWeight = FontWeight.Black); Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = .65f)); Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Black.copy(alpha = .25f), RoundedCornerShape(13.dp))); Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Black.copy(alpha = .25f), RoundedCornerShape(13.dp))); Button(onClick = {}, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = parseHotspotColor(primary, Accent))) { Text(button, color = Color.Black, fontWeight = FontWeight.Black) } } } }

@Composable private fun StatusPreview(brand: String, primary: String, bg: String) { Box(Modifier.fillMaxWidth().height(340.dp).background(parseHotspotColor(bg, Color(0xFF07111F)), RoundedCornerShape(24.dp)).padding(18.dp), contentAlignment = Alignment.Center) { Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().background(Color.White.copy(alpha = .10f), RoundedCornerShape(24.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(brand, fontSize = 26.sp, fontWeight = FontWeight.Black); Text("حالة الاتصال", color = Color.White.copy(alpha = .65f)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(2) { Box(Modifier.weight(1f).height(70.dp).background(Color.Black.copy(alpha = .25f), RoundedCornerShape(14.dp))) } }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(2) { Box(Modifier.weight(1f).height(70.dp).background(Color.Black.copy(alpha = .25f), RoundedCornerShape(14.dp))) } }; Text("تسجيل الخروج", color = parseHotspotColor(primary, Accent), fontWeight = FontWeight.Bold) } } }

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
private fun generateLoginHtml(brand: String, title: String, subtitle: String, primary: String, bg: String, button: String) = """<!doctype html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>${esc(title)}</title>
<style>:root{--p:${esc(primary)};--bg:${esc(bg)};--ink:#f8fafc;--muted:#9fb0c7;--line:#ffffff24}*{box-sizing:border-box}body{margin:0;min-height:100vh;padding:16px;display:grid;place-items:center;background:radial-gradient(circle at 12% 8%,${esc(primary)}55,transparent 30%),linear-gradient(145deg,var(--bg),#050816 78%);color:var(--ink);font-family:Tahoma,Arial,sans-serif}.shell{width:min(480px,100%)}.hero{height:165px;border-radius:26px 26px 0 0;display:grid;place-items:center;background:radial-gradient(circle at 50% 42%,${esc(primary)}88,transparent 29%),repeating-linear-gradient(135deg,#ffffff08 0 2px,transparent 2px 12px),#101a35;border:1px solid var(--line);overflow:hidden}.wifi{font-size:54px;text-shadow:0 0 25px var(--p)}.card{margin-top:-1px;border:1px solid var(--line);border-radius:0 0 28px 28px;padding:22px;background:#07111fee;box-shadow:0 28px 90px #000b}.brand{text-align:center;font-size:27px;font-weight:900;color:#ffd66b;letter-spacing:.4px}.sub{text-align:center;color:var(--muted);font-size:13px;margin:7px 0 18px}.tabs{display:flex;justify-content:center;gap:28px;color:#f7b2e8;border-bottom:1px solid var(--p);padding-bottom:9px;margin-bottom:17px}.tabs b{color:#ffe18a}.status{min-height:20px;color:#fb7185;font-size:13px;margin:8px 0}.field{position:relative;margin:10px 0}.field input{width:100%;height:54px;padding:0 47px 0 14px;border:1px solid #d45ad8aa;border-radius:15px;background:#304155;color:var(--ink);font-size:16px;outline:0}.field input:focus{border-color:var(--p);box-shadow:0 0 0 4px ${esc(primary)}33}.field span{position:absolute;right:16px;top:14px;font-size:20px}.primary{width:100%;height:54px;border:0;border-radius:15px;background:linear-gradient(135deg,#ffb02e,#ff9f1c);font-size:16px;font-weight:900;cursor:pointer}.plans{width:100%;border-collapse:collapse;margin:20px 0;color:#ffe18a;font-size:13px}.plans th,.plans td{border:1px solid #f4d06f;padding:11px 8px;text-align:center}.plans th{font-weight:900}.wa{display:block;text-align:center;color:#ffe18a;text-decoration:none;font-size:14px;margin-top:15px}.foot{text-align:center;color:#bc9c86;font-size:11px;margin-top:18px}</style></head>
<body><main class="shell"><section class="hero"><div class="wifi">☊<br><small>WI‑FI ZONE</small></div></section><section class="card"><div class="brand">${esc(brand)}</div><p class="sub">${esc(title)} — ${esc(subtitle)}</p><nav class="tabs"><span>Member</span><b>Voucher</b><span>QR Code</span></nav><div class="status">${'$'}(error)</div><form action="${'$'}(link-login-only)" method="post" autocomplete="on"><input type="hidden" name="dst" value="${'$'}(link-orig)"><label class="field"><input name="username" autocomplete="username" placeholder="Voucher / Username" required><span>🚀</span></label><label class="field"><input type="password" name="password" autocomplete="current-password" placeholder="Password" required><span>🔑</span></label><button class="primary" type="submit">${esc(button)}</button></form><table class="plans"><thead><tr><th>Heures</th><th>Tarifs</th><th>Validité</th></tr></thead><tbody><tr><td>8 Heures</td><td>1000 Fcfa</td><td>1 Jour</td></tr><tr><td>24 Heures</td><td>2000 Fcfa</td><td>2 Jours</td></tr><tr><td>1 SEMAINE</td><td>5000 Fcfa</td><td>7 Jours</td></tr><tr><td>1 MOIS</td><td>20000 Fcfa</td><td>30 Jours</td></tr></tbody></table><a class="wa" href="https://wa.me/218930090401">واتساب الدعم: +218 93 009 0401</a><div class="foot">Powered by ${esc(brand)}</div></section></main><script>(function(){try{var q=new URLSearchParams(location.search),u=q.get('username'),p=q.get('password'),f=document.forms[0];if(u)f.username.value=u;if(p)f.password.value=p;if(u&&history.replaceState)history.replaceState(null,'',location.pathname);if(q.get('auto')==='1'&&u&&f.password.value)f.submit();}catch(e){}})();</script></body></html>"""
private fun generateStatusHtml(brand: String, primary: String, bg: String) = """<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(brand)} Status</title><style>:root{--p:${esc(primary)};--bg:${esc(bg)}}body{margin:0;min-height:100vh;display:grid;place-items:center;font-family:Arial,sans-serif;background:linear-gradient(145deg,var(--bg),#0f172a);color:#f8fafc;padding:20px}.card{width:min(500px,100%);padding:28px;border:1px solid #ffffff22;border-radius:28px;background:#ffffff0d}.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.item{padding:15px;border-radius:15px;background:#0004}.k{color:#94a3b8;font-size:12px}.v{font-weight:800;margin-top:5px}a{color:var(--p)}</style></head><body><main class="card"><h1>${esc(brand)}</h1><p>حالة الاتصال</p><div class="grid"><div class="item"><div class="k">المستخدم</div><div class="v">$(username)</div></div><div class="item"><div class="k">IP</div><div class="v">$(ip)</div></div><div class="item"><div class="k">التحميل</div><div class="v">$(bytes-out-nice)</div></div><div class="item"><div class="k">الرفع</div><div class="v">$(bytes-in-nice)</div></div></div><p>مدة الجلسة: $(uptime)</p><p>المتبقي: $(session-time-left)</p><a href="$(link-logout)">تسجيل الخروج</a></main></body></html>"""
