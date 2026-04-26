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

// Centralni ViewModel. Svi fragmenti ga dobijaju preko activityViewModels().
// Ovdje je sva logika: auth, CRUD, real-time slusanje Firestore baze.
// Fragmenti razmjenjuju podatke iskljucivo preko StateFlow-ova ispod.
class SharedViewModel(application: Application) : AndroidViewModel(application) {

    // Firebase singleton.
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Kod trenutno aktivnog apartmana.
    private var currentApartmentCode: String? = null

    // Pomocno admin stanje koje preživljava rotaciju ekrana.
    var isCreatingNew: Boolean = false
    var existingAccessCode: String? = null
    var currentApartmentImageUrl: String = ""

    // Real-time listeneri. Cuvamo ih da ih mozemo ugasiti i izbjeci leak.
    private var apartmentListener: ListenerRegistration? = null
    private var recListener: ListenerRegistration? = null
    private var contactListener: ListenerRegistration? = null
    private var apartmentsListListener: ListenerRegistration? = null

    // StateFlow-ovi. Fragmenti se pretplate i automatski dobijaju nova stanja. Observer pattern
    private val _adminApartmentData = MutableStateFlow<Resource<Apartment?>>(Resource.Loading)
    val adminApartmentData: StateFlow<Resource<Apartment?>> = _adminApartmentData

    private val _ownedApartments = MutableStateFlow<List<Apartment>>(emptyList())
    val ownedApartments: StateFlow<List<Apartment>> = _ownedApartments

    private val _recommendations = MutableStateFlow<List<Recommendation>>(emptyList())
    val recommendations: StateFlow<List<Recommendation>> = _recommendations

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    // ----- AUTH -----

    fun getCurrentUser() = auth.currentUser
    fun getUserEmail() = auth.currentUser?.email ?: ""

    // Login preko emaila i lozinke.
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

    // Registracija novog vlasnika.
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

    // Odjava. Gasi sve listenere i prazni stanje.
    fun logout() {
        auth.signOut()
        apartmentsListListener?.remove()
        apartmentsListListener = null
        clearCurrentApartment()
        _adminApartmentData.value = Resource.Success(null)
        _ownedApartments.value = emptyList()
    }

    // Gasi listenere za trenutno aktivan apartman. Listu svih apartmana ne dira.
    fun clearCurrentApartment() {
        currentApartmentCode = null
        apartmentListener?.remove()
        recListener?.remove()
        contactListener?.remove()
        _contacts.value = emptyList()
        _recommendations.value = emptyList()
    }

    // Promjena emaila i/ili lozinke. Prvo se trazi re-auth sa starom lozinkom,
    // pa onda paralelno oba update-a ako su oba trazena.
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

    // ----- APARTMANI: ucitavanje i konekcija -----

    // Dovuce sve apartmane ulogovanog vlasnika i automatski selektuje prvi ako nema izabranog.
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


    // Provjera postoji li apartman s tim kodom.
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

    // Kaci 3 listenera odjednom: apartman, recommendations, contacts.
    // Funkcija je idempotentna. Ako vec slusamo isti kod, odmah vraca.
    fun connectToApartment(accessCode: String) {
        val currentState = _adminApartmentData.value
        if (currentApartmentCode == accessCode && currentState is Resource.Success) {
            return
        }

        currentApartmentCode = accessCode

        if (currentState !is Resource.Loading) {
            _adminApartmentData.value = Resource.Loading
        }

        // 1. Listener na sam apartman.
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

        // 2. Listener na sub-kolekciju preporuka.
        recListener?.remove()
        recListener = db.collection("apartments").document(accessCode)
            .collection("recommendations")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val list = snapshot?.toObjects(Recommendation::class.java) ?: emptyList()
                _recommendations.value = list
            }

        // 3. Listener na sub-kolekciju kontakata.
        contactListener?.remove()
        contactListener = db.collection("apartments").document(accessCode)
            .collection("contacts")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val list = snapshot?.toObjects(Contact::class.java) ?: emptyList()
                _contacts.value = list
            }
    }

    // ----- CRUD: APARTMAN -----

    // Upisuje novi ili azurira postojeci apartman. Firestore set() radi oboje.
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

    // Brise apartman. Subkolekcije ostaju u bazi dok ih rucno ne pocistimo.
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

    // ----- CRUD: PREPORUKE -----

    // Dodaje novu preporuku. Firestore generise ID, a mi ga upisemo u sam objekat
    // da bi kasnije mogli da ga citamo prilikom edit/delete.
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

    // Mijenja postojecu preporuku po ID-u.
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

    // Brise preporuku po ID-u.
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

    // ----- CRUD: KONTAKTI -----

    // Dodaje novi kontakt (taxi, policija, hitna i slicno).
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

    // Mijenja postojeci kontakt.
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

    // Brise kontakt.
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

    // Pred unistavanje ViewModel-a gasimo sve listenere.
    // Bez ovoga bi memorija curila, a Firestore bi nastavio da naplacuje citanja.
    override fun onCleared() {
        super.onCleared()
        apartmentListener?.remove()
        recListener?.remove()
        contactListener?.remove()
        apartmentsListListener?.remove()
    }
}
