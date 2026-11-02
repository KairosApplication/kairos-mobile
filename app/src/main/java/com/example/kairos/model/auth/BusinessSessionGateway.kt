package com.example.kairos.model.auth

interface BusinessSessionGateway {
    fun session(uid: String): BusinessIdentity
    fun link(uid: String, password: String): BusinessIdentity
    fun register(uid: String, profile: UserProfile): BusinessIdentity = error("Cadastro indisponível.")
}
