package com.example.guestguide

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Activity za vlasnika apartmana. Drzi AdminLoginFragment i AdminSetupFragment.
// Logout u ovoj Activity poziva finish() i vraca korisnika na WelcomeActivity.
class AdminActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)
    }
}
