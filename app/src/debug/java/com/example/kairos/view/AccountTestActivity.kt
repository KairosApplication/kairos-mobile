package com.example.kairos.view

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

/** Isolated debug host for UI tests: no auth observers, production reads or splash dialogs. */
class AccountTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(FrameLayout(this))
    }
}
