package com.example.kairos.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.kairos.model.auth.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

enum class AuthScreen { LOGIN, LOADING, REGISTER, RECOVERY, COMPLETE_PROFILE, HOME }

data class AuthUiState(
    val screen: AuthScreen = AuthScreen.LOGIN,
    val loading: Boolean = false,
    val message: String = "",
    val user: SignedInUser? = null,
    val accountOperation: AccountOperation? = null
)

class AuthViewModel(private val repository: AuthRepository?) : ViewModel() {
    private val mutableState = MutableLiveData(AuthUiState())
    val state: LiveData<AuthUiState> = mutableState
    private val executor = Executors.newSingleThreadScheduledExecutor()

    init {
        if (repository == null) {
            mutableState.value = AuthUiState(message = "Firebase não configurado. Adicione o google-services.json do Kairos e sincronize o Gradle.")
        } else {
            run { it.restoreSession()?.let(::authenticated) ?: AuthUiState() }
        }
    }

    private fun authenticated(user: SignedInUser) = AuthUiState(
        screen = if (user.profile == null) AuthScreen.COMPLETE_PROFILE else AuthScreen.HOME,
        user = user,
        message = if (user.profile == null) "Complete os dados para concluir seu cadastro." else ""
    )

    fun navigate(screen: AuthScreen) {
        if (mutableState.value?.loading == true) return
        if (screen !in listOf(AuthScreen.LOGIN, AuthScreen.REGISTER, AuthScreen.RECOVERY)) return
        if (mutableState.value?.user != null) { logout(); return }
        mutableState.value = AuthUiState(screen)
    }

    fun showError(message: String) {
        mutableState.value = mutableState.value!!.copy(message = message)
    }

    private fun run(
        minimumLoadingMillis: Long = 0L,
        loadingScreen: AuthScreen? = null,
        accountOperation: AccountOperation? = null,
        action: (AuthRepository) -> AuthUiState
    ) {
        val previous = mutableState.value!!
        if (previous.loading) return
        mutableState.value = previous.copy(screen = loadingScreen ?: previous.screen, loading = true, message = "", accountOperation = accountOperation)
        val startedAt = System.nanoTime()
        executor.execute {
            val result = try {
                action(repository ?: throw IllegalArgumentException(
                    "Adicione app/google-services.json para com.example.kairos e sincronize o Gradle."))
            } catch (e: AccountChangedException) {
                AuthUiState(message = e.message.orEmpty())
            } catch (e: ProfileIncompleteException) {
                authenticated(e.user).copy(message = e.message.orEmpty() + "\n" +
                    AuthErrorMessage.from(e.cause as? Exception ?: e))
            } catch (e: IllegalArgumentException) {
                previous.copy(message = e.message ?: "Confira os dados informados.")
            } catch (e: Exception) {
                previous.copy(message = if (accountOperation != null) "Não foi possível atualizar sua conta. Verifique a conexão e tente novamente."
                    else AuthErrorMessage.from(e))
            }
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            val remaining = (minimumLoadingMillis - elapsedMillis).coerceAtLeast(0L)
            if (!executor.isShutdown) executor.schedule({
                mutableState.postValue(result.copy(loading = false, accountOperation = null))
            }, remaining, TimeUnit.MILLISECONDS)
        }
    }

    fun register(request: Registration, confirmation: String) = run {
        require(request.password == confirmation) { "As senhas não coincidem." }
        authenticated(it.register(request))
    }

    fun registerAccount(email: String, password: String, confirmation: String, acceptedTerms: Boolean) = run {
        require(acceptedTerms) { "Marque a opção de concordância com os termos de serviço." }
        require(password == confirmation) { "As senhas não coincidem." }
        authenticated(it.registerAccount(email, password))
    }

    fun login(email: String, password: String) = run(
        minimumLoadingMillis = 3_000L, loadingScreen = AuthScreen.LOADING
    ) {
        authenticated(it.login(email, password))
    }

    fun completeProfile(details: ProfileDetails) = run {
        authenticated(it.completeProfile(details))
    }

    fun requestResetLink(email: String) = run {
        it.requestPasswordReset(email)
        AuthUiState(AuthScreen.RECOVERY, message =
            "Se houver uma conta elegível para esse email, você receberá um link para redefinir a senha. " +
            "Confira também o spam. Após alterar a senha, volte ao login.")
    }

    fun logout() = run {
        it.logout()
        AuthUiState()
    }

    fun editAccount(request: AccountEditRequest) = run(accountOperation = request.operation) {
        if (mutableState.value?.user?.uid != request.uid) throw AccountChangedException()
        val user = when (request) {
            is AccountEditRequest.Name -> it.updateName(request.uid, request.name, request.lastName)
            is AccountEditRequest.Email -> it.requestEmailChange(request.uid, request.email, request.currentPassword)
            is AccountEditRequest.Password -> it.changePassword(request.uid, request.currentPassword, request.newPassword)
        }
        if (user.uid != request.uid) throw AccountChangedException()
        authenticated(user).copy(message = when (request.operation) {
            AccountOperation.NAME -> "Nome atualizado com sucesso."
            AccountOperation.EMAIL -> "Enviamos um link para o novo e-mail. Confirme a alteração na mensagem recebida."
            AccountOperation.PASSWORD -> "Senha atualizada com sucesso."
            AccountOperation.REFRESH -> ""
        })
    }

    fun refreshAccount() {
        val previous = mutableState.value ?: return
        if (previous.screen != AuthScreen.HOME || previous.loading) return
        run(accountOperation = AccountOperation.REFRESH) {
            val updated = it.restoreSession() ?: throw AccountChangedException()
            authenticated(updated).copy(message = if (previous.user?.email != updated.email)
                "E-mail atualizado com sucesso." else previous.message)
        }
    }

    override fun onCleared() { executor.shutdownNow() }

    class Factory(private val repository: AuthRepository?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(repository) as T
    }
}
