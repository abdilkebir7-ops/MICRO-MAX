package com.micromax.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray

@Composable
fun HotspotAdvancedPrintDialog(cards: JSONArray, close: () -> Unit) {
    val context = LocalContext.current
    var paperW by remember { mutableStateOf("210") }; var paperH by remember { mutableStateOf("297") }
    var cardW by remember { mutableStateOf("90") }; var cardH by remember { mutableStateOf("55") }
    var cols by remember { mutableStateOf("2") }; var rows by remember { mutableStateOf("4") }
    var marginX by remember { mutableStateOf("10") }; var marginY by remember { mutableStateOf("10") }
    var gapX by remember { mutableStateOf("4") }; var gapY by remember { mutableStateOf("4") }
    var font by remember { mutableStateOf("11") }; var spacing by remember { mutableStateOf("4") }
    var auto by remember { mutableStateOf(true) }; var marks by remember { mutableStateOf(true) }
    var fontStyle by remember { mutableStateOf("عريض") }; var borderStyle by remember { mutableStateOf("متصل") }; var radius by remember { mutableStateOf("3") }; var borderWidth by remember { mutableStateOf("0.5") }
    val pw = (paperW.toIntOrNull() ?: 210).coerceAtLeast(20); val ph = (paperH.toIntOrNull() ?: 297).coerceAtLeast(20)
    val cw = (cardW.toIntOrNull() ?: 90).coerceAtLeast(10); val ch = (cardH.toIntOrNull() ?: 55).coerceAtLeast(10)
    val mx = (marginX.toIntOrNull() ?: 10).coerceAtLeast(0); val my = (marginY.toIntOrNull() ?: 10).coerceAtLeast(0)
    val gx = (gapX.toIntOrNull() ?: 4).coerceAtLeast(0); val gy = (gapY.toIntOrNull() ?: 4).coerceAtLeast(0)
    val fitCols = ((pw - mx * 2 + gx) / (cw + gx)).coerceAtLeast(1); val fitRows = ((ph - my * 2 + gy) / (ch + gy)).coerceAtLeast(1)
    val actualCols = if (auto) fitCols else (cols.toIntOrNull() ?: 2).coerceIn(1, 12); val actualRows = if (auto) fitRows else (rows.toIntOrNull() ?: 4).coerceIn(1, 20)
    AlertDialog(onDismissRequest = close, title = { Text("طباعة دفعة احترافية") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("${cards.length()} كرت فعلي • ${actualCols}×${actualRows} = ${actualCols * actualRows} كرت في الصفحة", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            // معاينة مباشرة مبسطة تتغير مع خيارات التصميم
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("معاينة الكرت قبل الطباعة", style = MaterialTheme.typography.labelMedium, color = Accent)
                    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                        val sw = size.width; val sh = size.height
                        val stroke = (borderWidth.toFloatOrNull() ?: .5f).coerceIn(.2f, 3f) * density
                        val r = (radius.toFloatOrNull() ?: 3f).coerceIn(0f, 15f) * density
                        drawRoundRect(color = androidx.compose.ui.graphics.Color.White, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), size = size)
                        drawRoundRect(color = androidx.compose.ui.graphics.Color.DarkGray, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
                        drawLine(androidx.compose.ui.graphics.Color.LightGray, androidx.compose.ui.geometry.Offset(10f, 37f), androidx.compose.ui.geometry.Offset(sw - 10f, 37f), strokeWidth = stroke)
                        drawRect(color = androidx.compose.ui.graphics.Color(0xFFE5E7EB), topLeft = androidx.compose.ui.geometry.Offset(sw - 58f, 45f), size = androidx.compose.ui.geometry.Size(48f, 48f))
                        drawLine(androidx.compose.ui.graphics.Color.Gray, androidx.compose.ui.geometry.Offset(sw - 53f, 50f), androidx.compose.ui.geometry.Offset(sw - 15f, 88f), strokeWidth = 2f)
                        drawLine(androidx.compose.ui.graphics.Color.Gray, androidx.compose.ui.geometry.Offset(sw - 15f, 50f), androidx.compose.ui.geometry.Offset(sw - 53f, 88f), strokeWidth = 2f)
                    }
                    Text("MICRO-MAX • باقة الإنترنت", fontWeight = if (fontStyle == "عريض") FontWeight.ExtraBold else if (fontStyle == "رقيق") FontWeight.Light else FontWeight.Normal, fontSize = 14.sp, fontFamily = when(fontStyle) { "مزخرف" -> androidx.compose.ui.text.font.FontFamily.Cursive; "ثابت" -> androidx.compose.ui.text.font.FontFamily.Monospace; else -> androidx.compose.ui.text.font.FontFamily.SansSerif })
                    Text("USER: MAX-1024     PASS: ••••••     QR", fontSize = 11.sp, fontFamily = when(fontStyle) { "مزخرف" -> androidx.compose.ui.text.font.FontFamily.Cursive; "ثابت" -> androidx.compose.ui.text.font.FontFamily.Monospace; else -> androidx.compose.ui.text.font.FontFamily.SansSerif })
                }
            }
            Text("نوع الخط", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("رقيق", "عادي", "عريض", "مزخرف", "ثابت").forEach { v -> FilterChip(fontStyle == v, { fontStyle = v }, label = { Text(v) }) } }
            Text("إطار الكرت والتحكم في المربعات", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("متصل", "متقطع", "بدون").forEach { v -> FilterChip(borderStyle == v, { borderStyle = v }, label = { Text(v) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.weight(1f)) { AppField(radius, { radius = it.filter { c -> c.isDigit() || c == '.' } }, "استدارة الزوايا mm", Icons.Outlined.CropSquare) }; Box(Modifier.weight(1f)) { AppField(borderWidth, { borderWidth = it.filter { c -> c.isDigit() || c == '.' } }, "سماكة الإطار mm", Icons.Outlined.BorderStyle) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(pw == 210 && ph == 297, { paperW = "210"; paperH = "297" }, label = { Text("A4") }); FilterChip(cw == 85 && ch == 54, { cardW = "85"; cardH = "54" }, label = { Text("85×54") }); FilterChip(cw == 90 && ch == 55, { cardW = "90"; cardH = "55" }, label = { Text("90×55") }) }
            Text("مقاسات الورق والكرت بالملليمتر", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.weight(1f)) { AppField(paperW, { paperW = it.filter(Char::isDigit) }, "عرض الورقة", Icons.Outlined.Straighten) }; Box(Modifier.weight(1f)) { AppField(paperH, { paperH = it.filter(Char::isDigit) }, "ارتفاع الورقة", Icons.Outlined.Straighten) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.weight(1f)) { AppField(cardW, { cardW = it.filter(Char::isDigit) }, "عرض الكرت", Icons.Outlined.CreditCard) }; Box(Modifier.weight(1f)) { AppField(cardH, { cardH = it.filter(Char::isDigit) }, "ارتفاع الكرت", Icons.Outlined.CreditCard) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(auto, { auto = it }); Text("حساب الصفوف والأعمدة تلقائيًا", Modifier.weight(1f), fontSize = 12.sp) }
            if (!auto) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.weight(1f)) { AppField(cols, { cols = it.filter(Char::isDigit) }, "الأعمدة", Icons.Outlined.ViewColumn) }; Box(Modifier.weight(1f)) { AppField(rows, { rows = it.filter(Char::isDigit) }, "الصفوف", Icons.Outlined.GridView) } }
            Text("الهوامش والفواصل", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.weight(1f)) { AppField(marginX, { marginX = it.filter(Char::isDigit) }, "هامش أفقي", Icons.Outlined.Margin) }; Box(Modifier.weight(1f)) { AppField(marginY, { marginY = it.filter(Char::isDigit) }, "هامش رأسي", Icons.Outlined.Margin) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.weight(1f)) { AppField(gapX, { gapX = it.filter(Char::isDigit) }, "فاصل أفقي", Icons.Outlined.SpaceBar) }; Box(Modifier.weight(1f)) { AppField(gapY, { gapY = it.filter(Char::isDigit) }, "فاصل رأسي", Icons.Outlined.SpaceBar) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.weight(1f)) { AppField(font, { font = it.filter { c -> c.isDigit() || c == '.' } }, "حجم الخط", Icons.Outlined.FormatSize) }; Box(Modifier.weight(1f)) { AppField(spacing, { spacing = it.filter { c -> c.isDigit() || c == '.' } }, "تباعد السطور", Icons.Outlined.FormatLineSpacing) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(marks, { marks = it }); Text("إضافة علامات قص تجارية", fontSize = 12.sp) }
        }
    }, confirmButton = { Button({ printCardSheetAdvanced(context, cards, AdvancedCardPrintSettings(pw, ph, cw, ch, actualCols, actualRows, mx, my, gx, gy, font.toFloatOrNull() ?: 11f, spacing.toFloatOrNull() ?: 4f, marks, fontStyle, borderStyle, radius.toFloatOrNull() ?: 3f, borderWidth.toFloatOrNull() ?: .5f), "MICROMAX HotSpot Cards"); close() }) { Text("طباعة ${cards.length()} كرت") } }, dismissButton = { TextButton(close) { Text("إلغاء") } })
}
