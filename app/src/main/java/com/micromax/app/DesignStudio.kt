package com.micromax.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

private val StudioOrange = Color(0xFFFF9B5A)
private val StudioAccent = Color(0xFF22D3EE)
private val StudioMuted = Color(0xFF94A3B8)
private val StudioRed = Color(0xFFF43F5E)
private val StudioMint = Color(0xFF82E3BF)

@Composable
fun DesignStudioPage(api: Api, router: JSONObject?, activity: MainActivity) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("استوديو التصميم والطباعة", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("القوالب • المعاينة • التوليد • الطباعة التجارية", color = StudioMuted, fontSize = 13.sp)
            }
            Icon(Icons.Default.AutoAwesome, null, tint = StudioAccent, modifier = Modifier.size(30.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("تصميم الكرت", "صفحات HotSpot", "الطباعة").forEachIndexed { i, label ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) }, leadingIcon = {
                    Icon(if (i == 0) Icons.Default.CreditCard else if (i == 1) Icons.Default.Web else Icons.Default.Print, null)
                })
            }
        }
        when (tab) {
            0 -> CardDesignStudio(api, router)
            1 -> HotspotPageStudio(api, router)
            else -> PrinterStudio(activity)
        }
    }
}

@Composable
private fun CardDesignStudio(api: Api, router: JSONObject?) {
    var template by remember { mutableStateOf("Midnight Glass") }
    var title by remember { mutableStateOf("WiFi Access") }
    var subtitle by remember { mutableStateOf("اتصال سريع وآمن") }
    var support by remember { mutableStateOf("support@micromax.app") }
    var primary by remember { mutableStateOf("#22D3EE") }
    var price by remember { mutableStateOf("500 XOF") }
    var showPrice by remember { mutableStateOf(true) }
    var showQr by remember { mutableStateOf(true) }
    var showBarcode by remember { mutableStateOf(true) }
    var monochrome by remember { mutableStateOf(true) }
    var showBrand by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val routerId = router?.optString("id")
    val templates = listOf("Modern Wave", "Midnight Glass", "VIP Neon", "Classic Gold", "Minimal Mono", "Clean White", "Coffee House", "Hotel Luxe", "School Clean", "Market Orange")
    val templateColors = mapOf("Modern Wave" to "#2563EB", "Midnight Glass" to "#22D3EE", "VIP Neon" to "#E879F9", "Classic Gold" to "#D4A84F", "Minimal Mono" to "#111827", "Clean White" to "#2563EB", "Coffee House" to "#B77945", "Hotel Luxe" to "#66D9C0", "School Clean" to "#1D4ED8", "Market Orange" to "#FF9B5A")
    val templateDescriptions = mapOf("Modern Wave" to "عصري • تدرج أزرق", "Midnight Glass" to "داكن • زجاجي", "VIP Neon" to "جريء • نيون", "Classic Gold" to "كلاسيكي • فاخر", "Minimal Mono" to "بسيط • أبيض وأسود", "Clean White" to "نظيف • تجاري", "Coffee House" to "مقهى • دافئ", "Hotel Luxe" to "فندق • راقٍ", "School Clean" to "تعليمي • واضح", "Market Orange" to "متجر • حيوي")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("مكتبة القوالب", fontWeight = FontWeight.Black, fontSize = 20.sp); Text("اختر نقطة بداية احترافية ثم خصص كل تفصيل", color = StudioMuted, fontSize = 12.sp) }; Icon(Icons.Default.AutoAwesome, null, tint = StudioOrange) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { name ->
                    val swatch = parseStudioColor(templateColors[name] ?: primary, StudioAccent)
                    Card(onClick = { template = name; primary = templateColors[name] ?: primary }, modifier = Modifier.width(142.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (template == name) swatch.copy(alpha = .22f) else Color.White.copy(alpha = .06f))) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Box(Modifier.fillMaxWidth().height(42.dp).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(swatch, templatePreviewBackground(name))), RoundedCornerShape(10.dp))); Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(templateDescriptions[name] ?: "قالب جاهز", fontSize = 9.sp, color = if (template == name) swatch else StudioMuted, maxLines = 1); Text(if (template == name) "محدد الآن" else "اختيار القالب", fontSize = 9.sp, color = if (template == name) swatch else StudioMuted) }
                    }
                }
            }
        }
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("لوحة المعاينة", fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f)); Text("LIVE", color = StudioMint, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            DesignedCardPreview(template, title, subtitle, support, price, primary, showPrice, showQr, showBarcode, monochrome, showBrand)
            Text(if (monochrome) "وضع الطباعة: أبيض وأسود — ترتيب محسّن للطابعات المكتبية والحرارية." else "المعاينة الملونة — فعّل وضع أبيض وأسود قبل الطباعة التجارية.", color = StudioMuted, fontSize = 11.sp)
        }
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("محرر المحتوى", fontWeight = FontWeight.Black, fontSize = 19.sp); Text("النصوص والهوية البصرية", color = StudioMuted, fontSize = 11.sp) }; IconButton({ advanced = !advanced }) { Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = StudioAccent) } }
            AppField(title, { title = it }, "العنوان الرئيسي", Icons.Default.Title)
            AppField(subtitle, { subtitle = it }, "الوصف العربي", Icons.Default.Translate)
            AppField(support, { support = it }, "معلومات الدعم أو المتجر", Icons.Default.SupportAgent)
            AppField(price, { price = it }, "السعر الظاهر على الكرت", Icons.Default.Payments)
            if (advanced) { AppField(primary, { primary = it }, "اللون الرئيسي HEX", Icons.Default.Palette); Text("خيارات الهوية والطباعة", fontWeight = FontWeight.Bold, color = StudioOrange); Row(verticalAlignment = Alignment.CenterVertically) { Switch(showBrand, { showBrand = it }); Text("إظهار اسم النظام على الكرت", Modifier.weight(1f)) }; Text("مغلق افتراضيًا حتى يكون الكرت محايدًا وقابلًا لإعادة البيع.", fontSize = 10.sp, color = StudioMuted); Row(verticalAlignment = Alignment.CenterVertically) { Switch(monochrome, { monochrome = it }); Text("وضع أبيض وأسود للطباعة", Modifier.weight(1f)) }; Row(verticalAlignment = Alignment.CenterVertically) { Switch(showPrice, { showPrice = it }); Text("إظهار السعر", Modifier.weight(1f)) }; Row(verticalAlignment = Alignment.CenterVertically) { Switch(showQr, { showQr = it }); Text("إظهار QR Code", Modifier.weight(1f)) }; Row(verticalAlignment = Alignment.CenterVertically) { Switch(showBarcode, { showBarcode = it }); Text("إظهار Barcode", Modifier.weight(1f)) } }
            message?.let { Text(it, color = if (it.startsWith("تم")) StudioMint else StudioRed, fontSize = 12.sp) }
            Button(onClick = { if (routerId == null) { message = "اختر راوتر أولاً لحفظ هوية HotSpot"; return@Button }; scope.launch { saving = true; try { api.post("/api/themes/$routerId", JSONObject().put("name", template).put("primaryColor", primary).put("background", "#FFFFFF").put("logoUrl", if (showBrand) "MICRO-MAX" else "").put("title", title).put("subtitle", subtitle).put("price", price).put("showPrice", showPrice).put("showQr", showQr).put("showBarcode", showBarcode).put("showBrand", showBrand).toString()); message = "تم حفظ تصميم $template للراوتر" } catch (e: Exception) { message = e.message } finally { saving = false } } }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text(if (saving) "جاري الحفظ…" else "حفظ الهوية والتصميم") }
        }
    }
}

