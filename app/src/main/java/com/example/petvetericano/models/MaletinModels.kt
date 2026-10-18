package com.example.petvetericano.models

import java.io.Serializable

/**
 * Modelo de datos para los medicamentos asignados al maletín del veterinario.
 */
data class MedicamentoMaletin(
    val id: String,
    val nombre: String,
    val concentracion: String,
    val categoria: String,
    val presentacion: String,
    val unidadMedida: String, // "ml", "comprimidos", "spray", etc.
    var cantidadDisponible: Double,
    val cantidadInicial: Double,
    val lote: String,
    val fechaVencimiento: String,
    val viaRecomendada: String,
    val descripcionUso: String = ""
) : Serializable

/**
 * Modelo para registrar la aplicación de un medicamento durante la atención clínica en campo.
 */
data class MedicamentoAplicadoActa(
    val idMedicamento: String,
    val nombre: String,
    val concentracion: String,
    val cantidad: Double,
    val unidadMedida: String,
    val viaAdministracion: String,
    val pacienteDestino: String? = null,
    val fechaHora: Long = System.currentTimeMillis()
) : Serializable
