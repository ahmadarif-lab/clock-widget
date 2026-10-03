package id.my.bontot.clock_widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws the minimal face (red day/date above a big white time, Bebas Neue) into a transparent
 * Bitmap. Bundled fonts are ignored by some launchers when set on a widget TextView, but they load
 * fine here in the app process.
 */
class MinimalRenderer(context: Context) {

    private val d = context.resources.displayMetrics.density
    private val width = (CANVAS_W * d).toInt()
    private val height = (CANVAS_H * d).toInt()
    private val typeface = ResourcesCompat.getFont(context, R.font.bebas) ?: Typeface.DEFAULT_BOLD

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TIME_DP * d
        typeface = this@MinimalRenderer.typeface
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF0000")
        textSize = DATE_DP * d
        typeface = this@MinimalRenderer.typeface
    }

    fun render(time: String, now: Date): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = width / 2f
        // Bebas digits are ~0.7 em tall, so the ink is centred when the baseline sits 0.35 em below
        val timeBaseline = height / 2f + 0.35f * timePaint.textSize
        canvas.drawText(time, cx - timePaint.measureText(time) / 2f, timeBaseline, timePaint)

        val date = SimpleDateFormat("EEE d", Locale.getDefault()).format(now).uppercase()
        val dateBaseline = height / 2f - 0.35f * timePaint.textSize - 6 * d
        canvas.drawText(date, cx - datePaint.measureText(date) / 2f, dateBaseline, datePaint)
        return bitmap
    }

    private companion object {
        const val CANVAS_W = 300f
        const val CANVAS_H = 170f
        const val TIME_DP = 100f
        const val DATE_DP = 22f
    }
}
