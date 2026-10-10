package com.example.petvetericano

data class AnimalCompania(
    val idFicha: String,
    val nombre: String,
    val raza: String = "Criollo",
    val descripcion: String,
    val urlImagen: String? = null,
    val urlYoutube: String? = null
)