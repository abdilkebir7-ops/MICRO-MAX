package com.micromax.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject

private val RefBg = Color(0xFF0B0F15)
private val RefPanel = Color(0xFF151B23)
private val RefPanel2 = Color(0xFF1B222C)
private val RefBlue = Color(0xFF1976D2)
private val RefCyan = Color(0xFF4DD0E1)
private val RefText = Color(0xFFF2F4F8)
private val RefMuted = Color(0xFF9BA4B2)

@Composable
fun ReferenceRouterScreen(api: Api, onSaved: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("micromax_router_draft", android.content.Context.MODE_PRIVATE) }
    var name by remember { mutableStateOf(prefs.getString("name", "الراوتر الرئيسي") ?: "الراوتر الرئيسي") }
    var host by remember { mutableStateOf(prefs.getString("host", "") ?: "") }
    // MikroTik's built-in account is lowercase `admin`; usernames are case-sensitive.
    var user by remember { mutableStateOf(prefs.getString("user", "admin") ?: "admin") }
    var password by remember { mutableStateOf("") }
    // Hamo-style direct mode supports both API and API-SSL from the phone.
    // Hamo's alternative WinBox-compatible path is RouterOS binary API on 8728/8729.
    var port by remember { mutableStateOf(prefs.getString("port", "8728") ?: "8728") }
    var protocol by remember { mutableStateOf("API") }
    var rememberData by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun connect() {
        scope.launch {
            busy = true
            message = null
            try {
                if (protocol != "API") {
                    message = if (protocol == "REST API") "REST API يحتاج تفعيل www-ssl على الراوتر." else "SSH متاح من طرفية RouterOS داخل الخدمات."
                } else {
                    message = "1/4 فحص عنوان الراوتر…"; delay(250)
                    message = "2/4 فحص المنفذ $port…"; delay(250)
                    message = "3/4 التحقق من بيانات الدخول…"
                    val selectedPort = port.toIntOrNull() ?: 8728
                    val selectedTls = selectedPort == 8729
                    // Test both API 8728 and API-SSL 8729 directly on the phone.
                    // Render is only used afterward to sync the saved router.
                    val candidates = if (selectedPort == 22) listOf(22 to false, 8728 to false, 8729 to true) else listOf(selectedPort to selectedTls)
                    var actualPort: Int? = null
                    var actualTls = false
                    var lastFailure: Exception? = null
                    for ((candidatePort, candidateTls) in candidates) {
                        try {
                            val local = RouterOsApi(host.trim(), candidatePort, tls = candidateTls)
                            try { local.connect(user.trim(), password) }
                            finally { local.close() }
                            actualPort = candidatePort
                            actualTls = candidateTls
                            break
                        } catch (candidateError: Exception) { lastFailure = candidateError }
                    }
                    if (actualPort == null) throw (lastFailure ?: Exception("ROUTER_API_UNREACHABLE"))
                    message = "تم الاتصال مباشرة عبر ${if (actualTls) "API-SSL" else "API"} على المنفذ $actualPort…"
                    message = "4/4 حفظ الراوتر في حسابك…"
                    val saved = JSONObject().put("name", name.trim().ifBlank { "الراوتر الرئيسي" })
                        .put("host", host.trim()).put("port", actualPort)
                        .put("username", user.trim()).put("password", password).put("tls", actualTls)
                    api.post("/api/routers", saved.toString())
                    if (rememberData) prefs.edit().putString("name", name.trim()).putString("host", host.trim()).putString("user", user.trim()).putString("port", port).apply()
                    else prefs.edit().clear().apply()
                    message = "تم اختبار الاتصال وحفظ الراوتر بنجاح"
                    Toast.makeText(ctx, "تم الاتصال بالراوتر بنجاح", Toast.LENGTH_SHORT).show()
                    delay(200)
                    onSaved()
                }
            } catch (e: Exception) {
                message = routerConnectionMessage(e.message ?: "فشل الاتصال") + "\n\n" + routerNetworkReport(host.trim(), port.toIntOrNull() ?: 8728)
            } finally { busy = false }
        }
    }

    Surface(color = cs.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(25.dp)).background(cs.surface)) {
                    Icon(Icons.Outlined.Info, "معلومات", tint = cs.onBackground, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("MICROMAX", color = cs.onBackground, style = MaterialTheme.typography.headlineSmall)
                    Text("إدارة شبكات MikroTik", color = cs.onSurfaceVariant, fontSize = 13.sp)
                    Text("Internet Network Management", color = cs.onSurfaceVariant, fontSize = 10.sp)
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(cs.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Router, "Router", tint = Color.White, modifier = Modifier.size(31.dp))
                }
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp), horizontalAlignment = Alignment.End) {
                Spacer(Modifier.height(8.dp))
                Text("إضافة راوتر جديد", color = cs.onBackground, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(22.dp))
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.primary)) {
                    Text("اختر من الراوترات المحفوظة (0)", fontSize = 16.sp); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.Hub, null)
                }
                Spacer(Modifier.height(12.dp))
                RefField(name, { name = it }, "اسم الراوتر", Icons.Outlined.Label)
                RefField(host, { host = it }, "عنوان الراوتر", Icons.Outlined.Dns)
                RefField(user, { user = it }, "اسم المستخدم", Icons.Outlined.Person)
                RefField(password, { password = it }, "كلمة المرور", Icons.Outlined.VisibilityOff, PasswordVisualTransformation())
                RefField(port, { port = it.filter(Char::isDigit) }, "منفذ الاتصال", Icons.Outlined.SettingsEthernet)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(30.dp)).border(1.dp, cs.outline, RoundedCornerShape(30.dp)), verticalAlignment = Alignment.CenterVertically) {
                    listOf("API", "REST API", "SSH").forEach { item ->
                        TextButton(onClick = { protocol = item; port = when (item) { "REST API" -> "443"; "SSH" -> "22"; else -> "8728" } }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.textButtonColors(containerColor = if (protocol == item) cs.primary.copy(alpha = .22f) else Color.Transparent, contentColor = cs.onSurface)) { Text(item, fontSize = 16.sp) }
                    }
                }
                Text(if (protocol == "API") "طريقة بديلة عبر بروتوكول MikroTik الثنائي (نفس WinBox) • المنافذ 8728/8729 • بدون الطرفية ورفع الملفات (SFTP)." else if (protocol == "REST API") "اتصال HTTPS عبر REST API • المنفذ 443." else "اتصال طرفية آمن SSH • المنفذ 22.", color = cs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    Text("تذكر البيانات", color = cs.onBackground, fontSize = 16.sp); Checkbox(rememberData, { rememberData = it }, colors = CheckboxDefaults.colors(checkedColor = cs.primary))
                }
                message?.let { msg -> Surface(color = cs.tertiaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Text(msg, color = cs.onTertiaryContainer, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp)) } }
                Button(onClick = ::connect, enabled = !busy && host.isNotBlank() && user.isNotBlank(), modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(containerColor = cs.primary)) { Icon(Icons.Outlined.Link, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "جاري الاتصال…" else "اختبار الاتصال وحفظ الراوتر", fontSize = 16.sp) }
                Spacer(Modifier.height(18.dp))
                Spacer(Modifier.height(18.dp))
            }
            Row(Modifier.fillMaxWidth().height(76.dp).background(cs.surface).padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(Icons.Outlined.GridView, "المزيد", tint = cs.onSurface, modifier = Modifier.size(26.dp)); Icon(Icons.Outlined.BarChart, "المبيعات", tint = cs.onSurface, modifier = Modifier.size(26.dp)); Icon(Icons.Outlined.ConfirmationNumber, "الكروت", tint = cs.onSurface, modifier = Modifier.size(26.dp)); Icon(Icons.Outlined.Router, "الراوتر", tint = cs.secondary, modifier = Modifier.size(30.dp)); Text("الرئيسية", color = cs.onSecondary, modifier = Modifier.clip(RoundedCornerShape(28.dp)).background(cs.secondary).padding(horizontal = 18.dp, vertical = 13.dp))
            }
        }
    }
}

@Composable
private fun RefField(value: String, onValueChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, transformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(value, onValueChange, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), label = { Text(label, color = cs.onSurfaceVariant) }, leadingIcon = { Icon(icon, null, tint = cs.onSurface) }, singleLine = true, visualTransformation = transformation, shape = RoundedCornerShape(22.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = cs.surfaceVariant, unfocusedContainerColor = cs.surfaceVariant, focusedBorderColor = cs.primary, unfocusedBorderColor = cs.outline, focusedTextColor = cs.onSurface, unfocusedTextColor = cs.onSurface, focusedLabelColor = cs.primary, unfocusedLabelColor = cs.onSurfaceVariant))
}
