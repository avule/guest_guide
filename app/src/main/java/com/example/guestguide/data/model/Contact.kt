package com.example.guestguide.data.model

// Vazan broj telefona za gosta. Moze biti taxi, policija, hitna ili nesto drugo.
data class Contact(
    val id: String = "",
    val name: String = "",
    val number: String = "",
    val type: ContactType = ContactType.OTHER
)

// Tipovi kontakata. Odredjuju koja se ikonica prikazuje uz kontakt.
enum class ContactType {
    TAXI, POLICE, AMBULANCE, OTHER
}