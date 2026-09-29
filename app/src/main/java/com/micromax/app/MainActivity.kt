package com.micromax.app

import android.graphics.Bitmap
import android.os.Bundle
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

private val Bg = Color(0xFF050A14)
private val Card = Color(0xFF101B2C)
private val Card2 = Color(0xFF0B1524)
private val Accent = Color(0xFF22D3EE)
private val Blue = Color(0xFF3B82F6)
private val Green = Color(0xFF22C55E)
private val Red = Color(0xFFF43F5E)
private val Amber = Color(0xFFF59E0B)
private val TextMain = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 10, 20)
        window.navigationBarColor = android.graphics.Color.rgb(5, 10, 20)
        window.decorView.systemUiVisibility = 0
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
        colorScheme = darkColorScheme(primary = Accent, secondary = Blue, tertiary = Color(0xFF8B5CF6), background = Bg, surface = Card, surfaceVariant = Card2, onSurface = TextMain, onBackground = TextMain),
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
    displaySmall = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black),
    headlineSmall = androidx.compose.ui.text.TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Black),
    titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp), bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
)
private fun microMaxShapes() = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(13.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp)
)

@Composable
private fun AuthScreen(api: Api, prefs: android.content.SharedPreferences, onAuthenticated: (String) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(api.base) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF050A14), Color(0xFF0B1B2D), Color(0xFF10142B)))).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 44.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(20.dp))
            Logo()
            Text("MICRO-MAX", fontSize = 30.sp, fontWeight = FontWeight.Black, color = TextMain)
            Text("HOTSPOT CONTROL CENTER", fontSize = 10.sp, letterSpacing = 2.sp, color = Accent, fontWeight = FontWeight.Bold)
            Text(if (register) "أنشئ حساب المدير الأول" else "سجّل الدخول إلى مساحة عملك", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Text("اربط التطبيق بخادمك ثم تحكم في الراوترات والكروت والباقات بأمان.", fontSize = 12.sp, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            GlassCard {
                AppField(server, { server = it }, "رابط Backend — مثال: https://api.example.com", Icons.Default.Cloud)
                Text(if (server.contains("10.0.2.2")) "وضع المحاكي: هذا العنوان يصل إلى Backend على جهاز التطوير. في هاتف حقيقي استخدم IP الكمبيوتر على نفس Wi‑Fi أو رابط VPS/HTTPS." else "استخدم HTTPS في الإنتاج، أو عنوان IP محلي قابل للوصول من الهاتف أثناء الاختبار.", fontSize = 10.sp, color = TextMuted)
                AppField(email, { email = it }, "البريد الإلكتروني", Icons.Default.Email)
                OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors())
                message?.let { Text(it, color = if (it.startsWith("تم")) Green else Color(0xFFFF8A8A), fontSize = 12.sp) }
                Button(onClick = {
                    scope.launch {
                        busy = true; message = null
                        try {
                            val clean = server.trim().trimEnd('/')
                            require(clean.startsWith("http://") || clean.startsWith("https://")) { "اكتب رابط Backend يبدأ بـ http:// أو https://" }
                            require(email.contains("@") && password.length >= 8) { "أدخل بريدًا صحيحًا وكلمة مرور من 8 أحرف على الأقل" }
                            api.base = clean
                            val body = JSONObject().put("email", email.trim()).put("password", password).toString()
                            val path = if (register) "/api/auth/register" else "/api/auth/login"
                            val result = JSONObject(api.post(path, body))
                            val token = result.optString("token")
                            require(token.isNotBlank()) { "الخادم لم يُرجع رمز الجلسة" }
                            onAuthenticated(token)
                        } catch (e: Exception) { message = e.message ?: "تعذر الاتصال بالخادم" }
                        finally { busy = false }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text(if (busy) "جاري الاتصال…" else if (register) "إنشاء الحساب والدخول" else "دخول آمن")
                }
                OutlinedButton(onClick = { register = !register; message = null }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Accent.copy(alpha = .45f))) {
                    Text(if (register) "لدي حساب — تسجيل الدخول" else "أول مرة؟ إنشاء حساب مدير")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.VerifiedUser, null, tint = Green, modifier = Modifier.size(16.dp))
                Text("بياناتك تُرسل إلى رابط Backend الذي تختاره فقط", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

@Composable fun fieldColors()=OutlinedTextFieldDefaults.colors(focusedBorderColor=Accent,unfocusedBorderColor=Color(0xFF64748B),focusedLabelColor=Accent,unfocusedLabelColor=TextMuted,cursorColor=Accent,focusedTextColor=TextMain,unfocusedTextColor=TextMain)
@Composable fun Logo(){Box(Modifier.size(92.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF8B5CF6),Accent,Blue))),Alignment.Center){Box(Modifier.size(76.dp).clip(CircleShape).background(Color(0xFF08111F).copy(alpha=.78f)),Alignment.Center){Text("M",fontSize=38.sp,fontWeight=FontWeight.Black,color=Color.White)}}}
@Composable fun AppField(v:String,on:(String)->Unit,label:String,icon:androidx.compose.ui.graphics.vector.ImageVector){OutlinedTextField(v,on,label={Text(label)},leadingIcon={Icon(icon,null)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=fieldColors())}
@Composable fun GlassCard(content:@Composable ColumnScope.()->Unit){
    Card(colors=CardDefaults.cardColors(containerColor=Card.copy(alpha=.92f)), shape=RoundedCornerShape(24.dp), border=BorderStroke(1.dp,Color.White.copy(alpha=.08f)), modifier=Modifier.fillMaxWidth(), elevation=CardDefaults.cardElevation(defaultElevation=8.dp)){
        Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp), content=content)
    }
}

