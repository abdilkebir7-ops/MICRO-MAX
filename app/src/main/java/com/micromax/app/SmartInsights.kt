package com.micromax.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Deterministic suggestions from the latest RouterOS snapshot, not claimed to be ML or a speed controller. */
data class NetworkInsight(val title: String, val detail: String, val urgent: Boolean = false, val target: Int = 8)

fun evaluateNetworkInsights(cpu: Float, ram: Float, disk: Float, downInterfaces: Int): List<NetworkInsight> {
    val list = mutableListOf<NetworkInsight>()
    if (cpu >= 85f) list += NetworkInsight("ضغط مرتفع على المعالج", "CPU عند ${cpu.toInt()}%. راجع الجلسات وقواعد الجدار الناري قبل تغيير أي حدود سرعة.", true)
    if (ram >= 88f) list += NetworkInsight("الذاكرة تقترب من الحد", "RAM عند ${ram.toInt()}%. تحقق من عدد المستخدمين والمهام النشطة على الراوتر.", true)
    if (disk >= 90f) list += NetworkInsight("مساحة التخزين منخفضة", "الاستخدام ${disk.toInt()}%. راجع السجلات والملفات قبل حذف أي شيء.", true)
    if (downInterfaces > 0) list += NetworkInsight("${downInterfaces} واجهة غير نشطة", "قد تكون منافذ احتياطية؛ راجع قائمة الواجهات قبل اتخاذ إجراء.")
    if (list.isEmpty()) list += NetworkInsight("لا توجد إشارات حرجة", "المؤشرات الظاهرة طبيعية في القراءة الحالية. استمر في مراقبة الشبكة.")
    return list
}

fun routerInsights(dash: JSONObject?): List<NetworkInsight> {
    if (dash == null) return listOf(NetworkInsight("بانتظار بيانات الراوتر", "اختر راوترًا أو حدّث الصفحة لتحليل آخر قراءة."))
    val resource = dash.optJSONObject("resource")
    val cpu = resource?.optString("cpu-load")?.toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f
    val ram = memorySignal(resource, "total-memory", "free-memory")
    val disk = memorySignal(resource, "total-hdd-space", "free-hdd-space")
    val ints = dash.optJSONArray("interfaces")
    val down = (0 until (ints?.length() ?: 0)).count { i ->
        val o = ints?.optJSONObject(i)
        val n = o?.optString("name", "") ?: ""
        o?.optString("running") == "false" && o.optString("disabled") != "true" && !n.contains("backup", ignoreCase = true)
    }
    return evaluateNetworkInsights(cpu, ram, disk, down)
}

private fun memorySignal(r: JSONObject?, total: String, free: String): Float {
    val t = r?.optString(total)?.toLongOrNull() ?: return 0f
    val f = r.optString(free)?.toLongOrNull() ?: return 0f
    return if (t > 0) ((t - f) * 100f / t).coerceIn(0f, 100f) else 0f
}

@Composable
fun InsightsPanel(dash: JSONObject?, onOpenNetwork: () -> Unit) {
    val insights = routerInsights(dash)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolTile(MmIcons.Sparkle, Tangerine, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("مرصد الشبكة", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("توصيات مبنية على آخر قراءة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LedDot(Link, size = 5.dp, live = true); Text("مباشر", style = MaterialTheme.typography.labelSmall, color = Link)
            }
            insights.take(2).forEach { insight ->
                val tint = if (insight.urgent) Fault else if (insight.title.startsWith("لا توجد")) Teal else Tangerine
                Surface(color = tint.copy(alpha = .08f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(insight.title, style = MaterialTheme.typography.labelLarge, color = tint)
                        Text(insight.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = onOpenNetwork, modifier = Modifier.align(Alignment.End)) {
                Text("فحص أدوات الشبكة")
                Spacer(Modifier.width(5.dp))
                Icon(MmIcons.ChevronL, null, Modifier.size(16.dp))
            }
        }
    }
}
