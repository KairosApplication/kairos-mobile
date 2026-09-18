package com.example.kairos.auth

import com.example.kairos.model.auth.AuthErrorMessage
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeoutException

class AuthErrorMessageTest {
    @Test fun permissionErrorShowsCodeWithoutRawDetails() {
        val message = AuthErrorMessage.firestoreCode("PERMISSION_DENIED")
        assertTrue(message.contains("PERMISSION_DENIED"))
        assertFalse(AuthErrorMessage.from(Exception("private-user-data")).contains("private-user-data"))
    }

    @Test fun distinguishesUnavailableFromTimeout() {
        assertTrue(AuthErrorMessage.firestoreCode("UNAVAILABLE").contains("UNAVAILABLE"))
        assertTrue(AuthErrorMessage.from(TimeoutException()).contains("TIMEOUT"))
    }
}
