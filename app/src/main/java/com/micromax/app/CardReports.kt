package com.micromax.app

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private data class CardReportRow(
    val username: String,
    val router: String,
    val plan: String,
    val status: String,
    val price: String,
    val created: String,
    val batch: String
)

@Composable
fun CardReportsPage(api: Api, routers: JSONArray, selectedRouter: JSONObject?) {
    val context = LocalContext.current
    var cards by remember { mutableStateOf(emptyList<CardReportRow>()) }
    var batches by remember { mutableStateOf(emptyList<JSONObject>()) }
    var statusFilter by remember { mutableStateOf("all") }
    var routerFilter by remember(selectedRouter?.optString("id")) { mutableStateOf(selectedRouter?.optString("id") ?: "all") }
    var tab by remember { mutableStateOf("summary") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            try {
                val cardArray = JSONObject(api.get("/api/cards")).optJSONArray("cards") ?: JSONArray()
                cards = (0 until cardArray.length()).mapNotNull { index ->
                    val item = cardArray.optJSONObject(index) ?: return@mapNotNull null
                    CardReportRow(
                        username = item.optString("username", "-"),
                        router = item.optString("router_name", "-"),
                        plan = item.optString("plan_name", item.optString("profile", "-")),
                        status = item.optString("status", "available").lowercase(),
                        price = "${item.optDouble("price", 0.0)} ${item.optString("price_currency", "XOF")}",
                        created = item.optString("created_at", "-").take(10),
                        batch = item.optString("batch_id", "-")
                    )
                }
                val batchArray = JSONObject(api.get("/api/card-batches")).optJSONArray("batches") ?: JSONArray()
                batches = (0 until batchArray.length()).mapNotNull { batchArray.optJSONObject(it) }
                error = null
            } catch (e: Exception) { error = e.message ?: "تعذر تحميل تقارير الكروت" } finally { loading = false }
        }
    }

    fun export(extension: String, mime: String) {
        scope.launch {
            exporting = extension
            try {
                val bytes = api.download("/api/reports/cards.$extension${reportQuery(routerFilter, statusFilter)}")
                val filename = "micromax-card-report-${System.currentTimeMillis()}.$extension"
                saveReportToDownloads(context, bytes, filename, mime)
                Toast.makeText(context, "تم حفظ التقرير في مجلد Downloads", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "فشل التصدير: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
            } finally { exporting = null }
        }
    }

    LaunchedEffect(routerFilter) { load() }
    val visible = cards.filter { row ->
        (statusFilter == "all" || normalizedCardStatus(row.status) == statusFilter) &&
            (routerFilter == "all" || routersContainsName(routers, routerFilter, row.router))
    }
    val count = { state: String -> cards.count { normalizedCardStatus(it.status) == state } }

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("تقارير الكروت", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("مراقبة الكروت المستخدمة وغير المستخدمة والمخزون والدفعات", color = TextMuted, fontSize = 12.sp)
            }
            Button(onClick = { load() }, enabled = !loading && exporting == null) {
                Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(5.dp)); Text(if (loading) "جاري…" else "تحديث")
            }
        }
        error?.let { ErrorCard(it) }
        ExportActions(exporting = exporting, onExcel = { export("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }, onPdf = { export("pdf", "application/pdf") }, onCsv = { export("csv", "text/csv") })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(tab == "summary", { tab = "summary" }, label = { Text("الملخص") }, leadingIcon = { Icon(Icons.Outlined.Assessment, null) })
            FilterChip(tab == "cards", { tab = "cards" }, label = { Text("تفاصيل الكروت") })
            FilterChip(tab == "batches", { tab = "batches" }, label = { Text("الدفعات") })
        }
        Text("الراوتر", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(routerFilter == "all", { routerFilter = "all" }, label = { Text("كل الراوترات") })
            for (index in 0 until routers.length()) {
                val router = routers.optJSONObject(index) ?: continue
                val id = router.optString("id")
                FilterChip(routerFilter == id, { routerFilter = id }, label = { Text(router.optString("name", router.optString("host", "راوتر"))) })
            }
        }
        when (tab) {
            "summary" -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ReportMetric("كل الكروت", cards.size.toString(), Color(0xFF2563EB), Modifier.weight(1f)); ReportMetric("غير مستخدم", count("available").toString(), Color(0xFF16A34A), Modifier.weight(1f)) } }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ReportMetric("مستخدم / مباع", (count("used") + count("sold")).toString(), Color(0xFFF59E0B), Modifier.weight(1f)); ReportMetric("منتهي / معطل", (count("expired") + count("disabled")).toString(), Color(0xFFDC2626), Modifier.weight(1f)) } }
                item { StatusFilters(statusFilter) { statusFilter = it } }
                item { GlassCard { Text("قراءة سريعة", fontWeight = FontWeight.Bold); Text("المتاح للبيع: ${count("available")} • المستخدم: ${count("used")} • المباع: ${count("sold")}\nالدفعات المسجلة: ${batches.size}", color = TextMuted, fontSize = 13.sp) } }
                item { Text("آخر الكروت", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                items(visible.take(12), key = { it.username }) { CardReportItem(it) }
            }
            "cards" -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { StatusFilters(statusFilter) { statusFilter = it } }
                items(visible, key = { it.username }) { CardReportItem(it) }
                if (visible.isEmpty()) item { EmptyCard("لا توجد كروت مطابقة للفلاتر الحالية") }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Text("دفعات التوليد", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                items(batches, key = { it.optString("batch_id") }) { batch -> BatchReportItem(batch) }
                if (batches.isEmpty()) item { EmptyCard("لا توجد دفعات مسجلة بعد") }
            }
        }
    }
}

