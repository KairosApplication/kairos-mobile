package com.example.kairos.model.auth

/** UID do Firebase é String, não o SERIAL do PostgreSQL. */
data class SignedInUser(val uid: String, val email: String, val profile: UserProfile?) {
    val name: String get() = profile?.name.orEmpty()
}
