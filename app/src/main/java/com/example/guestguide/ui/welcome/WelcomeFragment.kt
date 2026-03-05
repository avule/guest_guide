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

class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()

    // Lokalno skladištenje – pamti zadnji kod kojim je gost pristupio apartmanu
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

        // Popuni polje sa zadnjim korištenim kodom (lokalno)
        val lastCode = prefs.getString("last_access_code", "")
        if (!lastCode.isNullOrEmpty()) {
            binding.etCode.setText(lastCode)
        }

        binding.btnEnter.setOnClickListener {
            val code = binding.etCode.text.toString().uppercase().trim()

            if (code.isNotEmpty()) {
                binding.btnEnter.isEnabled = false
                binding.btnEnter.text = "Provjera..."

                viewModel.verifyApartmentCode(code) { exists ->
                    if (!isAdded) return@verifyApartmentCode

                    if (exists) {
                        prefs.edit().putString("last_access_code", code).apply()
                        viewModel.connectToApartment(code)
                        findNavController().navigate(R.id.action_welcome_to_guest)
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

        binding.tvAdminLogin.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_admin)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
