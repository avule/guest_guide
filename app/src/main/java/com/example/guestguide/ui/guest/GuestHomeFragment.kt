package com.example.guestguide.ui.guest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.bumptech.glide.Glide
import com.example.guestguide.BuildConfig
import com.example.guestguide.GuestActivity
import com.example.guestguide.R
import com.example.guestguide.databinding.FragmentGuestHomeBinding
import com.example.guestguide.ui.adapter.ContactsAdapter
import com.example.guestguide.utils.Resource
import com.example.guestguide.viewmodel.SharedViewModel
import kotlinx.coroutines.launch

// Gostov pocetni ekran. Prikazuje wifi, pravila, mapu i kontakte.
// Podatke dobija real-time iz Firestore-a kroz SharedViewModel.
class GuestHomeFragment : Fragment() {

    private var _binding: FragmentGuestHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()
    private lateinit var contactsAdapter: ContactsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuestHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // accessCode dolazi iz Intent extras-a koji je postavila WelcomeActivity.
        // connectToApartment je idempotentan. Ako vec slusamo isti kod, ne radi nista.
        val accessCode = requireActivity().intent.getStringExtra(GuestActivity.EXTRA_ACCESS_CODE) ?: ""
        if (accessCode.isNotEmpty()) {
            viewModel.connectToApartment(accessCode)
        }
        setupUI()
        setupObservers()
    }

    // Postavlja klik listenere i horizontalnu listu kontakata.
    private fun setupUI() {
        // X dugme zatvara cijelu GuestActivity i vraca korisnika na Welcome.
        binding.ivExit.setOnClickListener {
            requireActivity().finish()
        }

        // Klik na lozinku je kopira u clipboard.
        binding.btnCopyWifi.setOnClickListener {
            val wifiPass = binding.tvWifiPass.text.toString()
            if (wifiPass.isNotEmpty() && wifiPass != "..." && wifiPass != "Učitavanje...") {
                copyToClipboard("Wi-Fi lozinka", wifiPass)
            }
        }

        // Dugme ISTRAZI GRAD prelazi na ekran sa preporukama.
        binding.btnExplore.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_guest_home_to_explore)
            } catch (e: Exception) {
                Toast.makeText(context, "Navigacija ka istraži grad", Toast.LENGTH_SHORT).show()
            }
        }

        // Klik na karticu vlasnika otvara dialer sa njegovim brojem.
        binding.cardContactOwner.setOnClickListener {
            val resource = viewModel.adminApartmentData.value
            if (resource is Resource.Success) {
                val ownerContact = resource.data?.ownerContact
                if (!ownerContact.isNullOrEmpty()) {
                    dialNumber(ownerContact)
                } else {
                    Toast.makeText(context, "Broj vlasnika nije dostupan", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Klik na mapu otvara Google Maps app. Ako app nije instaliran, ide preko web pretrage.
        binding.cardMap.setOnClickListener {
            val resource = viewModel.adminApartmentData.value
            if (resource is Resource.Success) {
                val location = resource.data?.location
                if (!location.isNullOrEmpty()) {
                    val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(location)}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    try {
                        startActivity(mapIntent)
                    } catch (e: Exception) {
                        val webMapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(location)}"))
                        startActivity(webMapIntent)
                    }
                }
            }
        }

        contactsAdapter = ContactsAdapter(
            isAdmin = false,
            onContactClick = { contact -> dialNumber(contact.number) },
            onDeleteClick = {}
        )
        binding.rvContactsGuest.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = contactsAdapter
        }
    }

    // Slusa dva StateFlow-a (apartman i kontakti) i ažurira UI cim stignu novi podaci.
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.adminApartmentData.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        binding.tvApartmentName.text = "Učitavanje..."
                        binding.tvWifiSsid.text = "..."
                        binding.tvWifiPass.text = "..."
                        binding.tvRules.text = "..."
                        binding.cardMap.visibility = View.GONE
                        binding.tvLocationLabel.visibility = View.GONE
                    }
                    is Resource.Success -> {
                        val apartment = resource.data
                        if (apartment != null) {
                            binding.tvApartmentName.text = apartment.name
                            binding.tvWifiSsid.text = apartment.wifiSsid
                            binding.tvWifiPass.text = apartment.wifiPassword

                            // Formatiraj kucna pravila tako da svaki red postaje bullet stavka.
                            if (apartment.houseRules.isNotEmpty()) {
                                val lines = apartment.houseRules.split("\n")
                                val formattedRules = lines
                                    .filter { it.isNotBlank() }
                                    .joinToString("\n") { "•  ${it.trim()}" }

                                binding.tvRules.text = formattedRules
                            } else {
                                binding.tvRules.text = "Nema posebnih pravila."
                            }

                            // Mapu prikazujemo kao sliku preko Maps Static API-ja. Lakše nego puni Maps SDK.
                            if (apartment.location.isNotEmpty()) {
                                binding.cardMap.visibility = View.VISIBLE
                                binding.tvLocationLabel.visibility = View.VISIBLE

                                val encodedLocation = Uri.encode(apartment.location)
                                val staticMapUrl = "https://maps.googleapis.com/maps/api/staticmap?center=$encodedLocation&zoom=15&size=600x300&maptype=roadmap&markers=color:red%7C$encodedLocation&key=${BuildConfig.MAPS_API_KEY}"

                                Glide.with(this@GuestHomeFragment)
                                    .load(staticMapUrl)
                                    .centerCrop()
                                    .placeholder(android.R.drawable.ic_dialog_map)
                                    .into(binding.ivMap)
                            } else {
                                binding.cardMap.visibility = View.GONE
                                binding.tvLocationLabel.visibility = View.GONE
                            }
                        }
                    }
                    is Resource.Error -> {
                        binding.tvApartmentName.text = "Greška"
                        Toast.makeText(context, resource.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.contacts.collect { list -> contactsAdapter.submitList(list) }
        }
    }

    // Otvara dialer sa vec unesenim brojem. Ne poziva automatski, korisnik mora pritisnuti zeleno dugme.
    private fun dialNumber(phoneNumber: String) {
        if (phoneNumber.isEmpty()) return
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Ne mogu otvoriti poziv.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Lozinka kopirana!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}