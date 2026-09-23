package com.example.kairos.auth

import com.example.kairos.model.auth.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FirebaseAuthRepositoryTest {
    private val request = Registration("Ana", "Silva", LocalDate.of(2000, 1, 1),
        "123.456.789-01", "ana@example.com", "senhaTeste", "12345-678", "basico")
    private class AuthFake : AuthGateway {
        var identity: AuthIdentity? = null
        var creates = 0
        var resetEmail: String? = null
        override fun create(email: String, password: String): AuthIdentity {
            creates++
            return AuthIdentity("firebase-uid", email).also { identity = it }
        }
        override fun signIn(email: String, password: String) =
            AuthIdentity("firebase-uid", email).also { identity = it }
        override fun current() = identity
        override fun sendResetLink(email: String) { resetEmail = email }
        override fun signOut() { identity = null }
    }
    private class StoreFake : ProfileStore {
        var fail = false
        var saved: UserProfile? = null
        override fun read(uid: String): UserProfile? {
            if (fail) error("offline")
            return saved
        }
        override fun create(profile: UserProfile): UserProfile {
            if (fail) error("offline")
            return saved ?: profile.also { saved = it }
        }
    }
    private val auth = AuthFake()
    private val store = StoreFake()
    private val repo = FirebaseAuthRepository(auth, store)

    @Test fun createsAccountThenCompletesProfileWithoutCreatingAnotherAccount() {
        val account = repo.registerAccount(request.email, request.password)
        assertNull(account.profile)
        assertNull(store.saved)
        assertEquals(account, repo.restoreSession())
        val completed = repo.completeProfile(request.details())
        assertNotNull(completed.profile)
        assertEquals(account.uid, completed.uid)
        assertEquals(1, auth.creates)
    }

    @Test fun rejectsInvalidCredentialsBeforeCreatingAccount() {
        listOf("invalid" to "senhaTeste", request.email to "123").forEach { (email, password) ->
            try { repo.registerAccount(email, password); fail() }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, auth.creates)
    }

    @Test fun savesModelFieldsWithoutPassword() {
        val user = repo.register(request)
        val data = user.profile!!.toDocument()
        assertEquals("firebase-uid", user.uid)
        assertEquals("12345678901", data["cpf"])
        assertEquals("12345678", data["zipCode"])
        assertEquals("2000-01-01", data["birthDate"])
        assertEquals(8, data.size)
        assertFalse(data.containsKey("password"))
        assertFalse(request.toString().contains(request.password))
        assertEquals(user.profile, UserProfile.fromDocument(data))
    }

    @Test fun resumesPartialRegistrationWithoutAnotherAccount() {
        store.fail = true
        try { repo.register(request); fail() }
        catch (e: ProfileIncompleteException) { assertNull(e.user.profile) }
        assertEquals(1, auth.creates)
        store.fail = false
        assertNull(repo.restoreSession()!!.profile)
        assertNotNull(repo.completeProfile(request.details()).profile)
        assertEquals(1, auth.creates)
    }

    @Test fun restoresExistingProfileAndLogsOut() {
        val user = repo.register(request)
        assertEquals(user, repo.restoreSession())
        repo.logout()
        assertNull(repo.restoreSession())
        assertEquals(user, repo.login(request.email, request.password))
    }

    @Test fun missingProfileRequiresCompletion() {
        assertNull(repo.login(request.email, request.password).profile)
    }

    @Test fun profileCompletionIsIdempotent() {
        repo.register(request)
        repo.completeProfile(request.details().copy(name = "Outro nome"))
        assertEquals("Ana", store.saved!!.name)
    }

    @Test fun resetDelegatesOnlyEmail() {
        repo.requestPasswordReset(request.email)
        assertEquals(request.email, auth.resetEmail)
    }

    @Test fun rejectsInvalidDataBeforeCreatingAccount() {
        try { repo.register(request.copy(password = "123")); fail() }
        catch (_: IllegalArgumentException) { }
        try { repo.register(request.copy(plan = "")); fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, auth.creates)
    }

    @Test fun readFailureDoesNotMasqueradeAsMissingProfile() {
        repo.register(request)
        store.fail = true
        try { repo.restoreSession(); fail() }
        catch (_: IllegalStateException) { }
    }

    @Test fun rejectsAnotherUsersProfile() {
        repo.register(request)
        store.saved = store.saved!!.copy(uid = "other-uid")
        try { repo.restoreSession(); fail() }
        catch (_: IllegalStateException) { }
    }
}
