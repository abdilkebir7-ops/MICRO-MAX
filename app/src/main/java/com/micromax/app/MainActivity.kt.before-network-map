package com.micromax.app

import android.graphics.Bitmap
import android.os.Bundle
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Legacy screen aliases now follow the selected color scheme, not static dark-only colors.
val Bg: Color @Composable get() = MaterialTheme.colorScheme.background
val Card: Color @Composable get() = MaterialTheme.colorScheme.surface
val Card2: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
val TextMain: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // applies postSplashScreenTheme: NoActionBar, avoiding duplicate title
        super.onCreate(savedInstanceState)
        setContent { MicroMaxApp(this) }
    }

    fun printBitmap(bitmap: Bitmap) {
        try {
            androidx.print.PrintHelper(this).apply { scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT }.printBitmap("MICRO-MAX Card", bitmap)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "تعذر فتح الطباعة", Toast.LENGTH_LONG).show()
        }
    }
}

class Api(var base: String) {
    // Large card batches and router operations can take longer than a normal call.
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    // Render's free plan sleeps after inactivity and needs up to ~60s to wake: ping /health first so real calls don't time out.
    private val waker = http.newBuilder().connectTimeout(75, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS).callTimeout(80, TimeUnit.SECONDS).build()
    @Volatile private var lastOk = 0L
    private fun wake() {
        if (System.currentTimeMillis() - lastOk < 8 * 60_000L) return
        try { waker.newCall(Request.Builder().url(base.trim().trimEnd('/') + "/health").get().build()).execute().use { if (it.isSuccessful) lastOk = System.currentTimeMillis() } } catch (_: Exception) { }
    }
    var token: String? = null
    private fun req(path: String) = Request.Builder().url(base.trim().trimEnd('/') + path).apply { token?.let { addHeader("Authorization", "Bearer $it") } }
    suspend fun get(path: String): String = call(req(path).get().build())
    suspend fun post(path: String, body: String = "{}"): String = call(req(path).post(body.toRequestBody("application/json".toMediaType())).build())
    suspend fun delete(path: String): String = call(req(path).delete().build())
    suspend fun download(path: String): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(req(path).get().build()).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw Exception(try { JSONObject(body).optString("detail").ifBlank { JSONObject(body).optString("error") } } catch (_: Exception) { "HTTP ${response.code}" })
            }
            response.body?.bytes() ?: ByteArray(0)
        }
    }
    private suspend fun call(r: Request): String = withContext(Dispatchers.IO) { wake(); http.newCall(r).execute().use { x -> val b=x.body?.string().orEmpty(); if(x.code < 500) lastOk = System.currentTimeMillis(); if(!x.isSuccessful) throw Exception(try{JSONObject(b).optString("detail").ifBlank{JSONObject(b).optString("error")}}catch(_:Exception){"HTTP ${x.code}"}); b } }
}

@Composable
fun MicroMaxApp(activity: MainActivity) {
    val themePrefs = remember { activity.getSharedPreferences("micromax_session", Context.MODE_PRIVATE) }
    var themeDark by remember { mutableStateOf(themePrefs.getBoolean("theme_dark", false)) }
    MicroMaxTheme(themeDark) {
        val barColor = MaterialTheme.colorScheme.background
        SideEffect {
            val w = activity.window
            w.statusBarColor = barColor.toArgb()
            w.navigationBarColor = barColor.toArgb()
            val c = androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
            c.isAppearanceLightStatusBars = !themeDark
            c.isAppearanceLightNavigationBars = !themeDark
        }
        val prefs = remember { activity.getSharedPreferences("micromax_session", Context.MODE_PRIVATE) }
        val api = remember { Api((prefs.getString("api_base_url", null)?.takeIf { it.startsWith("http") } ?: BuildConfig.MICROMAX_API_BASE_URL).ifBlank { "https://micro-max-api.onrender.com" }) }
        var authenticated by remember { mutableStateOf(prefs.getString("jwt_token", null).isNullOrBlank().not()) }
        if (authenticated) {
            api.token = prefs.getString("jwt_token", null)
            MainShell(api, activity, themeDark, { themeDark = !themeDark; themePrefs.edit().putBoolean("theme_dark", themeDark).apply() }) {
                prefs.edit().remove("jwt_token").apply()
                api.token = null
                authenticated = false
            }
        } else {
            AuthScreen(api, prefs) { token ->
                prefs.edit().putString("jwt_token", token).apply()
                api.token = token
                authenticated = true
            }
        }
    }
}

@Composable
private fun AuthPill(label: String, icon: ImageVector) {
    Row(Modifier.background(Color.White.copy(alpha = .08f), CircleShape).padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = SignalSoft, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

private fun authError(raw: String): String = when {
    raw.contains("INVALID_CREDENTIALS") -> "البريد أو كلمة المرور غير صحيحة."
    raw.contains("EMAIL_EXISTS") -> "هذا البريد مسجّل مسبقًا. انتقل إلى «تسجيل الدخول»."
    raw.contains("REGISTRATION_CLOSED") -> "إنشاء الحسابات الجديدة مغلق على هذا الخادم. فعّل ALLOW_REGISTRATION=true في إعدادات Render ثم أعد النشر."
    raw.contains("WEAK_PASSWORD") -> "كلمة المرور قصيرة: 8 أحرف على الأقل."
    raw.contains("INVALID_EMAIL") -> "صيغة البريد الإلكتروني غير صحيحة."
    raw.contains("429") || raw.contains("many", true) -> "محاولات كثيرة. انتظر دقيقة ثم أعد المحاولة."
    raw.contains("scheme", true) || raw.contains("Expected URL", true) -> "رابط الخادم غير صحيح. اضغط على اسم الخادم في الأسفل لتعديله."
    raw.contains("timeout", true) || raw.contains("resolve", true) || raw.contains("failed to connect", true) || raw.contains("UnknownHost", true) ->
        "تعذر الوصول إلى الخادم. تأكد من الإنترنت؛ الخادم المجاني قد يحتاج دقيقة ليستيقظ."
    else -> raw
}

private fun passwordScore(p: String): Int {
    var n = 0
    if (p.length >= 8) n++
    if (p.length >= 12) n++
    if (p.any { it.isDigit() } && p.any { it.isLetter() }) n++
    if (p.any { !it.isLetterOrDigit() } || (p.any { it.isUpperCase() } && p.any { it.isLowerCase() })) n++
    return n
}

@Composable
private fun AuthTabs(register: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.surfaceVariant, RoundedCornerShape(18.dp)).padding(4.dp)) {
        listOf(false to "تسجيل الدخول", true to "حساب جديد").forEach { (isRegister, label) ->
            val on = register == isRegister
            val bg by animateColorAsState(if (on) cs.surface else Color.Transparent, tween(200), label = "tabBg")
            val fg by animateColorAsState(if (on) cs.primary else cs.onSurfaceVariant, tween(200), label = "tabFg")
            Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(bg).pressScale({ onChange(isRegister) }), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, reveal: Boolean, onReveal: () -> Unit, ime: ImeAction, onDone: () -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) },
        leadingIcon = { Icon(MmIcons.Lock, null) },
        trailingIcon = { IconButton(onReveal) { Icon(if (reveal) MmIcons.EyeOff else MmIcons.Eye, if (reveal) "إخفاء" else "إظهار") } },
        singleLine = true, visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ime),
        keyboardActions = KeyboardActions(onNext = { }, onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors())
}