@Composable
private fun ExportActions(exporting: String?, onExcel: () -> Unit, onPdf: () -> Unit, onCsv: () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("تصدير التقرير", fontWeight = FontWeight.Bold); Text("يتم حفظ الملف تلقائيًا داخل Downloads", color = TextMuted, fontSize = 11.sp) }
            Icon(Icons.Outlined.Description, null, tint = Accent)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(onClick = onExcel, enabled = exporting == null, modifier = Modifier.weight(1f)) { Text(if (exporting == "xlsx") "جاري…" else "Excel") }
            Button(onClick = onPdf, enabled = exporting == null, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.width(4.dp)); Text(if (exporting == "pdf") "جاري…" else "PDF") }
            OutlinedButton(onClick = onCsv, enabled = exporting == null, modifier = Modifier.weight(1f)) { Text(if (exporting == "csv") "جاري…" else "CSV") }
        }
    }
}

private suspend fun saveReportToDownloads(context: Context, bytes: ByteArray, filename: String, mime: String) {
    if (bytes.isEmpty()) throw IllegalStateException("الملف الذي أعاده Backend فارغ")
    val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, filename); put(MediaStore.Downloads.MIME_TYPE, mime); put(MediaStore.Downloads.IS_PENDING, 1) }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("تعذر إنشاء ملف داخل Downloads")
    try {
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IllegalStateException("تعذر فتح ملف التنزيل")
        values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); resolver.update(uri, values, null, null)
    } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
}

private fun reportQuery(routerId: String, status: String): String {
    val values = mutableListOf<String>()
    if (routerId != "all") values += "routerId=${encodeQuery(routerId)}"
    if (status != "all") values += "status=${encodeQuery(status)}"
    return if (values.isEmpty()) "" else "?${values.joinToString("&")}" 
}

private fun encodeQuery(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

@Composable
private fun ReportMetric(label: String, value: String, color: Color, modifier: Modifier) { Card(modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .12f))) { Column(Modifier.padding(14.dp)) { Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = color); Text(label, color = TextMuted, fontSize = 11.sp) } } }

@Composable
private fun StatusFilters(current: String, onChange: (String) -> Unit) { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("all" to "الكل", "available" to "غير مستخدم", "used" to "مستخدم", "sold" to "مباع", "expired" to "منتهي", "disabled" to "معطل").forEach { (key, label) -> FilterChip(current == key, { onChange(key) }, label = { Text(label) }) } } }

@Composable
private fun CardReportItem(row: CardReportRow) { GlassCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(row.username, fontWeight = FontWeight.Bold); Text("${row.plan} • ${row.router}", color = TextMuted, fontSize = 12.sp); Text("${row.price} • ${row.created}", color = TextMuted, fontSize = 11.sp) }; StatusBadge(row.status) } } }

@Composable
private fun BatchReportItem(batch: JSONObject) { GlassCard { Text(batch.optString("plan_name", "دفعة كروت"), fontWeight = FontWeight.Bold); Text("${batch.optInt("total")} كرت • ${batch.optString("router_name", "-")}", color = TextMuted, fontSize = 12.sp); Text("متاح ${batch.optInt("available")} • مباع ${batch.optInt("sold")} • مستخدم ${batch.optInt("used")} • منتهي ${batch.optInt("expired")} • معطل ${batch.optInt("disabled")}", color = Accent, fontSize = 12.sp); Text("Batch: ${batch.optString("batch_id", "-")}", color = TextMuted, fontSize = 10.sp) } }

@Composable
private fun StatusBadge(status: String) { val normalized = normalizedCardStatus(status); val (label, color) = when (normalized) { "available" -> "غير مستخدم" to Color(0xFF16A34A); "used" -> "مستخدم" to Color(0xFFF59E0B); "sold" -> "مباع" to Color(0xFF7C3AED); "expired" -> "منتهي" to Color(0xFFDC2626); "disabled" -> "معطل" to Color(0xFF64748B); else -> normalized to TextMuted }; Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }

private fun normalizedCardStatus(value: String): String = when (value.lowercase()) { "available", "unused" -> "available"; "active", "used" -> "used"; "sold" -> "sold"; "expired" -> "expired"; "disabled" -> "disabled"; else -> value.lowercase() }

private fun routersContainsName(routers: JSONArray, id: String, name: String): Boolean { for (index in 0 until routers.length()) { val router = routers.optJSONObject(index) ?: continue; if (router.optString("id") == id) return router.optString("name", router.optString("host")) == name }; return false }
