package com.example.kairos.model.auth

/** Adaptadores bloqueantes: executar somente no executor do ViewModel. */
data class AuthIdentity(val uid: String, val email: String)

interface AuthGateway {
    fun create(email: String, password: String): AuthIdentity
    fun signIn(email: String, password: String): AuthIdentity
    fun current(): AuthIdentity?
    fun sendResetLink(email: String)
    fun signOut()
    fun currentUid(): String? = current()?.uid
    fun refreshToken() = Unit
    fun requestEmailChange(uid: String, email: String, password: String): Unit = error("Operação indisponível.")
    fun changePassword(uid: String, currentPassword: String, newPassword: String): Unit = error("Operação indisponível.")
}

interface ProfileStore {
    fun read(uid: String): UserProfile?
    fun create(profile: UserProfile): UserProfile
    fun updateName(uid: String, name: String, lastName: String): UserProfile = error("Operação indisponível.")
    fun updateEmail(uid: String, email: String): UserProfile = error("Operação indisponível.")
}

class ProfileIncompleteException(val user: SignedInUser, cause: Exception) :
    Exception("Conta criada, mas o perfil ainda não foi salvo. Complete o cadastro para continuar.", cause)
