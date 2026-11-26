package com.example.petvetericano.ui.reporte

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomBoundsCalculatorTest {

    @Test
    fun computeBaseScale_mantieneProporcionesSinRecortarImagenHorizontal() {
        // Imagen 1600x900 en pantalla 1080x2200 -> debe ajustarse al ancho (1080/1600 = 0.675)
        val scale = ZoomBoundsCalculator.computeBaseScale(
            drawableWidth = 1600f,
            drawableHeight = 900f,
            viewWidth = 1080f,
            viewHeight = 2200f
        )
        assertEquals(0.675f, scale, 0.0001f)
    }

    @Test
    fun computeBaseScale_mantieneProporcionesSinRecortarImagenVertical() {
        // Imagen 900x2000 en pantalla 1080x1600 -> debe ajustarse al alto (1600/2000 = 0.8)
        val scale = ZoomBoundsCalculator.computeBaseScale(
            drawableWidth = 900f,
            drawableHeight = 2000f,
            viewWidth = 1080f,
            viewHeight = 1600f
        )
        assertEquals(0.8f, scale, 0.0001f)
    }

    @Test
    fun computeClampedAxisDelta_centraContenidoMenorQueVista() {
        val delta = ZoomBoundsCalculator.computeClampedAxisDelta(
            contentSize = 600f,
            viewSize = 1000f,
            currentTrans = 50f
        )
        // Destino centrado: (1000 - 600) / 2 = 200 -> delta = 200 - 50 = 150
        assertEquals(150f, delta, 0.0001f)
    }

    @Test
    fun computeClampedAxisDelta_acotaDesplazamientoCuandoImagenSuperaVista() {
        // content=2000, view=1000 -> rango permitido [-1000, 0]
        val deltaBordeInicio = ZoomBoundsCalculator.computeClampedAxisDelta(
            contentSize = 2000f,
            viewSize = 1000f,
            currentTrans = 120f
        )
        assertEquals(-120f, deltaBordeInicio, 0.0001f)

        val deltaBordeFin = ZoomBoundsCalculator.computeClampedAxisDelta(
            contentSize = 2000f,
            viewSize = 1000f,
            currentTrans = -1250f
        )
        assertEquals(250f, deltaBordeFin, 0.0001f)

        val deltaDentroDeRango = ZoomBoundsCalculator.computeClampedAxisDelta(
            contentSize = 2000f,
            viewSize = 1000f,
            currentTrans = -400f
        )
        assertEquals(0f, deltaDentroDeRango, 0.0001f)
    }
}
