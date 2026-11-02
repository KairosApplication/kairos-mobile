package com.example.kairos.auth

import com.example.kairos.model.network.*
import org.junit.Test
import org.junit.Assert.*

class ApiConfigurationTest {
    @Test fun localHttpIsOnlyAllowedForDevelopmentLoopback() {
        for (host in listOf("127.0.0.1", "localhost", "10.0.2.2")) {
            ApiOrigin.validate("http://$host:8081", allowLocalHttp = true)
            try { ApiOrigin.validate("http://$host:8081", allowLocalHttp = false); fail() }
            catch (_: ServiceConfigurationException) { }
        }
        for (url in listOf("http://example.com", "http://192.168.1.20:8081", "http://127.0.0.1.evil.example", "http://user:pass@127.0.0.1")) {
            try { ApiOrigin.validate(url, allowLocalHttp = true); fail() }
            catch (_: ServiceConfigurationException) { }
        }
    }
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