private data class Nav(val title:String,val icon:androidx.compose.ui.graphics.vector.ImageVector,val page:Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(api: Api, activity: MainActivity, onLogout: () -> Unit) {
    val nav = listOf(
        Nav("الرئيسية", Icons.Default.Dashboard, 0), Nav("الراوترات", Icons.Default.Router, 1),
        Nav("Profiles", Icons.Default.Speed, 2), Nav("الكروت", Icons.Default.QrCode2, 3),
        Nav("المتجر", Icons.Default.Storefront, 4), Nav("المبيعات", Icons.Default.PointOfSale, 5),
        Nav("التقارير", Icons.Default.Assessment, 6), Nav("الأمان", Icons.Default.Security, 7),
        Nav("الشبكة", Icons.Default.Lan, 8), Nav("الإعدادات", Icons.Default.Settings, 9),
        Nav("HotSpot", Icons.Default.Code, 10), Nav("Terminal", Icons.Default.Terminal, 11),
        Nav("Studio", Icons.Default.AutoAwesome, 12), Nav("Visual Editor", Icons.Default.Web, 13)
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
    var themeDark by remember { mutableStateOf(sessionPrefs.getBoolean("theme_dark", true)) }
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

    val scheme = if (themeDark) darkColorScheme(primary = Accent, secondary = Blue, background = Bg, surface = Card, onSurface = TextMain)
                 else lightColorScheme(primary = Color(0xFF075E9E), secondary = Blue, background = Color(0xFFF4F7FB), surface = Color.White, onSurface = Color(0xFF14283B))

    MaterialTheme(colorScheme = scheme, typography = microMaxTypography(), shapes = microMaxShapes()) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(if (themeDark) listOf(Color(0xFF12243B), Bg, Bg) else listOf(Color(0xFFE8F4FF), Color(0xFFF5F8FC), Color(0xFFF5F8FC)), radius = 1100f))) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (themeDark) Color(0xCC08111F) else Color(0xF7FFFFFF), titleContentColor = MaterialTheme.colorScheme.onSurface, actionIconContentColor = Accent),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("MICRO-MAX", fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(8.dp))
                            Text("VIP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Accent,
                                modifier = Modifier.background(Accent.copy(alpha = .12f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp))
                        }
                    },
                    actions = {
                        IconButton({ refresh() }) { Icon(Icons.Default.Refresh, null) }
                    }
                )
            },
            bottomBar = {
                Box {
                    NavigationBar(containerColor = if (themeDark) Color(0xEE08111F) else Color(0xF7FFFFFF), tonalElevation = 10.dp) {
                        nav.take(5).forEach { n ->
                            NavigationBarItem(selected = page == n.page, onClick = { page = n.page }, icon = { Icon(n.icon, null) }, label = { Text(n.title, fontSize = 9.sp, fontWeight = FontWeight.Bold) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Accent.copy(alpha = if (themeDark) .16f else .10f), unselectedIconColor = TextMuted, unselectedTextColor = TextMuted))
                        }
                        NavigationBarItem(selected = page >= 5, onClick = { moreOpen = !moreOpen }, icon = { Icon(Icons.Default.MoreHoriz, null) }, label = { Text("المزيد", fontSize = 9.sp) })
                    }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        nav.drop(5).forEach { n ->
                            DropdownMenuItem(text = { Text(n.title) }, leadingIcon = { Icon(n.icon, null) }, onClick = { page = n.page; moreOpen = false })
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
                    4 -> StorePage(api)
                    5 -> SalesPage(api)
                    6 -> ReportsPage(api)
                    7 -> SecurityPage(api)
                    8 -> NetworkPage(api, selected)
                    9 -> SettingsPage(themeDark, { themeDark = !themeDark; sessionPrefs.edit().putBoolean("theme_dark", themeDark).apply() }, api.base, onLogout)
                    10 -> HotspotEditorPage(api, selected)
                    11 -> TerminalPage(api, selected)
                    12 -> DesignStudioPage(api, selected, activity)
                    13 -> HotspotVisualEditorPage(api, selected)
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

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)
    ) {
        item {
            DashboardHeader()
        }
        error?.let { item { ErrorCard(it) } }

        item {
            if (selected != null && dash != null) {
                RouterHeroCard(selected, resource, cpu, ram, storage, dash)
            } else {
                EmptyRouterCard(add)
            }
        }

        if (selected != null && dash != null) {
            item { Text("إجراءات سريعة", fontSize = 20.sp, fontWeight = FontWeight.Black) }
            item {
                QuickActions(
                    onRouter = add,
                    onCards = { openPage(3) },
                    onUsers = { openPage(2) },
                    onStore = { openPage(4) },
                    onReports = { openPage(6) },
                    onDesign = { openPage(12) }
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("الراوترات", fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("الحالة والموارد في نظرة واحدة", fontSize = 12.sp, color = TextMuted)
                }
                TextButton(onClick = { }) { Text("عرض الكل") }
            }
        }
        items((0 until routers.length()).map { routers.getJSONObject(it) }) { r ->
            RouterCompactRow(r, selected?.optString("id") == r.optString("id"), { select(r) })
        }

        if (selected != null && dash != null) {
            item { Text("واجهات الشبكة", fontSize = 20.sp, fontWeight = FontWeight.Black) }
            val ints = dash.optJSONArray("interfaces") ?: JSONArray()
            items((0 until ints.length()).take(6)) { InterfaceRow(ints.getJSONObject(it)) }
            item { Text("آخر مستخدمي Hotspot", fontSize = 20.sp, fontWeight = FontWeight.Black) }
            items((0 until users.length()).take(6)) { UserRow(users.getJSONObject(it)) }
        }
    }
}

@Composable
private fun DashboardHeader() {
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Color.Transparent)){
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF123C54),Color(0xFF0D1728),Color(0xFF241638))),RoundedCornerShape(28.dp)).padding(18.dp)){
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Accent,Blue))),contentAlignment=Alignment.Center){Icon(Icons.Default.Dashboard,null,tint=Color.White,modifier=Modifier.size(25.dp))}
                    Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("لوحة التحكم",fontSize=24.sp,fontWeight=FontWeight.Black,color=Color.White);Text("NETWORK OPERATIONS",fontSize=9.sp,letterSpacing=1.2.sp,color=Accent,fontWeight=FontWeight.Bold)}
                    Surface(color=Color.White.copy(alpha=.10f),shape=RoundedCornerShape(12.dp)){Text("LIVE",modifier=Modifier.padding(horizontal=9.dp,vertical=6.dp),color=Green,fontWeight=FontWeight.Bold,fontSize=10.sp)}
                }
                Text("إدارة الراوترات والكروت والمبيعات من مساحة واحدة",fontSize=13.sp,color=Color.White.copy(alpha=.82f))
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("RouterOS","Cards Studio","Reports").forEach{Surface(color=Color.White.copy(alpha=.08f),shape=RoundedCornerShape(50)){Text(it,modifier=Modifier.padding(horizontal=9.dp,vertical=5.dp),fontSize=9.sp,color=Color.White.copy(alpha=.75f))}}}
            }
        }
    }
}

