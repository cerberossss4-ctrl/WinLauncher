package com.winlauncher.app

import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val TASKBAR_H get() = dp(48)

    private lateinit var workArea: FrameLayout
    private lateinit var windowLayer: FrameLayout
    private lateinit var taskbar: Taskbar
    private lateinit var startMenu: StartMenu

    private val windows = mutableListOf<AppWindow>()
    private var activeWindow: AppWindow? = null
    private var installed: List<AppEntry> = emptyList()
    private val recentExternal = LinkedHashMap<String, AppEntry>()
    private var openCounter = 0

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { taskbar.updateClock() }
    }
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { loadApps() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.fitsSystemWindows = true
        root.addView(WallpaperView(this), FrameLayout.LayoutParams(-1, -1))

        workArea = FrameLayout(this)
        workArea.addView(buildDesktopIcons(), FrameLayout.LayoutParams(-2, -1))
        windowLayer = FrameLayout(this)
        workArea.addView(windowLayer, FrameLayout.LayoutParams(-1, -1))
        root.addView(workArea, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = TASKBAR_H })

        taskbar = Taskbar(
            this,
            onStart = { toggleStart(false) },
            onSearch = { toggleStart(true) },
            onShowDesktop = { showDesktop() }
        )
        root.addView(taskbar, FrameLayout.LayoutParams(-1, TASKBAR_H, Gravity.BOTTOM))

        startMenu = StartMenu(this, { installed }, { launchEntry(it) }, { openBuiltIn("settings") })
        root.addView(startMenu, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = TASKBAR_H })

        setContentView(root)

        // rotazione, split-screen, finestre libere: adatta tutto allo spazio disponibile
        workArea.addOnLayoutChangeListener { _, l, t, r, b, oL, oT, oR, oB ->
            if (r - l != oR - oL || b - t != oB - oT) {
                startMenu.layoutPanel(r - l, b - t)
                windows.forEach { it.fitParent() }
            }
        }
        workArea.post { startMenu.layoutPanel(workArea.width, workArea.height) }

        refreshTaskbar()
        loadApps()
    }

    // ---------- icone sul desktop ----------
    private fun buildDesktopIcons(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        for (e in builtInEntries()) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(6), dp(4), dp(6))
                background = hoverBg()
                isClickable = true; isFocusable = true
                setOnClickListener { openBuiltIn(e.builtIn!!.id) }
            }
            item.addView(iconView(e, 44), LinearLayout.LayoutParams(dp(44), dp(44)))
            item.addView(TextView(this).apply {
                text = e.label; textSize = 11f; setTextColor(T.TEXT); gravity = Gravity.CENTER; maxLines = 2
                setShadowLayer(4f, 0f, 1f, 0xFF000000.toInt())
                setPadding(0, dp(4), 0, 0)
            }, LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT))
            col.addView(item, LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return col
    }

    // ---------- elenco app installate ----------
    private fun loadApps() {
        Thread {
            val pm = packageManager
            val map = LinkedHashMap<String, AppEntry>()
            for (cat in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)) {
                val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
                for (ri in pm.queryIntentActivities(i, 0)) {
                    val ai = ri.activityInfo
                    if (ai.packageName == packageName) continue
                    val key = ai.packageName + "/" + ai.name
                    if (!map.containsKey(key)) map[key] = AppEntry(ri.loadLabel(pm).toString(), ai.packageName, ai.name, ri.loadIcon(pm), null)
                }
            }
            val sorted = map.values.sortedBy { it.label.lowercase() }
            runOnUiThread { installed = sorted; if (startMenu.showing) startMenu.rebuild() }
        }.start()
    }

    // ---------- menu Start ----------
    private fun toggleStart(search: Boolean) {
        if (startMenu.showing) startMenu.hide() else startMenu.show(search)
    }

    private fun launchEntry(e: AppEntry) {
        val b = e.builtIn
        if (b != null) openBuiltIn(b.id) else launchExternal(e)
    }

    // ---------- finestre integrate ----------
    private fun openBuiltIn(id: String) {
        startMenu.hide()
        val existing = windows.firstOrNull { it.appId == id }
        if (existing != null) { showWindow(existing); return }
        if (windowLayer.width == 0) { windowLayer.post { openBuiltIn(id) }; return }
        val b = BUILTINS.first { it.id == id }
        val content: View = when (id) {
            "browser" -> BrowserView(this)
            "notes" -> NotesView(this)
            "calc" -> CalculatorView(this)
            else -> SettingsView(this)
        }
        val w = AppWindow(this, id, b.name, b.glyph, content)
        w.onClose = { closeWindow(it) }
        w.onFocusRequest = { focusWindow(it) }
        w.onStateChanged = { refreshTaskbar() }
        windowLayer.addView(w, FrameLayout.LayoutParams(dp(400), dp(300)))
        windows.add(w)
        val small = windowLayer.width < dp(600)           // telefono: parte a tutto schermo
        w.defaultPlace(openCounter++, small)
        focusWindow(w)
    }

    private fun showWindow(w: AppWindow) {
        if (w.state == AppWindow.State.MINIMIZED) w.unminimize()
        focusWindow(w)
    }

    private fun focusWindow(w: AppWindow) {
        activeWindow = w
        w.bringToFront()
        windows.forEach { it.setActive(it === w) }
        refreshTaskbar()
    }

    private fun closeWindow(w: AppWindow) {
        windowLayer.removeView(w)
        windows.remove(w)
        if (activeWindow === w) activeWindow = topVisible()
        windows.forEach { it.setActive(it === activeWindow) }
        refreshTaskbar()
    }

    private fun topVisible(): AppWindow? {
        for (i in windowLayer.childCount - 1 downTo 0) {
            val v = windowLayer.getChildAt(i)
            if (v is AppWindow && v.state != AppWindow.State.MINIMIZED) return v
        }
        return null
    }

    private fun taskbarClick(w: AppWindow) {
        when {
            w.state == AppWindow.State.MINIMIZED -> showWindow(w)
            activeWindow === w -> { w.minimize(); activeWindow = topVisible(); windows.forEach { it.setActive(it === activeWindow) }; refreshTaskbar() }
            else -> focusWindow(w)
        }
    }

    private fun showDesktop() {
        startMenu.hide()
        if (windows.any { it.state != AppWindow.State.MINIMIZED }) {
            windows.forEach { it.minimize() }
            activeWindow = null
            windows.forEach { it.setActive(false) }
        } else {
            windows.forEach { it.unminimize() }
            windows.lastOrNull()?.let { focusWindow(it) }
        }
        refreshTaskbar()
    }

    private fun refreshTaskbar() {
        if (!::taskbar.isInitialized) return
        val list = mutableListOf<TaskItem>()
        for (e in builtInEntries()) {
            val id = e.builtIn!!.id
            val w = windows.firstOrNull { it.appId == id }
            val status = when {
                w == null -> 0
                w === activeWindow && w.state != AppWindow.State.MINIMIZED -> 2
                else -> 1
            }
            list.add(TaskItem(e, status) { if (w != null) taskbarClick(w) else openBuiltIn(id) })
        }
        for (e in recentExternal.values) list.add(TaskItem(e, 1) { launchExternal(e) })
        taskbar.refresh(list)
    }

    // ---------- app di terze parti ----------
    private fun launchExternal(e: AppEntry) {
        startMenu.hide()
        val pkg = e.pkg ?: return
        val cls = e.cls ?: return
        val i = Intent(Intent.ACTION_MAIN).setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        var opts: Bundle? = null
        // Se il sistema supporta le finestre libere (Android 14+ desktop mode, Samsung DeX, ecc.),
        // l'app si apre in una finestra ridimensionabile con limiti propri.
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) && windowLayer.width > 0) {
            val loc = IntArray(2)
            windowLayer.getLocationOnScreen(loc)
            val w = (windowLayer.width * 0.7f).toInt()
            val h = (windowLayer.height * 0.8f).toInt()
            val l = loc[0] + (windowLayer.width - w) / 2
            val t = loc[1] + (windowLayer.height - h) / 2
            opts = ActivityOptions.makeBasic().setLaunchBounds(Rect(l, t, l + w, t + h)).toBundle()
        }
        try {
            startActivity(i, opts)
            recentExternal.remove(pkg + "/" + cls)
            recentExternal[pkg + "/" + cls] = e
            while (recentExternal.size > 5) recentExternal.remove(recentExternal.keys.first())
            refreshTaskbar()
        } catch (ex: Exception) {
            Toast.makeText(this, "Impossibile aprire " + e.label, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- ciclo di vita ----------
    override fun onResume() {
        super.onResume()
        registerReceiver(tick, IntentFilter(Intent.ACTION_TIME_TICK).apply { addAction(Intent.ACTION_TIMEZONE_CHANGED) })
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addDataScheme("package")
        })
        taskbar.updateClock()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(tick); unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (startMenu.showing) startMenu.hide()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (startMenu.showing) { startMenu.hide(); return }
        val w = activeWindow ?: return
        (w.content as? BackAware)?.onBack()
        // un launcher non si chiude mai
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { toggleStart(false); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)   // gestito dal listener di layout
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
