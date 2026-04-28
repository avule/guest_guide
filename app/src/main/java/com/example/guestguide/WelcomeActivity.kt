package com.example.guestguide

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Pocetna Activity. Drzi samo WelcomeFragment.
// Iz njega gost ide u GuestActivity, a admin u AdminActivity preko Intent-a.
class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
    }
}
