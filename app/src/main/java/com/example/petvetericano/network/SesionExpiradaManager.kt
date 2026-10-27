package com.example.petvetericano.network

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.GoogleAuthHelper
import com.example.petvetericano.MainActivity
import com.example.petvetericano.R
import com.example.petvetericano.Registro
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.inicio_sesion
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Gestor centralizado para el ciclo de vida de sesiones expiradas en PetVetericano.
 *
 * Cuando el servidor responde 400 o 401 al intentar refrescar el token (refresh muerto),
 * este gestor evita tormentas de llamadas de red redundantes, asegura que el aviso
 * se despliegue en primer plano ante el usuario (o quede pendiente si la app estaba en segundo plano)
 * y ejecuta el flujo de retorno a login/bienvenida limpiando credenciales pero conservando
 * las actas guardadas en almacenamiento local.
 */
object SesionExpiradaManager : Application.ActivityLifecycleCallbacks {

    @Volatile
    var refreshMuerto: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentActivityRef: WeakReference<Activity>? = null
    private var currentDialog: AlertDialog? = null
    private var currentDialogActivity: WeakReference<Activity>? = null

    private var avisoPendiente = false
    private var dialogoVisible = false

    fun init(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    @Synchronized
    fun marcarSesionMuerta(refreshFallido: String) {
        refreshMuerto = refreshFallido
        if (avisoPendiente || dialogoVisible) {
            return
        }
        avisoPendiente = true
        mainHandler.post {
            intentarMostrarAviso()
        }
    }

    private fun esPantallaLogin(activity: Activity): Boolean {
        return activity is MainActivity || activity is inicio_sesion || activity is Registro
    }

    private fun intentarMostrarAviso() {
        val activity = currentActivityRef?.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            // No hay Activity resumida en primer plano; queda avisoPendiente = true
            // y se mostrará en el siguiente onActivityResumed.
            return
        }

        if (esPantallaLogin(activity)) {
            avisoPendiente = false
            return
        }

        if (dialogoVisible) {
            return
        }

        mostrarAviso(activity)
    }

    private fun mostrarAviso(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) {
            avisoPendiente = true
            return
        }

        avisoPendiente = false
        dialogoVisible = true

        val dialog = MaterialAlertDialogBuilder(activity)
            .setIcon(R.drawable.warning_icon4)
            .setTitle(R.string.sesion_expirada_titulo)
            .setMessage(R.string.sesion_expirada_mensaje)
            .setCancelable(false)
            .setPositiveButton(R.string.sesion_expirada_boton) { d, _ ->
                d.dismiss()
                onBotonIniciarSesionPulsado(activity)
            }
            .create()

        dialog.setOnDismissListener {
            if (currentDialog === dialog) {
                currentDialog = null
                currentDialogActivity = null
                dialogoVisible = false
            }
        }

        currentDialog = dialog
        currentDialogActivity = WeakReference(activity)

        try {
            dialog.show()
        } catch (_: Exception) {
            dialogoVisible = false
            currentDialog = null
            currentDialogActivity = null
            avisoPendiente = true
        }
    }

    private fun onBotonIniciarSesionPulsado(activity: Activity) {
        avisoPendiente = false
        dialogoVisible = false
        refreshMuerto = null
        currentDialog = null
        currentDialogActivity = null

        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}

        if (activity is ComponentActivity) {
            activity.lifecycleScope.launch {
                try {
                    GoogleAuthHelper.cerrarSesionGoogle(activity)
                } catch (_: Exception) {}
            }
        }

        val prefs = SharedPreferencesManager(activity)
        prefs.clearSession()
        RetrofitClient.authToken = null

        val intent = Intent(activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }

    // Callbacks de ciclo de vida de Activity
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        if (esPantallaLogin(activity)) {
            avisoPendiente = false
            return
        }
        if (avisoPendiente && !dialogoVisible) {
            mostrarAviso(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentDialogActivity?.get() === activity) {
            try {
                currentDialog?.dismiss()
            } catch (_: Exception) {}
            currentDialog = null
            currentDialogActivity = null
            dialogoVisible = false
            if (refreshMuerto != null) {
                avisoPendiente = true
            }
        }
    }
}