@Composable
private fun RouterHeroCard(r: JSONObject, resource: JSONObject?, cpu: Float, ram: Float, storage: Float, d: JSONObject) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Card2),
        border = androidx.compose.foundation.BorderStroke(1.dp, Accent.copy(alpha = .22f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .95f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Router, null, tint = Blue, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.optString("name", "MikroTik"), fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(7.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Green))
                    }
                    Text(r.optString("host", "-"), fontSize = 12.sp, color = TextMuted)
                    Text("RouterOS ${resource?.optString("version", "-") ?: "-"}", fontSize = 12.sp, color = TextMuted)
                }
                Surface(color = Accent.copy(alpha = .10f), shape = RoundedCornerShape(14.dp)) {
                    Text("متصل", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricGauge("CPU", cpu, Accent, Modifier.weight(1f))
                MetricGauge("RAM", ram, Green, Modifier.weight(1f))
                MetricGauge("التخزين", storage, Amber, Modifier.weight(1f))
            }
            HorizontalDivider(color = Color.White.copy(alpha = .08f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniMetric(Icons.Default.People, "المستخدمون", d.optInt("users", d.optInt("activeUsers", 0)).toString())
                MiniMetric(Icons.Default.Wifi, "المتصلون", d.optInt("active", d.optInt("activeUsers", 0)).toString())
                MiniMetric(Icons.Default.DataUsage, "الترافيك", trafficText(d))
            }
        }
    }
}

@Composable
private fun MetricGauge(label: String, value: Float, tint: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${value.toInt()}%", fontSize = 20.sp, fontWeight = FontWeight.Black, color = tint)
        LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)), color = tint, trackColor = tint.copy(alpha = .13f))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
