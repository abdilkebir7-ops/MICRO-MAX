package com.micromax.app

import android.graphics.Bitmap
import android.os.Bundle
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import androidx.compose.material.icons.filled.*
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
val Blue = Royal
val Green = Teal
val Red = Color(0xFFCB4562)
val Amber = Tangerine
val TextMain: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // applies postSplashScreenTheme: NoActionBar, avoiding duplicate title
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(243, 245, 249)
        window.navigationBarColor = android.graphics.Color.rgb(255, 255, 255)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
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
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    var token: String? = null
    private fun req(path: String) = Request.Builder().url(base.trim().trimEnd('/') + path).apply { token?.let { addHeader("Authorization", "Bearer $it") } }
    suspend fun get(path: String): String = call(req(path).get().build())
    suspend fun post(path: String, body: String = "{}"): String = call(req(path).post(body.toRequestBody("application/json".toMediaType())).build())
    suspend fun delete(path: String): String = call(req(path).delete().build())
    private suspend fun call(r: Request): String = withContext(Dispatchers.IO) { http.newCall(r).execute().use { x -> val b=x.body?.string().orEmpty(); if(!x.isSuccessful) throw Exception(try{JSONObject(b).optString("detail").ifBlank{JSONObject(b).optString("error")}}catch(_:Exception){"HTTP ${x.code}"}); b } }
}

@Composable
fun MicroMaxApp(activity: MainActivity) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Royal, onPrimary = Paper, secondary = Tangerine, background = Porcelain, surface = Paper,
            surfaceVariant = Mist, onSurface = Ink, onBackground = Ink, onSurfaceVariant = MutedInk, outlineVariant = Color(0xFFDCE3EE)),
        typography = microMaxTypography(), shapes = microMaxShapes()
    ) {

        val prefs = remember { activity.getSharedPreferences("micromax_session", Context.MODE_PRIVATE) }
        val api = remember { Api(prefs.getString("api_base_url", BuildConfig.MICROMAX_API_BASE_URL).orEmpty()) }
        var authenticated by remember { mutableStateOf(prefs.getString("jwt_token", null).isNullOrBlank().not()) }
        if (authenticated) {
            api.token = prefs.getString("jwt_token", null)
            MainShell(api, activity) {
                prefs.edit().remove("jwt_token").apply()
                api.token = null
                authenticated = false
            }
        } else {
            AuthScreen(api, prefs) { token ->
                prefs.edit().putString("jwt_token", token).putString("api_base_url", api.base.trim().trimEnd('/')).apply()
                api.token = token
                authenticated = true
            }
        }
    }
}

private fun microMaxTypography() = Typography(
    displaySmall = androidx.compose.ui.text.TextStyle(fontFamily = BrandFont, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 43.sp),
    headlineMedium = androidx.compose.ui.text.TextStyle(fontFamily = BrandFont, fontSize = 27.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp),
    headlineSmall = androidx.compose.ui.text.TextStyle(fontFamily = BrandFont, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 33.sp),
    titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = BrandFont, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp),
    titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = BrandFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = BrandFont, fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = BrandFont, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = androidx.compose.ui.text.TextStyle(fontFamily = BrandFont, fontSize = 12.sp, lineHeight = 19.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = BrandFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = androidx.compose.ui.text.TextStyle(fontFamily = BrandFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = androidx.compose.ui.text.TextStyle(fontFamily = BrandFont, fontSize = 11.sp, fontWeight = FontWeight.Medium)
)
private fun microMaxShapes() = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(26.dp)
)

@Composable
private fun AuthPill(label: String, icon: ImageVector) {
    Surface(color = Color.White.copy(alpha = .1f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Color.White.copy(alpha = .15f))) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color(0xFFB8CBFF), modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)); Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun AuthScreen(api: Api, prefs: android.content.SharedPreferences, onAuthenticated: (String) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(api.base) }
    var serverOpen by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Porcelain).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(310.dp).background(Ink)) {
            Box(Modifier.size(250.dp).align(Alignment.TopEnd).offset(x = 76.dp, y = (-78).dp).background(Color(0xFF12325E), CircleShape))
            Box(Modifier.size(150.dp).align(Alignment.BottomStart).offset(x = (-55).dp, y = 48.dp).background(Color(0xFF075E9E).copy(alpha = .8f), CircleShape))
            Column(Modifier.fillMaxSize().padding(horizontal = 25.dp, vertical = 25.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkBadge(size = 54.dp)
                    Spacer(Modifier.width(13.dp))
                    Column {
                        Text("MICRO-MAX", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.5.sp)
                        Text("INTERNET NETWORK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22D3EE), letterSpacing = 1.8.sp)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("منصة تشغيل حقيقية لأصحاب شبكات MikroTik", color = Color(0xFFB8CBFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (register) "ابدأ شبكة أكثر ذكاءً" else "تحكم في شبكتك بثقة", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Black)
                    Text("إدارة الراوترات، الباقات، الكروت، البوابة والمبيعات من مركز واحد.", color = Color(0xFFD1DBEF), style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AuthPill("MikroTik", Icons.Outlined.Router)
                    AuthPill("HotSpot", Icons.Outlined.Wifi)
                    AuthPill("Smart Ops", Icons.Outlined.AutoAwesome)
                }
            }
        }
        Column(Modifier.fillMaxWidth().offset(y = (-24).dp).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(25.dp), color = Color.White,
                shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (register) "إنشاء حساب المدير" else "تسجيل الدخول", style = MaterialTheme.typography.headlineSmall, color = Ink)
                    Text("استخدم بيانات حسابك المرتبط بالخادم", style = MaterialTheme.typography.bodySmall, color = MutedInk)
                    HorizontalDivider(color = Mist)
                    AppField(email, { email = it }, "البريد الإلكتروني", Icons.Outlined.AlternateEmail)
                    OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        trailingIcon = { IconButton({ reveal = !reveal }) { Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (reveal) "إخفاء" else "إظهار") } },
                        singleLine = true, visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), colors = fieldColors())
                    message?.let { ErrorCard(it) }
                    Button(onClick = {
                        scope.launch {
                            busy = true; message = null
                            try {
                                val clean = server.trim().trimEnd('/')
                                require(clean.startsWith("http://") || clean.startsWith("https://")) { "رابط الخادم يجب أن يبدأ بـ http:// أو https://" }
                                require(email.contains("@") && password.length >= 8) { "أدخل بريدًا صحيحًا وكلمة مرور من 8 أحرف على الأقل" }
                                api.base = clean
                                val body = JSONObject().put("email", email.trim()).put("password", password).toString()
                                val raw = api.post(if (register) "/api/auth/register" else "/api/auth/login", body)
                                val token = JSONObject(raw).optString("token")
                                require(token.isNotBlank()) { "الخادم لم يُرجع رمز الجلسة" }
                                onAuthenticated(token)
                            } catch (e: Exception) { message = e.message ?: "تعذر الاتصال بالخادم" }
                            finally { busy = false }
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(53.dp), shape = RoundedCornerShape(15.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text(if (register) "إنشاء الحساب والدخول" else "الدخول إلى لوحة التشغيل")
                    }
                    TextButton({ register = !register; message = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (register) "لديك حساب؟ تسجيل الدخول" else "أول مرة؟ إنشاء حساب المدير")
                    }
                }
            }
            Surface(shape = RoundedCornerShape(17.dp), color = Color.White) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SymbolTile(Icons.Outlined.Dns, Royal, size = 39.dp)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("خادم الاتصال", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
                            Text(server.ifBlank { "لم يُحدّد بعد" }, fontSize = 11.sp, color = MutedInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton({ serverOpen = !serverOpen }) { Text(if (serverOpen) "إخفاء" else "تغيير") }
                    }
                    if (serverOpen) {
                        AppField(server, { server = it }, "رابط Backend (HTTPS)", Icons.Outlined.Link)
                        Text(if (server.contains("10.0.2.2")) "10.0.2.2 للمحاكي فقط. للهاتف الحقيقي استخدم خادم HTTPS أو عنوان شبكة قابل للوصول." else "استخدم HTTPS للخادم الحقيقي.", fontSize = 11.sp, color = MutedInk)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(7.dp)) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = Teal, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("بياناتك تُرسل إلى الخادم الذي تحدده أنت فقط", style = MaterialTheme.typography.bodySmall, color = MutedInk)
            }
        }
    }
}

