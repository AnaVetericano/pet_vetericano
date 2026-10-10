package com.example.petvetericano

data class ActividadVoluntariado( //cambios
    val idActividad: String,
    val titulo: String,
    val fecha: String,
    val descripcion: String,
    val urlImagen: String? = null,
    val urlYoutube: String? = null
)