@Composable
private fun AuthScreen(api: Api, prefs: android.content.SharedPreferences, onAuthenticated: (String) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(prefs.getString("last_email", "") ?: "") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var serverDialog by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(api.base) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val score = passwordScore(password)

    fun submit() {
        if (busy) return
        val mail = email.trim()
        val problem = when {
            !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(mail) -> "أدخل بريدًا إلكترونيًا صحيحًا."
            password.length < 8 -> "كلمة المرور 8 أحرف على الأقل."
            register && password != confirm -> "كلمتا المرور غير متطابقتين."
            else -> null
        }
        if (problem != null) { message = problem; return }
        scope.launch {
            busy = true; message = null
            try {
                val body = JSONObject().put("email", mail).put("password", password)
                if (register && name.isNotBlank()) body.put("name", name.trim())
                val raw = api.post(if (register) "/api/auth/register" else "/api/auth/login", body.toString())
                val token = JSONObject(raw).optString("token")
                require(token.isNotBlank()) { "الخادم لم يُرجع رمز الجلسة" }
                prefs.edit().putString("last_email", mail).apply()
                onAuthenticated(token)
            } catch (e: Exception) { message = authError(e.message ?: "تعذر الاتصال بالخادم") }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
        ChassisPanel(Modifier.fillMaxWidth().padding(14.dp), radius = 32.dp) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkBadge(size = 50.dp)
                    Spacer(Modifier.width(13.dp))
                    Column {
                        Text("MICRO-MAX", style = MaterialTheme.typography.titleLarge, color = Color.White, letterSpacing = 1.4.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LedDot(Link, size = 5.dp, live = true)
                            Text("إدارة شبكات MikroTik", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .65f))
                        }
                    }
                }
                Text(if (register) "أنشئ حسابك وابدأ" else "أهلًا بعودتك", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(if (register) "حساب مستقل: راوتراتك وكروتك ومبيعاتك لا يراها غيرك." else "سجّل الدخول لإدارة راوتراتك وكروتك ومبيعاتك.", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AuthTabs(register) { register = it; message = null }
            Surface(shape = RoundedCornerShape(28.dp), color = cs.surface, border = BorderStroke(1.dp, cs.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    AnimatedVisibility(register) {
                        OutlinedTextField(name, { name = it }, label = { Text("الاسم (اختياري)") }, leadingIcon = { Icon(MmIcons.Person, null) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors())
                    }
                    OutlinedTextField(email, { email = it }, label = { Text("البريد الإلكتروني") }, leadingIcon = { Icon(MmIcons.Mail, null) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors())
                    PasswordField(password, { password = it }, "كلمة المرور", reveal, { reveal = !reveal }, if (register) ImeAction.Next else ImeAction.Done) { submit() }
                    AnimatedVisibility(register) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                SegmentMeter(score * 25f, tint = when (score) { 0, 1 -> Fault; 2 -> Activity; else -> Link }, segments = 4, height = 6.dp)
                                Text(
                                    if (password.isEmpty()) "استخدم 8 أحرف على الأقل، ويفضّل حروفًا وأرقامًا ورموزًا."
                                    else when (score) { 0, 1 -> "ضعيفة"; 2 -> "مقبولة"; 3 -> "جيدة"; else -> "قوية" },
                                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant
                                )
                            }
                            PasswordField(confirm, { confirm = it }, "تأكيد كلمة المرور", reveal, { reveal = !reveal }, ImeAction.Done) { submit() }
                        }
                    }
                    AnimatedVisibility(message != null) { ErrorCard(message ?: "") }
                    Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text(if (register) "إنشاء الحساب" else "تسجيل الدخول", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton({ register = !register; message = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (register) "لديك حساب؟ سجّل الدخول" else "ليس لديك حساب؟ أنشئ حسابًا جديدًا")
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressScale({ serverUrl = api.base; serverDialog = true }).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(MmIcons.Cloud, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("الخادم: ${api.base.removePrefix("https://").removePrefix("http://").trimEnd('/')}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("تغيير", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (serverDialog) AlertDialog(
        onDismissRequest = { serverDialog = false }, shape = RoundedCornerShape(28.dp),
        title = { Text("رابط الخادم") },
        text = { OutlinedTextField(serverUrl, { serverUrl = it }, singleLine = true, label = { Text("https://...") }, shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri)) },
        confirmButton = { TextButton({
            val clean = serverUrl.trim().trimEnd('/')
            if (clean.startsWith("https://") || clean.startsWith("http://")) { api.base = clean; prefs.edit().putString("api_base_url", clean).apply(); message = null; serverDialog = false }
        }) { Text("حفظ") } },
        dismissButton = { TextButton({ serverDialog = false }) { Text("إلغاء") } }
    )
}

@Composable fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary, unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary, unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
)
@Composable fun Logo() { NetworkBadge(size = 92.dp) }
@Composable fun AppField(v: String, on: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OutlinedTextField(v, on, label = { Text(label) }, leadingIcon = { Icon(icon, null) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors())
}
@Composable fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}

@Composable
private fun DockItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) SignalSoft else Color.Transparent, tween(220), label = "dockBg")
    val fg by animateColorAsState(if (active) Chassis else Color.White.copy(alpha = .72f), tween(220), label = "dockFg")
    Row(
        Modifier.height(46.dp).clip(CircleShape).background(bg).pressScale(onClick)
            .animateContentSize(spring(dampingRatio = .8f, stiffness = 500f)).padding(horizontal = if (active) 15.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, Modifier.size(23.dp), tint = fg)
        if (active) { Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1) }
    }
}

private data class Nav(val title:String,val icon:androidx.compose.ui.graphics.vector.ImageVector,val page:Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(api: Api, activity: MainActivity, themeDark: Boolean, toggleTheme: () -> Unit, onLogout: () -> Unit) {
    val nav = listOf(
        Nav("الرئيسية", MmIcons.Home, 0), Nav("الراوترات", MmIcons.Router, 1),
        Nav("الباقات", MmIcons.Gauge, 2), Nav("الكروت", MmIcons.Ticket, 3),
        Nav("المتجر", MmIcons.Store, 4), Nav("المبيعات", MmIcons.Money, 5),
        Nav("التقارير", MmIcons.Chart, 6), Nav("الأمان", MmIcons.Shield, 7),
        Nav("الشبكة", MmIcons.Network, 8), Nav("الإعدادات", MmIcons.Sliders, 9),
        Nav("HTML HotSpot", MmIcons.Code, 10), Nav("طرفية RouterOS", MmIcons.Terminal, 11),
        Nav("استوديو الكروت", MmIcons.Palette, 12), Nav("محرر الواجهات", MmIcons.Web, 13), Nav("التحكم الذكي", MmIcons.Sparkle, 14), Nav("تقارير الكروت", MmIcons.Layers, 15)
    )
    var page by remember { mutableIntStateOf(0) }
    var moreOpen by remember { mutableStateOf(false) }
    var routers by remember { mutableStateOf(JSONArray()) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var dash by remember { mutableStateOf<JSONObject?>(null) }
    var users by remember { mutableStateOf(JSONArray()) }
    var error by remember { mutableStateOf<String?>(null) }
    var add by remember { mutableStateOf(false) }
    val sessionPrefs = remember { activity.getSharedPreferences("micromax_session", Context.MODE_PRIVATE) }
    var refreshing by remember { mutableStateOf(false) }
    var switcher by remember { mutableStateOf(false) }
    var chosenTemplate by remember { mutableStateOf("Midnight Glass") }
    var storeRouter by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh(manual: Boolean = false) {
        scope.launch {
            if (manual) refreshing = true
            try {
                val root = JSONObject(api.get("/api/routers"))
                routers = root.optJSONArray("routers") ?: JSONArray()
                val old = selected?.optString("id")
                selected = (0 until routers.length()).map { routers.getJSONObject(it) }
                    .firstOrNull { it.optString("id") == old }
                    ?: if (routers.length() > 0) routers.getJSONObject(0) else null
                if (selected != null) {
                    val id = selected!!.optString("id")
                    dash = JSONObject(api.get("/api/routers/$id/dashboard"))
                    users = JSONArray(api.get("/api/routers/$id/hotspot/users"))
                } else {
                    dash = null
                    users = JSONArray()
                }
                error = null
            } catch (e: Exception) { error = e.message ?: "تعذر تحميل البيانات" }
            finally { refreshing = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    // Keep the main dashboard useful for live MikroTik monitoring without
    // requiring the administrator to press refresh repeatedly.
    LaunchedEffect(page) {
        if (page == 0) {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                refresh()
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val dockItems = listOf(nav[0], nav[1], nav[3], nav[4])
    val haptic = LocalHapticFeedback.current
    Box(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            // Top bar: brand mark, current page, router switcher and live refresh
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                NetworkBadge(size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(nav[page].title, style = MaterialTheme.typography.titleMedium, color = colors.onBackground, maxLines = 1)
                    Box {
                        Row(Modifier.clip(RoundedCornerShape(8.dp)).pressScale({ switcher = true }), verticalAlignment = Alignment.CenterVertically) {
                            LedDot(if (selected != null && error == null) Link else if (selected != null) Fault else colors.outline, size = 6.dp, live = selected != null && error == null)
                            Text(selected?.optString("name", "Router") ?: "لا يوجد راوتر", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                            Icon(MmIcons.ChevronL, null, Modifier.size(14.dp).graphicsLayer { rotationZ = -90f }, tint = colors.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = switcher, onDismissRequest = { switcher = false }) {
                            for (i in 0 until routers.length()) {
                                val r = routers.getJSONObject(i)
                                DropdownMenuItem(
                                    text = { Text(r.optString("name", "Router"), style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = { Icon(MmIcons.Router, null, Modifier.size(20.dp), tint = if (r.optString("id") == selected?.optString("id")) colors.primary else colors.onSurfaceVariant) },
                                    onClick = { selected = r; switcher = false; refresh(true) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("إضافة راوتر", style = MaterialTheme.typography.bodyMedium, color = colors.primary) },
                                leadingIcon = { Icon(MmIcons.Add, null, Modifier.size(20.dp), tint = colors.primary) },
                                onClick = { switcher = false; add = true }
                            )
                        }
                    }
                }
                val spin by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "deg")
                Box(Modifier.size(42.dp).clip(CircleShape).background(colors.surface).border(1.dp, colors.outlineVariant, CircleShape).pressScale({ refresh(true) }), contentAlignment = Alignment.Center) {
                    Icon(MmIcons.Sync, "تحديث", Modifier.size(20.dp).graphicsLayer { rotationZ = if (refreshing) spin else 0f }, tint = colors.primary)
                }
            }
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 70)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 28 }) togetherWith fadeOut(tween(110))
                },
                label = "page"
            ) { p ->
                Box(Modifier.fillMaxSize().padding(bottom = 92.dp)) {
                    when (p) {
                        0 -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refresh(true) }, modifier = Modifier.fillMaxSize()) {
                            DashboardPage(routers, selected, dash, users, error, { add = true }, { page = it }) { r -> selected = r; refresh() }
                        }
                        1 -> RoutersPage(routers, selected, { add = true }, { r -> selected = r; page = 0; refresh() }) { id -> scope.launch { try { api.delete("/api/routers/$id"); refresh() } catch (e: Exception) { error = e.message } } }
                        2 -> ProfilesPage(api, selected)
                        3 -> CardsStudioPage(api, selected, activity)
                        4 -> StorePage(api, routers) { t, r -> chosenTemplate = t; storeRouter = if (r.has("id")) r else null; if (r.has("id")) selected = r; page = 12 }
                        5 -> SalesPage(api)
                        6 -> ReportsPage(api)
                        7 -> SecurityPage(api)
                        8 -> NetworkPage(api, selected)
                        9 -> SettingsPage(themeDark, toggleTheme, api.base, { newUrl -> api.base = newUrl; sessionPrefs.edit().putString("api_base_url", newUrl).apply(); refresh() }, onLogout)
                        10 -> HotspotEditorPage(api, selected)
                        11 -> TerminalPage(api, selected)
                        12 -> DesignStudioPage(api, selected, activity, chosenTemplate)
                        13 -> HotspotVisualEditorPage(api, selected)
                        14 -> SmartControlPage(api, selected, { page = 11 }, { page = 8 }, { page = 3 })
                        15 -> CardReportsPage(api, routers, selected)
                    }
                }
            }
        }

        // Floating dock: graphite chassis, active item expands into a labelled pill
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
            Surface(shape = RoundedCornerShape(30.dp), color = Chassis, shadowElevation = 14.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val moreActive = page !in listOf(0, 1, 3, 4)
                    dockItems.forEach { item ->
                        DockItem(item.icon, item.title, page == item.page) { page = item.page }
                    }
                    DockItem(MmIcons.Apps, "الخدمات", moreActive) { moreOpen = true }
                }
            }
        }
    }

    if (moreOpen) ModalBottomSheet(onDismissRequest = { moreOpen = false }, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            PageHeading("مركز العمليات", "كل الأدوات", "انتقل بسرعة إلى أي جزء من النظام")
            val extra = listOf(nav[2], nav[5], nav[6], nav[15], nav[12], nav[13], nav[14], nav[8], nav[7], nav[10], nav[11], nav[9])
            extra.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEach { item -> ActionTile(item.icon, item.title, when (item.page) { 5 -> Link; 12, 13 -> Violet; 6 -> Activity; 7 -> Fault; 8 -> DataBlue; else -> Signal }, Modifier.weight(1f)) { page = item.page; moreOpen = false } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    if (add) AddRouterChooser(api, { add = false; refresh() }, { add = false })
}

@Composable
fun DashboardPage(
    routers: JSONArray,
    selected: JSONObject?,
    dash: JSONObject?,
    users: JSONArray,
    error: String?,
    add: () -> Unit,
    openPage: (Int) -> Unit,
    select: (JSONObject) -> Unit
) {
    val resource = dash?.optJSONObject("resource")
    val cpu = resource?.optString("cpu-load")?.toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f
    val ram = memoryPercent(resource)
    val storage = storagePercent(resource)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 17.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp), contentPadding = PaddingValues(top = 17.dp, bottom = 30.dp)) {
        item { DashboardHeader() }
        error?.let { item { ErrorCard(it) } }
        item { if (selected != null && dash != null) RouterHeroCard(selected, resource, cpu, ram, storage, dash) else EmptyRouterCard(add) }
        if (selected != null) item { InsightsPanel(dash) { openPage(8) } }
        if (selected != null && dash != null) {
            item { SectionHeading("اختصارات التشغيل", "ابدأ مهمتك دون التنقل بين القوائم") }
            item { QuickActions(add, { openPage(3) }, { openPage(2) }, { openPage(4) }, { openPage(6) }, { openPage(12) }, { openPage(14) }, { openPage(11) }) }
        }
        item { SectionHeading("الراوترات", "${routers.length()} جهاز في مساحة العمل", onMore = { openPage(1) }) }
        items((0 until routers.length()).map { routers.getJSONObject(it) }) { r -> RouterCompactRow(r, selected?.optString("id") == r.optString("id")) { select(r) } }
        if (selected != null && dash != null) {
            item { SectionHeading("حركة الشبكة", "الواجهات المتصلة ومستخدمو HotSpot") }
            val ints = dash.optJSONArray("interfaces") ?: JSONArray()
            items((0 until ints.length()).take(6)) { InterfaceRow(ints.getJSONObject(it)) }
            if (users.length() > 0) item { SectionHeading("آخر المستخدمين") }
            items((0 until users.length()).take(6)) { UserRow(users.getJSONObject(it)) }
        }
    }
}

