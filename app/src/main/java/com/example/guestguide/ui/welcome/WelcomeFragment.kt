package com.example.guestguide.ui.welcome

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.guestguide.R
import com.example.guestguide.databinding.FragmentWelcomeBinding
import com.example.guestguide.viewmodel.SharedViewModel

// Pocetni ekran. Gost upisuje pristupni kod, a admin ide na login.
// Kod se prvo provjerava u bazi, pa tek onda navigacija na sledeci ekran.
class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()

    // SharedPreferences sluzi kao lokalno skladiste, pamti zadnji uneseni kod.
    private val prefs by lazy {
        requireContext().getSharedPreferences("GuestGuidePrefs", Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Gost je kliknuo PRISTUPI. Prvo provjeri kod u bazi, pa idi na GuestHome.
        binding.btnEnter.setOnClickListener {
            val code = binding.etCode.text.toString().uppercase().trim()

            if (code.isNotEmpty()) {
                binding.btnEnter.isEnabled = false
                binding.btnEnter.text = "Provjera..."

                viewModel.verifyApartmentCode(code) { exists ->
                    // Fragment je mozda vec unisten dok je callback stigao.
                    if (!isAdded) return@verifyApartmentCode

                    if (exists) {
                        // Upamti kod za sljedeci put.
                        prefs.edit().putString("last_access_code", code).apply()
                        viewModel.connectToApartment(code)
                        // SafeArgs prosledjuje kod do GuestHome ekrana.
                        val action = WelcomeFragmentDirections.actionWelcomeToGuest(code)
                        findNavController().navigate(action)
                    } else {
                        binding.btnEnter.isEnabled = true
                        binding.btnEnter.text = "PRISTUPI"
                        Toast.makeText(context, "Apartman s tim kodom ne postoji", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Unesite kod", Toast.LENGTH_SHORT).show()
            }
        }

        // Link za vlasnika apartmana. Vodi na admin login.
        binding.tvAdminLogin.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_admin)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