@Composable fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Royal, unfocusedBorderColor = Color(0xFFD6DFEC), focusedLabelColor = Royal,
    unfocusedLabelColor = MutedInk, cursorColor = Royal, focusedTextColor = Ink, unfocusedTextColor = Ink,
    focusedContainerColor = Paper, unfocusedContainerColor = Paper
)
@Composable fun Logo() { NetworkBadge(size = 92.dp) }
@Composable fun AppField(v: String, on: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OutlinedTextField(v, on, label = { Text(label) }, leadingIcon = { Icon(icon, null) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), colors = fieldColors())
}
@Composable fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}

private data class Nav(val title:String,val icon:androidx.compose.ui.graphics.vector.ImageVector,val page:Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(api: Api, activity: MainActivity, onLogout: () -> Unit) {
    val nav = listOf(
        Nav("الرئيسية", Icons.Outlined.SpaceDashboard, 0), Nav("الراوترات", Icons.Outlined.Router, 1),
        Nav("الباقات", Icons.Outlined.Speed, 2), Nav("الكروت", Icons.Outlined.QrCode2, 3),
        Nav("المتجر", Icons.Outlined.Storefront, 4), Nav("المبيعات", Icons.Outlined.PointOfSale, 5),
        Nav("التقارير", Icons.Outlined.BarChart, 6), Nav("الأمان", Icons.Outlined.AdminPanelSettings, 7),
        Nav("الشبكة", Icons.Outlined.Lan, 8), Nav("الإعدادات", Icons.Outlined.Settings, 9),
        Nav("HTML HotSpot", Icons.Outlined.Code, 10), Nav("طرفية RouterOS", Icons.Outlined.Terminal, 11),
        Nav("استوديو الكروت", Icons.Outlined.Palette, 12), Nav("محرر الواجهات", Icons.Outlined.Web, 13), Nav("التحكم الذكي", Icons.Outlined.AutoAwesome, 14)
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
    var themeDark by remember { mutableStateOf(sessionPrefs.getBoolean("theme_dark", false)) }
    var chosenTemplate by remember { mutableStateOf("Midnight Glass") }
    var storeRouter by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
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

    val scheme = if (themeDark) darkColorScheme(primary = Color(0xFF91B4FF), onPrimary = Ink, secondary = Tangerine,
        background = Color(0xFF101C30), surface = Color(0xFF1B2B45), surfaceVariant = Color(0xFF243651),
        onBackground = Color(0xFFF1F5FF), onSurface = Color(0xFFF1F5FF), onSurfaceVariant = Color(0xFFBAC6D9), outlineVariant = Color(0xFF3B4D66))
        else lightColorScheme(primary = Royal, onPrimary = Paper, secondary = Tangerine, background = Porcelain,
            surface = Paper, surfaceVariant = Mist, onBackground = Ink, onSurface = Ink,
            onSurfaceVariant = MutedInk, outlineVariant = Color(0xFFDCE3EE))
    MaterialTheme(colorScheme = scheme, typography = microMaxTypography(), shapes = microMaxShapes()) {
        val surface = MaterialTheme.colorScheme.surface
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(color = Ink, shadowElevation = 5.dp) {
                    Row(Modifier.fillMaxWidth().height(65.dp).padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetworkBadge(size = 38.dp)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("MICRO-MAX", fontSize = 21.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.2.sp)
                            Text("NETWORK CONTROL  •  ${nav[page].title}", fontSize = 10.sp, color = Color(0xFFB8CBFF), fontWeight = FontWeight.SemiBold)
                        }
                        IconButton({ refresh() }, modifier = Modifier.size(43.dp)) { Icon(Icons.Outlined.Sync, "تحديث", tint = Accent) }
                    }
                }
            },
            bottomBar = {
                Surface(color = surface, shadowElevation = 15.dp) {
                    Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                        val primary = listOf(nav[0], nav[1], nav[3], nav[4])
                        primary.forEach { item ->
                            val active = page == item.page
                            Column(Modifier.weight(1f).fillMaxHeight().clickable { page = item.page }.padding(vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(Modifier.width(52.dp).height(33.dp).background(if (active) Accent.copy(alpha = .12f) else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(item.icon, item.title, tint = if (active) Accent else TextMuted, modifier = Modifier.size(23.dp))
                                }
                                Text(item.title, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) Accent else TextMuted, maxLines = 1)
                            }
                        }
                        val active = page !in listOf(0,1,3,4)
                        Column(Modifier.weight(1f).fillMaxHeight().clickable { moreOpen = true }.padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.width(52.dp).height(33.dp).background(if (active) Accent.copy(alpha = .12f) else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Apps, "الخدمات", tint = if (active) Accent else TextMuted, modifier = Modifier.size(23.dp))
                            }
                            Text("الخدمات", fontSize = 10.sp, color = if (active) Accent else TextMuted)
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (page) {
                    0 -> DashboardPage(routers, selected, dash, users, error, { add = true }, { page = it }) { r -> selected = r; refresh() }
                    1 -> RoutersPage(routers, selected, { add = true }, { r -> selected = r; page = 0; refresh() }) { id -> scope.launch { try { api.delete("/api/routers/$id"); refresh() } catch (e: Exception) { error = e.message } } }
                    2 -> ProfilesPage(api, selected)
                    3 -> CardsStudioPage(api, selected, activity)
                    4 -> StorePage(api, routers) { t, r -> chosenTemplate = t; storeRouter = if (r.has("id")) r else null; if (r.has("id")) selected = r; page = 12 }
                    5 -> SalesPage(api)
                    6 -> ReportsPage(api)
                    7 -> SecurityPage(api)
                    8 -> NetworkPage(api, selected)
                    9 -> SettingsPage(themeDark, { themeDark = !themeDark; sessionPrefs.edit().putBoolean("theme_dark", themeDark).apply() }, api.base, { newUrl -> api.base = newUrl; sessionPrefs.edit().putString("api_base_url", newUrl).apply(); refresh() }, onLogout)
                    10 -> HotspotEditorPage(api, selected)
                    11 -> TerminalPage(api, selected)
                    12 -> DesignStudioPage(api, selected, activity, chosenTemplate)
                    13 -> HotspotVisualEditorPage(api, selected)
                    14 -> SmartControlPage(api, selected, { page = 11 }, { page = 8 }, { page = 3 })
                }
            }
        }
        if (moreOpen) ModalBottomSheet(onDismissRequest = { moreOpen = false }, containerColor = surface) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                PageHeading("مركز العمليات", "كل الأدوات", "انتقل بسرعة إلى أي جزء من النظام")
                val extra = listOf(nav[2], nav[5], nav[6], nav[12], nav[13], nav[14], nav[8], nav[7], nav[10], nav[11], nav[9])
                extra.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        row.forEach { item -> ActionTile(item.icon, item.title, when (item.page) { 5 -> Teal; 12, 13 -> Lavender; 6 -> Tangerine; else -> Accent }, Modifier.weight(1f)) { page = item.page; moreOpen = false } }
                        repeat(3-row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    if (add) AddRouterDialog(api, { add = false; refresh() }, { add = false })
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("مركز التشغيل  /  نظرة عامة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent)
        Text("أدِر شبكتك بوضوح.", style = MaterialTheme.typography.headlineMedium, color = TextMain)
        Text("أداء الشبكة، الباقات والكروت في مكان واحد", fontSize = 13.sp, color = TextMuted)
    }
}

@Composable
private fun RouterHeroCard(r: JSONObject, resource: JSONObject?, cpu: Float, ram: Float, storage: Float, d: JSONObject) {
    Surface(shape = RoundedCornerShape(26.dp), color = Ink, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(47.dp).background(Color.White.copy(alpha = .12f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Router, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("الجهاز النشط", fontSize = 11.sp, color = Color(0xFFB7C7E5))
                    Text(r.optString("name", "MikroTik"), fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.background(Color(0xFF173F42), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("● متصل", color = Color(0xFF77E0BE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("${r.optString("host", "-")}  •  RouterOS ${resource?.optString("version", "-") ?: "-"}", fontSize = 12.sp, color = Color(0xFFB7C7E5))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStat("${d.optInt("active", d.optInt("activeUsers", 0))}", "متصل الآن", Modifier.weight(1f))
                HeroStat("${d.optInt("users", d.optInt("activeUsers", 0))}", "مستخدم", Modifier.weight(1f))
                HeroStat(trafficText(d), "حركة البيانات", Modifier.weight(1f))
            }
            HorizontalDivider(color = Color.White.copy(alpha = .16f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Memory, null, tint = Color(0xFFB8CCFF), modifier = Modifier.size(18.dp))
                Text("CPU ${cpu.toInt()}%", fontSize = 11.sp, color = Color.White)
                Spacer(Modifier.weight(1f))
                Text("RAM ${ram.toInt()}%", fontSize = 11.sp, color = Color.White)
                Text("Disk ${storage.toInt()}%", fontSize = 11.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .09f), RoundedCornerShape(13.dp)).padding(10.dp)) {
        Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Color(0xFFB7C7E5), fontSize = 10.sp)
    }
}

@Composable
private fun QuickActions(onRouter: () -> Unit, onCards: () -> Unit, onUsers: () -> Unit, onStore: () -> Unit, onReports: () -> Unit, onDesign: () -> Unit, onSmart: () -> Unit, onTerminal: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ActionTile(Icons.Outlined.AddCircleOutline, "إضافة راوتر", Royal, Modifier.weight(1f), onRouter)
            ActionTile(Icons.Outlined.QrCode2, "إنشاء كروت", Teal, Modifier.weight(1f), onCards)
            ActionTile(Icons.Outlined.Speed, "الباقات", Lavender, Modifier.weight(1f), onUsers)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ActionTile(Icons.Outlined.Palette, "التصميم", Tangerine, Modifier.weight(1f), onDesign)
            ActionTile(Icons.Outlined.Storefront, "القوالب", Royal, Modifier.weight(1f), onStore)
            ActionTile(Icons.Outlined.BarChart, "التقارير", Teal, Modifier.weight(1f), onReports)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ActionTile(Icons.Outlined.AutoAwesome, "التحكم الذكي", Lavender, Modifier.weight(1f), onSmart)
            ActionTile(Icons.Outlined.Terminal, "Terminal", Ink, Modifier.weight(1f), onTerminal)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RouterCompactRow(r: JSONObject, selected: Boolean, click: () -> Unit) {
    ItemSurface(onClick = click) {
        SymbolTile(Icons.Outlined.Router, if (selected) Royal else MutedInk)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(r.optString("name", "MikroTik"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMain, maxLines = 1)
            Text(r.optString("host", "-"), fontSize = 11.sp, color = TextMuted)
        }
        if (selected) StatusPill("النشط") else Icon(Icons.Outlined.ChevronLeft, null, tint = TextMuted)
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

@Composable fun RouterSelector(rs:JSONArray,s:JSONObject?,add:()->Unit,select:(JSONObject)->Unit){GlassCard{Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Router,null,tint=Accent);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("الراوتر الحالي",fontSize=12.sp,color=TextMuted);Text(s?.optString("name")?:"لا يوجد راوتر",fontWeight=FontWeight.Bold)};Button(add){Icon(Icons.Default.Add,null);Text("إضافة")}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){for(i in 0 until rs.length()){val r=rs.getJSONObject(i);FilterChip(s?.optString("id")==r.optString("id"),{select(r)},{Text(r.optString("name","Router"),maxLines=1)})}}}}
@Composable fun StatsGrid(r:JSONObject?,d:JSONObject){Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("CPU",r?.optString("cpu-load","-")+"%",Icons.Default.Speed,Modifier.weight(1f));Stat("المستخدمون",d.optInt("users",d.optInt("activeUsers",0)).toString(),Icons.Default.People,Modifier.weight(1f))};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("متصل الآن",d.optInt("active",d.optInt("activeUsers",0)).toString(),Icons.Default.Wifi,Modifier.weight(1f));Stat("RAM",memoryText(r),Icons.Default.Memory,Modifier.weight(1f))};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("Storage",storageText(r),Icons.Default.Storage,Modifier.weight(1f));Stat("Traffic",trafficText(d),Icons.Default.DataUsage,Modifier.weight(1f))}}}
@Composable fun Stat(t:String,v:String,i:androidx.compose.ui.graphics.vector.ImageVector,m:Modifier){Card(m,colors=CardDefaults.cardColors(containerColor=Card),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp)){Icon(i,null,tint=Accent);Text(t,fontSize=12.sp,color=TextMuted);Text(v,fontSize=22.sp,fontWeight=FontWeight.Black)}}}
@Composable fun ResourceCard(r:JSONObject?,router:JSONObject){GlassCard{Text(router.optString("name","Router"),fontWeight=FontWeight.Bold);Text(router.optString("host","-"),fontSize=12.sp,color=TextMuted);HorizontalDivider();Text("RouterOS ${r?.optString("version","-")}",fontWeight=FontWeight.Bold);Text("${r?.optString("board-name","-")} • ${r?.optString("architecture-name","-")}",fontSize=12.sp,color=TextMuted);Text("Uptime: ${r?.optString("uptime","-")}",fontSize=12.sp,color=TextMuted)}}
fun memoryText(r:JSONObject?):String{val t=r?.optString("total-memory")?.toLongOrNull();val f=r?.optString("free-memory")?.toLongOrNull();return if(t!=null&&f!=null&&t>0)"${((t-f)*100/t)}%" else "-"}
fun storageText(r:JSONObject?):String{val t=r?.optString("total-hdd-space")?.toLongOrNull();val f=r?.optString("free-hdd-space")?.toLongOrNull();return if(t!=null&&f!=null&&t>0)"${((t-f)*100/t)}%" else "-"}
fun trafficText(d:JSONObject):String{val a=d.optJSONArray("interfaces")?:return "-";var total=0L;for(i in 0 until a.length()){val o=a.optJSONObject(i);total+=o?.optString("rx-byte")?.toLongOrNull()?:0L;total+=o?.optString("tx-byte")?.toLongOrNull()?:0L};return if(total<=0)"-" else if(total>1_000_000_000)String.format("%.1f GB",total/1_000_000_000.0) else if(total>1_000_000)String.format("%.1f MB",total/1_000_000.0) else "${total/1000} KB"}
@Composable fun InterfaceRow(o:JSONObject){Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Card),shape=RoundedCornerShape(16.dp)){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Lan,null,tint=Accent);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(o.optString("name","-"),fontWeight=FontWeight.Bold);Text(o.optString("type",""),fontSize=12.sp,color=TextMuted)};Text(if(o.optString("running")=="true")"UP" else "DOWN",color=if(o.optString("running")=="true")Green else Red,fontWeight=FontWeight.Bold)}}}
@Composable fun UserRow(o:JSONObject){val dis=o.optString("disabled")=="true";Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Card),shape=RoundedCornerShape(16.dp)){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Person,null,tint=if(dis)Red else Green);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(o.optString("name","-"),fontWeight=FontWeight.Bold);Text(o.optString("profile","default"),fontSize=12.sp,color=TextMuted)};Text(if(dis)"معطل" else "فعال",color=if(dis)Red else Green,fontWeight=FontWeight.Bold)}}}
@Composable fun EmptyRouterCard(add:()->Unit){GlassCard{Icon(Icons.Default.Router,null,tint=Accent,modifier=Modifier.size(42.dp));Text("لا يوجد راوتر مرتبط",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("أضف MikroTik ثم اختبر الاتصال.",color=TextMuted);Button(add,Modifier.fillMaxWidth()){Text("إضافة راوتر MikroTik")}}}
@Composable fun ErrorCard(s:String){Card(colors=CardDefaults.cardColors(containerColor=Red.copy(alpha=.12f)),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){Text("⚠ $s",Modifier.padding(14.dp),color=Color(0xFFFFA4B0))}}

@Composable fun RoutersPage(rs:JSONArray,selected:JSONObject?,add:()->Unit,choose:(JSONObject)->Unit,remove:(String)->Unit){LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("إدارة الراوترات",fontSize=28.sp,fontWeight=FontWeight.Black);Text("إضافة • اختبار • حفظ • إدارة عدة MikroTik",fontSize=13.sp,color=TextMuted)};Button(add){Icon(Icons.Default.Add,null);Text("إضافة")}}};items((0 until rs.length()).map{rs.getJSONObject(it)}){r->RouterRow(r,selected?.optString("id")==r.optString("id"),{choose(r)},{remove(r.optString("id"))})}}}
@Composable fun RouterRow(r:JSONObject,sel:Boolean,choose:()->Unit,remove:()->Unit){Card(Modifier.fillMaxWidth().clickable{choose()},colors=CardDefaults.cardColors(containerColor=if(sel)Accent.copy(alpha=.12f)else Card),shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Router,null,tint=Blue);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(r.optString("name","Router"),fontWeight=FontWeight.Bold);Text("${r.optString("host","-")}:${r.optInt("port",8728)} • ${if(r.optBoolean("tls"))"API-SSL" else "API"}",fontSize=12.sp,color=TextMuted)};IconButton(remove){Icon(Icons.Default.Delete,null,tint=Red)}}}}

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
        Text("Profiles & Plans", fontSize=28.sp, fontWeight=FontWeight.Black)
        Text("Profile = سلوك الشبكة • Plan = المنتج والسعر", color=TextMuted)
        error?.let { ErrorCard(it) }
        if (router == null) EmptyCard("اختر راوتر أولاً") else {
            Button({ show=true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add,null); Text("إضافة Profile + Plan") }
            Text("HotSpot Profiles", fontSize=20.sp, fontWeight=FontWeight.Bold)
            for(i in 0 until profiles.length()) { val p=profiles.getJSONObject(i); GlassCard { Text(p.optString("name"),fontWeight=FontWeight.Bold); Text("Rate: ${p.optString("rateLimit","-")} • Session: ${p.optString("sessionTimeout","-")} • Shared: ${p.optInt("sharedUsers",1)}",fontSize=12.sp,color=TextMuted) } }
            Text("Plans", fontSize=20.sp, fontWeight=FontWeight.Bold)
            for(i in 0 until plans.length()) { val p=plans.getJSONObject(i); GlassCard { Text(p.optString("name"),fontWeight=FontWeight.Bold); Text("${p.optDouble("price",0.0)} ${p.optString("currency","XOF")} • ${p.optString("profileName",p.optString("profile_name","-"))}",color=Accent) } }
        }
    }
    if(show && router!=null) ProfileDialog(api,router,{show=false;load()},{show=false})
}

