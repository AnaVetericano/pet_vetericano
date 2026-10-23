package com.example.petvetericano

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.petvetericano.databinding.ActivityPermisosBinding

class PermisosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermisosBinding

    // Launcher para solicitar permiso de ubicación
    private val requestLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) {
                binding.switchLocation.isChecked = true
                Toast.makeText(this, "Ubicación activada", Toast.LENGTH_SHORT).show()
            } else {
                // El usuario rechazó — devolver el switch a su estado real
                binding.switchLocation.isChecked = false
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }
        }

    // Launcher para solicitar permiso de cámara
    private val requestCameraLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) {
                binding.switchCamera.isChecked = true
                Toast.makeText(this, "Cámara activada", Toast.LENGTH_SHORT).show()
            } else {
                binding.switchCamera.isChecked = false
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
            }
        }

    // Launcher para solicitar permiso de notificaciones (Android 13+)
    private val requestNotificationsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) {
                binding.switchNotifications.isChecked = true
                Toast.makeText(this, "Notificaciones activadas", Toast.LENGTH_SHORT).show()
            } else {
                binding.switchNotifications.isChecked = false
                Toast.makeText(this, "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPermisosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar switches con el estado REAL de los permisos en el dispositivo
        cargarEstadoPermisos()

        binding.btnBack.setOnClickListener {
            finish()
        }

        // Switch de Ubicación
        binding.switchLocation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                when {
                    // Ya tiene el permiso — no hace falta pedirlo
                    tienePermiso(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                        Toast.makeText(this, "Ubicación ya estaba activada", Toast.LENGTH_SHORT).show()
                    }
                    // Solicitar el permiso al sistema
                    else -> {
                        requestLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
            } else {
                // Android no permite revocar permisos desde código — hay que ir a Ajustes
                abrirAjustesApp()
                Toast.makeText(this, "Desactiva el permiso manualmente en Ajustes", Toast.LENGTH_LONG).show()
            }
        }

        // Switch de Cámara
        binding.switchCamera.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                when {
                    tienePermiso(Manifest.permission.CAMERA) -> {
                        Toast.makeText(this, "Cámara ya estaba activada", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        requestCameraLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            } else {
                abrirAjustesApp()
                Toast.makeText(this, "Desactiva el permiso manualmente en Ajustes", Toast.LENGTH_LONG).show()
            }
        }

        // Switch de Notificaciones
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    when {
                        tienePermiso(Manifest.permission.POST_NOTIFICATIONS) -> {
                            Toast.makeText(this, "Notificaciones ya estaban activadas", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            requestNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                } else {
                    // En Android 12 o menor no se necesita permiso explícito
                    Toast.makeText(this, "Notificaciones activadas", Toast.LENGTH_SHORT).show()
                }
            } else {
                abrirAjustesApp()
                Toast.makeText(this, "Desactiva el permiso manualmente en Ajustes", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Actualiza los switches con el estado real de los permisos al entrar a la pantalla
    private fun cargarEstadoPermisos() {
        binding.switchLocation.isChecked      = tienePermiso(Manifest.permission.ACCESS_FINE_LOCATION)
        binding.switchCamera.isChecked        = tienePermiso(Manifest.permission.CAMERA)
        binding.switchNotifications.isChecked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            tienePermiso(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true // En Android 12 o menor las notificaciones están activas por defecto
        }
    }

    // Verifica si un permiso específico ya fue concedido
    private fun tienePermiso(permiso: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_GRANTED
    }

    // Abre los ajustes de la app para que el usuario pueda revocar permisos manualmente
    // Android no permite revocar permisos desde código por seguridad
    private fun abrirAjustesApp() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}