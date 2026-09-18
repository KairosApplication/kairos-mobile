package com.example.kairos.model.auth

import java.time.LocalDate

data class ProfileDetails(
    val name: String,
    val lastName: String,
    val birthDate: LocalDate,
    val cpf: String,
    val zipCode: String,
    val plan: String
) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 100) { "Informe o nome (até 100 caracteres)." }
        require(lastName.isNotBlank() && lastName.length <= 100) { "Informe o sobrenome (até 100 caracteres)." }
        require(!birthDate.isAfter(LocalDate.now())) { "Nascimento não pode estar no futuro." }
        require(cpf.matches(Regex("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{11}"))) { "Informe o CPF com 11 dígitos." }
        require(zipCode.matches(Regex("\\d{5}-?\\d{3}"))) { "Informe o CEP com 8 dígitos." }
        require(plan.isNotBlank() && plan.length <= 20) { "Informe o plano (até 20 caracteres)." }
    }

    fun toProfile(uid: String, email: String) =
        UserProfile(uid, name, lastName, birthDate, cpf.filter(Char::isDigit),
            email, zipCode.filter(Char::isDigit), plan)
}
