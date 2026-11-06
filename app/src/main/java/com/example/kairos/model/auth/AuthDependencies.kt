package com.example.kairos.model.auth

import android.content.Context

object AuthDependencies {
    @Volatile private var repository: AuthRepository? = null

    @Synchronized
    fun repository(context: Context): AuthRepository? {
        repository?.let { return it }
        val session = com.example.kairos.model.network.ApiDependencies.initialize(context.applicationContext)
        return SpringAuthRepository(session).also { repository = it }
    }
}
