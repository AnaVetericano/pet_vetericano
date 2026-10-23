package com.example.petvetericano

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityLenguajeBinding

class LenguajeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLenguajeBinding
    private lateinit var prefs: SharedPreferencesManager // Llama al intermediario de nuestra memoria persistente.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLenguajeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializamos pasándole el "this" (el contexto de vida de LenguajeActivity) para que el gestor sepa en qué archivo físico del teléfono debe guardar los datos.
        prefs = SharedPreferencesManager(this)

        binding.btnBack.setOnClickListener { finish() }

        // when es la versión avanzada de switch en Kotlin. Compara el String extraído de la memoria contra tres opciones fijas.
        when (prefs.getLanguage()) {
            "en" -> binding.rbEnglish.isChecked = true // Marca visualmente el radio button de inglés.
            "pt" -> binding.rbPortuguese.isChecked = true // Marca el de portugués.
            // Si el valor devuelto no es ninguno de los anteriores (ej. si la app es nueva y aún no se guarda nada), el 'else' atrapa el caso garantizando que el español quede seleccionado por defecto, previniendo fallos gráficos.
            else -> binding.rbSpanish.isChecked = true
        }

        // Cada vez que tocas una carta, ejecutamos la función privada enviándole el parámetro crudo (el código internacional del idioma).
        binding.rbSpanish.setOnClickListener { changeLanguage("es") }
        binding.rbEnglish.setOnClickListener { changeLanguage("en") }
        binding.rbPortuguese.setOnClickListener { changeLanguage("pt") }
    }

    // Función aislada y privada (inaccesible desde otros archivos) encargada de cambiar la preferencia y cerrar.
    private fun changeLanguage(langCode: String) {
        // Evaluamos: si el idioma que el usuario seleccionó es DIFERENTE (!=) al que ya estaba guardado en memoria...
        // Si no usas este if, y el usuario toca "Español" teniendo ya "Español", el sistema sobreescribirá la memoria inútilmente gastando recursos del CPU y cerrando la pantalla sin aportar ningún cambio.
        if (prefs.getLanguage() != langCode) {
            prefs.setLanguage(langCode) // Dispara el método que inyecta el nuevo código en el archivo XML de SharedPreferences local.
            finish()
        }
    }
}