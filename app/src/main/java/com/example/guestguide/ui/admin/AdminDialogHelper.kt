package com.example.guestguide.ui.admin

import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.guestguide.R
import com.example.guestguide.data.model.Apartment
import com.example.guestguide.data.model.Contact
import com.example.guestguide.data.model.ContactType
import com.example.guestguide.data.model.Recommendation
import com.example.guestguide.viewmodel.SharedViewModel
import com.google.android.material.card.MaterialCardView

// Pomocna klasa. Sve admin dialoge drzimo ovdje, ne u samom Fragment-u, da bi kod bio cistiji.
// activeDialogs lista nam omogucava da ih sve pogasimo kad fragment ode.
class AdminDialogHelper(
    private val fragment: Fragment,
    private val viewModel: SharedViewModel,
    private val onNavigateToLogin: () -> Unit,
    private val onCreateNewApartment: () -> Unit
) {

    // Lista svih trenutno otvorenih dialoga. Bitna je za izbjegavanje WindowLeaked greske.
    private val activeDialogs = mutableListOf<android.app.Dialog>()

    private val context get() = fragment.context
    private fun requireContext() = fragment.requireContext()

    // Ubaci dialog u listu i automatski ga skini kad se zatvori.
    private fun trackDialog(dialog: android.app.Dialog): android.app.Dialog {
        activeDialogs.add(dialog)
        dialog.setOnDismissListener { activeDialogs.remove(dialog) }
        return dialog
    }

    // Gasi sve otvorene dialoge. Zove se iz Fragment.onDestroyView().
    fun dismissAll() {
        activeDialogs.toList().forEach { if (it.isShowing) it.dismiss() }
        activeDialogs.clear()
    }

    // ----- BOCNI MENI (profil + odjava) -----
    fun showSideMenu() {
        val dialog = trackDialog(android.app.Dialog(requireContext()))
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_admin_menu)

        val window = dialog.window
        window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setGravity(Gravity.END)

        window?.attributes?.windowAnimations = android.R.style.Animation_Dialog

        val tvEmail = dialog.findViewById<TextView>(R.id.tvMenuEmail)
        tvEmail.text = viewModel.getUserEmail()

        dialog.findViewById<View>(R.id.btnMenuProfile).setOnClickListener {
            dialog.dismiss()
            showEditProfileDialog()
        }

        dialog.findViewById<View>(R.id.btnMenuLogout).setOnClickListener {
            dialog.dismiss()
            viewModel.logout()
            onNavigateToLogin()
            Toast.makeText(context, "Uspješno ste se odjavili", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    // ----- IZMJENA PROFILA -----
    // Mijenja email i/ili lozinku. Trazi unos trenutne lozinke radi re-autentifikacije.
    fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_profile, null)
        val etCurrentPass = dialogView.findViewById<EditText>(R.id.etCurrentPass)
        val etNewEmail = dialogView.findViewById<EditText>(R.id.etNewEmail)
        val etNewPass = dialogView.findViewById<EditText>(R.id.etNewPass)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveProfile)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelEdit)

        etNewEmail.setText(viewModel.getUserEmail())

        val dialog = trackDialog(AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create())
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val currentPass = etCurrentPass.text.toString()
            val newEmail = etNewEmail.text.toString()
            val newPass = etNewPass.text.toString()

            if (currentPass.isEmpty()) {
                Toast.makeText(context, "Morate unjeti trenutnu lozinku", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            btnSave.text = "Čuvanje..."

            // Provjera da li korisnik zaista mijenja email.
            val emailChanged = newEmail.isNotEmpty() && newEmail != viewModel.getUserEmail()

            viewModel.updateProfile(currentPass, newEmail, newPass,
                onSuccess = {
                    // Firebase email change zahtijeva klik na verifikacioni link u novom inbox-u.
                    // Lozinka se mijenja odmah.
                    val msg = if (emailChanged) {
                        "Verifikacioni link je poslan na $newEmail. Otvorite ga da potvrdite promjenu emaila."
                    } else {
                        "Profil uspješno ažuriran!"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                },
                onError = { error ->
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    btnSave.isEnabled = true
                    btnSave.text = "Sačuvaj"
                }
            )
        }
        dialog.show()
    }

    // ----- DODAJ ILI IZMJENI PREPORUKU -----
    // Ako je recToEdit null, otvara se forma za kreiranje. Inace ide izmjena.
    fun showAddPlaceDialog(recToEdit: Recommendation?, currentCode: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_recommendation, null)

        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etDesc = dialogView.findViewById<EditText>(R.id.etDesc)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)
        val etMapLink = dialogView.findViewById<EditText>(R.id.etMapLink)
        val etCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.etCategory)
        val btnAdd = dialogView.findViewById<Button>(R.id.btnAdd)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val tvTitle = dialogView.findViewById<TextView>(R.id.titleDialog)

        val categories = arrayOf("Hrana", "Piće", "Vinarija", "Znamenitost")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories)
        etCategory.setAdapter(adapter)
        etCategory.setText(categories[0], false)

        if (recToEdit != null) {
            tvTitle.text = "Izmjeni preporuku"
            etName.setText(recToEdit.name)
            etDesc.setText(recToEdit.description)
            etMapLink.setText(recToEdit.mapLink)
            ratingBar.rating = recToEdit.rating.toFloat()
            etCategory.setText(recToEdit.category, false)
            btnAdd.text = "SAČUVAJ IZMJENE"
        } else {
            tvTitle.text = "Nova preporuka"
            btnAdd.text = "DODAJ"
        }

        val dialog = trackDialog(AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create())
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAdd.setOnClickListener {
            val name = etName.text.toString()
            val desc = etDesc.text.toString()
            val cat = etCategory.text.toString()
            val rating = ratingBar.rating.toDouble()
            val mapLink = etMapLink.text.toString()

            if (name.isNotEmpty() && currentCode.isNotEmpty()) {
                btnAdd.isEnabled = false
                btnAdd.text = "Čuvanje..."

                val imageUrl = recToEdit?.imageUrl ?: ""

                if (recToEdit == null) {
                    viewModel.addPlace(Recommendation("", currentCode, name, desc, cat, rating, imageUrl, mapLink))
                    Toast.makeText(context, "Preporuka dodata!", Toast.LENGTH_SHORT).show()
                } else {
                    val updatedRec = recToEdit.copy(
                        name = name,
                        description = desc,
                        category = cat,
                        rating = rating,
                        imageUrl = imageUrl,
                        mapLink = mapLink
                    )
                    viewModel.updatePlace(updatedRec)
                    Toast.makeText(context, "Izmjene sačuvane!", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Sačuvajte apartman prvo!", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    // ----- IZBOR APARTMANA -----
    // Prikazuje listu svih apartmana vlasnika. Ima i dugme za brisanje.
    fun showApartmentSelectionDialog(myApartments: List<Apartment>) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_my_apartments, null)

        val dialog = trackDialog(AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create())
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val container = dialogView.findViewById<LinearLayout>(R.id.containerApartments)
        val btnAdd = dialogView.findViewById<View>(R.id.btnAddNewApartment)
        val btnClose = dialogView.findViewById<View>(R.id.btnCloseDialog)

        for (apt in myApartments) {
            val itemView = LayoutInflater.from(context).inflate(R.layout.item_dialog_apartment, container, false)

            val cardRoot = itemView.findViewById<MaterialCardView>(R.id.cardRoot)
            val tvName = itemView.findViewById<TextView>(R.id.tvApartmentName)
            val tvCode = itemView.findViewById<TextView>(R.id.tvApartmentCode)
            val ivIcon = itemView.findViewById<ImageView>(R.id.ivIcon)
            val tvActive = itemView.findViewById<TextView>(R.id.tvActiveLabel)
            val btnDelete = itemView.findViewById<ImageView>(R.id.btnDeleteApartment)

            tvName.text = apt.name
            tvCode.text = "KOD: ${apt.accessCode}"

            // Istakni karticu trenutno aktivnog apartmana zlatnim okvirom.
            if (apt.accessCode == viewModel.existingAccessCode) {
                cardRoot.strokeColor = ContextCompat.getColor(requireContext(), R.color.gold_accent)
                cardRoot.strokeWidth = 4
                cardRoot.setCardBackgroundColor(Color.parseColor("#FFFCF2"))
                ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary_dark))
                tvActive.visibility = View.VISIBLE
            } else {
                cardRoot.strokeColor = Color.parseColor("#E0E0E0")
                cardRoot.strokeWidth = 2
                cardRoot.setCardBackgroundColor(Color.WHITE)
                ivIcon.setColorFilter(Color.parseColor("#B0B0B0"))
                tvActive.visibility = View.GONE
            }

            itemView.setOnClickListener {
                viewModel.isCreatingNew = false
                viewModel.existingAccessCode = null
                viewModel.connectToApartment(apt.accessCode)
                Toast.makeText(context, "Izabran: ${apt.name}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }

            // Brisanje apartmana. Uvijek prvo trazimo potvrdu.
            btnDelete.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Obriši apartman?")
                    .setMessage("Da li ste sigurni da želite da obrišete '${apt.name}'? Ovo je nepovratno.")
                    .setPositiveButton("OBRIŠI") { _, _ ->
                        viewModel.deleteApartment(apt.accessCode,
                            onSuccess = {
                                Toast.makeText(context, "Apartman obrisan", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()

                                // Ako jos ima apartmana, prebaci se na prvi. Inace pokreni mod za novi.
                                val remainingApartments = myApartments.filter { it.accessCode != apt.accessCode }

                                if (remainingApartments.isNotEmpty()) {
                                    val nextApartment = remainingApartments[0]
                                    viewModel.isCreatingNew = false
                                    viewModel.existingAccessCode = null
                                    viewModel.connectToApartment(nextApartment.accessCode)
                                } else {
                                    onCreateNewApartment()
                                }
                            },
                            onError = { Toast.makeText(context, "Greška: $it", Toast.LENGTH_SHORT).show() }
                        )
                    }
                    .setNegativeButton("Odustani", null)
                    .show()
            }

            container.addView(itemView)
        }

        btnAdd.setOnClickListener {
            onCreateNewApartment()
            dialog.dismiss()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ----- DODAJ ILI IZMJENI KONTAKT -----
    fun showAddContactDialog(contactToEdit: Contact?) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etNumber = dialogView.findViewById<EditText>(R.id.etNumber)
        val etCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.etCategory)
        val btnAdd = dialogView.findViewById<Button>(R.id.btnAdd)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnClose = dialogView.findViewById<View>(R.id.btnClose)

        val types = arrayOf("Taxi", "Policija", "Hitna", "Ostalo")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, types)
        etCategory.setAdapter(adapter)
        etCategory.setText(types[0], false)

        if (contactToEdit != null) {
            etName.setText(contactToEdit.name)
            etNumber.setText(contactToEdit.number)

            val typeStr = when (contactToEdit.type) {
                ContactType.TAXI -> "Taxi"
                ContactType.POLICE -> "Policija"
                ContactType.AMBULANCE -> "Hitna"
                ContactType.OTHER -> "Ostalo"
            }
            etCategory.setText(typeStr, false)
            btnAdd.text = "SAČUVAJ"
        } else {
            btnAdd.text = "DODAJ"
        }

        val dialog = trackDialog(AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create())
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val close = View.OnClickListener { dialog.dismiss() }
        btnClose.setOnClickListener(close)
        btnCancel.setOnClickListener(close)

        btnAdd.setOnClickListener {
            val name = etName.text.toString()
            val number = etNumber.text.toString()
            val typeStr = etCategory.text.toString()

            if (name.isNotEmpty() && number.isNotEmpty()) {
                val type = when(typeStr) {
                    "Taxi" -> ContactType.TAXI
                    "Policija" -> ContactType.POLICE
                    "Hitna" -> ContactType.AMBULANCE
                    else -> ContactType.OTHER
                }

                if (contactToEdit == null) {
                    viewModel.addContact(Contact("", name, number, type))
                    Toast.makeText(context, "Broj dodat!", Toast.LENGTH_SHORT).show()
                } else {
                    val updatedContact = contactToEdit.copy(name = name, number = number, type = type)
                    viewModel.updateContact(updatedContact)
                    Toast.makeText(context, "Broj izmjenjen!", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // ----- POTVRDA BRISANJA PREPORUKE -----
    fun confirmDelete(id: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_delete_confirmation, null)
        val dialog = trackDialog(AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create())
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btnCancelDelete).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnConfirmDelete).setOnClickListener {
            viewModel.deletePlace(id)
            Toast.makeText(context, "Obrisano.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }
}
