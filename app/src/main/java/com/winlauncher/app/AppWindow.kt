package com.winlauncher.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Contenuti che vogliono gestire "Indietro" / pulizia. */
interface BackAware { fun onBack(): Boolean }
interface Disposable { fun dispose() }

/** Pulsanti della barra del titolo, disegnati a mano (nessuna dipendenza da font). */
class CaptionButton(c: Context, var type: Int) : View(c) {
    companion object { const val MIN = 0; const val MAX = 1; const val RESTORE = 2; const val CLOSE = 3 }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = T.TEXT; style = Paint.Style.STROKE; strokeWidth = c.dpf(1.2f)
    }
    init {
        isClickable = true
        isFocusable = true
        val red = type == CLOSE
        background = android.graphics.drawable.StateListDrawable().apply {
            val hot = if (red) 0xFFC42B1C.toInt() else 0x33FFFFFF
            addState(intArrayOf(android.R.attr.state_pressed), android.graphics.drawable.ColorDrawable(hot))
            addState(intArrayOf(android.R.attr.state_focused), android.graphics.drawable.ColorDrawable(hot))
            addState(intArrayOf(), android.graphics.drawable.ColorDrawable(0))
        }
    }
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = context.dpf(5f)
        when (type) {
            MIN -> canvas.drawLine(cx - s, cy, cx + s, cy, p)
            MAX -> canvas.drawRect(cx - s, cy - s, cx + s, cy + s, p)
            RESTORE -> {
                val o = context.dpf(2f)
                canvas.drawRect(cx - s, cy - s + o, cx + s - o, cy + s, p)
                canvas.drawLine(cx - s + o, cy - s + o, cx - s + o, cy - s, p)
                canvas.drawLine(cx - s + o, cy - s, cx + s, cy - s, p)
                canvas.drawLine(cx + s, cy - s, cx + s, cy + s - o, p)
                canvas.drawLine(cx + s - o, cy + s - o, cx + s, cy + s - o, p)
            }
            CLOSE -> {
                canvas.drawLine(cx - s, cy - s, cx + s, cy + s, p)
                canvas.drawLine(cx - s, cy + s, cx + s, cy - s, p)
            }
        }
    }
}

/**
 * Finestra "desktop" ospitata nel launcher: trascinabile, ridimensionabile dai bordi,
 * minimizzabile, massimizzabile, con snap ai bordi (sinistra/destra/alto) come in Windows 11.
 * Da telecomando: seleziona la barra del titolo e premi OK per alternare spostamento / ridimensionamento.
 */
