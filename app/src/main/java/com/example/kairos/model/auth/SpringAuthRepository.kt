package com.example.kairos.model.auth

import com.example.kairos.model.network.SpringSession

class SpringAuthRepository(private val session: SpringSession) : AuthRepository {
    override fun login(email: String, password: String) = session.login(email.trim(), password)
    override fun restoreSession() = session.restore()
    override fun logout() = session.logout()
    override fun invalidateSession() = session.invalidate()
    override fun registerCustomerAccount(request: CustomerRegistration) = session.register(request)
    override fun registerAccount(email: String, password: String): SignedInUser = registrationUnavailable()
    override fun register(request: Registration): SignedInUser = registrationUnavailable()
    override fun completeProfile(details: ProfileDetails): SignedInUser = registrationUnavailable()
    private fun registrationUnavailable(): Nothing = throw IllegalArgumentException(
        "O cadastro pelo aplicativo ainda depende da seleção de um mercado. Nenhuma conta foi criada.")
    override fun updateName(uid: String, name: String, lastName: String): SignedInUser = accountUnavailable()
    override fun requestEmailChange(uid: String, email: String, currentPassword: String): SignedInUser = accountUnavailable()
    override fun changePassword(uid: String, currentPassword: String, newPassword: String): SignedInUser = accountUnavailable()
    private fun accountUnavailable(): Nothing = throw IllegalArgumentException(
        "A edição da própria conta ainda não está disponível no aplicativo. Nenhum dado foi alterado.")
}
