package com.kairos.models.shared

import java.time.LocalDate

data class Users(val id: Int, val name: String, val lastName: String, val birthDate: LocalDate, val cpf: String, val email: String, val password: String, val zipCode: String, val plan: String)
