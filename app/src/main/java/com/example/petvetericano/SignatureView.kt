package com.example.petvetericano

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import java.io.ByteArrayOutputStream

/**
 * Vista de firma digital táctil nativa para capturar firmas de funcionarios y ciudadanos.
 * Permite dibujar trazos fluidos y exportar el resultado a Base64 para el backend de Django.
 */
class SignatureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val path = Path()
    private val paint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#0F172A") // Slate 900
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 5.5f
    }

    private var hasSignature = false
    private var restoredBitmap: Bitmap? = null
    private var hasDrawnNewStrokes = false
    private var originalDataUri: String? = null

    /**
     * Callback para notificar cambios en el estado de la firma (nuevo trazo, borrado o restauración).
     */
    var onSignatureChangeListener: (() -> Unit)? = null

    /**
     * Determina si la aplicación se encuentra en modo oscuro (por configuración del sistema o ThemePrefs).
     */
    fun isDarkMode(): Boolean {
        val uiModeNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val prefsNight = try {
            context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE).getBoolean("isDarkMode", false)
        } catch (_: Exception) {
            false
        }
        return uiModeNight || prefsNight
    }

    /**
     * Color dinámico para la firma:
     * - Modo oscuro: Blanco (#FFFFFF) para contraste sobre fondo oscuro.
     * - Modo blanco: Negro (#0F172A) para contraste sobre fondo blanco.
     */
    fun getStrokeColor(): Int {
        return if (isDarkMode()) Color.WHITE else Color.parseColor("#0F172A")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val darkMode = isDarkMode()

        restoredBitmap?.let { bitmap ->
            if (!bitmap.isRecycled && width > 0 && height > 0) {
                val destRect = Rect(0, 0, width, height)
                if (darkMode) {
                    // Mapeo adaptativo: fondo blanco se adapta a input_background (#162032) y trazo a blanco
                    val rScale = (22f - 255f) / 255f
                    val gScale = (32f - 255f) / 255f
                    val bScale = (50f - 255f) / 255f
                    val darkFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                        rScale, 0f,     0f,     0f, 255f,
                        0f,     gScale, 0f,     0f, 255f,
                        0f,     0f,     bScale, 0f, 255f,
                        0f,     0f,     0f,     1f, 0f
                    )))
                    val filterPaint = Paint().apply { colorFilter = darkFilter }
                    canvas.drawBitmap(bitmap, null, destRect, filterPaint)
                } else {
                    canvas.drawBitmap(bitmap, null, destRect, null)
                }
            }
        }

        // Color del trazo dinámico según el modo de tema
        paint.color = getStrokeColor()
        canvas.drawPath(path, paint)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        paint.color = getStrokeColor()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Evitar que el NestedScrollView intercepte el toque mientras se firma
        parent?.requestDisallowInterceptTouchEvent(true)

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                hasSignature = true
                hasDrawnNewStrokes = true
                invalidate()
                onSignatureChangeListener?.invoke()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Restaura una firma previa a partir de una cadena data URI en Base64
     * con formato "data:image/png;base64,..."
     */
    fun restoreFromBase64(dataUri: String?) {
        if (dataUri.isNullOrBlank()) {
            clear()
            return
        }
        try {
            val base64Clean = if (dataUri.contains(",")) {
                dataUri.substringAfter(",")
            } else {
                dataUri
            }
            val bytes = Base64.decode(base64Clean, Base64.DEFAULT)
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (decoded != null) {
                restoredBitmap?.recycle()
                restoredBitmap = decoded
                originalDataUri = if (dataUri.startsWith("data:image/")) dataUri else "data:image/png;base64,$dataUri"
                hasSignature = true
                hasDrawnNewStrokes = false
                path.reset()
                invalidate()
                onSignatureChangeListener?.invoke()
            }
        } catch (e: Exception) {
            Log.e("SignatureView", "Error restaurando firma Base64", e)
        }
    }

    /**
     * Limpia el trazo de la firma y el bitmap restaurado
     */
    fun clear() {
        path.reset()
        restoredBitmap?.recycle()
        restoredBitmap = null
        originalDataUri = null
        hasSignature = false
        hasDrawnNewStrokes = false
        invalidate()
        onSignatureChangeListener?.invoke()
    }

    /**
     * Indica si se ha realizado al menos un trazo o restaurado una firma válida
     */
    fun isSigned(): Boolean = hasSignature

    /**
     * Alias de isSigned para compatibilidad de nomenclatura
     */
    fun hasSignature(): Boolean = hasSignature

    /**
     * Convierte la firma dibujada en una cadena Base64 PNG compatible con Django TextField
     */
    fun toBase64(): String? {
        if (!hasSignature) return null

        // Si la firma proviene de una restauración y el usuario no realizó nuevos trazos,
        // devolvemos la cadena original garantizando fidelidad e independencia del ciclo de vida de la vista.
        if (!hasDrawnNewStrokes && !originalDataUri.isNullOrEmpty()) {
            return originalDataUri
        }

        val targetWidth = if (width > 0) width else restoredBitmap?.width ?: 0
        val targetHeight = if (height > 0) height else restoredBitmap?.height ?: 0
        if (targetWidth <= 0 || targetHeight <= 0) return null

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        restoredBitmap?.let { rBitmap ->
            if (!rBitmap.isRecycled) {
                val destRect = Rect(0, 0, targetWidth, targetHeight)
                canvas.drawBitmap(rBitmap, null, destRect, null)
            }
        }

        // Para actas y documentos oficiales, exportar siempre con trazo negro sobre fondo blanco
        val exportPaint = Paint(paint).apply {
            color = Color.parseColor("#0F172A")
        }
        canvas.drawPath(path, exportPaint)

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return "data:image/png;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
