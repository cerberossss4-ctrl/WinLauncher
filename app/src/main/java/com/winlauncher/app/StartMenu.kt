package com.winlauncher.app

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/** Overlay a schermo intero: tocca fuori dal pannello per chiuderlo. */
class StartMenu(
    c: Context,
    private val apps: () -> List<AppEntry>,
    private val onLaunch: (AppEntry) -> Unit,
    private val onSettings: () -> Unit
) : FrameLayout(c) {

    private val panel = LinearLayout(c)
    private val search = EditText(c)
    private val scroll = ScrollView(c)
    private val list = LinearLayout(c)
    private var panelW = c.dp(360)
    private var firstResult: AppEntry? = null

    init {
        visibility = GONE
        isClickable = true
        setOnClickListener { hide() }

        panel.orientation = LinearLayout.VERTICAL
        panel.background = c.roundRect(0xF2262626.toInt(), 8f, T.STROKE, 1)
        panel.elevation = c.dpf(24f)
        panel.isClickable = true

        search.apply {
            setSingleLine(true); textSize = 14f; setTextColor(T.TEXT)
            hint = "Digita qui per cercare"; setHintTextColor(0x99FFFFFF.toInt())
            background = c.roundRect(0xFF3A3A3A.toInt(), 18f, T.STROKE, 1)
            setPadding(c.dp(16), 0, c.dp(16), 0)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun afterTextChanged(s: Editable?) { rebuild() }
            })
            setOnEditorActionListener { _, _, _ -> firstResult?.let { launch(it) }; true }
        }
        panel.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(40)).apply {
            setMargins(c.dp(16), c.dp(16), c.dp(16), c.dp(8))
        })

        list.orientation = LinearLayout.VERTICAL
        list.setPadding(c.dp(12), 0, c.dp(12), c.dp(8))
        scroll.addView(list, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // barra inferiore
        val foot = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0x33000000)
            setPadding(c.dp(20), 0, c.dp(12), 0)
        }
        foot.addView(TextView(c).apply { text = "Win Launcher"; textSize = 13f; setTextColor(T.TEXT) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        foot.addView(TextView(c).apply {
            text = "⚙️"; textSize = 18f; gravity = Gravity.CENTER
            isClickable = true; isFocusable = true; background = c.hoverBg()
            setOnClickListener { hide(); onSettings() }
        }, LinearLayout.LayoutParams(c.dp(44), c.dp(44)))
        panel.addView(foot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(56)))

        addView(panel, LayoutParams(panelW, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
    }

    /** Adatta il pannello allo spazio disponibile (telefono verticale, tablet, TV...). */
    fun layoutPanel(areaW: Int, areaH: Int) {
        val c = context
        panelW = min(c.dp(640), areaW - c.dp(16))
        val h = min(c.dp(660), areaH - c.dp(12))
        val p = panel.layoutParams as LayoutParams
        p.width = panelW; p.height = max(c.dp(240), h); p.bottomMargin = c.dp(8)
        panel.layoutParams = p
        if (isShown) rebuild()
    }

    val showing get() = visibility == VISIBLE

    fun show(focusSearch: Boolean) {
        search.setText("")
        rebuild()
        visibility = VISIBLE
        panel.alpha = 0f
        panel.translationY = context.dpf(40f)
        panel.animate().alpha(1f).translationY(0f).setDuration(160).start()
        scroll.scrollTo(0, 0)
        if (focusSearch) {
            search.requestFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        } else {
            post { findFirstFocusable(list)?.requestFocus() }
        }
    }

    fun hide() {
        if (!showing) return
        search.clearFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(windowToken, 0)
        panel.animate().alpha(0f).translationY(context.dpf(40f)).setDuration(120)
            .withEndAction { visibility = GONE }.start()
    }

    private fun launch(e: AppEntry) { hide(); onLaunch(e) }

    private fun findFirstFocusable(v: View): View? {
        if (v.isFocusable && v !is EditText) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findFirstFocusable(v.getChildAt(i))?.let { return it }
        return null
    }

    fun rebuild() {
        val c = context
        list.removeAllViews()
        val q = search.text.toString().trim().lowercase()
        val all = apps()
        val builtins = builtInEntries()
        val cell = c.dp(88)
        val cols = max(3, (panelW - c.dp(24)) / cell)

        if (q.isEmpty()) {
            list.addView(section("App bloccate"))
            addGrid(builtins, cols)
            list.addView(section("Tutte le app"))
            addGrid(all, cols)
            firstResult = builtins.firstOrNull()
        } else {
            val res = (builtins + all).filter { it.label.lowercase().contains(q) }
            list.addView(section(if (res.isEmpty()) "Nessun risultato" else "Risultati"))
            addGrid(res, cols)
            firstResult = res.firstOrNull()
        }
    }

    private fun section(t: String) = TextView(context).apply {
        text = t; textSize = 13f; setTextColor(T.TEXT)
        setPadding(context.dp(8), context.dp(12), 0, context.dp(6))
    }

    private fun addGrid(entries: List<AppEntry>, cols: Int) {
        val c = context
        var row: LinearLayout? = null
        entries.forEachIndexed { i, e ->
            if (i % cols == 0) {
                row = LinearLayout(c)
                list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            row!!.addView(tile(e), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        // riempie l'ultima riga cosi' le colonne restano allineate
        val rem = entries.size % cols
        if (rem != 0) for (k in rem until cols) row?.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
    }

    private fun tile(e: AppEntry): View {
        val c = context
        val t = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(c.dp(4), c.dp(8), c.dp(4), c.dp(8))
            background = c.hoverBg()
            isClickable = true; isFocusable = true
            setOnClickListener { launch(e) }
        }
        t.addView(c.iconView(e, 40), LinearLayout.LayoutParams(c.dp(40), c.dp(40)))
        t.addView(TextView(c).apply {
            text = e.label; textSize = 11f; setTextColor(T.TEXT); gravity = Gravity.CENTER
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, c.dp(6), 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return t
    }
}
