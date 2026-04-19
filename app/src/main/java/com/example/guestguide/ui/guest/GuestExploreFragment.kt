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

// Ekran za istraživanje preporuka — gost može filtrirati po kategoriji i pretraživati po tekstu.
// Podaci dolaze iz SharedViewModel-a koji ih vuče iz Firestore sub-kolekcije apartmana.
class GuestExploreFragment : Fragment() {

    private var _binding: FragmentGuestExploreBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()
    private lateinit var adapter: RecommendationAdapter

    private var fullList: List<Recommendation> = emptyList() // kompletna lista prije filtriranja

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recommendations.collect { list ->
                fullList = list
                applyFilters()
            }
        }

        binding.ivBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Filtriranje po kategoriji putem Chip dugmadi (Hrana, Piće, Vinarija, Znamenitost)
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

        // Pretraga po imenu ili opisu preporuke u realnom vremenu
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                applyFilters()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // Kombinuje kategorijski filter i tekstualnu pretragu, pa ažurira listu
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