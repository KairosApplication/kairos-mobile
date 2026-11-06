package com.example.kairos.model.network

import com.example.kairos.model.auth.*
import org.json.JSONObject
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

interface SessionStore {
    fun read(): String?
    fun write(value: String?)
}

data class SessionResponse(val status: Int, val body: String, val cookies: List<String> = emptyList())
fun interface SessionTransport {
    fun send(path: String, method: String, headers: Map<String, String>, body: String?): SessionResponse
}

class SessionHttpTransport(private val base: String) : SessionTransport {
    override fun send(path: String, method: String, headers: Map<String, String>, body: String?): SessionResponse {
        ApiOrigin.validate(base, com.example.kairos.BuildConfig.DEBUG)
        val connection = URI(base.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            body?.let { value ->
                connection.doOutput = true
                connection.outputStream.use { it.write(value.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while(true) {
                    val count=it.read(buffer)
                    if(count<0) break
                    require(output.size()+count <= 256*1024) { "Resposta excedeu o limite permitido." }
                    output.write(buffer,0,count)
                }
                output.toByteArray()
            } ?: byteArrayOf()
            return SessionResponse(status, String(bytes, Charsets.UTF_8),
                connection.headerFields.filterKeys { it?.equals("Set-Cookie", true) == true }.values.flatten())
        } catch (error: java.io.IOException) {
            throw ApiFailure(503)
        } finally { connection.disconnect() }
    }
}

/** One lock serializes refresh rotation. Passwords never enter the persisted session. */
class SpringSession(private val origin: String, private val store: SessionStore,
    private val transport: SessionTransport = SessionHttpTransport(origin),
    private val now: () -> Long = { System.currentTimeMillis() }) : IdentityTokenSource {
    private var access: String? = null
    @Volatile private var owner: String? = null
    private var expiresAt = 0L
    private val cookies = linkedMapOf<String, String>()
    init {
        runCatching {
            val saved = store.read()?.let(::JSONObject) ?: return@runCatching
            if (saved.getString("origin") != origin) return@runCatching
            access = saved.getString("access")
            owner = saved.getString("owner")
            expiresAt = saved.getLong("expiresAt")
            val jar = saved.getJSONObject("cookies")
            jar.keys().forEach { cookies[it] = jar.getString(it) }
        }.onFailure { clear() }
    }
    override fun uid(): String? = owner
    @Synchronized fun invalidate() = clear()
    @Synchronized override fun token(uid: String, refresh: Boolean): String {
        if (owner != uid) throw AccountChangedException()
        if (refresh || now() + 30_000 >= expiresAt) renew()
        if (owner != uid) throw AccountChangedException()
        return access ?: throw AccountChangedException()
    }
    @Synchronized fun login(email: String, password: String): SignedInUser {
        AuthValidation.email(email); AuthValidation.password(password)
        // Do not carry another account's refresh cookies into a login.
        clear()
        try {
            val csrf = csrf()
            val form = "email=${encode(email)}&password=${encode(password)}"
            acceptAccess(send("/api/v1/auth/login", "POST", mapOf(csrf.first to csrf.second,
                "Content-Type" to "application/x-www-form-urlencoded; charset=utf-8"), form))
            return identity()
        } catch (error: Exception) {
            clear()
            if (error is ApiFailure && error.status == 401)
                throw IllegalArgumentException("E-mail ou senha não reconhecidos pelo Kairos. Confira se sua conta já tem acesso ao serviço.")
            throw error
        }
    }
    @Synchronized fun restore(): SignedInUser? {
        if (owner == null) return null
        return try {
            token(owner!!, false)
            identity()
        } catch (error: ApiFailure) {
            if (error.status == 401) { clear(); null } else throw error
        }
    }
    @Synchronized fun register(request: CustomerRegistration): SignedInUser {
        val payload=request.json().toString()
        require(owner==null) { "Saia da conta atual antes de cadastrar outro cliente." }
        clear()
        var created=false
        try {
            val csrf=csrf()
            val response=send("/api/v1/users/registration","POST",
                mapOf(csrf.first to csrf.second,"Content-Type" to "application/json; charset=utf-8"),payload)
            require(response.status==201) { "O servidor não confirmou a criação da conta. Confira seu acesso antes de tentar novamente." }
            created=true
            acceptAccess(response)
            return identity()
        } catch(error:Exception) {
            clear()
            if(created) throw IllegalArgumentException(
                "Conta criada, mas não foi possível confirmar o login. Entre com o e-mail e a senha cadastrados.")
            if(error is ApiFailure) throw IllegalArgumentException(when(error.status) {
                409 -> "Não foi possível cadastrar: e-mail ou CPF já utilizado. Tente entrar ou solicite ajuda."
                400,422 -> "Confira os dados pessoais e o código do mercado. O cadastro não foi confirmado."
                403 -> "Não foi possível validar o envio do cadastro. Tente novamente."
                404 -> "Não foi possível localizar o mercado para o cadastro. Confira o código ou solicite ajuda."
                429 -> "Muitas tentativas de cadastro. Aguarde antes de tentar novamente."
                503,502,504 -> "Não recebemos a confirmação do cadastro. Antes de reenviar, tente entrar com os dados informados."
                else -> "O cadastro não foi confirmado pelo serviço. Tente novamente mais tarde."
            })
            throw error
        }
    }
    @Synchronized fun logout() {
        try {
            if (owner != null) {
                val csrf = csrf()
                send("/api/v1/auth/logout", "POST", mapOf(csrf.first to csrf.second))
            }
        } catch (error: Exception) {
            throw IllegalArgumentException("Sessão removida deste aparelho. Não foi possível confirmar o encerramento no servidor.")
        } finally { clear() }
    }
    private fun identity(): SignedInUser {
        val me = JSONObject(send("/api/v1/auth/me", "GET").body)
        val roles = me.getJSONArray("roles").let { rows -> (0 until rows.length()).map { rows.getString(it) } }
        val role = listOf("MANAGER", "EMPLOYEE", "CUSTOMER").firstOrNull { it in roles }
            ?: throw IllegalArgumentException("Seu perfil não possui acesso ao aplicativo.")
        val userId = owner?.removePrefix("spring:")?.toLongOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Identidade inválida recebida do servidor.")
        return SignedInUser(owner!!, me.getString("email"), null,
            business = BusinessIdentity(userId, null, null, role, "", apiAuthenticated = true))
    }
    private fun renew() {
        val previous = owner
        try {
            val csrf = csrf()
            acceptAccess(send("/api/v1/auth/refresh", "POST", mapOf(csrf.first to csrf.second)))
            check(owner == previous) { "A conta da sessão foi alterada." }
        } catch (error: ApiFailure) {
            if (error.status == 401) clear()
            throw error
        }
    }
    private fun csrf(): Pair<String, String> {
        val json = JSONObject(send("/api/v1/auth/login", "GET").body)
        val name = json.getString("headerName")
        require(name.equals("X-CSRF-TOKEN", true))
        return name to json.getString("token")
    }
    private fun send(path: String, method: String, extra: Map<String, String> = emptyMap(), body: String? = null): SessionResponse {
        val headers = mutableMapOf("Cache-Control" to "no-store")
        if (cookies.isNotEmpty()) headers["Cookie"] = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        access?.let { headers["Authorization"] = "Bearer $it" }
        headers.putAll(extra)
        val result = transport.send(path, method, headers, body)
        result.cookies.forEach { raw -> HttpCookie.parse(raw).forEach {
            if (it.maxAge == 0L) cookies.remove(it.name) else cookies[it.name] = it.value
        } }
        if (result.status !in 200..299) throw ApiFailure(result.status)
        if (owner != null) persist()
        return result
    }
    private fun acceptAccess(response: SessionResponse) {
        val json = JSONObject(response.body)
        require(json.getString("tokenType").equals("Bearer", true))
        val value = json.getString("accessToken")
        // Claims are used only after /me verifies this token with the server.
        val parts = value.split('.')
        require(parts.size == 3)
        val claims = JSONObject(String(java.util.Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
        val id = claims.getString("sub").toLongOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Identidade inválida recebida do servidor.")
        access = value
        owner = "spring:$id"
        expiresAt = now() + json.getLong("expiresIn").coerceIn(1, 86400) * 1000
        persist()
    }
    private fun persist() {
        store.write(JSONObject().put("origin", origin).put("owner", owner).put("access", access)
            .put("expiresAt", expiresAt).put("cookies", JSONObject(cookies.toMap())).toString())
    }
    private fun clear() { access = null; owner = null; expiresAt = 0; cookies.clear(); store.write(null) }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
