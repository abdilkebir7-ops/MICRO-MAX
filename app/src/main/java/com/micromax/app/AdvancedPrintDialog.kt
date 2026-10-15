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
    val pw = (paperW.toIntOrNull() ?: 210).coerceAtLeast(20); val ph = (paperH.toIntOrNull() ?: 297).coerceAtLeast(20)
    val cw = (cardW.toIntOrNull() ?: 90).coerceAtLeast(10); val ch = (cardH.toIntOrNull() ?: 55).coerceAtLeast(10)
    val mx = (marginX.toIntOrNull() ?: 10).coerceAtLeast(0); val my = (marginY.toIntOrNull() ?: 10).coerceAtLeast(0)
    val gx = (gapX.toIntOrNull() ?: 4).coerceAtLeast(0); val gy = (gapY.toIntOrNull() ?: 4).coerceAtLeast(0)
    val fitCols = ((pw - mx * 2 + gx) / (cw + gx)).coerceAtLeast(1); val fitRows = ((ph - my * 2 + gy) / (ch + gy)).coerceAtLeast(1)
    val actualCols = if (auto) fitCols else (cols.toIntOrNull() ?: 2).coerceIn(1, 12); val actualRows = if (auto) fitRows else (rows.toIntOrNull() ?: 4).coerceIn(1, 20)
    AlertDialog(onDismissRequest = close, title = { Text("طباعة دفعة احترافية") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("${cards.length()} كرت فعلي • ${actualCols}×${actualRows} = ${actualCols * actualRows} كرت في الصفحة", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
    }, confirmButton = { Button({ printCardSheetAdvanced(context, cards, AdvancedCardPrintSettings(pw, ph, cw, ch, actualCols, actualRows, mx, my, gx, gy, font.toFloatOrNull() ?: 11f, spacing.toFloatOrNull() ?: 4f, marks), "MICROMAX HotSpot Cards"); close() }) { Text("طباعة ${cards.length()} كرت") } }, dismissButton = { TextButton(close) { Text("إلغاء") } })
}
