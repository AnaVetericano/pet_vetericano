package com.example.petvetericano.ui.reporte

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.petvetericano.R
import com.example.petvetericano.databinding.BottomSheetConfirmarReporteBinding
import com.example.petvetericano.databinding.ItemEvidenciaMiniaturaBinding
import com.example.petvetericano.reportar_peticion
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.io.File

class BottomSheetConfirmarReporte : BottomSheetDialogFragment() {

    private var _binding: BottomSheetConfirmarReporteBinding? = null
    private val binding get() = _binding!!
    private var dialogoDuplicadoActual: AlertDialog? = null

    private val viewModel: ReporteCiudadanoViewModel by lazy {
        ViewModelProvider(requireActivity())[ReporteCiudadanoViewModel::class.java]
    }

    override fun getTheme(): Int = R.style.ThemeOverlay_Vetericano_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetConfirmarReporteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED

        val state = viewModel.uiState.value

        val tipoNombre = state.tipoSeleccionado?.nombreRemoto
            ?: getString(R.string.reporte_offline_tipo_predeterminado)
        binding.tvTipoReporte.text = tipoNombre

        val direccion = when {
            !state.direccionTexto.isNullOrBlank() -> state.direccionTexto
            state.latitud != null && state.longitud != null -> getString(
                R.string.reporte_offline_direccion_coordenadas,
                state.latitud,
                state.longitud
            )
            else -> getString(R.string.reporte_offline_ubicacion_no_seleccionada)
        }
        binding.tvUbicacion.text = direccion

        binding.tvDescripcion.text = state.descripcion.trim()

        val cantidad = state.evidencias.size
        if (cantidad == 0) {
            binding.tvNumEvidencias.text = getString(R.string.reporte_offline_sin_evidencias)
            binding.hsvMiniaturas.visibility = View.GONE
        } else {
            val hayVideo = state.evidencias.any { it.esVideo }
            binding.tvNumEvidencias.text = when {
                cantidad == 1 && hayVideo -> getString(R.string.reporte_offline_un_video)
                cantidad == 1 && !hayVideo -> getString(R.string.reporte_offline_una_foto)
                cantidad == 2 && hayVideo -> getString(R.string.reporte_offline_foto_y_video)
                else -> getString(R.string.reporte_offline_evidencias_formato, cantidad)
            }
            binding.hsvMiniaturas.visibility = View.VISIBLE
            binding.llMiniaturasContainer.removeAllViews()
            for (item in state.evidencias) {
                val itemBinding = ItemEvidenciaMiniaturaBinding.inflate(
                    layoutInflater,
                    binding.llMiniaturasContainer,
                    false
                )
                itemBinding.btnQuitar.visibility = View.GONE
                val file = File(item.localPath)

                if (item.esVideo) {
                    Glide.with(this)
                        .asBitmap()
                        .load(file)
                        .apply(RequestOptions.frameOf(1_000_000L))
                        .override(160, 160)
                        .centerCrop()
                        .placeholder(R.drawable.baseline_pets_24)
                        .error(
                            Glide.with(this)
                                .asBitmap()
                                .load(file)
                                .override(160, 160)
                                .centerCrop()
                                .placeholder(R.drawable.baseline_pets_24)
                                .error(R.drawable.baseline_pets_24)
                        )
                        .into(itemBinding.ivMiniatura)

                    itemBinding.layoutVideoOverlay.visibility = View.VISIBLE
                    itemBinding.cardMiniatura.setOnClickListener {
                        (activity as? reportar_peticion)?.reproducirVideo(item)
                    }
                } else {
                    Glide.with(this)
                        .load(file)
                        .override(160, 160)
                        .centerCrop()
                        .placeholder(R.drawable.baseline_pets_24)
                        .error(R.drawable.baseline_pets_24)
                        .into(itemBinding.ivMiniatura)

                    itemBinding.layoutVideoOverlay.visibility = View.GONE
                    itemBinding.cardMiniatura.setOnClickListener {
                        VisorEvidenciaDialogFragment.nuevaInstancia(item.localPath)
                            .show(parentFragmentManager, VisorEvidenciaDialogFragment.TAG)
                    }
                }
                binding.llMiniaturasContainer.addView(itemBinding.root)
            }
        }

        val hayConexion = hayConexionInternet(requireContext())
        binding.layoutAvisoSinConexion.visibility = if (hayConexion) View.GONE else View.VISIBLE

        if (state.fotosCount > 0) {
            binding.cardConsentimientoFotos.visibility = View.VISIBLE
            binding.cbAutorizarFotosDuplicados.isChecked = state.autorizarFotosDuplicados
            binding.cbAutorizarFotosDuplicados.setOnCheckedChangeListener { _, isChecked ->
                viewModel.actualizarAutorizarFotosDuplicados(isChecked)
            }
        } else {
            binding.cardConsentimientoFotos.visibility = View.GONE
            if (state.autorizarFotosDuplicados) {
                viewModel.actualizarAutorizarFotosDuplicados(false)
            }
        }

