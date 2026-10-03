package id.my.bontot.clock_widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "id.my.bontot.clock_widget/theme"
    private val UPDATE_CHANNEL = "id.my.bontot.clock_widget/update"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        configureUpdateChannel(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "setTheme") {
                val theme = call.argument<String>("theme")
                if (theme != null) {
                    // Save to SharedPreferences
                    val prefs = getSharedPreferences("WidgetTheme", Context.MODE_PRIVATE)
                    prefs.edit().putString("theme", theme).apply()
                    ClockTickService.sync(this, theme)

                    // Trigger widget update
                    val intent = Intent(this, ClockWidgetProvider::class.java)
                    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(
                        ComponentName(application, ClockWidgetProvider::class.java)
                    )
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    sendBroadcast(intent)

                    result.success(true)
                } else {
                    result.error("UNAVAILABLE", "Theme not provided.", null)
                }
            } else if (call.method == "getTheme") {
                val prefs = getSharedPreferences("WidgetTheme", Context.MODE_PRIVATE)
                result.success(prefs.getString("theme", "bubble"))
            } else {
                result.notImplemented()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // A widget added from the launcher cannot start the foreground service (the provider runs in
        // the background); opening the app can, so a freshly added widget starts ticking on time.
        val ids = AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, ClockWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            val theme = getSharedPreferences("WidgetTheme", Context.MODE_PRIVATE).getString("theme", "bubble") ?: "bubble"
            ClockTickService.sync(this, theme)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // the notification permission was just answered: announce a release that was found before it
        Thread { UpdateChecker.announce(applicationContext) }.start()
    }

    /** Update check for the picker: see [UpdateChecker]. Everything that needs the network runs off the main thread. */
    private fun configureUpdateChannel(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, UPDATE_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "check" -> {
                    val force = call.argument<Boolean>("force") == true
                    Thread {
                        val update = UpdateChecker.check(applicationContext, force, announce = false)
                        val reply = update?.let {
                            mapOf(
                                "version" to it.version,
                                "notes" to it.notes,
                                "downloadUrl" to it.downloadUrl,
                                "page" to it.page,
                                "dismissed" to UpdateChecker.isDismissed(applicationContext, it.version),
                            )
                        }
                        runOnUiThread { result.success(reply) }
                    }.start()
                }
                "dismiss" -> {
                    call.argument<String>("version")?.let { UpdateChecker.dismiss(this, it) }
                    result.success(null)
                }
                "open" -> {
                    val url = call.argument<String>("url")
                    if (url != null && UpdateChecker.isAllowedLink(this, url)) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                }
                "installedVersion" -> result.success(UpdateChecker.installedVersion(this))
                "getAutoCheck" -> result.success(UpdateChecker.isAutoCheck(this))
                "setAutoCheck" -> {
                    UpdateChecker.setAutoCheck(this, call.argument<Boolean>("enabled") != false)
                    result.success(null)
                }
                "requestNotifications" -> {
                    // Android 13+ needs a runtime grant before the update notification can show; ask once.
                    val prefs = getSharedPreferences("UpdateCheck", Context.MODE_PRIVATE)
                    if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                        !prefs.getBoolean("asked_notifications", false)
                    ) {
                        prefs.edit().putBoolean("asked_notifications", true).apply()
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
                    }
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }
}
