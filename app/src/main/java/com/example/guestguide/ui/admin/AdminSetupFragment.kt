package com.example.guestguide.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.guestguide.R
import com.example.guestguide.data.model.Apartment
import com.example.guestguide.databinding.FragmentAdminSetupBinding
import com.example.guestguide.ui.adapter.ContactsAdapter
import com.example.guestguide.ui.adapter.RecommendationAdapter
import com.example.guestguide.utils.Resource
import com.example.guestguide.viewmodel.SharedViewModel
import kotlinx.coroutines.launch

class AdminSetupFragment : Fragment() {

    private var _binding: FragmentAdminSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()
    private lateinit var adapter: RecommendationAdapter
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var dialogHelper: AdminDialogHelper

    private var myApartments: List<Apartment> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (viewModel.getCurrentUser() == null) {
            findNavController().navigate(R.id.adminLoginFragment)
            return
        }

        viewModel.loadUserApartments()

        dialogHelper = AdminDialogHelper(
            fragment = this,
            viewModel = viewModel,
            onNavigateToLogin = {
                findNavController().navigate(R.id.adminLoginFragment)
            },
            onCreateNewApartment = { createNewApartmentMode() }
        )
        setupRecyclerView()
        observeData()

        binding.btnSaveAndGenerate.setOnClickListener { saveApartmentData() }

        binding.btnAddPlace.setOnClickListener {
            if (viewModel.existingAccessCode == null) {
                Toast.makeText(context, "Sačuvajte apartman prvo!", Toast.LENGTH_SHORT).show()
            } else {
                dialogHelper.showAddPlaceDialog(null, viewModel.existingAccessCode!!)
            }
        }
        binding.btnAddContact.setOnClickListener {
            if (viewModel.existingAccessCode == null) {
                Toast.makeText(context, "Sačuvajte apartman prvo!", Toast.LENGTH_SHORT).show()
            } else {
                dialogHelper.showAddContactDialog(null)
            }
        }

        binding.ivReset.setOnClickListener {
            createNewApartmentMode()
        }

        binding.ivApartmentList.setOnClickListener {
            dialogHelper.showApartmentSelectionDialog(myApartments)
        }

        binding.ivProfile.setOnClickListener {
            dialogHelper.showSideMenu()
        }

        binding.tvGeneratedCode.setOnClickListener {
            copyToClipboard(binding.tvGeneratedCode.text.toString())
        }
        binding.cardResult.setOnClickListener {
            copyToClipboard(binding.tvGeneratedCode.text.toString())
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Access Code", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Kod kopiran!", Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        adapter = RecommendationAdapter(
            isAdmin = true,
            onDeleteClick = { id -> dialogHelper.confirmDelete(id) },
            onEditClick = { rec ->
                val currentCode = viewModel.existingAccessCode ?: binding.tvGeneratedCode.text.toString()
                dialogHelper.showAddPlaceDialog(rec, currentCode)
            }
        )
        binding.rvRecommendations.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@AdminSetupFragment.adapter
        }

        contactsAdapter = ContactsAdapter(
            isAdmin = true,
            onContactClick = {},
            onDeleteClick = { contact ->
                viewModel.deleteContact(contact)
                Toast.makeText(context, "Broj obrisan", Toast.LENGTH_SHORT).show()
            },
            onEditClick = { contact ->
                dialogHelper.showAddContactDialog(contact)
            }
        )
        binding.rvContactsAdmin.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = contactsAdapter
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.adminApartmentData.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.mainContent.visibility = View.GONE
                    }
                    is Resource.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.mainContent.visibility = View.VISIBLE

                        val apartment = resource.data

                        if (apartment != null) {
                            if (viewModel.isCreatingNew) return@collect

                            if (viewModel.existingAccessCode == null || viewModel.existingAccessCode == apartment.accessCode) {
                                viewModel.existingAccessCode = apartment.accessCode
                                binding.etApartmentName.setText(apartment.name)
                                binding.etLocation.setText(apartment.location)
                                binding.etWifiName.setText(apartment.wifiSsid)
                                binding.etWifiPass.setText(apartment.wifiPassword)
                                binding.etContact.setText(apartment.ownerContact)
                                binding.etRules.setText(apartment.houseRules)

                                viewModel.currentApartmentImageUrl = apartment.imageUrl

                                binding.cardResult.visibility = View.VISIBLE
                                binding.tvGeneratedCode.text = apartment.accessCode
                                binding.btnSaveAndGenerate.text = "AŽURIRAJ PODATKE"
                            }
                        } else {
                            if (!viewModel.isCreatingNew) {
                                resetFormUI()
                                viewModel.existingAccessCode = null
                            }
                        }
                    }
                    is Resource.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.mainContent.visibility = View.VISIBLE
                        Toast.makeText(context, "Greška: ${resource.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch { viewModel.recommendations.collect { adapter.submitList(it) } }
        viewLifecycleOwner.lifecycleScope.launch { viewModel.contacts.collect { contactsAdapter.submitList(it) } }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ownedApartments.collect { list -> myApartments = list }
        }
    }

    private fun createNewApartmentMode() {
        viewModel.isCreatingNew = true
        viewModel.existingAccessCode = null
        viewModel.clearCurrentApartment()
        resetFormUI()
        binding.btnSaveAndGenerate.text = "SAČUVAJ NOVI APARTMAN"
        Toast.makeText(context, "Unesite podatke za novi apartman.", Toast.LENGTH_SHORT).show()
    }

    private fun resetFormUI() {
        binding.etApartmentName.setText("")
        binding.etLocation.setText("")
        binding.etWifiName.setText("")
        binding.etWifiPass.setText("")
        binding.etContact.setText("")
        binding.etRules.setText("")
        binding.cardResult.visibility = View.GONE
        viewModel.currentApartmentImageUrl = ""

        adapter.submitList(emptyList())
        contactsAdapter.submitList(emptyList())
    }

    private fun saveApartmentData() {
        val name = binding.etApartmentName.text.toString()
        val location = binding.etLocation.text.toString()
        val wifiName = binding.etWifiName.text.toString()
        val wifiPass = binding.etWifiPass.text.toString()
        val contact = binding.etContact.text.toString()
        val rules = binding.etRules.text.toString()

        if (name.isEmpty() || wifiName.isEmpty()) {
            Toast.makeText(context, "Popunite naziv i Wi-Fi", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSaveAndGenerate.isEnabled = false
        binding.btnSaveAndGenerate.text = "ČUVANJE..."

        val finalCode = viewModel.existingAccessCode ?: generateRandomCode()

        val newApartment = Apartment(
            accessCode = finalCode,
            name = name,
            location = location,
            wifiSsid = wifiName,
            wifiPassword = wifiPass,
            ownerContact = contact,
            houseRules = rules,
            imageUrl = viewModel.currentApartmentImageUrl
        )

        viewModel.saveApartmentSettings(newApartment)

        viewModel.existingAccessCode = finalCode
        viewModel.isCreatingNew = false

        binding.cardResult.visibility = View.VISIBLE
        binding.tvGeneratedCode.text = finalCode

        binding.btnSaveAndGenerate.isEnabled = true
        binding.btnSaveAndGenerate.text = "AŽURIRAJ PODATKE"

        Toast.makeText(context, "Uspješno sačuvano!", Toast.LENGTH_SHORT).show()
    }

    private fun generateRandomCode(): String {
        return (1..6).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString("")
    }

    override fun onDestroyView() {
        dialogHelper.dismissAll()
        super.onDestroyView()
        _binding = null
    }
}
