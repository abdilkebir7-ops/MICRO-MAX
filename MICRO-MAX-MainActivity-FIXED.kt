/*
 * MICRO-MAX — Android Hotspot Manager
 * Complete MainActivity.kt
 *
 * Package: com.micromax.app
 *
 * Includes the agreed UI areas:
 * - HTTPS backend login
 * - Google OAuth entry point through the backend
 * - Multiple MikroTik routers
 * - Router save/test/delete
 * - Dashboard: CPU/RAM/Storage/Uptime/Users/Active/Traffic
 * - Hotspot users
 * - Cards + QR/Barcode/PDF actions through the backend
 * - Profiles
 * - Products / sales / payments
 * - Store / templates
 * - Reports
 * - Security / permissions / audit
 * - Dark/light mode
 *
 * IMPORTANT:
 * Google OAuth and the advanced pages require the corresponding backend
 * endpoints to exist. This file does not invent RouterOS credentials or
 * bypass the backend.
 */
package com.micromax.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private val Bg2 = Color(0xFF07111F)
private val Glass = Color(0xE6111C2D)
private val Glass2 = Color(0xFF0D1726)
private val Accent = Color(0xFF22D3EE)
private val Blue = Color(0xFF3B82F6)
private val Green = Color(0xFF22C55E)
private val Red = Color(0xFFF43F5E)
private val TextMain = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)

private const val DEFAULT_SERVER =
    "https://micromax-hotspot-manager.onrender.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MicroMaxApp() }
    }
}

class Api(private val baseUrl: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    var token: String? = null

    private fun request(path: String): Request.Builder {
        val url = baseUrl.trim().trimEnd('/') + path
        return Request.Builder()
            .url(url)
            .apply {
                token?.let { addHeader("Authorization", "Bearer $it") }
            }
    }

    suspend fun get(path: String): String =
        call(request(path).get().build())

    suspend fun post(path: String, json: String): String =
        call(
            request(path)
                .post(
                    json.toRequestBody(
                        "application/json; charset=utf-8".toMediaType()
                    )
                )
                .build()
        )

    suspend fun put(path: String, json: String): String =
        call(
            request(path)
                .put(
                    json.toRequestBody(
                        "application/json; charset=utf-8".toMediaType()
                    )
                )
                .build()
        )

    suspend fun delete(path: String): String =
        call(request(path).delete().build())

    private suspend fun call(req: Request): String =
        withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw Exception(parseError(body, response.code))
                }
                body
            }
        }

    private fun parseError(body: String, code: Int): String {
        return try {
            JSONObject(body)
                .optString("error")
                .ifBlank { "HTTP $code" }
        } catch (_: Exception) {
            "HTTP $code"
        }
    }
}

data class NavItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun MicroMaxApp() {
    var api by remember { mutableStateOf<Api?>(null) }
    var loggedIn by remember { mutableStateOf(false) }
    var lightMode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val scheme = if (lightMode) {
        lightColorScheme(
            primary = Blue,
            secondary = Accent,
            background = Color(0xFFF5F7FB),
            surface = Color.White,
            onSurface = Color(0xFF111827)
        )
    } else {
        darkColorScheme(
            primary = Accent,
            secondary = Blue,
            background = Bg,
            surface = Glass2,
            onSurface = TextMain
        )
    }

    MaterialTheme(colorScheme = scheme) {
        if (!loggedIn || api == null) {
            LoginScreen(
                onLogin = { url, username, password, result ->
                    scope.launch {
                        try {
                            val a = Api(url)
                            val raw = a.post(
                                "/api/login",
                                JSONObject()
                                    .put("username", username)
                                    .put("password", password)
                                    .toString()
                            )
                            a.token = JSONObject(raw)
                                .optString("token")
                                .ifBlank { null }

                            if (a.token.isNullOrBlank()) {
                                throw Exception(
                                    "الخادم لم يُرجع رمز الدخول"
                                )
                            }

                            api = a
                            loggedIn = true
                            result(null)
                        } catch (e: Exception) {
                            result(
                                e.message ?: "تعذر تسجيل الدخول"
                            )
                        }
                    }
                },
                onGoogle = { url, result ->
                    scope.launch {
                        try {
                            val a = Api(url)
                            val raw = a.get("/api/auth/google")
                            val authUrl = try {
                                JSONObject(raw).optString("url")
                            } catch (_: Exception) {
                                raw
                            }.trim()

                            if (authUrl.isBlank()) {
                                throw Exception(
                                    "الخادم لم يُرجع رابط Google"
                                )
                            }

                            result(authUrl)
                        } catch (e: Exception) {
                            result(
                                e.message
                                    ?: "تعذر بدء تسجيل Google"
                            )
                        }
                    }
                }
            )
        } else {
            MainShell(
                api = api!!,
                lightMode = lightMode,
                onToggleTheme = {
                    lightMode = !lightMode
                },
                onLogout = {
                    loggedIn = false
                    api = null
                }
            )
        }
    }
}

