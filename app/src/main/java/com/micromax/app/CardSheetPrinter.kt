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
fun printCardSheet(context: Context, cards: JSONArray, layout: SheetLayout, title: String = "MICRO-MAX Cards") {
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