private fun MiniMetric(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(20.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(title, fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
private fun QuickActions(onRouter: () -> Unit, onCards: () -> Unit, onUsers: () -> Unit, onStore: () -> Unit, onReports: () -> Unit, onDesign: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            QuickAction("إضافة راوتر", Icons.Default.Add, Blue, onRouter, Modifier.weight(1f))
            QuickAction("كرت جديد", Icons.Default.CreditCard, Green, onCards, Modifier.weight(1f))
            QuickAction("المستخدمون", Icons.Default.People, Color(0xFF8B5CF6), onUsers, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            QuickAction("متجر الكروت", Icons.Default.ShoppingCart, Amber, onStore, Modifier.weight(1f))
            QuickAction("تصميم الكرت", Icons.Default.Palette, Color(0xFFEC4899), onDesign, Modifier.weight(1f))
            QuickAction("التقارير", Icons.Default.Description, TextMuted, onReports, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, click: () -> Unit, modifier: Modifier) {
    Card(modifier.clickable(onClick = click), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = .11f)), border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .25f))) {
        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(5.dp))
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun RouterCompactRow(r: JSONObject, selected: Boolean, click: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = click), colors = CardDefaults.cardColors(containerColor = if (selected) Accent.copy(alpha = .10f) else Card), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Accent.copy(alpha = .25f) else Color.White.copy(alpha = .06f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .08f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Router, null, tint = Accent) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(r.optString("name", "MikroTik"), fontWeight = FontWeight.Bold); Spacer(Modifier.width(6.dp)); Box(Modifier.size(7.dp).clip(CircleShape).background(Green)) }
                Text(r.optString("host", "-"), fontSize = 11.sp, color = TextMuted)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
        }
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

@Composable fun CardsStudioPage(api:Api,router:JSONObject?,activity:MainActivity){var plans by remember{mutableStateOf(JSONArray())};var selectedPlan by remember{mutableStateOf<JSONObject?>(null)};var portal by remember{mutableStateOf("")};var ssid by remember{mutableStateOf("")};var mode by remember{mutableStateOf("userpass")};var count by remember{mutableStateOf("10")};var prefix by remember{mutableStateOf("KMX")};var digits by remember{mutableStateOf("6")};var result by remember{mutableStateOf<JSONArray?>(null)};var batches by remember{mutableStateOf<JSONArray?>(null)};var err by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var preview by remember{mutableStateOf<JSONObject?>(null)};val scope=rememberCoroutineScope();LaunchedEffect(router?.optString("id")){if(router!=null)scope.launch{try{plans=JSONObject(api.get("/api/routers/${router.optString("id")}/plans")).optJSONArray("plans")?:JSONArray()}catch(e:Exception){err=e.message}}};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Card Studio",fontSize=28.sp,fontWeight=FontWeight.Black);Text("QR + Barcode + Preflight + توليد + Batch Center — بدون اشتراك",color=TextMuted);if(router==null)EmptyCard("اختر راوتر أولاً") else {GlassCard{Text("بيانات الكروت",fontWeight=FontWeight.Bold);AppField(portal,{portal=it},"رابط HotSpot الحقيقي",Icons.Default.Link);AppField(ssid,{ssid=it},"اسم شبكة الواي فاي SSID (لـ QR الاتصال التلقائي)",Icons.Default.Wifi);Text("طريقة الكروت",fontSize=13.sp,color=TextMuted);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(mode=="userpass",{mode="userpass"},{Text("1) يوزر + باسورد + QR")});FilterChip(mode=="pin",{mode="pin";if((digits.toIntOrNull()?:0)<8)digits="8"},{Text("2) كود فقط + QR")})};if(mode=="pin")Text("الكود فقط: صفحة login-code.html، ويلزم 8 خانات على الأقل",fontSize=11.sp,color=Amber);AppField(count,{count=it},"عدد الكروت",Icons.Default.Numbers);AppField(prefix,{prefix=it},"Prefix",Icons.Default.Tag);AppField(digits,{digits=it},"عدد أرقام اسم المستخدم",Icons.Default.Numbers);Text("الخطة",fontSize=13.sp,color=TextMuted);if(plans.length()==0)Text("أنشئ Plan أولاً من Profiles",color=Amber);for(i in 0 until plans.length()){val p=plans.getJSONObject(i);FilterChip(selectedPlan?.optString("id")==p.optString("id"),{selectedPlan=p},{Text("${p.optString("name")} • ${p.optDouble("price")} ${p.optString("currency","XOF")}")})};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({scope.launch{busy=true;try{val id=router.optString("id");api.post("/api/routers/$id/qr-preflight",JSONObject().put("portalUrl",portal).toString());val raw=api.post("/api/routers/$id/cards/preflight",JSONObject().put("count",count.toIntOrNull()?:1).put("planId",selectedPlan?.optString("id")).put("prefix",prefix).put("usernameDigits",digits.toIntOrNull()?:6).put("usernameLetters",0).put("portalUrl",portal).put("passwordMode",mode).toString());err="Preflight جاهز: ${JSONObject(raw).optBoolean("ready",true)}"}catch(e:Exception){err=e.message}}},enabled=!busy&&selectedPlan!=null&&portal.isNotBlank()){Text("فحص ذكي")};Button({scope.launch{busy=true;err=null;try{val id=router.optString("id");val raw=api.post("/api/routers/$id/cards/generate",JSONObject().put("count",count.toIntOrNull()?:1).put("planId",selectedPlan?.optString("id")).put("prefix",prefix).put("usernameDigits",digits.toIntOrNull()?:6).put("usernameLetters",0).put("portalUrl",portal).put("ssid",ssid.trim()).put("passwordMode",mode).toString());result=JSONObject(raw).optJSONArray("cards")}catch(e:Exception){err=e.message}finally{busy=false}}},enabled=!busy&&selectedPlan!=null&&portal.isNotBlank()){Text(if(busy)"جاري…" else "توليد")}}};err?.let{Text(it,color=if(it.startsWith("Preflight"))Green else Red)};result?.let{arr->Text("تم إنشاء ${arr.length()} كرت",fontSize=20.sp,fontWeight=FontWeight.Bold);for(i in 0 until minOf(arr.length(),50)){val c=arr.getJSONObject(i);Card(Modifier.fillMaxWidth().clickable{preview=c},colors=CardDefaults.cardColors(containerColor=Card),shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.QrCode2,null,tint=Accent);Column(Modifier.weight(1f)){Text(c.optString("username"),fontWeight=FontWeight.Bold);Text("${c.optString("password")} • ${c.optString("profile")}",fontSize=12.sp,color=TextMuted)};Text("${c.optDouble("price")} ${c.optString("currency","XOF")}",color=Accent)}}}};Button({scope.launch{try{val rid=router?.optString("id")?:"";val a=JSONObject(api.post("/api/routers/$rid/cards/audit","{}"));val c=a.optJSONObject("counts");val iss=a.optJSONObject("issues");err="Preflight فحص المزامنة: "+(if(a.optBoolean("healthy"))"سليم" else "توجد فروقات")+" • غير مستخدم ${c?.optInt("unused")} • مستخدم ${c?.optInt("used")} • ناقص على الراوتر ${iss?.optJSONArray("missingOnRouter")?.length()} • زائد ${iss?.optJSONArray("orphanOnRouter")?.length()}"}catch(e:Exception){err=e.message}}},Modifier.fillMaxWidth(),enabled=router!=null){Text("فحص ذكي للمزامنة مع MikroTik")};Button({scope.launch{try{batches=JSONObject(api.get("/api/card-batches")).optJSONArray("batches")}catch(e:Exception){err=e.message}}},Modifier.fillMaxWidth()){Text("فتح Batch Center")};batches?.let{Text("آخر الدُفعات",fontSize=20.sp,fontWeight=FontWeight.Bold);for(i in 0 until minOf(it.length(),20)){val b=it.getJSONObject(i);GlassCard{Text(b.optString("plan_name",b.optString("planName","Batch")),fontWeight=FontWeight.Bold);Text("${b.optInt("total")} كرت • متاح ${b.optInt("available")} • مباع ${b.optInt("sold")}",color=TextMuted)}}}};preview?.let{CardPreviewDialog(it,{preview=null},{bitmap->activity.printBitmap(bitmap)})}}}

