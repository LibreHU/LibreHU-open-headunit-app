package com.andrerinas.openheadunit.librehu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.AppThemeManager
import com.andrerinas.openheadunit.utils.Settings

/**
 * LibreHU fork: light / dark and accent of the LibreHU launcher. The launcher publishes its effective theme in
 * `content://org.librehu.launcher.theme/theme` (one row: `dark` 0/1, `accent` ARGB) and broadcasts
 * `org.librehu.action.THEME_CHANGED` (same values as extras) on every change.
 *
 * While [following] (launcher installed and the option on, the default): the night mode of the app is the launcher's,
 * and the launcher accent (one of its 8, light or dark variant) replaces Open Headunit's teal through a
 * `ThemeOverlay.LibreHU.*` put on every activity theme ([applyTo]); the launcher's segment display font (`font` column /
 * extra `segment`) adds `ThemeOverlay.LibreHU.Segment`. Otherwise nothing changes. (The launcher's retro colours,
 * drawn from any colour, are not followed here: these screens keep their themes, with the accent nearest to it.)
 */
object LibreHuTheme {
    private val URI: Uri = Uri.parse("content://org.librehu.launcher.theme/theme")
    private const val ACTION_THEME_CHANGED = "org.librehu.action.THEME_CHANGED"

    /** Launcher accents in its order (BLUE … PURPLE): dark variant, light variant, overlay. */
    private val ACCENTS =
        listOf(
            Triple(0xFF8AB4F8.toInt(), 0xFF1A73E8.toInt(), R.style.ThemeOverlay_LibreHU_Blue),
            Triple(0xFF78D9EC.toInt(), 0xFF12858F.toInt(), R.style.ThemeOverlay_LibreHU_Teal),
            Triple(0xFF81C995.toInt(), 0xFF188038.toInt(), R.style.ThemeOverlay_LibreHU_Green),
            Triple(0xFFFDD663.toInt(), 0xFFB06000.toInt(), R.style.ThemeOverlay_LibreHU_Yellow),
            Triple(0xFFFCAD70.toInt(), 0xFFE8710A.toInt(), R.style.ThemeOverlay_LibreHU_Orange),
            Triple(0xFFF28B82.toInt(), 0xFFD93025.toInt(), R.style.ThemeOverlay_LibreHU_Red),
            Triple(0xFFFF8BCB.toInt(), 0xFFC5221F.toInt(), R.style.ThemeOverlay_LibreHU_Pink),
            Triple(0xFFC58AF9.toInt(), 0xFF9334E6.toInt(), R.style.ThemeOverlay_LibreHU_Purple),
        )

    @Volatile private var available = false

    @Volatile private var enabled = false

    @Volatile var dark = true
        private set

    @Volatile private var overlay = 0

    @Volatile private var segment = false
    private var listening = false

    /** True when the app takes the launcher's theme. */
    val following: Boolean get() = available && enabled

    /** Reads the launcher's theme (synchronously, activities need it before their first frame) and follows it. */
    fun start(context: Context) {
        val app = context.applicationContext
        available = app.packageManager.resolveContentProvider(URI.authority!!, 0) != null
        enabled = Settings(app).librehuFollowLauncherTheme
        if (!available) return
        read(app)?.let { (d, a, st) -> onLauncherTheme(d, a, st, notify = false) }
        if (listening) return
        listening = true
        val handler = Handler(Looper.getMainLooper())
        runCatching {
            app.contentResolver.registerContentObserver(
                URI,
                false,
                object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) {
                        read(app)?.let { (d, a, st) -> onLauncherTheme(d, a, st, notify = true) }
                    }
                },
            )
        }.onFailure { AppLog.w("LibreHuTheme: observer: ${it.message}") }
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (!intent.hasExtra("dark") || !intent.hasExtra("accent")) return
                    onLauncherTheme(intent.getBooleanExtra("dark", true), intent.getIntExtra("accent", 0), intent.getStringExtra("font"), notify = true)
                }
            },
            IntentFilter(ACTION_THEME_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /** The option of the dark mode screen: on, the launcher's theme; off, Open Headunit's own theme settings. */
    fun setEnabled(
        context: Context,
        on: Boolean,
    ) {
        val settings = Settings(context)
        settings.librehuFollowLauncherTheme = on
        enabled = on
        if (on && available) read(context.applicationContext)?.let { (d, a, st) -> onLauncherTheme(d, a, st, notify = false) }
        AppThemeManager.reapply(context, settings)
        AppThemeManager.signalVisualChange()
    }

    fun isAvailable(context: Context): Boolean = context.packageManager.resolveContentProvider(URI.authority!!, 0) != null

    /** Night mode of the app while following; called by [AppThemeManager.reapply]. */
    fun applyNightMode() {
        val mode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** Puts the launcher accent over [theme]; call after every theme resource applied to an activity or dialog. */
    fun applyTo(theme: Resources.Theme) {
        val o = overlay
        if (following && o != 0) theme.applyStyle(o, true)
        if (following && segment) theme.applyStyle(R.style.ThemeOverlay_LibreHU_Segment, true)
    }

    /** The accent of [context]'s theme: the launcher's while following, else Open Headunit's teal. */
    fun accent(context: Context): Int {
        val v = TypedValue()
        return if (context.theme.resolveAttribute(R.attr.librehuAccent, v, true) && v.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
            v.data
        } else {
            ContextCompat.getColor(context, R.color.brand_teal)
        }
    }

    private fun read(context: Context): Triple<Boolean, Int, String?>? =
        try {
            context.contentResolver.query(URI, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return null
                val d = c.getColumnIndex("dark").takeIf { it >= 0 }?.let { c.getInt(it) != 0 } ?: true
                val a = c.getColumnIndex("accent").takeIf { it >= 0 }?.let { c.getInt(it) } ?: return null
                val st = c.getColumnIndex("font").takeIf { it >= 0 }?.let { c.getString(it) }
                Triple(d, a, st)
            }
        } catch (e: Exception) {
            AppLog.w("LibreHuTheme: ${e.message}")
            null
        }

    private fun onLauncherTheme(
        dark: Boolean,
        accent: Int,
        font: String?,
        notify: Boolean,
    ) {
        val o = nearest(accent)
        val seg = font == "segment"
        if (dark == this.dark && o == overlay && seg == segment) return
        val accentChanged = o != overlay || seg != segment
        segment = seg
        this.dark = dark
        overlay = o
        AppLog.i("LibreHuTheme: launcher theme dark=$dark accent=#${Integer.toHexString(accent)} font=$font")
        if (!notify || !following) return
        Handler(Looper.getMainLooper()).post {
            // The night mode recreates the activities by itself (the projection only gets a configuration change);
            // a new accent needs the recreate of AppThemeManager's signal.
            applyNightMode()
            if (accentChanged) AppThemeManager.signalVisualChange()
        }
    }

    /** The overlay of the launcher accent closest to [argb] (the launcher only publishes its 8, in either variant). */
    private fun nearest(argb: Int): Int {
        fun distance(
            a: Int,
            b: Int,
        ): Int {
            val r = Color.red(a) - Color.red(b)
            val g = Color.green(a) - Color.green(b)
            val bl = Color.blue(a) - Color.blue(b)
            return r * r + g * g + bl * bl
        }
        return ACCENTS.minByOrNull { (d, l, _) -> minOf(distance(argb, d), distance(argb, l)) }!!.third
    }
}
