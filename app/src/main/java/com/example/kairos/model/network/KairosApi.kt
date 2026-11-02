package com.example.kairos.model.network

import com.example.kairos.BuildConfig
import com.example.kairos.model.auth.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

class ServiceConfigurationException : IllegalArgumentException("Você entrou, mas a conexão com os serviços do mercado ainda não foi configurada.")

object ApiOrigin {
    fun validate(base: String, allowLocalHttp: Boolean) {
        val origin = runCatching { URI(base) }.getOrNull()
        val local = origin?.scheme == "http" && origin.host in setOf("127.0.0.1", "localhost", "10.0.2.2")
        if (origin == null || !(origin.scheme == "https" || allowLocalHttp && local) || origin.host.isNullOrBlank() ||
            origin.userInfo != null || origin.query != null || origin.fragment != null || origin.path.orEmpty() !in setOf("", "/")) {
            throw ServiceConfigurationException()
        }
    }
}

class ApiFailure(val status: Int, code: String? = null) : Exception(when (code) {
    "AI_NOT_CONFIGURED" -> "As respostas de IA ainda não estão ativadas. Você já pode criar e consultar suas conversas."
    "VISION_NOT_CONFIGURED" -> "A análise de fotos ainda não foi ativada no servidor. Seu login continua válido."
    "VISION_PROVIDER_UNAVAILABLE" -> "O serviço de análise de fotos não respondeu. Tente novamente em instantes."
    "VISION_INVALID_RESPONSE" -> "Não foi possível interpretar a análise. Tente uma foto mais próxima e nítida."
    else -> when (status) {
    401 -> "Sessão expirada ou revogada. Entre novamente."
    403 -> "Sua conta não tem autorização para esta operação."
    404 -> "O recurso não está disponível para sua conta."
    409 -> "A operação está em conflito. Atualize os dados antes de continuar."
    413 -> "A imagem excede o limite permitido."
    422, 400 -> "Confira os dados e as quantidades informadas."
    429 -> "Limite de solicitações atingido. Aguarde e tente novamente."
    502, 503, 504 -> "Não conseguimos acessar o servidor agora. Tente novamente em instantes."
    else -> "Não foi possível concluir a solicitação no servidor. Tente novamente."
}})

interface IdentityTokenSource {
    fun uid(): String?
    fun token(uid: String, refresh: Boolean): String
}

class FirebaseTokens(private val auth: FirebaseAuth) : IdentityTokenSource {
    override fun uid() = auth.currentUser?.uid
    override fun token(uid: String, refresh: Boolean): String {
        val user = auth.currentUser?.takeIf { it.uid == uid } ?: throw AccountChangedException()
        return Tasks.await(user.getIdToken(refresh), 30, TimeUnit.SECONDS).token ?: throw AccountChangedException()
    }
}

/** No cookie jar, redirects, disk cache, body logging or automatic retry of mutations. */
class KairosApi(private val base: String, private val tokens: IdentityTokenSource) {
    fun request(uid: String, path: String, method: String = "GET", payload: JSONObject? = null): String {
        return requestAttempt(uid, path, method, payload, false)
    }
    private fun requestAttempt(uid: String, path: String, method: String, payload: JSONObject?, refreshed: Boolean): String {
        ApiOrigin.validate(base, BuildConfig.DEBUG)
        require(path.startsWith("/api/v1/") && !path.contains(".."))
        fun owner() { if (tokens.uid() != uid) throw AccountChangedException() }
        owner()
        val token = tokens.token(uid, false)
        owner()
        val connection = URI(base.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 90_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("X-Kairos-Identity", "firebase")
            connection.setRequestProperty("Accept", "application/json")
            payload?.let {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { output -> output.write(it.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            owner()
            if (status == 401 && method == "GET" && !refreshed) {
                connection.disconnect()
                tokens.token(uid, true)
                owner()
                return requestAttempt(uid, path, method, payload, true)
            }
            if (status !in 200..299) {
                val code = if (status == 503) runCatching {
                    connection.errorStream?.use { stream ->
                        val bytes = ByteArray(2048)
                        val count = stream.read(bytes)
                        if (count > 0) JSONObject(String(bytes, 0, count, Charsets.UTF_8)).let {
                            it.optJSONObject("detail")?.optString("code") ?: it.optString("code")
                        } else null
                    }
                }.getOrNull() else null
                throw ApiFailure(status, code)
            }
            val body = connection.inputStream.use { stream ->
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    check(bytes.size() + count <= 4 * 1024 * 1024) { "Resposta excedeu o limite permitido." }
                    bytes.write(buffer, 0, count)
                }
                bytes.toString("UTF-8")
            }
            owner()
            return body
        } catch (error: java.io.IOException) {
            owner()
            throw ApiFailure(503)
        } finally { connection.disconnect() }
    }
}

class HttpBusinessSession(private val api: KairosApi) : BusinessSessionGateway {
    private fun parse(text: String) = JSONObject(text).let {
        BusinessIdentity(it.optionalLong("userId"), it.optionalLong("employeeId"), it.optionalLong("companyId"),
            it.getString("role"), it.optString("companyName", ""))
    }
    override fun session(uid: String) = parse(api.request(uid, "/api/v1/mobile/session"))
    override fun link(uid: String, password: String) = parse(api.request(uid, "/api/v1/mobile/link", "POST", JSONObject().put("password", password)))
    override fun register(uid: String, profile: UserProfile) = parse(api.request(uid, "/api/v1/mobile/registration", "POST", JSONObject()
        .put("name", profile.name).put("lastName", profile.lastName).put("birthDate", profile.birthDate.toString()).put("cpf", profile.cpf)))
}

fun JSONObject.optionalLong(name: String): Long? = if (isNull(name)) null else getLong(name)
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

object ApiDependencies {
    private fun tokens() = object : IdentityTokenSource {
        override fun uid() = FirebaseTokens(FirebaseAuth.getInstance()).uid()
        override fun token(uid: String, refresh: Boolean) = FirebaseTokens(FirebaseAuth.getInstance()).token(uid, refresh)
    }
    fun spring() = KairosApi(BuildConfig.SPRING_API_URL, tokens())
    fun chat() = KairosApi(BuildConfig.CHAT_API_URL, tokens())
}
