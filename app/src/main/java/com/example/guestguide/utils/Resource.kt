package com.example.guestguide.utils

// Omotač za rezultate async operacija — koristi se u ViewModelu i fragmentima
// da se zna da li je podatak u procesu učitavanja, uspješan ili greška.
sealed class Resource<out T> {
    object Loading : Resource<Nothing>()
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String) : Resource<Nothing>()
}