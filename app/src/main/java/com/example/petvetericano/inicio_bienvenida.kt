package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityBienvenidaBinding
import com.example.petvetericano.databinding.ActivityInicioBienvenidaBinding

class inicio_bienvenida : AppCompatActivity() {

    private lateinit var binding: ActivityInicioBienvenidaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInicioBienvenidaBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}