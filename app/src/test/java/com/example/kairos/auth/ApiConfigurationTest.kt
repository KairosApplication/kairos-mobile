package com.example.kairos.auth

import com.example.kairos.model.network.*
import org.junit.Test
import org.junit.Assert.*

class ApiConfigurationTest {
    @Test fun unsafeOriginsAreRejectedBeforeReadingCredentials() {
        val tokens = object : IdentityTokenSource {
            override fun uid(): String? = error("Credentials should not be read")
            override fun token(uid: String, refresh: Boolean): String = error("Credentials should not be read")
        }
        for (url in listOf("", "http://example.com", "https://user:password@example.com", "https://example.com?target=evil", "https://example.com/path")) {
            try { KairosApi(url, tokens).request("uid", "/api/v1/mobile/session"); fail() }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun failureMessagesNeverContainResponseBodiesOrCredentials() {
        assertTrue(ApiFailure(401).message!!.contains("Sessão"))
        assertTrue(ApiFailure(403).message!!.contains("autorização"))
        assertTrue(ApiFailure(429).message!!.contains("Limite"))
    }
}
