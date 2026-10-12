package com.example.kairos.model.auth

/** Chamadas executadas fora da thread de UI. */
interface AuthRepository {
    fun registerAccount(email: String, password: String): SignedInUser
    fun register(request: Registration): SignedInUser
    fun login(email: String, password: String): SignedInUser
    fun restoreSession(): SignedInUser?
    fun completeProfile(details: ProfileDetails): SignedInUser
    fun requestPasswordReset(email: String)
    fun logout()
    fun updateName(uid: String, name: String, lastName: String): SignedInUser = error("Operação indisponível.")
    fun requestEmailChange(uid: String, email: String, currentPassword: String): SignedInUser = error("Operação indisponível.")
    fun changePassword(uid: String, currentPassword: String, newPassword: String): SignedInUser = error("Operação indisponível.")
}