@Composable fun CardPreviewDialog(c:JSONObject,close:()->Unit,print:(Bitmap)->Unit){val qr=c.optString("qrContent");val q=remember(qr){generateCode(qr,BarcodeFormat.QR_CODE,520,520)};val wq=c.optString("wifiQr");val w=remember(wq){if(wq.isBlank())null else generateCode(wq,BarcodeFormat.QR_CODE,520,520)};val b=remember(c.optString("username")){generateCode(c.optString("username"),BarcodeFormat.CODE_128,700,150)};AlertDialog(onDismissRequest=close,title={Text("معاينة الكرت — أبيض وأسود")},text={Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.verticalScroll(rememberScrollState())){Card(colors=CardDefaults.cardColors(containerColor=Color.White),border=BorderStroke(1.dp,Color.Black),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){Text(c.optString("profile",c.optString("plan","WiFi Access")),fontWeight=FontWeight.Bold,color=Color.Black);HorizontalDivider(color=Color.Black);val pinMode=c.optString("passwordMode")=="pin";Text("${if(pinMode)"الكود" else "اسم المستخدم"}  ${c.optString("username")}",color=Color.Black);if(!pinMode)Text("كلمة المرور  ${c.optString("password")}",color=Color.Black);w?.let{Text("1) امسح للاتصال بالشبكة",fontSize=10.sp,color=Color.Black);Image(it.asImageBitmap(),null,Modifier.size(130.dp))};q?.let{Text(if(w!=null)"2) امسح لتسجيل الدخول" else "امسح لتسجيل الدخول",fontSize=10.sp,color=Color.Black);Image(it.asImageBitmap(),null,Modifier.size(150.dp))};b?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxWidth().height(45.dp))};Text("QR وBarcode واضحان للطباعة",fontSize=10.sp,color=Color.DarkGray)}}}},confirmButton={Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton({composeCardBitmap(c)?.let{print(it)}}){Icon(Icons.Default.Print,null);Spacer(Modifier.width(5.dp));Text("طباعة")};TextButton(close){Text("إغلاق")}}})}

