package com.example.kairos.auth

import com.example.kairos.model.auth.*
import com.example.kairos.model.scanner.ConfirmedRestocking
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class BusinessIdentityTest {
    @Test fun onlyLinkedStockersAndManagersWithCompanyAndEmployeeCanRestock() {
        for (role in listOf("CUSTOMER", "CASHIER", "UNLINKED", "MANAGER", "STOCKER", "ADMIN")) {
            assertEquals(role in setOf("MANAGER", "STOCKER"), BusinessIdentity(1, 2, 3, role, "Store").canRestock)
        }
        assertFalse(BusinessIdentity(null, 2, 3, "STOCKER", "Store").canRestock)
        assertFalse(BusinessIdentity(1, null, 3, "STOCKER", "Store").canRestock)
        assertFalse(BusinessIdentity(1, 2, null, "STOCKER", "Store").canRestock)
    }

    @Test fun firestorePlanCannotGrantBusinessAccess() {
        val profile = UserProfile("uid", "Ana", "Silva", LocalDate.of(2000, 1, 1), "", "test@example.com", "", "MANAGER")
        assertNull(SignedInUser("uid", "test@example.com", profile).business)
    }

    @Test fun businessLoginAndLinkDoNotRequireEmailConfirmation() {
        val auth = object : AuthGateway {
            override fun create(email: String, password: String) = error("unused")
            override fun signIn(email: String, password: String) = current()
            override fun current() = AuthIdentity("uid", "test@example.com", false)
            override fun sendResetLink(email: String) = Unit
            override fun signOut() = Unit
        }
        val profiles = object : ProfileStore {
            override fun read(uid: String): UserProfile? = null
            override fun create(profile: UserProfile) = profile
        }
        val business = object : BusinessSessionGateway {
            override fun session(uid: String) = BusinessIdentity(1, 2, 3, "STOCKER", "Store")
            override fun link(uid: String, password: String) = session(uid)
        }
        val repo = FirebaseAuthRepository(auth, profiles, business)
        val user = repo.login("test@example.com", "test-password")
        assertFalse(user.emailVerified)
        assertTrue(user.business!!.canRestock)
        assertTrue(repo.linkBusinessAccount("test-password").business!!.canRestock)
    }

    @Test fun accountSwitchDuringBusinessLookupRejectsOldIdentity() {
        var currentUid = "first"
        val auth = object : AuthGateway {
            override fun create(email: String, password: String) = error("unused")
            override fun signIn(email: String, password: String) = current()
            override fun current() = AuthIdentity(currentUid, "test@example.com")
            override fun currentUid() = currentUid
            override fun sendResetLink(email: String) = Unit
            override fun signOut() = Unit
        }
        val profiles = object : ProfileStore {
            override fun read(uid: String): UserProfile? = null
            override fun create(profile: UserProfile) = profile
        }
        val business = object : BusinessSessionGateway {
            override fun session(uid: String): BusinessIdentity {
                currentUid = "second"
                return BusinessIdentity(1, 2, 3, "MANAGER", "Other company")
            }
            override fun link(uid: String, password: String) = error("unused")
        }
        try { FirebaseAuthRepository(auth, profiles, business).restoreSession(); fail() }
        catch (_: AccountChangedException) { }
    }

    @Test fun invalidQuantitiesNeverBecomeConfirmableCommands() {
        for ((quantity, damages) in listOf(0 to 0, -1 to 0, 5 to -1, 5 to 6)) {
            try { ConfirmedRestocking("id", 1, 2, 3, quantity, damages).validate(); fail() }
            catch (_: IllegalArgumentException) { }
        }
        ConfirmedRestocking("id", 1, 2, 3, 5, 5).validate()
    }
}
