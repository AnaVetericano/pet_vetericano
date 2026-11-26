package com.example.petvetericano.ui.reporte

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * Calculos puros para ajustar proporcionalmente la imagen a la vista y acotar el desplazamiento.
 */
internal object ZoomBoundsCalculator {

    fun computeBaseScale(
        drawableWidth: Float,
        drawableHeight: Float,
        viewWidth: Float,
        viewHeight: Float
    ): Float {
        if (drawableWidth <= 0f || drawableHeight <= 0f || viewWidth <= 0f || viewHeight <= 0f) {
            return 1f
        }
        return min(viewWidth / drawableWidth, viewHeight / drawableHeight)
    }

    fun computeClampedAxisDelta(
        contentSize: Float,
        viewSize: Float,
        currentTrans: Float,
        viewStart: Float = 0f
    ): Float {
        if (contentSize <= 0f || viewSize <= 0f) return 0f
        return if (contentSize <= viewSize) {
            val targetTrans = viewStart + (viewSize - contentSize) / 2f
            targetTrans - currentTrans
        } else {
            val minTrans = viewStart + viewSize - contentSize
            val maxTrans = viewStart
            when {
                currentTrans > maxTrans -> maxTrans - currentTrans
                currentTrans < minTrans -> minTrans - currentTrans
                else -> 0f
            }
        }
    }
}