@Composable fun ProfileDialog(api:Api,r:JSONObject,done:()->Unit,cancel:()->Unit){var name by remember{mutableStateOf("")};var duration by remember{mutableStateOf("60")};var rate by remember{mutableStateOf("")};var session by remember{mutableStateOf("")};var shared by remember{mutableStateOf("1")};var plan by remember{mutableStateOf("")};var price by remember{mutableStateOf("0")};var busy by remember{mutableStateOf(false)};var err by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest=cancel,title={Text("Profile + Plan")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){AppField(name,{name=it},"اسم Profile",Icons.Default.Speed);AppField(duration,{duration=it},"المدة بالدقائق",Icons.Default.Timer);AppField(rate,{rate=it},"Rate Limit (مثال 2M/2M)",Icons.Default.Speed);AppField(session,{session=it},"Session Timeout",Icons.Default.Timer);AppField(shared,{shared=it},"Shared Users",Icons.Default.People);AppField(plan,{plan=it},"اسم Plan",Icons.Default.Inventory2);AppField(price,{price=it},"السعر XOF",Icons.Default.Payments);err?.let{Text(it,color=Red)}}},confirmButton={Button({scope.launch{busy=true;try{val id=r.optString("id");api.post("/api/routers/$id/hotspot-profiles",JSONObject().put("name",name).put("durationMinutes",duration.toIntOrNull()?:0).put("rateLimit",rate).put("sessionTimeout",session).put("sharedUsers",shared.toIntOrNull()?:1).toString());api.post("/api/routers/$id/plans",JSONObject().put("profileName",name).put("name",if(plan.isBlank())name else plan).put("price",price.toDoubleOrNull()?:0).put("currency","XOF").toString());done()}catch(e:Exception){err=e.message}finally{busy=false}}},enabled=!busy&&name.isNotBlank()&&price.isNotBlank()){Text(if(busy)"جاري الحفظ…" else "حفظ")}},dismissButton={TextButton(cancel){Text("إلغاء")}})}

