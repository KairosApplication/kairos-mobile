package com.example.kairos.model.auth

import java.time.LocalDate

/** Documento users/{uid}. Senhas são gerenciadas exclusivamente pelo Firebase Auth. */
data class UserProfile(
    val uid: String,
    val name: String,
    val lastName: String,
    val birthDate: LocalDate,
    val cpf: String,
    val email: String,
    val zipCode: String,
    val plan: String
) {
    fun toDocument(): Map<String, String> = mapOf(
        "uid" to uid, "name" to name, "lastName" to lastName,
        "birthDate" to birthDate.toString(), "cpf" to cpf.filter(Char::isDigit),
        "email" to email, "zipCode" to zipCode.filter(Char::isDigit), "plan" to plan
    )

    companion object {
        fun fromDocument(data: Map<String, Any>): UserProfile {
            fun text(key: String) = data[key] as? String ?: error("Perfil inválido: $key")
            return UserProfile(text("uid"), text("name"), text("lastName"),
                LocalDate.parse(text("birthDate")), text("cpf"), text("email"), text("zipCode"), text("plan"))
        }
    }
}
