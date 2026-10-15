package com.micromax.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private data class MapNode(val id:String,val type:String,val label:String,val subtitle:String,val online:Boolean)

@Composable
fun NetworkMapPage(api: Api, router: JSONObject?) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf("router") }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { if (router == null) return@launch; loading = true; try { data = JSONObject(api.get("/api/routers/${router.optString("id")}/network-map")); error = null } catch (e: Exception) { data = null; error = e.message ?: "تعذر تحميل خريطة الشبكة" } finally { loading = false } } }
    LaunchedEffect(router?.optString("id"), reload) { load() }
    val raw = data?.optJSONArray("nodes") ?: JSONArray()
    val nodes = (0 until raw.length()).mapNotNull { raw.optJSONObject(it)?.let { n -> MapNode(n.optString("id"), n.optString("type"), n.optString("label","جهاز"), n.optString("subtitle",""), n.optBoolean("online", true)) } }
    val stats = data?.optJSONObject("stats")
    val aps = nodes.filter { it.type == "access_point" }
    val clients = nodes.filter { it.type == "client" }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("خريطة الشبكة", fontSize = 27.sp, fontWeight = FontWeight.Black); Text(router?.optString("name") ?: "اختر راوتر", color = TextMuted) }
            IconButton({ reload++ }, enabled = !loading && router != null) { Icon(MmIcons.Sync, "تحديث", tint = Accent) }
        }
        if (router == null) EmptyCard("اختر راوتر أولاً")
        else if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MapStat("أجهزة متصلة", stats?.optInt("connectedDevices", clients.size) ?: clients.size, Color(0xFF65E6B1), Modifier.weight(1f))
                MapStat("أكسسات شغالة", stats?.optInt("accessPoints", aps.size) ?: aps.size, Color(0xFF55A7FF), Modifier.weight(1f))
                MapStat("أجهزة فاصلة", stats?.optInt("offlineDevices", 0) ?: 0, Color(0xFFB7B7BA), Modifier.weight(1f))
            }
            error?.let { ErrorCard(it) }
            NetworkGraph(nodes, selectedId) { selectedId = it }
            nodes.firstOrNull { it.id == selectedId }?.let { n ->
                GlassCard { Text(n.label, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(n.subtitle.ifBlank { "بدون تفاصيل إضافية" }, color = TextMuted); Text(if (n.online) "متصل" else "غير متصل", color = if (n.online) Color(0xFF65E6B1) else Color(0xFFFFA7A7), fontSize = 12.sp) }
            }
            Text("الأجهزة المكتشفة", style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                items(nodes.filter { it.id != "router" }) { n ->
                    Box(Modifier.clickable { selectedId = n.id }) {
                        GlassCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (n.type == "access_point") MmIcons.Wifi else MmIcons.Users, null, tint = if (n.type == "access_point") Color(0xFF55A7FF) else Accent)
                                Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(n.label, fontWeight = FontWeight.Bold); Text(n.subtitle, color = TextMuted, fontSize = 12.sp) }
                                Text(if (n.online) "متصل" else "فاصل", color = if (n.online) Color(0xFF65E6B1) else TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun MapStat(label:String, value:Int, color:Color, modifier:Modifier) { Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(value.toString(), color = color, fontSize = 22.sp, fontWeight = FontWeight.Black); Text(label, color = TextMuted, fontSize = 11.sp) } } }

@Composable private fun NetworkGraph(nodes:List<MapNode>, selected:String, onSelect:(String)->Unit) {
    Canvas(Modifier.fillMaxWidth().height(350.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)).pointerInput(nodes) { detectTapGestures { tap -> val w = size.width.toFloat(); val h = size.height.toFloat(); val points = graphPoints(nodes, w, h); points.minByOrNull { distance(it.second, tap) }?.let { if (distance(it.second, tap) < 60f) onSelect(it.first.id) } } }) {
        val points = graphPoints(nodes, size.width, size.height); val router = points.firstOrNull { it.first.id == "router" } ?: return@Canvas
        points.filter { it.first.id != "router" }.forEach { (_, p) -> drawLine(Color(0xFF2C7BC7), router.second, p, strokeWidth = 4f) }
        points.forEach { (node, p) -> val radius = if (node.id == "router") 43f else 31f; val color = if (node.id == selected) Color(0xFF70B8FF) else if (node.type == "access_point") Color(0xFF2A81D5) else Color(0xFF344B68); drawCircle(color, radius, p); drawCircle(Color(0xFF59AAFF), radius, p, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)) }
    }
}

private fun graphPoints(nodes:List<MapNode>, width:Float, height:Float):List<Pair<MapNode,Offset>> { val center = Offset(width / 2f, height / 2f); val others = nodes.filter { it.id != "router" }.take(12); val radius = min(width, height) * .32f; val root = (nodes.firstOrNull { it.id == "router" } ?: MapNode("router","router","Router","",true)) to center; return listOf(root) + others.mapIndexed { i,n -> val angle = (-Math.PI / 2.0) + (2.0 * Math.PI * i / maxOf(1, others.size)); n to Offset(center.x + (cos(angle) * radius).toFloat(), center.y + (sin(angle) * radius).toFloat()) } }
private fun distance(a:Offset,b:Offset):Float { val dx=a.x-b.x; val dy=a.y-b.y; return sqrt(dx*dx+dy*dy) }