@Composable
private fun DashboardHeader() {
    PageHeading("لوحة التشغيل", "نظرة عامة", "أداء الشبكة والباقات والكروت في مكان واحد")
}

@Composable
private fun MeterRow(label: String, value: Float, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .65f), modifier = Modifier.weight(1f))
            Text("${value.toInt()}%", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
        SegmentMeter(value, tint = tint, track = Color.White.copy(alpha = .1f), segments = 24, height = 8.dp)
    }
}

@Composable
private fun RouterHeroCard(r: JSONObject, resource: JSONObject?, cpu: Float, ram: Float, storage: Float, d: JSONObject) {
    ChassisPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).background(Color.White.copy(alpha = .08f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Icon(MmIcons.Router, null, tint = SignalSoft, modifier = Modifier.size(25.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.optString("name", "MikroTik"), style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${r.optString("host", "-")}  ·  RouterOS ${resource?.optString("version", "-") ?: "-"}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .6f), maxLines = 1)
                }
                Row(Modifier.background(Link.copy(alpha = .16f), CircleShape).padding(start = 6.dp, end = 12.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    LedDot(Link, size = 6.dp, live = true)
                    Text("متصل", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7BE0AD))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelStat("متصل الآن", "${d.optInt("active", d.optInt("activeUsers", 0))}", Modifier.weight(1f), SignalSoft)
                PanelStat("المستخدمون", "${d.optInt("users", d.optInt("activeUsers", 0))}", Modifier.weight(1f))
                PanelStat("حركة البيانات", trafficText(d), Modifier.weight(1.3f))
            }
            HorizontalDivider(color = Color.White.copy(alpha = .1f))
            MeterRow("المعالج CPU", cpu, if (cpu >= 85f) Fault else SignalSoft)
            MeterRow("الذاكرة RAM", ram, if (ram >= 88f) Fault else SignalSoft)
            MeterRow("التخزين", storage, if (storage >= 90f) Fault else Color(0xFF8FA6FF))
        }
    }
}

