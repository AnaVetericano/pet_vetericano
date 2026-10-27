package com.example.petvetericano.models

import java.io.Serializable
import java.util.UUID

/**
 * Estados semánticos oficiales para el semáforo clínico de existencias del maletín.
 */
enum class NivelSemaforoStock(val etiqueta: String) {
    OPTIMO("Óptimo"),
    REORDEN("Stock bajo"),
    CRITICO("Crítico"),
    AGOTADO("Agotado"),
    VENCIDO("Vencido")
}

/**
 * Modelo de datos para los medicamentos asignados al maletín del veterinario.
 * Compatible con la respuesta del backend Django (/api/inventario/kit/mi-kit/ y snapshot-movil)
 * y con las vistas locales de Android.
 */
data class MedicamentoMaletin(
    val id: String, // String para compatibilidad, representa id_medicamento o código
    val nombre: String,
    val concentracion: String = "",
    val categoria: String = "General",
    val presentacion: String = "Unidad",
    val unidadMedida: String = "unidades", // "comprimidos", "ampollas", "frascos", etc.
    var cantidadDisponible: Double = 0.0,
    val cantidadInicial: Double = 0.0,
    val lote: String = "Lote estándar",
    val fechaVencimiento: String = "",
    val viaRecomendada: String = "Clínica",
    val descripcionUso: String = "",
    val saldoVencido: Int = 0,
    val lotes: List<KitLoteResponse> = emptyList()
) : Serializable {

    val idMedicamentoInt: Int
        get() = id.toIntOrNull() ?: 0

    val saldoDisponibleInt: Int
        get() = cantidadDisponible.toInt()

    val tieneLotesVencidos: Boolean
        get() = saldoVencido > 0 || lotes.any { it.vencido }

    /**
     * Cuota de referencia asignada al veterinario para su kit de campo.
     */
    val cuotaAsignada: Int?
        get() = if (cantidadInicial > 0) cantidadInicial.toInt() else null

    /**
     * Nivel del semáforo clínico según la Regla Híbrida CBA:
     * - CRÍTICO: Stock <= 15% de la cuota O <= 1 unidad
     * - REORDEN: Stock <= 40% de la cuota
     * - ÓPTIMO: Stock > 40% de la cuota
     * - AGOTADO: Stock <= 0
     * - VENCIDO: Solo tiene lotes vencidos
     */
    val nivelSemaforo: NivelSemaforoStock
        get() {
            if (tieneLotesVencidos && saldoDisponibleInt == 0) return NivelSemaforoStock.VENCIDO
            if (cantidadDisponible <= 0.0) return NivelSemaforoStock.AGOTADO

            val cuota = if (cantidadInicial > 0) cantidadInicial else 1.0
            val umbralCritico = maxOf(1.0, kotlin.math.ceil(0.15 * cuota))
            val umbralReorden = cuota * 0.40

            return when {
                cantidadDisponible <= umbralCritico -> NivelSemaforoStock.CRITICO
                cantidadDisponible <= umbralReorden -> NivelSemaforoStock.REORDEN
                else -> NivelSemaforoStock.OPTIMO
            }
        }

    val unidadesFaltantesParaCuota: Int
        get() = maxOf(0, (cantidadInicial - cantidadDisponible).toInt())

    val esCritico: Boolean
        get() = nivelSemaforo == NivelSemaforoStock.CRITICO || nivelSemaforo == NivelSemaforoStock.AGOTADO

    val esReorden: Boolean
        get() = nivelSemaforo == NivelSemaforoStock.REORDEN

    val necesitaReposicion: Boolean
        get() = esCritico || esReorden || unidadesFaltantesParaCuota > 0

    companion object {
        /**
         * Crea un MedicamentoMaletin a partir del KitItemResponse del backend.
         */
        fun fromKitItem(item: KitItemResponse): MedicamentoMaletin {
            val primerLote = item.lotes.firstOrNull()
            val loteStr = if (item.lotes.size > 1) {
                "${item.lotes.size} lotes (${primerLote?.idInventario?.let { "Lote #$it" } ?: "General"})"
            } else {
                primerLote?.idInventario?.let { "Lote #$it" } ?: "Lote asignado"
            }

            val fechaVencStr = primerLote?.fechaVencimiento ?: "No registrada"
            val cantTotal = (item.saldoDisponible + item.saldoVencido).toDouble()
            val totalReferencia = item.lotes.sumOf { it.cantidadReferencia }.toDouble()
            val cantidadInicialCalculada = if (totalReferencia > 0) {
                totalReferencia
            } else if (cantTotal > 0) {
                cantTotal
            } else {
                maxOf(item.saldoDisponible.toDouble(), 1.0)
            }

            return MedicamentoMaletin(
                id = item.idMedicamento.toString(),
                nombre = item.nombre,
                concentracion = "",
                categoria = categorizarPorNombre(item.nombre),
                presentacion = "Unidad clínica",
                unidadMedida = "unidades",
                cantidadDisponible = item.saldoDisponible.toDouble(),
                cantidadInicial = cantidadInicialCalculada,
                lote = loteStr,
                fechaVencimiento = fechaVencStr,
                viaRecomendada = "Vía veterinaria",
                descripcionUso = "Medicamento dotado desde la farmacia central CBA.",
                saldoVencido = item.saldoVencido,
                lotes = item.lotes
            )
        }

        fun categorizarPorNombre(nombre: String): String {
            val n = nombre.lowercase()
            return when {
                // Antibióticos
                n.contains("amox") || n.contains("cefa") || n.contains("enro") ||
                n.contains("ampi") || n.contains("estomizol") || n.contains("metronidazol") ||
                n.contains("doxiciclina") || n.contains("azitro") || n.contains("penic") ||
                n.contains("oxitet") -> "Antibióticos"

                // Anestésicos y Sedantes
                n.contains("ketamina") || n.contains("xilacina") || n.contains("acepromacina") ||
                n.contains("propofol") || n.contains("lidocaina") || n.contains("diazepam") -> "Anestésicos"

                // Analgésicos y Antiinflamatorios (AINE)
                n.contains("tramadol") || n.contains("meloxicam") || n.contains("aspirina") ||
                n.contains("ibuprofeno") || n.contains("carprof") || n.contains("dipirona") ||
                n.contains("flunixin") || n.contains("ketoprof") || n.contains("naproxeno") ||
                n.contains("acetaminof") || n.contains("paracetamol") -> "Analgésicos / AINE"

                // Corticoides
                n.contains("dexa") || n.contains("predni") || n.contains("hidrocort") -> "Corticoides"

                // Vitaminas y Suplementos
                n.contains("vitamina") || n.contains("complejo") || n.contains("calcio") ||
                n.contains("hierro") -> "Vitaminas"

                // Antiparasitarios
                n.contains("iverm") || n.contains("prazi") || n.contains("pirant") ||
                n.contains("fenbend") || n.contains("albend") || n.contains("fipronil") -> "Antiparasitarios"

                // Fluidoterapia
                n.contains("ringer") || n.contains("salino") || n.contains("dextrosa") ||
                n.contains("cloruro") || n.contains("suero") -> "Fluidoterapia"

                else -> "Otros"
            }
        }
    }
}

/**
 * Modelo para registrar la aplicación de un medicamento durante la atención clínica en campo.
 * Incluye client_uuid para garantizar la idempotencia de consumo en el backend.
 */
data class MedicamentoAplicadoActa(
    val idMedicamento: String,
    val nombre: String,
    val concentracion: String = "",
    val cantidad: Double = 1.0,
    val unidadMedida: String = "unidades",
    val viaAdministracion: String = "Clínica",
    val pacienteDestino: String? = null,
    val fechaHora: Long = System.currentTimeMillis(),
    val clientUuid: String = UUID.randomUUID().toString()
) : Serializable {

    val idMedicamentoInt: Int
        get() = idMedicamento.toIntOrNull() ?: 0

    val unidadesEnteras: Int
        get() = maxOf(1, cantidad.toInt())

    fun toMedicamentoUsadoRequest(): MedicamentoUsadoRequest {
        return MedicamentoUsadoRequest(
            idMedicamento = idMedicamentoInt,
            unidades = unidadesEnteras,
            clientUuid = clientUuid
        )
    }
}
