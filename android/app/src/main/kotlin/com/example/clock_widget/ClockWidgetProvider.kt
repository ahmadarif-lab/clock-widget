package com.example.clock_widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Renders the clock widget for the currently selected theme.
 *
 * Bundled fonts are ignored inside RemoteViews on some launchers (the text silently falls back to
 * the system font), so the bubble, LED and flip faces draw their digits as vector drawables
 * generated from the real fonts (tools/gen_glyphs.py) and pick the glyph with setImageLevel. That
 * also gives the bubble face its per-digit colours. These faces re-render once a minute through an
 * AlarmManager alarm; the flip face's seconds are a ViewFlipper the host advances by itself.
 * Only the minimal face still uses a self-updating [android.widget.TextClock].
 */
class ClockWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TICK = "com.example.clock_widget.ACTION_TICK"
        private const val PREFS = "WidgetTheme"
        private const val REQUEST_TICK = 1001
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val theme = currentTheme(context)
        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, theme))
        }
        if (needsTick(theme)) scheduleTick(context) else cancelTick(context)
    }

    override fun onEnabled(context: Context) {
        if (needsTick(currentTheme(context))) scheduleTick(context)
    }

    override fun onDisabled(context: Context) {
        cancelTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
            val theme = currentTheme(context)
            for (appWidgetId in ids) {
                manager.updateAppWidget(appWidgetId, buildViews(context, theme))
            }
            if (needsTick(theme)) scheduleTick(context)
        }
    }

    // ---------------------------------------------------------------- rendering

    private fun currentTheme(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("theme", "bubble") ?: "bubble"

    /** Faces whose digits are bound by hand and so must be re-rendered every minute. */
    private fun needsTick(theme: String): Boolean = theme == "bubble" || theme == "digital" || theme == "flip"

    private fun layoutFor(theme: String): Int = when (theme) {
        "digital" -> R.layout.widget_theme_digital
        "minimal" -> R.layout.widget_theme_minimal
        "flip" -> R.layout.widget_theme_flip
        else -> R.layout.widget_theme_bubble
    }

    private fun buildViews(context: Context, theme: String): RemoteViews {
        val views = RemoteViews(context.packageName, layoutFor(theme))

        if (theme == "bubble") {
            bindPastelDigits(context, views)
        } else if (theme == "digital") {
            bindLedDigits(context, views)
        } else if (theme == "flip") {
            bindFlipDigits(context, views)
        }

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launch != null) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pending = PendingIntent.getActivity(context, 0, launch, flags)
            if (theme == "minimal") {
                views.setOnClickPendingIntent(R.id.clock_time, pending)
            } else {
                views.setOnClickPendingIntent(R.id.widget_root, pending)
            }
        }
        return views
    }

    /**
     * Pastel face: four fixed digit slots, each with its own colour (set in the layout XML).
     * #FFCDD2 hours, #F8BBD0 second hour digit, #E1BEE7 minutes, #FFF59D second minute digit.
     */
    private fun bindPastelDigits(context: Context, views: RemoteViews) {
        val now = Calendar.getInstance()
        val hour = hourOf(context, now)
        val minute = now.get(Calendar.MINUTE)

        views.setViewVisibility(R.id.clock_h0, View.VISIBLE)
        if (hour < 10) {
            // single-digit hour keeps the first slot (and therefore the first colour)
            views.setViewVisibility(R.id.clock_h1, View.GONE)
            views.setInt(R.id.clock_h0, "setImageLevel", hour)
        } else {
            views.setViewVisibility(R.id.clock_h1, View.VISIBLE)
            views.setInt(R.id.clock_h0, "setImageLevel", hour / 10)
            views.setInt(R.id.clock_h1, "setImageLevel", hour % 10)
        }
        views.setInt(R.id.clock_m0, "setImageLevel", minute / 10)
        views.setInt(R.id.clock_m1, "setImageLevel", minute % 10)
    }

    /** Hour as shown on the face: 0-23, or 1-12 when the system uses 12-hour time. */
    private fun hourOf(context: Context, now: Calendar): Int {
        if (DateFormat.is24HourFormat(context)) return now.get(Calendar.HOUR_OF_DAY)
        val h = now.get(Calendar.HOUR)
        return if (h == 0) 12 else h
    }

    /** Flip face: hh / mm cards by level, and a 60-child ViewFlipper for the seconds card. */
    private fun bindFlipDigits(context: Context, views: RemoteViews) {
        val now = Calendar.getInstance()
        val hour = hourOf(context, now)
        val minute = now.get(Calendar.MINUTE)
        views.setInt(R.id.flip_h0, "setImageLevel", hour / 10)
        views.setInt(R.id.flip_h1, "setImageLevel", hour % 10)
        views.setInt(R.id.flip_m0, "setImageLevel", minute / 10)
        views.setInt(R.id.flip_m1, "setImageLevel", minute % 10)

        views.removeAllViews(R.id.flip_seconds)
        for (second in 0..59) {
            val item = RemoteViews(context.packageName, R.layout.flip_second_item)
            item.setInt(R.id.flip_s0, "setImageLevel", second / 10)
            item.setInt(R.id.flip_s1, "setImageLevel", second % 10)
            views.addView(R.id.flip_seconds, item)
        }
        views.setDisplayedChild(R.id.flip_seconds, now.get(Calendar.SECOND))
    }

    /**
     * LED face: four digit slots (each a level-list of the ten 7-segment drawables), plus the
     * battery percentage text and the battery icon fill.
     */
    private fun bindLedDigits(context: Context, views: RemoteViews) {
        val now = Calendar.getInstance()
        val hour = hourOf(context, now)
        val minute = now.get(Calendar.MINUTE)
        views.setInt(R.id.led_h0, "setImageLevel", hour / 10)
        views.setInt(R.id.led_h1, "setImageLevel", hour % 10)
        views.setInt(R.id.led_m0, "setImageLevel", minute / 10)
        views.setInt(R.id.led_m1, "setImageLevel", minute % 10)

        // AM / PM (hidden in 24-hour mode)
        if (DateFormat.is24HourFormat(context)) {
            views.setViewVisibility(R.id.led_ampm, View.INVISIBLE)
        } else {
            views.setViewVisibility(R.id.led_ampm, View.VISIBLE)
            views.setImageViewResource(R.id.led_ampm, if (now.get(Calendar.AM_PM) == Calendar.AM) R.drawable.seg_am else R.drawable.seg_pm)
        }

        val day = now.get(Calendar.DAY_OF_MONTH)
        val month = now.get(Calendar.MONTH) + 1
        views.setInt(R.id.led_day0, "setImageLevel", day / 10)
        views.setInt(R.id.led_day1, "setImageLevel", day % 10)
        views.setInt(R.id.led_mon0, "setImageLevel", month / 10)
        views.setInt(R.id.led_mon1, "setImageLevel", month % 10)

        // battery: icon fill + up to three digits (leading slots hidden)
        val battery = batteryPercent(context)
        views.setInt(R.id.led_battery, "setImageLevel", battery.coerceAtLeast(0) * 100)
        val ids = intArrayOf(R.id.led_pct0, R.id.led_pct1, R.id.led_pct2)
        val digits = if (battery < 0) "" else battery.toString()
        for (i in ids.indices) {
            val d = digits.getOrNull(i - (ids.size - digits.length))
            if (d == null) {
                views.setViewVisibility(ids[i], View.GONE)
            } else {
                views.setViewVisibility(ids[i], View.VISIBLE)
                views.setInt(ids[i], "setImageLevel", d - '0')
            }
        }
    }

    /** Battery level 0-100, or -1 when it cannot be read. */
    private fun batteryPercent(context: Context): Int {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return -1
        return level * 100 / scale
    }

    // ------------------------------------------------------------------ ticking

    private fun tickIntent(context: Context): PendingIntent {
        val intent = Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TICK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_TICK, intent, flags)
    }

    /** Fire at the next minute boundary and reschedule after every tick. */
    private fun scheduleTick(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = tickIntent(context)
        val now = System.currentTimeMillis()
        val next = now + (60_000L - now % 60_000L) + 250L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pending)
            } else {
                alarmManager.setExact(AlarmManager.RTC, next, pending)
            }
        } catch (e: SecurityException) {
            // Exact alarms not permitted (Android 12+ without SCHEDULE_EXACT_ALARM): fall back.
            alarmManager.set(AlarmManager.RTC, next, pending)
        }
    }

    private fun cancelTick(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(tickIntent(context))
    }
}
