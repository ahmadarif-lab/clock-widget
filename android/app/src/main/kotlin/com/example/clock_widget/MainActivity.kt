package com.example.clock_widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.clock_widget/theme"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
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
}
