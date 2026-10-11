package com.example.kairos.auth

import com.example.kairos.model.auth.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AccountRepositoryTest {
    private val initial = UserProfile("a", "João", "Silva", LocalDate.of(1985, 10, 22),
        "12364578902", "old@example.com", "12345678", "basico")
    private class AuthFake : AuthGateway {
        var identity: AuthIdentity? = AuthIdentity("a", "old@example.com")
        var emailRequested: String? = null
        var passwordUpdated: String? = null
        var credential: String? = null
        var refreshes = 0
        var fail = false
        override fun create(email: String, password: String) = error("Unused")
        override fun signIn(email: String, password: String) = error("Unused")
        override fun current() = identity
        override fun sendResetLink(email: String) = Unit
        override fun signOut() { identity = null }
        override fun refreshToken() { refreshes++ }
        override fun requestEmailChange(uid: String, email: String, password: String) {
            require(uid == identity?.uid)
            if (fail) throw IllegalArgumentException("Senha atual incorreta")
            credential = password
            emailRequested = email
        }
        override fun changePassword(uid: String, currentPassword: String, newPassword: String) {
            require(uid == identity?.uid)
            if (fail) throw IllegalArgumentException("Senha atual incorreta")
            credential = currentPassword
            passwordUpdated = newPassword
        }
    }
    private class StoreFake(var profile: UserProfile) : ProfileStore {
        var writes = 0
        override fun read(uid: String) = profile
        override fun create(profile: UserProfile) = error("Unused")
        override fun updateName(uid: String, name: String, lastName: String): UserProfile {
            writes++
            return profile.copy(name = name, lastName = lastName).also { profile = it }
        }
        override fun updateEmail(uid: String, email: String): UserProfile {
            writes++
            return profile.copy(email = email).also { profile = it }
        }
    }
    private val auth = AuthFake()
    private val store = StoreFake(initial)
    private val repo = FirebaseAuthRepository(auth, store)

    @Test fun nameUpdateTrimsAndPreservesImmutableData() {
        val updated = repo.updateName("a", " Maria Alice ", " de Souza ")
        assertEquals(initial.copy(name = "Maria Alice", lastName = "de Souza"), updated.profile)
        assertEquals(1, store.writes)
        assertFalse(updated.profile!!.toDocument().containsKey("password"))
    }
    @Test fun emailStaysUnchangedUntilTheVerifiedIdentityChanges() {
        val requested = repo.requestEmailChange("a", " new@example.com ", "current-password")
        assertEquals("new@example.com", auth.emailRequested)
        assertEquals("old@example.com", requested.email)
        assertEquals(0, store.writes)
        auth.identity = AuthIdentity("a", "new@example.com")
        val refreshed = repo.restoreSession()!!
        assertEquals("new@example.com", refreshed.email)
        assertEquals("new@example.com", refreshed.profile!!.email)
        assertEquals(1, auth.refreshes)
        assertEquals(1, store.writes)
        repo.restoreSession()
        assertEquals(1, store.writes)
    }
    @Test fun passwordChangeOnlyUsesAuthAndNeverWritesTheProfile() {
        val updated = repo.changePassword("a", "current-password", "new-password")
        assertEquals("current-password", auth.credential)
        assertEquals("new-password", auth.passwordUpdated)
        assertEquals(initial, updated.profile)
        assertEquals(0, store.writes)
    }
    @Test fun invalidInputAndAnotherAccountAreRejectedBeforeWrites() {
        fun rejects(action: () -> Unit) {
            try { action(); fail("Should reject mutation") } catch (_: IllegalArgumentException) { }
        }
        rejects { repo.updateName("a", " ", "Silva") }
        rejects { repo.requestEmailChange("a", "invalid", "password") }
        rejects { repo.changePassword("a", "password", "123") }
        auth.identity = AuthIdentity("b", "b@example.com")
        rejects { repo.updateName("a", "Maria", "Silva") }
        rejects { repo.requestEmailChange("a", "new@example.com", "password") }
        rejects { repo.changePassword("a", "password", "password-updated") }
        assertEquals(0, store.writes)
        assertNull(auth.emailRequested)
        assertNull(auth.passwordUpdated)
    }
    @Test fun failedReauthenticationDoesNotChangeEmailOrPassword() {
        auth.fail = true
        try { repo.requestEmailChange("a", "new@example.com", "bad-password"); fail() } catch (_: IllegalArgumentException) { }
        try { repo.changePassword("a", "bad-password", "new-password"); fail() } catch (_: IllegalArgumentException) { }
        assertNull(auth.emailRequested)
        assertNull(auth.passwordUpdated)
        assertEquals(0, store.writes)
    }
    @Test fun credentialRequestsDoNotExposeSecretsInDiagnostics() {
        val email = AccountEditRequest.Email("a", "private@example.com", "current-password")
        val password = AccountEditRequest.Password("a", "current-password", "new-password")
        for (request in listOf(email, password)) {
            assertFalse(request.toString().contains("current-password"))
            assertFalse(request.toString().contains("new-password"))
            assertFalse(request.toString().contains("private@example.com"))
        }
        assertEquals("***.645.***-02", AccountValidation.maskCpf("123.645.789-02"))
        assertNull(AccountValidation.maskCpf(""))
        assertNull(AccountValidation.maskCpf("123"))
    }
}
