package com.example.petvetericano.ui.reporte

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.example.petvetericano.R
import com.example.petvetericano.databinding.BottomSheetPosiblesDuplicadosBinding
import com.example.petvetericano.models.CandidatoDuplicado
import com.example.petvetericano.models.NivelCercania
import com.example.petvetericano.models.NivelSimilitud
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class BottomSheetPosiblesDuplicados : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPosiblesDuplicadosBinding? = null
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
        _binding = BottomSheetPosiblesDuplicadosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED

        binding.btnSiMismoAnimal.setOnClickListener {
            setBotonesHabilitados(false)
            viewModel.responderCandidatoSi()
        }

        binding.btnNoOtroAnimal.setOnClickListener {
            setBotonesHabilitados(false)
            viewModel.responderCandidatoNo()
        }

        var ultimoIndicePintado: Int? = null
        var ultimoCandidatoPintado: CandidatoDuplicado? = null

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val candidatos = state.candidatosDuplicados
                    if (candidatos.isEmpty()) {
                        dismissAllowingStateLoss()
                        return@collect
                    }

                    val indice = state.indiceCandidatoDuplicado.coerceIn(0, candidatos.lastIndex)
                    val candidato = candidatos[indice]
                    if (indice != ultimoIndicePintado || candidato != ultimoCandidatoPintado) {
                        ultimoIndicePintado = indice
                        ultimoCandidatoPintado = candidato
                        renderizarCandidato(candidato, indice, candidatos.size)
                        setBotonesHabilitados(true)
                    }
                }
            }
        }
    }

    private fun renderizarCandidato(
        candidato: CandidatoDuplicado,
        indice: Int,
        total: Int
    ) {
        binding.tvDupContador.text = getString(R.string.reporte_dup_contador, indice + 1, total)

        val fotoUrl = candidato.fotoSegura
        if (fotoUrl == null) {
            binding.ivDupFoto.setImageDrawable(null)
            binding.ivDupFoto.visibility = View.GONE
            binding.layoutDupSinFoto.visibility = View.VISIBLE
        } else {
            binding.layoutDupSinFoto.visibility = View.GONE
            binding.ivDupFoto.visibility = View.VISIBLE
            Glide.with(this)
                .load(fotoUrl)
                .centerCrop()
                .placeholder(R.drawable.baseline_pets_24)
                .error(R.drawable.baseline_pets_24)
                .into(binding.ivDupFoto)
        }

        val muestraSimilitud = when (candidato.nivelSimilitud) {
            NivelSimilitud.ALTA -> {
                binding.chipDupSimilitud.setText(R.string.reporte_dup_chip_similitud_alta)
                binding.chipDupSimilitud.setChipBackgroundColorResource(R.color.radix_amber_bg)
                binding.chipDupSimilitud.setChipStrokeColorResource(R.color.radix_amber_border)
                binding.chipDupSimilitud.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.radix_amber_text)
                )
                binding.chipDupSimilitud.visibility = View.VISIBLE
                true
            }
            NivelSimilitud.MEDIA -> {
                binding.chipDupSimilitud.setText(R.string.reporte_dup_chip_similitud_media)
                binding.chipDupSimilitud.setChipBackgroundColorResource(R.color.chip_bg_neutral)
                binding.chipDupSimilitud.setChipStrokeColorResource(R.color.chip_stroke_neutral)
                binding.chipDupSimilitud.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_secondary)
                )
                binding.chipDupSimilitud.visibility = View.VISIBLE
                true
            }
            NivelSimilitud.NO_DISPONIBLE -> {
                binding.chipDupSimilitud.visibility = View.GONE
                false
            }
        }

        val muestraCercania = when (candidato.nivelCercania) {
            NivelCercania.MUY_CERCA -> {
                binding.chipDupCercania.setText(R.string.reporte_dup_chip_cercania_muy_cerca)
                binding.chipDupCercania.setChipBackgroundColorResource(R.color.primary_light)
                binding.chipDupCercania.setChipStrokeColorResource(R.color.card_stroke)
                binding.chipDupCercania.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
                binding.chipDupCercania.visibility = View.VISIBLE
                true
            }
            NivelCercania.CERCA -> {
                binding.chipDupCercania.setText(R.string.reporte_dup_chip_cercania_cerca)
                binding.chipDupCercania.setChipBackgroundColorResource(R.color.chip_bg_neutral)
                binding.chipDupCercania.setChipStrokeColorResource(R.color.chip_stroke_neutral)
                binding.chipDupCercania.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_secondary)
                )
                binding.chipDupCercania.visibility = View.VISIBLE
                true
            }
            NivelCercania.NO_DISPONIBLE -> {
                binding.chipDupCercania.visibility = View.GONE
                false
            }
        }

        binding.chipGroupDupEtiquetas.visibility =
            if (muestraSimilitud || muestraCercania) View.VISIBLE else View.GONE
    }

    private fun setBotonesHabilitados(habilitados: Boolean) {
        binding.btnSiMismoAnimal.isEnabled = habilitados
        binding.btnNoOtroAnimal.isEnabled = habilitados
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        viewModel.cancelarAdvertenciaDuplicados()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "BottomSheetPosiblesDuplicados"
    }
}
