package com.example.kairos.auth

import com.example.kairos.model.auth.*
import com.example.kairos.model.network.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BusinessAvailabilityTest {
    private fun repository(error: Exception): AuthRepository {
        val profile = UserProfile("firebase-user", "Ana", "Silva", LocalDate.of(2000, 1, 1), "", "ana@example.com", "", "public")
        val auth = object : AuthGateway {
            override fun current() = AuthIdentity(profile.uid, profile.email, false)
            override fun signIn(email: String, password: String) = current()
            override fun create(email: String, password: String) = error("Unused")
            override fun signOut() = Unit
        }
        val profiles = object : ProfileStore {
            override fun read(uid: String) = profile
            override fun create(profile: UserProfile) = profile
        }
        val business = object : BusinessSessionGateway {
            override fun session(uid: String): BusinessIdentity = throw error
            override fun link(uid: String, password: String): BusinessIdentity = throw error
        }
        return FirebaseAuthRepository(auth, profiles, business)
    }

    @Test fun missingServiceConfigurationPreservesFirebaseLoginWithoutGrantingBusinessAccess() {
        val repo = repository(ServiceConfigurationException())
        val user = repo.login("ana@example.com", "test-password")
        assertEquals("firebase-user", user.uid)
        assertNotNull(user.profile)
        assertNull(user.business)
        assertTrue(user.businessIssue!!.contains("não foi configurada"))
        assertEquals(user, repo.restoreSession())
    }

    @Test fun unavailableOrForbiddenBusinessServicesDoNotTurnIntoInvalidPasswordOrFakeAccess() {
        for (status in listOf(403, 503)) {
            val user = repository(ApiFailure(status)).login("ana@example.com", "test-password")
            assertEquals("firebase-user", user.uid)
            assertNull(user.business)
            assertFalse(user.businessIssue.isNullOrBlank())
        }
    }

    @Test fun rejectedBusinessTokenPreservesFirebaseLoginWithoutGrantingAccess() {
        val user = repository(ApiFailure(401)).login("ana@example.com", "test-password")
        assertEquals("firebase-user", user.uid)
        assertNull(user.business)
        assertNotNull(user.profile)
        assertTrue(user.businessIssue!!.contains("verificada"))
    }
}
