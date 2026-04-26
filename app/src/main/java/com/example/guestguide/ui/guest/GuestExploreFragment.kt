package com.example.guestguide.ui.guest

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.guestguide.R
import com.example.guestguide.data.model.Recommendation
import com.example.guestguide.databinding.FragmentGuestExploreBinding
import com.example.guestguide.ui.adapter.RecommendationAdapter
import com.example.guestguide.viewmodel.SharedViewModel
import kotlinx.coroutines.launch

// Ekran sa preporukama. Ima filter po kategoriji i pretragu po tekstu.
// Preporuke dolaze iz SharedViewModel-a (sub-kolekcija apartmana u Firestore-u).
class GuestExploreFragment : Fragment() {

    private var _binding: FragmentGuestExploreBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()
    private lateinit var adapter: RecommendationAdapter

    // Sve preporuke prije filtriranja. Drzimo ih lokalno da pretraga ide instant, bez poziva baze.
    private var fullList: List<Recommendation> = emptyList()

    // Trenutno izabran chip (kategorija).
    private var currentCategory = "Sve"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuestExploreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        // Pretplati se na flow preporuka. Kad stignu novi podaci, filtriraj iznova.
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recommendations.collect { list ->
                fullList = list
                applyFilters()
            }
        }

        binding.ivBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Chip-ovi za kategorije: Sve, Hrana, Piće, Vinarija, Znamenitost.
        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                currentCategory = when (checkedIds[0]) {
                    R.id.chipAll -> "Sve"
                    R.id.chipFood -> "Hrana"
                    R.id.chipDrinks -> "Piće"
                    R.id.chipWinery -> "Vinarija"
                    R.id.chipSights -> "Znamenitost"
                    else -> "Sve"
                }
                applyFilters()
            }
        }

        // Pretraga preko TextWatcher-a. Okida applyFilters na svaku promjenu teksta.
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                applyFilters()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // Primjeni i kategorijski filter i tekst pretragu, pa salji rezultat u adapter.
    private fun applyFilters() {
        val query = binding.etSearch.text.toString().trim()

        var filtered = fullList

        if (currentCategory != "Sve") {
            filtered = filtered.filter { it.category.contains(currentCategory, ignoreCase = true) }
        }

        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        }

        adapter.submitList(filtered)
    }

    private fun setupRecyclerView() {
        adapter = RecommendationAdapter(
            isAdmin = false,
            onDeleteClick = {},
            onEditClick = null
        )
        binding.rvGuestRecommendations.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@GuestExploreFragment.adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}