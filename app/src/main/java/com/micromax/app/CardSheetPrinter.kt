package com.micromax.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import com.google.zxing.BarcodeFormat
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileOutputStream
import java.io.IOException

/** How many cards fit on one A4 page. Cards are drawn in a fixed 185x100 box and scaled to the cell. */
enum class SheetLayout(val cols: Int, val rows: Int, val label: String) {
    BIG(2, 5, "10 كروت (كبير)"),
    NORMAL(3, 8, "24 كرت"),
    SMALL(4, 10, "40 كرت (صغير)");
    val perPage get() = cols * rows
}

/** Opens Android's print dialog (printer or "Save as PDF") with a cut-ready sheet of cards. Works offline. */
fun printCardSheet(context: Context, cards: JSONArray, layout: SheetLayout, title: String = "MICROMAX Cards") {
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val attrs = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
    pm.print(title, CardSheetAdapter(context, cards, layout), attrs)
}

private class CardSheetAdapter(val ctx: Context, val cards: JSONArray, val layout: SheetLayout) : PrintDocumentAdapter() {
    private var pdf: PrintedPdfDocument? = null
    private val pages get() = maxOf(1, (cards.length() + layout.perPage - 1) / layout.perPage)

    override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
        pdf?.close(); pdf = PrintedPdfDocument(ctx, new)
        if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
        cb.onLayoutFinished(PrintDocumentInfo.Builder("cards.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build(), true)
    }

    override fun onWrite(ranges: Array<out PageRange>, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
        val doc = pdf ?: run { cb.onWriteFailed("no document"); return }
        // Rendering hundreds of QR codes must not block the UI thread.
        Thread {
            try {
                for (i in 0 until pages) {
                    if (cancel?.isCanceled == true) { cb.onWriteCancelled(); return@Thread }
                    val page = doc.startPage(i)
                    drawPage(page.canvas, i, doc.pageWidth.toFloat(), doc.pageHeight.toFloat())
                    doc.finishPage(page)
                }
                FileOutputStream(dest.fileDescriptor).use { doc.writeTo(it) }
                cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: IOException) { cb.onWriteFailed(e.message) } catch (e: Exception) { cb.onWriteFailed(e.message) }
        }.start()
    }

    override fun onFinish() { pdf?.close(); pdf = null }

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val bold = Paint(ink).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 11f }
    private val mono = Paint(ink).apply { typeface = Typeface.MONOSPACE; textSize = 11f; isFakeBoldText = true }
    private val small = Paint(ink).apply { textSize = 7.5f; color = Color.DKGRAY }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = Color.LTGRAY; pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f) }
    private val crisp = Paint().apply { isFilterBitmap = false }

    private fun drawPage(c: Canvas, index: Int, w: Float, h: Float) {
        val margin = 18f
        val cellW = (w - 2 * margin) / layout.cols
        val cellH = (h - 2 * margin) / layout.rows
        val s = minOf(cellW / 185f, cellH / 100f)
        val first = index * layout.perPage
        for (k in 0 until layout.perPage) {
            val n = first + k
            if (n >= cards.length()) break
            val x = margin + (k % layout.cols) * cellW
            val y = margin + (k / layout.cols) * cellH
            c.save()
            c.translate(x + (cellW - 185f * s) / 2f, y + (cellH - 100f * s) / 2f)
            c.scale(s, s)
            drawCard(c, cards.getJSONObject(n))
            c.restore()
            c.drawRect(x, y, x + cellW, y + cellH, frame)
        }
    }

    private fun qr(value: String, px: Int): Bitmap? = if (value.isBlank()) null else generateCode(value, BarcodeFormat.QR_CODE, px, px)

    /** Card box is 185x100 units. Right: login QR (scan = automatic login). Bottom-left: optional Wi-Fi QR. */
    private fun drawCard(c: Canvas, card: org.json.JSONObject) {
        val pin = card.optString("passwordMode") == "pin" || card.optString("password").isBlank() || card.optString("password") == card.optString("username")
        c.drawText(card.optString("planName", card.optString("profile")).take(20), 8f, 16f, bold)
        c.drawText(if (pin) "الكود: " else "User: ", 8f, 33f, small)
        c.drawText(card.optString("username"), 40f, 33f, mono)
        if (!pin) { c.drawText("Pass: ", 8f, 48f, small); c.drawText(card.optString("password"), 40f, 48f, mono) }
        val price = card.optDouble("price", 0.0)
        val info = listOfNotNull(
            if (price > 0) "${price.toLong()} ${card.optString("currency", "XOF")}" else null,
            card.optInt("durationMinutes", 0).takeIf { it > 0 }?.let { m -> if (m % 1440 == 0) "${m / 1440}d" else if (m % 60 == 0) "${m / 60}h" else "${m}m" }
        ).joinToString(" • ")
        val wifi = qr(card.optString("wifiQr"), 220)
        val infoX = if (wifi != null) 46f else 8f
        if (info.isNotEmpty()) c.drawText(info, infoX, 80f, small)
        wifi?.let { c.drawBitmap(it, null, RectF(8f, 58f, 40f, 90f), crisp); c.drawText("1) Wi-Fi", 8f, 97f, small) }
        qr(card.optString("qrContent"), 420)?.let {
            c.drawBitmap(it, null, RectF(108f, 8f, 180f, 80f), crisp)
            c.drawText(if (wifi != null) "2) امسح للدخول" else "امسح للدخول", 112f, 92f, small)
        }
    }
}

