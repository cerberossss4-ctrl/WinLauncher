package com.winlauncher.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Elemento della taskbar: status 0 = solo appuntata, 1 = aperta, 2 = attiva. */
class TaskItem(val entry: AppEntry, val status: Int, val onClick: () -> Unit)

class Taskbar(
    c: Context,
    private val onStart: () -> Unit,
    private val onSearch: () -> Unit,
    private val onShowDesktop: () -> Unit
) : FrameLayout(c) {

    private val items = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
    private val clock = TextView(c)
    private val leftSpacer = View(c)
    private val tray = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val battery = TextView(c)

    init {
        setBackgroundColor(T.TASKBAR)
        val row = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }

        row.addView(leftSpacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1, 1f))

        val scroll = HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(items, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        row.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        row.addView(View(c), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1, 1f))

        battery.apply { textSize = 11f; setTextColor(T.TEXT); gravity = Gravity.CENTER; setPadding(c.dp(6), 0, c.dp(6), 0) }
        clock.apply { textSize = 11f; setTextColor(T.TEXT); gravity = Gravity.END or Gravity.CENTER_VERTICAL; setPadding(c.dp(8), 0, c.dp(8), 0) }
        tray.addView(battery, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tray.addView(clock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        row.addView(tray, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // "Mostra desktop" nell'angolo
        val sliver = View(c).apply {
            isClickable = true; isFocusable = true
            background = c.hoverBg(0f)
            setOnClickListener { onShowDesktop() }
        }
        row.addView(sliver, LinearLayout.LayoutParams(c.dp(12), ViewGroup.LayoutParams.MATCH_PARENT))

        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // bordo superiore sottile
        addView(View(c).apply { setBackgroundColor(0x22FFFFFF) }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        updateClock()
    }

    fun updateClock() {
        val now = Date()
        clock.text = timeFmt.format(now) + "\n" + dateFmt.format(now)
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        battery.text = if (level >= 0 && scale > 0) "🔋 ${level * 100 / scale}%" else ""
        battery.visibility = if (battery.text.isEmpty()) GONE else VISIBLE
        // tiene il centro delle icone allineato al centro dello schermo
        tray.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        leftSpacer.minimumWidth = tray.measuredWidth + context.dp(12)
    }

    /** Ricostruisce le icone: Start, Cerca e poi le app. */
    fun refresh(list: List<TaskItem>) {
        val c = context
        items.removeAllViews()
        items.addView(cell(StartLogo(c), 0, onStart), cellLp())
        items.addView(cell(TextView(c).apply { text = "🔍"; textSize = 15f; gravity = Gravity.CENTER; includeFontPadding = false }, 0, onSearch), cellLp())
        for (it in list) items.addView(cell(c.iconView(it.entry, 24, glyphOnly = true), it.status, it.onClick), cellLp())
    }

    private fun cellLp() = LinearLayout.LayoutParams(context.dp(44), context.dp(40)).apply {
        setMargins(context.dp(2), context.dp(4), context.dp(2), context.dp(4))
    }

    private fun cell(icon: View, status: Int, click: () -> Unit): View {
        val c = context
        val f = FrameLayout(c).apply {
            background = c.hoverBg()
            isClickable = true; isFocusable = true
            setOnClickListener { click() }
        }
        f.addView(icon, LayoutParams(c.dp(24), c.dp(24), Gravity.CENTER))
        if (status > 0) {
            val w = if (status == 2) 16 else 6
            val ind = View(c).apply { background = c.roundRect(if (status == 2) T.ACCENT else 0xFF9A9A9A.toInt(), 2f) }
            f.addView(ind, LayoutParams(c.dp(w), c.dp(3), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = c.dp(1) })
        }
        return f
    }
}
