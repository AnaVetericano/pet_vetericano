package com.example.petvetericano.ui.reporte

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.example.petvetericano.R
import com.example.petvetericano.databinding.DialogPosibleDuplicadoCiudadanoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialogo preventivo Material 3 que informa al ciudadano sobre un posible reporte previo
 * cercano y visualmente similar, mostrando su fotografia cuando el backend la devuelve.
 */
object DialogoDuplicadoCiudadanoHelper {

    fun mostrar(
        context: Context,
        alerta: AlertaDuplicadoUiModel,
        onCancelarNuevoEnvio: () -> Unit,
        onContinuarEnvio: () -> Unit
    ): AlertDialog {
        val binding = DialogPosibleDuplicadoCiudadanoBinding.inflate(LayoutInflater.from(context))

        val resumen = alerta.mensajeResumen
        if (!resumen.isNullOrBlank()) {
            binding.tvResumenDuplicado.text = resumen
        }

        // Cargar fotografia del reporte previo automaticamente si el backend la devuelve
        val urlSegura = alerta.fotoAutorizadaUrl
        if (!urlSegura.isNullOrBlank()) {
            binding.cardFotoAutorizada.visibility = View.VISIBLE
            binding.layoutFotoPrivada.visibility = View.GONE
            Glide.with(context)
                .load(urlSegura)
                .centerCrop()
                .placeholder(R.color.chip_bg_neutral)
                .error(R.color.chip_bg_neutral)
                .into(binding.ivFotoAutorizada)
        } else {
            binding.cardFotoAutorizada.visibility = View.GONE
            binding.layoutFotoPrivada.visibility = View.VISIBLE
        }

        val porcentajeTexto = DuplicadoCiudadanoPolicy.formatearPorcentajeVisual(alerta.porcentajeSimilitudVisual)
        when {
            porcentajeTexto != null -> {
                binding.tvSimilitudVisual.visibility = View.VISIBLE
                binding.tvSimilitudVisual.text = context.getString(
                    R.string.duplicado_ciudadano_similitud_visual,
                    porcentajeTexto
                )
            }
            !alerta.similitudVisualLabel.isNullOrBlank() && alerta.similitudVisualLabel != "no_disponible" -> {
                binding.tvSimilitudVisual.visibility = View.VISIBLE
                binding.tvSimilitudVisual.text = context.getString(
                    R.string.duplicado_ciudadano_similitud_visual_sin_porcentaje,
                    alerta.similitudVisualLabel
                )
            }
            else -> {
                binding.tvSimilitudVisual.visibility = View.VISIBLE
                binding.tvSimilitudVisual.text = context.getString(
                    R.string.duplicado_ciudadano_similitud_visual_no_disponible
                )
            }
        }

        when (alerta.cercania) {
            "muy_cerca" -> {
                binding.tvCercaniaGps.visibility = View.VISIBLE
                binding.tvCercaniaGps.text = context.getString(R.string.duplicado_ciudadano_cercania_muy_cerca)
            }
            "cerca" -> {
                binding.tvCercaniaGps.visibility = View.VISIBLE
                binding.tvCercaniaGps.text = context.getString(R.string.duplicado_ciudadano_cercania_cerca)
            }
            else -> {
                binding.tvCercaniaGps.visibility = View.VISIBLE
                binding.tvCercaniaGps.text = context.getString(R.string.duplicado_ciudadano_cercania_general)
            }
        }

        binding.tvAdvertenciaMetrica.text =
            alerta.advertenciaMetricaVisual ?: DuplicadoCiudadanoPolicy.ADVERTENCIA_METRICA_DEFECTO

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        binding.btnSiMismoAnimal.setOnClickListener {
            binding.layoutPasoPregunta.visibility = View.GONE
            binding.layoutPasoYaExiste.visibility = View.VISIBLE
        }

        binding.btnVolverPregunta.setOnClickListener {
            binding.layoutPasoYaExiste.visibility = View.GONE
            binding.layoutPasoPregunta.visibility = View.VISIBLE
        }

        binding.btnCancelarNuevoEnvio.setOnClickListener {
            dialog.dismiss()
            onCancelarNuevoEnvio()
        }

        binding.btnNoContinuarEnvio.setOnClickListener {
            dialog.dismiss()
            onContinuarEnvio()
        }

        dialog.show()
        return dialog
    }
}
