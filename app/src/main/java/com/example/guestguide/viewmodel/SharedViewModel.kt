package com.example.guestguide.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.guestguide.data.model.Apartment
import com.example.guestguide.data.model.Contact
import com.example.guestguide.data.model.Recommendation
import com.example.guestguide.utils.Resource
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Centralni ViewModel koji dijele svi fragmenti putem activityViewModels().
// Sadrži svu poslovnu logiku: autentifikaciju, CRUD operacije i real-time Firestore listenere.
// Fragmenti komuniciraju međusobno isključivo preko StateFlow-ova iz ovog ViewModela.
class SharedViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var currentApartmentCode: String? = null

    // Admin UI stanje (preživljava rotaciju ekrana jer je u ViewModelu)
    var isCreatingNew: Boolean = false
    var existingAccessCode: String? = null
    var currentApartmentImageUrl: String = ""

    private var apartmentListener: ListenerRegistration? = null
    private var recListener: ListenerRegistration? = null
    private var contactListener: ListenerRegistration? = null
    private var apartmentsListListener: ListenerRegistration? = null

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
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, pass).await()
                loadUserApartments()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Greška")
            }
        }
    }

    fun register(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                auth.createUserWithEmailAndPassword(email, pass).await()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Greška")
            }
        }
    }

    fun logout() {
        auth.signOut()
        apartmentsListListener?.remove()
        apartmentsListListener = null
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

    // Promjena email-a i/ili lozinke — zahtijeva re-autentifikaciju sa trenutnom lozinkom.
    // Ako su oba tražena, paralelno se izvršavaju preko async/awaitAll.
    fun updateProfile(currentPass: String, newEmail: String, newPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser
        if (user == null || user.email == null) {
            onError("Korisnik nije ulogovan")
            return
        }

        viewModelScope.launch {
            try {
                val credential = EmailAuthProvider.getCredential(user.email!!, currentPass)
                user.reauthenticate(credential).await()
            } catch (_: Exception) {
                onError("Trenutna lozinka nije tačna.")
                return@launch
            }

            val needsEmailUpdate = newEmail.isNotEmpty() && newEmail != user.email
            val needsPassUpdate = newPass.isNotEmpty()

            if (!needsEmailUpdate && !needsPassUpdate) {
                onSuccess()
                return@launch
            }

            try {
                val ops = buildList {
                    if (needsEmailUpdate) add(async { user.verifyBeforeUpdateEmail(newEmail).await() })
                    if (needsPassUpdate) add(async { user.updatePassword(newPass).await() })
                }
                ops.awaitAll()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Greška pri ažuriranju profila")
            }
        }
    }

    // Učitava sve apartmane vlasnika i automatski selektuje prvi ako nijedan nije izabran
    fun loadUserApartments() {
        val userId = auth.currentUser?.uid ?: return

        apartmentsListListener?.remove()
        apartmentsListListener = db.collection("apartments")
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
        viewModelScope.launch {
            try {
                val doc = db.collection("apartments").document(code).get().await()
                onResult(doc.exists())
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    // Postavlja real-time listenere na apartman, preporuke i kontakte iz Firestore-a
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

    // Čuva novi ili ažurira postojeći apartman u Firestore-u
    fun saveApartmentSettings(apartment: Apartment) {
        _adminApartmentData.value = Resource.Loading
        val ownerId = auth.currentUser?.uid ?: ""
        val apartmentWithOwner = apartment.copy(ownerId = ownerId)

        currentApartmentCode = apartment.accessCode

        viewModelScope.launch {
            try {
                db.collection("apartments").document(apartment.accessCode)
                    .set(apartmentWithOwner).await()
                connectToApartment(apartment.accessCode)
            } catch (e: Exception) {
                currentApartmentCode = null
                _adminApartmentData.value = Resource.Error(e.message ?: "Greška pri čuvanju")
            }
        }
    }

    fun deleteApartment(accessCode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                db.collection("apartments").document(accessCode).delete().await()
                if (currentApartmentCode == accessCode) {
                    currentApartmentCode = null
                    _adminApartmentData.value = Resource.Success(null)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Greška pri brisanju")
            }
        }
    }

    fun addPlace(rec: Recommendation) {
        val code = currentApartmentCode ?: return
        val newDocRef = db.collection("apartments").document(code).collection("recommendations").document()
        val newRec = rec.copy(id = newDocRef.id, apartmentCode = code)
        viewModelScope.launch {
            try {
                newDocRef.set(newRec).await()
            } catch (e: Exception) {
                Log.e("Firebase", "Greška pri dodavanju preporuke", e)
            }
        }
    }

    fun updatePlace(rec: Recommendation) {
        val code = currentApartmentCode ?: return
        viewModelScope.launch {
            try {
                db.collection("apartments").document(code)
                    .collection("recommendations").document(rec.id).set(rec).await()
            } catch (e: Exception) {
                Log.e("Firebase", "Greška pri izmjeni preporuke", e)
            }
        }
    }

    fun deletePlace(id: String) {
        val code = currentApartmentCode ?: return
        viewModelScope.launch {
            try {
                db.collection("apartments").document(code)
                    .collection("recommendations").document(id).delete().await()
            } catch (e: Exception) {
                Log.e("Firebase", "Greška pri brisanju preporuke", e)
            }
        }
    }

    fun addContact(contact: Contact) {
        val code = currentApartmentCode ?: return
        val newDocRef = db.collection("apartments").document(code).collection("contacts").document()
        val newContact = contact.copy(id = newDocRef.id)
        viewModelScope.launch {
            try {
                newDocRef.set(newContact).await()
            } catch (e: Exception) {
                Log.e("Firebase", "Greška pri dodavanju kontakta", e)
            }
        }
    }

    fun updateContact(contact: Contact) {
        val code = currentApartmentCode ?: return
        viewModelScope.launch {
            try {
                db.collection("apartments").document(code)
                    .collection("contacts").document(contact.id).set(contact).await()
            } catch (e: Exception) {
                Log.e("Firebase", "Greška pri izmjeni kontakta", e)
            }
        }
    }

    fun deleteContact(contact: Contact) {
        val code = currentApartmentCode ?: return
        viewModelScope.launch {
            try {
                db.collection("apartments").document(code)
                    .collection("contacts").document(contact.id).delete().await()
            } catch (e: Exception) {
                Log.e("Firebase", "Greška pri brisanju kontakta", e)
            }
        }
    }

    // Čišćenje svih listenera kad se ViewModel uništi (izbjegava memory leak i curenje Firestore reads)
    override fun onCleared() {
        super.onCleared()
        apartmentListener?.remove()
        recListener?.remove()
        contactListener?.remove()
        apartmentsListListener?.remove()
    }
}