fun generateCode(value:String,format:BarcodeFormat,w:Int,h:Int):Bitmap?{return try{val hints=mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,EncodeHintType.MARGIN to 1);val m:BitMatrix=MultiFormatWriter().encode(value,format,w,h,hints);val bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);for(x in 0 until w)for(y in 0 until h)bmp.setPixel(x,y,if(m[x,y])android.graphics.Color.BLACK else android.graphics.Color.WHITE);bmp}catch(_:Exception){null}}

fun MainActivity.printBitmap(bitmap:Bitmap){try{androidx.print.PrintHelper(this).apply{scaleMode=androidx.print.PrintHelper.SCALE_MODE_FIT}.printBitmap("MICRO-MAX Card",bitmap)}catch(e:Exception){Toast.makeText(this,e.message?:"تعذر فتح الطباعة",Toast.LENGTH_LONG).show()}}

fun composeCardBitmap(c:JSONObject):Bitmap?{val qr=generateCode(c.optString("qrContent"),BarcodeFormat.QR_CODE,520,520)?:return null;val bar=generateCode(c.optString("username"),BarcodeFormat.CODE_128,700,150)?:return null;val out=Bitmap.createBitmap(900,900,Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(out);canvas.drawColor(android.graphics.Color.WHITE);val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);p.color=android.graphics.Color.BLACK;p.typeface=android.graphics.Typeface.DEFAULT_BOLD;p.textSize=30f;canvas.drawText(c.optString("profile",c.optString("plan","WiFi Access")),40f,65f,p);p.strokeWidth=2f;canvas.drawRect(40f,84f,860f,87f,p);p.typeface=android.graphics.Typeface.DEFAULT;p.textSize=24f;val pinM=c.optString("passwordMode")=="pin";canvas.drawText("${if(pinM)"الكود" else "اسم المستخدم"}  ${c.optString("username")}",40f,135f,p);if(!pinM)canvas.drawText("كلمة المرور  ${c.optString("password")}",40f,175f,p);val wqr=c.optString("wifiQr").let{if(it.isBlank())null else generateCode(it,BarcodeFormat.QR_CODE,520,520)};if(wqr!=null){p.textSize=22f;canvas.drawText("1) اتصال بالشبكة",60f,205f,p);canvas.drawText("2) تسجيل الدخول",480f,205f,p);canvas.drawBitmap(wqr,null,android.graphics.Rect(60,220,420,580),p);canvas.drawBitmap(qr,null,android.graphics.Rect(480,220,840,580),p)}else{canvas.drawBitmap(qr,null,android.graphics.Rect(190,220,710,740),p)};canvas.drawBitmap(bar,null,android.graphics.Rect(100,765,800,850),p);return out}

@Composable fun StorePage(api:Api){var items by remember{mutableStateOf(JSONArray())};var msg by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope();val catalog=listOf("Modern Wave" to "عصري • تدرج أزرق" ,"Midnight Glass" to "داكن • زجاجي", "VIP Neon" to "جريء • نيون", "Classic Gold" to "كلاسيكي • فاخر", "Minimal Mono" to "بسيط • أبيض وأسود", "Clean White" to "نظيف • تجاري", "Coffee House" to "مقهى • دافئ", "Hotel Luxe" to "فندق • راقٍ", "School Clean" to "تعليمي • واضح", "Market Orange" to "متجر • حيوي", "Travel WiFi" to "سفر • سياحة", "Gaming Arena" to "ألعاب • شبابي");val swatches=listOf(Color(0xFF2563EB),Color(0xFF22D3EE),Color(0xFFE879F9),Color(0xFFD4A84F),Color(0xFF111827),Color(0xFF60A5FA),Color(0xFFB77945),Color(0xFF66D9C0),Color(0xFF1D4ED8),Color(0xFFFF9B5A),Color(0xFF7C3AED),Color(0xFFEF4444));LaunchedEffect(Unit){scope.launch{try{items=JSONObject(api.get("/api/store")).optJSONArray("items")?:JSONArray()}catch(e:Exception){msg=e.message}}};LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("المتجر",fontSize=28.sp,fontWeight=FontWeight.Black);Text("مكتبة قوالب جاهزة للمنشآت — اختر أسلوبًا ثم افتحه في استوديو التصميم",color=TextMuted);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("الكل","عصري","كلاسيكي","بسيط","منشآت").forEach{FilterChip(selected=it=="الكل",onClick={},label={Text(it)})}}};item{Text("قوالب الكروت الجاهزة",fontSize=20.sp,fontWeight=FontWeight.Black)};items(catalog.indices.toList()){i->val entry=catalog[i];Card(colors=CardDefaults.cardColors(containerColor=Card),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,swatches[i].copy(alpha=.35f)),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(swatches[i],Color(0xFF0B1624)))),contentAlignment=Alignment.Center){Icon(Icons.Default.CreditCard,null,tint=Color.White)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(entry.first,fontSize=16.sp,fontWeight=FontWeight.Bold);Text(entry.second,color=TextMuted,fontSize=11.sp);Text("جاهز للتخصيص والطباعة",color=swatches[i],fontSize=10.sp)};TextButton(onClick={msg="تم اختيار قالب ${entry.first} — افتح Studio للتخصيص"}){Text("اختيار")}}}};msg?.let{item{Text(it,color=Accent,fontSize=12.sp)}};if(items.length()>0){item{Text("إضافات الخادم",fontSize=20.sp,fontWeight=FontWeight.Black)};items((0 until items.length()).map{items.getJSONObject(it)}){o->GlassCard{Text(o.optString("name"),fontSize=18.sp,fontWeight=FontWeight.Bold);Text(o.optString("description"),color=TextMuted);Button({scope.launch{try{val x=api.post("/api/store/${o.optString("id")}/activate");msg=JSONObject(x).optString("message","تم التفعيل") }catch(e:Exception){msg=e.message}}},Modifier.fillMaxWidth()){Text("تفعيل")}}}}}}

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