@Composable
private fun DesignedCardPreview(template: String, title: String, subtitle: String, support: String, price: String, primary: String, showPrice: Boolean, showQr: Boolean, showBarcode: Boolean, monochrome: Boolean, showBrand: Boolean) {
    val color = if (monochrome) Color.Black else parseStudioColor(primary, StudioAccent)
    val bg = if (monochrome) Color.White else templatePreviewBackground(template)
    val lightTemplate = template == "Clean White" || template == "Minimal Mono" || template == "School Clean"
    val fg = if (monochrome || lightTemplate) Color(0xFF0F172A) else Color.White
    Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(260.dp).background(bg, RoundedCornerShape(24.dp)).padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { if (showBrand) Text("MICRO-MAX", color = fg, fontWeight = FontWeight.Black, fontSize = 14.sp); Spacer(Modifier.weight(1f)); if (showPrice) Text(price, color = color, fontWeight = FontWeight.Black, fontSize = 13.sp) }
                HorizontalDivider(color = color.copy(alpha = .35f))
                Text(title, color = fg, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = color, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("بيانات الدخول", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text("اسم المستخدم  KMX-482091", color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text("كلمة المرور  ••••••", color = fg, fontSize = 11.sp); if (showBarcode) Text("▌▌▌ ▌▌ ▌▌▌", color = color, fontSize = 14.sp, letterSpacing = 2.sp) }
                    if (showQr) { val qr = remember { generateCode("https://wifi.micromax.app/login?u=KMX-482091", com.google.zxing.BarcodeFormat.QR_CODE, 130, 130) }; qr?.let { Image(it.asImageBitmap(), "QR", Modifier.size(78.dp)) } }
                }
                HorizontalDivider(color = color.copy(alpha = .25f))
                Text(support, color = if (monochrome) Color(0xFF475569) else if (template == "Clean White") Color(0xFF64748B) else StudioMuted, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun HotspotPageStudio(api: Api, router: JSONObject?) {
    var page by remember { mutableStateOf("login") }
    var content by remember { mutableStateOf(defaultHotspotHtml("login")) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val id = router?.optString("id")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (router == null) EmptyCard("اختر راوتر أولاً لتصميم صفحات HotSpot") else {
            GlassCard {
                Text("واجهة الزائر", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("صمم login.html و status.html مع معاينة قبل النشر. النشر يحتاج دور Admin ويُنشئ نسخة احتياطية.", color = StudioMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = page == "login", onClick = { page = "login"; content = defaultHotspotHtml("login"); status = null }, label = { Text("login.html") }, leadingIcon = { Icon(Icons.Default.Login, null) })
                    FilterChip(selected = page == "status", onClick = { page = "status"; content = defaultHotspotHtml("status"); status = null }, label = { Text("status.html") }, leadingIcon = { Icon(Icons.Default.Dashboard, null) })
                }
                AppField(content, { content = it }, "HTML المصدر", Icons.Default.Code)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            try { val raw = api.post("/api/routers/$id/hotspot-pages/validate", JSONObject().put("page", page).put("content", content).toString()); status = if (JSONObject(raw).optBoolean("valid")) "تم التحقق: الصفحة متوافقة" else "تحقق غير مكتمل: $raw" }
                            catch (e: Exception) { status = e.message } finally { busy = false }
                        }
                    }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("تحقق") }
                    Button(onClick = {
                        scope.launch {
                            busy = true
                            try { api.post("/api/routers/$id/hotspot-pages/publish", JSONObject().put("page", page).put("content", content).toString()); status = "تم نشر $page.html مع Backup تلقائي" }
                            catch (e: Exception) { status = e.message } finally { busy = false }
                        }
                    }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("نشر آمن") }
                }
                status?.let { Text(it, color = if (it.startsWith("تم")) StudioMint else StudioRed, fontSize = 12.sp) }
            }
            GlassCard {
                Text("المعاينة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                HotspotPreview(page, content)
            }
        }
    }
}

