package com.example.guestguide.data.model

data class Contact(
    val id: String = "",
    val name: String = "",
    val number: String = "",
    val type: ContactType = ContactType.OTHER
)

enum class ContactType {
    TAXI, POLICE, AMBULANCE, OTHER
}