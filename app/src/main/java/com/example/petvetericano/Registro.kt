package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityRegistroBinding

class Registro : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        lateinit var binding: ActivityRegistroBinding
        super.onCreate(savedInstanceState)
        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnSignUp.setOnClickListener { SignUp() }
    }

    private fun SignUp(){
        intent = Intent(this, Inicio_sesion::class.java)
        startActivity(intent)
    }
}