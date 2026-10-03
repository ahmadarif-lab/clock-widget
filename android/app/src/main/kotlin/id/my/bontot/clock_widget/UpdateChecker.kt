package id.my.bontot.clock_widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Looks for a newer release on apps.bontot.my.id (`/clock-widget/latest.json`, the same manifest the
 * site publishes for every app) and tells the user: the theme picker shows a banner, and a system
 * notification is posted once per new version, which is how new themes reach people.
 *
 * The manifest is untrusted input: only `https` links to the hosts in [ALLOWED_HOSTS] are ever
 * opened, and nothing is downloaded or installed here - "Download" hands the link to the browser.
 *
 * It is throttled to one request a day (three hours after a failed attempt) and the answer is
 * cached, so opening the app does not need the network. Called from the picker and, in the
 * background, from [ClockTickService].
 */
object UpdateChecker {

    data class Update(val version: String, val notes: List<String>, val downloadUrl: String, val page: String)

    private const val TAG = "UpdateChecker"
    private const val PREFS = "UpdateCheck"
    private const val LATEST_URL = "https://apps.bontot.my.id/clock-widget/latest.json"
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 2
    private const val OK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    private const val FAILED_INTERVAL_MS = 3 * 60 * 60 * 1000L
    private const val SPAWN_INTERVAL_MS = 60 * 60 * 1000L
    private const val MAX_BODY_BYTES = 64 * 1024
    private val ALLOWED_HOSTS = setOf("apps.bontot.my.id", "github.com")

    @Volatile private var lastSpawn = 0L

    /**
     * Checks (if due) and returns the newest release when it is newer than the installed one.
     * [announce] also posts the system notification for a version not yet announced; the picker
     * passes false, since it shows the banner itself.
     */
    @Synchronized
    fun check(context: Context, force: Boolean = false, announce: Boolean = true): Update? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val due = now - prefs.getLong("last_attempt", 0) >= if (prefs.getBoolean("last_ok", false)) OK_INTERVAL_MS else FAILED_INTERVAL_MS
        // with the switch off nothing touches the network unless the user asked ("Check now")
        if (force || (due && isAutoCheck(context))) {
            prefs.edit().putLong("last_attempt", now).apply()
            try {
                val body = fetch(context, prefs)
                parse(context, body, prefs) ?: throw IllegalStateException("manifest without a usable release")
                prefs.edit().putString("latest_json", body).putBoolean("last_ok", true).apply()
            } catch (e: Exception) {
                Log.i(TAG, "update check failed: ${e.message}")
                prefs.edit().putBoolean("last_ok", false).apply()
            }
        }
        val update = cached(context)
        if (update != null && announce) notifyOnce(context, update)
        return update
    }

    /** Posts the notification for the cached release if it was not announced yet (e.g. once the permission is granted). */
    fun announce(context: Context) {
        if (!isAutoCheck(context)) return
        cached(context)?.let { notifyOnce(context, it) }
    }

    /** Runs [check] on a background thread, at most once an hour; for callers that must not block. */
    fun checkInBackground(context: Context) {
        if (!isAutoCheck(context)) return
        val now = System.currentTimeMillis()
        if (now - lastSpawn < SPAWN_INTERVAL_MS) return
        lastSpawn = now
        val app = context.applicationContext
        Thread({ check(app) }, "update-check").start()
    }

    /** The release found by the last successful check, if it is newer than this install. */
    fun cached(context: Context): Update? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val body = prefs.getString("latest_json", null) ?: return null
        val update = try {
            parse(context, body, prefs)
        } catch (e: Exception) {
            null
        } ?: return null
        return if (compareVersions(update.version, installedVersion(context)) > 0) update else null
    }

    /** The "Check for updates" switch in the picker's settings; on by default. */
    fun isAutoCheck(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto_check", true)

    fun setAutoCheck(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("auto_check", enabled).apply()
    }

    fun isDismissed(context: Context, version: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("dismissed", null) == version

    fun dismiss(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("dismissed", version).apply()
    }

    /** True for an https link to one of the hosts we trust (anything is allowed for a debug test URL). */
    fun isAllowedLink(context: Context, url: String): Boolean {
        if (debugOverride(context, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)) != null) return true
        val uri = try { Uri.parse(url) } catch (e: Exception) { return false }
        return uri.scheme == "https" && uri.host in ALLOWED_HOSTS
    }

    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    // ------------------------------------------------------------------ internals

    /** A debug build can be pointed at another manifest (adb + `url_override`), release builds never. */
    private fun debugOverride(context: Context, prefs: android.content.SharedPreferences): String? {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return if (debuggable) prefs.getString("url_override", null) else null
    }

    private fun fetch(context: Context, prefs: android.content.SharedPreferences): String {
        val url = debugOverride(context, prefs) ?: LATEST_URL
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != 200) throw IllegalStateException("HTTP ${connection.responseCode}")
            val out = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_BODY_BYTES) throw IllegalStateException("manifest too large")
                }
            }
            return out.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    /** Reads latest.json; null when it has no version or no link we are willing to open. */
    private fun parse(context: Context, body: String, prefs: android.content.SharedPreferences): Update? {
        val json = JSONObject(body)
        val version = json.getString("version")
        if (!Regex("""\d+(\.\d+)*""").matches(version)) return null

        val notesJson = json.optJSONObject("notes")
        val language = if (Locale.getDefault().language == "id") "id" else "en"
        val array = notesJson?.optJSONArray(language) ?: notesJson?.optJSONArray("en")
        val notes = (0 until (array?.length() ?: 0)).map { plainText(array!!.getString(it)) }.filter { it.isNotBlank() }

        val page = json.optString("page")
        val downloads = json.optJSONObject("downloads")
        val candidates = mutableListOf<String>()
        for (abi in Build.SUPPORTED_ABIS) downloads?.optString(abi)?.takeIf { it.isNotEmpty() }?.let { candidates += it }
        candidates += page
        val link = candidates.firstOrNull { isAllowedLink(context, it) } ?: return null
        return Update(version, notes, link, page)
    }

    /** The notes are HTML on the site (<strong>, <code>, entities); the app shows plain text. */
    private fun plainText(html: String): String =
        html.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
            .trim()

    private fun compareVersions(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }) - (y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** One system notification per version; only counted as sent when it was really allowed to show. */
    private fun notifyOnce(context: Context, update: Update) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("notified", null) == update.version) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val channel = NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tells you when a new version of Clock Widget, with new faces and fixes, is available"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val open = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = update.notes.firstOrNull() ?: "Tap to see what is new"
        val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Clock Widget ${update.version} is available")
            .setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(update.notes.joinToString("\n").ifBlank { text }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
            prefs.edit().putString("notified", update.version).apply()
        } catch (e: SecurityException) {
            Log.i(TAG, "notification not permitted")
        }
    }
}
