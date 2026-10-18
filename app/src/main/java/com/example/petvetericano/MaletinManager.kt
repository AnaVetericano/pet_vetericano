package com.example.petvetericano

import android.content.Context
import android.content.SharedPreferences
import com.example.petvetericano.models.MedicamentoMaletin
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Gestor del inventario del Maletín de Medicamentos asignado al veterinario.
 * Gestiona el stock local, las deducciones automáticas desde el acta de atención
 * y provee métricas para el Dashboard y la pantalla de Maletín.
 */
class MaletinManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("PetVetericanoMaletinPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_MEDICAMENTOS = "MALETIN_MEDICAMENTOS_LIST"

        @Volatile
        private var instance: MaletinManager? = null

        fun getInstance(context: Context): MaletinManager {
            return instance ?: synchronized(this) {
                instance ?: MaletinManager(context).also { instance = it }
            }
        }
    }

    /**
     * Obtiene la lista completa de medicamentos asignados al maletín.
     * Si es la primera vez que se accede, precarga el inventario médico veterinario.
     */
    fun getMedicamentos(): List<MedicamentoMaletin> {
        val json = prefs.getString(KEY_MEDICAMENTOS, null)
        return if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<MedicamentoMaletin>>() {}.type
                gson.fromJson<List<MedicamentoMaletin>>(json, type) ?: getListaInicialMedicamentos()
            } catch (_: Exception) {
                getListaInicialMedicamentos()
            }
        } else {
            val inicial = getListaInicialMedicamentos()
            guardarMedicamentos(inicial)
            inicial
        }
    }

    /**
     * Obtiene un medicamento por su ID único.
     */
    fun getMedicamento(id: String): MedicamentoMaletin? {
        return getMedicamentos().find { it.id == id }
    }

    /**
     * Guarda la lista actualizada de medicamentos.
     */
    private fun guardarMedicamentos(lista: List<MedicamentoMaletin>) {
        val json = gson.toJson(lista)
        prefs.edit().putString(KEY_MEDICAMENTOS, json).apply()
    }

    /**
     * Descuenta una cantidad específica del stock disponible de un medicamento.
     * @return true si se pudo descontar, false si no hay suficiente stock.
     */
    @Synchronized
    fun descontarStock(id: String, cantidad: Double): Boolean {
        if (cantidad <= 0) return false
        val lista = getMedicamentos().toMutableList()
        val index = lista.indexOfFirst { it.id == id }
        if (index != -1) {
            val med = lista[index]
            if (med.cantidadDisponible >= cantidad) {
                val nuevaCantidad = String.format(java.util.Locale.US, "%.2f", med.cantidadDisponible - cantidad).toDouble()
                lista[index] = med.copy(cantidadDisponible = maxOf(0.0, nuevaCantidad))
                guardarMedicamentos(lista)
                return true
            }
        }
        return false
    }

    /**
     * Revierte / devuelve una cantidad descontada (en caso de que el usuario remueva el ítem).
     */
    @Synchronized
    fun devolverStock(id: String, cantidad: Double) {
        if (cantidad <= 0) return
        val lista = getMedicamentos().toMutableList()
        val index = lista.indexOfFirst { it.id == id }
        if (index != -1) {
            val med = lista[index]
            val nuevaCantidad = String.format(java.util.Locale.US, "%.2f", med.cantidadDisponible + cantidad).toDouble()
            lista[index] = med.copy(cantidadDisponible = minOf(med.cantidadInicial, nuevaCantidad))
            guardarMedicamentos(lista)
        }
    }

    /**
     * Retorna el número de referencias de medicamentos con stock disponible (> 0).
     */
    fun getTotalMedicamentosDisponibles(): Int {
        return getMedicamentos().count { it.cantidadDisponible > 0 }
    }

    /**
     * Retorna el total de referencias en el maletín.
     */
    fun getTotalReferencias(): Int {
        return getMedicamentos().size
    }

    /**
     * Calcula el estado general del maletín (Stock disponible, Stock bajo, etc.).
     */
    fun getEstadoStock(): String {
        val meds = getMedicamentos()
        if (meds.isEmpty()) return "Sin asignación"

        val agotados = meds.count { it.cantidadDisponible <= 0 }
        val bajos = meds.count { it.cantidadDisponible > 0 && (it.cantidadDisponible / it.cantidadInicial) <= 0.25 }

        return when {
            agotados > 0 -> "$agotados agotados"
            bajos > 0 -> "$bajos stock bajo"
            else -> "Stock disponible"
        }
    }

    /**
     * Reinicia el inventario al stock inicial asignado (ideal para recargar el maletín en campo o pruebas).
     */
    fun reiniciarStock() {
        val inicial = getListaInicialMedicamentos()
        guardarMedicamentos(inicial)
    }

    /**
     * Inventario inicial médico veterinario realista para atención en campo.
     */
    private fun getListaInicialMedicamentos(): List<MedicamentoMaletin> {
        return listOf(
            MedicamentoMaletin(
                id = "MED-001",
                nombre = "Meloxicam 0.5%",
                concentracion = "5 mg/ml",
                categoria = "Analgésico / AINE",
                presentacion = "Frasco ampolla 20 ml",
                unidadMedida = "ml",
                cantidadDisponible = 18.5,
                cantidadInicial = 20.0,
                lote = "LT-2026-M01",
                fechaVencimiento = "04/2027",
                viaRecomendada = "SC / IV / Oral",
                descripcionUso = "Antiinflamatorio y analgésico no esteroideo para control del dolor e inflamación en caninos y felinos."
            ),
            MedicamentoMaletin(
                id = "MED-002",
                nombre = "Enrofloxacina 10%",
                concentracion = "100 mg/ml",
                categoria = "Antibiótico",
                presentacion = "Frasco inyectable 50 ml",
                unidadMedida = "ml",
                cantidadDisponible = 42.0,
                cantidadInicial = 50.0,
                lote = "LT-2026-E12",
                fechaVencimiento = "08/2027",
                viaRecomendada = "SC / IM",
                descripcionUso = "Fluoroquinolona de amplio espectro bactericida para infecciones dérmicas, respiratorias y gastrointestinales."
            ),
            MedicamentoMaletin(
                id = "MED-003",
                nombre = "Tramadol Clorhidrato",
                concentracion = "50 mg/ml",
                categoria = "Analgésico / AINE",
                presentacion = "Ampolla inyectable 20 ml",
                unidadMedida = "ml",
                cantidadDisponible = 15.0,
                cantidadInicial = 20.0,
                lote = "LT-2026-T44",
                fechaVencimiento = "11/2026",
                viaRecomendada = "SC / IM / IV",
                descripcionUso = "Analgésico opioide para dolor agudo, politraumatismos y procedimientos de emergencia en campo."
            ),
            MedicamentoMaletin(
                id = "MED-004",
                nombre = "Dexametasona 2 mg",
                concentracion = "2 mg/ml",
                categoria = "Corticoide",
                presentacion = "Frasco inyectable 50 ml",
                unidadMedida = "ml",
                cantidadDisponible = 45.0,
                cantidadInicial = 50.0,
                lote = "LT-2026-D09",
                fechaVencimiento = "01/2028",
                viaRecomendada = "IM / IV / SC",
                descripcionUso = "Glucocorticoide de alta potencia para reacciones anafilácticas, shock y procesos inflamatorios agudos."
            ),
            MedicamentoMaletin(
                id = "MED-005",
                nombre = "Oxitetraciclina Spray",
                concentracion = "Polvo en aerosol",
                categoria = "Antibiótico",
                presentacion = "Frasco spray 200 ml",
                unidadMedida = "ml",
                cantidadDisponible = 180.0,
                cantidadInicial = 200.0,
                lote = "LT-2026-O88",
                fechaVencimiento = "05/2027",
                viaRecomendada = "Tópica",
                descripcionUso = "Cicatrizante antiséptico y protector dérmico con tinte violeta para heridas expuestas y miasis."
            ),
            MedicamentoMaletin(
                id = "MED-006",
                nombre = "Lactato de Ringer",
                concentracion = "Solución electrolítica",
                categoria = "Fluidoterapia",
                presentacion = "Bolsa infusión 500 ml",
                unidadMedida = "ml",
                cantidadDisponible = 500.0,
                cantidadInicial = 500.0,
                lote = "LT-2026-R03",
                fechaVencimiento = "09/2028",
                viaRecomendada = "IV / SC",
                descripcionUso = "Fluido cristaloide isotónico para rehidratación urgente y estabilización hemodinámica."
            ),
            MedicamentoMaletin(
                id = "MED-007",
                nombre = "Praziquantel + Pirantel",
                concentracion = "50 mg / 144 mg",
                categoria = "Antiparasitario",
                presentacion = "Blíster x 30 comprimidos",
                unidadMedida = "comprimidos",
                cantidadDisponible = 26.0,
                cantidadInicial = 30.0,
                lote = "LT-2026-P55",
                fechaVencimiento = "03/2027",
                viaRecomendada = "Oral",
                descripcionUso = "Antiparasitario interno oral de amplio espectro contra tenias, nematodos y ascaris."
            ),
            MedicamentoMaletin(
                id = "MED-008",
                nombre = "Ivermectina 1%",
                concentracion = "10 mg/ml",
                categoria = "Antiparasitario",
                presentacion = "Frasco inyectable 50 ml",
                unidadMedida = "ml",
                cantidadDisponible = 38.0,
                cantidadInicial = 50.0,
                lote = "LT-2026-I17",
                fechaVencimiento = "10/2027",
                viaRecomendada = "SC",
                descripcionUso = "Endectocida para tratamiento de sarna sarcóptica, demodécica y parásitos externos."
            ),
            MedicamentoMaletin(
                id = "MED-009",
                nombre = "Cefalexina 500 mg",
                concentracion = "500 mg",
                categoria = "Antibiótico",
                presentacion = "Caja x 40 comprimidos",
                unidadMedida = "comprimidos",
                cantidadDisponible = 32.0,
                cantidadInicial = 40.0,
                lote = "LT-2026-C23",
                fechaVencimiento = "02/2027",
                viaRecomendada = "Oral",
                descripcionUso = "Cefalosporina oral para piodermas, heridas infectadas e infecciones de tejidos blandos."
            ),
            MedicamentoMaletin(
                id = "MED-010",
                nombre = "Furosemida 50 mg",
                concentracion = "50 mg/ml",
                categoria = "Cardiología / Renal",
                presentacion = "Ampolla 20 ml",
                unidadMedida = "ml",
                cantidadDisponible = 16.0,
                cantidadInicial = 20.0,
                lote = "LT-2026-F91",
                fechaVencimiento = "07/2027",
                viaRecomendada = "IV / IM",
                descripcionUso = "Diurético de asa para edema pulmonar cardiogénico o ascitis severa."
            )
        )
    }
}
