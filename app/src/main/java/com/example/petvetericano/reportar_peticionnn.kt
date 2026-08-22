package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityReportarPeticionnnBinding

class reportar_peticionnn : AppCompatActivity() {
    private lateinit var binding: ActivityReportarPeticionnnBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding= ActivityReportarPeticionnnBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnContinue.setOnClickListener {
            val intent = Intent(this, confirmar_reporte::class.java)
            startActivity(intent)

        }

    }
}