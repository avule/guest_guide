package com.example.guestguide.utils

// Omotac za rezultate async operacija. Postoje tri stanja: Loading, Success, Error.
// Sealed klasa znaci da kompajler trazi da pokrijem sva tri stanja u when bloku.
sealed class Resource<out T> {
    object Loading : Resource<Nothing>()
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String) : Resource<Nothing>()
}