@Composable
private fun QuickActions(onRouter: () -> Unit, onCards: () -> Unit, onUsers: () -> Unit, onStore: () -> Unit, onReports: () -> Unit, onDesign: () -> Unit, onSmart: () -> Unit, onTerminal: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(MmIcons.Ticket, "إنشاء كروت", Signal, Modifier.weight(1f), onCards)
            ActionTile(MmIcons.Gauge, "الباقات", Violet, Modifier.weight(1f), onUsers)
            ActionTile(MmIcons.Add, "إضافة راوتر", Link, Modifier.weight(1f), onRouter)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(MmIcons.Palette, "التصميم", Activity, Modifier.weight(1f), onDesign)
            ActionTile(MmIcons.Store, "القوالب", DataBlue, Modifier.weight(1f), onStore)
            ActionTile(MmIcons.Chart, "التقارير", Link, Modifier.weight(1f), onReports)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(MmIcons.Sparkle, "التحكم الذكي", Violet, Modifier.weight(1f), onSmart)
            ActionTile(MmIcons.Terminal, "الطرفية", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f), onTerminal)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RouterCompactRow(r: JSONObject, selected: Boolean, click: () -> Unit) {
    ItemSurface(onClick = click) {
        SymbolTile(MmIcons.Router, if (selected) Signal else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.optString("name", "MikroTik"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(r.optString("host", "-"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) StatusPill("النشط") else Icon(MmIcons.ChevronL, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun memoryPercent(r: JSONObject?): Float {
    val t = r?.optString("total-memory")?.toLongOrNull() ?: return 0f
    val f = r.optString("free-memory").toLongOrNull() ?: return 0f
    return if (t > 0) ((t - f) * 100f / t).coerceIn(0f, 100f) else 0f
}

private fun storagePercent(r: JSONObject?): Float {
    val t = r?.optString("total-hdd-space")?.toLongOrNull() ?: return 0f
    val f = r.optString("free-hdd-space").toLongOrNull() ?: return 0f
    return if (t > 0) ((t - f) * 100f / t).coerceIn(0f, 100f) else 0f
}

@Composable fun RouterSelector(rs:JSONArray,s:JSONObject?,add:()->Unit,select:(JSONObject)->Unit){GlassCard{Row(verticalAlignment=Alignment.CenterVertically){Icon(MmIcons.Router,null,tint=Accent);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("الراوتر الحالي",fontSize=12.sp,color=TextMuted);Text(s?.optString("name")?:"لا يوجد راوتر",fontWeight=FontWeight.Bold)};Button(add){Icon(MmIcons.Add,null);Text("إضافة")}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){for(i in 0 until rs.length()){val r=rs.getJSONObject(i);FilterChip(s?.optString("id")==r.optString("id"),{select(r)},{Text(r.optString("name","Router"),maxLines=1)})}}}}
@Composable fun StatsGrid(r:JSONObject?,d:JSONObject){Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("CPU",r?.optString("cpu-load","-")+"%",MmIcons.Gauge,Modifier.weight(1f));Stat("المستخدمون",d.optInt("users",d.optInt("activeUsers",0)).toString(),MmIcons.Users,Modifier.weight(1f))};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("متصل الآن",d.optInt("active",d.optInt("activeUsers",0)).toString(),MmIcons.Wifi,Modifier.weight(1f));Stat("RAM",memoryText(r),MmIcons.Chip,Modifier.weight(1f))};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("Storage",storageText(r),Icons.Outlined.Storage,Modifier.weight(1f));Stat("Traffic",trafficText(d),Icons.Outlined.DataUsage,Modifier.weight(1f))}}}
@Composable fun Stat(t:String,v:String,i:androidx.compose.ui.graphics.vector.ImageVector,m:Modifier){Card(m,colors=CardDefaults.cardColors(containerColor=Card),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp)){Icon(i,null,tint=Accent);Text(t,fontSize=12.sp,color=TextMuted);Text(v,fontSize=22.sp,fontWeight=FontWeight.Black)}}}
@Composable fun ResourceCard(r:JSONObject?,router:JSONObject){GlassCard{Text(router.optString("name","Router"),fontWeight=FontWeight.Bold);Text(router.optString("host","-"),fontSize=12.sp,color=TextMuted);HorizontalDivider();Text("RouterOS ${r?.optString("version","-")}",fontWeight=FontWeight.Bold);Text("${r?.optString("board-name","-")} • ${r?.optString("architecture-name","-")}",fontSize=12.sp,color=TextMuted);Text("Uptime: ${r?.optString("uptime","-")}",fontSize=12.sp,color=TextMuted)}}
fun memoryText(r:JSONObject?):String{val t=r?.optString("total-memory")?.toLongOrNull();val f=r?.optString("free-memory")?.toLongOrNull();return if(t!=null&&f!=null&&t>0)"${((t-f)*100/t)}%" else "-"}
fun storageText(r:JSONObject?):String{val t=r?.optString("total-hdd-space")?.toLongOrNull();val f=r?.optString("free-hdd-space")?.toLongOrNull();return if(t!=null&&f!=null&&t>0)"${((t-f)*100/t)}%" else "-"}
fun trafficText(d:JSONObject):String{val a=d.optJSONArray("interfaces")?:return "-";var total=0L;for(i in 0 until a.length()){val o=a.optJSONObject(i);total+=o?.optString("rx-byte")?.toLongOrNull()?:0L;total+=o?.optString("tx-byte")?.toLongOrNull()?:0L};return if(total<=0)"-" else if(total>1_000_000_000)String.format("%.1f GB",total/1_000_000_000.0) else if(total>1_000_000)String.format("%.1f MB",total/1_000_000.0) else "${total/1000} KB"}
@Composable fun InterfaceRow(o:JSONObject){val up=o.optString("running")=="true";ItemSurface{SymbolTile(MmIcons.Network,if(up)Signal else MaterialTheme.colorScheme.onSurfaceVariant,size=38.dp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(o.optString("name","-"),style=MaterialTheme.typography.titleMedium,maxLines=1);Text(o.optString("type",""),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};LedDot(if(up)Link else Fault,size=7.dp,live=up);Text(if(up)"UP" else "DOWN",style=MaterialTheme.typography.labelMedium,color=if(up)Link else Fault)}}
@Composable fun UserRow(o:JSONObject){val dis=o.optString("disabled")=="true";ItemSurface{SymbolTile(MmIcons.Person,if(dis)Fault else Link,size=38.dp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(o.optString("name","-"),style=MaterialTheme.typography.titleMedium,maxLines=1);Text(o.optString("profile","default"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};StatusPill(if(dis)"معطل" else "فعال",!dis)}}
@Composable fun EmptyRouterCard(add:()->Unit){GlassCard{SymbolTile(MmIcons.Router,Signal,size=52.dp);Text("لا يوجد راوتر بعد",style=MaterialTheme.typography.titleLarge);Text("اربط راوتر MikroTik بنقرة واحدة من هاتفك، ثم تابع حالته مباشرة.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Button(add,Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(16.dp)){Icon(MmIcons.Add,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("إضافة راوتر")}}}
@Composable fun ErrorCard(s:String){Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer,RoundedCornerShape(16.dp)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){LedDot(Fault,size=7.dp);Spacer(Modifier.width(8.dp));Text(s,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onErrorContainer)}}

@Composable fun RoutersPage(rs:JSONArray,selected:JSONObject?,add:()->Unit,choose:(JSONObject)->Unit,remove:(String)->Unit){LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(top=8.dp,bottom=24.dp)){item{PageHeading("الأسطول","الراوترات","${rs.length()} جهاز MikroTik في مساحة العمل"){FilledTonalButton(add,shape=RoundedCornerShape(14.dp)){Icon(MmIcons.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("إضافة")}}};if(rs.length()==0)item{EmptyRouterCard(add)};items((0 until rs.length()).map{rs.getJSONObject(it)}){r->RouterRow(r,selected?.optString("id")==r.optString("id"),{choose(r)},{remove(r.optString("id"))})}}}
@Composable fun RouterRow(r:JSONObject,sel:Boolean,choose:()->Unit,remove:()->Unit){var confirm by remember{mutableStateOf(false)};ItemSurface(onClick=choose){SymbolTile(MmIcons.Router,if(sel)Signal else MaterialTheme.colorScheme.onSurfaceVariant,size=46.dp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Row(verticalAlignment=Alignment.CenterVertically){Text(r.optString("name","Router"),style=MaterialTheme.typography.titleMedium,maxLines=1,modifier=Modifier.weight(1f,false));if(sel){Spacer(Modifier.width(8.dp));StatusPill("النشط")}};Text("${r.optString("host","-")}:${r.optInt("port",8728)}  ·  ${if(r.optBoolean("tls"))"API-SSL" else "API"}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton({confirm=true}){Icon(MmIcons.Trash,"حذف",Modifier.size(20.dp),tint=Fault)}};if(confirm)AlertDialog(onDismissRequest={confirm=false},shape=RoundedCornerShape(24.dp),title={Text("حذف الراوتر؟")},text={Text("سيُزال ${r.optString("name","Router")} من مساحة العمل. لن يتغير شيء على الراوتر نفسه.")},confirmButton={TextButton({confirm=false;remove()}){Text("حذف",color=Fault)}},dismissButton={TextButton({confirm=false}){Text("إلغاء")}})}

@Composable
fun ProfilesPage(api: Api, router: JSONObject?) {
    var profiles by remember { mutableStateOf(JSONArray()) }
    var plans by remember { mutableStateOf(JSONArray()) }
    var error by remember { mutableStateOf<String?>(null) }
    var show by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { if (router != null) try { val id=router.optString("id"); profiles=JSONObject(api.get("/api/routers/$id/hotspot-profiles")).optJSONArray("profiles")?:JSONArray(); plans=JSONObject(api.get("/api/routers/$id/plans")).optJSONArray("plans")?:JSONArray() } catch(e:Exception){ error=e.message } } }
    LaunchedEffect(router?.optString("id")) { load() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Profiles & Plans",style=MaterialTheme.typography.headlineMedium)
        Text("Profile = سلوك الشبكة • Plan = المنتج والسعر", color=TextMuted)
        error?.let { ErrorCard(it) }
        if (router == null) EmptyCard("اختر راوتر أولاً") else {
            Button({ show=true }, Modifier.fillMaxWidth()) { Icon(MmIcons.Add,null); Text("إضافة Profile + Plan") }
            RouterProfilesSection(api, router) { load() }
            Text("HotSpot Profiles",style=MaterialTheme.typography.titleLarge)
            for(i in 0 until profiles.length()) { val p=profiles.getJSONObject(i); GlassCard { Text(p.optString("name"),fontWeight=FontWeight.Bold); Text("Rate: ${p.optString("rateLimit","-")} • Session: ${p.optString("sessionTimeout","-")} • Shared: ${p.optInt("sharedUsers",1)}",fontSize=12.sp,color=TextMuted) } }
            Text("Plans",style=MaterialTheme.typography.titleLarge)
            for(i in 0 until plans.length()) { val p=plans.getJSONObject(i); GlassCard { Text(p.optString("name"),fontWeight=FontWeight.Bold); Text("${p.optDouble("price",0.0)} ${p.optString("currency","XOF")} • ${p.optString("profileName",p.optString("profile_name","-"))}",color=Accent) } }
        }
    }
    if(show && router!=null) ProfileDialog(api,router,{show=false;load()},{show=false})
}

@Composable fun ProfileDialog(api:Api,r:JSONObject,done:()->Unit,cancel:()->Unit){var name by remember{mutableStateOf("")};var duration by remember{mutableStateOf("60")};var rate by remember{mutableStateOf("")};var session by remember{mutableStateOf("")};var shared by remember{mutableStateOf("1")};var plan by remember{mutableStateOf("")};var price by remember{mutableStateOf("0")};var busy by remember{mutableStateOf(false)};var err by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest=cancel,title={Text("Profile + Plan")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){AppField(name,{name=it},"اسم Profile",MmIcons.Gauge);AppField(duration,{duration=it},"المدة بالدقائق",MmIcons.Timer);AppField(rate,{rate=it},"Rate Limit (مثال 2M/2M)",MmIcons.Gauge);AppField(session,{session=it},"Session Timeout",MmIcons.Timer);AppField(shared,{shared=it},"Shared Users",MmIcons.Users);AppField(plan,{plan=it},"اسم Plan",Icons.Outlined.Inventory2);AppField(price,{price=it},"السعر XOF",MmIcons.Money);err?.let{Text(it,color=Red)}}},confirmButton={Button({scope.launch{busy=true;try{val id=r.optString("id");api.post("/api/routers/$id/hotspot-profiles",JSONObject().put("name",name).put("durationMinutes",duration.toIntOrNull()?:0).put("rateLimit",rate).put("sessionTimeout",session).put("sharedUsers",shared.toIntOrNull()?:1).toString());api.post("/api/routers/$id/plans",JSONObject().put("profileName",name).put("name",if(plan.isBlank())name else plan).put("price",price.toDoubleOrNull()?:0).put("currency","XOF").toString());done()}catch(e:Exception){err=e.message}finally{busy=false}}},enabled=!busy&&name.isNotBlank()&&price.isNotBlank()){Text(if(busy)"جاري الحفظ…" else "حفظ")}},dismissButton={TextButton(cancel){Text("إلغاء")}})}

@Composable fun CardsStudioPage(api:Api,router:JSONObject?,activity:MainActivity){var plans by remember{mutableStateOf(JSONArray())};var selectedPlan by remember{mutableStateOf<JSONObject?>(null)};var portal by remember{mutableStateOf("")};var ssid by remember{mutableStateOf("")};var wifiOpen by remember{mutableStateOf(false)};var passwordLength by remember{mutableStateOf("8")};var letters by remember{mutableStateOf("0")};var mode by remember{mutableStateOf("userpass")};var count by remember{mutableStateOf("10")};var prefix by remember{mutableStateOf("KMX")};var digits by remember{mutableStateOf("6")};var result by remember{mutableStateOf<JSONArray?>(null)};var batches by remember{mutableStateOf<JSONArray?>(null)};var err by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var preview by remember{mutableStateOf<JSONObject?>(null)};val scope=rememberCoroutineScope();LaunchedEffect(router?.optString("id")){if(router!=null)scope.launch{try{plans=JSONObject(api.get("/api/routers/${router.optString("id")}/plans")).optJSONArray("plans")?:JSONArray()}catch(e:Exception){err=e.message}}};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Card Studio",style=MaterialTheme.typography.headlineMedium);Text("QR + Barcode + Preflight + توليد + Batch Center — بدون اشتراك",color=TextMuted);if(router==null)EmptyCard("اختر راوتر أولاً") else {GlassCard{Text("بيانات الكروت",fontWeight=FontWeight.Bold);AppField(portal,{portal=it},"رابط HotSpot الحقيقي",MmIcons.Link);AppField(ssid,{ssid=it},"اسم شبكة الواي فاي SSID",MmIcons.Wifi);Row(verticalAlignment=Alignment.CenterVertically){Switch(wifiOpen,{wifiOpen=it});Text("إنشاء QR اتصال Wi-Fi مفتوح",Modifier.weight(1f));Text(if(wifiOpen) "مفعّل" else "مغلق",fontSize=11.sp,color=TextMuted)};Text("طريقة الكروت",fontSize=13.sp,color=TextMuted);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(mode=="userpass",{mode="userpass"},{Text("1) يوزر + باسورد + QR")});FilterChip(mode=="pin",{mode="pin";if((digits.toIntOrNull()?:0)<8)digits="8"},{Text("2) كود فقط + QR")})};if(mode=="pin")Text("الكود فقط: صفحة login-code.html، ويلزم 8 خانات على الأقل",fontSize=11.sp,color=Amber);AppField(count,{count=it},"عدد الكروت",MmIcons.Tag);AppField(prefix,{prefix=it},"Prefix",Icons.Outlined.Tag);AppField(digits,{digits=it.filter(Char::isDigit)},"عدد أرقام اسم المستخدم",MmIcons.Tag);AppField(letters,{letters=it.filter(Char::isDigit)},"عدد حروف اسم المستخدم",Icons.Outlined.Translate);AppField(passwordLength,{passwordLength=it.filter(Char::isDigit)},"طول كلمة المرور",Icons.Outlined.Password);Text("الخطة",fontSize=13.sp,color=TextMuted);if(plans.length()==0)Text("أنشئ Plan أولاً من Profiles",color=Amber);for(i in 0 until plans.length()){val p=plans.getJSONObject(i);FilterChip(selectedPlan?.optString("id")==p.optString("id"),{selectedPlan=p},{Text("${p.optString("name")} • ${p.optDouble("price")} ${p.optString("currency","XOF")}")})};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({scope.launch{busy=true;try{val id=router.optString("id");api.post("/api/routers/$id/qr-preflight",JSONObject().put("portalUrl",portal).toString());val raw=api.post("/api/routers/$id/cards/preflight",JSONObject().put("count",count.toIntOrNull()?:1).put("planId",selectedPlan?.optString("id")).put("prefix",prefix).put("usernameDigits",digits.toIntOrNull()?:6).put("usernameLetters",letters.toIntOrNull()?:0).put("passwordLength",passwordLength.toIntOrNull()?:8).put("portalUrl",portal).put("passwordMode",mode).toString());err="Preflight جاهز: ${JSONObject(raw).optBoolean("ready",true)}"}catch(e:Exception){err=e.message}}},enabled=!busy&&selectedPlan!=null&&portal.isNotBlank()){Text("فحص ذكي")};Button({scope.launch{busy=true;err=null;try{val id=router.optString("id");val raw=api.post("/api/routers/$id/cards/generate",JSONObject().put("count",count.toIntOrNull()?:1).put("planId",selectedPlan?.optString("id")).put("prefix",prefix).put("usernameDigits",digits.toIntOrNull()?:6).put("usernameLetters",letters.toIntOrNull()?:0).put("passwordLength",passwordLength.toIntOrNull()?:8).put("portalUrl",portal).put("ssid",ssid.trim()).put("wifiOpen",wifiOpen).put("passwordMode",mode).toString());result=JSONObject(raw).optJSONArray("cards")}catch(e:Exception){err=e.message}finally{busy=false}}},enabled=!busy&&selectedPlan!=null&&portal.isNotBlank()){Text(if(busy)"جاري…" else "توليد")}}};err?.let{Text(it,color=if(it.startsWith("Preflight"))Green else Red)};result?.let{arr->Text("تم إنشاء ${arr.length()} كرت",style=MaterialTheme.typography.titleLarge);for(i in 0 until minOf(arr.length(),50)){val c=arr.getJSONObject(i);Card(Modifier.fillMaxWidth().clickable{preview=c},colors=CardDefaults.cardColors(containerColor=Card),shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(MmIcons.Scan,null,tint=Accent);Column(Modifier.weight(1f)){Text(c.optString("username"),fontWeight=FontWeight.Bold);Text("${c.optString("password")} • ${c.optString("profile")}",fontSize=12.sp,color=TextMuted)};Text("${c.optDouble("price")} ${c.optString("currency","XOF")}",color=Accent)}}}};Button({scope.launch{try{val rid=router?.optString("id")?:"";val a=JSONObject(api.post("/api/routers/$rid/cards/audit","{}"));val c=a.optJSONObject("counts");val iss=a.optJSONObject("issues");err="Preflight فحص المزامنة: "+(if(a.optBoolean("healthy"))"سليم" else "توجد فروقات")+" • غير مستخدم ${c?.optInt("unused")} • مستخدم ${c?.optInt("used")} • ناقص على الراوتر ${iss?.optJSONArray("missingOnRouter")?.length()} • زائد ${iss?.optJSONArray("orphanOnRouter")?.length()}"}catch(e:Exception){err=e.message}}},Modifier.fillMaxWidth(),enabled=router!=null){Text("فحص ذكي للمزامنة مع MikroTik")};Button({scope.launch{try{batches=JSONObject(api.get("/api/card-batches")).optJSONArray("batches")}catch(e:Exception){err=e.message}}},Modifier.fillMaxWidth()){Text("فتح Batch Center")};batches?.let{Text("آخر الدُفعات",style=MaterialTheme.typography.titleLarge);for(i in 0 until minOf(it.length(),20)){val b=it.getJSONObject(i);GlassCard{Text(b.optString("plan_name",b.optString("planName","Batch")),fontWeight=FontWeight.Bold);Text("${b.optInt("total")} كرت • متاح ${b.optInt("available")} • مباع ${b.optInt("sold")}",color=TextMuted)}}}};preview?.let{CardPreviewDialog(it,{preview=null},{bitmap->activity.printBitmap(bitmap)})}}}

@Composable
fun CardPreviewDialog(c: JSONObject, close: () -> Unit, print: (Bitmap) -> Unit) {
    val qr = c.optString("qrContent")
    val q = remember(qr) { generateCode(qr, BarcodeFormat.QR_CODE, 520, 520) }
    val wq = c.optString("wifiQr")
    val w = remember(wq) { if (wq.isBlank()) null else generateCode(wq, BarcodeFormat.QR_CODE, 520, 520) }
    val b = remember(c.optString("username")) { generateCode(c.optString("username"), BarcodeFormat.CODE_128, 700, 150) }
    var zoom by remember { mutableFloatStateOf(1f) }
    AlertDialog(onDismissRequest = close, title = { Text("معاينة الكرت — تحكم كامل") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ZoomOut, null, tint = TextMuted)
                Slider(value = zoom, onValueChange = { zoom = it }, valueRange = .65f..1.8f, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ZoomIn, null, tint = Accent)
                Text("${(zoom * 100).toInt()}%", fontSize = 11.sp, color = TextMuted)
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color.Black), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.optString("profile", c.optString("plan", "WiFi Access")), fontWeight = FontWeight.Bold, color = Color.Black)
                    HorizontalDivider(color = Color.Black)
                    val pinMode = c.optString("passwordMode") == "pin"
                    Text("${if (pinMode) "الكود" else "اسم المستخدم"}  ${c.optString("username")}", color = Color.Black)
                    if (!pinMode) Text("كلمة المرور  ${c.optString("password")}", color = Color.Black)
                    w?.let { Text("1) امسح للاتصال بالشبكة", fontSize = 10.sp, color = Color.Black); Image(it.asImageBitmap(), null, Modifier.size(130.dp * zoom)) }
                    q?.let { Text(if (w != null) "2) امسح لتسجيل الدخول" else "امسح لتسجيل الدخول", fontSize = 10.sp, color = Color.Black); Image(it.asImageBitmap(), null, Modifier.size(150.dp * zoom)) }
                    b?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(45.dp * zoom)) }
                    Text("QR وBarcode حقيقيان من بيانات الكرت", fontSize = 10.sp, color = Color.DarkGray)
                }
            }
        }
    }, confirmButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ composeCardBitmap(c)?.let { print(it) } }) { Icon(MmIcons.Print, null); Spacer(Modifier.width(5.dp)); Text("طباعة بالحجم الحقيقي") }
            TextButton(close) { Text("إغلاق") }
        }
    })
}

