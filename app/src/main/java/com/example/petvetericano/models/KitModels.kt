package com.example.petvetericano.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Modelos de datos para el módulo de Inventario por Kit de Veterinario.
 * Basados en el contrato del backend Django (inventario/api/kit_serializers.py).
 */

// =========================================================================
// 1. MI KIT Y SNAPSHOT MÓVIL
// =========================================================================

data class KitMiKitResponse(
    @SerializedName("total_unidades")
    val totalUnidades: Int = 0,

    @SerializedName("items")
    val items: List<KitItemResponse> = emptyList()
) : Serializable

data class KitItemResponse(
    @SerializedName("id_medicamento")
    val idMedicamento: Int,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("saldo_disponible")
    val saldoDisponible: Int,

    @SerializedName("saldo_vencido")
    val saldoVencido: Int = 0,

    @SerializedName("lotes")
    val lotes: List<KitLoteResponse> = emptyList()
) : Serializable

data class KitLoteResponse(
    @SerializedName("id_kit_lote")
    val idKitLote: Int,

    @SerializedName("id_inventario")
    val idInventario: Int,

    @SerializedName("cantidad_actual")
    val cantidadActual: Int,

    @SerializedName("cantidad_referencia")
    val cantidadReferencia: Int = 0,

    @SerializedName("fecha_vencimiento")
    val fechaVencimiento: String?,

    @SerializedName("vencido")
    val vencido: Boolean = false
) : Serializable

data class SnapshotMovilResponse(
    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("stock")
    val stock: List<StockSnapshotItem> = emptyList()
) : Serializable

data class StockSnapshotItem(
    @SerializedName("id_medicamento")
    val idMedicamento: Int,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("saldo_disponible")
    val saldoDisponible: Int
) : Serializable

// =========================================================================
// 2. CONSUMO EN CAMPO (ACTA DE ATENCIÓN)
// =========================================================================

data class MedicamentoUsadoRequest(
    @SerializedName("id_medicamento")
    val idMedicamento: Int,

    @SerializedName("unidades")
    val unidades: Int,

    @SerializedName("client_uuid")
    val clientUuid: String
) : Serializable

data class AlertasFarmaciaResponse(
    @SerializedName("conflictos_stock")
    val conflictosStock: List<ConflictoStockItemResponse> = emptyList()
) : Serializable

data class ConflictoStockItemResponse(
    @SerializedName("client_uuid")
    val clientUuid: String? = null,

    @SerializedName("id_medicamento")
    val idMedicamento: Int,

    @SerializedName("nombre_medicamento")
    val nombreMedicamento: String? = null,

    @SerializedName("solicitado")
    val solicitado: Int = 0,

    @SerializedName("disponible")
    val disponible: Int = 0,

    @SerializedName("motivo_rechazo")
    val motivoRechazo: String? = null
) : Serializable

// =========================================================================
// 3. SOLICITUDES DE KIT (REPOSICIÓN Y DEVOLUCIÓN)
// =========================================================================

data class ItemSolicitudInput(
    @SerializedName("id_medicamento")
    val idMedicamento: Int,

    @SerializedName("cantidad")
    val cantidad: Int
) : Serializable

data class CrearSolicitudKitRequest(
    @SerializedName("tipo_solicitud")
    val tipoSolicitud: String, // "REPOSICION" o "DEVOLUCION"

    @SerializedName("motivo")
    val motivo: String,

    @SerializedName("es_reposicion_total")
    val esReposicionTotal: Boolean = false,

    @SerializedName("items")
    val items: List<ItemSolicitudInput>? = null
) : Serializable

data class SolicitudKitResponse(
    @SerializedName("id_solicitud")
    val idSolicitud: Int,

    @SerializedName("tipo_solicitud")
    val tipoSolicitud: String,

    @SerializedName("estado")
    val estado: String,

    @SerializedName("id_veterinario")
    val idVeterinario: Int?,

    @SerializedName("nombre_veterinario")
    val nombreVeterinario: String?,

    @SerializedName("id_gestor")
    val idGestor: Int?,

    @SerializedName("nombre_gestor")
    val nombreGestor: String?,

    @SerializedName("fecha_solicitud")
    val fechaSolicitud: String?,

    @SerializedName("fecha_resolucion")
    val fechaResolucion: String?,

    @SerializedName("motivo_solicitud")
    val motivoSolicitud: String?,

    @SerializedName("observaciones_gestor")
    val observacionesGestor: String?,

    @SerializedName("es_reposicion_total")
    val esReposicionTotal: Boolean = false,

    @SerializedName("detalles")
    val detalles: List<DetalleSolicitudResponse> = emptyList()
) : Serializable

data class DetalleSolicitudResponse(
    @SerializedName("id_detalle_solicitud")
    val idDetalleSolicitud: Int,

    @SerializedName("id_medicamento")
    val idMedicamento: Int,

    @SerializedName("nombre_medicamento")
    val nombreMedicamento: String?,

    @SerializedName("id_inventario_origen")
    val idInventarioOrigen: Int?,

    @SerializedName("cantidad_solicitada")
    val cantidadSolicitada: Int,

    @SerializedName("cantidad_aprobada")
    val cantidadAprobada: Int?,

    @SerializedName("es_vencido")
    val esVencido: Boolean? = null
) : Serializable