@Composable
private fun HotspotPreview(page: String, content: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MICRO-MAX", color = Color(0xFF0B5FA5), fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text(if (page == "login") "اتصل بالإنترنت" else "حالة الاتصال", color = Color(0xFF1F2937), fontWeight = FontWeight.Bold)
            if (page == "login") { Text("أدخل بيانات الكرت", color = Color.Gray, fontSize = 12.sp); OutlinedButton(onClick = {}) { Text("تسجيل الدخول") } }
            else { Text("KMX-482091 • متصل", color = Color(0xFF14804A)); Text("الوقت المتبقي: 58 دقيقة", color = Color.Gray, fontSize = 12.sp) }
            Text("مصدر ${content.length} حرف • ${if (page == "login") "MikroTik login.html" else "MikroTik status.html"}", color = Color.Gray, fontSize = 9.sp)
        }
    }
}

@Composable
private fun PrinterStudio(activity: MainActivity) {
    val prefs = remember { activity.getSharedPreferences("micromax_printer", android.content.Context.MODE_PRIVATE) }
    var paper by remember { mutableStateOf(prefs.getString("paper", "A4") ?: "A4") }
    var columns by remember { mutableStateOf(prefs.getInt("columns", 2).toString()) }
    var rows by remember { mutableStateOf(prefs.getInt("rows", 4).toString()) }
    var copies by remember { mutableStateOf(prefs.getInt("copies", 1).toString()) }
    var saved by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Text("مركز الطباعة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("تحكم في مقاس الورق وعدد الكروت في الورقة. التصميم الافتراضي أبيض وأسود ومناسب لمعظم طابعات A4 والحرارية.", color = StudioMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("A4", "80mm Thermal", "Custom").forEach { FilterChip(selected = paper == it, onClick = { paper = it }, label = { Text(it) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AppField(columns, { columns = it.filter(Char::isDigit) }, "أعمدة", Icons.Default.ViewColumn); AppField(rows, { rows = it.filter(Char::isDigit) }, "صفوف", Icons.Default.GridView) }
            AppField(copies, { copies = it.filter(Char::isDigit) }, "عدد النسخ", Icons.Default.ContentCopy)
            Button(onClick = { prefs.edit().putString("paper", paper).putInt("columns", columns.toIntOrNull() ?: 2).putInt("rows", rows.toIntOrNull() ?: 4).putInt("copies", copies.toIntOrNull() ?: 1).apply(); saved = true }, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "تم حفظ إعدادات الطابعة" else "حفظ إعدادات الطباعة") }
        }
        GlassCard {
            Text("معاينة ورقة الطباعة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            PrintSheetPreview(columns.toIntOrNull() ?: 2, rows.toIntOrNull() ?: 4)
            Text("MICRO-MAX يظهر مرة واحدة في أعلى كل كرت • استخدم Actual size / 100% في نافذة الطباعة.", color = StudioMuted, fontSize = 11.sp)
            Button(onClick = { activity.printBitmap(composePrintSheet(columns.toIntOrNull() ?: 2, rows.toIntOrNull() ?: 4)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text("فتح نافذة الطباعة") }
        }
    }
}

@Composable
private fun PrintSheetPreview(columns: Int, rows: Int) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFEFF3F5), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(rows.coerceIn(1, 6)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(columns.coerceIn(1, 4)) { PrintCell(Modifier.weight(1f)) } } }
    }
}

