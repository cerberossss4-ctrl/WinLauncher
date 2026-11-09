package com.winlauncher.app

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.math.BigDecimal
import java.math.MathContext

private fun label(c: Context, t: String, sp: Float = 14f, color: Int = T.TEXT) =
    TextView(c).apply { text = t; textSize = sp; setTextColor(color) }

// ======================= NOTE =======================
class NotesView(c: Context) : FrameLayout(c) {
    private val prefs = c.getSharedPreferences("notes", Context.MODE_PRIVATE)

    init {
        val et = EditText(c).apply {
            setText(prefs.getString("text", ""))
            hint = "Scrivi qui..."
            setHintTextColor(0x88FFFFFF.toInt())
            setTextColor(T.TEXT)
            textSize = 15f
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = null
            setPadding(c.dp(12), c.dp(10), c.dp(12), c.dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun afterTextChanged(s: Editable?) { prefs.edit().putString("text", s.toString()).apply() }
            })
        }
        addView(et, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}

// ======================= CALCOLATRICE =======================
class CalculatorView(c: Context) : LinearLayout(c) {
    private val screen = TextView(c)
    private var cur = "0"
    private var acc: Double? = null
    private var op: String? = null
    private var fresh = true

    init {
        orientation = VERTICAL
        setPadding(c.dp(6), c.dp(6), c.dp(6), c.dp(6))
        screen.apply {
            text = "0"; textSize = 34f; setTextColor(T.TEXT); gravity = Gravity.END or Gravity.BOTTOM
            maxLines = 1; setPadding(c.dp(8), c.dp(8), c.dp(8), c.dp(8))
        }
        addView(screen, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.1f))
        val rows = listOf(
            listOf("%", "C", "⌫", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("±", "0", ".", "=")
        )
        for (r in rows) {
            val row = LinearLayout(c)
            for (k in r) {
                val isOp = k in listOf("÷", "×", "−", "+")
                val base = when {
                    k == "=" -> T.ACCENT_DARK
                    k[0].isDigit() || k == "." -> 0xFF3B3B3B.toInt()
                    else -> 0xFF323232.toInt()
                }
                val b = TextView(c).apply {
                    text = k; textSize = if (isOp) 20f else 18f
                    setTextColor(T.TEXT); gravity = Gravity.CENTER
                    isClickable = true; isFocusable = true
                    background = c.buttonBg(base, 0xFF555555.toInt())
                    setOnClickListener { press(k) }
                }
                row.addView(b, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(c.dp(2), c.dp(2), c.dp(2), c.dp(2)) })
            }
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun fmt(v: Double): String =
        if (v.isNaN() || v.isInfinite()) "Errore"
        else BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString()

    private fun compute() {
        val v = cur.toDoubleOrNull() ?: 0.0
        val a = acc
        val o = op
        val r = if (a == null || o == null) v else when (o) {
            "+" -> a + v; "−" -> a - v; "×" -> a * v; else -> a / v
        }
        if (r.isNaN() || r.isInfinite()) { cur = "Errore"; acc = null; op = null; fresh = true; return }
        acc = r; cur = fmt(r)
    }

    private fun press(k: String) {
        if (cur == "Errore" && k != "C") { if (k[0].isDigit()) { cur = "0"; fresh = true } else return }
        when {
            k[0].isDigit() -> { if (fresh) { cur = k; fresh = false } else cur = if (cur == "0") k else cur + k }
            k == "." -> { if (fresh) { cur = "0."; fresh = false } else if (!cur.contains('.')) cur += "." }
            k == "C" -> { cur = "0"; acc = null; op = null; fresh = true }
            k == "⌫" -> { cur = if (!fresh && cur.length > 1) cur.dropLast(1) else "0" }
            k == "±" -> { cur = if (cur.startsWith("-")) cur.drop(1) else if (cur != "0") "-$cur" else cur }
            k == "%" -> { cur = fmt((cur.toDoubleOrNull() ?: 0.0) / 100); fresh = true }
            k == "=" -> { if (!(fresh && op != null)) compute(); op = null; fresh = true }
            else -> { if (!(fresh && op != null)) compute(); if (cur != "Errore") { op = k; fresh = true } }
        }
        screen.text = cur
        screen.textSize = if (cur.length > 11) 22f else 34f
    }
}

// ======================= BROWSER =======================
class BrowserView(c: Context) : LinearLayout(c), BackAware, Disposable {
    private val web = WebView(c)
    private val url = EditText(c)

