package com.micromax.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The new identity deliberately uses quiet porcelain surfaces, cobalt navigation,
// charcoal operational data and a single warm signal accent. No glassmorphism.
val Ink = Color(0xFF172643)
val Royal = Color(0xFF2458E9)
val Porcelain = Color(0xFFF3F5F9)
val Paper = Color(0xFFFFFFFF)
val Mist = Color(0xFFE9EDF5)
val MutedInk = Color(0xFF65738B)
val Tangerine = Color(0xFFF48952)
val Teal = Color(0xFF0E927B)
val Lavender = Color(0xFF7464C7)

val BrandFont = FontFamily(
    Font(R.font.ibm_plex_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_arabic_bold, FontWeight.Bold)
)

/** A node-link monogram that stays recognisable at toolbar, login and launcher sizes. */
@Composable
fun NetworkMark(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val stroke = w * .075f
        drawLine(color, Offset(w * .22f, h * .68f), Offset(w * .47f, h * .28f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * .47f, h * .28f), Offset(w * .78f, h * .66f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * .22f, h * .68f), Offset(w * .78f, h * .66f), stroke, cap = StrokeCap.Round)
        val r = w * .12f
        drawCircle(color, r, Offset(w * .22f, h * .68f))
        drawCircle(color, r, Offset(w * .47f, h * .28f))
        drawCircle(color, r, Offset(w * .78f, h * .66f))
    }
}

@Composable
fun NetworkBadge(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 46.dp) {
    Box(
        modifier.size(size).background(Brush.linearGradient(listOf(Royal, Color(0xFF5D86FF))), RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center
    ) { NetworkMark(Modifier.size(size * .58f)) }
}

@Composable
fun PageHeading(kicker: String, title: String, detail: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(kicker, style = MaterialTheme.typography.labelMedium, color = Royal, fontWeight = FontWeight.Bold)
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
            Text("عرض الكل", fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ArrowBackIosNew, null, Modifier.size(12.dp))
        }
    }
}

@Composable
fun SymbolTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(modifier.size(size).background(tint.copy(alpha = .11f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * .52f))
    }
}

@Composable
fun StatusPill(text: String, good: Boolean = true) {
    val tint = if (good) Teal else Tangerine
    Row(Modifier.background(tint.copy(alpha = .10f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(tint, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(text, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(95.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            SymbolTile(icon, tint, size = 37.dp)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun NumberTile(value: String, label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SymbolTile(icon, tint, size = 37.dp)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ItemSurface(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), onClick = onClick ?: {}, enabled = onClick != null,
        shape = RoundedCornerShape(19.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}
