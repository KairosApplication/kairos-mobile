package com.example.kairos.model.auth

enum class AccountOperation { NAME, EMAIL, PASSWORD, REFRESH }

sealed class AccountEditRequest(val uid: String, val operation: AccountOperation) {
    class Name(uid: String, val name: String, val lastName: String) : AccountEditRequest(uid, AccountOperation.NAME)
    class Email(uid: String, val email: String, val currentPassword: String) : AccountEditRequest(uid, AccountOperation.EMAIL)
    class Password(uid: String, val currentPassword: String, val newPassword: String) : AccountEditRequest(uid, AccountOperation.PASSWORD)

    override fun toString() = "AccountEditRequest(operation=$operation, data=[redacted])"
}

class AccountChangedException : IllegalArgumentException("A sessão mudou. Faça login novamente.")

object AccountValidation {
    fun name(name: String, lastName: String) {
        require(name.isNotBlank() && name.length <= 100) { "Informe o nome (até 100 caracteres)." }
        require(lastName.isNotBlank() && lastName.length <= 100) { "Informe o sobrenome (até 100 caracteres)." }
    }
    fun password(current: String, password: String, confirmation: String = password) {
        AuthValidation.password(current)
        AuthValidation.password(password)
        require(password.length >= 6) { "Use uma senha com pelo menos 6 caracteres." }
        require(password == confirmation) { "As senhas não coincidem." }
        require(password != current) { "A nova senha deve ser diferente da senha atual." }
    }
    fun maskCpf(cpf: String?): String? {
        val digits = cpf.orEmpty().filter(Char::isDigit)
        return if (digits.length == 11) "***.${digits.substring(3, 6)}.***-${digits.takeLast(2)}" else null
    }
    fun fullName(user: SignedInUser?): String = user?.profile?.let {
        "${it.name.trim()} ${it.lastName.trim()}".trim()
    }.orEmpty()
}