@Composable fun SettingsPage(dark:Boolean,toggle:()->Unit,apiBase:String,onLogout:()->Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("الإعدادات",fontSize=28.sp,fontWeight=FontWeight.Black);GlassCard{Text("MICRO-MAX V2.16",fontWeight=FontWeight.Bold);Text("إدارة MikroTik عبر RouterOS API / API-SSL",color=TextMuted);Text("الخادم المتصل",fontSize=11.sp,color=TextMuted);Text(apiBase, color=Accent, fontSize=12.sp);Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.DarkMode,null,tint=Accent);Spacer(Modifier.width(10.dp));Text("الوضع الزجاجي الداكن",Modifier.weight(1f));Switch(dark,{toggle()})};OutlinedButton(onClick=onLogout,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Color(0xFFFF6B7A))){Icon(Icons.Default.Logout,null);Spacer(Modifier.width(8.dp));Text("تسجيل الخروج")}};GlassCard{Text("المميزات المتفق عليها",fontWeight=FontWeight.Bold);Text("✓ عدة MikroTik\n✓ Dashboard وCPU/RAM/Uptime/Users/Traffic\n✓ Profiles وPlans والأسعار\n✓ Cards + QR + Barcode + Batch Center\n✓ Store + Sales + Reports\n✓ Users/Roles + Audit\n✓ Network: Interfaces/DHCP/ARP/DNS/IP/Routes/Firewall/Queues/Logs\n✓ HotSpot login.html Editor + Backup/Restore\n✓ الدفع الإلكتروني اختياري",color=TextMuted)}}}

