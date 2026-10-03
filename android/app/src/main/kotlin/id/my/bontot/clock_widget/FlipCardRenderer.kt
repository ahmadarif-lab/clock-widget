package id.my.bontot.clock_widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

/**
 * Draws one flip-clock card (64 x 80 dp: warm white body, 1 dp shadow, dark seam) and its
 * flip animation into a Bitmap.
 *
 * RemoteViews cannot animate a 3D flip, but this runs in the app process, where the bundled font
 * does load, so the widget is fed one bitmap per animation frame instead. A flip is a single flap
 * hinged on the centre seam: its front is the old top half, its back is the new bottom half, and
 * it turns through 180 degrees around the horizontal axis.
 */
class FlipCardRenderer(context: Context) {

    private val d = context.resources.displayMetrics.density
    val width = (CARD_W * d).toInt()
    val height = (CARD_H * d).toInt()

    // the card body leaves a 1 dp strip at the bottom for the shadow
    private val bodyBottom = height - d
    private val centerX = width / 2f
    private val centerY = bodyBottom / 2f
    private val radius = 5 * d

    private val bodyPath = Path().apply { addRoundRect(RectF(0f, 0f, width.toFloat(), bodyBottom), radius, radius, Path.Direction.CW) }
    private val shadowPath = Path().apply { addRoundRect(RectF(0f, d, width.toFloat(), height.toFloat()), radius, radius, Path.Direction.CW) }
    private val topHalf = RectF(0f, 0f, width.toFloat(), centerY)
    private val bottomHalf = RectF(0f, centerY, width.toFloat(), bodyBottom)

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FBF8F3") }
    private val shadePaint = Paint().apply { color = Color.BLACK }
    private val seamPaint = Paint().apply { color = Color.parseColor("#2D2125") }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = TEXT_DP * d
        typeface = ResourcesCompat.getFont(context, R.font.flip) ?: Typeface.DEFAULT_BOLD
    }

    private val camera = Camera().apply { setLocation(0f, 0f, -CAMERA_Z) }
    private val matrix = Matrix()

    /** The card showing [text] with no animation. */
    fun still(text: String): Bitmap = frame(text, text, 1f)

    /** The card [progress] (0..1) of the way through flipping from [old] to [new]. */
    fun frame(old: String, new: String, progress: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPath(shadowPath, shadowPaint)

        if (progress >= 1f || old == new) {
            drawFace(canvas, new, null)
            canvas.drawRect(0f, centerY - d / 2, width.toFloat(), centerY + d / 2, seamPaint)
            return bitmap
        }

        // underneath: new top half and old bottom half
        drawFace(canvas, new, topHalf)
        drawFace(canvas, old, bottomHalf)

        // the flap: easeInOut over 0..180 degrees
        val eased = if (progress < 0.5f) 2f * progress * progress else 1f - 2f * (1f - progress) * (1f - progress)
        val angle = eased * 180f
        canvas.save()
        canvas.clipPath(bodyPath)
        if (angle < 90f) {
            // front of the flap: old top half folding forward and down
            applyFlap(canvas, -angle)
            drawFace(canvas, old, topHalf)
            shade(canvas, topHalf, angle / 90f)
        } else {
            // back of the flap: new bottom half, opening from the seam
            applyFlap(canvas, 180f - angle)
            drawFace(canvas, new, bottomHalf)
            shade(canvas, bottomHalf, (180f - angle) / 90f)
        }
        canvas.restore()

        canvas.drawRect(0f, centerY - d / 2, width.toFloat(), centerY + d / 2, seamPaint)
        return bitmap
    }

    /** Rotates the canvas [degrees] around the horizontal axis through the centre seam. */
    private fun applyFlap(canvas: Canvas, degrees: Float) {
        camera.save()
        camera.rotateX(degrees)
        camera.getMatrix(matrix)
        camera.restore()
        matrix.preTranslate(-centerX, -centerY)
        matrix.postTranslate(centerX, centerY)
        canvas.concat(matrix)
    }

    private fun shade(canvas: Canvas, half: RectF, amount: Float) {
        shadePaint.alpha = (amount * MAX_SHADE * 255).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.clipPath(bodyPath)
        canvas.drawRect(half, shadePaint)
        canvas.restore()
    }

    /** The card face with [text]; [half] restricts it to the top or bottom half. */
    private fun drawFace(canvas: Canvas, text: String, half: RectF?) {
        canvas.save()
        canvas.clipPath(bodyPath)
        if (half != null) canvas.clipRect(half)
        canvas.drawPath(bodyPath, cardPaint)
        // League Gothic digits span baseline - 0.743 em .. baseline + 0.008 em, so the ink is
        // centred on the card when the baseline sits 0.3675 em below the centre
        val baseline = centerY + 0.3675f * textPaint.textSize
        canvas.drawText(text, centerX - textPaint.measureText(text) / 2f, baseline, textPaint)
        canvas.restore()
    }

    private companion object {
        const val CARD_W = 64
        const val CARD_H = 80
        const val TEXT_DP = 68f
        const val CAMERA_Z = 12f
        const val MAX_SHADE = 0.3f
    }
}