class AppWindow(
    ctx: Context,
    val appId: String,
    val title: String,
    val glyph: String,
    val content: View
) : FrameLayout(ctx) {

    enum class State { NORMAL, MAXIMIZED, MINIMIZED }

    var state = State.NORMAL
        private set
    var onClose: ((AppWindow) -> Unit)? = null
    var onFocusRequest: ((AppWindow) -> Unit)? = null
    var onStateChanged: ((AppWindow) -> Unit)? = null

    private val titleH = ctx.dp(38)
    private val minW = ctx.dp(260)
    private val minH = ctx.dp(180)
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val restore = Rect()          // left, top, right, bottom
    private var snapSide = 0              // -1 sinistra, 1 destra, 0 nessuno
    private var active = false
    private var keyMode = 0               // 0 nessuno, 1 sposta, 2 ridimensiona
    private var anim: ValueAnimator? = null

    private val bar = LinearLayout(ctx)
    private val titleText = TextView(ctx)
    private val btnMin = CaptionButton(ctx, CaptionButton.MIN)
    private val btnMax = CaptionButton(ctx, CaptionButton.MAX)
    private val btnClose = CaptionButton(ctx, CaptionButton.CLOSE)
    private val handles = mutableListOf<View>()

    init {
        elevation = ctx.dpf(8f)
        clipToOutline = true

        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        bar.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
        }
        val g = TextView(ctx).apply { text = glyph; textSize = 14f; gravity = Gravity.CENTER; includeFontPadding = false }
        bar.addView(g, LinearLayout.LayoutParams(ctx.dp(34), titleH))
        titleText.apply {
            text = title; setTextColor(T.TEXT); textSize = 12f; maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        bar.addView(titleText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val bw = ctx.dp(44)
        for (b in listOf(btnMin, btnMax, btnClose)) bar.addView(b, LinearLayout.LayoutParams(bw, titleH))
        btnMin.setOnClickListener { minimize() }
        btnMax.setOnClickListener { toggleMaximize() }
        btnClose.setOnClickListener { close() }

        column.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, titleH))
        column.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setupBarTouch()
        setupBarKeys()
        val e = ctx.dp(10)
        val c = ctx.dp(26)
        addHandle(RIGHT, LayoutParams(e, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END).apply { topMargin = titleH; bottomMargin = c })
        addHandle(LEFT, LayoutParams(e, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START).apply { topMargin = titleH; bottomMargin = c })
        addHandle(BOTTOM, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, e, Gravity.BOTTOM).apply { marginStart = c; marginEnd = c })
        addHandle(RIGHT or BOTTOM, LayoutParams(c, c, Gravity.BOTTOM or Gravity.END))
        addHandle(LEFT or BOTTOM, LayoutParams(c, c, Gravity.BOTTOM or Gravity.START))
        updateChrome()
    }

    companion object {
        const val LEFT = 1; const val RIGHT = 2; const val BOTTOM = 4
    }

    // ---------- geometria ----------
    private fun lp() = layoutParams as LayoutParams
    private fun pw() = (parent as View).width
    private fun ph() = (parent as View).height

    fun setBounds(x: Int, y: Int, w: Int, h: Int) {
        val p = lp()
        p.leftMargin = x; p.topMargin = y; p.width = w; p.height = h
        layoutParams = p
    }

    private fun bounds() = Rect(lp().leftMargin, lp().topMargin, lp().leftMargin + lp().width, lp().topMargin + lp().height)

    fun defaultPlace(index: Int, startMaximized: Boolean) {
        val w = pw(); val h = ph()
        val cw = min(w, max(minW, (w * 0.62f).toInt()))
        val ch = min(h, max(minH, (h * 0.72f).toInt()))
        val off = (index % 6) * context.dp(30)
        val x = min(max(0, w - cw), (w - cw) / 2 - context.dp(60) + off)
        val y = min(max(0, h - ch), context.dp(24) + off)
        setBounds(max(0, x), y, cw, ch)
        restore.set(max(0, x), y, max(0, x) + cw, y + ch)
        if (startMaximized) { saveRestoreIfFree(); applyMaximized(false) }
    }

    private fun saveRestoreIfFree() {
        if (state == State.NORMAL && snapSide == 0) restore.set(bounds())
    }

    /** Chiamata quando cambia la dimensione dell'area di lavoro (rotazione, split-screen...). */
    fun fitParent() {
        if (parent == null || pw() == 0) return
        when {
            state == State.MAXIMIZED -> setBounds(0, 0, pw(), ph())
            snapSide != 0 -> snap(snapSide, false)
            else -> {
                val w = min(lp().width, pw()); val h = min(lp().height, ph())
                val x = min(max(lp().leftMargin, -(w - context.dp(80))), pw() - context.dp(80))
                val y = min(max(lp().topMargin, 0), max(0, ph() - titleH))
                setBounds(x, y, w, h)
            }
        }
    }

    private fun animateTo(x: Int, y: Int, w: Int, h: Int, end: (() -> Unit)? = null) {
        anim?.cancel()
        val s = bounds()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 160
            addUpdateListener {
                val f = it.animatedValue as Float
                setBounds(
                    (s.left + (x - s.left) * f).toInt(), (s.top + (y - s.top) * f).toInt(),
                    (s.width() + (w - s.width()) * f).toInt(), (s.height() + (h - s.height()) * f).toInt()
                )
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { end?.invoke() }
            })
            start()
        }
    }

    // ---------- stati ----------
    fun toggleMaximize() {
        if (state == State.MAXIMIZED) restoreWindow() else maximize()
    }

    fun maximize() {
        if (state == State.MINIMIZED) restoreFromMinimized()
        saveRestoreIfFree()
        applyMaximized(true)
    }

    private fun applyMaximized(animated: Boolean) {
        state = State.MAXIMIZED; snapSide = 0
        if (animated) animateTo(0, 0, pw(), ph()) else setBounds(0, 0, pw(), ph())
        updateChrome(); onStateChanged?.invoke(this)
    }

    fun restoreWindow() {
        state = State.NORMAL; snapSide = 0
        animateTo(restore.left, restore.top, restore.width(), restore.height())
        updateChrome(); onStateChanged?.invoke(this)
    }

    fun snap(side: Int, animated: Boolean = true) {
        if (state == State.MINIMIZED) restoreFromMinimized()
        saveRestoreIfFree()
        state = State.NORMAL; snapSide = side
        val half = pw() / 2
        val x = if (side < 0) 0 else pw() - half
        if (animated) animateTo(x, 0, half, ph()) else setBounds(x, 0, half, ph())
        updateChrome(); onStateChanged?.invoke(this)
    }

    fun minimize() {
        if (state == State.MINIMIZED) return
        state = State.MINIMIZED
        animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).translationY(ph() * 0.25f).setDuration(180)
            .withEndAction { if (state == State.MINIMIZED) visibility = GONE }.start()
        onStateChanged?.invoke(this)
    }

    /** Riporta in primo piano una finestra minimizzata (torna nello stato precedente). */
    fun unminimize() {
        if (state != State.MINIMIZED) return
        restoreFromMinimized()
        onStateChanged?.invoke(this)
    }

    private fun restoreFromMinimized() {
        state = if (lp().width >= pw() && lp().height >= ph()) State.MAXIMIZED else State.NORMAL
        visibility = VISIBLE
        animate().cancel()
        alpha = 0f; scaleX = 0.7f; scaleY = 0.7f; translationY = ph() * 0.25f
        animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(180).start()
        updateChrome()
    }

    fun close() {
        anim?.cancel()
        (content as? Disposable)?.dispose()
        onClose?.invoke(this)
    }

    fun setActive(a: Boolean) {
        active = a
        elevation = context.dpf(if (a) 24f else 8f)
        updateChrome()
    }

    private fun updateChrome() {
        val max = state == State.MAXIMIZED
        btnMax.type = if (max) CaptionButton.RESTORE else CaptionButton.MAX
        btnMax.invalidate()
        val r = if (max) 0f else 8f
        background = context.roundRect(T.SURFACE, r, if (active) T.ACCENT_DARK else T.STROKE, 1)
        invalidateOutline()
        val barColor = when (keyMode) { 1 -> T.ACCENT_DARK; 2 -> T.ORANGE; else -> if (active) T.BAR_ACTIVE else T.BAR_INACTIVE }
        bar.setBackgroundColor(barColor)
        titleText.alpha = if (active) 1f else 0.7f
        val v = if (max) GONE else VISIBLE
        handles.forEach { it.visibility = v }
    }

    // ---------- trascinamento barra del titolo ----------
    private fun setupBarTouch() {
        val gd = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { toggleMaximize(); return true }
        })
        var dx0 = 0f; var dy0 = 0f; var l0 = 0; var t0 = 0; var dragging = false
        bar.setOnTouchListener { _, e ->
            gd.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    onFocusRequest?.invoke(this)
                    dx0 = e.rawX; dy0 = e.rawY; l0 = lp().leftMargin; t0 = lp().topMargin; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(e.rawX - dx0, e.rawY - dy0) > slop) {
                        dragging = true
                        if (state == State.MAXIMIZED || snapSide != 0) detachForDrag(e.rawX)
                        l0 = lp().leftMargin; t0 = lp().topMargin; dx0 = e.rawX; dy0 = e.rawY
                    }
                    if (dragging) moveTo(l0 + (e.rawX - dx0).toInt(), t0 + (e.rawY - dy0).toInt())
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging && e.actionMasked == MotionEvent.ACTION_UP) endDrag(e.rawX, e.rawY)
                    dragging = false
                }
            }
            true
        }
    }

    private fun parentOrigin(): IntArray { val a = IntArray(2); (parent as View).getLocationOnScreen(a); return a }

    private fun detachForDrag(rawX: Float) {
        val fx = rawX - parentOrigin()[0]
        val w = min(restore.width(), pw()); val h = min(restore.height(), ph())
        val x = (fx - w * (fx / max(1, pw()))).toInt()
        state = State.NORMAL; snapSide = 0
        anim?.cancel()
        setBounds(x, 0, w, h)
        updateChrome(); onStateChanged?.invoke(this)
    }

    private fun moveTo(x: Int, y: Int) {
        val nx = min(max(x, -(lp().width - context.dp(80))), pw() - context.dp(80))
        val ny = min(max(y, 0), max(0, ph() - titleH))
        setBounds(nx, ny, lp().width, lp().height)
    }

    private fun endDrag(rawX: Float, rawY: Float) {
        val o = parentOrigin()
        val px = rawX - o[0]; val py = rawY - o[1]
        val edge = context.dp(10)
        when {
            py <= edge -> maximize()
            px <= edge -> snap(-1)
            px >= pw() - edge -> snap(1)
        }
    }

    // ---------- ridimensionamento dai bordi ----------
    private fun addHandle(flags: Int, lp: LayoutParams) {
        val h = object : View(context) {
            var sx = 0f; var sy = 0f; var b = Rect()
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        onFocusRequest?.invoke(this@AppWindow)
                        parent.requestDisallowInterceptTouchEvent(true)
                        sx = e.rawX; sy = e.rawY; b = bounds()
                    }
                    MotionEvent.ACTION_MOVE -> resize(flags, b, (e.rawX - sx).toInt(), (e.rawY - sy).toInt())
                }
                return true
            }
        }
        handles.add(h)
        addView(h, lp)
    }

    private fun resize(flags: Int, b: Rect, dx: Int, dy: Int) {
        if (state != State.NORMAL) return
        snapSide = 0
        var l = b.left; var w = b.width(); var h = b.height()
        if (flags and RIGHT != 0) w = max(minW, b.width() + dx)
        if (flags and LEFT != 0) { w = max(minW, b.width() - dx); l = b.right - w }
        if (flags and BOTTOM != 0) h = max(minH, b.height() + dy)
        setBounds(l, b.top, w, min(h, ph() - b.top + context.dp(40)))
    }

    // ---------- telecomando / tastiera ----------
    private fun setupBarKeys() {
        val step = context.dp(40)
        bar.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                keyMode = (keyMode + 1) % 3
                updateChrome(); return@setOnKeyListener true
            }
            if (keyMode == 0) return@setOnKeyListener false
            val dx = when (code) { KeyEvent.KEYCODE_DPAD_LEFT -> -step; KeyEvent.KEYCODE_DPAD_RIGHT -> step; else -> 0 }
            val dy = when (code) { KeyEvent.KEYCODE_DPAD_UP -> -step; KeyEvent.KEYCODE_DPAD_DOWN -> step; else -> 0 }
            if (dx == 0 && dy == 0) return@setOnKeyListener false
            if (state == State.MAXIMIZED || snapSide != 0) detachForDrag(pw() / 2f + parentOrigin()[0])
            if (keyMode == 1) moveTo(lp().leftMargin + dx, lp().topMargin + dy)
            else resize(RIGHT or BOTTOM, bounds(), dx, dy)
            true
        }
        bar.setOnFocusChangeListener { _, f -> if (!f) { keyMode = 0; updateChrome() } }
    }

    // ---------- attivazione al tocco ovunque ----------
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && !active) onFocusRequest?.invoke(this)
        return super.dispatchTouchEvent(ev)
    }
}