        binding.btnEditarTipo.setOnClickListener {
            dismiss()
            (activity as? reportar_peticion)?.navegarDesdeModal(DestinoEdicion.TIPO)
        }

        binding.btnEditarUbicacion.setOnClickListener {
            dismiss()
            (activity as? reportar_peticion)?.navegarDesdeModal(DestinoEdicion.UBICACION)
        }

        binding.btnEditarDescripcion.setOnClickListener {
            dismiss()
            (activity as? reportar_peticion)?.navegarDesdeModal(DestinoEdicion.DESCRIPCION)
        }

        binding.btnEditarAdjuntos.setOnClickListener {
            dismiss()
            (activity as? reportar_peticion)?.navegarDesdeModal(DestinoEdicion.ADJUNTOS)
        }

        binding.btnEditar.setOnClickListener {
            dismiss()
        }

        binding.btnEnviarReporte.setOnClickListener {
            solicitarEnvioReporte()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    actualizarEstadoEnvio(
                        estaVerificandoDuplicados = uiState.estaVerificandoDuplicados,
                        estaEnviando = uiState.estaEnviando
                    )
                }
            }
        }
    }

    private fun actualizarEstadoEnvio(
        estaVerificandoDuplicados: Boolean,
        estaEnviando: Boolean
    ) {
        val ocupado = estaVerificandoDuplicados || estaEnviando
        binding.btnEnviarReporte.isEnabled = !ocupado
        binding.btnEditar.isEnabled = !ocupado
        binding.btnEditarTipo.isEnabled = !ocupado
        binding.btnEditarUbicacion.isEnabled = !ocupado
        binding.btnEditarDescripcion.isEnabled = !ocupado
        binding.btnEditarAdjuntos.isEnabled = !ocupado
        binding.cbAutorizarFotosDuplicados.isEnabled = !ocupado
        isCancelable = !ocupado
        (dialog as? BottomSheetDialog)?.behavior?.isHideable = !ocupado

        when {
            estaVerificandoDuplicados -> {
                binding.btnEnviarReporte.setText(R.string.duplicado_ciudadano_verificando)
                binding.tvEstadoVerificacionDuplicados.visibility = View.VISIBLE
                binding.pbEnvio.visibility = View.VISIBLE
            }
            estaEnviando -> {
                binding.btnEnviarReporte.setText(R.string.reporte_offline_guardando_reporte)
                binding.tvEstadoVerificacionDuplicados.visibility = View.GONE
                binding.pbEnvio.visibility = View.VISIBLE
            }
            else -> {
                binding.btnEnviarReporte.setText(R.string.btn_enviar_reporte)
                binding.tvEstadoVerificacionDuplicados.visibility = View.GONE
                binding.pbEnvio.visibility = View.GONE
            }
        }
    }

    private fun solicitarEnvioReporte() {
        val state = viewModel.uiState.value
        if (state.estaVerificandoDuplicados || state.estaEnviando) return
        val tipo = state.tipoSeleccionado

        if (tipo == null || tipo.idTipo <= 0) {
            Toast.makeText(requireContext(), R.string.reporte_ux_error_tipo_no_definido, Toast.LENGTH_SHORT).show()
            return
        }

        if (dialogoDuplicadoActual?.isShowing == true) return
        Log.i(TAG, "Iniciando verificacion preventiva de duplicados (fotos=${state.fotosCount}).")

        viewLifecycleOwner.lifecycleScope.launch {
            val alerta = viewModel.verificarDuplicadosAntesDeConfirmar(requireContext())
            if (!isAdded || _binding == null) {
                Log.w(TAG, "BottomSheet ya no esta adjunto al recibir resultado de duplicados.")
                return@launch
            }
            if (alerta != null) {
                Log.i(TAG, "Mostrando dialogo de posible duplicado al ciudadano.")
                dialogoDuplicadoActual?.dismiss()
                dialogoDuplicadoActual = DialogoDuplicadoCiudadanoHelper.mostrar(
                    context = requireContext(),
                    alerta = alerta,
                    onCancelarNuevoEnvio = {
                        Log.i(TAG, "Ciudadano respondio 'Si es': cancelando nuevo envio.")
                        (activity as? reportar_peticion)?.cancelarReportePorDuplicado()
                    },
                    onContinuarEnvio = {
                        Log.i(TAG, "Ciudadano respondio 'No es': continuando con envio del reporte.")
                        continuarConEnvioDefinitivo()
                    }
                )
            } else {
                Log.i(TAG, "Sin alerta de duplicado (o fail-open): continuando envio normal.")
                continuarConEnvioDefinitivo()
            }
        }
    }

    private fun continuarConEnvioDefinitivo() {
        Toast.makeText(requireContext(), R.string.reporte_offline_guardando_reporte, Toast.LENGTH_SHORT).show()
        viewModel.enviarReporte(requireContext())
    }

    private fun hayConexionInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onDestroyView() {
        dialogoDuplicadoActual?.dismiss()
        dialogoDuplicadoActual = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "BottomSheetConfirmarReporte"
    }
}
