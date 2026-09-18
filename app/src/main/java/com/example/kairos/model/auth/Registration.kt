package com.example.kairos.model.auth

import java.time.LocalDate

/** Senha somente em trânsito para Firebase Auth; nunca faz parte do documento Firestore. */
data class Registration(
    val name: String,
    val lastName: String,
    val birthDate: LocalDate,
    val cpf: String,
    val email: String,
    val password: String,
    val zipCode: String,
    val plan: String
) {
    fun details() = ProfileDetails(name, lastName, birthDate, cpf, zipCode, plan)

    fun validate() {
        details().validate()
        AuthValidation.email(email)
        AuthValidation.password(password)
        require(password.length >= 6) { "Use uma senha com pelo menos 6 caracteres." }
    }

    override fun toString() = "Registration(email=[redacted], password=[redacted])"
}

object AuthValidation {
    fun email(value: String) {
        require(value.length <= 255 && value.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) { "Informe um email válido." }
    }
    fun password(value: String) {
        require(value.isNotBlank() && value.length <= 255) { "Informe uma senha (até 255 caracteres)." }
    }
}
