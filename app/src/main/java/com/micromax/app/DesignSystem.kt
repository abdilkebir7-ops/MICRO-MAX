package com.micromax.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────────────────────── "Panel" design language ─────────────────────────
// A router's front panel: graphite chassis, LED status dots, segmented meters, tabular numerals.
// Frost surfaces stay quiet; the single bold element is the dashboard chassis panel.

val Chassis = Color(0xFF0F1E25)
val ChassisHi = Color(0xFF1A2E37)
val Frost = Color(0xFFEDF1F2)
val Signal = Color(0xFF0A7E8C)
val SignalSoft = Color(0xFF4CD0DF)
val Link = Color(0xFF2FA66A)
val Activity = Color(0xFFE08A00)
val Fault = Color(0xFFD63B4B)
val DataBlue = Color(0xFF4F6BED)
val Violet = Color(0xFF7C5CD6)

// Legacy names kept so older screens inherit the new identity.
val Ink = Chassis
val Paper = Color(0xFFFFFFFF)
val Porcelain = Frost
val Mist = Color(0xFFE4EAEC)
val MutedInk = Color(0xFF5A6D76)
val Royal = Signal
val Teal = Link
val Tangerine = Activity
val Lavender = Violet
val Red = Fault
val Blue = DataBlue
val Green = Link
val Amber = Activity

val BrandFont = FontFamily(
    Font(R.font.ibm_plex_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_arabic_bold, FontWeight.Bold)
)

private fun mmStyle(size: Int, weight: FontWeight, line: Int) =
    TextStyle(fontFamily = BrandFont, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, fontFeatureSettings = "tnum")

fun mmTypography() = Typography(
    displaySmall = mmStyle(34, FontWeight.Bold, 44),
    headlineMedium = mmStyle(26, FontWeight.Bold, 36),
    headlineSmall = mmStyle(22, FontWeight.Bold, 32),
    titleLarge = mmStyle(18, FontWeight.Bold, 28),
    titleMedium = mmStyle(16, FontWeight.SemiBold, 24),
    bodyLarge = mmStyle(15, FontWeight.Normal, 24),
    bodyMedium = mmStyle(14, FontWeight.Normal, 22),
    bodySmall = mmStyle(12, FontWeight.Normal, 18),
    labelLarge = mmStyle(14, FontWeight.SemiBold, 20),
    labelMedium = mmStyle(12, FontWeight.SemiBold, 18),
    labelSmall = mmStyle(11, FontWeight.Medium, 16)
)

fun mmShapes() = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)
)

private val LightScheme = lightColorScheme(
    primary = Signal, onPrimary = Color.White, primaryContainer = Color(0xFFD3EEF1), onPrimaryContainer = Color(0xFF053E45),
    secondary = Activity, onSecondary = Color.White, tertiary = Violet,
    background = Frost, onBackground = Color(0xFF10232B),
    surface = Paper, onSurface = Color(0xFF10232B),
    surfaceVariant = Color(0xFFE4EAEC), onSurfaceVariant = Color(0xFF5A6D76),
    surfaceContainerLowest = Paper, surfaceContainerLow = Color(0xFFF6F8F9), surfaceContainer = Color(0xFFF0F4F5),
    surfaceContainerHigh = Color(0xFFE9EEF0), surfaceContainerHighest = Color(0xFFE4EAEC),
    outline = Color(0xFF8A9BA3), outlineVariant = Color(0xFFD9E1E4),
    error = Fault, onError = Color.White, errorContainer = Color(0xFFFBE3E6), onErrorContainer = Color(0xFF6E1520)
)

private val DarkScheme = darkColorScheme(
    primary = SignalSoft, onPrimary = Color(0xFF00363C), primaryContainer = Color(0xFF0F4F58), onPrimaryContainer = Color(0xFFBFF1F7),
    secondary = Color(0xFFFFB04A), onSecondary = Color(0xFF3F2500), tertiary = Color(0xFFB9A4FF),
    background = Color(0xFF0B151A), onBackground = Color(0xFFE6EEF0),
    surface = Color(0xFF131F26), onSurface = Color(0xFFE6EEF0),
    surfaceVariant = Color(0xFF1B2A33), onSurfaceVariant = Color(0xFF9FB2BA),
    surfaceContainerLowest = Color(0xFF0B151A), surfaceContainerLow = Color(0xFF101C22), surfaceContainer = Color(0xFF16242B),
    surfaceContainerHigh = Color(0xFF1B2A33), surfaceContainerHighest = Color(0xFF22333D),
    outline = Color(0xFF6C8089), outlineVariant = Color(0xFF263640),
    error = Color(0xFFFF8793), onError = Color(0xFF4A0A14), errorContainer = Color(0xFF5A1822), onErrorContainer = Color(0xFFFFD9DD)
)

@Composable
fun MicroMaxTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = mmTypography(), shapes = mmShapes(), content = content)
}

// ───────────────────────── Motion & haptics ─────────────────────────

