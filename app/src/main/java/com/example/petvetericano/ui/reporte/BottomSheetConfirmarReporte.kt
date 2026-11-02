package com.example.petvetericano.ui.reporte

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.petvetericano.R
import com.example.petvetericano.databinding.BottomSheetConfirmarReporteBinding
import com.example.petvetericano.databinding.ItemEvidenciaMiniaturaBinding
import com.example.petvetericano.offline.ReporteEncolador
import com.example.petvetericano.reportar_peticion
import com.example.petvetericano.reporte_enviado
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.io.File

class BottomSheetConfirmarReporte : BottomSheetDialogFragment() {

    private var _binding: BottomSheetConfirmarReporteBinding? = null
    private val binding get() = _binding!!

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
                Glide.with(this)
                    .load(File(item.localPath))
                    .override(160, 160)
                    .centerCrop()
                    .placeholder(R.drawable.baseline_pets_24)
                    .error(R.drawable.baseline_pets_24)
                    .into(itemBinding.ivMiniatura)

                if (item.esVideo) {
                    itemBinding.layoutVideoOverlay.visibility = View.VISIBLE
                } else {
                    itemBinding.layoutVideoOverlay.visibility = View.GONE
                }
                binding.llMiniaturasContainer.addView(itemBinding.root)
            }
        }

        val hayConexion = hayConexionInternet(requireContext())
        binding.layoutAvisoSinConexion.visibility = if (hayConexion) View.GONE else View.VISIBLE

        binding.btnEditar.setOnClickListener {
            dismiss()
        }

        binding.btnEnviarReporte.setOnClickListener {
            enviarReporte()
        }
    }

    private fun enviarReporte() {
        val state = viewModel.uiState.value
        val tipo = state.tipoSeleccionado

        if (tipo == null || tipo.idTipo <= 0) {
            Toast.makeText(requireContext(), R.string.reporte_ux_error_tipo_no_definido, Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnEnviarReporte.isEnabled = false
        binding.btnEditar.isEnabled = false
        isCancelable = false
        (dialog as? BottomSheetDialog)?.behavior?.isHideable = false

        Toast.makeText(requireContext(), R.string.reporte_offline_guardando_reporte, Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext().applicationContext
            val latitud = state.latitud ?: 0.0
            val longitud = state.longitud ?: 0.0
            val dirFinal = when {
                !state.direccionTexto.isNullOrBlank() -> state.direccionTexto
                latitud != 0.0 && longitud != 0.0 -> getString(
                    R.string.reporte_offline_direccion_coordenadas,
                    latitud,
                    longitud
                )
                else -> getString(R.string.reporte_offline_ubicacion_no_seleccionada)
            }

            val evidenciasList = state.evidencias
                .filter { File(it.localPath).exists() }
                .map { Pair(it.localPath, it.esVideo) }

            val idLocal = ReporteEncolador.encolar(
                context = context,
                idTipo = tipo.idTipo,
                tipoNombre = tipo.nombreRemoto,
                descripcion = state.descripcion.trim(),
                direccion = dirFinal,
                latitud = latitud,
                longitud = longitud,
                evidencias = evidenciasList
            )

            if (idLocal != null && idLocal > 0L) {
                (activity as? reportar_peticion)?.marcarReporteEncoladoExitoso()

                viewModel.limpiarBorrador()

                context.getSharedPreferences("MisReportesPrefs", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("MapaOffline", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("ReporteOfflineMultimedia", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("ReporteOffline", Context.MODE_PRIVATE).edit().clear().apply()

                try {
                    val draftDir = File(context.filesDir, "reporte_drafts")
                    draftDir.listFiles()?.forEach { f ->
                        if (f.isFile) f.delete()
                    }
                } catch (_: Exception) {
                }

                val conn = hayConexionInternet(context)
                val act = activity
                dismissAllowingStateLoss()
                if (act != null) {
                    val nuevoIntent = Intent(act, reporte_enviado::class.java).apply {
                        putExtra("ID_LOCAL", idLocal)
                        putExtra("HAY_CONEXION", conn)
                    }
                    act.startActivity(nuevoIntent)
                    act.finish()
                }
            } else {
                binding.btnEnviarReporte.isEnabled = true
                binding.btnEditar.isEnabled = true
                isCancelable = true
                (dialog as? BottomSheetDialog)?.behavior?.isHideable = true
                Toast.makeText(context, R.string.reporte_offline_error_guardar_reporte, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hayConexionInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "BottomSheetConfirmarReporte"
    }
}
