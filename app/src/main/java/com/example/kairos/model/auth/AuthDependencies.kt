package com.example.kairos.model.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

object AuthDependencies {
    @Volatile private var repository: AuthRepository? = null

    @Synchronized
    fun repository(context: Context): AuthRepository? {
        repository?.let { return it }
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(context.applicationContext) ?: return null
        val firestore = FirebaseFirestore.getInstance(app)
        // Perfis com CPF não ficam em cache persistente no dispositivo.
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        return FirebaseAuthRepository(
            FirebaseAuthGateway(FirebaseAuth.getInstance(app)),
            FirestoreProfileStore(firestore)
        ).also { repository = it }
    }
}
