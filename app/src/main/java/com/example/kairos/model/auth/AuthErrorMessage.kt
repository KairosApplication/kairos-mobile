package com.example.kairos.model.auth

import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.concurrent.TimeoutException

/** Mensagens controladas: não mostram mensagens brutas que podem conter dados do usuário. */
object AuthErrorMessage {
    fun from(error: Exception): String = when (error) {
        is FirebaseFirestoreException -> firestoreCode(error.code.name)
        is TimeoutException ->
            "O Firebase não respondeu em 30 segundos (TIMEOUT). Verifique a conexão e tente novamente."
        is IllegalArgumentException -> error.message ?: "Confira os dados informados."
        else -> "Não foi possível concluir (${error.javaClass.simpleName}). Verifique a conexão e a configuração do Firebase."
    }

    internal fun firestoreCode(code: String): String = when (code) {
            "PERMISSION_DENIED" ->
                "O Firestore negou o acesso (PERMISSION_DENIED). Verifique as regras publicadas no projeto Firebase. Ao completar o cadastro, o CPF também pode estar reservado."
            "UNAVAILABLE" ->
                "O Firestore está indisponível (UNAVAILABLE). Confira a conexão e se o banco (default) foi criado no projeto Firebase."
            "NOT_FOUND" ->
                "Recurso do Firestore não encontrado (NOT_FOUND). Confira se o banco (default) foi criado no projeto Firebase."
            "UNAUTHENTICATED" ->
                "A sessão não foi aceita pelo Firestore (UNAUTHENTICATED). Entre novamente."
            "DEADLINE_EXCEEDED" ->
                "O Firestore não respondeu a tempo (DEADLINE_EXCEEDED). Verifique a conexão e tente novamente."
            else -> "Não foi possível acessar o Firestore ($code)."
    }
}
