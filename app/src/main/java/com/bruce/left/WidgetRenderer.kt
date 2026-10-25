package com.bruce.left

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.min

/**
 * Dessine les widgets en bitmap : carte "Liquid Glass" noir et blanc + grille de points.
 * Trois calques partagent exactement la même mise en page :
 *  - STATIC : tout, point actuel bien blanc (animation désactivée)
 *  - BASE   : tout, point actuel atténué
 *  - GLOW   : uniquement le halo du point actuel (calque transparent qui pulse par-dessus)
 */
object WidgetRenderer {

    enum class Layer { STATIC, BASE, GLOW }

    private fun white(a: Int) = Color.argb(a, 255, 255, 255)

    fun render(
        p: TimeProgress,
        w: Int,
        h: Int,
        d: Float,
        glass: Boolean,
        square: Boolean,
        layer: Layer,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val minSide = min(w, h).toFloat()
        val inset = 1.5f * d
        val rect = RectF(inset, inset, w - inset, h - inset)
        val radius = min(26f * d, minSide * 0.22f)

        if (layer != Layer.GLOW) drawGlass(c, rect, radius, d, glass)

        val pad = if (minSide < 130f * d) 11f * d else 15f * d
        val titleSize = (minSide / d * 0.085f).coerceIn(11f, 16f) * d
        val compact = h < 100f * d

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = titleSize
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white(215)
            textSize = titleSize * 1.05f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white(130)
            textSize = titleSize * 0.8f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        val left = rect.left + pad
        val right = rect.right - pad
        var y = rect.top + pad + titleSize * 0.82f

        if (layer != Layer.GLOW) {
            var maxTitle = right - left
            if (p.total > 0) {
                val t = "${(p.fraction * 100f).toInt()}%"
                c.drawText(t, right, y, pctPaint)
                maxTitle -= pctPaint.measureText(t) + 8f * d
            }
            c.drawText(fit(p.title, titlePaint, maxTitle), left, y, titlePaint)
        }
        if (!compact || p.total <= 0) {
            y += subPaint.textSize * 1.45f
            if (layer != Layer.GLOW) c.drawText(fit(p.subtitle, subPaint, right - left), left, y, subPaint)
        }
        if (p.total <= 0) return bmp

        val area = RectF(left, y + 10f * d, right, rect.bottom - pad)
        if (area.height() < 6f * d || area.width() < 6f * d) return bmp

        // Choisit le nombre de colonnes qui donne les plus gros points possibles
        var bestCols = 1
        var bestCell = 0f
        for (cols in 1..p.total) {
            val rows = (p.total + cols - 1) / cols
            val cell = min(area.width() / cols, area.height() / rows)
            if (cell > bestCell) {
                bestCell = cell
                bestCols = cols
            }
        }
        val cols = bestCols
        val rows = (p.total + cols - 1) / cols
        val cell = bestCell
        val r = min(cell * 0.34f, 9f * d)
        val ox = area.left + (area.width() - cols * cell) / 2f
        val oy = area.top + (area.height() - rows * cell) / 2f

        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until p.total) {
            val cx = ox + (i % cols) * cell + cell / 2f
            val cy = oy + (i / cols) * cell + cell / 2f
            when {
                i < p.elapsed -> if (layer != Layer.GLOW) {
                    dot.color = white(50)
                    drawDot(c, cx, cy, r, square, dot)
                }
                i == p.elapsed -> when (layer) {
                    Layer.STATIC -> {
                        dot.color = Color.WHITE
                        drawDot(c, cx, cy, r * 1.1f, square, dot)
                    }
                    Layer.BASE -> {
                        dot.color = white(120)
                        drawDot(c, cx, cy, r * 0.85f, square, dot)
                    }
                    Layer.GLOW -> {
                        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = white(115)
                            maskFilter = BlurMaskFilter(r * 1.4f, BlurMaskFilter.Blur.NORMAL)
                        }
                        c.drawCircle(cx, cy, r * 1.9f, glow)
                        dot.color = Color.WHITE
                        drawDot(c, cx, cy, r * 1.15f, square, dot)
                    }
                }
                else -> if (layer != Layer.GLOW) {
                    dot.color = white(235)
                    drawDot(c, cx, cy, r, square, dot)
                }
            }
        }
        return bmp
    }

    private fun drawDot(c: Canvas, cx: Float, cy: Float, r: Float, square: Boolean, paint: Paint) {
        if (square) {
            c.drawRoundRect(cx - r, cy - r, cx + r, cy + r, r * 0.35f, r * 0.35f, paint)
        } else {
            c.drawCircle(cx, cy, r, paint)
        }
    }

    private fun drawGlass(c: Canvas, rect: RectF, radius: Float, d: Float, glass: Boolean) {
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }

        // Fond : verre fumé translucide, ou noir profond
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = if (glass) Color.argb(140, 10, 10, 10) else Color.rgb(4, 4, 4)
        c.drawPath(path, fill)

        // Reflet diagonal
        fill.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(white(if (glass) 48 else 26), white(6), white(if (glass) 24 else 12)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawPath(path, fill)

        // Lumière en haut de la carte
        c.save()
        c.clipPath(path)
        val sheenH = rect.height() * 0.42f
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, rect.top, 0f, rect.top + sheenH, white(38), white(0), Shader.TileMode.CLAMP)
        }
        c.drawRect(rect.left, rect.top, rect.right, rect.top + sheenH, sheen)
        c.restore()

        // Bordure lumineuse
        val sw = 1.2f * d
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = sw
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(white(175), white(22), white(100)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        val br = RectF(rect)
        br.inset(sw / 2f, sw / 2f)
        c.drawRoundRect(br, radius, radius, stroke)
    }

    private fun fit(text: String, paint: Paint, max: Float): String {
        if (max <= 0f) return ""
        if (paint.measureText(text) <= max) return text
        var s = text
        while (s.isNotEmpty() && paint.measureText("$s…") > max) s = s.dropLast(1)
        return "$s…"
    }
}
