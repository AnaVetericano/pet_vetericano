package com.example.petvetericano.facial

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Diálogos Material 3 compartidos para activar, re-enrolar o eliminar el reconocimiento facial
 * desde el perfil de Ciudadano ([com.example.petvetericano.editar_perfil]) y Veterinario
 * ([com.example.petvetericano.OpcionesEditarPerfilActivity]).
 */
object FacialProfileHelper {

    fun textoEstadoPerfil(activity: Activity, userId: Int): String {
        val activo = FacialStorageManager(activity).tieneRostroEnroladoParaUsuario(userId)
        return if (activo) {
            activity.getString(R.string.facial_perfil_estado_activo)
        } else {
            activity.getString(R.string.facial_perfil_estado_inactivo)
        }
    }

    fun manejarClickConfiguracionFacial(
        activity: Activity,
        enrolarLauncher: ActivityResultLauncher<Intent>,
        onEstadoActualizado: (mensajeFeedback: String?) -> Unit
    ) {
        val prefs = SharedPreferencesManager(activity)
        val userId = prefs.getUserId()
        val refreshToken = prefs.getRefreshToken().trim()
        if (userId <= 0 || refreshToken.isEmpty()) {
            onEstadoActualizado("Inicia sesión nuevamente para configurar el reconocimiento facial.")
            return
        }

        val storage = FacialStorageManager(activity)
        val yaActivoParaEsteUsuario = storage.tieneRostroEnroladoParaUsuario(userId)

        if (yaActivoParaEsteUsuario) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.facial_opciones_titulo)
                .setMessage(R.string.facial_opciones_mensaje)
                .setPositiveButton(R.string.facial_opciones_reenrolar) { dialog, _ ->
                    dialog.dismiss()
                    enrolarLauncher.launch(ReconocimientoFacialActivity.crearIntentEnrolar(activity))
                }
                .setNegativeButton(R.string.facial_opciones_eliminar) { dialog, _ ->
                    dialog.dismiss()
                    storage.eliminarPerfilFacial()
                    onEstadoActualizado(activity.getString(R.string.facial_eliminado_ok))
                }
                .setNeutralButton(R.string.btn_cancelar) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            val perfilOtro = storage.obtenerPerfilFacial()
            val mensajeBase = activity.getString(R.string.facial_consentimiento_mensaje)
            val mensajeFinal = if (perfilOtro != null && perfilOtro.userId != userId) {
                "$mensajeBase\n\nNota: Al continuar, se reemplazará el rostro previamente registrado en este dispositivo."
            } else {
                mensajeBase
            }

            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.facial_consentimiento_titulo)
                .setMessage(mensajeFinal)
                .setNegativeButton(R.string.btn_cancelar) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(R.string.facial_consentimiento_aceptar) { dialog, _ ->
                    dialog.dismiss()
                    enrolarLauncher.launch(ReconocimientoFacialActivity.crearIntentEnrolar(activity))
                }
                .show()
        }
    }
}