@Composable fun CardsStudioPage(api:Api,router:JSONObject?,activity:MainActivity){var plans by remember{mutableStateOf(JSONArray())};var selectedPlan by remember{mutableStateOf<JSONObject?>(null)};var portal by remember{mutableStateOf("")};var ssid by remember{mutableStateOf("")};var wifiOpen by remember{mutableStateOf(false)};var passwordLength by remember{mutableStateOf("8")};var letters by remember{mutableStateOf("0")};var mode by remember{mutableStateOf("userpass")};var count by remember{mutableStateOf("10")};var prefix by remember{mutableStateOf("KMX")};var digits by remember{mutableStateOf("6")};var result by remember{mutableStateOf<JSONArray?>(null)};var batches by remember{mutableStateOf<JSONArray?>(null)};var err by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var preview by remember{mutableStateOf<JSONObject?>(null)};val scope=rememberCoroutineScope();LaunchedEffect(router?.optString("id")){if(router!=null)scope.launch{try{plans=JSONObject(api.get("/api/routers/${router.optString("id")}/plans")).optJSONArray("plans")?:JSONArray()}catch(e:Exception){err=e.message}}};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Card Studio",fontSize=28.sp,fontWeight=FontWeight.Black);Text("QR + Barcode + Preflight + توليد + Batch Center — بدون اشتراك",color=TextMuted);if(router==null)EmptyCard("اختر راوتر أولاً") else {GlassCard{Text("بيانات الكروت",fontWeight=FontWeight.Bold);AppField(portal,{portal=it},"رابط HotSpot الحقيقي",Icons.Default.Link);AppField(ssid,{ssid=it},"اسم شبكة الواي فاي SSID",Icons.Default.Wifi);Row(verticalAlignment=Alignment.CenterVertically){Switch(wifiOpen,{wifiOpen=it});Text("إنشاء QR اتصال Wi-Fi مفتوح",Modifier.weight(1f));Text(if(wifiOpen) "مفعّل" else "مغلق",fontSize=11.sp,color=TextMuted)};Text("طريقة الكروت",fontSize=13.sp,color=TextMuted);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(mode=="userpass",{mode="userpass"},{Text("1) يوزر + باسورد + QR")});FilterChip(mode=="pin",{mode="pin";if((digits.toIntOrNull()?:0)<8)digits="8"},{Text("2) كود فقط + QR")})};if(mode=="pin")Text("الكود فقط: صفحة login-code.html، ويلزم 8 خانات على الأقل",fontSize=11.sp,color=Amber);AppField(count,{count=it},"عدد الكروت",Icons.Default.Numbers);AppField(prefix,{prefix=it},"Prefix",Icons.Default.Tag);AppField(digits,{digits=it.filter(Char::isDigit)},"عدد أرقام اسم المستخدم",Icons.Default.Numbers);AppField(letters,{letters=it.filter(Char::isDigit)},"عدد حروف اسم المستخدم",Icons.Default.Translate);AppField(passwordLength,{passwordLength=it.filter(Char::isDigit)},"طول كلمة المرور",Icons.Default.Password);Text("الخطة",fontSize=13.sp,color=TextMuted);if(plans.length()==0)Text("أنشئ Plan أولاً من Profiles",color=Amber);for(i in 0 until plans.length()){val p=plans.getJSONObject(i);FilterChip(selectedPlan?.optString("id")==p.optString("id"),{selectedPlan=p},{Text("${p.optString("name")} • ${p.optDouble("price")} ${p.optString("currency","XOF")}")})};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({scope.launch{busy=true;try{val id=router.optString("id");api.post("/api/routers/$id/qr-preflight",JSONObject().put("portalUrl",portal).toString());val raw=api.post("/api/routers/$id/cards/preflight",JSONObject().put("count",count.toIntOrNull()?:1).put("planId",selectedPlan?.optString("id")).put("prefix",prefix).put("usernameDigits",digits.toIntOrNull()?:6).put("usernameLetters",letters.toIntOrNull()?:0).put("passwordLength",passwordLength.toIntOrNull()?:8).put("portalUrl",portal).put("passwordMode",mode).toString());err="Preflight جاهز: ${JSONObject(raw).optBoolean("ready",true)}"}catch(e:Exception){err=e.message}}},enabled=!busy&&selectedPlan!=null&&portal.isNotBlank()){Text("فحص ذكي")};Button({scope.launch{busy=true;err=null;try{val id=router.optString("id");val raw=api.post("/api/routers/$id/cards/generate",JSONObject().put("count",count.toIntOrNull()?:1).put("planId",selectedPlan?.optString("id")).put("prefix",prefix).put("usernameDigits",digits.toIntOrNull()?:6).put("usernameLetters",letters.toIntOrNull()?:0).put("passwordLength",passwordLength.toIntOrNull()?:8).put("portalUrl",portal).put("ssid",ssid.trim()).put("wifiOpen",wifiOpen).put("passwordMode",mode).toString());result=JSONObject(raw).optJSONArray("cards")}catch(e:Exception){err=e.message}finally{busy=false}}},enabled=!busy&&selectedPlan!=null&&portal.isNotBlank()){Text(if(busy)"جاري…" else "توليد")}}};err?.let{Text(it,color=if(it.startsWith("Preflight"))Green else Red)};result?.let{arr->Text("تم إنشاء ${arr.length()} كرت",fontSize=20.sp,fontWeight=FontWeight.Bold);for(i in 0 until minOf(arr.length(),50)){val c=arr.getJSONObject(i);Card(Modifier.fillMaxWidth().clickable{preview=c},colors=CardDefaults.cardColors(containerColor=Card),shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.QrCode2,null,tint=Accent);Column(Modifier.weight(1f)){Text(c.optString("username"),fontWeight=FontWeight.Bold);Text("${c.optString("password")} • ${c.optString("profile")}",fontSize=12.sp,color=TextMuted)};Text("${c.optDouble("price")} ${c.optString("currency","XOF")}",color=Accent)}}}};Button({scope.launch{try{val rid=router?.optString("id")?:"";val a=JSONObject(api.post("/api/routers/$rid/cards/audit","{}"));val c=a.optJSONObject("counts");val iss=a.optJSONObject("issues");err="Preflight فحص المزامنة: "+(if(a.optBoolean("healthy"))"سليم" else "توجد فروقات")+" • غير مستخدم ${c?.optInt("unused")} • مستخدم ${c?.optInt("used")} • ناقص على الراوتر ${iss?.optJSONArray("missingOnRouter")?.length()} • زائد ${iss?.optJSONArray("orphanOnRouter")?.length()}"}catch(e:Exception){err=e.message}}},Modifier.fillMaxWidth(),enabled=router!=null){Text("فحص ذكي للمزامنة مع MikroTik")};Button({scope.launch{try{batches=JSONObject(api.get("/api/card-batches")).optJSONArray("batches")}catch(e:Exception){err=e.message}}},Modifier.fillMaxWidth()){Text("فتح Batch Center")};batches?.let{Text("آخر الدُفعات",fontSize=20.sp,fontWeight=FontWeight.Bold);for(i in 0 until minOf(it.length(),20)){val b=it.getJSONObject(i);GlassCard{Text(b.optString("plan_name",b.optString("planName","Batch")),fontWeight=FontWeight.Bold);Text("${b.optInt("total")} كرت • متاح ${b.optInt("available")} • مباع ${b.optInt("sold")}",color=TextMuted)}}}};preview?.let{CardPreviewDialog(it,{preview=null},{bitmap->activity.printBitmap(bitmap)})}}}

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
                Icon(Icons.Default.ZoomOut, null, tint = TextMuted)
                Slider(value = zoom, onValueChange = { zoom = it }, valueRange = .65f..1.8f, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ZoomIn, null, tint = Accent)
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
            TextButton({ composeCardBitmap(c)?.let { print(it) } }) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(5.dp)); Text("طباعة بالحجم الحقيقي") }
            TextButton(close) { Text("إغلاق") }
        }
    })
}

