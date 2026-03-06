package com.example.guestguide.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.example.guestguide.data.model.Apartment
import com.example.guestguide.data.model.Contact
import com.example.guestguide.data.model.Recommendation
import com.example.guestguide.utils.Resource
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var currentApartmentCode: String? = null

    // Admin UI state (survives rotation)
    var isCreatingNew: Boolean = false
    var existingAccessCode: String? = null
    var currentApartmentImageUrl: String = ""

    // Listeners
    private var apartmentListener: ListenerRegistration? = null
    private var recListener: ListenerRegistration? = null
    private var contactListener: ListenerRegistration? = null

    // State Flows
    private val _adminApartmentData = MutableStateFlow<Resource<Apartment?>>(Resource.Loading)
    val adminApartmentData: StateFlow<Resource<Apartment?>> = _adminApartmentData

    private val _ownedApartments = MutableStateFlow<List<Apartment>>(emptyList())
    val ownedApartments: StateFlow<List<Apartment>> = _ownedApartments

    private val _recommendations = MutableStateFlow<List<Recommendation>>(emptyList())
    val recommendations: StateFlow<List<Recommendation>> = _recommendations

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    fun getCurrentUser() = auth.currentUser
    fun getUserEmail() = auth.currentUser?.email ?: ""

    fun login(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                loadUserApartments()
                onSuccess()
            }
            .addOnFailureListener { onError(it.message ?: "Greška") }
    }

    fun register(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Greška") }
    }

    fun logout() {
        auth.signOut()
        clearCurrentApartment()
        _adminApartmentData.value = Resource.Success(null)
        _ownedApartments.value = emptyList()
    }

    fun clearCurrentApartment() {
        currentApartmentCode = null
        apartmentListener?.remove()
        recListener?.remove()
        contactListener?.remove()
        _contacts.value = emptyList()
        _recommendations.value = emptyList()
    }

    fun updateProfile(currentPass: String, newEmail: String, newPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser
        if (user == null || user.email == null) {
            onError("Korisnik nije ulogovan")
            return
        }

        val credential = EmailAuthProvider.getCredential(user.email!!, currentPass)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                val needsEmailUpdate = newEmail.isNotEmpty() && newEmail != user.email
                val needsPassUpdate = newPass.isNotEmpty()

                if (!needsEmailUpdate && !needsPassUpdate) {
                    onSuccess()
                    return@addOnSuccessListener
                }

                var pendingOps = (if (needsEmailUpdate) 1 else 0) + (if (needsPassUpdate) 1 else 0)
                var hasError = false

                fun checkComplete() {
                    pendingOps--
                    if (pendingOps == 0 && !hasError) {
                        onSuccess()
                    }
                }

                if (needsEmailUpdate) {
                    user.verifyBeforeUpdateEmail(newEmail)
                        .addOnSuccessListener { checkComplete() }
                        .addOnFailureListener { e ->
                            if (!hasError) {
                                hasError = true
                                onError("Greška pri promjeni emaila: ${e.message}")
                            }
                        }
                }

                if (needsPassUpdate) {
                    user.updatePassword(newPass)
                        .addOnSuccessListener { checkComplete() }
                        .addOnFailureListener { e ->
                            if (!hasError) {
                                hasError = true
                                onError("Greška pri promjeni šifre: ${e.message}")
                            }
                        }
                }
            }
            .addOnFailureListener {
                onError("Trenutna lozinka nije tačna.")
            }
    }

    fun loadUserApartments() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("apartments")
            .whereEqualTo("ownerId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("Firebase", "Greška pri učitavanju liste apartmana", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val apartments = snapshot.toObjects(Apartment::class.java)
                    _ownedApartments.value = apartments

                    if (currentApartmentCode == null && apartments.isNotEmpty()) {
                        connectToApartment(apartments[0].accessCode)
                    } else if (apartments.isEmpty()) {
                        clearCurrentApartment()
                        _adminApartmentData.value = Resource.Success(null)
                    }
                }
            }
    }


    // Jednokratna provjera da li apartman postoji (za WelcomeFragment)
    fun verifyApartmentCode(code: String, onResult: (Boolean) -> Unit) {
        db.collection("apartments").document(code)
            .get()
            .addOnSuccessListener { doc -> onResult(doc.exists()) }
            .addOnFailureListener { onResult(false) }
    }

    fun connectToApartment(accessCode: String) {
        val currentState = _adminApartmentData.value
        if (currentApartmentCode == accessCode && currentState is Resource.Success) {
            return
        }

        currentApartmentCode = accessCode

        if (currentState !is Resource.Loading) {
            _adminApartmentData.value = Resource.Loading
        }

        apartmentListener?.remove()
        apartmentListener = db.collection("apartments").document(accessCode)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _adminApartmentData.value = Resource.Error(e.message ?: "Greška pri učitavanju")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val apt = snapshot.toObject(Apartment::class.java)
                    _adminApartmentData.value = Resource.Success(apt)
                } else {
                    _adminApartmentData.value = Resource.Success(null)
                }
            }

        recListener?.remove()
        recListener = db.collection("apartments").document(accessCode)
            .collection("recommendations")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val list = snapshot?.toObjects(Recommendation::class.java) ?: emptyList()
                _recommendations.value = list
            }

        contactListener?.remove()
        contactListener = db.collection("apartments").document(accessCode)
            .collection("contacts")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val list = snapshot?.toObjects(Contact::class.java) ?: emptyList()
                _contacts.value = list
            }
    }

    fun saveApartmentSettings(apartment: Apartment) {
        _adminApartmentData.value = Resource.Loading
        val ownerId = auth.currentUser?.uid ?: ""
        val apartmentWithOwner = apartment.copy(ownerId = ownerId)

        currentApartmentCode = apartment.accessCode

        db.collection("apartments").document(apartment.accessCode)
            .set(apartmentWithOwner)
            .addOnSuccessListener {
                connectToApartment(apartment.accessCode)
            }
            .addOnFailureListener { e ->
                currentApartmentCode = null
                _adminApartmentData.value = Resource.Error(e.message ?: "Greška pri čuvanju")
            }
    }

    fun deleteApartment(accessCode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("apartments").document(accessCode)
            .delete()
            .addOnSuccessListener {
                if (currentApartmentCode == accessCode) {
                    currentApartmentCode = null
                    _adminApartmentData.value = Resource.Success(null)
                }
                onSuccess()
            }
            .addOnFailureListener { onError(it.message ?: "Greška pri brisanju") }
    }

    fun addPlace(rec: Recommendation) {
        val code = currentApartmentCode ?: return
        val newDocRef = db.collection("apartments").document(code).collection("recommendations").document()
        val newRec = rec.copy(id = newDocRef.id, apartmentCode = code)
        newDocRef.set(newRec)
    }

    fun updatePlace(rec: Recommendation) {
        val code = currentApartmentCode ?: return
        db.collection("apartments").document(code).collection("recommendations").document(rec.id).set(rec)
    }

    fun deletePlace(id: String) {
        val code = currentApartmentCode ?: return
        db.collection("apartments").document(code).collection("recommendations").document(id).delete()
    }

    fun addContact(contact: Contact) {
        val code = currentApartmentCode ?: return
        val newDocRef = db.collection("apartments").document(code).collection("contacts").document()
        val newContact = contact.copy(id = newDocRef.id)
        newDocRef.set(newContact)
    }

    fun updateContact(contact: Contact) {
        val code = currentApartmentCode ?: return
        db.collection("apartments").document(code).collection("contacts").document(contact.id).set(contact)
    }

    fun deleteContact(contact: Contact) {
        val code = currentApartmentCode ?: return
        db.collection("apartments").document(code).collection("contacts").document(contact.id).delete()
    }

    override fun onCleared() {
        super.onCleared()
        apartmentListener?.remove()
        recListener?.remove()
        contactListener?.remove()
    }
}