fun generateCode(value:String,format:BarcodeFormat,w:Int,h:Int):Bitmap?{return try{val hints=mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,EncodeHintType.MARGIN to 1);val m:BitMatrix=MultiFormatWriter().encode(value,format,w,h,hints);val bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);for(x in 0 until w)for(y in 0 until h)bmp.setPixel(x,y,if(m[x,y])android.graphics.Color.BLACK else android.graphics.Color.WHITE);bmp}catch(_:Exception){null}}

fun MainActivity.printBitmap(bitmap:Bitmap){try{androidx.print.PrintHelper(this).apply{scaleMode=androidx.print.PrintHelper.SCALE_MODE_FIT}.printBitmap("MICRO-MAX Card",bitmap)}catch(e:Exception){Toast.makeText(this,e.message?:"تعذر فتح الطباعة",Toast.LENGTH_LONG).show()}}

fun composeCardBitmap(c:JSONObject):Bitmap?{val qr=generateCode(c.optString("qrContent"),BarcodeFormat.QR_CODE,520,520)?:return null;val bar=generateCode(c.optString("username"),BarcodeFormat.CODE_128,700,150)?:return null;val out=Bitmap.createBitmap(900,900,Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(out);canvas.drawColor(android.graphics.Color.WHITE);val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);p.color=android.graphics.Color.BLACK;p.typeface=android.graphics.Typeface.DEFAULT_BOLD;p.textSize=30f;canvas.drawText(c.optString("profile",c.optString("plan","WiFi Access")),40f,65f,p);p.strokeWidth=2f;canvas.drawRect(40f,84f,860f,87f,p);p.typeface=android.graphics.Typeface.DEFAULT;p.textSize=24f;val pinM=c.optString("passwordMode")=="pin";canvas.drawText("${if(pinM)"الكود" else "اسم المستخدم"}  ${c.optString("username")}",40f,135f,p);if(!pinM)canvas.drawText("كلمة المرور  ${c.optString("password")}",40f,175f,p);val wqr=c.optString("wifiQr").let{if(it.isBlank())null else generateCode(it,BarcodeFormat.QR_CODE,520,520)};if(wqr!=null){p.textSize=22f;canvas.drawText("1) اتصال بالشبكة",60f,205f,p);canvas.drawText("2) تسجيل الدخول",480f,205f,p);canvas.drawBitmap(wqr,null,android.graphics.Rect(60,220,420,580),p);canvas.drawBitmap(qr,null,android.graphics.Rect(480,220,840,580),p)}else{canvas.drawBitmap(qr,null,android.graphics.Rect(190,220,710,740),p)};canvas.drawBitmap(bar,null,android.graphics.Rect(100,765,800,850),p);return out}


