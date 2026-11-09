package com.winlauncher.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.min

/** Palette in stile Windows 11 (tema scuro). */
object T {
    val TASKBAR = 0xFF202020.toInt()
    val SURFACE = 0xFF2B2B2B.toInt()
    val SURFACE2 = 0xFF383838.toInt()
    val BAR_ACTIVE = 0xFF1F1F1F.toInt()
    val BAR_INACTIVE = 0xFF2E2E2E.toInt()
    val ACCENT = 0xFF60CDFF.toInt()
    val ACCENT_DARK = 0xFF0067C0.toInt()
    val ORANGE = 0xFFF7630C.toInt()
    val TEXT = 0xFFFFFFFF.toInt()
    val TEXT2 = 0xFFC8C8C8.toInt()
    const val HOVER = 0x26FFFFFF
    const val STROKE = 0x33FFFFFF
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.roundRect(color: Int, radiusDp: Float, stroke: Int = 0, strokeDp: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dpf(radiusDp)
        if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }

/** Sfondo trasparente con feedback per tocco e focus (telecomando / tastiera). */
fun Context.hoverBg(radiusDp: Float = 6f): StateListDrawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), roundRect(0x40FFFFFF, radiusDp))
    addState(intArrayOf(android.R.attr.state_focused), roundRect(0x33FFFFFF, radiusDp, T.ACCENT, 2))
    addState(intArrayOf(), ColorDrawable(0))
}

fun Context.buttonBg(base: Int, pressed: Int, radiusDp: Float = 4f): StateListDrawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), roundRect(pressed, radiusDp))
    addState(intArrayOf(android.R.attr.state_focused), roundRect(base, radiusDp, T.ACCENT, 2))
    addState(intArrayOf(), roundRect(base, radiusDp))
}

/** App integrata (disegnata con una glifo) */
data class BuiltIn(val id: String, val name: String, val glyph: String, val color: Int)

val BUILTINS = listOf(
    BuiltIn("browser", "Browser", "🌐", 0xFF0F6CBD.toInt()),
    BuiltIn("notes", "Note", "📝", 0xFFB58900.toInt()),
    BuiltIn("calc", "Calcolatrice", "🧮", 0xFF107C10.toInt()),
    BuiltIn("settings", "Impostazioni", "⚙️", 0xFF5C5C5C.toInt())
)

/** Voce di app: installata (pkg/cls/icon) oppure integrata. */
class AppEntry(
    val label: String,
    val pkg: String?,
    val cls: String?,
    val icon: Drawable?,
    val builtIn: BuiltIn?
)

fun builtInEntries(): List<AppEntry> = BUILTINS.map { AppEntry(it.name, null, null, null, it) }

/** Icona quadrata per una voce; sizeDp e` il lato. */
fun Context.iconView(e: AppEntry, sizeDp: Int, glyphOnly: Boolean = false): View {
    val b = e.builtIn
    if (b != null) {
        return TextView(this).apply {
            text = b.glyph
            gravity = Gravity.CENTER
            textSize = sizeDp * 0.55f
            if (!glyphOnly) background = roundRect(b.color, sizeDp * 0.22f)
            includeFontPadding = false
        }
    }
    return ImageView(this).apply {
        setImageDrawable(e.icon)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
}

/** Sfondo del desktop: gradiente blu con petali luminosi (originale, non e` lo sfondo Microsoft). */
class WallpaperView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(0xFF031B3F.toInt(), 0xFF0A4DA8.toInt(), 0xFF1E88E5.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, p)
        val cx = w * 0.5f
        val cy = h * 0.46f
        val r = min(w, h) * 0.42f
        for (i in 0 until 6) {
            canvas.save()
            canvas.rotate(i * 60f + 15f, cx, cy)
            p.shader = RadialGradient(
                cx, cy - r * 0.5f, r * 0.75f,
                intArrayOf(0x996CC4FF.toInt(), 0x002F7BE0), null, Shader.TileMode.CLAMP
            )
            canvas.drawOval(RectF(cx - r * 0.3f, cy - r, cx + r * 0.3f, cy), p)
            canvas.restore()
        }
        p.shader = null
    }
}

/** Logo "quattro riquadri" del pulsante Start. */
class StartLogo(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = T.ACCENT }
    override fun onDraw(canvas: Canvas) {
        val s = min(width, height) * 0.82f
        val g = s * 0.07f
        val q = (s - g) / 2f
        val x0 = (width - s) / 2f
        val y0 = (height - s) / 2f
        val r = s * 0.06f
        for (i in 0..1) for (j in 0..1) {
            val l = x0 + i * (q + g)
            val t = y0 + j * (q + g)
            canvas.drawRoundRect(RectF(l, t, l + q, t + q), r, r, p)
        }
    }
}