@Composable
fun AddRouterDialog(api:Api,onSaved:()->Unit,onCancel:()->Unit){
    var step by remember{mutableIntStateOf(0)};var mode by remember{mutableStateOf("local")};var name by remember{mutableStateOf("")};var host by remember{mutableStateOf("")};var port by remember{mutableStateOf("8729")};var user by remember{mutableStateOf("admin")};var pass by remember{mutableStateOf("")};var tls by remember{mutableStateOf(true)};var packageName by remember{mutableStateOf("اقتصادية")};var download by remember{mutableStateOf("2M")};var upload by remember{mutableStateOf("1M")};var duration by remember{mutableStateOf("60")};var packagePrice by remember{mutableStateOf("500")};var sharedUsers by remember{mutableStateOf("1")};var busy by remember{mutableStateOf(false)};var tested by remember{mutableStateOf(false)};var err by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
    fun payload()=JSONObject().put("name",name.trim()).put("host",host.trim()).put("port",port.toIntOrNull()?:8729).put("username",user.trim()).put("password",pass).put("tls",tls)
    val canContinue = when(step){0 -> name.isNotBlank(); 1 -> host.isNotBlank()&&user.isNotBlank()&&pass.isNotBlank(); 2 -> packageName.isNotBlank()&&download.isNotBlank()&&upload.isNotBlank()&&duration.toIntOrNull()!=null; else -> tested}
    AlertDialog(onDismissRequest=onCancel,title={
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Text("إضافة راوتر جديد",fontSize=23.sp,fontWeight=FontWeight.Black);Text("اربط MikroTik خلال دقيقة واحدة",fontSize=12.sp,color=TextMuted)}
    },text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("هوية","اتصال","الباقة","مراجعة").forEachIndexed{i,label->Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(if(i<=step)Accent else Color.White.copy(alpha=.10f)));Text("")}}
        Text("الخطوة ${step+1} من 4",fontSize=11.sp,color=Accent,fontWeight=FontWeight.Bold)
        when(step){
            0->{Text("سمِّ الراوتر",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("سيظهر هذا الاسم في لوحة التحكم وقائمة الراوترات.",fontSize=12.sp,color=TextMuted);AppField(name,{name=it},"اسم واضح، مثل الفرع الرئيسي",Icons.Default.Badge)}
            1->{Text("كيف سيتصل التطبيق؟",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("اختر نوع الشبكة ثم أدخل بيانات RouterOS. لا يتم حفظ كلمة المرور إلا داخل الخادم.",fontSize=12.sp,color=TextMuted);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=mode=="local",onClick={mode="local";if(host.isBlank())host="192.168.88.1"},label={Text("شبكة محلية")},leadingIcon={Icon(Icons.Default.Home,null)});FilterChip(selected=mode=="public",onClick={mode="public";if(host=="192.168.88.1")host=""},label={Text("عنوان عام")},leadingIcon={Icon(Icons.Default.Language,null)})};AppField(host,{host=it},if(mode=="local")"IP الراوتر، مثال 192.168.88.1" else "Hostname أو IP عام",Icons.Default.Cloud);Text("المنفذ",fontSize=12.sp,color=TextMuted,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=port=="8729",onClick={port="8729";tls=true},label={Text("8729 • API-SSL")});FilterChip(selected=port=="8728",onClick={port="8728";tls=false},label={Text("8728 • API")})};AppField(user,{user=it},"اسم المستخدم",Icons.Default.Person);OutlinedTextField(pass,{pass=it},label={Text("كلمة مرور MikroTik")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true,colors=fieldColors());Row(verticalAlignment=Alignment.CenterVertically){Checkbox(tls,{tls=it;port=if(it)"8729" else "8728"});Text("تشفير API-SSL موصى به للإنتاج")};if(tested) Text("تم اختبار الاتصال بنجاح",color=Green,fontWeight=FontWeight.Bold)}
            2->{Text("أنشئ أول باقة",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("سيتم إنشاء Profile في MikroTik وPlan للبيع داخل MICRO-MAX.",fontSize=12.sp,color=TextMuted);AppField(packageName,{packageName=it},"اسم الباقة، مثل اقتصادية أو VIP",Icons.Default.LocalOffer);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){AppField(download,{download=it},"Download، مثال 2M",Icons.Default.Download)};Box(Modifier.weight(1f)){AppField(upload,{upload=it},"Upload، مثال 1M",Icons.Default.Upload)}};Text("سرعات جاهزة",fontSize=12.sp,color=TextMuted,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("1M/512K","2M/1M","5M/2M","10M/5M").forEach{speed->FilterChip(selected=download+"/"+upload==speed,onClick={val p=speed.split("/");download=p[0];upload=p[1]},label={Text(speed)})}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){AppField(duration,{duration=it.filter(Char::isDigit)},"المدة بالدقائق",Icons.Default.Timer)};Box(Modifier.weight(1f)){AppField(packagePrice,{packagePrice=it.filter(Char::isDigit)},"السعر XOF",Icons.Default.Payments)}};AppField(sharedUsers,{sharedUsers=it.filter(Char::isDigit)},"عدد الأجهزة المسموحة",Icons.Default.People)}
            else->{Text("جاهز للحفظ",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("راجع الاتصال والباقة قبل إنشاء الإعدادات.",fontSize=12.sp,color=TextMuted);GlassCard{Text(name.ifBlank{"بدون اسم"},fontSize=17.sp,fontWeight=FontWeight.Bold);Text("${host.ifBlank{"-"}}:${port.ifBlank{"8729"}}",color=Accent);Text("${user.ifBlank{"-"}} • ${if(tls)"API-SSL آمن" else "API"}",color=TextMuted);HorizontalDivider();Text("الباقة: $packageName",fontWeight=FontWeight.Bold);Text("سرعة: $download تنزيل / $upload رفع • مدة: $duration دقيقة",color=Accent);Text("السعر: $packagePrice XOF • أجهزة: $sharedUsers",color=TextMuted);Text("سيتم اختبار الراوتر ثم إنشاء Profile وPlan تلقائيًا.",fontSize=12.sp,color=TextMuted)}}
        }
        err?.let{Text("⚠ $it",color=Color(0xFFFFA4B0),fontSize=12.sp)}
    }},confirmButton={
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(step>0)OutlinedButton({step--;err=null}){Text("رجوع")}
            Button({scope.launch{busy=true;err=null;try{if(step==1){api.post("/api/routers/test",payload().toString());tested=true;step++}else if(step==3){val created=JSONObject(api.post("/api/routers",payload().toString()));val id=created.optString("id");val rate="$download/$upload";api.post("/api/routers/$id/hotspot-profiles",JSONObject().put("name",packageName).put("durationMinutes",duration.toIntOrNull()?:60).put("rateLimit",rate).put("sharedUsers",sharedUsers.toIntOrNull()?:1).toString());api.post("/api/routers/$id/plans",JSONObject().put("profileName",packageName).put("name",packageName).put("price",packagePrice.toDoubleOrNull()?:0.0).put("currency","XOF").toString());onSaved()}else step++}catch(e:Exception){err=e.message?:"تعذر الاتصال أو إنشاء الباقة"}finally{busy=false}}},enabled=!busy && canContinue){Text(if(busy)"جاري التحقق…" else if(step==3)"حفظ الراوتر والباقة" else if(step==1)"اختبار الاتصال" else "متابعة")}
        }
    },dismissButton={TextButton(onCancel){Text("إلغاء")}})
}

@Composable fun EmptyCard(s:String){Card(colors=CardDefaults.cardColors(containerColor=Card2),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){Text(s,Modifier.padding(18.dp),color=TextMuted)}}
