package com.blushdesk.app.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.OperatorProfile
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.domain.BrandPalette
import com.blushdesk.app.domain.Formats
import com.blushdesk.app.domain.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId

/**
 * Draws a one-page A4 receipt with Android's native [PdfDocument] (vector text and shapes, so it
 * stays sharp when printed) and writes it to [generate]'s destination file.
 *
 * Layout, top to bottom: showroom header band, "billed to" card with the purchase facts, the
 * itemized table, the total, a thank-you footer and a large semi-transparent PAID stamp across the
 * page. Units are PDF points (1/72 in); A4 is 595 x 842.
 */
class PdfReceiptGenerator {

    /**
     * Only a paid order may be receipted, so the rule lives here as well as in the UI: a caller
     * that forgets to check cannot produce a "PAID" receipt for an unpaid order.
     */
    suspend fun generate(
        destination: File,
        operator: OperatorProfile,
        buyer: Buyer,
        order: Order,
        zone: ZoneId = ZoneId.systemDefault(),
        issuedAt: Instant = Instant.now(),
    ): File = withContext(Dispatchers.IO) {
        require(order.paymentStatus.isPaid) { "A receipt can only be issued for a paid order" }
        require(order.buyerId == buyer.id) { "Order ${order.id} does not belong to buyer ${buyer.id}" }

        destination.absoluteFile.parentFile?.mkdirs()
        val partial = File(destination.absoluteFile.parentFile, destination.name + ".part")
        val avatar = operator.photoPath?.let { loadSampled(it, AVATAR_DECODE_PX) }
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
            ReceiptPage(page.canvas, operator, buyer, order, zone, issuedAt, avatar).draw()
            document.finishPage(page)
            FileOutputStream(partial).use { document.writeTo(it) }
            if (destination.exists()) destination.delete()
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
        } catch (e: Throwable) {
            partial.delete()
            throw e
        } finally {
            document.close()
            avatar?.recycle()
        }
        destination
    }

    private fun loadSampled(path: String, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private class ReceiptPage(
        private val canvas: Canvas,
        private val operator: OperatorProfile,
        private val buyer: Buyer,
        private val order: Order,
        private val zone: ZoneId,
        private val issuedAt: Instant,
        private val avatar: Bitmap?,
    ) {
        private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private val shape = Paint(Paint.ANTI_ALIAS_FLAG)

        private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
        private val bold = Typeface.create("sans-serif", Typeface.BOLD)

        private val storeName = operator.storeName.ifBlank { "Showroom" }

        fun draw() {
            header()
            billedTo()
            val tableBottom = table()
            total(tableBottom)
            footer()
            paidStamp()
        }

        // ---- Header band --------------------------------------------------------------------

        private fun header() {
            rect(0f, 0f, W, HEADER_H, BrandPalette.DEEP_MAGENTA)
            rect(0f, HEADER_H, W, HEADER_H + 6f, BrandPalette.VIBRANT_ROSE)

            val cx = MARGIN + 34f
            val cy = 68f
            avatar(cx, cy, 34f)

            val textX = MARGIN + 86f
            val rightEdge = W - MARGIN
            val titleWidth = 150f
            line(storeName, textX, 56f, 22f, BrandPalette.WHITE, bold = true, maxWidth = rightEdge - titleWidth - 14f - textX)

            var y = 78f
            listOf(operator.fullName, operator.email, operator.phone).filter { it.isNotBlank() }.forEach {
                line(it, textX, y, 10.5f, BrandPalette.SOFT_PINK, maxWidth = rightEdge - titleWidth - 14f - textX)
                y += 15f
            }

            line("RECEIPT", rightEdge, 56f, 30f, BrandPalette.WHITE, bold = true, align = Paint.Align.RIGHT, spacing = 0.08f)
            line("No. ${Formats.orderNumber(order.id)}", rightEdge, 78f, 11.5f, BrandPalette.SOFT_PINK, bold = true, align = Paint.Align.RIGHT)
            line("Issued ${Formats.date(issuedAt, zone)}", rightEdge, 94f, 10.5f, BrandPalette.SOFT_PINK, align = Paint.Align.RIGHT)
        }

        /** The operator's photo in a circle, or their initial on a soft-pink disc. */
        private fun avatar(cx: Float, cy: Float, r: Float) {
            if (avatar != null) {
                val path = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
                canvas.withClip(path) {
                    // Center-crop: scale to cover the circle, then center.
                    val scale = (2 * r) / minOf(avatar.width, avatar.height)
                    val w = avatar.width * scale
                    val h = avatar.height * scale
                    drawBitmap(avatar, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), shape)
                }
            } else {
                shape.style = Paint.Style.FILL
                shape.color = argb(BrandPalette.SOFT_PINK)
                canvas.drawCircle(cx, cy, r, shape)
                val name = operator.fullName.ifBlank { storeName }
                line(Formats.initials(name), cx, cy + 10f, 28f, BrandPalette.DEEP_MAGENTA, bold = true, align = Paint.Align.CENTER)
            }
            shape.style = Paint.Style.STROKE
            shape.strokeWidth = 2.5f
            shape.color = argb(BrandPalette.WHITE)
            canvas.drawCircle(cx, cy, r, shape)
        }

        // ---- Billed-to card -----------------------------------------------------------------

        private fun billedTo() {
            val top = 168f
            line("BILLED TO", MARGIN, top, 9f, BrandPalette.VIBRANT_ROSE, bold = true, spacing = 0.14f)
            val cardTop = top + 10f
            val cardBottom = cardTop + 104f
            roundRect(MARGIN, cardTop, W - MARGIN, cardBottom, 12f, BrandPalette.LAVENDER_BLUSH, BrandPalette.OUTLINE)

            val leftX = MARGIN + 18f
            val dividerX = 318f
            line(buyer.fullName, leftX, cardTop + 34f, 15f, BrandPalette.DEEP_MAGENTA, bold = true, maxWidth = dividerX - leftX - 14f)
            var y = cardTop + 56f
            if (buyer.contact.isNotBlank()) {
                line(buyer.contact, leftX, y, 11f, BrandPalette.INK, maxWidth = dividerX - leftX - 14f)
                y += 18f
            }
            if (buyer.email.isNotBlank()) {
                line(buyer.email, leftX, y, 11f, BrandPalette.MUTED, maxWidth = dividerX - leftX - 14f)
            }

            shape.style = Paint.Style.STROKE
            shape.strokeWidth = 1f
            shape.color = argb(BrandPalette.OUTLINE)
            canvas.drawLine(dividerX, cardTop + 14f, dividerX, cardBottom - 14f, shape)

            val keyX = dividerX + 18f
            val valueX = W - MARGIN - 18f
            val facts = listOf(
                "Purchased" to Formats.dateTime(order.purchasedAt, zone),
                "Payment method" to order.paymentMode.label,
                "Payment status" to order.paymentStatus.label.uppercase(),
                "Fulfillment" to order.orderStatus.label,
            )
            facts.forEachIndexed { index, (key, value) ->
                val rowY = cardTop + 28f + index * 20f
                line(key, keyX, rowY, 9.5f, BrandPalette.MUTED)
                val status = key == "Payment status"
                line(
                    value, valueX, rowY, 10.5f,
                    if (status) BrandPalette.tone(order.paymentStatus).foreground else BrandPalette.INK,
                    bold = true, align = Paint.Align.RIGHT, maxWidth = valueX - keyX - 80f,
                )
            }
        }

        // ---- Itemized table -----------------------------------------------------------------

        /** Draws the header and the single order line; returns the y of the table's bottom edge. */
        private fun table(): Float {
            val top = 306f
            val headerH = 28f
            val itemX = MARGIN + 16f
            // Column layout (points): item 52-252, unit price 262-372, qty 390-434, amount 440-543.
            // Numbers shrink to their column rather than overlap a neighbour or lose digits.
            val itemWidth = 200f
            val unitRight = 372f
            val unitWidth = 108f
            val qtyCenter = 412f
            val qtyWidth = 44f
            val amountRight = W - MARGIN - 16f
            val amountWidth = 103f

            // Header: rose bar with rounded top corners.
            val bar = RectF(MARGIN, top, W - MARGIN, top + headerH)
            shape.style = Paint.Style.FILL
            shape.color = argb(BrandPalette.VIBRANT_ROSE)
            canvas.drawRoundRect(bar, 8f, 8f, shape)
            canvas.drawRect(MARGIN, top + headerH / 2, W - MARGIN, top + headerH, shape)

            val headerBaseline = top + 18f
            line("ITEM", itemX, headerBaseline, 9.5f, BrandPalette.WHITE, bold = true, spacing = 0.1f)
            line("UNIT PRICE", unitRight, headerBaseline, 9.5f, BrandPalette.WHITE, bold = true, align = Paint.Align.RIGHT, spacing = 0.1f)
            line("QTY", qtyCenter, headerBaseline, 9.5f, BrandPalette.WHITE, bold = true, align = Paint.Align.CENTER, spacing = 0.1f)
            line("AMOUNT", amountRight, headerBaseline, 9.5f, BrandPalette.WHITE, bold = true, align = Paint.Align.RIGHT, spacing = 0.1f)

            // Product name wraps (at most 3 lines) so a long name can never run into the price.
            applyStyle(12.5f, BrandPalette.INK, bold = true)
            val nameLayout = StaticLayout.Builder
                .obtain(order.productName, 0, order.productName.length, text, itemWidth.toInt())
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            val rowTop = top + headerH
            val rowH = maxOf(56f, nameLayout.height + 40f)

            canvas.withTranslation(itemX, rowTop + 14f) { nameLayout.draw(this) }
            line("Order ${Formats.orderNumber(order.id)}", itemX, rowTop + 14f + nameLayout.height + 14f, 9f, BrandPalette.MUTED)

            val baseline = rowTop + rowH / 2f + 4f
            line(Money.format(order.unitPriceMinor), unitRight, baseline, 11f, BrandPalette.INK, align = Paint.Align.RIGHT, fitWidth = unitWidth)
            line(order.quantity.toString(), qtyCenter, baseline, 11f, BrandPalette.INK, align = Paint.Align.CENTER, fitWidth = qtyWidth)
            line(Money.format(order.totalMinor), amountRight, baseline, 11.5f, BrandPalette.INK, bold = true, align = Paint.Align.RIGHT, fitWidth = amountWidth)

            // Outline of the whole table, drawn last so it sits on top of the bar's corners.
            val bottom = rowTop + rowH
            shape.style = Paint.Style.STROKE
            shape.strokeWidth = 1f
            shape.color = argb(BrandPalette.OUTLINE)
            canvas.drawRoundRect(RectF(MARGIN, top, W - MARGIN, bottom), 8f, 8f, shape)
            return bottom
        }

        private fun total(tableBottom: Float) {
            val boxW = 290f
            val boxH = 88f
            val left = W - MARGIN - boxW
            val top = tableBottom + 20f
            roundRect(left, top, W - MARGIN, top + boxH, 12f, BrandPalette.SOFT_PINK, null)
            // Label row on top, the amount on its own line below so even a very large total has the full box width.
            line("TOTAL PAID", left + 18f, top + 28f, 10f, BrandPalette.DEEP_MAGENTA, bold = true, spacing = 0.14f)
            line("via ${order.paymentMode.label}", W - MARGIN - 18f, top + 28f, 9.5f, BrandPalette.MUTED, align = Paint.Align.RIGHT)
            line(Money.format(order.totalMinor), W - MARGIN - 18f, top + 66f, 28f, BrandPalette.DEEP_MAGENTA, bold = true, align = Paint.Align.RIGHT, fitWidth = boxW - 36f)
        }

        // ---- Footer -------------------------------------------------------------------------

        private fun footer() {
            shape.style = Paint.Style.STROKE
            shape.strokeWidth = 1f
            shape.color = argb(BrandPalette.OUTLINE)
            canvas.drawLine(MARGIN, H - 96f, W - MARGIN, H - 96f, shape)

            line("Thank you for shopping with $storeName!", W / 2, H - 70f, 13f, BrandPalette.DEEP_MAGENTA, bold = true, align = Paint.Align.CENTER, maxWidth = W - 2 * MARGIN)
            line("This is a computer-generated receipt and does not need a signature.", W / 2, H - 52f, 9f, BrandPalette.MUTED, align = Paint.Align.CENTER)
            line("Generated ${Formats.dateTime(issuedAt, zone)} with BlushDesk", W / 2, H - 38f, 8.5f, BrandPalette.MUTED, align = Paint.Align.CENTER)
            rect(0f, H - 10f, W, H, BrandPalette.VIBRANT_ROSE)
        }

        // ---- Watermark ----------------------------------------------------------------------

        /** A rotated, ~16%-opaque rubber-stamp "PAID" across the middle of the page. */
        private fun paidStamp() {
            val alpha = 42
            applyStyle(124f, BrandPalette.VIBRANT_ROSE, bold = true)
            text.letterSpacing = 0.06f
            text.alpha = alpha
            text.textAlign = Paint.Align.CENTER
            val metrics = text.fontMetrics
            val textWidth = text.measureText("PAID")
            val frame = RectF(-textWidth / 2 - 34f, metrics.ascent - 8f, textWidth / 2 + 34f, metrics.descent + 14f)

            // Drawn around the origin, then moved to the page center and tilted like a rubber stamp.
            canvas.withTranslation(W / 2, H / 2 + 20f) {
                rotate(-22f)
                shape.style = Paint.Style.STROKE
                shape.color = argb(BrandPalette.VIBRANT_ROSE, alpha)
                shape.strokeWidth = 9f
                drawRoundRect(frame, 22f, 22f, shape)
                shape.strokeWidth = 3f
                frame.inset(15f, 15f)
                drawRoundRect(frame, 12f, 12f, shape)
                drawText("PAID", 0f, 0f, text)
            }
        }

        // ---- Primitives ---------------------------------------------------------------------

        private fun applyStyle(size: Float, color: Int, bold: Boolean = false, spacing: Float = 0f) {
            text.textSize = size
            text.color = argb(color)
            text.typeface = if (bold) this.bold else regular
            text.letterSpacing = spacing
            text.textAlign = Paint.Align.LEFT
            text.alpha = 255
        }

        /**
         * One line of text at a baseline. Prose that is too long for [maxWidth] is ellipsized with
         * "…"; numbers pass [fitWidth] instead and are scaled down (never below [MIN_FIT_SIZE]).
         */
        private fun line(
            value: String,
            x: Float,
            baseline: Float,
            size: Float,
            color: Int,
            bold: Boolean = false,
            align: Paint.Align = Paint.Align.LEFT,
            spacing: Float = 0f,
            maxWidth: Float? = null,
            fitWidth: Float? = null,
        ) {
            applyStyle(size, color, bold, spacing)
            text.textAlign = align
            // Money and quantities must never be cut off, so they shrink to fit instead of ellipsizing.
            if (fitWidth != null) {
                val measured = text.measureText(value)
                if (measured > fitWidth) text.textSize = maxOf(MIN_FIT_SIZE, size * fitWidth / measured)
            }
            val shown = if (maxWidth != null) {
                TextUtils.ellipsize(value, text, maxWidth, TextUtils.TruncateAt.END).toString()
            } else {
                value
            }
            canvas.drawText(shown, x, baseline, text)
        }

        private fun rect(l: Float, t: Float, r: Float, b: Float, color: Int) {
            shape.style = Paint.Style.FILL
            shape.color = argb(color)
            canvas.drawRect(l, t, r, b, shape)
        }

        private fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, fill: Int, stroke: Int?) {
            val box = RectF(l, t, r, b)
            shape.style = Paint.Style.FILL
            shape.color = argb(fill)
            canvas.drawRoundRect(box, radius, radius, shape)
            if (stroke != null) {
                shape.style = Paint.Style.STROKE
                shape.strokeWidth = 1f
                shape.color = argb(stroke)
                canvas.drawRoundRect(box, radius, radius, shape)
            }
        }

        private fun argb(rgb: Int, alpha: Int = 255): Int = (alpha shl 24) or rgb
    }

    private companion object {
        const val PAGE_W = 595
        const val PAGE_H = 842
        const val AVATAR_DECODE_PX = 256
    }
}

private const val W = 595f
private const val H = 842f
private const val MARGIN = 36f
private const val HEADER_H = 132f
private const val MIN_FIT_SIZE = 6.5f