fun generateCode(value:String,format:BarcodeFormat,w:Int,h:Int):Bitmap?{return try{val hints=mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,EncodeHintType.MARGIN to 1);val m:BitMatrix=MultiFormatWriter().encode(value,format,w,h,hints);val bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);for(x in 0 until w)for(y in 0 until h)bmp.setPixel(x,y,if(m[x,y])android.graphics.Color.BLACK else android.graphics.Color.WHITE);bmp}catch(_:Exception){null}}

fun MainActivity.printBitmap(bitmap:Bitmap){try{androidx.print.PrintHelper(this).apply{scaleMode=androidx.print.PrintHelper.SCALE_MODE_FIT}.printBitmap("MICRO-MAX Card",bitmap)}catch(e:Exception){Toast.makeText(this,e.message?:"تعذر فتح الطباعة",Toast.LENGTH_LONG).show()}}

fun composeCardBitmap(c:JSONObject):Bitmap?{val qr=generateCode(c.optString("qrContent"),BarcodeFormat.QR_CODE,520,520)?:return null;val bar=generateCode(c.optString("username"),BarcodeFormat.CODE_128,700,150)?:return null;val out=Bitmap.createBitmap(900,900,Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(out);canvas.drawColor(android.graphics.Color.WHITE);val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);p.color=android.graphics.Color.BLACK;p.typeface=android.graphics.Typeface.DEFAULT_BOLD;p.textSize=30f;canvas.drawText(c.optString("profile",c.optString("plan","WiFi Access")),40f,65f,p);p.strokeWidth=2f;canvas.drawRect(40f,84f,860f,87f,p);p.typeface=android.graphics.Typeface.DEFAULT;p.textSize=24f;val pinM=c.optString("passwordMode")=="pin";canvas.drawText("${if(pinM)"الكود" else "اسم المستخدم"}  ${c.optString("username")}",40f,135f,p);if(!pinM)canvas.drawText("كلمة المرور  ${c.optString("password")}",40f,175f,p);val wqr=c.optString("wifiQr").let{if(it.isBlank())null else generateCode(it,BarcodeFormat.QR_CODE,520,520)};if(wqr!=null){p.textSize=22f;canvas.drawText("1) اتصال بالشبكة",60f,205f,p);canvas.drawText("2) تسجيل الدخول",480f,205f,p);canvas.drawBitmap(wqr,null,android.graphics.Rect(60,220,420,580),p);canvas.drawBitmap(qr,null,android.graphics.Rect(480,220,840,580),p)}else{canvas.drawBitmap(qr,null,android.graphics.Rect(190,220,710,740),p)};canvas.drawBitmap(bar,null,android.graphics.Rect(100,765,800,850),p);return out}


