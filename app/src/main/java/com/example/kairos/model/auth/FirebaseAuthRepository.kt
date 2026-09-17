package com.example.kairos.model.auth

/** Coordena Auth e Firestore; cadastro parcial pode ser retomado sem criar outra conta. */
class FirebaseAuthRepository(
    private val auth: AuthGateway,
    private val profiles: ProfileStore
) : AuthRepository {
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
        val profile = profiles.read(identity.uid)
        check(profile == null || profile.uid == identity.uid) { "Perfil não corresponde à sessão." }
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
}
