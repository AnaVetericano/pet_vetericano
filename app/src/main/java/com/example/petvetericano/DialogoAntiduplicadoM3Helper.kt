package com.example.petvetericano

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import com.example.petvetericano.databinding.DialogAlertaAntiduplicadoMd3Binding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Muestra el diálogo con diseño Material Design 3 informando
 * que un medicamento ya tiene una solicitud previa en trámite ante Farmacia CBA,
 * evitando envíos duplicados con una interfaz limpia, pedagógica y moderna.
 */
object DialogoAntiduplicadoM3Helper {

    fun mostrar(
        context: Context,
        nombreMedicamento: String,
        idSolicitud: Int? = null,
        cantidadPedida: Int? = null,
        mensajePersonalizado: String? = null,
        onVerSolicitudes: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        val binding = DialogAlertaAntiduplicadoMd3Binding.inflate(LayoutInflater.from(context))

        binding.tvNombreMedDuplicado.text = nombreMedicamento

        val detalle = when {
            mensajePersonalizado != null -> mensajePersonalizado
            idSolicitud != null && idSolicitud > 0 && cantidadPedida != null && cantidadPedida > 0 ->
                "Ya existe la Solicitud #$idSolicitud con $cantidadPedida unidades pedidas, pendiente de entrega por Farmacia CBA."
            idSolicitud != null && idSolicitud > 0 ->
                "Ya existe la Solicitud #$idSolicitud en trámite pendiente de entrega por Farmacia CBA."
            idSolicitud != null && idSolicitud == -1 ->
                "Este medicamento ya se encuentra en tu bandeja de salida offline local esperando conexión para radicarse."
            else ->
                "Este fármaco ya cuenta con una solicitud de reposición activa pendiente de entrega por Farmacia CBA."
        }
        binding.tvDetalleSolicitudDuplicada.text = detalle

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        binding.btnCerrarAntiduplicado.setOnClickListener {
            dialog.dismiss()
            onDismiss?.invoke()
        }

        binding.btnVerSolicitudesAntiduplicado.setOnClickListener {
            dialog.dismiss()
            if (onVerSolicitudes != null) {
                onVerSolicitudes.invoke()
            } else {
                val intent = Intent(context, SolicitudesKitActivity::class.java)
                context.startActivity(intent)
            }
        }

        dialog.setOnDismissListener {
            onDismiss?.invoke()
        }

        dialog.show()
    }
}
