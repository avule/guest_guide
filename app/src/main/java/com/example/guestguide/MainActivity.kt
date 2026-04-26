package com.example.guestguide

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Jedina Activity u aplikaciji. Drzi NavHostFragment unutar layout-a.
// Svi ekrani (Welcome, Admin, Guest) su fragmenti.
class MainActivity : AppCompatActivity() {

    companion object {
        // Sluzi da razlikujemo rotaciju ekrana od potpunog restarta procesa.
        private var isProcessRunning = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ako je proces tek startovan, ignorisemo stari state da Navigation
        // ne pokusa da vrati back stack koji vise nema smisla.
        val effectiveState = if (isProcessRunning) savedInstanceState else null
        super.onCreate(effectiveState)
        setContentView(R.layout.activity_main)
        isProcessRunning = true
    }
}