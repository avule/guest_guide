package com.example.guestguide.data.model

// Preporuka za gosta. Moze biti restoran, kafic, vinarija ili znamenitost.
// Cuva se kao sub-kolekcija unutar apartmana: apartments/{accessCode}/recommendations/{id}
data class Recommendation(
    val id: String = "",            // Auto-generisan od strane Firestore-a.
    val apartmentCode: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",      // Hrana, Pice, Vinarija ili Znamenitost.
    val rating: Double = 0.0,
    val imageUrl: String = "",
    val mapLink: String = ""
)