@Composable fun SalesPage(api:Api){var sales by remember{mutableStateOf(JSONArray())};var cards by remember{mutableStateOf(JSONArray())};var err by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope();fun load(){scope.launch{try{sales=JSONObject(api.get("/api/sales")).optJSONArray("sales")?:JSONArray();cards=JSONObject(api.get("/api/cards")).optJSONArray("cards")?:JSONArray()}catch(e:Exception){err=e.message}}};LaunchedEffect(Unit){load()};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("المبيعات",fontSize=28.sp,fontWeight=FontWeight.Black);Text("بيع الكرت نقداً الآن أو تتبع حالة الدفع الإلكتروني الاختياري",color=TextMuted);err?.let{ErrorCard(it)};Text("كروت جاهزة للبيع",fontSize=20.sp,fontWeight=FontWeight.Bold);for(i in 0 until minOf(cards.length(),40)){val c=cards.getJSONObject(i);if(c.optString("status")=="available"){GlassCard{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(c.optString("username"),fontWeight=FontWeight.Bold);Text("${c.optString("plan_name",c.optString("profile"))} • ${c.optDouble("price")} ${c.optString("price_currency","XOF")}",fontSize=12.sp,color=TextMuted)};Button({scope.launch{busy=true;try{api.post("/api/sales",JSONObject().put("cardId",c.optString("id")).put("paymentMethod","cash").toString());load()}catch(e:Exception){err=e.message}finally{busy=false}}},enabled=!busy){Text("بيع نقداً")}}}}};Text("آخر المبيعات",fontSize=20.sp,fontWeight=FontWeight.Bold);for(i in 0 until minOf(sales.length(),30)){val o=sales.getJSONObject(i);GlassCard{Text(o.optString("card_username","-"),fontWeight=FontWeight.Bold);Text("${o.optDouble("amount")} • ${o.optString("payment_method")} • ${o.optString("payment_status")}",color=TextMuted)}}}}

@Composable fun ReportsPage(api:Api){var data by remember{mutableStateOf(JSONArray())};var err by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope();LaunchedEffect(Unit){scope.launch{try{data=JSONObject(api.get("/api/reports/sales")).optJSONArray("rows")?:JSONArray()}catch(e:Exception){err=e.message}}};LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("التقارير",fontSize=28.sp,fontWeight=FontWeight.Black);Text("تقارير المبيعات والإيرادات اليومية",color=TextMuted)};err?.let{item{ErrorCard(it)}};items((0 until data.length()).map{data.getJSONObject(it)}){o->GlassCard{Text(o.optString("day","-"),fontWeight=FontWeight.Bold);Text("المبيعات: ${o.optInt("sales")} • الإيراد: ${o.optDouble("revenue")} • مدفوع: ${o.optInt("paid")}",color=TextMuted)}}}}

