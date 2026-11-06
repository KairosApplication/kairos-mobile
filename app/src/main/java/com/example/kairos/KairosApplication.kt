package com.example.kairos

import android.app.Application
import com.example.kairos.model.cliente.FirebaseReceiptAi

class KairosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseReceiptAi.initialize(this)
    }
}
