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









    }
}