/** Subtle press-down scale used on every tappable surface. */
fun Modifier.pressScale(onClick: (() -> Unit)?, enabled: Boolean = true, haptic: Boolean = true): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) .97f else 1f, spring(dampingRatio = .6f, stiffness = 800f), label = "press")
    val feedback = LocalHapticFeedback.current
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .then(
            if (onClick != null && enabled) Modifier.androidxClickable(source) {
                if (haptic) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            } else Modifier
        )
}

private fun Modifier.androidxClickable(source: MutableInteractionSource, onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(interactionSource = source, indication = null, onClick = onClick))

/** Shimmer placeholder used while router data loads. */
@Composable
fun ShimmerBlock(modifier: Modifier = Modifier, radius: Dp = 14.dp) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceVariant
    val hi = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .35f)
    Box(modifier.clip(RoundedCornerShape(radius)).drawBehind {
        drawRect(base)
        val w = size.width
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, hi, Color.Transparent), startX = w * x, endX = w * (x + .6f)))
    })
}

// ───────────────────────── Panel parts ─────────────────────────

/** Status LED. Pulses softly when [live]. */
@Composable
fun LedDot(color: Color, modifier: Modifier = Modifier, size: Dp = 9.dp, live: Boolean = false) {
    val t = rememberInfiniteTransition(label = "led")
    val a by t.animateFloat(1f, .45f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    Box(modifier.size(size + 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size + 6.dp).background(color.copy(alpha = .16f * (if (live) a else 1f)), CircleShape))
        Box(Modifier.size(size).background(color.copy(alpha = if (live) a else 1f), CircleShape))
    }
}

/** Graphite chassis surface with a faint vent-dot texture: the dashboard hero and auth header. */
@Composable
fun ChassisPanel(modifier: Modifier = Modifier, radius: Dp = 28.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(radius))
            .background(Brush.verticalGradient(listOf(ChassisHi, Chassis)))
            .drawBehind {
                val step = 14.dp.toPx()
                val r = 1.1.dp.toPx()
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < size.width) { drawCircle(Color.White.copy(alpha = .045f), r, Offset(x, y)); x += step }
                    y += step
                }
            }
    ) { Column(Modifier.fillMaxWidth(), content = content) }
}

/** Segmented meter, like a router signal bar. Value is 0..100. */
@Composable
fun SegmentMeter(value: Float, modifier: Modifier = Modifier, tint: Color = Signal, track: Color = Color.Unspecified, segments: Int = 20, height: Dp = 10.dp) {
    val animated by animateFloatAsState(value.coerceIn(0f, 100f), tween(700, easing = FastOutSlowInEasing), label = "meter")
    val trackColor = if (track == Color.Unspecified) MaterialTheme.colorScheme.surfaceVariant else track
    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val lit = (animated / 100f * segments)
        for (i in 0 until segments) {
            val on = i + 1 <= lit + .0001f || (i < lit)
            // RTL aware: segments fill from the layout start
            val left = i * (w + gap)
            drawRoundRect(if (on) tint else trackColor, Offset(left, 0f), Size(w, size.height), CornerRadius(w / 2.2f, w / 2.2f))
        }
    }
}

/** Compact stat for the chassis panel: label over tabular value. */
@Composable
fun PanelStat(label: String, value: String, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .6f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = tint, maxLines = 1)
    }
}

// ───────────────────────── Brand ─────────────────────────

/** M monogram with a status LED. */
@Composable
fun NetworkMark(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val stroke = w * .105f
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * .22f, h * .74f); lineTo(w * .22f, h * .30f); lineTo(w * .5f, h * .58f); lineTo(w * .78f, h * .30f); lineTo(w * .78f, h * .74f)
        }
        drawPath(p, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(Link, w * .075f, Offset(w * .82f, h * .2f))
    }
}

@Composable
fun NetworkBadge(modifier: Modifier = Modifier, size: Dp = 46.dp) {
    Box(
        modifier.size(size).background(Brush.verticalGradient(listOf(ChassisHi, Chassis)), RoundedCornerShape(size * .3f)),
        contentAlignment = Alignment.Center
    ) { NetworkMark(Modifier.size(size * .62f)) }
}

// ───────────────────────── Components (legacy names preserved) ─────────────────────────

@Composable
fun PageHeading(kicker: String, title: String, detail: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LedDot(MaterialTheme.colorScheme.primary, size = 6.dp)
            Text(kicker, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeading(title: String, detail: String? = null, onMore: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (onMore != null) TextButton(onClick = onMore) {
            Text("عرض الكل", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Icon(MmIcons.ChevronL, null, Modifier.size(16.dp))
        }
    }
}

@Composable
fun SymbolTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier.size(size).background(tint.copy(alpha = .12f), RoundedCornerShape(size * .32f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * .54f))
    }
}

@Composable
fun StatusPill(text: String, good: Boolean = true) {
    val tint = if (good) Link else Activity
    Row(Modifier.background(tint.copy(alpha = .12f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        LedDot(tint, size = 5.dp, live = good)
        Spacer(Modifier.width(3.dp))
        Text(text, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.height(92.dp).pressScale(onClick), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            SymbolTile(icon, tint, size = 36.dp)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun NumberTile(value: String, label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SymbolTile(icon, tint, size = 36.dp)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ItemSurface(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth().pressScale(onClick, haptic = false), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}