data class ListadoPaginadoResponse<T>(
    @SerializedName("count")
    val count: Int,

    @SerializedName("next")
    val next: String?,

    @SerializedName("previous")
    val previous: String?,

    @SerializedName("results")
    val results: List<T> = emptyList()
) : Serializable

// =========================================================================
// 4. SINCRONIZACIÓN EN LOTE OFFLINE
// =========================================================================

data class ConsumoOfflineItem(
    @SerializedName("client_uuid")
    val clientUuid: String,

    @SerializedName("id_medicamento")
    val idMedicamento: Int,

    @SerializedName("unidades")
    val unidades: Int,

    @SerializedName("origen_tipo")
    val origenTipo: String = "DIRECTO", // "DIRECTO", "ACTA_VISITA", "HISTORIA_CLINICA"

    @SerializedName("id_seguimiento_visita")
    val idSeguimientoVisita: Int? = null,

    @SerializedName("id_consulta")
    val idConsulta: Int? = null
) : Serializable

data class SincronizarOfflineRequest(
    @SerializedName("consumos")
    val consumos: List<ConsumoOfflineItem>
) : Serializable

data class ItemConsumoAplicado(
    @SerializedName("id_consumo")
    val idConsumo: Int?,

    @SerializedName("client_uuid")
    val clientUuid: String,

    @SerializedName("id_medicamento")
    val idMedicamento: Int,

    @SerializedName("nombre_medicamento")
    val nombreMedicamento: String?,

    @SerializedName("unidades_solicitadas")
    val unidadesSolicitadas: Int,

    @SerializedName("unidades_descontadas")
    val unidadesDescontadas: Int,

    @SerializedName("estado")
    val estado: String,

    @SerializedName("codigo_http")
    val codigoHttp: Int,

    @SerializedName("idempotente")
    val idempotente: Boolean = false
) : Serializable

data class ItemConsumoConflicto(
    @SerializedName("id_consumo")
    val idConsumo: Int?,

    @SerializedName("client_uuid")
    val clientUuid: String,

    @SerializedName("id_medicamento")
    val idMedicamento: Int,

    @SerializedName("nombre_medicamento")
    val nombreMedicamento: String?,

    @SerializedName("unidades")
    val unidades: Int? = null,

    @SerializedName("cantidad_solicitada")
    val cantidadSolicitada: Int? = null,

    @SerializedName("stock_disponible_actual")
    val stockDisponibleActual: Int = 0,

    @SerializedName("estado")
    val estado: String,

    @SerializedName("codigo_http")
    val codigoHttp: Int,

    @SerializedName("motivo_rechazo")
    val motivoRechazo: String?,

    @SerializedName("idempotente")
    val idempotente: Boolean = false
) : Serializable

data class SincronizarOfflineResponse(
    @SerializedName("error")
    val error: String? = null,

    @SerializedName("aplicados")
    val aplicados: List<ItemConsumoAplicado> = emptyList(),

    @SerializedName("conflictos")
    val conflictos: List<ItemConsumoConflicto> = emptyList()
) : Serializable

// =========================================================================
// 5. ALERTAS DE INVENTARIO
// =========================================================================

data class AlertaItemResponse(
    @SerializedName("id_medicamento")
    val idMedicamento: Int,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("saldo_vigente")
    val saldoVigente: Int = 0,

    @SerializedName("id_veterinario")
    val idVeterinario: Int? = null,

    @SerializedName("nombre_veterinario")
    val nombreVeterinario: String? = null
) : Serializable

data class AlertaLoteResponse(
    @SerializedName("id_kit_lote")
    val idKitLote: Int?,

    @SerializedName("id_inventario")
    val idInventario: Int?,

    @SerializedName("id_medicamento")
    val idMedicamento: Int,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("cantidad")
    val cantidad: Int,

    @SerializedName("fecha_vencimiento")
    val fechaVencimiento: String?,

    @SerializedName("dias_vencido")
    val diasVencido: Int? = null,

    @SerializedName("dias_restantes")
    val diasRestantes: Int? = null,

    @SerializedName("id_veterinario")
    val idVeterinario: Int? = null,

    @SerializedName("nombre_veterinario")
    val nombreVeterinario: String? = null
) : Serializable

data class AlertasKitResponse(
    @SerializedName("agotados")
    val agotados: List<AlertaItemResponse> = emptyList(),

    @SerializedName("criticos")
    val criticos: List<AlertaItemResponse> = emptyList(),

    @SerializedName("bajos")
    val bajos: List<AlertaItemResponse> = emptyList(),

    @SerializedName("vencidos")
    val vencidos: List<AlertaLoteResponse> = emptyList(),

    @SerializedName("proximos_a_vencer")
    val proximosAVencer: List<AlertaLoteResponse> = emptyList()
) : Serializable

// =========================================================================
// 8. CATÁLOGO GENERAL DE MEDICAMENTOS CBA
// =========================================================================

data class MedicamentoCatalogoItem(
    @SerializedName("id_medicamento")
    val idMedicamento: Int,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("principio_activo")
    val principioActivo: String? = null,

    @SerializedName("presentacion")
    val presentacion: String? = null,

    @SerializedName("concentracion")
    val concentracion: String? = null,

    @SerializedName("tipo")
    val tipo: String? = null,

    @SerializedName("activo")
    val activo: Boolean = true
) : Serializable

