package com.example.guestguide.data.model

// Kontakt broj vezan za apartman (taxi, policija, hitna, ostalo)
data class Contact(
    val id: String = "",
    val name: String = "",
    val number: String = "",
    val type: ContactType = ContactType.OTHER
)

// Kategorije kontakata za brzo prepoznavanje ikone i tipa
enum class ContactType {
    TAXI, POLICE, AMBULANCE, OTHER
}