@Composable fun SalesPage(api:Api){var sales by remember{mutableStateOf(JSONArray())};var cards by remember{mutableStateOf(JSONArray())};var err by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope();fun load(){scope.launch{try{sales=JSONObject(api.get("/api/sales")).optJSONArray("sales")?:JSONArray();cards=JSONObject(api.get("/api/cards")).optJSONArray("cards")?:JSONArray()}catch(e:Exception){err=e.message}}};LaunchedEffect(Unit){load()};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("المبيعات",style=MaterialTheme.typography.headlineMedium);Text("بيع الكرت نقداً الآن أو تتبع حالة الدفع الإلكتروني الاختياري",color=TextMuted);err?.let{ErrorCard(it)};Text("كروت جاهزة للبيع",style=MaterialTheme.typography.titleLarge);for(i in 0 until minOf(cards.length(),40)){val c=cards.getJSONObject(i);if(c.optString("status")=="available"){GlassCard{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(c.optString("username"),fontWeight=FontWeight.Bold);Text("${c.optString("plan_name",c.optString("profile"))} • ${c.optDouble("price")} ${c.optString("price_currency","XOF")}",fontSize=12.sp,color=TextMuted)};Button({scope.launch{busy=true;try{api.post("/api/sales",JSONObject().put("cardId",c.optString("id")).put("paymentMethod","cash").toString());load()}catch(e:Exception){err=e.message}finally{busy=false}}},enabled=!busy){Text("بيع نقداً")}}}}};Text("آخر المبيعات",style=MaterialTheme.typography.titleLarge);for(i in 0 until minOf(sales.length(),30)){val o=sales.getJSONObject(i);GlassCard{Text(o.optString("card_username","-"),fontWeight=FontWeight.Bold);Text("${o.optDouble("amount")} • ${o.optString("payment_method")} • ${o.optString("payment_status")}",color=TextMuted)}}}}

@Composable fun ReportsPage(api:Api){var data by remember{mutableStateOf(JSONArray())};var err by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope();LaunchedEffect(Unit){scope.launch{try{data=JSONObject(api.get("/api/reports/sales")).optJSONArray("rows")?:JSONArray()}catch(e:Exception){err=e.message}}};LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("التقارير",style=MaterialTheme.typography.headlineMedium);Text("تقارير المبيعات والإيرادات اليومية",color=TextMuted)};err?.let{item{ErrorCard(it)}};items((0 until data.length()).map{data.getJSONObject(it)}){o->GlassCard{Text(o.optString("day","-"),fontWeight=FontWeight.Bold);Text("المبيعات: ${o.optInt("sales")} • الإيراد: ${o.optDouble("revenue")} • مدفوع: ${o.optInt("paid")}",color=TextMuted)}}}}

@Composable
fun SecurityPage(api: Api) {
    var tab by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf(JSONArray()) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    fun load(){ scope.launch { try { val o=JSONObject(api.get(if(tab==0) "/api/users" else "/api/audit")); data=o.optJSONArray(if(tab==0) "users" else "logs")?:JSONArray(); err=null } catch(e:Exception){err=e.message} } }
    LaunchedEffect(tab){load()}
    Column(Modifier.fillMaxSize().padding(16.dp)){
        Text("الأمان والصلاحيات",style=MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ FilterChip(tab==0,{tab=0},{Text("المستخدمون")}); FilterChip(tab==1,{tab=1},{Text("سجل التدقيق")}) }
        err?.let{ErrorCard(it)}
        LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(top=12.dp)){items((0 until data.length()).map{data.getJSONObject(it)}){o->GlassCard{Text(if(tab==0)o.optString("email","-") else o.optString("action","-"),fontWeight=FontWeight.Bold);Text(if(tab==0)o.optString("role","staff") else "${o.optString("entity")} • ${o.optString("created_at")}",color=TextMuted)}}}
    }
}

@Composable
fun NetworkPage(api: Api, router: JSONObject?) {
    var tab by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf(JSONArray()) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    val names=listOf("Interfaces","DHCP","ARP","DNS","IP","Routes","Firewall","Queues","Logs","IP Bindings")
    val paths=listOf("interfaces","dhcp-leases","arp","dns-static","ip-addresses","routes","firewall","queues","logs","ip-bindings")
    LaunchedEffect(router?.optString("id"),tab){ if(router!=null) scope.launch{ try{val o=JSONObject(api.get("/api/routers/${router.optString("id")}/network/${paths[tab]}"));data=o.optJSONArray("items")?:JSONArray();err=null}catch(e:Exception){err=e.message}} }
    Column(Modifier.fillMaxSize().padding(12.dp)){
        Text("أدوات الشبكة",fontSize=27.sp,fontWeight=FontWeight.Black); Text(router?.optString("name")?:"اختر راوتر",color=TextMuted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){names.forEachIndexed{i,n->FilterChip(tab==i,{tab=i},{Text(n,fontSize=11.sp)})}}
        err?.let{ErrorCard(it)}
        LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=10.dp)){items((0 until data.length()).map{data.getJSONObject(it)}){o->GlassCard{val title=o.optString("name",o.optString("address",o.optString("dst-address",o.optString("message","-"))));Text(title,fontWeight=FontWeight.Bold);Text(o.toString().removePrefix("{").removeSuffix("}").take(260),fontSize=11.sp,color=TextMuted)}}}
    }
}


@Composable
fun TerminalPage(api: Api, router: JSONObject?) {
    var command by remember { mutableStateOf("/system/resource/print") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Terminal RouterOS", fontSize = 27.sp, fontWeight = FontWeight.Black)
        Text(router?.optString("name") ?: "اختر راوتر", color = TextMuted)
        if (router == null) EmptyCard("اختر راوتر أولاً") else {
            OutlinedTextField(command, { command = it }, label = { Text("أمر RouterOS") }, modifier = Modifier.fillMaxWidth(), minLines = 2, colors = fieldColors())
            Text("مثال: /system/resource/print أو /ip/hotspot/user/print", color = TextMuted, fontSize = 11.sp)
            Button({
                scope.launch {
                    busy = true; err = null
                    try {
                        val raw = api.post("/api/routers/${router.optString("id")}/terminal", JSONObject().put("command", command).toString())
                        output = JSONObject(raw).optJSONArray("result")?.toString(2) ?: raw
                    } catch(e: Exception) { err = e.message } finally { busy = false }
                }
            }, enabled = !busy && command.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "جاري التنفيذ…" else "تنفيذ الأمر") }
            err?.let { ErrorCard(it) }
            if (output.isNotBlank()) GlassCard { Text("النتيجة", fontWeight = FontWeight.Bold); Text(output, fontSize = 11.sp, color = TextMuted) }
        }
    }
}

@Composable
fun HotspotEditorPage(api: Api, router: JSONObject?) {
    var content by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    var backups by remember { mutableStateOf(JSONArray()) }
    var busy by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    LaunchedEffect(router?.optString("id")) {
        if(router!=null) scope.launch { try {
            val id=router.optString("id")
            val o=JSONObject(api.get("/api/routers/$id/hotspot-login"))
            content=o.optString("content","")
            backups=JSONObject(api.get("/api/routers/$id/hotspot-login/backups")).optJSONArray("backups")?:JSONArray()
        } catch(e:Exception){ validation=e.message } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("HotSpot login.html",fontSize=27.sp,fontWeight=FontWeight.Black)
        Text(router?.optString("name")?:"اختر راوتر",color=TextMuted)
        if(router==null) EmptyCard("اختر راوتر أولاً") else {
            OutlinedTextField(content,{content=it},label={Text("مصدر login.html")},modifier=Modifier.fillMaxWidth().height(420.dp),textStyle=LocalTextStyle.current.copy(fontSize=11.sp),colors=fieldColors())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button({ scope.launch { busy=true; try { val r=api.post("/api/routers/${router.optString("id")}/hotspot-login/validate",JSONObject().put("content",content).toString()); validation=JSONObject(r).toString(2) } catch(e:Exception){validation=e.message} finally{busy=false} } }) { Text("تحقق") }
                Button({ scope.launch { busy=true; try { api.post("/api/routers/${router.optString("id")}/hotspot-login/publish",JSONObject().put("content",content).toString()); validation="تم النشر مع إنشاء Backup تلقائي" } catch(e:Exception){validation=e.message} finally{busy=false} } },enabled=!busy) { Text("نشر") }
            }
            validation?.let { GlassCard { Text(it,fontSize=11.sp) } }
            Text("Backups",style=MaterialTheme.typography.titleLarge)
            for(i in 0 until backups.length()) {
                val b=backups.getJSONObject(i)
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Card)) {
                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(b.optString("name"),Modifier.weight(1f),fontSize=11.sp)
                        TextButton({ scope.launch { try { api.post("/api/routers/${router.optString("id")}/hotspot-login/restore",JSONObject().put("backupName",b.optString("name")).toString()); validation="تمت الاستعادة" } catch(e:Exception){validation=e.message} } }) { Text("استعادة") }
                    }
                }
            }
        }
    }
}

