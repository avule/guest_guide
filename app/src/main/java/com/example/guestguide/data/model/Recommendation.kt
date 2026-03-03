package com.example.guestguide.data.model

data class Recommendation(
    val id: String = "",
    val apartmentCode: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val rating: Double = 0.0,
    val imageUrl: String = "",
    val mapLink: String = ""
)