/**
 * ImageView con ajuste inicial proporcional (fitCenter), zoom con dos dedos (pinch-to-zoom),
 * doble toque y desplazamiento acotado a los bordes visibles de la imagen.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val drawMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val centroidScratch = FloatArray(2)

    private var baseScale = 1f
    private var currentZoom = MIN_ZOOM

    private var lastCentroidX = 0f
    private var lastCentroidY = 0f
    private var lastPointerCount = 0
    private var hasDraggedOrScaled = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                hasDraggedOrScaled = true
                return drawable != null
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val rawFactor = detector.scaleFactor
                if (!rawFactor.isFinite() || rawFactor <= 0f || baseScale <= 0f) return false

                val targetZoom = (currentZoom * rawFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                val appliedFactor = targetZoom / currentZoom
                currentZoom = targetZoom

                if (currentZoom <= MIN_ZOOM + ZOOM_EPSILON) {
                    configureBaseMatrix()
                } else {
                    drawMatrix.postScale(appliedFactor, appliedFactor, detector.focusX, detector.focusY)
                    fixTranslation()
                    imageMatrix = drawMatrix
                }
                return true
            }
        }
    ).apply {
        isQuickScaleEnabled = false
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (drawable == null || baseScale <= 0f) return false
                if (currentZoom > MIN_ZOOM + DOUBLE_TAP_RESET_THRESHOLD) {
                    configureBaseMatrix()
                } else {
                    val appliedFactor = DOUBLE_TAP_ZOOM / currentZoom
                    currentZoom = DOUBLE_TAP_ZOOM
                    drawMatrix.postScale(appliedFactor, appliedFactor, e.x, e.y)
                    fixTranslation()
                    imageMatrix = drawMatrix
                }
                hasDraggedOrScaled = true
                return true
            }
        }
    )

    init {
        super.setScaleType(ScaleType.MATRIX)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        configureBaseMatrix()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        configureBaseMatrix()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        configureBaseMatrix()
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        configureBaseMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            configureBaseMatrix()
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawable == null) return super.onTouchEvent(event)

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastCentroidX = event.x
                lastCentroidY = event.y
                lastPointerCount = 1
                hasDraggedOrScaled = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (computeCentroid(event, ignoreIndex = -1, out = centroidScratch)) {
                    lastCentroidX = centroidScratch[0]
                    lastCentroidY = centroidScratch[1]
                }
                lastPointerCount = event.pointerCount
                hasDraggedOrScaled = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (computeCentroid(event, ignoreIndex = -1, out = centroidScratch)) {
                    val cx = centroidScratch[0]
                    val cy = centroidScratch[1]
                    if (event.pointerCount == lastPointerCount && currentZoom > MIN_ZOOM + ZOOM_EPSILON) {
                        val dx = cx - lastCentroidX
                        val dy = cy - lastCentroidY
                        if (dx != 0f || dy != 0f) {
                            drawMatrix.postTranslate(dx, dy)
                            fixTranslation()
                            imageMatrix = drawMatrix
                            hasDraggedOrScaled = true
                        }
                    }
                    lastCentroidX = cx
                    lastCentroidY = cy
                    lastPointerCount = event.pointerCount
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (computeCentroid(event, ignoreIndex = event.actionIndex, out = centroidScratch)) {
                    lastCentroidX = centroidScratch[0]
                    lastCentroidY = centroidScratch[1]
                }
                lastPointerCount = (event.pointerCount - 1).coerceAtLeast(0)
            }

            MotionEvent.ACTION_UP -> {
                if (!hasDraggedOrScaled) {
                    performClick()
                }
                lastPointerCount = 0
            }

            MotionEvent.ACTION_CANCEL -> {
                lastPointerCount = 0
            }
        }

        parent?.requestDisallowInterceptTouchEvent(
            currentZoom > MIN_ZOOM + ZOOM_EPSILON || event.pointerCount >= 2 || scaleDetector.isInProgress
        )
        return true
    }

    private fun configureBaseMatrix() {
        val d = drawable ?: return
        val dWidth = d.intrinsicWidth.toFloat()
        val dHeight = d.intrinsicHeight.toFloat()
        val vWidth = (width - paddingLeft - paddingRight).toFloat()
        val vHeight = (height - paddingTop - paddingBottom).toFloat()
        if (dWidth <= 0f || dHeight <= 0f || vWidth <= 0f || vHeight <= 0f) return

        baseScale = ZoomBoundsCalculator.computeBaseScale(dWidth, dHeight, vWidth, vHeight)
        currentZoom = MIN_ZOOM

        drawMatrix.reset()
        drawMatrix.postScale(baseScale, baseScale)
        val offsetX = paddingLeft + (vWidth - dWidth * baseScale) / 2f
        val offsetY = paddingTop + (vHeight - dHeight * baseScale) / 2f
        drawMatrix.postTranslate(offsetX, offsetY)
        imageMatrix = drawMatrix
    }

    private fun fixTranslation() {
        val d = drawable ?: return
        val dWidth = d.intrinsicWidth.toFloat()
        val dHeight = d.intrinsicHeight.toFloat()
        val vWidth = (width - paddingLeft - paddingRight).toFloat()
        val vHeight = (height - paddingTop - paddingBottom).toFloat()
        if (dWidth <= 0f || dHeight <= 0f || vWidth <= 0f || vHeight <= 0f) return

        drawMatrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]
        val contentWidth = dWidth * matrixValues[Matrix.MSCALE_X]
        val contentHeight = dHeight * matrixValues[Matrix.MSCALE_Y]

        val deltaX = ZoomBoundsCalculator.computeClampedAxisDelta(
            contentSize = contentWidth,
            viewSize = vWidth,
            currentTrans = transX,
            viewStart = paddingLeft.toFloat()
        )
        val deltaY = ZoomBoundsCalculator.computeClampedAxisDelta(
            contentSize = contentHeight,
            viewSize = vHeight,
            currentTrans = transY,
            viewStart = paddingTop.toFloat()
        )

        if (deltaX != 0f || deltaY != 0f) {
            drawMatrix.postTranslate(deltaX, deltaY)
        }
    }

    private fun computeCentroid(event: MotionEvent, ignoreIndex: Int, out: FloatArray): Boolean {
        var sumX = 0f
        var sumY = 0f
        var count = 0
        for (i in 0 until event.pointerCount) {
            if (i == ignoreIndex) continue
            sumX += event.getX(i)
            sumY += event.getY(i)
            count++
        }
        if (count == 0) return false
        out[0] = sumX / count
        out[1] = sumY / count
        return true
    }

    companion object {
        private const val MIN_ZOOM = 1.0f
        private const val MAX_ZOOM = 5.0f
        private const val DOUBLE_TAP_ZOOM = 2.5f
        private const val ZOOM_EPSILON = 0.001f
        private const val DOUBLE_TAP_RESET_THRESHOLD = 0.05f
    }
}
