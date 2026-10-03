package com.micromax.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class DesignTemplate(val name: String, val group: String, val description: String, val tint: Color, val dark: Boolean)
private val builtIns = listOf(
    DesignTemplate("Modern Wave", "عصري", "موجة زرقاء للشبكات الحديثة", Color(0xFF3159F4), true),
    DesignTemplate("Midnight Glass", "عصري", "كحلي هادئ وتباين مريح", Color(0xFF22B9B3), true),
    DesignTemplate("VIP Neon", "عصري", "هوية قوية للباقة المميزة", Color(0xFFC75CDA), true),
    DesignTemplate("Classic Gold", "كلاسيكي", "ألوان ضيافة راقية", Color(0xFFC59041), true),
    DesignTemplate("Minimal Mono", "بسيط", "الأفضل للطابعات أحادية اللون", Color(0xFF222B39), false),
    DesignTemplate("Clean White", "بسيط", "متوازن للطباعة التجارية", Color(0xFF3165BC), false),
    DesignTemplate("Coffee House", "منشآت", "أسلوب دافئ للمقاهي", Color(0xFFA86A42), true),
    DesignTemplate("Hotel Luxe", "منشآت", "هوية فندقية مطمئنة", Color(0xFF2E8D86), true),
    DesignTemplate("School Clean", "منشآت", "واضح للمؤسسات التعليمية", Color(0xFF2F60B2), false),
    DesignTemplate("Market Orange", "منشآت", "عروض المتاجر والمراكز", Color(0xFFF0783E), true),
    DesignTemplate("Travel WiFi", "منشآت", "للفنادق والسفر", Color(0xFF7658CF), true),
    DesignTemplate("Gaming Arena", "منشآت", "لصالات الألعاب", Color(0xFFCD4A71), true)
)

@Composable
fun StorePage(api: Api, routers: JSONArray, onAddToRouter: (String, JSONObject) -> Unit) {
    var filter by remember { mutableStateOf("الكل") }
    var serverItems by remember { mutableStateOf(JSONArray()) }
    var selected by remember { mutableStateOf<DesignTemplate?>(null) }
    var showRouterPicker by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { serverItems = JSONObject(api.get("/api/store")).optJSONArray("items") ?: JSONArray() } catch (_: Exception) {} }
    val shown = if (filter == "الكل") builtIns else builtIns.filter { it.group == filter }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 17.dp), verticalArrangement = Arrangement.spacedBy(15.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp)) {
        item {
            PageHeading("مكتبة مستقلة عن الراوتر", "المتجر والتصاميم", "تصفح القوالب بحرية أولاً، ثم أضف التصميم المختار إلى أي راوتر عند الحاجة")
            Spacer(Modifier.height(12.dp))
            Surface(color = Royal.copy(alpha = .08f), shape = RoundedCornerShape(18.dp)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Storefront, null, tint = Royal); Spacer(Modifier.width(9.dp)); Column { Text("المتجر لا يحتاج راوترًا محددًا", fontWeight = FontWeight.Bold, color = Ink); Text("زر إضافة إلى راوتر يظهر بعد اختيار التصميم.", fontSize = 11.sp, color = MutedInk) } } }
        }
        item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("الكل", "عصري", "كلاسيكي", "بسيط", "منشآت").forEach { category -> FilterChip(category == filter, { filter = category }, label = { Text(category) }) } } }
        item { SectionHeading("القوالب الجاهزة", "${shown.size} تصميم متاح للتخصيص") }
        items(shown.chunked(2)) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) { row.forEach { t -> TemplateCard(t, Modifier.weight(1f)) { selected = t; showRouterPicker = true } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } }
        if (serverItems.length() > 0) {
            item { SectionHeading("إضافات الخادم", "منتجات المنشأة النشطة") }
            items((0 until serverItems.length()).map { serverItems.getJSONObject(it) }) { o -> GlassCard { Text(o.optString("name"), fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(o.optString("description"), color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick = { scope.launch { try { message = JSONObject(api.post("/api/store/${o.optString("id")}/activate")).optString("message", "تم التفعيل") } catch (e: Exception) { message = e.message } } }) { Text("تفعيل الإضافة") } } }
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) } }
    }

    if (showRouterPicker && selected != null) AlertDialog(onDismissRequest = { showRouterPicker = false }, title = { Text("إضافة التصميم إلى راوتر") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("التصميم المختار: ${selected!!.name}", fontWeight = FontWeight.Bold)
            if (routers.length() == 0) Text("لا يوجد راوتر حتى الآن. يمكنك حفظ التصميم في الاستوديو ثم إضافة الراوتر لاحقًا.", color = MutedInk)
            else for (i in 0 until routers.length()) { val r = routers.getJSONObject(i); OutlinedButton(onClick = { onAddToRouter(selected!!.name, r); showRouterPicker = false }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Router, null); Spacer(Modifier.width(8.dp)); Column(horizontalAlignment = Alignment.Start) { Text(r.optString("name", "Router"), fontWeight = FontWeight.Bold); Text("${r.optString("host", "-")} • ${r.optInt("port", 8728)}", fontSize = 11.sp, color = MutedInk) } } }
            OutlinedButton(onClick = { onAddToRouter(selected!!.name, JSONObject()); showRouterPicker = false }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Palette, null); Spacer(Modifier.width(8.dp)); Text("فتح الاستوديو بدون راوتر الآن") }
        }
    }, confirmButton = { TextButton({ showRouterPicker = false }) { Text("إلغاء") } })
}

@Composable private fun TemplateCard(t: DesignTemplate, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(21.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.fillMaxWidth().height(113.dp).background(if (t.dark) Brush.linearGradient(listOf(Ink, t.tint.copy(alpha = .83f))) else Brush.linearGradient(listOf(Color.White, t.tint.copy(alpha = .14f))), RoundedCornerShape(13.dp))) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(16.dp).background(t.tint, RoundedCornerShape(5.dp))); Spacer(Modifier.weight(1f)); Text("Wi-Fi", color = if (t.dark) Color.White else Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Row(verticalAlignment = Alignment.Bottom) { Column(Modifier.weight(1f)) { Text("ACCESS", fontSize = 14.sp, color = if (t.dark) Color.White else Ink, fontWeight = FontWeight.Bold); Text("•••• ••••", color = if (t.dark) Color.White.copy(alpha = .7f) else MutedInk, fontSize = 10.sp) }; Box(Modifier.size(32.dp).background(Color.White, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.QrCode2, "مكان QR", tint = Ink, modifier = Modifier.size(25.dp)) } } }
            }
            Text(t.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 2, minLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("إضافة إلى راوتر", color = t.tint, fontSize = 10.sp, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.AddCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
        }
    }
}
