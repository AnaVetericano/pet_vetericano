package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityInicioBienvenidaBinding
import com.example.petvetericano.databinding.ActivityInicioSesionBinding

class inicio_bienvenida : AppCompatActivity() {
    private lateinit var binding: ActivityInicioBienvenidaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = ActivityInicioBienvenidaBinding.inflate(layoutInflater)
        setContentView(binding.root)

    }


}