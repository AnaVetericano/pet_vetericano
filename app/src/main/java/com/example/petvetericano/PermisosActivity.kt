package com.example.petvetericano

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// El error principal radicaba aquí. El import de tu código original estaba incompleto.
// Ahora apunta exactamente a la clase Binding generada a partir de activity_permisos.xml
import com.example.petvetericano.databinding.ActivityPermisosBinding

class PermisosActivity : AppCompatActivity() {

    // Instancia del binding de la vista de permisos.
    private lateinit var binding: ActivityPermisosBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflamos la interfaz.
        binding = ActivityPermisosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Acción de regreso.
        binding.btnBack.setOnClickListener {
            finish()
        }

        // setOnCheckedChangeListener está atento a los cambios de estado (prendido/apagado) del SwitchMaterial.
        // La lambda recibe dos parámetros: el botón en sí mismo, y un booleano (isChecked).
        // Usamos '_' para el primer parámetro porque Kotlin requiere que ocupemos la posición, pero no nos interesa leer las propiedades de la vista del switch en este momento, solo su estado booleano.
        binding.switchLocation.setOnCheckedChangeListener { _, isChecked ->
            // if (isChecked) simplifica la evaluación "if (isChecked == true)".
            val estado = if (isChecked) "activada" else "desactivada"
            // El signo $ incrusta el valor de la variable 'estado' directamente en la cadena sin tener que usar el operador + para concatenar.
            Toast.makeText(this, "Ubicación $estado", Toast.LENGTH_SHORT).show()
        }

        binding.switchCamera.setOnCheckedChangeListener { _, isChecked ->
            val estado = if (isChecked) "activada" else "desactivada"
            Toast.makeText(this, "Cámara $estado", Toast.LENGTH_SHORT).show()
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            val estado = if (isChecked) "activadas" else "desactivadas"
            Toast.makeText(this, "Notificaciones $estado", Toast.LENGTH_SHORT).show()
        }
    }
}