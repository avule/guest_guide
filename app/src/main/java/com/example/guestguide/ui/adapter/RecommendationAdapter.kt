package com.example.guestguide.ui.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.guestguide.data.model.Recommendation
import com.example.guestguide.databinding.ItemRecommendationBinding

// Adapter za listu preporuka. Koristi ga i admin i gost ekran.
// isAdmin flag odredjuje da li se prikazuju dugmad za edit i delete.
// Nasljedjuje ListAdapter zbog DiffUtil-a koji animira samo promijenjene stavke.
class RecommendationAdapter(
    private val isAdmin: Boolean,
    private val onDeleteClick: (String) -> Unit,
    private val onEditClick: ((Recommendation) -> Unit)? = null
) : ListAdapter<Recommendation, RecommendationAdapter.RecommendationViewHolder>(RecommendationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val binding = ItemRecommendationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecommendationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecommendationViewHolder(private val binding: ItemRecommendationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Recommendation) {
            // Popuni sva tekstualna polja kartice.
            binding.tvPlaceName.text = item.name
            binding.tvPlaceDesc.text = item.description
            binding.tvRating.text = item.rating.toString()
            binding.tvCategory.text = item.category.uppercase()

            // Ucitaj sliku preko Glide-a. Placeholder se prikazuje dok slika ne stigne.
            if (item.imageUrl.isNotEmpty()) {
                Glide.with(binding.root.context)
                    .load(item.imageUrl)
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.stat_notify_error)
                    .into(binding.ivPlaceImage)
            } else {
                binding.ivPlaceImage.setImageResource(android.R.drawable.ic_menu_gallery)
                binding.ivPlaceImage.scaleType = android.widget.ImageView.ScaleType.CENTER
            }

            // Klik na karticu otvara lokaciju u mapi, ako preporuka ima link.
            binding.root.setOnClickListener {
                if (item.mapLink.isNotEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.mapLink))
                        binding.root.context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(binding.root.context, "Neispravan link za mapu", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(binding.root.context, "Lokacija nije unesena", Toast.LENGTH_SHORT).show()
                }
            }

            // Edit i delete dugmad se prikazuju samo na admin ekranu.
            if (isAdmin) {
                binding.btnDelete.visibility = View.VISIBLE
                binding.btnEdit.visibility = View.VISIBLE

                binding.btnDelete.setOnClickListener {
                    onDeleteClick(item.id)
                }

                binding.btnEdit.setOnClickListener {
                    onEditClick?.invoke(item)
                }
            } else {
                binding.btnDelete.visibility = View.GONE
                binding.btnEdit.visibility = View.GONE
            }
        }
    }

    // DiffUtil racuna razliku izmedju stare i nove liste.
    class RecommendationDiffCallback : DiffUtil.ItemCallback<Recommendation>() {
        // Da li su dvije stavke iste? Poredimo po ID-u.
        override fun areItemsTheSame(oldItem: Recommendation, newItem: Recommendation): Boolean {
            return oldItem.id == newItem.id
        }

        // Da li su sadrzaji isti? Data klasa automatski generise equals.
        override fun areContentsTheSame(oldItem: Recommendation, newItem: Recommendation): Boolean {
            return oldItem == newItem
        }
    }
}