@Composable
fun SecurityPage(api: Api) {
    var tab by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf(JSONArray()) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    fun load(){ scope.launch { try { val o=JSONObject(api.get(if(tab==0) "/api/users" else "/api/audit")); data=o.optJSONArray(if(tab==0) "users" else "logs")?:JSONArray(); err=null } catch(e:Exception){err=e.message} } }
    LaunchedEffect(tab){load()}
    Column(Modifier.fillMaxSize().padding(16.dp)){
        Text("الأمان والصلاحيات",fontSize=28.sp,fontWeight=FontWeight.Black)
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
            Text("Backups",fontSize=20.sp,fontWeight=FontWeight.Bold)
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

@Composable fun SettingsPage(dark:Boolean,toggle:()->Unit,apiBase:String,onApiChange:(String)->Unit,onLogout:()->Unit){var newApi by remember(apiBase){mutableStateOf(apiBase)};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("الإعدادات",fontSize=28.sp,fontWeight=FontWeight.Black);GlassCard{Text("MICRO-MAX V3.0.2",fontWeight=FontWeight.Bold);Text("إدارة MikroTik عبر RouterOS API / API-SSL",color=TextMuted);Text("خادم Backend",fontSize=11.sp,color=TextMuted);OutlinedTextField(newApi,{newApi=it},label={Text("رابط HTTPS للخادم")},singleLine=true,modifier=Modifier.fillMaxWidth(),colors=fieldColors());Button(onClick={val clean=newApi.trim().trimEnd('/');if(clean.startsWith("http://")||clean.startsWith("https://"))onApiChange(clean)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Save,null);Spacer(Modifier.width(7.dp));Text("حفظ واختبار الخادم")};Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.DarkMode,null,tint=Accent);Spacer(Modifier.width(10.dp));Text("الوضع الزجاجي الداكن",Modifier.weight(1f));Switch(dark,{toggle()})};OutlinedButton(onClick=onLogout,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Color(0xFFFF6B7A))){Icon(Icons.Default.Logout,null);Spacer(Modifier.width(8.dp));Text("تسجيل الخروج")}};GlassCard{Text("المميزات المتفق عليها",fontWeight=FontWeight.Bold);Text("✓ عدة MikroTik\n✓ Dashboard وCPU/RAM/Uptime/Users/Traffic\n✓ Profiles وPlans والأسعار\n✓ Cards + QR + Barcode + Batch Center\n✓ Store + Sales + Reports\n✓ Users/Roles + Audit\n✓ Network: Interfaces/DHCP/ARP/DNS/IP/Routes/Firewall/Queues/Logs\n✓ HotSpot login.html Editor + Backup/Restore\n✓ الدفع الإلكتروني اختياري",color=TextMuted)}}}