@Composable fun SettingsPage(dark:Boolean,toggle:()->Unit,apiBase:String,onApiChange:(String)->Unit,onLogout:()->Unit){
    var newApi by remember(apiBase){mutableStateOf(apiBase)}
    val cs=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        PageHeading("الحساب","الإعدادات","المظهر والخادم والجلسة")
        GlassCard{
            Row(verticalAlignment=Alignment.CenterVertically){SymbolTile(MmIcons.Moon,Violet,size=40.dp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("الوضع الداكن",style=MaterialTheme.typography.titleMedium);Text("يريح العين في الإضاءة المنخفضة",style=MaterialTheme.typography.bodySmall,color=cs.onSurfaceVariant)};Switch(dark,{toggle()})}
        }
        GlassCard{
            Row(verticalAlignment=Alignment.CenterVertically){SymbolTile(MmIcons.Cloud,Signal,size=40.dp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("خادم MICRO-MAX",style=MaterialTheme.typography.titleMedium);Text("يجب أن يبدأ الرابط بـ https",style=MaterialTheme.typography.bodySmall,color=cs.onSurfaceVariant)}}
            OutlinedTextField(newApi,{newApi=it},label={Text("رابط الخادم")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=fieldColors())
            Button(onClick={val clean=newApi.trim().trimEnd('/');if(clean.startsWith("http://")||clean.startsWith("https://"))onApiChange(clean)},modifier=Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(16.dp)){Icon(MmIcons.Check,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("حفظ واختبار الخادم")}
        }
        GlassCard{
            Row(verticalAlignment=Alignment.CenterVertically){NetworkBadge(size=40.dp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("MICRO-MAX",style=MaterialTheme.typography.titleMedium);Text("إدارة MikroTik عبر RouterOS API / API-SSL",style=MaterialTheme.typography.bodySmall,color=cs.onSurfaceVariant)}}
            HorizontalDivider(color=cs.outlineVariant)
            listOf("عدة راوترات وربط بنقرة من الهاتف","Profiles وباقات وأسعار","كروت QR تتصل تلقائيًا + طباعة A4","متجر قوالب ومحرر HotSpot","مبيعات وتقارير وأمان وتدقيق").forEach{Row(verticalAlignment=Alignment.CenterVertically){Icon(MmIcons.Check,null,Modifier.size(16.dp),tint=Link);Spacer(Modifier.width(8.dp));Text(it,style=MaterialTheme.typography.bodySmall,color=cs.onSurfaceVariant)}}
        }
        OutlinedButton(onClick=onLogout,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Fault.copy(alpha=.6f))){Icon(MmIcons.Logout,null,Modifier.size(20.dp),tint=Fault);Spacer(Modifier.width(8.dp));Text("تسجيل الخروج",color=Fault)}
    }
}

@Composable
fun AddRouterDialog(api:Api,onSaved:()->Unit,onCancel:()->Unit,initialMode:String="local"){val ctx=LocalContext.current;
    var step by remember{mutableIntStateOf(0)};var mode by remember{mutableStateOf(initialMode)};var name by remember{mutableStateOf("")};var host by remember{mutableStateOf(if(initialMode=="local")"192.168.88.1" else "")};var port by remember{mutableStateOf(if(initialMode=="public")"8729" else "8728")};var user by remember{mutableStateOf("admin")};var pass by remember{mutableStateOf("")};var tls by remember{mutableStateOf(initialMode=="public")};var packageName by remember{mutableStateOf("اقتصادية")};var download by remember{mutableStateOf("2M")};var upload by remember{mutableStateOf("1M")};var duration by remember{mutableStateOf("60")};var packagePrice by remember{mutableStateOf("500")};var sharedUsers by remember{mutableStateOf("1")};var busy by remember{mutableStateOf(false)};var diagnostic by remember{mutableStateOf<String?>(null)};var discovered by remember{mutableStateOf<List<String>>(emptyList())};var tested by remember{mutableStateOf(false)};var err by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
    fun payload()=JSONObject().put("name",name.trim()).put("host",host.trim()).put("port",port.toIntOrNull()?:8728).put("username",user.trim()).put("password",pass).put("tls",tls)
    val canContinue = when(step){0 -> name.isNotBlank(); 1 -> host.isNotBlank()&&user.isNotBlank()&&pass.isNotBlank(); 2 -> packageName.isNotBlank()&&download.isNotBlank()&&upload.isNotBlank()&&duration.toIntOrNull()!=null; else -> tested}
    AlertDialog(onDismissRequest=onCancel, containerColor=MaterialTheme.colorScheme.surface, shape=RoundedCornerShape(28.dp), title={
        ChassisPanel(Modifier.fillMaxWidth(),radius=22.dp){ Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){ NetworkBadge(size=46.dp); Spacer(Modifier.width(12.dp)); Column{ Text("إضافة راوتر",style=MaterialTheme.typography.titleLarge,color=Color.White); Row(verticalAlignment=Alignment.CenterVertically){LedDot(Link,size=5.dp,live=true);Text("MikroTik · RouterOS",style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=.65f))} } } }
    },text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("اربط شبكتك وابدأ الإدارة الذكية",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onSurface)
        Text("إعداد آمن من أربع مراحل — كلمة المرور تُرسل للخادم فقط ولا تظهر في لوحة التطبيق.",fontSize=12.sp,color=TextMuted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("هوية","اتصال","باقة","مراجعة").forEachIndexed{i,label->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(if(i<=step)Accent else MaterialTheme.colorScheme.surfaceVariant));Text(label,fontSize=9.sp,color=if(i<=step)Accent else TextMuted,fontWeight=FontWeight.Bold)}}}
        Text("الخطوة ${step+1} من 4",fontSize=11.sp,color=Accent,fontWeight=FontWeight.Bold)
        when(step){
            0->{Text("سمِّ الراوتر",style=MaterialTheme.typography.titleMedium);Text("سيظهر هذا الاسم في لوحة التحكم وقائمة الراوترات.",fontSize=12.sp,color=TextMuted);AppField(name,{name=it},"اسم واضح، مثل الفرع الرئيسي",MmIcons.Tag)}
            1->{Text("كيف سيتصل التطبيق؟",style=MaterialTheme.typography.titleMedium);Text("اختر نوع الشبكة ثم أدخل بيانات RouterOS. لا يتم حفظ كلمة المرور إلا داخل الخادم.",fontSize=12.sp,color=TextMuted);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=mode=="local",onClick={mode="local";if(host.isBlank())host="192.168.88.1"},label={Text("شبكة محلية")},leadingIcon={Icon(MmIcons.Home,null)});FilterChip(selected=mode=="public",onClick={mode="public";if(host=="192.168.88.1")host=""},label={Text("عنوان عام")},leadingIcon={Icon(MmIcons.Cloud,null)})};if(mode=="local"){Text("تنبيه: هذا الخيار يعمل فقط إذا كان الـ Backend نفسه داخل شبكة الراوتر. خادم Render لا يصل إلى 192.168.x.x. للراوتر البعيد ارجع واختر Agent.",fontSize=11.sp,color=Activity);OutlinedButton(onClick={scope.launch{diagnostic="جاري اكتشاف MikroTik من Backend…";try{val o=JSONObject(api.post("/api/routers/discover",JSONObject().put("network","192.168.88.0/24").toString()));val a=o.optJSONArray("routers");discovered=(0 until (a?.length()?:0)).map{a!!.getJSONObject(it).optString("host")+":"+a.getJSONObject(it).optInt("port")};diagnostic=if(discovered.isEmpty())"لم يتم العثور على API في 192.168.88.0/24" else "تم العثور على ${discovered.size} جهاز MikroTik"}catch(e:Exception){diagnostic=routerConnectionMessage(e.message?:"فشل الاكتشاف")}}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Radar,null);Spacer(Modifier.width(6.dp));Text("اكتشاف MikroTik تلقائيًا")};discovered.forEach{found->FilterChip(selected=host==found.substringBefore(":"),onClick={host=found.substringBefore(":");port=found.substringAfter(":");tls=port=="8729"},label={Text("راوتر $found")},leadingIcon={Icon(MmIcons.Router,null)})}};AppField(host,{host=it},if(mode=="local")"IP الراوتر، مثال 192.168.88.1" else "Hostname أو IP عام",MmIcons.Cloud);Text("المنفذ",fontSize=12.sp,color=TextMuted,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=port=="8729",onClick={port="8729";tls=true},label={Text("8729 • API-SSL")});FilterChip(selected=port=="8728",onClick={port="8728";tls=false},label={Text("8728 • API")})};OutlinedTextField(user,{user=it},label={Text("اسم المستخدم")},leadingIcon={Icon(MmIcons.Person,null)},keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.None,autoCorrectEnabled=false,keyboardType=KeyboardType.Ascii),modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp),colors=fieldColors());OutlinedTextField(pass,{pass=it},label={Text("كلمة مرور MikroTik")},leadingIcon={Icon(MmIcons.Lock,null)},keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.None,autoCorrectEnabled=false,keyboardType=KeyboardType.Password),shape=RoundedCornerShape(16.dp),visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true,colors=fieldColors());Row(verticalAlignment=Alignment.CenterVertically){Checkbox(tls,{tls=it;port=if(it)"8729" else "8728"});Text("تشفير API-SSL موصى به للإنتاج")};OutlinedButton(onClick={scope.launch{diagnostic="جاري الفحص من Backend…";try{diagnostic=formatRouterDiagnostic(JSONObject(api.post("/api/routers/diagnose",payload().toString())))}catch(e:Exception){diagnostic=routerConnectionMessage(e.message?:"فشل التشخيص")}}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.NetworkCheck,null);Spacer(Modifier.width(6.dp));Text("تشخيص الشبكة من Backend")};diagnostic?.let{Text(it,fontSize=11.sp,color=if(it.startsWith("نجح"))Green else Amber)};if(tested) Text("تم اختبار الاتصال بنجاح",color=Green,fontWeight=FontWeight.Bold)}
            2->{Text("أنشئ أول باقة",style=MaterialTheme.typography.titleMedium);Text("سيتم إنشاء Profile في MikroTik وPlan للبيع داخل MICRO-MAX.",fontSize=12.sp,color=TextMuted);AppField(packageName,{packageName=it},"اسم الباقة، مثل اقتصادية أو VIP",MmIcons.Tag);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){AppField(download,{download=it},"Download، مثال 2M",MmIcons.Download)};Box(Modifier.weight(1f)){AppField(upload,{upload=it},"Upload، مثال 1M",Icons.Outlined.Upload)}};Text("سرعات جاهزة",fontSize=12.sp,color=TextMuted,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("1M/512K","2M/1M","5M/2M","10M/5M").forEach{speed->FilterChip(selected=download+"/"+upload==speed,onClick={val p=speed.split("/");download=p[0];upload=p[1]},label={Text(speed)})}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){AppField(duration,{duration=it.filter(Char::isDigit)},"المدة بالدقائق",MmIcons.Timer)};Box(Modifier.weight(1f)){AppField(packagePrice,{packagePrice=it.filter(Char::isDigit)},"السعر XOF",MmIcons.Money)}};AppField(sharedUsers,{sharedUsers=it.filter(Char::isDigit)},"عدد الأجهزة المسموحة",MmIcons.Users)}
            else->{Text("جاهز للحفظ",style=MaterialTheme.typography.titleMedium);Text("راجع الاتصال والباقة قبل إنشاء الإعدادات.",fontSize=12.sp,color=TextMuted);GlassCard{Text(name.ifBlank{"بدون اسم"},fontSize=17.sp,fontWeight=FontWeight.Bold);Text("${host.ifBlank{"-"}}:${port.ifBlank{"8729"}}",color=Accent);Text("${user.ifBlank{"-"}} • ${if(tls)"API-SSL آمن" else "API"}",color=TextMuted);HorizontalDivider();Text("الباقة: $packageName",fontWeight=FontWeight.Bold);Text("سرعة: $download تنزيل / $upload رفع • مدة: $duration دقيقة",color=Accent);Text("السعر: $packagePrice XOF • أجهزة: $sharedUsers",color=TextMuted);Text("سيتم اختبار الراوتر ثم إنشاء Profile وPlan تلقائيًا.",fontSize=12.sp,color=TextMuted)}}
        }
        err?.let{ErrorCard(routerConnectionMessage(it))}
    }},confirmButton={
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(step>0)OutlinedButton({step--;err=null}){Text("رجوع")}
            Button({scope.launch{busy=true;err=null;try{if(step==1){api.post("/api/routers/test",payload().toString());tested=true;step++}else if(step==3){val created=JSONObject(api.post("/api/routers",payload().toString()));val id=created.optString("id");try{val rate="$download/$upload";api.post("/api/routers/$id/hotspot-profiles",JSONObject().put("name",packageName).put("durationMinutes",duration.toIntOrNull()?:60).put("rateLimit",rate).put("sharedUsers",sharedUsers.toIntOrNull()?:1).toString());api.post("/api/routers/$id/plans",JSONObject().put("profileName",packageName).put("name",packageName).put("price",packagePrice.toDoubleOrNull()?:0.0).put("currency","XOF").toString())}catch(pe:Exception){Toast.makeText(ctx,"تم حفظ الراوتر، لكن تعذر إنشاء الباقة. أضفها من صفحة الباقات. (${pe.message?:""})",Toast.LENGTH_LONG).show()};onSaved()}else step++}catch(e:Exception){err=e.message?:"تعذر الاتصال أو إنشاء الباقة"}finally{busy=false}}},enabled=!busy && canContinue){Text(if(busy)"جاري التحقق…" else if(step==3)"حفظ الراوتر والباقة" else if(step==1)"اختبار الاتصال" else "متابعة")}
        }
    },dismissButton={TextButton(onCancel){Text("إلغاء")}})
}

