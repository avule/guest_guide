package com.example.guestguide.data.model

// Model apartmana — mapira se direktno na Firestore dokument.
// accessCode služi kao jedinstveni ID i kao kod za pristup gostiju.
data class Apartment(
    val accessCode: String = "",
    val name: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val ownerContact: String = "",
    val houseRules: String = "",
    val ownerId: String = "",
    val imageUrl: String = "",
    val location: String = ""
)