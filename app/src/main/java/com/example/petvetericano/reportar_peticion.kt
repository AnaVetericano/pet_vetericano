package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityReportarPeticionBinding


class reportar_peticion : AppCompatActivity() {
    private lateinit var binding: ActivityReportarPeticionBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding= ActivityReportarPeticionBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.btndev.setOnClickListener {
            val intent= Intent(this, bienvenida::class.java)
            startActivity(intent)
        }
        binding.cardHerido.setOnClickListener {
            val intent = Intent(this, reportar_peticionn::class.java)
            startActivity(intent)
        }

        binding.cardMaltrato.setOnClickListener {

            val intent= Intent(this, reportar_peticionn::class.java)
            startActivity(intent)
        }
        binding.cardCalle.setOnClickListener {

            val intent= Intent(this, reportar_peticionn::class.java)
            startActivity(intent)
        }

    }
}