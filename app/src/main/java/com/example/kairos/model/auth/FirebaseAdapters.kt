package com.example.kairos.model.auth

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** Await é usado exclusivamente no executor do ViewModel, nunca na main thread. */
private fun <T> Task<T>.resultInWorker(): T = try {
    Tasks.await(this, 30, TimeUnit.SECONDS)
} catch (e: ExecutionException) {
    throw (e.cause as? Exception ?: e)
}

class FirebaseAuthGateway(private val auth: FirebaseAuth) : AuthGateway {
    init { auth.setLanguageCode("pt-BR") }

    private fun identity(): AuthIdentity {
        val user = auth.currentUser ?: error("Sessão ausente.")
        return AuthIdentity(user.uid, user.email ?: error("Email ausente."))
    }

    private fun <T> translate(action: () -> T): T = try {
        action()
    } catch (e: FirebaseTooManyRequestsException) {
        throw IllegalArgumentException("Muitas tentativas. Aguarde antes de tentar novamente.")
    } catch (e: FirebaseNetworkException) {
        throw IllegalArgumentException("Sem conexão com o Firebase. Verifique sua internet.")
    } catch (e: FirebaseAuthException) {
        val message = when (e.errorCode) {
            "ERROR_WEAK_PASSWORD" -> "A senha não atende à política do projeto Firebase."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Não foi possível cadastrar. Tente entrar ou recuperar sua senha."
            "ERROR_INVALID_EMAIL" -> "Informe um email válido."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Habilite Email/Senha no Firebase Authentication."
            else -> "Não foi possível autenticar. Confira email e senha ou recupere sua senha."
        }
        throw IllegalArgumentException(message)
    }

    override fun create(email: String, password: String): AuthIdentity = translate {
        auth.createUserWithEmailAndPassword(email, password).resultInWorker()
        identity()
    }

    override fun signIn(email: String, password: String): AuthIdentity = translate {
        auth.signInWithEmailAndPassword(email, password).resultInWorker()
        identity()
    }

    override fun current(): AuthIdentity? = translate {
        if (auth.currentUser == null) null else {
            auth.currentUser!!.reload().resultInWorker()
            identity()
        }
    }

    override fun sendResetLink(email: String) = translate {
        try {
            auth.sendPasswordResetEmail(email).resultInWorker()
        } catch (e: FirebaseAuthException) {
            // Mantém resposta genérica também em projetos sem proteção contra enumeração.
            if (e.errorCode != "ERROR_USER_NOT_FOUND") throw e
        }
        Unit
    }

    override fun signOut() = auth.signOut()
}

class FirestoreProfileStore(private val db: FirebaseFirestore) : ProfileStore {
    override fun read(uid: String): UserProfile? {
        val document = db.collection("users").document(uid).get(Source.SERVER).resultInWorker()
        return document.data?.let(UserProfile::fromDocument)
    }

    override fun create(profile: UserProfile): UserProfile {
        val document = profile.toDocument()
        val userRef = db.collection("users").document(profile.uid)
        val cpfRef = db.collection("cpfClaims").document(document.getValue("cpf"))
        return db.runTransaction { transaction ->
            val existing = transaction.get(userRef)
            if (existing.exists()) {
                UserProfile.fromDocument(existing.data ?: error("Perfil inválido."))
            } else {
                val claim = transaction.get(cpfRef)
                require(!claim.exists() || claim.getString("uid") == profile.uid) {
                    "CPF indisponível para este cadastro."
                }
                transaction.set(cpfRef, mapOf("uid" to profile.uid))
                transaction.set(userRef, document)
                profile.copy(cpf = document.getValue("cpf"), zipCode = document.getValue("zipCode"))
            }
        }.resultInWorker()
    }
}
