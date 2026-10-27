package com.example.petvetericano.offline

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.ActaAtencionActivity
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.databinding.BottomSheetActaColaBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActaColaBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetActaColaBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ActaColaAdapter
    private var idVeterinario: Int = -1

    companion object {
        const val TAG = "ActaColaBottomSheet"
        const val RESULT_KEY = "acta_cola_cambio"
        private const val ARG_ID_VETERINARIO = "arg_id_veterinario"

        fun newInstance(idVeterinario: Int = -1): ActaColaBottomSheet {
            return ActaColaBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_ID_VETERINARIO, idVeterinario)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val idArg = arguments?.getInt(ARG_ID_VETERINARIO, -1) ?: -1
        idVeterinario = if (idArg > 0) {
            idArg
        } else {
            SharedPreferencesManager(requireContext()).getUserId()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetActaColaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ActaColaAdapter(
            onReenviar = { item -> reenviarItem(item) },
            onRevisar = { item -> corregirItem(item) }
        )
        binding.rvColaActas.adapter = adapter

        binding.btnColaReenviarTodas.setOnClickListener {
            reenviarTodas()
        }

        cargarLista()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        notificarCambioDashboard()
    }

    private fun notificarCambioDashboard() {
        try {
            if (isAdded) {
                parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
            }
        } catch (_: Exception) {
            // Protección defensiva si se invoca durante el desacople del FragmentManager
        }
    }

    private fun cargarLista() {
        if (idVeterinario <= 0) {
            mostrarVacio()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val appContext = requireContext().applicationContext
            val items = ActaLocalRepository.getInstance(appContext).obtenerEnCola(idVeterinario)

            withContext(Dispatchers.Main) {
                if (!isAdded || _binding == null) return@withContext
                actualizarUi(items)
            }
        }
    }

    private fun actualizarUi(items: List<ActaColaItem>) {
        if (items.isEmpty()) {
            mostrarVacio()
        } else {
            binding.tvColaVacio.visibility = View.GONE
            binding.rvColaActas.visibility = View.VISIBLE
            val hayReenviables = items.any { item ->
                when (item.estado) {
                    EstadoSincronizacionActa.PENDIENTE_ENVIO,
                    EstadoSincronizacionActa.ENVIANDO,
                    EstadoSincronizacionActa.ERROR_REINTENTABLE -> true
                    EstadoSincronizacionActa.REQUIERE_ATENCION -> item.ultimoCodigoHttp == 401
                    else -> false
                }
            }
            binding.btnColaReenviarTodas.visibility = if (hayReenviables) View.VISIBLE else View.GONE
            adapter.submitList(items)
        }
    }

    private fun mostrarVacio() {
        binding.tvColaVacio.visibility = View.VISIBLE
        binding.rvColaActas.visibility = View.GONE
        binding.btnColaReenviarTodas.visibility = View.GONE
        adapter.submitList(emptyList())
    }

    private fun reenviarItem(item: ActaColaItem) {
        if (idVeterinario <= 0) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val appContext = requireContext().applicationContext
            val resultado = ActaSyncScheduler.reenviarAhora(appContext, item.idLocal, idVeterinario)

            withContext(Dispatchers.Main) {
                if (!isAdded || _binding == null) return@withContext

                val mensajeRes = when (resultado) {
                    ResultadoReenvio.ENCOLADA -> R.string.acta_offline_cola_reenvio_ok
                    ResultadoReenvio.YA_EN_PROCESO -> R.string.acta_offline_cola_ya_en_proceso
                    ResultadoReenvio.REQUIERE_REVISION -> R.string.acta_offline_cola_requiere_revision_msg
                    ResultadoReenvio.NO_ENCONTRADA -> R.string.acta_offline_cola_no_encontrada
                }
                Toast.makeText(requireContext(), getString(mensajeRes), Toast.LENGTH_SHORT).show()

                notificarCambioDashboard()
                cargarLista()
            }
        }
    }

    private fun reenviarTodas() {
        if (idVeterinario <= 0) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val appContext = requireContext().applicationContext
            val totalEncoladas = ActaSyncScheduler.reenviarTodas(appContext, idVeterinario)

            withContext(Dispatchers.Main) {
                if (!isAdded || _binding == null) return@withContext

                if (totalEncoladas > 0) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.acta_offline_cola_reenvio_todas_ok, totalEncoladas),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                notificarCambioDashboard()
                cargarLista()
            }
        }
    }

    private fun corregirItem(item: ActaColaItem) {
        val intent = Intent(requireContext(), ActaAtencionActivity::class.java).apply {
            putExtra("PETICION_ID", item.idPeticion)
        }
        startActivity(intent)
        notificarCambioDashboard()
        dismiss()
    }
}
