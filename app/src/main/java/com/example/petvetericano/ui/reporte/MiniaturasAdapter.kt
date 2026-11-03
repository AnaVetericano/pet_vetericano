package com.example.petvetericano.ui.reporte

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.petvetericano.R
import com.example.petvetericano.databinding.ItemEvidenciaMiniaturaBinding
import java.io.File

class MiniaturasAdapter(
    private val onFotoClick: (EvidenciaItemLocal) -> Unit,
    private val onVideoClick: (EvidenciaItemLocal) -> Unit,
    private val onEliminarClick: (EvidenciaItemLocal) -> Unit
) : ListAdapter<EvidenciaItemLocal, MiniaturasAdapter.MiniaturaViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MiniaturaViewHolder {
        val binding = ItemEvidenciaMiniaturaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MiniaturaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MiniaturaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MiniaturaViewHolder(
        private val binding: ItemEvidenciaMiniaturaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EvidenciaItemLocal) {
            val file = File(item.localPath)
            val context = binding.root.context

            if (item.esVideo) {
                Glide.with(context)
                    .asBitmap()
                    .load(file)
                    .apply(RequestOptions.frameOf(1_000_000L))
                    .override(160, 160)
                    .centerCrop()
                    .placeholder(R.drawable.baseline_pets_24)
                    .error(
                        Glide.with(context)
                            .asBitmap()
                            .load(file)
                            .override(160, 160)
                            .centerCrop()
                            .placeholder(R.drawable.baseline_pets_24)
                            .error(R.drawable.baseline_pets_24)
                    )
                    .into(binding.ivMiniatura)

                binding.layoutVideoOverlay.visibility = View.VISIBLE
                binding.cardMiniatura.setOnClickListener {
                    onVideoClick(item)
                }
            } else {
                Glide.with(context)
                    .load(file)
                    .override(160, 160)
                    .centerCrop()
                    .placeholder(R.drawable.baseline_pets_24)
                    .error(R.drawable.baseline_pets_24)
                    .into(binding.ivMiniatura)

                binding.layoutVideoOverlay.visibility = View.GONE
                binding.cardMiniatura.setOnClickListener {
                    onFotoClick(item)
                }
            }

            binding.btnQuitar.setOnClickListener {
                onEliminarClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<EvidenciaItemLocal>() {
            override fun areItemsTheSame(oldItem: EvidenciaItemLocal, newItem: EvidenciaItemLocal): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: EvidenciaItemLocal, newItem: EvidenciaItemLocal): Boolean {
                return oldItem == newItem
            }
        }
    }
}