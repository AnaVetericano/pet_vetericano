package com.example.petvetericano

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Utilidad centralizada para presentar alertas y diálogos modales
 * consistentes bajo las guías de Material Design 3 y la identidad Vetericano.
 */
object MaterialDialogHelper {

    /**
     * Muestra una alerta de confirmación con dos botones de acción.
     */
    fun mostrarConfirmacion(
        context: Context,
        titulo: String,
        mensaje: String,
        textoPositivo: String = "Aceptar",
        textoNegativo: String = "Cancelar",
        @DrawableRes icono: Int = R.drawable.ic_info_circle,
        onConfirmar: () -> Unit
    ): AlertDialog {
        return MaterialAlertDialogBuilder(context)
            .setIcon(icono)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton(textoPositivo) { dialog, _ ->
                dialog.dismiss()
                onConfirmar()
            }
            .setNegativeButton(textoNegativo) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Muestra una alerta de confirmación de eliminación destructiva.
     */
    fun mostrarEliminacion(
        context: Context,
        titulo: String,
        mensaje: String,
        textoPositivo: String = "Eliminar",
        textoNegativo: String = "Cancelar",
        onConfirmar: () -> Unit
    ): AlertDialog {
        return MaterialAlertDialogBuilder(context)
            .setIcon(R.drawable.ic_delete)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton(textoPositivo) { dialog, _ ->
                dialog.dismiss()
                onConfirmar()
            }
            .setNegativeButton(textoNegativo) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Muestra una alerta de éxito de la operación.
     */
    fun mostrarExito(
        context: Context,
        titulo: String,
        mensaje: String,
        textoBoton: String = "Entendido",
        cancelable: Boolean = true,
        onAceptar: (() -> Unit)? = null
    ): AlertDialog {
        return MaterialAlertDialogBuilder(context)
            .setIcon(R.drawable.ic_check)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setCancelable(cancelable)
            .setPositiveButton(textoBoton) { dialog, _ ->
                dialog.dismiss()
                onAceptar?.invoke()
            }
            .show()
    }

    /**
     * Muestra una alerta de error crítico o fallo de conexión.
     */
    fun mostrarError(
        context: Context,
        titulo: String,
        mensaje: String,
        textoBoton: String = "Entendido",
        onAceptar: (() -> Unit)? = null
    ): AlertDialog {
        return MaterialAlertDialogBuilder(context)
            .setIcon(R.drawable.warning_icon4)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton(textoBoton) { dialog, _ ->
                dialog.dismiss()
                onAceptar?.invoke()
            }
            .show()
    }

    /**
     * Muestra una advertencia o aviso de validación.
     */
    fun mostrarAdvertencia(
        context: Context,
        titulo: String,
        mensaje: String,
        textoBoton: String = "Entendido",
        onAceptar: (() -> Unit)? = null
    ): AlertDialog {
        return MaterialAlertDialogBuilder(context)
            .setIcon(R.drawable.warning_icon4)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton(textoBoton) { dialog, _ ->
                dialog.dismiss()
                onAceptar?.invoke()
            }
            .show()
    }
}
