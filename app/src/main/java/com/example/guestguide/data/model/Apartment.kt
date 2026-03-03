package com.example.guestguide.data.model

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