package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityReportarPeticionBinding

class reportar_peticion : AppCompatActivity() {

    // Declaramos el ViewBinding. Se usa lateinit porque se inicializará en onCreate, no de inmediato.
    // Si no usamos binding y usamos findViewById, el código sería más lento y propenso a errores de nulos (NullPointerException).
    private lateinit var binding: ActivityReportarPeticionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Habilita que la app ocupe toda la pantalla, incluyendo el área de la barra de estado y navegación.
        enableEdgeToEdge()

        // Inflamos la vista. Esto convierte el archivo XML en objetos de Kotlin que podemos manipular.
        binding = ActivityReportarPeticionBinding.inflate(layoutInflater)

        // Establecemos la raíz del binding como la vista principal de esta actividad.
        setContentView(binding.root)

        // Asignamos listeners a las tarjetas. Al hacer clic, llaman a la función abrirSiguientePantalla
        // pasándole un String estático que define el tipo de caso.
        binding.cardHerido.setOnClickListener {
            abrirSiguientePantalla("Herido")
        }

        binding.cardMaltrato.setOnClickListener {
            abrirSiguientePantalla("Maltrato")
        }

        binding.cardCalle.setOnClickListener {
            abrirSiguientePantalla("En calle")
        }
    }

    // Función privada que recibe el tipo de reporte y prepara el viaje a la pantalla intermedia.
    private fun abrirSiguientePantalla(tipoReporte: String) {
        // Creamos el Intent hacia la pantalla intermedia (reportar_peticionn).
        // Usamos la función de alcance '.apply' para configurar el Intent inmediatamente después de crearlo.
        // Si no usáramos .apply, tendríamos que escribir: intent.putExtra(...) en una línea separada.
        val intent = Intent(this, reportar_peticionn::class.java).apply {

            // putExtra empaca el dato. Consta de una LLAVE ("TIPO_REPORTE") y el VALOR (tipoReporte).
            // La llave es vital que sea exactamente igual en todas las pantallas.
            putExtra("TIPO_REPORTE", tipoReporte)
        }

        // Arrancamos la actividad.
        startActivity(intent)
    }
}