@Composable
fun SmartControlPage(api: Api, router: JSONObject?, openTerminal: () -> Unit, openNetwork: () -> Unit, openCards: () -> Unit) {
    var output by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeading("مركز القرار", "التحكم الذكي", "إجراءات سريعة مبنية على حالة الراوتر بدل التنقل بين الشاشات")
        if (router == null) EmptyCard("اختر راوترًا من شاشة الراوترات أولاً") else {
            ChassisPanel(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(MmIcons.Sparkle, null, tint = SignalSoft, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("المساعد التشغيلي", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelMedium) }; Text(router.optString("name", "MikroTik"), color = Color.White, style = MaterialTheme.typography.headlineSmall); Text("راقب، حلّل، ثم نفّذ من مكان واحد", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall) } }
            GlassCard { Text("إجراءات ذكية", style = MaterialTheme.typography.titleLarge); ActionTile(MmIcons.Terminal, "فحص RouterOS من Terminal", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxWidth()) { openTerminal() }; ActionTile(MmIcons.Network, "تحليل الواجهات والـ Queues", Link, Modifier.fillMaxWidth()) { openNetwork() }; ActionTile(MmIcons.Ticket, "توليد دفعة كروت مع فحص مسبق", Signal, Modifier.fillMaxWidth()) { openCards() }; Button(enabled = !busy, onClick = { scope.launch { busy = true; try { val raw = api.get("/api/routers/${router.optString("id")}/dashboard"); output = JSONObject(raw).let { "CPU ${it.optJSONObject("resource")?.optString("cpu-load", "-")}% • المستخدمون ${it.optInt("active", it.optInt("activeUsers", 0))}" } } catch (e: Exception) { output = e.message } finally { busy = false } } }, modifier = Modifier.fillMaxWidth()) { Icon(MmIcons.Sparkle, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "جاري التحليل…" else "تحليل حالة الراوتر الآن") }; output?.let { Text(it, color = Accent, fontWeight = FontWeight.Bold) } }
        }
    }
}

fun formatRouterDiagnostic(o: JSONObject): String {
    val steps=o.optJSONArray("steps") ?: return o.optString("advice", "انتهى التشخيص")
    val lines=(0 until steps.length()).map { val x=steps.getJSONObject(it); "${if(x.optBoolean("ok")) "✓" else "✕"} ${x.optString("name")}: ${x.optString("message", x.optString("code"))}" }
    return if(o.optBoolean("ok")) "نجح الاتصال من Backend\n"+lines.joinToString("\n") else "فشل الاتصال\n"+lines.joinToString("\n")+"\n"+o.optString("advice")
}

fun routerConnectionMessage(raw: String): String {
    val s = raw.trim()
    return when { s.contains("HOST_NOT_ALLOWED") -> "الخادم لا يستطيع الوصول إلى هذا العنوان (عنوان خاص أو محجوب). للراوتر داخل شبكتك استخدم «راوتر عن بعد — Agent»."; s.contains("PORT_NOT_ALLOWED") -> "المنفذ غير مسموح. المسموح 8728 و8729 فقط."; s.contains("CERT_PIN_MISMATCH") -> "بصمة شهادة الراوتر لا تطابق المحفوظة. إن غيّرت الشهادة أعد إضافة الراوتر ببصمة جديدة."; s.contains("PUBLIC_BASE_URL_REQUIRED") -> "لم يضبط PUBLIC_BASE_URL على الخادم (Render ← Environment)."; s.contains("timeout", true) && s.contains("connect", true) -> "تعذر الوصول إلى الخادم. الخطة المجانية في Render تنام بعد خمول، انتظر دقيقة ثم أعد المحاولة."; s.contains("404", true) -> "الـBackend يعمل بنسخة قديمة: أعد نشر Backend من GitHub ثم جرّب مرة أخرى."; s.contains("ECONNREFUSED", true) -> "رفض الراوتر الاتصال: فعّل خدمة API على 8728 وتأكد من IP والجدار الناري."; s.contains("ETIMEDOUT", true) || s.contains("TIMEOUT", true) -> "انتهت مهلة الاتصال: الهاتف والـBackend والراوتر يجب أن يكونوا على شبكة قابلة للوصول."; s.contains("AUTH", true) -> { val rm = Regex("\"routerMessage\"\\s*:\\s*\"([^\"]*)\"").find(s)?.groupValues?.get(1); "الراوتر رفض تسجيل الدخول" + (if (rm.isNullOrBlank()) "" else " (${rm})") + ". تأكد من اسم المستخدم كما هو في Winbox بالضبط، وأن مجموعته تملك سياسة api وأن حقل Address للمستخدم لا يحجب الخادم. سجل الراوتر (System ← Logs) يعرض سبب الرفض." }; s.contains("certificate", true) || s.contains("TLS", true) -> "خطأ شهادة TLS: استخدم API 8728 للاختبار المحلي أو ثبّت شهادة API-SSL."; else -> s }
}

@Composable fun EmptyCard(s:String){Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(18.dp)).padding(20.dp),contentAlignment=Alignment.Center){Text(s,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
