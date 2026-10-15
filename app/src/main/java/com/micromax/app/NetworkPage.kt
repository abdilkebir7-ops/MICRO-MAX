package com.micromax.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val netFlagKeys = listOf("disabled", "dynamic", "running", "invalid", "inactive", "bound")

/** أدوات الشبكة: تبويبات + بحث + عدّاد + تحديث + عرض مرتّب (بدل JSON الخام). */
@Composable
fun NetworkPage(api: Api, router: JSONObject?) {
    var tab by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf(listOf<JSONObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    val names = listOf("Interfaces", "DHCP", "ARP", "DNS", "IP", "Routes", "Firewall", "Queues", "Logs", "IP Bindings")
    val paths = listOf("interfaces", "dhcp-leases", "arp", "dns-static", "ip-addresses", "routes", "firewall", "queues", "logs", "ip-bindings")

    LaunchedEffect(router?.optString("id"), tab, reload) {
        if (router == null) return@LaunchedEffect
        loading = true
        try {
            val o = JSONObject(api.get("/api/routers/${router.optString("id")}/network/${paths[tab]}"))
            val a = o.optJSONArray("items") ?: JSONArray()
            rows = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            err = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            err = e.message ?: "تعذر التحميل"; rows = emptyList()
        } finally { loading = false }
    }

    val shown = remember(rows, query) { if (query.isBlank()) rows else rows.filter { it.toString().contains(query.trim(), ignoreCase = true) } }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("أدوات الشبكة", fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text(router?.optString("name") ?: "اختر راوتر", color = TextMuted)
            }
            IconButton({ reload++ }, enabled = router != null && !loading) { Icon(MmIcons.Sync, "تحديث", tint = Accent) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEachIndexed { i, n -> FilterChip(tab == i, { tab = i; query = "" }, { Text(n, fontSize = 11.sp) }) }
        }
        OutlinedTextField(query, { query = it }, label = { Text("بحث في ${names[tab]}") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (router != null && err == null) Text("${shown.size} من ${rows.size}", fontSize = 11.sp, color = TextMuted)
        err?.let { ErrorCard(it) }
        if (router == null) EmptyCard("اختر راوتر أولاً")
        else if (!loading && err == null && shown.isEmpty()) EmptyCard(if (rows.isEmpty()) "لا توجد عناصر" else "لا نتائج مطابقة للبحث")
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(shown) { o ->
                val title = o.optString("name", o.optString("address", o.optString("dst-address", o.optString("message", o.optString("comment", "-")))))
                val flags = netFlagKeys.filter { o.optString(it) == "true" }
                val details = o.keys().asSequence().filter { it != ".id" && it != "name" && o.optString(it).isNotBlank() && it !in netFlagKeys }
                    .map { "$it: ${o.optString(it)}" }.take(8).toList()
                GlassCard {
                    Text(title, fontWeight = FontWeight.Bold, maxLines = 2)
                    if (flags.isNotEmpty()) Text(flags.joinToString(" • "), fontSize = 11.sp, color = Accent)
                    details.forEach { Text(it.take(120), fontSize = 11.sp, color = TextMuted) }
                }
            }
        }
    }
}
