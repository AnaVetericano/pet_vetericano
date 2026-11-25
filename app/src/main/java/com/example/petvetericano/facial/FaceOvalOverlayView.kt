package com.example.petvetericano.facial

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.petvetericano.R
import kotlin.math.min

/**
 * Superposición visual con recorte ovalado centrado para guiar al usuario durante
 * el escaneo facial con CameraX.
 */
class FaceOvalOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class EstadoMarco {
        NEUTRO,
        ACTIVO,
        EXITO,
        ALERTA
    }

    private val scrimPath = Path()
    private val ovalRect = RectF()
    private val progressRect = RectF()

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 11, 15, 29)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3f)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.blue)
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(5.5f)
        strokeCap = Paint.Cap.ROUND
    }

    private var estadoActual: EstadoMarco = EstadoMarco.NEUTRO
    private var progresoActual: Float = 0f

    fun actualizarEstado(estado: EstadoMarco, progreso: Float) {
        estadoActual = estado
        progresoActual = progreso.coerceIn(0f, 1f)
        borderPaint.color = when (estado) {
            EstadoMarco.NEUTRO -> Color.argb(210, 255, 255, 255)
            EstadoMarco.ACTIVO -> ContextCompat.getColor(context, R.color.blue)
            EstadoMarco.EXITO -> ContextCompat.getColor(context, R.color.success)
            EstadoMarco.ALERTA -> ContextCompat.getColor(context, R.color.error)
        }
        progressPaint.color = when (estado) {
            EstadoMarco.EXITO -> ContextCompat.getColor(context, R.color.success)
            EstadoMarco.ALERTA -> ContextCompat.getColor(context, R.color.error)
            else -> ContextCompat.getColor(context, R.color.blue)
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val ovalWidth = min(w * 0.72f, h * 0.44f)
        val ovalHeight = ovalWidth * 1.32f
        val cx = w / 2f
        val cy = h * 0.43f

        ovalRect.set(
            cx - ovalWidth / 2f,
            cy - ovalHeight / 2f,
            cx + ovalWidth / 2f,
            cy + ovalHeight / 2f
        )

        val inset = dpToPx(6f)
        progressRect.set(
            ovalRect.left - inset,
            ovalRect.top - inset,
            ovalRect.right + inset,
            ovalRect.bottom + inset
        )

        scrimPath.reset()
        scrimPath.fillType = Path.FillType.EVEN_ODD
        scrimPath.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        scrimPath.addOval(ovalRect, Path.Direction.CCW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (ovalRect.isEmpty) return

        canvas.drawPath(scrimPath, scrimPaint)
        canvas.drawOval(ovalRect, borderPaint)

        if (progresoActual > 0.01f) {
            canvas.drawArc(progressRect, -90f, 360f * progresoActual, false, progressPaint)
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
