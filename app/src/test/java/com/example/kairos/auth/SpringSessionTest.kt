package com.example.kairos.auth

import com.example.kairos.model.network.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SpringSessionTest {
    private class MemoryStore : SessionStore {
        var value: String? = null
        override fun read() = value
        override fun write(value: String?) { this.value = value }
    }
    private class Server : SessionTransport {
        var failMe = 0
        var failLogout = false
        var rejectLogin = false
        var refreshes = 0
        var meCalls = 0
        var loginForm = ""
        var role = "CUSTOMER"
        var registrationStatus=201
        var registrationBody:JSONObject?=null
        var registrationCalls=0
        val calls = mutableListOf<String>()
        private fun access(): String {
            val claims = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"42\"}".toByteArray())
            return "fake.$claims.signature"
        }
        override fun send(path: String, method: String, headers: Map<String,String>, body: String?): SessionResponse {
            calls.add("$method $path")
            assertFalse(headers.containsKey("X-Kairos-Identity"))
            return when {
                path=="/api/v1/users/registration" -> {
                    registrationCalls++
                    assertEquals("POST",method)
                    assertEquals("test-csrf",headers["X-CSRF-TOKEN"])
                    assertTrue(headers["Cookie"].orEmpty().contains("csrf=test-csrf"))
                    assertTrue(headers["Content-Type"].orEmpty().startsWith("application/json"))
                    assertFalse(headers.containsKey("Authorization"))
                    registrationBody=JSONObject(body!!)
                    SessionResponse(registrationStatus,JSONObject().put("id",42)
                        .put("accessToken",access()).put("tokenType","Bearer").put("expiresIn",60).toString(),
                        listOf("refresh=registered; Path=/api/v1; Secure; HttpOnly"))
                }
                path == "/api/v1/auth/login" && method == "GET" -> SessionResponse(200,
                    "{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"test-csrf\"}", listOf("csrf=test-csrf; Path=/api/v1; Secure; HttpOnly"))
                path == "/api/v1/auth/login" -> {
                    assertEquals("test-csrf",headers["X-CSRF-TOKEN"])
                    assertTrue(headers["Cookie"].orEmpty().contains("csrf=test-csrf"))
                    loginForm=body.orEmpty()
                    if(rejectLogin) SessionResponse(401,"") else SessionResponse(200,
                        JSONObject().put("accessToken",access()).put("tokenType","Bearer").put("expiresIn",60).toString(),
                        listOf("refresh=first; Path=/api/v1; Secure; HttpOnly"))
                }
                path == "/api/v1/auth/me" -> {
                    meCalls++
                    assertTrue(headers["Authorization"].orEmpty().startsWith("Bearer fake."))
                    if(failMe != 0) SessionResponse(failMe,"") else SessionResponse(200,
                        "{\"email\":\"customer@example.invalid\",\"roles\":[\"$role\"]}")
                }
                path == "/api/v1/auth/refresh" -> {
                    refreshes++
                    assertTrue(headers["Cookie"].orEmpty().contains("refresh="))
                    assertEquals("test-csrf",headers["X-CSRF-TOKEN"])
                    SessionResponse(200,JSONObject().put("accessToken",access()).put("tokenType","Bearer").put("expiresIn",60).toString(),
                        listOf("refresh=rotated-$refreshes; Path=/api/v1; Secure; HttpOnly"))
                }
                path == "/api/v1/auth/logout" -> SessionResponse(if(failLogout) 503 else 204,"")
                else -> error("Unexpected request: $method $path")
            }
        }
    }
    private val origin="https://api.example.invalid"
    private fun registration(company:Long=17)=com.example.kairos.model.auth.CustomerRegistration(
        "Cliente","Teste",java.time.LocalDate.of(2000,1,1),"529.982.247-25",
        "customer@example.invalid","test-password",company)

    @Test fun registrationUsesRealContractAndConfirmsCustomerSession() {
        val server=Server(); val store=MemoryStore(); val session=SpringSession(origin,store,server)
        val user=session.register(registration())
        assertTrue(user.business!!.isCustomer)
        assertEquals(17L,server.registrationBody!!.getLong("companyId"))
        assertEquals("52998224725",server.registrationBody!!.getString("cpf"))
        assertEquals("2000-01-01",server.registrationBody!!.getString("birthDate"))
        assertFalse(server.registrationBody!!.has("role"))
        assertEquals(listOf("GET /api/v1/auth/login","POST /api/v1/users/registration","GET /api/v1/auth/me"),server.calls)
        assertFalse(store.value!!.contains("test-password"))
    }
    @Test fun registrationConflictDoesNotCreateLocalSessionOrRetry() {
        val server=Server().apply { registrationStatus=409 }; val store=MemoryStore()
        val session=SpringSession(origin,store,server)
        try { session.register(registration()); fail() } catch(e:IllegalArgumentException) { assertTrue(e.message!!.contains("já utilizado")) }
        assertEquals(1,server.registrationCalls); assertNull(session.uid()); assertNull(store.value)
    }
    @Test fun uncertainRegistrationResponseDoesNotClaimSuccessOrRetry() {
        val server=Server().apply { registrationStatus=503 }; val session=SpringSession(origin,MemoryStore(),server)
        try { session.register(registration()); fail() } catch(e:IllegalArgumentException) { assertTrue(e.message!!.contains("Antes de reenviar")) }
        assertEquals(1,server.registrationCalls); assertNull(session.uid())
    }
    @Test fun missingMarketProvidesActionableFeedbackWithoutSession() {
        val server=Server().apply { registrationStatus=404 }; val session=SpringSession(origin,MemoryStore(),server)
        try { session.register(registration()); fail() } catch(e:IllegalArgumentException) { assertTrue(e.message!!.contains("Confira o código")) }
        assertEquals(1,server.registrationCalls); assertNull(session.uid())
    }
    @Test fun createdAccountWithFailedSessionTellsUserToLoginInsteadOfRegisterAgain() {
        val server=Server().apply { failMe=401 }; val store=MemoryStore(); val session=SpringSession(origin,store,server)
        try { session.register(registration()); fail() } catch(e:IllegalArgumentException) { assertTrue(e.message!!.startsWith("Conta criada")) }
        assertEquals(1,server.registrationCalls); assertNull(session.uid()); assertNull(store.value)
    }
    @Test fun invalidCompanyOrCpfIsRejectedBeforeAnyNetworkCall() {
        val server=Server(); val session=SpringSession(origin,MemoryStore(),server)
        val invalid=com.example.kairos.model.auth.CustomerRegistration("Cliente","Teste",java.time.LocalDate.of(2000,1,1),
            "11111111111","customer@example.invalid","test-password",17)
        for(request in listOf(registration(0),invalid)) {
            try { session.register(request); fail() } catch(_:IllegalArgumentException) { }
        }
        assertTrue(server.calls.isEmpty())
    }
    @Test fun customerLoginUsesFormCsrfAndServerIdentityWithoutCompany() {
        val server=Server(); val store=MemoryStore()
        val session=SpringSession(origin,store,server)
        val user=session.login("customer@example.invalid","test&password+")
        assertEquals("email=customer%40example.invalid&password=test%26password%2B",server.loginForm)
        assertEquals("spring:42",user.uid)
        assertTrue(user.business!!.isCustomer)
        assertNull(user.business.companyId)
        assertNull(user.profile)
        assertEquals(1,server.meCalls)
        assertFalse(store.value!!.contains("test&password"))
        assertEquals(listOf("GET /api/v1/auth/login","POST /api/v1/auth/login","GET /api/v1/auth/me"),server.calls)
    }
    @Test fun restoreRenewsExpiredSessionAndPersistsRotatedCookie() {
        var clock=0L
        val store=MemoryStore(); val server=Server()
        SpringSession(origin,store,server){clock}.login("customer@example.invalid","test-password")
        clock=61_000
        val restored=SpringSession(origin,store,server){clock}
        assertTrue(restored.restore()!!.business!!.isCustomer)
        assertEquals(1,server.refreshes)
        assertTrue(store.value!!.contains("rotated-1"))
    }
    @Test fun concurrentTokenReadsRotateRefreshOnlyOnce() {
        var clock=0L
        val server=Server(); val session=SpringSession(origin,MemoryStore(),server){clock}
        session.login("customer@example.invalid","test-password"); clock=61_000
        val pool=Executors.newFixedThreadPool(6)
        try {
            val results=(1..6).map { pool.submit<String> { session.token("spring:42",false) } }
            results.forEach { assertTrue(it.get(5,TimeUnit.SECONDS).startsWith("fake.")) }
            assertEquals(1,server.refreshes)
        } finally { pool.shutdownNow() }
    }
    @Test fun unauthorizedSessionIsRemovedButOutagePreservesIt() {
        val store=MemoryStore(); val server=Server(); val session=SpringSession(origin,store,server)
        session.login("customer@example.invalid","test-password")
        server.failMe=503
        try { session.restore(); fail() } catch(e:ApiFailure) { assertEquals(503,e.status) }
        assertEquals("spring:42",session.uid()); assertNotNull(store.value)
        server.failMe=401
        assertNull(session.restore()); assertNull(session.uid()); assertNull(store.value)
    }
    @Test fun rejectedAccountSwitchNeverRestoresPreviousAccount() {
        val store=MemoryStore(); val server=Server(); val session=SpringSession(origin,store,server)
        session.login("customer@example.invalid","test-password")
        server.rejectLogin=true
        try { session.login("other@example.invalid","wrong-password"); fail() } catch(e:IllegalArgumentException) {
            assertTrue(e.message!!.contains("E-mail ou senha"))
        }
        assertNull(session.uid()); assertNull(store.value)
    }
    @Test fun identityIsNotAcceptedWhenMeRejectsToken() {
        val store=MemoryStore(); val server=Server().apply { failMe=401 }
        val session=SpringSession(origin,store,server)
        try { session.login("customer@example.invalid","test-password"); fail() } catch(_:IllegalArgumentException) { }
        assertNull(session.uid()); assertNull(store.value)
    }
    @Test fun storedCredentialsAreNotReusedOnAnotherOrigin() {
        val store=MemoryStore(); val server=Server()
        SpringSession(origin,store,server).login("customer@example.invalid","test-password")
        val other=SpringSession("https://other.example.invalid",store,server)
        assertNull(other.uid()); assertNull(other.restore())
        assertEquals(3,server.calls.size)
    }
    @Test fun logoutAlwaysClearsLocalSessionEvenWhenServerIsUnavailable() {
        val store=MemoryStore(); val server=Server(); val session=SpringSession(origin,store,server)
        session.login("customer@example.invalid","test-password"); server.failLogout=true
        try { session.logout(); fail() } catch(e:IllegalArgumentException) { assertTrue(e.message!!.contains("removida")) }
        assertNull(session.uid()); assertNull(store.value)
    }
    @Test fun employeeUsesPublishedOperationalRole() {
        val server=Server().apply { role="EMPLOYEE" }
        val user=SpringSession(origin,MemoryStore(),server).login("employee@example.invalid","test-password")
        assertTrue(user.business!!.canRestock); assertFalse(user.business.isCustomer)
    }
}
