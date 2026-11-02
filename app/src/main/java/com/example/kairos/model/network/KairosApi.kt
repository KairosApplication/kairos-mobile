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

class ApiFailure(val status: Int) : Exception(when (status) {
    401 -> "Sessão expirada ou revogada. Entre novamente."
    403 -> "Sua conta não tem autorização para esta operação."
    404 -> "O recurso não está disponível para sua conta."
    409 -> "A operação está em conflito. Atualize os dados antes de continuar."
    413 -> "A imagem excede o limite permitido."
    422, 400 -> "Confira os dados e as quantidades informadas."
    429 -> "Limite de solicitações atingido. Aguarde e tente novamente."
    else -> "Serviço indisponível. Verifique a conexão e tente novamente."
})

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
        val origin = runCatching { URI(base) }.getOrNull()
        require(origin?.scheme == "https" && !origin.host.isNullOrBlank() && origin.userInfo == null &&
            origin.query == null && origin.fragment == null && origin.path.orEmpty() in setOf("", "/")) {
            "Configure uma origem HTTPS para os serviços Kairos."
        }
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
            if (status !in 200..299) throw ApiFailure(status)
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
