package com.example.guestguide

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Jedina aktivnost u aplikaciji — koristi Navigation Component za upravljanje fragmentima.
// Svi ekrani (Welcome, Admin, Guest) su fragmenti unutar NavHostFragment-a.
class MainActivity : AppCompatActivity() {

    companion object {
        // Sprečava ponovni restore stanja pri procesnom restartu
        private var isProcessRunning = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Na svježem startu procesa ignorišemo savedInstanceState da Navigation Component
        // ne restaurira prethodni back stack. Na rotaciji (isProcessRunning == true) čuvamo stanje.
        val effectiveState = if (isProcessRunning) savedInstanceState else null
        super.onCreate(effectiveState)
        setContentView(R.layout.activity_main)
        isProcessRunning = true
    }
}