@Composable
fun LoginScreen(
    onLogin: (
        String,
        String,
        String,
        (String?) -> Unit
    ) -> Unit,
    onGoogle: (
        String,
        (String?) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current

    var server by remember {
        mutableStateOf(DEFAULT_SERVER)
    }
    var username by remember {
        mutableStateOf("admin")
    }
    var password by remember {
        mutableStateOf("")
    }
    var showPassword by remember {
        mutableStateOf(false)
    }
    var loading by remember {
        mutableStateOf(false)
    }
    var error by remember {
        mutableStateOf<String?>(null)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Bg2,
                        Bg,
                        Color(0xFF02050B)
                    )
                )
            )
            .systemBarsPadding()
            .imePadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                MicroLogo()
            }

            item {
                Text(
                    "MICRO-MAX",
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Black,
                    color = TextMain
                )
                Text(
                    "HOTSPOT MANAGER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Accent
                )
                Text(
                    "إدارة MikroTik باحتراف",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }

            item {
                GlassCard {
                    Text(
                        "تسجيل الدخول",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "ادخل بيانات حساب مدير التطبيق",
                        fontSize = 13.sp,
                        color = TextMuted
                    )

                    AppField(
                        server,
                        { server = it },
                        "رابط الخادم",
                        Icons.Default.Cloud
                    )

                    AppField(
                        username,
                        { username = it },
                        "اسم المستخدم",
                        Icons.Default.Person
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                        },
                        label = {
                            Text("كلمة المرور")
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                null
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    showPassword =
                                        !showPassword
                                }
                            ) {
                                Icon(
                                    if (showPassword)
                                        Icons.Default.VisibilityOff
                                    else
                                        Icons.Default.Visibility,
                                    null
                                )
                            }
                        },
                        visualTransformation =
                            if (showPassword)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    if (error != null) {
                        ErrorCard(error!!)
                    }

                    Button(
                        onClick = {
                            if (
                                server.isBlank() ||
                                username.isBlank() ||
                                password.isBlank()
                            ) {
                                error =
                                    "أكمل جميع البيانات"
                                return@Button
                            }

                            loading = true
                            error = null

                            onLogin(
                                server.trim(),
                                username.trim(),
                                password
                            ) {
                                error = it
                                loading = false
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            if (loading)
                                "جاري التحقق…"
                            else
                                "دخول آمن",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    OutlinedButton(
                        onClick = {
                            loading = true
                            error = null

                            onGoogle(server.trim()) { value ->
                                loading = false

                                if (
                                    value != null &&
                                    value.startsWith("http")
                                ) {
                                    try {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(value)
                                            )
                                        )
                                    } catch (_: Exception) {
                                        error =
                                            "تعذر فتح صفحة Google"
                                    }
                                } else {
                                    error =
                                        value
                                            ?: "تعذر بدء تسجيل Google"
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "تسجيل الدخول بواسطة Google",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "Google OAuth يحتاج أن يكون مسار " +
                            "/api/auth/google مفعلاً في الخادم.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            item {
                Text(
                    "MICRO-MAX • MikroTik Hotspot Manager",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun MicroLogo() {
    Box(
        modifier = Modifier
            .size(82.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Accent, Blue)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "M",
            fontSize = 39.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
    }
}

@Composable
fun AppField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, null)
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun GlassCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Glass
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    api: Api,
    lightMode: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit
) {
    val nav = listOf(
        NavItem("الرئيسية", Icons.Default.Home),
        NavItem("الراوترات", Icons.Default.Cloud),
        NavItem("المستخدمون", Icons.Default.Person),
        NavItem("الكروت", Icons.Default.Lock),
        NavItem("المزيد", Icons.Default.Settings)
    )

    var page by remember {
        mutableIntStateOf(0)
    }
    var routers by remember {
        mutableStateOf(JSONArray())
    }
    var selected by remember {
        mutableStateOf<JSONObject?>(null)
    }
    var dashboard by remember {
        mutableStateOf<JSONObject?>(null)
    }
    var users by remember {
        mutableStateOf(JSONArray())
    }
    var error by remember {
        mutableStateOf<String?>(null)
    }
    var loading by remember {
        mutableStateOf(false)
    }
    var showAddRouter by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            error = null

            try {
                routers =
                    JSONArray(api.get("/api/routers"))

                if (routers.length() > 0) {
                    val oldId =
                        selected?.optInt("id", -1)
                            ?: -1

                    selected =
                        (0 until routers.length())
                            .map {
                                routers.getJSONObject(it)
                            }
                            .firstOrNull {
                                it.optInt("id") == oldId
                            }
                            ?: routers.getJSONObject(0)

                    loadRouter(
                        api,
                        selected!!,
                        { dashboard = it },
                        { users = it }
                    )
                } else {
                    selected = null
                    dashboard = null
                    users = JSONArray()
                }
            } catch (e: Exception) {
                error =
                    e.message
                        ?: "تعذر تحميل البيانات"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        containerColor =
            if (lightMode)
                Color(0xFFF5F7FB)
            else
                Bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            "MICRO-MAX",
                            fontWeight =
                                FontWeight.Black
                        )

                        Spacer(
                            Modifier.width(8.dp)
                        )

                        Surface(
                            shape =
                                RoundedCornerShape(50),
                            color =
                                Accent.copy(
                                    alpha = .14f
                                )
                        ) {
                            Text(
                                "PRO",
                                modifier =
                                    Modifier.padding(
                                        horizontal = 9.dp,
                                        vertical = 3.dp
                                    ),
                                fontSize = 10.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                color = Accent
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refresh() }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            "تحديث"
                        )
                    }

                    IconButton(
                        onClick = onToggleTheme
                    ) {
                        Text(
                            if (lightMode)
                                "☀"
                            else
                                "☾"
                        )
                    }

                    IconButton(
                        onClick = onLogout
                    ) {
                        Text("↪")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor =
                    if (lightMode)
                        Color.White
                    else
                        Color(0xFF08111F)
            ) {
                nav.forEachIndexed {
                    index,
                    item ->
                    NavigationBarItem(
                        selected =
                            page == index,
                        onClick = {
                            page = index
                        },
                        icon = {
                            Icon(
                                item.icon,
                                null
                            )
                        },
                        label = {
                            Text(
                                item.title,
                                fontSize = 10.sp
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (page) {
                0 -> DashboardPage(
                    routers = routers,
                    selected = selected,
                    dash = dashboard,
                    users = users,
                    error = error,
                    loading = loading,
                    add = {
                        showAddRouter = true
                    },
                    select = { router ->
                        selected = router
                        scope.launch {
                            try {
                                loadRouter(
                                    api,
                                    router,
                                    { dashboard = it },
                                    { users = it }
                                )
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    }
                )

                1 -> RoutersPage(
                    routers = routers,
                    selected = selected,
                    add = {
                        showAddRouter = true
                    },
                    choose = { router ->
                        selected = router
                        page = 0

                        scope.launch {
                            try {
                                loadRouter(
                                    api,
                                    router,
                                    { dashboard = it },
                                    { users = it }
                                )
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    },
                    remove = { id ->
                        scope.launch {
                            try {
                                api.delete(
                                    "/api/routers/$id"
                                )
                                refresh()
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    }
                )

                2 -> UsersPage(
                    users,
                    selected,
                    error
                )

                3 -> CardsPage(
                    selected,
                    api,
                    error
                )

                else -> MorePage(
                    api = api,
                    selected = selected,
                    onLogout = onLogout
                )
            }
        }
    }

    if (showAddRouter) {
        AddRouterDialog(
            api = api,
            onSaved = {
                showAddRouter = false
                refresh()
            },
            onCancel = {
                showAddRouter = false
            }
        )
    }
}

@Composable
fun DashboardPage(
    routers: JSONArray,
    selected: JSONObject?,
    dash: JSONObject?,
    users: JSONArray,
    error: String?,
    loading: Boolean,
    add: () -> Unit,
    select: (JSONObject) -> Unit
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement =
            Arrangement.spacedBy(14.dp),
        contentPadding =
            PaddingValues(
                top = 18.dp,
                bottom = 24.dp
            )
    ) {
        item {
            Text(
                "لوحة التحكم",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "مراقبة وإدارة شبكة MikroTik من مكان واحد",
                fontSize = 13.sp,
                color = TextMuted
            )
        }

        if (error != null) {
            item {
                ErrorCard(error)
            }
        }

        item {
            RouterSelector(
                routers,
                selected,
                add,
                select
            )
        }

        if (
            selected != null &&
            dash != null
        ) {
            val res =
                dash.optJSONObject("resource")

            item {
                StatsGrid(res, dash)
            }

            item {
                ResourceCard(res, selected)
            }

            item {
                TrafficCard(dash)
            }
        } else if (
            routers.length() == 0
        ) {
            item {
                EmptyRouterCard(add)
            }
        }

        item {
            Text(
                "المستخدمون",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "حسابات Hotspot الموجودة على الراوتر المحدد",
                fontSize = 12.sp,
                color = TextMuted
            )
        }

        if (
            users.length() == 0 &&
            selected != null
        ) {
            item {
                EmptyCard(
                    "لا توجد حسابات Hotspot حالياً"
                )
            }
        }

        items(
            (0 until users.length())
                .take(8)
        ) {
            UserRow(
                users.getJSONObject(it)
            )
        }

        if (loading) {
            item {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun RouterSelector(
    routers: JSONArray,
    selected: JSONObject?,
    add: () -> Unit,
    select: (JSONObject) -> Unit
) {
    GlassCard {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Cloud,
                null,
                tint = Accent
            )

            Spacer(
                Modifier.width(10.dp)
            )

            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    "الراوتر الحالي",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Text(
                    selected?.optString(
                        "name",
                        "اختر راوتر"
                    ) ?: "لا يوجد راوتر",
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Button(
                onClick = add
            ) {
                Icon(
                    Icons.Default.Add,
                    null
                )
                Spacer(
                    Modifier.width(4.dp)
                )
                Text("إضافة")
            }
        }

        if (routers.length() > 0) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                for (i in 0 until routers.length()) {
                    val router =
                        routers.getJSONObject(i)

                    FilterChip(
                        selected =
                            selected?.optInt("id") ==
                                router.optInt("id"),
                        onClick = {
                            select(router)
                        },
                        label = {
                            Text(
                                router.optString(
                                    "name",
                                    "Router"
                                ),
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatsGrid(
    res: JSONObject?,
    dash: JSONObject
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Stat(
                "CPU",
                (
                    res?.optString(
                        "cpu-load",
                        "0"
                    ) ?: "0"
                ) + "%",
                Icons.Default.Settings,
                Modifier.weight(1f)
            )

            Stat(
                "المستخدمون",
                dash.optInt(
                    "users"
                ).toString(),
                Icons.Default.Person,
                Modifier.weight(1f)
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Stat(
                "متصل الآن",
                dash.optInt(
                    "active"
                ).toString(),
                Icons.Default.Cloud,
                Modifier.weight(1f)
            )

            Stat(
                "RAM",
                memoryText(res),
                Icons.Default.Settings,
                Modifier.weight(1f)
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Stat(
                "Storage",
                storageText(res),
                Icons.Default.Settings,
                Modifier.weight(1f)
            )

            Stat(
                "Uptime",
                res?.optString(
                    "uptime",
                    "-"
                ) ?: "-",
                Icons.Default.Home,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun Stat(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Glass
        ),
        shape =
            RoundedCornerShape(20.dp)
    ) {
        Column(
            Modifier.padding(16.dp)
        ) {
            Icon(
                icon,
                null,
                tint = Accent
            )

            Spacer(
                Modifier.height(8.dp)
            )

            Text(
                title,
                fontSize = 12.sp,
                color = TextMuted
            )

            Text(
                value,
                fontSize = 19.sp,
                fontWeight =
                    FontWeight.Black
            )
        }
    }
}

@Composable
fun ResourceCard(
    res: JSONObject?,
    router: JSONObject
) {
    GlassCard {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Surface(
                Modifier.size(46.dp),
                RoundedCornerShape(14.dp),
                Accent.copy(alpha = .14f)
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        null,
                        tint = Accent
                    )
                }
            }

            Spacer(
                Modifier.width(12.dp)
            )

            Column {
                Text(
                    router.optString(
                        "name",
                        "Router"
                    ),
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    router.optString(
                        "host",
                        "-"
                    ),
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        HorizontalDivider()

        Text(
            "RouterOS ${
                res?.optString(
                    "version",
                    "-"
                )
            }",
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text = "${res?.optString(
                "board-name",
                "-"
            )} • ${
                res?.optString(
                    "architecture-name",
                    "-"
                )
            }",
            fontSize = 12.sp,
            color = TextMuted
        )

        Text(
            text = "Uptime: ${
                res?.optString(
                    "uptime",
                    "-"
                )
            }",
            fontSize = 12.sp,
            color = TextMuted
        )
    }
}

@Composable
fun TrafficCard(dash: JSONObject) {
    GlassCard {
        Text(
            "حركة الشبكة",
            fontWeight = FontWeight.Bold
        )

        val rx = dash.optString(
            "rx",
            dash.optString(
                "rx-bytes",
                "-"
            )
        )

        val tx = dash.optString(
            "tx",
            dash.optString(
                "tx-bytes",
                "-"
            )
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Download",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Text(
                    rx,
                    fontSize = 18.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Column(
                horizontalAlignment =
                    Alignment.End
            ) {
                Text(
                    text = "Upload",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Text(
                    tx,
                    fontSize = 18.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}

fun memoryText(
    res: JSONObject?
): String {
    val total =
        res?.optString(
            "total-memory"
        )?.toLongOrNull()

    val free =
        res?.optString(
            "free-memory"
        )?.toLongOrNull()

    return if (
        total != null &&
        free != null &&
        total > 0
    ) {
        "${((total - free) * 100 / total)}%"
    } else {
        "-"
    }
}

fun storageText(
    res: JSONObject?
): String {
    val total =
        res?.optString(
            "total-hdd-space"
        )?.toLongOrNull()

    val free =
        res?.optString(
            "free-hdd-space"
        )?.toLongOrNull()

    return if (
        total != null &&
        free != null &&
        total > 0
    ) {
        "${((total - free) * 100 / total)}%"
    } else {
        "-"
    }
}

@Composable
fun EmptyRouterCard(
    add: () -> Unit
) {
    GlassCard {
        Icon(
            Icons.Default.Cloud,
            null,
            tint = Accent,
            modifier = Modifier.size(42.dp)
        )

        Text(
            "لا يوجد راوتر مرتبط",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "أضف MikroTik ثم اختبر الاتصال لتظهر الإحصائيات.",
            fontSize = 13.sp,
            color = TextMuted
        )

        Button(
            onClick = add,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Add,
                null
            )

            Spacer(
                Modifier.width(6.dp)
            )

            Text(
                "إضافة راوتر MikroTik"
            )
        }
    }
}

@Composable
fun EmptyCard(
    text: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Glass2
        ),
        shape =
            RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            Modifier.padding(18.dp),
            color = TextMuted
        )
    }
}

@Composable
fun ErrorCard(
    text: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor =
                Red.copy(alpha = .12f)
        ),
        shape =
            RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "⚠ $text",
            Modifier.padding(14.dp),
            color = Color(0xFFFDA4AF),
            fontSize = 13.sp
        )
    }
}

@Composable
fun RoutersPage(
    routers: JSONArray,
    selected: JSONObject?,
    add: () -> Unit,
    choose: (JSONObject) -> Unit,
    remove: (Int) -> Unit
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
        contentPadding =
            PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        "إدارة الراوترات",
                        fontSize = 28.sp,
                        fontWeight =
                            FontWeight.Black
                    )

                    Text(
                        "إضافة واختبار وحذف أجهزة MikroTik",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }

                Button(
                    onClick = add
                ) {
                    Icon(
                        Icons.Default.Add,
                        null
                    )
                    Text("إضافة")
                }
            }
        }

        if (routers.length() == 0) {
            item {
                EmptyRouterCard(add)
            }
        }

        items(
            (0 until routers.length())
                .map {
                    routers.getJSONObject(it)
                }
        ) { router ->
            RouterRow(
                router = router,
                selected =
                    selected?.optInt("id") ==
                        router.optInt("id"),
                choose = {
                    choose(router)
                },
                remove = {
                    remove(
                        router.optInt("id")
                    )
                }
            )
        }
    }
}

@Composable
fun RouterRow(
    router: JSONObject,
    selected: Boolean,
    choose: () -> Unit,
    remove: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable {
                choose()
            },
        colors = CardDefaults.cardColors(
            containerColor =
                if (selected)
                    Accent.copy(alpha = .14f)
                else
                    Glass
        ),
        shape =
            RoundedCornerShape(20.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Surface(
                Modifier.size(48.dp),
                RoundedCornerShape(14.dp),
                Blue.copy(alpha = .14f)
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        null,
                        tint = Blue
                    )
                }
            }

            Spacer(
                Modifier.width(12.dp)
            )

            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    router.optString(
                        "name",
                        "Router"
                    ),
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    "${router.optString(
                        "host",
                        "-"
                    )}:${router.optInt(
                        "port",
                        8729
                    )}",
                    12.sp,
                    color = TextMuted
                )
            }

            IconButton(
                onClick = remove
            ) {
                Icon(
                    Icons.Default.Delete,
                    null,
                    tint = Red
                )
            }
        }
    }
}

@Composable
fun UsersPage(
    users: JSONArray,
    selected: JSONObject?,
    error: String?
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(10.dp),
        contentPadding =
            PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                "مستخدمو Hotspot",
                fontSize = 28.sp,
                fontWeight =
                    FontWeight.Black
            )

            Text(
                selected?.optString(
                    "name",
                    "اختر راوتر"
                ) ?: "لا يوجد راوتر محدد",
                13.sp,
                color = TextMuted
            )
        }

        if (error != null) {
            item {
                ErrorCard(error)
            }
        }

        items(
            (0 until users.length())
                .map {
                    users.getJSONObject(it)
                }
        ) { user ->
            UserRow(user)
        }

        if (users.length() == 0) {
            item {
                EmptyCard(
                    "لا توجد بيانات مستخدمين"
                )
            }
        }
    }
}

@Composable
fun UserRow(
    user: JSONObject
) {
    val disabled =
        user.optString("disabled") ==
            "true"

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Glass
        ),
        shape =
            RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Surface(
                Modifier.size(42.dp),
                CircleShape,
                if (disabled)
                    Red.copy(alpha = .12f)
                else
                    Green.copy(alpha = .12f)
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        null,
                        tint =
                            if (disabled)
                                Red
                            else
                                Green
                    )
                }
            }

            Spacer(
                Modifier.width(10.dp)
            )

            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    user.optString(
                        "name",
                        user.optString(
                            "username",
                            "-"
                        )
                    ),
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    user.optString(
                        "profile",
                        "default"
                    ),
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Text(
                if (disabled)
                    "معطل"
                else
                    "فعال",
                fontSize = 12.sp,
                color =
                    if (disabled)
                        Red
                    else
                        Green,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
fun CardsPage(
    selected: JSONObject?,
    api: Api,
    error: String?
) {
    var count by remember {
        mutableStateOf("10")
    }

    var profile by remember {
        mutableStateOf("default")
    }

    var prefix by remember {
        mutableStateOf("MMX")
    }

    var result by remember {
        mutableStateOf<JSONArray?>(null)
    }

    var msg by remember {
        mutableStateOf(error)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "الكروت و QR / Barcode",
            fontSize = 28.sp,
            fontWeight =
                FontWeight.Black
        )

        Text(
            selected?.optString(
                "name",
                "اختر راوتر"
            ) ?: "أضف راوتر أولاً",
            fontSize = 13.sp,
            color = TextMuted
        )

        GlassCard {
            AppField(
                count,
                { count = it },
                "عدد الكروت",
                Icons.Default.Lock
            )

            AppField(
                profile,
                { profile = it },
                "Profile",
                Icons.Default.Settings
            )

            AppField(
                prefix,
                { prefix = it },
                "بادئة الكرت",
                Icons.Default.Settings
            )

            Button(
                onClick = {
                    val id =
                        selected?.optInt("id")
                            ?: return@Button

                    loading = true
                    msg = null

                    scope.launch {
                        try {
                            val raw =
                                api.post(
                                    "/api/routers/$id/cards/generate",
                                    JSONObject()
                                        .put(
                                            "count",
                                            count.toIntOrNull()
                                                ?: 1
                                        )
                                        .put(
                                            "profile",
                                            profile
                                        )
                                        .put(
                                            "prefix",
                                            prefix
                                        )
                                        .toString()
                                )

                            result =
                                JSONObject(raw)
                                    .optJSONArray(
                                        "cards"
                                    )

                            if (result == null) {
                                msg =
                                    "الخادم لم يُرجع قائمة الكروت"
                            }
                        } catch (e: Exception) {
                            msg =
                                e.message
                                    ?: "فشل توليد الكروت"
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled =
                    selected != null &&
                        !loading,
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    if (loading)
                        "جاري التوليد…"
                    else
                        "توليد الكروت"
                )
            }

            OutlinedButton(
                onClick = {
                    val id =
                        selected?.optInt("id")
                            ?: return@OutlinedButton

                    scope.launch {
                        try {
                            api.post(
                                "/api/routers/$id/cards/export",
                                JSONObject()
                                    .put(
                                        "format",
                                        "pdf"
                                    )
                                    .toString()
                            )

                            msg =
                                "تم طلب تجهيز PDF للطباعة"
                        } catch (e: Exception) {
                            msg = e.message
                        }
                    }
                },
                enabled = selected != null,
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    "تجهيز PDF للطباعة"
                )
            }

            OutlinedButton(
                onClick = {
                    val id =
                        selected?.optInt("id")
                            ?: return@OutlinedButton

                    scope.launch {
                        try {
                            api.post(
                                "/api/routers/$id/cards/export",
                                JSONObject()
                                    .put(
                                        "format",
                                        "qr-barcode"
                                    )
                                    .toString()
                            )

                            msg =
                                "تم طلب تجهيز QR + Barcode"
                        } catch (e: Exception) {
                            msg = e.message
                        }
                    }
                },
                enabled = selected != null,
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    "تجهيز QR + Barcode"
                )
            }
        }

        if (msg != null) {
            ErrorCard(msg!!)
        }

        result?.let { cards ->
            Text(
                "تم إنشاء ${cards.length()} كرت",
                fontSize = 18.sp,
                fontWeight =
                    FontWeight.Bold
            )

            for (
                i in 0 until
                    minOf(
                        cards.length(),
                        100
                    )
            ) {
                val card =
                    cards.getJSONObject(i)

                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Glass
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        Text(
                            card.optString(
                                "username",
                                card.optString(
                                    "user",
                                    "-"
                                )
                            )
                        )

                        Text(
                            card.optString(
                                "password",
                                "-"
                            ),
                            color = Accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MorePage(
    api: Api,
    selected: JSONObject?,
    onLogout: () -> Unit
) {
    var page by remember {
        mutableIntStateOf(0)
    }

    if (page == 0) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
            contentPadding =
                PaddingValues(
                    bottom = 30.dp
                )
        ) {
            item {
                Text(
                    "المزيد",
                    fontSize = 28.sp,
                    fontWeight =
                        FontWeight.Black
                )

                Text(
                    text = "كل الأدوات الإدارية في MICRO-MAX",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }

            item {
                MoreButton(
                    "Profiles",
                    "إدارة ملفات Hotspot"
                ) { page = 1 }
            }

            item {
                MoreButton(
                    "المنتجات",
                    "خطط ومنتجات البيع"
                ) { page = 2 }
            }

            item {
                MoreButton(
                    "مركز الدفعات",
                    "المدفوعات والمعاملات"
                ) { page = 3 }
            }

            item {
                MoreButton(
                    "المبيعات",
                    "متابعة المبيعات"
                ) { page = 4 }
            }

            item {
                MoreButton(
                    "المتجر والقوالب",
                    "القوالب والخدمات والتصاميم"
                ) { page = 5 }
            }

            item {
                MoreButton(
                    "التقارير",
                    "تقارير النظام والشبكة"
                ) { page = 6 }
            }

            item {
                MoreButton(
                    "الصلاحيات والأمان",
                    "المستخدمون والأدوار وسجل التدقيق"
                ) { page = 7 }
            }

            item {
                MoreButton(
                    "الإعدادات",
                    "إعدادات التطبيق"
                ) { page = 8 }
            }

            item {
                OutlinedButton(
                    onClick = onLogout,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "تسجيل الخروج"
                    )
                }
            }
        }
    } else {
        SubPage(
            page = page,
            api = api,
            selected = selected,
            back = { page = 0 }
        )
    }
}

@Composable
fun MoreButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors =
            CardDefaults.cardColors(
                containerColor = Glass
            ),
        shape =
            RoundedCornerShape(20.dp)
    ) {
        Row(
            Modifier.padding(17.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Surface(
                Modifier.size(48.dp),
                RoundedCornerShape(15.dp),
                Accent.copy(alpha = .12f)
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        "•",
                        fontSize = 28.sp,
                        fontWeight =
                            FontWeight.Black,
                        color = Accent
                    )
                }
            }

            Spacer(
                Modifier.width(12.dp)
            )

            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    title,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Text(
                "›",
                fontSize = 25.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
fun SubPage(
    page: Int,
    api: Api,
    selected: JSONObject?,
    back: () -> Unit
) {
    var data by remember {
        mutableStateOf<JSONArray?>(null)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var loading by remember {
        mutableStateOf(true)
    }

    val config = when (page) {
        1 -> Triple(
            "Profiles",
            "/api/profiles",
            "إدارة ملفات Hotspot"
        )

        2 -> Triple(
            "المنتجات",
            "/api/products",
            "منتجات وخطط البيع"
        )

        3 -> Triple(
            "مركز الدفعات",
            "/api/payments",
            "المعاملات والمدفوعات"
        )

        4 -> Triple(
            "المبيعات",
            "/api/sales",
            "سجل المبيعات"
        )

        5 -> Triple(
            "المتجر والقوالب",
            "/api/store/templates",
            "القوالب والخدمات"
        )

        6 -> Triple(
            "التقارير",
            "/api/reports",
            "تقارير النظام"
        )

        7 -> Triple(
            "الصلاحيات والأمان",
            "/api/security/audit",
            "سجل التدقيق والأمان"
        )

        else -> Triple(
            "الإعدادات",
            "/api/settings",
            "إعدادات التطبيق"
        )
    }

    LaunchedEffect(
        page,
        selected?.optInt("id")
    ) {
        loading = true
        error = null

        try {
            val path =
                if (
                    selected != null &&
                    page in 1..4
                ) {
                    "${config.second}" +
                        "?routerId=" +
                        selected.optInt("id")
                } else {
                    config.second
                }

            data =
                JSONArray(api.get(path))
        } catch (e: Exception) {
            error =
                e.message
                    ?: "تعذر تحميل البيانات"
        } finally {
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick = back
            ) {
                Text("‹ رجوع")
            }

            Column {
                Text(
                    config.first,
                    fontSize = 26.sp,
                    fontWeight =
                        FontWeight.Black
                )

                Text(
                    text = config.third,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(
            Modifier.height(12.dp)
        )

        if (loading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth()
            )
        }

        if (error != null) {
            ErrorCard(error!!)
        }

        if (
            !loading &&
            data != null
        ) {
            if (data!!.length() == 0) {
                EmptyCard(
                    "لا توجد بيانات حالياً"
                )
            } else {
                LazyColumn(
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp),
                    contentPadding =
                        PaddingValues(
                            bottom = 30.dp
                        )
                ) {
                    items(
                        (0 until data!!.length())
                            .map {
                                data!!.getJSONObject(it)
                            }
                    ) { item ->
                        JsonItemCard(item)
                    }
                }
            }
        }
    }
}

@Composable
fun JsonItemCard(
    item: JSONObject
) {
    Card(
        Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = Glass
            ),
        shape =
            RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(15.dp),
            verticalArrangement =
                Arrangement.spacedBy(5.dp)
        ) {
            val keys = item.keys()
            var shown = 0

            while (
                keys.hasNext() &&
                shown < 8
            ) {
                val key = keys.next()
                val value = item.opt(key)

                Text(
                    "$key: ${
                        value?.toString() ?: "-"
                    }",
                    fontSize =
                        if (shown == 0)
                            15.sp
                        else
                            12.sp,
                    fontWeight =
                        if (shown == 0)
                            FontWeight.Bold
                        else
                            FontWeight.Normal,
                    color =
                        if (shown == 0)
                            TextMain
                        else
                            TextMuted
                )

                shown++
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRouterDialog(
    api: Api,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val scope =
        rememberCoroutineScope()

    var name by remember {
        mutableStateOf("")
    }

    var host by remember {
        mutableStateOf("")
    }

    var port by remember {
        mutableStateOf("8729")
    }

    var username by remember {
        mutableStateOf("admin")
    }

    var password by remember {
        mutableStateOf("")
    }

    var tls by remember {
        mutableStateOf(true)
    }

    var testing by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                "إضافة MikroTik",
                fontWeight =
                    FontWeight.Bold
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(
                    rememberScrollState()
                ),
                verticalArrangement =
                    Arrangement.spacedBy(9.dp)
            ) {
                AppField(
                    name,
                    { name = it },
                    "اسم الراوتر",
                    Icons.Default.Cloud
                )

                AppField(
                    host,
                    { host = it },
                    "IP / Host",
                    Icons.Default.Cloud
                )

                AppField(
                    port,
                    { port = it },
                    "Port",
                    Icons.Default.Settings
                )

                AppField(
                    username,
                    { username = it },
                    "اسم مستخدم MikroTik",
                    Icons.Default.Person
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                    },
                    label = {
                        Text(
                            "كلمة مرور MikroTik"
                        )
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape =
                        RoundedCornerShape(14.dp)
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = tls,
                        onCheckedChange = {
                            tls = it
                        }
                    )

                    Text(
                        "API-SSL (8729)"
                    )
                }

                if (error != null) {
                    Text(
                        error!!,
                        color =
                            Color(0xFFFDA4AF),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        testing = true
                        error = null

                        try {
                            val body =
                                JSONObject()
                                    .put(
                                        "name",
                                        name.trim()
                                    )
                                    .put(
                                        "host",
                                        host.trim()
                                    )
                                    .put(
                                        "port",
                                        port.toIntOrNull()
                                            ?: 8729
                                    )
                                    .put(
                                        "username",
                                        username.trim()
                                    )
                                    .put(
                                        "password",
                                        password
                                    )
                                    .put(
                                        "tls",
                                        tls
                                    )
                                    .put(
                                        "verifyTls",
                                        false
                                    )

                            val created =
                                JSONObject(
                                    api.post(
                                        "/api/routers",
                                        body.toString()
                                    )
                                )

                            val id =
                                created.optLong(
                                    "id",
                                    -1L
                                )

                            if (id <= 0) {
                                throw Exception(
                                    "الخادم لم يُرجع رقم الراوتر"
                                )
                            }

                            api.post(
                                "/api/routers/$id/test",
                                "{}"
                            )

                            onSaved()
                        } catch (e: Exception) {
                            error =
                                e.message
                                    ?: "فشل الحفظ أو الاختبار"
                        } finally {
                            testing = false
                        }
                    }
                },
                enabled =
                    !testing &&
                        name.isNotBlank() &&
                        host.isNotBlank() &&
                        username.isNotBlank() &&
                        password.isNotBlank()
            ) {
                Text(
                    if (testing)
                        "جاري الاختبار…"
                    else
                        "حفظ واختبار"
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text("إلغاء")
            }
        }
    )
}

private suspend fun loadRouter(
    api: Api,
    router: JSONObject,
    setDash: (JSONObject) -> Unit,
    setUsers: (JSONArray) -> Unit
) = withContext(Dispatchers.IO) {
    val id =
        router.getInt("id")

    val dashboard =
        try {
            JSONObject(
                api.get(
                    "/api/routers/$id/dashboard"
                )
            )
        } catch (_: Exception) {
            JSONObject()
        }

    val users =
        try {
            JSONArray(
                api.get(
                    "/api/routers/$id/hotspot/users"
                )
            )
        } catch (_: Exception) {
            JSONArray()
        }

    withContext(Dispatchers.Main) {
        setDash(dashboard)
        setUsers(users)
    }
}
