package com.example.kairos.model.cliente

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object ReceiptAppCheck {
    fun install(app: FirebaseApp) {
        val check=FirebaseAppCheck.getInstance(app)
        check.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        // The SDK generates a per-installation secret; never embed one in BuildConfig.
        check.getAppCheckToken(false).addOnFailureListener { /* Registration is completed in the Firebase console. */ }
    }
}
