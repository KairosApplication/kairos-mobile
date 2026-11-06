package com.example.kairos.model.auth

import java.time.LocalDate
import org.json.JSONObject

/** Contract of POST /api/v1/users/registration; credentials exist only in transit. */
class CustomerRegistration(val name: String, val lastName: String, val birthDate: LocalDate,
    val cpf: String, val email: String, val password: String, val companyId: Long) {
    fun validate() {
        AccountValidation.name(name,lastName)
        AuthValidation.email(email); AuthValidation.password(password)
        require(password.length>=6) { "Use uma senha com pelo menos 6 caracteres." }
        require(!birthDate.isAfter(LocalDate.now())) { "Nascimento não pode estar no futuro." }
        require(companyId>0) { "Informe o código do mercado fornecido pelo estabelecimento." }
        require(cpf.matches(Regex("\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}"))) { "Informe um CPF válido." }
        val digits=cpf.filter(Char::isDigit).map { it.digitToInt() }
        require(digits.distinct().size>1) { "Informe um CPF válido." }
        for(length in 9..10) {
            val remainder=(0 until length).sumOf { digits[it]*(length+1-it) }%11
            require(digits[length]==if(remainder<2) 0 else 11-remainder) { "Informe um CPF válido." }
        }
    }
    fun json(): JSONObject {
        validate()
        return JSONObject().put("name",name.trim()).put("lastName",lastName.trim())
            .put("birthDate",birthDate.toString()).put("cpf",cpf.filter(Char::isDigit))
            .put("email",email.trim()).put("password",password).put("companyId",companyId)
    }
    override fun toString()="CustomerRegistration(data=[redacted])"
}