/** إعدادات طباعة تجارية للدفعة الفعلية. جميع المقاسات بالملليمتر. */
data class AdvancedCardPrintSettings(
    val paperWidthMm: Int = 210,
    val paperHeightMm: Int = 297,
    val cardWidthMm: Int = 90,
    val cardHeightMm: Int = 55,
    val columns: Int = 2,
    val rows: Int = 4,
    val marginXmm: Int = 10,
    val marginYmm: Int = 10,
    val gapXmm: Int = 4,
    val gapYmm: Int = 4,
    val fontSize: Float = 11f,
    val lineSpacing: Float = 4f,
    val cutMarks: Boolean = true,
    val fontStyle: String = "عريض",
    val borderStyle: String = "متصل",
    val cornerRadiusMm: Float = 3f,
    val borderWidthMm: Float = .5f
)

/** طباعة الدفعة الفعلية بالحجم التجاري، مع QR وBarcode الحقيقيين لكل كرت. */
fun printCardSheetAdvanced(context: Context, cards: JSONArray, settings: AdvancedCardPrintSettings, title: String = "MICROMAX Cards") {
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val attrs = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
    pm.print(title, AdvancedCardSheetAdapter(context, cards, settings), attrs)
}

private class AdvancedCardSheetAdapter(private val ctx: Context, private val cards: JSONArray, private val settings: AdvancedCardPrintSettings) : PrintDocumentAdapter() {
    private var pdf: PrintedPdfDocument? = null
    private val cols get() = settings.columns.coerceIn(1, 12)
    private val rows get() = settings.rows.coerceIn(1, 20)
    private val perPage get() = cols * rows
    private val pages get() = maxOf(1, (cards.length() + perPage - 1) / perPage)
    private val crisp = Paint().apply { isFilterBitmap = false }

