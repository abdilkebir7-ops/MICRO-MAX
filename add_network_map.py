#!/usr/bin/env python3
"""Add a basic network-map tab to MICRO-MAX. Run at repo root."""
from pathlib import Path
import re, subprocess, sys
from datetime import datetime

branch = subprocess.run(["git", "branch", "--show-current"], text=True, capture_output=True)
if branch.returncode:
    sys.exit("ERROR: run this inside the MICRO-MAX git repository.")
if branch.stdout.strip() != "fix/direct-routeros-android":
    sys.exit("STOP: switch to branch fix/direct-routeros-android first. main will not be modified.")

path = Path("app/src/main/java/com/micromax/app/MainActivity.kt")
if not path.exists():
    sys.exit(f"ERROR: file not found: {path}")
src = path.read_text(encoding="utf-8")
m = re.search(r"(?m)^@Composable\s*\nfun NetworkPage\s*\(", src)
if not m:
    sys.exit("ERROR: NetworkPage was not found; no files changed.")
tail = src[m.end():]
n = re.search(r"(?m)^@Composable\s*\n(?:private\s+)?fun\s+\w+\s*\(", tail)
if not n:
    sys.exit("ERROR: could not safely find the end of NetworkPage; no files changed.")
end = m.end() + n.start()

new = '''@Composable
fun NetworkPage(api: Api, router: JSONObject?) {
    var tab by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf(JSONArray()) }
    var interfaces by remember { mutableStateOf(JSONArray()) }
    var arpDevices by remember { mutableStateOf(JSONArray()) }
    var dhcpLeases by remember { mutableStateOf(JSONArray()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val names = listOf("خريطة الشبكة", "Interfaces", "DHCP", "ARP", "DNS", "IP", "Routes", "Firewall", "Queues", "Logs", "IP Bindings")
    val paths = listOf("", "interfaces", "dhcp-leases", "arp", "dns-static", "ip-addresses", "routes", "firewall", "queues", "logs", "ip-bindings")

    suspend fun fetch(path: String): JSONArray {
        val response = JSONObject(api.get("/api/routers/${router?.optString("id")}/network/$path"))
        return response.optJSONArray("items") ?: JSONArray()
    }

    LaunchedEffect(router?.optString("id"), tab) {
        if (router == null) {
            data = JSONArray(); interfaces = JSONArray(); arpDevices = JSONArray(); dhcpLeases = JSONArray()
            err = null
        } else {
            loading = true
            try {
                if (tab == 0) {
                    interfaces = fetch("interfaces")
                    arpDevices = fetch("arp")
                    dhcpLeases = fetch("dhcp-leases")
                } else data = fetch(paths[tab])
                err = null
            } catch (e: Exception) {
                err = e.message ?: "تعذر تحميل بيانات الشبكة"
            } finally { loading = false }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("خريطة الشبكة", fontSize = 27.sp, fontWeight = FontWeight.Black)
        Text(router?.optString("name") ?: "اختر راوتر", color = TextMuted)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEachIndexed { i, name -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(name, fontSize = 11.sp) }) }
        }
        err?.let { ErrorCard(it) }
        if (tab == 0) {
            if (router == null) EmptyCard("اختر راوتر لعرض خريطة الشبكة")
            else if (loading) GlassCard { Text("جارٍ تحميل بيانات الشبكة…", color = TextMuted) }
            else {
                GlassCard {
                    Text("مخطط الاتصال", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f).background(TextMuted.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌐", fontSize = 24.sp); Text("الإنترنت", fontWeight = FontWeight.Bold)
                        }
                        Text("⇄", fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Column(Modifier.weight(1f).background(TextMuted.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📡", fontSize = 24.sp); Text(router.optString("name", "MikroTik"), fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("عرض مبسط مبني على البيانات التي يرجعها الراوتر عبر API.", fontSize = 12.sp, color = TextMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { GlassCard { Text("الواجهات", color = TextMuted, fontSize = 12.sp); Text(interfaces.length().toString(), fontSize = 24.sp, fontWeight = FontWeight.Black) } }
                    Column(Modifier.weight(1f)) { GlassCard { Text("أجهزة ARP", color = TextMuted, fontSize = 12.sp); Text(arpDevices.length().toString(), fontSize = 24.sp, fontWeight = FontWeight.Black) } }
                    Column(Modifier.weight(1f)) { GlassCard { Text("DHCP", color = TextMuted, fontSize = 12.sp); Text(dhcpLeases.length().toString(), fontSize = 24.sp, fontWeight = FontWeight.Black) } }
                }
                GlassCard {
                    Text("واجهات الراوتر", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (interfaces.length() == 0) Text("لا توجد بيانات واجهات.", color = TextMuted)
                    else Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        (0 until interfaces.length()).forEach { i ->
                            val item = interfaces.optJSONObject(i) ?: JSONObject()
                            Text(item.optString("name", item.optString("interface", "واجهة ${i + 1}")), fontWeight = FontWeight.Bold)
                            Text(listOf(item.optString("type"), item.optString("mac-address"), item.optString("running")).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "واجهة شبكة" }, color = TextMuted, fontSize = 11.sp)
                            if (i < interfaces.length() - 1) Divider(color = TextMuted.copy(alpha = 0.15f))
                        }
                    }
                }
                GlassCard {
                    Text("الأجهزة المكتشفة (ARP)", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (arpDevices.length() == 0) Text("لا توجد أجهزة ARP ظاهرة حالياً.", color = TextMuted)
                    else Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        (0 until minOf(arpDevices.length(), 30)).forEach { i ->
                            val item = arpDevices.optJSONObject(i) ?: JSONObject()
                            Text(item.optString("address", item.optString("ip-address", "IP غير معروف")), fontWeight = FontWeight.Bold)
                            Text(listOf(item.optString("mac-address"), item.optString("interface"), item.optString("status")).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "جهاز مكتشف" }, color = TextMuted, fontSize = 11.sp)
                            if (i < minOf(arpDevices.length(), 30) - 1) Divider(color = TextMuted.copy(alpha = 0.15f))
                        }
                        if (arpDevices.length() > 30) Text("يتم عرض أول 30 جهازاً.", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                items((0 until data.length()).mapNotNull { data.optJSONObject(it) }) { item ->
                    GlassCard {
                        Text(item.optString("name", item.optString("address", item.optString("dst-address", item.optString("message", "-")))), fontWeight = FontWeight.Bold)
                        Text(item.toString().removePrefix("{").removeSuffix("}").take(260), fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}'''

backup = path.with_name(path.name + ".backup-network-map-" + datetime.now().strftime("%Y%m%d-%H%M%S"))
backup.write_text(src, encoding="utf-8")
path.write_text(src[:m.start()] + new + "\n\n" + src[end:], encoding="utf-8")
print("تمت إضافة واجهة خريطة الشبكة.")
print("نسخة احتياطية:", backup)
print("الملف المعدل:", path)
print("الآن نفّذ: git diff --check")
print("ثم راجع التغيير: git diff -- app/src/main/java/com/micromax/app/MainActivity.kt")
