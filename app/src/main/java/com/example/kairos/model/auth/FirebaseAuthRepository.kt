package com.example.kairos.model.auth

/** Coordena Auth e Firestore; cadastro parcial pode ser retomado sem criar outra conta. */
class FirebaseAuthRepository(
    private val auth: AuthGateway,
    private val profiles: ProfileStore
) : AuthRepository {
    override fun registerAccount(email: String, password: String): SignedInUser {
        AuthValidation.email(email)
        AuthValidation.password(password)
        require(password.length >= 6) { "Use uma senha com pelo menos 6 caracteres." }
        val identity = auth.create(email, password)
        return SignedInUser(identity.uid, identity.email, null)
    }

    override fun register(request: Registration): SignedInUser {
        request.validate()
        val identity = auth.create(request.email, request.password)
        return try {
            val profile = profiles.create(request.details().toProfile(identity.uid, identity.email))
            SignedInUser(identity.uid, identity.email, profile)
        } catch (e: Exception) {
            throw ProfileIncompleteException(SignedInUser(identity.uid, identity.email, null), e)
        }
    }

    override fun login(email: String, password: String): SignedInUser {
        AuthValidation.email(email)
        AuthValidation.password(password)
        return session(auth.signIn(email, password))
    }

    override fun restoreSession(): SignedInUser? = auth.current()?.let(::session)

    private fun session(identity: AuthIdentity): SignedInUser {
        var profile = profiles.read(identity.uid)
        check(profile == null || profile.uid == identity.uid) { "Perfil não corresponde à sessão." }
        if (profile != null && profile.email != identity.email) {
            auth.refreshToken()
            profile = profiles.updateEmail(identity.uid, identity.email)
            check(profile.uid == identity.uid) { "Perfil não corresponde à sessão." }
        }
        return SignedInUser(identity.uid, identity.email, profile)
    }

    override fun completeProfile(details: ProfileDetails): SignedInUser {
        details.validate()
        val identity = auth.current() ?: throw IllegalArgumentException("Faça login novamente.")
        return SignedInUser(identity.uid, identity.email,
            profiles.create(details.toProfile(identity.uid, identity.email)))
    }

    override fun requestPasswordReset(email: String) {
        AuthValidation.email(email)
        auth.sendResetLink(email)
    }

    override fun logout() = auth.signOut()

    private fun currentAccount(uid: String): SignedInUser {
        val identity = auth.current() ?: throw AccountChangedException()
        if (identity.uid != uid) throw AccountChangedException()
        return session(identity)
    }

    private fun assertOwner(uid: String) {
        if (auth.currentUid() != uid) throw AccountChangedException()
    }

    override fun updateName(uid: String, name: String, lastName: String): SignedInUser {
        val first = name.trim()
        val last = lastName.trim()
        AccountValidation.name(first, last)
        val account = currentAccount(uid)
        require(account.profile != null) { "Complete o cadastro antes de editar sua conta." }
        val profile = profiles.updateName(uid, first, last)
        assertOwner(uid)
        check(profile.uid == uid) { "Perfil não corresponde à sessão." }
        return account.copy(profile = profile)
    }

    override fun requestEmailChange(uid: String, email: String, currentPassword: String): SignedInUser {
        val newEmail = email.trim()
        AuthValidation.email(newEmail)
        AuthValidation.password(currentPassword)
        val account = currentAccount(uid)
        require(!newEmail.equals(account.email, ignoreCase = true)) { "Informe um e-mail diferente do atual." }
        auth.requestEmailChange(uid, newEmail, currentPassword)
        assertOwner(uid)
        // The old address remains authoritative until the new address is verified.
        return account
    }

    override fun changePassword(uid: String, currentPassword: String, newPassword: String): SignedInUser {
        AccountValidation.password(currentPassword, newPassword)
        val account = currentAccount(uid)
        auth.changePassword(uid, currentPassword, newPassword)
        assertOwner(uid)
        return account
    }
}