    override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
        pdf?.close(); pdf = PrintedPdfDocument(ctx, new)
        if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
        cb.onLayoutFinished(PrintDocumentInfo.Builder("micromax-cards-advanced.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build(), true)
    }

    override fun onWrite(ranges: Array<out PageRange>, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
        val doc = pdf ?: run { cb.onWriteFailed("no document"); return }
        Thread {
            try {
                for (i in 0 until pages) {
                    if (cancel?.isCanceled == true) { cb.onWriteCancelled(); return@Thread }
                    val page = doc.startPage(i)
                    drawPage(page.canvas, i, doc.pageWidth.toFloat(), doc.pageHeight.toFloat())
                    doc.finishPage(page)
                }
                FileOutputStream(dest.fileDescriptor).use { doc.writeTo(it) }
                cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) { cb.onWriteFailed(e.message) }
        }.start()
    }

    override fun onFinish() { pdf?.close(); pdf = null }

    private fun drawPage(canvas: Canvas, pageIndex: Int, pageW: Float, pageH: Float) {
        canvas.drawColor(Color.WHITE)
        val mm = minOf(pageW / settings.paperWidthMm.coerceAtLeast(1), pageH / settings.paperHeightMm.coerceAtLeast(1))
        val cardW = settings.cardWidthMm * mm
        val cardH = settings.cardHeightMm * mm
        val mx = settings.marginXmm * mm
        val my = settings.marginYmm * mm
        val gx = settings.gapXmm * mm
        val gy = settings.gapYmm * mm
        val first = pageIndex * perPage
        for (slot in 0 until perPage) {
            val index = first + slot
            if (index >= cards.length()) break
            val x = mx + (slot % cols) * (cardW + gx)
            val y = my + (slot / cols) * (cardH + gy)
            drawAdvancedCard(canvas, cards.getJSONObject(index), x, y, cardW, cardH, mm)
        }
    }

    private fun drawAdvancedCard(canvas: Canvas, card: JSONObject, x: Float, y: Float, w: Float, h: Float, mm: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        p.style = Paint.Style.FILL; canvas.drawRoundRect(x, y, x + w, y + h, 3f * mm, 3f * mm, p.apply { color = Color.WHITE })
        p.style = Paint.Style.STROKE; p.strokeWidth = settings.borderWidthMm.coerceIn(.1f, 3f) * mm; p.color = Color.BLACK
        if (settings.borderStyle == "متقطع") p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f * mm, 2f * mm), 0f)
        val radius = settings.cornerRadiusMm.coerceIn(0f, 15f) * mm
        if (settings.borderStyle != "بدون") canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, p)
        p.pathEffect = null
        if (settings.cutMarks) { p.strokeWidth = .25f * mm; val mark = 3f * mm; canvas.drawLine(x - mark, y, x + 1.2f * mm, y, p); canvas.drawLine(x, y - mark, x, y + 1.2f * mm, p); canvas.drawLine(x + w - 1.2f * mm, y + h, x + w + mark, y + h, p); canvas.drawLine(x + w, y + h - 1.2f * mm, x + w, y + h + mark, p) }
        val pad = 4f * mm
        p.style = Paint.Style.FILL
        p.typeface = when (settings.fontStyle) {
            "رقيق" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            "عادي" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "مزخرف" -> Typeface.create("cursive", Typeface.NORMAL)
            "ثابت" -> Typeface.MONOSPACE
            else -> Typeface.create("sans-serif", Typeface.BOLD)
        }
        p.textSize = settings.fontSize * mm; p.color = Color.BLACK
        canvas.drawText(card.optString("planName", card.optString("profile", "WiFi Access")).take(22), x + pad, y + 7f * mm, p)
        p.strokeWidth = .35f * mm; canvas.drawRect(x + pad, y + 9f * mm, x + w - pad, y + 9.4f * mm, p)
        val pin = card.optString("passwordMode") == "pin" || card.optString("password").isBlank() || card.optString("password") == card.optString("username")
        p.typeface = when (settings.fontStyle) { "رقيق" -> Typeface.create("sans-serif-light", Typeface.NORMAL); "مزخرف" -> Typeface.create("cursive", Typeface.NORMAL); "عريض" -> Typeface.create("monospace", Typeface.BOLD); "ثابت" -> Typeface.MONOSPACE; else -> Typeface.create("sans-serif", Typeface.NORMAL) }; p.textSize = (settings.fontSize - settings.lineSpacing / 2f).coerceAtLeast(6f) * mm
        canvas.drawText((if (pin) "PIN: " else "USER: ") + card.optString("username"), x + pad, y + h - 12f * mm, p)
        if (!pin) canvas.drawText("PASS: " + card.optString("password"), x + pad, y + h - 6f * mm, p)
        val qr = generateCode(card.optString("qrContent"), BarcodeFormat.QR_CODE, 360, 360)
        qr?.let { val size = minOf(24f * mm, h - 18f * mm); canvas.drawBitmap(it, null, android.graphics.RectF(x + w - size - pad, y + h - size - pad, x + w - pad, y + h - pad), crisp) }
        val bar = generateCode(card.optString("username"), BarcodeFormat.CODE_128, 600, 120)
        bar?.let { canvas.drawBitmap(it, null, android.graphics.RectF(x + pad, y + 11f * mm, x + w * .52f, y + 18f * mm), crisp) }
    }
}