@Composable
fun AddRouterDialog(api:Api,onSaved:()->Unit,onCancel:()->Unit){
    var step by remember{mutableIntStateOf(0)};var mode by remember{mutableStateOf("local")};var name by remember{mutableStateOf("")};var host by remember{mutableStateOf("")};var port by remember{mutableStateOf("8728")};var user by remember{mutableStateOf("admin")};var pass by remember{mutableStateOf("")};var tls by remember{mutableStateOf(false)};var packageName by remember{mutableStateOf("اقتصادية")};var download by remember{mutableStateOf("2M")};var upload by remember{mutableStateOf("1M")};var duration by remember{mutableStateOf("60")};var packagePrice by remember{mutableStateOf("500")};var sharedUsers by remember{mutableStateOf("1")};var busy by remember{mutableStateOf(false)};var diagnostic by remember{mutableStateOf<String?>(null)};var discovered by remember{mutableStateOf<List<String>>(emptyList())};var tested by remember{mutableStateOf(false)};var err by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
    fun payload()=JSONObject().put("name",name.trim()).put("host",host.trim()).put("port",port.toIntOrNull()?:8728).put("username",user.trim()).put("password",pass).put("tls",tls)
    val canContinue = when(step){0 -> name.isNotBlank(); 1 -> host.isNotBlank()&&user.isNotBlank()&&pass.isNotBlank(); 2 -> packageName.isNotBlank()&&download.isNotBlank()&&upload.isNotBlank()&&duration.toIntOrNull()!=null; else -> tested}
    AlertDialog(onDismissRequest=onCancel, containerColor=Color(0xFFF8F9FF), title={
        Surface(color=Ink,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) { NetworkBadge(size=48.dp); Spacer(Modifier.width(11.dp)); Column { Text("MICRO-MAX",fontSize=19.sp,fontWeight=FontWeight.Black,color=Color.White,letterSpacing=1.1.sp); Text("NETWORK SETUP  •  3.0.2",fontSize=9.sp,fontWeight=FontWeight.Bold,color=Color(0xFF22D3EE),letterSpacing=1.4.sp); Text("إضافة راوتر MikroTik",fontSize=12.sp,color=Color(0xFFD1DBEF)) } } }
    },text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("اربط شبكتك وابدأ الإدارة الذكية",fontSize=21.sp,fontWeight=FontWeight.Black,color=Ink)
        Text("إعداد آمن من أربع مراحل — كلمة المرور تُرسل للخادم فقط ولا تظهر في لوحة التطبيق.",fontSize=12.sp,color=TextMuted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("هوية","اتصال","باقة","مراجعة").forEachIndexed{i,label->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(if(i<=step)Accent else Color(0xFFE1E5EE)));Text(label,fontSize=9.sp,color=if(i<=step)Accent else TextMuted,fontWeight=FontWeight.Bold)}}}
        Text("الخطوة ${step+1} من 4",fontSize=11.sp,color=Accent,fontWeight=FontWeight.Bold)
        when(step){
            0->{Text("سمِّ الراوتر",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("سيظهر هذا الاسم في لوحة التحكم وقائمة الراوترات.",fontSize=12.sp,color=TextMuted);AppField(name,{name=it},"اسم واضح، مثل الفرع الرئيسي",Icons.Default.Badge)}
            1->{Text("كيف سيتصل التطبيق؟",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("اختر نوع الشبكة ثم أدخل بيانات RouterOS. لا يتم حفظ كلمة المرور إلا داخل الخادم.",fontSize=12.sp,color=TextMuted);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=mode=="local",onClick={mode="local";if(host.isBlank())host="192.168.88.1"},label={Text("شبكة محلية")},leadingIcon={Icon(Icons.Default.Home,null)});FilterChip(selected=mode=="public",onClick={mode="public";if(host=="192.168.88.1")host=""},label={Text("عنوان عام")},leadingIcon={Icon(Icons.Default.Language,null)})};if(mode=="local"){OutlinedButton(onClick={scope.launch{diagnostic="جاري اكتشاف MikroTik من Backend…";try{val o=JSONObject(api.post("/api/routers/discover",JSONObject().put("network","192.168.88.0/24").toString()));val a=o.optJSONArray("routers");discovered=(0 until (a?.length()?:0)).map{a!!.getJSONObject(it).optString("host")+":"+a.getJSONObject(it).optInt("port")};diagnostic=if(discovered.isEmpty())"لم يتم العثور على API في 192.168.88.0/24" else "تم العثور على ${discovered.size} جهاز MikroTik"}catch(e:Exception){diagnostic=routerConnectionMessage(e.message?:"فشل الاكتشاف")}}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Radar,null);Spacer(Modifier.width(6.dp));Text("اكتشاف MikroTik تلقائيًا")};discovered.forEach{found->FilterChip(selected=host==found.substringBefore(":"),onClick={host=found.substringBefore(":");port=found.substringAfter(":");tls=port=="8729"},label={Text("راوتر $found")},leadingIcon={Icon(Icons.Outlined.Router,null)})}};AppField(host,{host=it},if(mode=="local")"IP الراوتر، مثال 192.168.88.1" else "Hostname أو IP عام",Icons.Default.Cloud);Text("المنفذ",fontSize=12.sp,color=TextMuted,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=port=="8729",onClick={port="8729";tls=true},label={Text("8729 • API-SSL")});FilterChip(selected=port=="8728",onClick={port="8728";tls=false},label={Text("8728 • API")})};AppField(user,{user=it},"اسم المستخدم",Icons.Default.Person);OutlinedTextField(pass,{pass=it},label={Text("كلمة مرور MikroTik")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true,colors=fieldColors());Row(verticalAlignment=Alignment.CenterVertically){Checkbox(tls,{tls=it;port=if(it)"8729" else "8728"});Text("تشفير API-SSL موصى به للإنتاج")};OutlinedButton(onClick={scope.launch{diagnostic="جاري الفحص من Backend…";try{diagnostic=formatRouterDiagnostic(JSONObject(api.post("/api/routers/diagnose",payload().toString())))}catch(e:Exception){diagnostic=routerConnectionMessage(e.message?:"فشل التشخيص")}}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.NetworkCheck,null);Spacer(Modifier.width(6.dp));Text("تشخيص الشبكة من Backend")};diagnostic?.let{Text(it,fontSize=11.sp,color=if(it.startsWith("نجح"))Green else Amber)};if(tested) Text("تم اختبار الاتصال بنجاح",color=Green,fontWeight=FontWeight.Bold)}
            2->{Text("أنشئ أول باقة",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("سيتم إنشاء Profile في MikroTik وPlan للبيع داخل MICRO-MAX.",fontSize=12.sp,color=TextMuted);AppField(packageName,{packageName=it},"اسم الباقة، مثل اقتصادية أو VIP",Icons.Default.LocalOffer);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){AppField(download,{download=it},"Download، مثال 2M",Icons.Default.Download)};Box(Modifier.weight(1f)){AppField(upload,{upload=it},"Upload، مثال 1M",Icons.Default.Upload)}};Text("سرعات جاهزة",fontSize=12.sp,color=TextMuted,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("1M/512K","2M/1M","5M/2M","10M/5M").forEach{speed->FilterChip(selected=download+"/"+upload==speed,onClick={val p=speed.split("/");download=p[0];upload=p[1]},label={Text(speed)})}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){AppField(duration,{duration=it.filter(Char::isDigit)},"المدة بالدقائق",Icons.Default.Timer)};Box(Modifier.weight(1f)){AppField(packagePrice,{packagePrice=it.filter(Char::isDigit)},"السعر XOF",Icons.Default.Payments)}};AppField(sharedUsers,{sharedUsers=it.filter(Char::isDigit)},"عدد الأجهزة المسموحة",Icons.Default.People)}
            else->{Text("جاهز للحفظ",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("راجع الاتصال والباقة قبل إنشاء الإعدادات.",fontSize=12.sp,color=TextMuted);GlassCard{Text(name.ifBlank{"بدون اسم"},fontSize=17.sp,fontWeight=FontWeight.Bold);Text("${host.ifBlank{"-"}}:${port.ifBlank{"8729"}}",color=Accent);Text("${user.ifBlank{"-"}} • ${if(tls)"API-SSL آمن" else "API"}",color=TextMuted);HorizontalDivider();Text("الباقة: $packageName",fontWeight=FontWeight.Bold);Text("سرعة: $download تنزيل / $upload رفع • مدة: $duration دقيقة",color=Accent);Text("السعر: $packagePrice XOF • أجهزة: $sharedUsers",color=TextMuted);Text("سيتم اختبار الراوتر ثم إنشاء Profile وPlan تلقائيًا.",fontSize=12.sp,color=TextMuted)}}
        }
        err?.let{Text("⚠ ${routerConnectionMessage(it)}",color=Color(0xFFFFA4B0),fontSize=12.sp)}
    }},confirmButton={
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(step>0)OutlinedButton({step--;err=null}){Text("رجوع")}
            Button({scope.launch{busy=true;err=null;try{if(step==1){api.post("/api/routers/test",payload().toString());tested=true;step++}else if(step==3){val created=JSONObject(api.post("/api/routers",payload().toString()));val id=created.optString("id");val rate="$download/$upload";api.post("/api/routers/$id/hotspot-profiles",JSONObject().put("name",packageName).put("durationMinutes",duration.toIntOrNull()?:60).put("rateLimit",rate).put("sharedUsers",sharedUsers.toIntOrNull()?:1).toString());api.post("/api/routers/$id/plans",JSONObject().put("profileName",packageName).put("name",packageName).put("price",packagePrice.toDoubleOrNull()?:0.0).put("currency","XOF").toString());onSaved()}else step++}catch(e:Exception){err=e.message?:"تعذر الاتصال أو إنشاء الباقة"}finally{busy=false}}},enabled=!busy && canContinue){Text(if(busy)"جاري التحقق…" else if(step==3)"حفظ الراوتر والباقة" else if(step==1)"اختبار الاتصال" else "متابعة")}
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
            Surface(color = Ink, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("المساعد التشغيلي", color = Color(0xFFB8CBFF), fontSize = 12.sp); Text(router.optString("name", "MikroTik"), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black); Text("راقب، حلّل، ثم نفّذ من مكان واحد", color = Color(0xFFD1DBEF), fontSize = 12.sp) } }
            GlassCard { Text("إجراءات ذكية", fontWeight = FontWeight.Black, fontSize = 19.sp); ActionTile(Icons.Outlined.Terminal, "فحص RouterOS من Terminal", Ink, Modifier.fillMaxWidth()) { openTerminal }; ActionTile(Icons.Outlined.Lan, "تحليل الواجهات والـ Queues", Teal, Modifier.fillMaxWidth()) { openNetwork }; ActionTile(Icons.Outlined.QrCode2, "توليد دفعة كروت مع فحص مسبق", Royal, Modifier.fillMaxWidth()) { openCards }; Button(enabled = !busy, onClick = { scope.launch { busy = true; try { val raw = api.get("/api/routers/${router.optString("id")}/dashboard"); output = JSONObject(raw).let { "CPU ${it.optJSONObject("resource")?.optString("cpu-load", "-")}% • المستخدمون ${it.optInt("active", it.optInt("activeUsers", 0))}" } } catch (e: Exception) { output = e.message } finally { busy = false } } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "جاري التحليل…" else "تحليل حالة الراوتر الآن") }; output?.let { Text(it, color = Accent, fontWeight = FontWeight.Bold) } }
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
    return when { s.contains("404", true) -> "الـBackend يعمل بنسخة قديمة: أعد نشر Backend من GitHub ثم جرّب مرة أخرى."; s.contains("ECONNREFUSED", true) -> "رفض الراوتر الاتصال: فعّل خدمة API على 8728 وتأكد من IP والجدار الناري."; s.contains("ETIMEDOUT", true) || s.contains("TIMEOUT", true) -> "انتهت مهلة الاتصال: الهاتف والـBackend والراوتر يجب أن يكونوا على شبكة قابلة للوصول."; s.contains("AUTH", true) -> "فشل تسجيل الدخول: تحقق من اسم المستخدم وكلمة المرور وصلاحية api."; s.contains("certificate", true) || s.contains("TLS", true) -> "خطأ شهادة TLS: استخدم API 8728 للاختبار المحلي أو ثبّت شهادة API-SSL."; else -> s }
}

@Composable fun EmptyCard(s:String){Card(colors=CardDefaults.cardColors(containerColor=Card2),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Text(s,Modifier.padding(18.dp),color=TextMuted)}}
