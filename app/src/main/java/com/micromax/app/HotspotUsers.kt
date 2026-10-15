package com.micromax.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun HotspotUsersPage(api: Api, router: JSONObject?) {
    val routerId = router?.optString("id")
    val scope = rememberCoroutineScope()
    var users by remember(routerId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var stats by remember(routerId) { mutableStateOf(JSONObject()) }
    var updatedAt by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var autoRefresh by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun fetchLive() {
        val id = routerId ?: return
        loading = true
        try {
            val root = JSONObject(api.get("/api/routers/$id/hotspot/live"))
            val array = root.optJSONArray("users") ?: JSONArray()
            users = (0 until array.length()).map { array.getJSONObject(it) }
            stats = root.optJSONObject("stats") ?: JSONObject()
            updatedAt = root.optString("updatedAt", "")
            error = null
        } catch (e: Exception) {
            error = friendlyHotspotError(e.message ?: "تعذر تحميل مستخدمي HotSpot")
        } finally { loading = false }
    }

    LaunchedEffect(routerId, autoRefresh) {
        if (routerId != null) {
            fetchLive()
            while (autoRefresh) { delay(15000); fetchLive() }
        }
    }

    val visible = users.filter { u ->
        val name = u.optString("name")
        val active = u.optBoolean("online")
        val matches = query.isBlank() || name.contains(query, true) || u.optString("comment").contains(query, true) || u.optString("profile").contains(query, true)
        matches && (filter == "all" || filter == "online" && active || filter == "offline" && !active)
    }.sortedWith(compareByDescending<JSONObject> { it.optBoolean("online") }.thenBy { it.optString("name") })

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeading("HotSpot", "المستخدمون والاستهلاك", "مراقبة الجلسات والتحميل والرفع من الراوتر مباشرة")
        if (router == null) {
            EmptyCard("اختر راوترًا أولًا لعرض مستخدمي HotSpot")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(router.optString("name", "MikroTik"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusPill(if (autoRefresh) "مباشر" else "متوقف")
                IconButton(onClick = { scope.launch { fetchLive() } }, enabled = !loading) { Icon(Icons.Outlined.Refresh, "تحديث") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveMetric("كل المستخدمين", stats.optInt("totalUsers").toString(), MmIcons.Users, DataBlue, Modifier.weight(1f))
                LiveMetric("متصل الآن", stats.optInt("activeUsers").toString(), MmIcons.Wifi, Green, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveMetric("تحميل", formatHotspotBytes(stats.optLong("downloadBytes")), Icons.Outlined.Download, Accent, Modifier.weight(1f))
                LiveMetric("رفع", formatHotspotBytes(stats.optLong("uploadBytes")), Icons.Outlined.Upload, Violet, Modifier.weight(1f))
            }
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) { AppField(query, { query = it }, "بحث بالاسم أو الباقة أو التعليق", Icons.Outlined.Person) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(filter == "all", { filter = "all" }, label = { Text("الكل") })
                    Spacer(Modifier.width(6.dp)); FilterChip(filter == "online", { filter = "online" }, label = { Text("متصل") })
                    Spacer(Modifier.width(6.dp)); FilterChip(filter == "offline", { filter = "offline" }, label = { Text("غير متصل") })
                    Spacer(Modifier.weight(1f)); Switch(autoRefresh, { autoRefresh = it }); Text("تحديث 15ث", fontSize = 11.sp, color = TextMuted)
                }
            }
            if (notice != null) Text(notice!!, color = Green, fontSize = 12.sp)
            error?.let { ErrorCard(it) }
            if (loading && users.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!loading && users.isEmpty() && error == null) EmptyCard("لا يوجد مستخدمو HotSpot على هذا الراوتر")
            visible.forEach { user -> HotspotUserCard(user, api, routerId ?: "", { notice = it; scope.launch { fetchLive() } }) }
            if (visible.isEmpty() && users.isNotEmpty()) EmptyCard("لا توجد نتائج مطابقة للبحث أو الفلتر")
            if (updatedAt.isNotBlank()) Text("آخر تحديث: ${updatedAt.replace("T", " ").take(19)} • البيانات مقروءة من MikroTik", color = TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun LiveMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Box(modifier) { GlassCard { Row(verticalAlignment = Alignment.CenterVertically) { SymbolTile(icon, tint, size = 32.dp); Spacer(Modifier.width(8.dp)); Column { Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp); Text(label, color = TextMuted, fontSize = 10.sp) } } } }
}

@Composable
private fun HotspotUserCard(user: JSONObject, api: Api, routerId: String, onResult: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val online = user.optBoolean("online")
    val active = user.optJSONObject("active")
    val name = user.optString("name", "بدون اسم")
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SymbolTile(if (online) Icons.Outlined.Wifi else Icons.Outlined.PersonOff, if (online) Green else TextMuted, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.width(7.dp)); StatusPill(if (online) "متصل" else "غير متصل") }
                Text("الباقة: ${user.optString("profile", "-")}", color = TextMuted, fontSize = 11.sp)
            }
            if (online) TextButton(onClick = { scope.launch { busy = true; try { val r = JSONObject(api.post("/api/routers/$routerId/hotspot/users/disconnect", JSONObject().put("username", name).toString())); onResult("تم فصل $name (${r.optInt("disconnected")} جلسة)") } catch (e: Exception) { onResult(friendlyHotspotError(e.message ?: "تعذر فصل الجلسة")) } finally { busy = false } } }, enabled = !busy) { Text(if (busy) "…" else "فصل", color = MaterialTheme.colorScheme.error) }
        }
        if (online && active != null) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserStat("IP", active.optString("address", "-"), Modifier.weight(1f))
                UserStat("المدة", active.optString("uptime", "-"), Modifier.weight(1f))
                UserStat("المتبقي", active.optString("session-time-left", "-"), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserStat("تحميل", formatHotspotBytes(parseHotspotBytes(active.optString("bytes-out"))), Modifier.weight(1f))
                UserStat("رفع", formatHotspotBytes(parseHotspotBytes(active.optString("bytes-in"))), Modifier.weight(1f))
                UserStat("MAC", active.optString("mac-address", "-"), Modifier.weight(1f))
            }
        } else {
            Text("لا توجد جلسة نشطة حاليًا • الحد: ${user.optString("limit-uptime", "غير محدد")}", color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun UserStat(label: String, value: String, modifier: Modifier) { Column(modifier) { Text(label, color = TextMuted, fontSize = 10.sp); Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) } }

private fun parseHotspotBytes(value: String): Long = value.filter { it.isDigit() }.toLongOrNull() ?: 0L
private fun formatHotspotBytes(value: Long): String = when { value >= 1_000_000_000 -> "%.2f GB".format(value / 1_000_000_000.0); value >= 1_000_000 -> "%.2f MB".format(value / 1_000_000.0); value >= 1_000 -> "%.1f KB".format(value / 1_000.0); else -> "$value B" }
private fun friendlyHotspotError(raw: String): String = when { raw.contains("AGENT_LIVE_USERS_NOT_REPORTED") -> "الراوتر يعمل بوضع Agent، لكن إصدار Agent الحالي لا يرسل تفاصيل استهلاك المستخدمين. استخدم الاتصال المباشر أو حدّث Agent."; raw.contains("TIMEOUT", true) || raw.contains("ETIMEDOUT", true) -> "انتهت مهلة قراءة بيانات HotSpot من الراوتر؛ تحقق من الاتصال ثم حدّث مرة أخرى."; else -> raw }
