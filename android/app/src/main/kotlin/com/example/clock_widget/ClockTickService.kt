package com.example.clock_widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * Keeps the widget ticking on time. A widget has no tick of its own, and AlarmManager alarms reach
 * this app late (the battery manager freezes the background process and defers the broadcast; ~10 s
 * was measured), so while a widget exists this foreground service keeps the process alive and
 * wakes itself on the boundary:
 *  - every second for the flip face, streaming the frames of a 3D flip into the seconds card, plus
 *    the minute and hour cards when they change in the same instant;
 *  - every minute for the other faces, asking the provider for a normal full update.
 *
 * It only works while the screen is on; when it turns off the loop sleeps until it turns on again.
 * It has to be started from the foreground (the theme picker does that), so after a reboot the
 * widget falls back to ClockWidgetProvider's once-a-minute alarm until the app is opened again.
 */
class ClockTickService : Service() {

    @Volatile private var stopped = false
    private var thread: Thread? = null
    private val screenLock = Object()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            synchronized(screenLock) { screenLock.notifyAll() }
            if (intent.action == Intent.ACTION_SCREEN_ON) refreshStill()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (thread == null) {
            running = true
            thread = Thread({ loop() }, "clock-tick").also { it.start() }
        } else {
            synchronized(screenLock) { screenLock.notifyAll() } // the theme may have changed
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        stopped = true
        synchronized(screenLock) { screenLock.notifyAll() }
        thread?.interrupt()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) {
            // already unregistered
        }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Clock widget", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps the clock widget ticking on time"
                setShowBadge(false)
            }
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Clock widget is running")
            .setContentText("Keeps the widget exactly on time")
            .setOngoing(true)
            .build()
    }

    private fun widgetIds(manager: AppWidgetManager): IntArray =
        manager.getAppWidgetIds(ComponentName(this, ClockWidgetProvider::class.java))

    /** Asks the provider for a normal full update (still cards showing the current time). */
    private fun refreshStill() {
        val manager = AppWidgetManager.getInstance(this)
        val ids = widgetIds(manager)
        if (ids.isEmpty()) return
        sendBroadcast(
            Intent(this, ClockWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        )
    }

    private fun loop() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val manager = AppWidgetManager.getInstance(this)
        val renderer = FlipCardRenderer(this)
        try {
            while (!stopped) {
                if (!power.isInteractive) {
                    synchronized(screenLock) { if (!stopped) screenLock.wait(WAIT_SCREEN_MS) }
                    continue
                }
                val flipping = currentTheme() == "flip"
                val period = if (flipping) 1000L else 60_000L
                val now = System.currentTimeMillis()
                val target = now + (period - now % period)
                synchronized(screenLock) { if (!stopped) screenLock.wait(target - now) }
                // woken early (screen change, theme change): look at the situation again
                if (stopped || System.currentTimeMillis() < target - 5 || !power.isInteractive) continue

                val ids = widgetIds(manager)
                if (ids.isEmpty()) {
                    stopSelf()
                    return
                }
                if (flipping) flip(renderer, manager, ids) else refreshStill()
            }
        } catch (e: InterruptedException) {
            // stopped
        }
    }

    private fun currentTheme(): String =
        getSharedPreferences("WidgetTheme", Context.MODE_PRIVATE).getString("theme", "bubble") ?: "bubble"

    /** One second has just rolled over: flip the seconds card (and mm / hh when they changed). */
    private fun flip(renderer: FlipCardRenderer, manager: AppWidgetManager, ids: IntArray) {
        val now = Calendar.getInstance()
        val before = (now.clone() as Calendar).apply { add(Calendar.SECOND, -1) }
        val secondNew = "%02d".format(now.get(Calendar.SECOND))
        val secondOld = "%02d".format(before.get(Calendar.SECOND))
        val minuteNew = "%02d".format(now.get(Calendar.MINUTE))
        val minuteOld = "%02d".format(before.get(Calendar.MINUTE))
        val hourNew = "%02d".format(ClockWidgetProvider.hourOf(this, now))
        val hourOld = "%02d".format(ClockWidgetProvider.hourOf(this, before))

        val start = System.currentTimeMillis()
        while (!stopped) {
            val progress = ((System.currentTimeMillis() - start) / FLIP_MS.toFloat()).coerceAtMost(1f)
            val frame = RemoteViews(packageName, R.layout.widget_theme_flip)
            frame.setImageViewBitmap(R.id.flip_s, renderer.frame(secondOld, secondNew, progress))
            if (minuteOld != minuteNew) frame.setImageViewBitmap(R.id.flip_m, renderer.frame(minuteOld, minuteNew, progress))
            if (hourOld != hourNew) frame.setImageViewBitmap(R.id.flip_h, renderer.frame(hourOld, hourNew, progress))
            try {
                manager.partiallyUpdateAppWidget(ids, frame)
            } catch (e: RuntimeException) {
                Log.w(TAG, "frame dropped", e)
            }
            if (progress >= 1f) break
            Thread.sleep(FRAME_GAP_MS)
        }
    }

    companion object {
        private const val TAG = "ClockTickService"
        private const val CHANNEL_ID = "clock_widget"
        private const val NOTIFICATION_ID = 1
        private const val FLIP_MS = 450L
        private const val FRAME_GAP_MS = 30L
        private const val WAIT_SCREEN_MS = 60_000L

        /** True while the service is alive; the provider then leaves the ticking to it. */
        @Volatile var running = false
            private set

        /**
         * Starts the service (or just re-evaluates the theme if it already runs), or stops it when
         * [theme] is "none". Starting needs the app to be in the foreground (Android 12+), so a
         * refused start is only logged and the widget keeps using the minute alarm.
         */
        fun sync(context: Context, theme: String) {
            val intent = Intent(context, ClockTickService::class.java)
            if (theme != "none") {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    Log.w(TAG, "cannot start from the background; using the minute alarm instead", e)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
