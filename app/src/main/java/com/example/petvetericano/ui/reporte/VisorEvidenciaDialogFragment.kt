package com.example.petvetericano.ui.reporte

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.example.petvetericano.R
import com.example.petvetericano.databinding.DialogVisorEvidenciaBinding
import java.io.File

class VisorEvidenciaDialogFragment : DialogFragment() {

    private var _binding: DialogVisorEvidenciaBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogVisorEvidenciaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val localPath = arguments?.getString(ARG_LOCAL_PATH).orEmpty()
        if (localPath.isNotBlank()) {
            val file = File(localPath)
            Glide.with(this)
                .load(file)
                .fitCenter()
                .placeholder(R.drawable.baseline_pets_24)
                .error(R.drawable.baseline_pets_24)
                .into(binding.ivFotoGrande)
        }

        binding.btnCerrarVisor.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "VisorEvidenciaDialogFragment"
        private const val ARG_LOCAL_PATH = "arg_local_path"

        fun nuevaInstancia(localPath: String): VisorEvidenciaDialogFragment {
            return VisorEvidenciaDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LOCAL_PATH, localPath)
                }
            }
        }
    }
}