    init {
        orientation = VERTICAL
        val bar = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(c.dp(4), c.dp(4), c.dp(4), c.dp(4)) }
        fun btn(t: String, a: () -> Unit) = TextView(c).apply {
            text = t; textSize = 18f; setTextColor(T.TEXT); gravity = Gravity.CENTER
            isClickable = true; isFocusable = true; background = c.hoverBg()
            setOnClickListener { a() }
        }
        bar.addView(btn("←") { if (web.canGoBack()) web.goBack() }, LayoutParams(c.dp(38), c.dp(38)))
        bar.addView(btn("→") { if (web.canGoForward()) web.goForward() }, LayoutParams(c.dp(38), c.dp(38)))
        bar.addView(btn("⟳") { web.reload() }, LayoutParams(c.dp(38), c.dp(38)))
        url.apply {
            setSingleLine(true); textSize = 13f; setTextColor(T.TEXT)
            hint = "Cerca o digita un indirizzo"; setHintTextColor(0x88FFFFFF.toInt())
            background = c.roundRect(0xFF3A3A3A.toInt(), 16f)
            setPadding(c.dp(14), 0, c.dp(14), 0)
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setOnEditorActionListener { _, id, ev ->
                if (id == EditorInfo.IME_ACTION_GO || (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    go(text.toString()); true
                } else false
            }
        }
        bar.addView(url, LayoutParams(0, c.dp(36), 1f).apply { setMargins(c.dp(4), 0, c.dp(4), 0) })
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.builtInZoomControls = true
        web.settings.displayZoomControls = false
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, u: String?, f: Bitmap?) { if (!url.hasFocus()) url.setText(u) }
        }
        addView(web, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        go("https://duckduckgo.com")
    }

    fun go(q: String) {
        val s = q.trim()
        if (s.isEmpty()) return
        val u = when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            s.contains('.') && !s.contains(' ') -> "https://$s"
            else -> "https://duckduckgo.com/?q=" + Uri.encode(s)
        }
        web.loadUrl(u)
        url.clearFocus()
    }

    override fun onBack(): Boolean = if (web.canGoBack()) { web.goBack(); true } else false
    override fun dispose() { web.stopLoading(); (web.parent as? ViewGroup)?.removeView(web); web.destroy() }
}

// ======================= IMPOSTAZIONI =======================
class SettingsView(c: Context) : ScrollView(c) {
    init {
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(c.dp(16), c.dp(12), c.dp(16), c.dp(16)) }
        col.addView(label(c, "Impostazioni", 22f).apply { setPadding(0, 0, 0, c.dp(10)) })

        fun row(glyph: String, name: String, action: String) {
            val r = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(c.dp(12), 0, c.dp(12), 0)
                background = c.buttonBg(T.SURFACE2, 0xFF4A4A4A.toInt())
                isClickable = true; isFocusable = true
                setOnClickListener {
                    try { c.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (e: Exception) { Toast.makeText(c, "Non disponibile su questo dispositivo", Toast.LENGTH_SHORT).show() }
                }
            }
            r.addView(label(c, glyph, 18f), LinearLayout.LayoutParams(c.dp(36), ViewGroup.LayoutParams.WRAP_CONTENT))
            r.addView(label(c, name, 14f))
            col.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(48)).apply { bottomMargin = c.dp(6) })
        }
        row("📶", "Rete e Wi-Fi", Settings.ACTION_WIFI_SETTINGS)
        row("🔵", "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS)
        row("🖥️", "Schermo", Settings.ACTION_DISPLAY_SETTINGS)
        row("🔊", "Suono", Settings.ACTION_SOUND_SETTINGS)
        row("📦", "App", Settings.ACTION_APPLICATION_SETTINGS)
        row("🏠", "Launcher predefinito", Settings.ACTION_HOME_SETTINGS)
        row("⚙️", "Tutte le impostazioni di Android", Settings.ACTION_SETTINGS)

        val ui = c.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val kind = when {
            ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION -> "TV"
            c.resources.configuration.smallestScreenWidthDp >= 600 -> "Tablet"
            else -> "Telefono"
        }
        val freeform = c.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        col.addView(label(c, "Informazioni", 16f).apply { setPadding(0, c.dp(14), 0, c.dp(4)) })
        col.addView(label(
            c,
            "Win Launcher 1.0\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nDispositivo: $kind\n" +
                "Finestre libere per app di terze parti: " + if (freeform) "supportate" else "non supportate dal sistema",
            12f, T.TEXT2
        ))
        addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
}
