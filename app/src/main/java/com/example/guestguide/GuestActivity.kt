package com.example.guestguide

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Activity za gosta. Drzi GuestHomeFragment i GuestExploreFragment.
// Prima accessCode preko Intent extras-a iz WelcomeActivity.
class GuestActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCESS_CODE = "accessCode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guest)
    }
}
