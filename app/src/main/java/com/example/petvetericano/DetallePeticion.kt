package com.example.petvetericano

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.bumptech.glide.Glide
import com.example.petvetericano.databinding.ActivityDetallePeticionBinding
import com.example.petvetericano.models.PeticionListResponse
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.offline.NetworkUtils
import com.example.petvetericano.offline.PeticionesCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class DetallePeticion : AppCompatActivity() {

    private lateinit var binding: ActivityDetallePeticionBinding
    private var peticionId: Int = -1
    private var direccionActual: String = ""
    private var peticionTieneActa: Boolean? = null
    private var datosCargados: Boolean = false
    private var cargaRemotaExitosa: Boolean = false
    private var primeraVezOnResume: Boolean = true
    private var abriendoActa: Boolean = false

    private val updateStatusLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            if (peticionId != -1) {
                obtenerDetallePeticion(peticionId)
            }
        }
    }

    private val actaLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        abriendoActa = false
        if (result.resultCode == RESULT_OK) {
            if (peticionId != -1) {
                obtenerDetallePeticion(peticionId)
            }
        }
    }

    private val corregirDireccionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val nuevaDireccion = result.data?.getStringExtra("NUEVA_DIRECCION")
            if (!nuevaDireccion.isNullOrBlank()) {
                direccionActual = nuevaDireccion
                binding.tvUbicacionTexto.text = nuevaDireccion
                Toast.makeText(this, getString(R.string.acta_offline_direccion_actualizada_ok), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDetallePeticionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        peticionId = intent.getIntExtra("PETICION_ID", -1)

        binding.btnBack.setOnClickListener { finish() }

        // Listener de btnAtender registrado SIEMPRE en onCreate
        binding.btnAtender.setOnClickListener {
            if (abriendoActa) return@setOnClickListener
            if (!datosCargados) {
                Toast.makeText(this, getString(R.string.acta_offline_detalle_sin_datos), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (peticionTieneActa == true) {
                mostrarDialogoPeticionYaAtendida()
            } else {
                abriendoActa = true
                abrirActaAtencion()
            }
        }

        if (peticionId != -1) {
            obtenerDetallePeticion(peticionId)
        } else {
            Toast.makeText(this, getString(R.string.acta_offline_id_peticion_invalido), Toast.LENGTH_SHORT).show()
        }

        // CONFIGURACIÓN PARA ACTUALIZAR ESTADO DEL CASO (Desde botón o desde el badge)
        val abrirActualizarEstado = {
            if (!NetworkUtils.hayRedDisponible(this)) {
                Toast.makeText(this, getString(R.string.acta_offline_requiere_conexion), Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, UpdateStatusActivity::class.java).apply {
                    putExtra("PETICION_ID", peticionId)
                    putExtra("PETICION_CODIGO", binding.tvCodigoPeticion.text.toString())
                    putExtra("ESTADO_ACTUAL", binding.tvBadgeEstado.text.toString())
                }
                updateStatusLauncher.launch(intent)
            }
        }

        binding.btnActualizarEstado.setOnClickListener { abrirActualizarEstado() }
        binding.tvBadgeEstado.setOnClickListener { abrirActualizarEstado() }

        // CONFIGURACIÓN DEL BOTÓN CORREGIR DIRECCIÓN CON ALERTA DE CONFIRMACIÓN (Material Design 3)
        binding.btnCorregirDireccion.setOnClickListener {
            if (!NetworkUtils.hayRedDisponible(this)) {
                Toast.makeText(this, getString(R.string.acta_offline_requiere_conexion), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.outline_add_location_alt_24)
                .setTitle(R.string.acta_offline_dialog_corregir_dir_titulo)
                .setMessage(R.string.acta_offline_dialog_corregir_dir_mensaje)
                .setPositiveButton(R.string.acta_offline_dialog_corregir_dir_positivo) { dialog, _ ->
                    dialog.dismiss()
                    val intent = Intent(this, CorregirDireccionActivity::class.java).apply {
                        putExtra("DIRECCION_ACTUAL", direccionActual)
                        putExtra("PETICION_ID", peticionId)
                    }
                    corregirDireccionLauncher.launch(intent)
                }
                .setNegativeButton(R.string.acta_offline_dialog_cancelar) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        binding.btnVerRuta.setOnClickListener {
            if (!NetworkUtils.hayRedDisponible(this)) {
                Toast.makeText(this, getString(R.string.acta_offline_requiere_conexion), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (direccionActual.isNotEmpty()) {
                val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(direccionActual)}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(direccionActual)}"))
                    startActivity(browserIntent)
                }
            } else {
                Toast.makeText(this, getString(R.string.acta_offline_sin_direccion), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        abriendoActa = false
        if (primeraVezOnResume) {
            primeraVezOnResume = false
            return
        }
        if (!cargaRemotaExitosa && peticionId != -1 && NetworkUtils.hayRedDisponible(this)) {
            obtenerDetallePeticion(peticionId)
        }
    }

    private fun obtenerDetallePeticion(id: Int) {
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        val idVet = prefs.getUserId()

        lifecycleScope.launch(Dispatchers.IO) {
            var remoteSuccess = false

            // 1) Fuente remota
            if (NetworkUtils.hayRedDisponible(this@DetallePeticion) && token.isNotEmpty()) {
                try {
                    val response = RetrofitClient.apiService.listarPeticiones("Bearer $token")
                    if (response.isSuccessful) {
                        val lista = response.body()
                        if (!lista.isNullOrEmpty() && idVet > 0) {
                            PeticionesCacheManager.guardar(this@DetallePeticion, idVet, lista)
                        }

                        val peticion = lista?.firstOrNull { it.id_peticion == id }
                        withContext(Dispatchers.Main) {
                            if (peticion != null) {
                                cargaRemotaExitosa = true
                                remoteSuccess = true
                                bindData(peticion)
                            } else {
                                Toast.makeText(this@DetallePeticion, getString(R.string.acta_offline_peticion_no_encontrada), Toast.LENGTH_SHORT).show()
                            }
                        }
                        if (remoteSuccess) return@launch
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@DetallePeticion, getString(R.string.acta_offline_error_cargar_detalle), Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                } catch (_: Exception) {
                    // Excepción de red: continuar a caché y extras
                }
            }

            // 2) Fuente caché local
            val cachedPeticion = if (idVet > 0) {
                PeticionesCacheManager.obtenerPorId(this@DetallePeticion, idVet, id)
            } else null

            if (cachedPeticion != null) {
                withContext(Dispatchers.Main) {
                    bindData(cachedPeticion)
                    Toast.makeText(this@DetallePeticion, getString(R.string.acta_offline_detalle_modo_sin_conexion), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // 3) Fuente extras del Intent
            val tieneExtras = withContext(Dispatchers.Main) {
                aplicarDatosDesdeExtras()
            }

            if (tieneExtras) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetallePeticion, getString(R.string.acta_offline_detalle_modo_sin_conexion), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // 4) Si no hay datos disponibles
            withContext(Dispatchers.Main) {
                Toast.makeText(this@DetallePeticion, getString(R.string.acta_offline_detalle_sin_datos), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun aplicarDatosDesdeExtras(): Boolean {
        val radicado = intent.getStringExtra("PETICION_RADICADO")
        val estado = intent.getStringExtra("PETICION_ESTADO")
        val tipo = intent.getStringExtra("PETICION_TIPO")
        val fecha = intent.getStringExtra("PETICION_FECHA")
        val descripcion = intent.getStringExtra("PETICION_DESCRIPCION")
        val direccion = intent.getStringExtra("DIRECCION_ACTUAL")
        val tieneActaExtra = if (intent.hasExtra("PETICION_TIENE_ACTA")) {
            intent.getBooleanExtra("PETICION_TIENE_ACTA", false)
        } else null

        if (radicado.isNullOrBlank() && estado.isNullOrBlank() && tipo.isNullOrBlank() && direccion.isNullOrBlank() && descripcion.isNullOrBlank()) {
            return false
        }

        direccionActual = direccion.orEmpty()
        val estadoActual = estado?.takeIf { it.isNotBlank() } ?: getString(R.string.acta_offline_sin_estado)
        binding.tvCodigoPeticion.text = radicado?.takeIf { it.isNotBlank() } ?: getString(R.string.acta_offline_na)
        binding.tvBadgeEstado.text = estadoActual
        actualizarEstiloBadge(estadoActual)

        binding.tvMotivoAnimal.text = tipo?.trim()?.takeIf { it.isNotEmpty() } ?: getString(R.string.acta_offline_sin_motivo)
        binding.tvFechaAsignada.text = formatearFecha(fecha)
        binding.tvObservaciones.text = descripcion?.trim()?.takeIf { it.isNotEmpty() } ?: getString(R.string.acta_offline_sin_observaciones)
        binding.tvUbicacionTexto.text = direccion?.trim()?.takeIf { it.isNotEmpty() } ?: getString(R.string.acta_offline_ubicacion_no_registrada)

        binding.cardFotoEvidencia.visibility = View.GONE

        val stLower = estadoActual.lowercase()
        val tieneActa = tieneActaExtra == true ||
            stLower.contains("tratamiento") ||
            stLower.contains("observación") ||
            stLower.contains("observacion") ||
            stLower.contains("alta") ||
            stLower.contains("atendida") ||
            stLower.contains("resuelta") ||
            stLower.contains("fallecido") ||
            stLower.contains("transferido")

        actualizarBotonAtender(tieneActa)
        datosCargados = true
        return true
    }

    private fun bindData(peticion: PeticionListResponse) {
        direccionActual = peticion.ubicacion_direccion.orEmpty()

        val estadoActual = peticion.estado?.takeIf { it.isNotBlank() } ?: getString(R.string.acta_offline_sin_estado)
        binding.tvCodigoPeticion.text = peticion.numero_radicado?.takeIf { it.isNotBlank() } ?: getString(R.string.acta_offline_na)
        binding.tvBadgeEstado.text = estadoActual
        actualizarEstiloBadge(estadoActual)

        binding.tvMotivoAnimal.text = peticion.tipo?.trim()?.takeIf { it.isNotEmpty() } ?: getString(R.string.acta_offline_sin_motivo)
        binding.tvFechaAsignada.text = formatearFecha(peticion.fecha_asignacion)
        binding.tvObservaciones.text = peticion.descripcion?.trim()?.takeIf { it.isNotEmpty() } ?: getString(R.string.acta_offline_sin_observaciones)
        binding.tvUbicacionTexto.text = peticion.ubicacion_direccion?.trim()?.takeIf { it.isNotEmpty() } ?: getString(R.string.acta_offline_ubicacion_no_registrada)

        // GESTIÓN Y CARGA DE LA FOTO / VIDEO DESDE LA BASE DE DATOS
        val fotoUrl = peticion.foto
        if (!fotoUrl.isNullOrBlank()) {
            binding.cardFotoEvidencia.visibility = View.VISIBLE

            val urlFinal = if (fotoUrl.startsWith("http")) {
                fotoUrl
            } else {
                "https://tu-servidor.com$fotoUrl"
            }

            val esVideo = urlFinal.contains(".mp4", ignoreCase = true) || urlFinal.contains("/video/", ignoreCase = true)
            if (esVideo) {
                Glide.with(this)
                    .asBitmap()
                    .load(urlFinal)
                    .centerCrop()
                    .into(binding.ivPeticionFoto)

                binding.cardFotoEvidencia.setOnClickListener {
                    try {
                        val videoIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(urlFinal), "video/*")
                        }
                        startActivity(videoIntent)
                    } catch (_: Exception) {
                        Toast.makeText(this, getString(R.string.acta_offline_reproducir_video_error), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Glide.with(this)
                    .load(urlFinal)
                    .into(binding.ivPeticionFoto)
                binding.cardFotoEvidencia.setOnClickListener(null)
            }
        } else {
            binding.cardFotoEvidencia.visibility = View.GONE
        }

        // CONTROL DE ACTA ÚNICA: Validar si la petición ya fue atendida
        val stLower = estadoActual.lowercase()
        val tieneActa = peticion.tiene_acta == true ||
            stLower.contains("tratamiento") ||
            stLower.contains("observación") ||
            stLower.contains("observacion") ||
            stLower.contains("alta") ||
            stLower.contains("atendida") ||
            stLower.contains("resuelta") ||
            stLower.contains("fallecido") ||
            stLower.contains("transferido")

        actualizarBotonAtender(tieneActa)
        datosCargados = true
    }

    private fun actualizarBotonAtender(tieneActa: Boolean) {
        peticionTieneActa = tieneActa
        if (tieneActa) {
            binding.btnAtender.text = getString(R.string.acta_offline_btn_atencion_registrada)
            binding.btnAtender.setIconResource(R.drawable.ic_check)
            binding.btnAtender.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            binding.btnAtender.iconPadding = (8 * resources.displayMetrics.density).toInt()
            binding.btnAtender.iconTint = ContextCompat.getColorStateList(this, R.color.white)
            binding.btnAtender.setBackgroundColor(ContextCompat.getColor(this, R.color.success_dark))
        } else {
            binding.btnAtender.text = getString(R.string.acta_offline_btn_atender)
            binding.btnAtender.icon = null
            binding.btnAtender.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
        }
    }

    private fun mostrarDialogoPeticionYaAtendida() {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_info_circle)
            .setTitle(R.string.acta_offline_dialog_ya_atendida_titulo)
            .setMessage(R.string.acta_offline_dialog_ya_atendida_mensaje)
            .setPositiveButton(R.string.acta_offline_dialog_boton_aceptar, null)
            .show()
    }

    private fun abrirActaAtencion() {
        abriendoActa = true
        val intent = Intent(this, ActaAtencionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("PETICION_ID", peticionId)
            putExtra("DIRECCION_ACTUAL", direccionActual)
        }
        actaLauncher.launch(intent)
    }

    private fun actualizarEstiloBadge(estado: String) {
        val st = estado.lowercase()
        val (colorTextoRes, colorFondoRes) = when {
            st.contains("fallecido") || st.contains("urgente") -> {
                R.color.badge_urgente_text to R.color.badge_urgente_bg
            }
            st.contains("evaluación") || st.contains("evaluacion") || st.contains("pendiente") -> {
                R.color.badge_proceso_text to R.color.badge_proceso_bg
            }
            st.contains("tratamiento") -> {
                R.color.primary to R.color.primary_light
            }
            st.contains("observación") || st.contains("observacion") -> {
                R.color.badge_observacion_text to R.color.badge_observacion_bg
            }
            st.contains("alta") || st.contains("atendida") || st.contains("resuelta") -> {
                R.color.badge_alta_text to R.color.badge_alta_bg
            }
            st.contains("transferido") -> {
                R.color.badge_transferido_text to R.color.badge_transferido_bg
            }
            else -> {
                R.color.text_secondary to R.color.chip_bg_neutral
            }
        }

        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = resources.displayMetrics.density * 8
            setColor(ContextCompat.getColor(this@DetallePeticion, colorFondoRes))
        }

        binding.tvBadgeEstado.background = backgroundDrawable
        binding.tvBadgeEstado.setTextColor(ContextCompat.getColor(this@DetallePeticion, colorTextoRes))
    }

    private fun formatearFecha(fechaISO: String?): String {
        if (fechaISO.isNullOrBlank()) return getString(R.string.acta_offline_fecha_pendiente)
        return try {
            val limpio = fechaISO.substringBefore(".").take(19)
            val entrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val salida = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-ES"))
            entrada.parse(limpio)?.let { salida.format(it) } ?: limpio
        } catch (_: Exception) {
            fechaISO
        }
    }
}
