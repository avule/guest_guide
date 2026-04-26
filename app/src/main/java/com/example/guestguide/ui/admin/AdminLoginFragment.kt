package com.example.guestguide.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.guestguide.R
import com.example.guestguide.databinding.FragmentAdminLoginBinding
import com.example.guestguide.viewmodel.SharedViewModel

// Login i registracija vlasnika kroz Firebase Auth.
// Jedan layout sluzi za oba moda. Prebacujemo se preko isLoginMode flaga.
class AdminLoginFragment : Fragment() {

    private var _binding: FragmentAdminLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModels()

    // True znaci da je trenutno login mod, false znaci registracija.
    private var isLoginMode = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ako je vec ulogovan, preskoci login ekran. Return da se ostatak ne izvrsi.
        if (viewModel.getCurrentUser() != null) {
            findNavController().navigate(R.id.action_login_to_setup)
            return
        }

        updateUI()

        // Back strelica. Pokusaj popBackStack, a ako Welcome nije u istoriji onda fresh navigacija.
        binding.ivBack.setOnClickListener {
            val popped = findNavController().popBackStack(R.id.welcomeFragment, false)
            if (!popped) {
                findNavController().navigate(R.id.welcomeFragment)
            }
        }

        // Toggle izmedju login i registracija moda.
        binding.btnSwitchMode.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUI()
        }

        // Glavno dugme. Pokrece login ili registraciju zavisno od trenutnog moda.
        binding.btnMainAction.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || pass.length < 6) {
                Toast.makeText(context, "Unesite validan email i lozinku (min 6 karaktera)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isLoginMode) {

                viewModel.login(email, pass,
                    onSuccess = {
                        Toast.makeText(context, "Dobrodošli nazad!", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.action_login_to_setup)
                    },
                    onError = { error ->
                        Toast.makeText(context, "Greška: $error", Toast.LENGTH_LONG).show()
                    }
                )
            } else {

                val confirmPass = binding.etConfirmPassword.text.toString().trim()

                if (pass != confirmPass) {
                    Toast.makeText(context, "Lozinke se ne podudaraju!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                viewModel.register(email, pass,
                    onSuccess = {
                        Toast.makeText(context, "Registracija uspješna! Prijavljivanje...", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.action_login_to_setup)
                    },
                    onError = { error ->
                        Toast.makeText(context, "Greška: $error", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    // Mijenja naslov, tekst dugmeta i prikaz polja zavisno od moda.
    private fun updateUI() {
        if (isLoginMode) {
            binding.tvTitle.text = "Prijava za vlasnika"
            binding.btnMainAction.text = "PRIJAVI SE"
            binding.btnSwitchMode.text = "Nemaš nalog? Registruj se"
            binding.layoutConfirmPassword.visibility = View.GONE
        } else {
            binding.tvTitle.text = "Nova registracija"
            binding.btnMainAction.text = "REGISTRUJ SE"
            binding.btnSwitchMode.text = "Već imaš nalog? Prijavi se"
            binding.layoutConfirmPassword.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}