@Composable
private fun PrintCell(modifier: Modifier) {
    Card(modifier.height(74.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(7.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black)) {
        Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("WiFi Access", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = Color.Black)
            Text("اسم المستخدم  KMX-482091", color = Color.Black, fontSize = 7.sp)
            Text("QR + Barcode", color = Color.Black, fontSize = 6.sp)
        }
    }
}

private fun composePrintSheet(columns: Int, rows: Int): Bitmap {
    val w = 1200
    val h = 1700
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    val cols = columns.coerceIn(1, 4); val rws = rows.coerceIn(1, 6)
    val gap = 22; val cardW = (w - gap * (cols + 1)) / cols; val cardH = (h - gap * (rws + 1)) / rws
    repeat(rws) { r -> repeat(cols) { c -> drawStudioCard(canvas, gap + c * (cardW + gap), gap + r * (cardH + gap), cardW, cardH) } }
    return out
}

private fun drawStudioCard(canvas: Canvas, x: Int, y: Int, w: Int, h: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.FILL; p.color = android.graphics.Color.WHITE; canvas.drawRoundRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), 22f, 22f, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = android.graphics.Color.BLACK; canvas.drawRoundRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), 22f, 22f, p); p.style = Paint.Style.FILL
    p.textSize = 22f; p.typeface = Typeface.DEFAULT_BOLD; canvas.drawText("WiFi Access", (x + 24).toFloat(), (y + 70).toFloat(), p)
    p.strokeWidth = 2f; canvas.drawRect((x + 24).toFloat(), (y + 86).toFloat(), (x + w - 24).toFloat(), (y + 88).toFloat(), p)
    p.textSize = 18f; canvas.drawText("اسم المستخدم  KMX-482091", (x + 24).toFloat(), (y + h - 72).toFloat(), p)
    p.textSize = 15f; canvas.drawText("كلمة المرور  ••••••", (x + 24).toFloat(), (y + h - 44).toFloat(), p)
    generateCode("https://wifi.micromax.app/login?u=KMX-482091", com.google.zxing.BarcodeFormat.QR_CODE, 260, 260)?.let { canvas.drawBitmap(it, null, android.graphics.Rect(x + w - 150, y + h - 170, x + w - 30, y + h - 50), p) }
}

private fun parseStudioColor(value: String, fallback: Color): Color = try { Color(android.graphics.Color.parseColor(value)) } catch (_: Exception) { fallback }

private fun templatePreviewBackground(template: String): Color = when (template) {
    "Classic Gold" -> Color(0xFF241B10)
    "VIP Neon" -> Color(0xFF24132D)
    "Coffee House" -> Color(0xFF2A1A13)
    "Hotel Luxe" -> Color(0xFF102B2A)
    "Market Orange" -> Color(0xFF3A2114)
    "Modern Wave" -> Color(0xFF102A55)
    "Minimal Mono", "Clean White", "School Clean" -> Color(0xFFF8FAFC)
    else -> Color(0xFF0B1624)
}

private fun defaultHotspotHtml(page: String): String = if (page == "login") """<!doctype html><html><head><meta charset="utf-8"><title>MICRO-MAX Login</title></head><body><form name="login" action="$(link-login-only)" method="post"><input name="username" placeholder="Username"><input name="password" type="password" placeholder="Password"><input type="hidden" name="dst" value="$(link-orig)"><button type="submit">تسجيل الدخول</button></form><p>MICRO-MAX HotSpot</p></body></html>""" else """<!doctype html><html><head><meta charset="utf-8"><title>MICRO-MAX Status</title></head><body><h1>MICRO-MAX</h1><p>مرحباً $(username)</p><p>الوقت المتبقي: $(session-time-left)</p><a href="$(link-logout)">تسجيل الخروج</a></body></html>"""
