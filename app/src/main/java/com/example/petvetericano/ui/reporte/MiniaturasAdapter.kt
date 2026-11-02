package com.example.petvetericano.ui.reporte

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.petvetericano.R
import com.example.petvetericano.databinding.ItemEvidenciaMiniaturaBinding
import java.io.File

class MiniaturasAdapter(
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
            Glide.with(binding.root.context)
                .load(file)
                .override(160, 160)
                .centerCrop()
                .placeholder(R.drawable.baseline_pets_24)
                .error(R.drawable.baseline_pets_24)
                .into(binding.ivMiniatura)

            if (item.esVideo) {
                binding.layoutVideoOverlay.visibility = View.VISIBLE
                binding.cardMiniatura.setOnClickListener {
                    onVideoClick(item)
                }
            } else {
                binding.layoutVideoOverlay.visibility = View.GONE
                binding.cardMiniatura.setOnClickListener(null)
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