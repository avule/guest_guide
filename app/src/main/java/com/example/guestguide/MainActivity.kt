package com.example.guestguide

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private var isProcessRunning = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Na svježem startu procesa, ignorišemo savedInstanceState
        // da Navigation Component ne restaurira prethodni back stack.
        // Na rotaciji ekrana (isProcessRunning == true), čuvamo stanje normalno.
        val effectiveState = if (isProcessRunning) savedInstanceState else null
        super.onCreate(effectiveState)
        setContentView(R.layout.activity_main)
        isProcessRunning = true
    }
}