package com.example.guestguide.data.model

// Model apartmana. Direktno se mapira na Firestore dokument.
// accessCode je istovremeno i ID dokumenta i pristupni kod za goste, jedan string radi oboje.
// Default vrijednosti su bitne, Firestore za toObject() trazi prazan konstruktor.
data class Apartment(
    val accessCode: String = "",
    val name: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val ownerContact: String = "",
    val houseRules: String = "",
    val ownerId: String = "",   // Firebase Auth UID vlasnika, koristi ga Security Rules.
    val imageUrl: String = "",
    val location: String = ""
)