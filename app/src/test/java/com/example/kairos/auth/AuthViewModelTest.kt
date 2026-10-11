package com.example.kairos.auth

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelStore
import com.example.kairos.model.auth.*
import com.example.kairos.viewmodel.*
import java.time.LocalDate
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthViewModelTest {
    private val store = ViewModelStore()

    @Before fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
    }

    @After fun tearDown() {
        store.clear()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test fun loginOpensDedicatedLoadingScreenBeforeHome() {
        verifyLogin(fail = false, destination = AuthScreen.HOME)
    }

    @Test fun failedLoginReturnsToLoginWithError() {
        verifyLogin(fail = true, destination = AuthScreen.LOGIN)
    }

    @Test fun accountEditsKeepHomeAndOnlyPublishConfirmedDataForTheSameOwner() {
        val initial = SignedInUser("account", "old@example.com", UserProfile("account", "João", "Silva",
            LocalDate.of(1985, 10, 22), "12364578902", "old@example.com", "12345678", "basico"))
        var writes = 0
        var reject = false
        val repository = object : AuthRepository {
            override fun restoreSession() = initial
            override fun registerAccount(email: String, password: String) = error("Unused")
            override fun register(request: Registration) = error("Unused")
            override fun login(email: String, password: String) = error("Unused")
            override fun completeProfile(details: ProfileDetails) = error("Unused")
            override fun requestPasswordReset(email: String) = Unit
            override fun logout() = Unit
            override fun updateName(uid: String, name: String, lastName: String): SignedInUser {
                if (reject) throw IllegalArgumentException("Falha ao salvar")
                writes++
                return initial.copy(profile = initial.profile!!.copy(name = name, lastName = lastName))
            }
        }
        val vm = AuthViewModel(repository)
        store.put("account", vm)
        val states = LinkedBlockingQueue<AuthUiState>()
        val observer = Observer<AuthUiState> { states.offer(it) }
        vm.state.observeForever(observer)
        fun finished(): AuthUiState {
            var state: AuthUiState
            do { state = states.poll(5, TimeUnit.SECONDS) ?: error("Account operation timed out") } while (state.loading)
            return state
        }
        try {
            finished()
            states.clear()
            vm.editAccount(AccountEditRequest.Name("account", "Maria", "Souza"))
            val loading = states.poll(5, TimeUnit.SECONDS)!!
            assertEquals(AuthScreen.HOME, loading.screen)
            assertEquals(AccountOperation.NAME, loading.accountOperation)
            val success = finished()
            assertEquals("Maria", success.user!!.profile!!.name)
            assertEquals(AuthScreen.HOME, success.screen)
            assertNull(success.accountOperation)
            reject = true
            states.clear()
            vm.editAccount(AccountEditRequest.Name("account", "New", "Name"))
            val failure = finished()
            assertEquals(success.user, failure.user)
            assertEquals("Falha ao salvar", failure.message)
            states.clear()
            vm.editAccount(AccountEditRequest.Name("other-account", "Other", "User"))
            val wrongOwner = finished()
            assertEquals(AuthScreen.LOGIN, wrongOwner.screen)
            assertNull(wrongOwner.user)
            assertEquals(1, writes)
        } finally { vm.state.removeObserver(observer) }
    }

    private fun verifyLogin(fail: Boolean, destination: AuthScreen) {
        val vm = AuthViewModel(FakeRepository(fail))
        store.put("auth", vm)
        val states = LinkedBlockingQueue<AuthUiState>()
        val observer = Observer<AuthUiState> { states.offer(it) }
        vm.state.observeForever(observer)
        try {
            // Wait for session restoration to finish before submitting the login.
            do {
                val initial = states.poll(5, TimeUnit.SECONDS) ?: error("Session restoration timed out")
            } while (initial.loading)
            states.clear()
            val startedAt = System.nanoTime()
            vm.login("test@example.com", "test-password")
            val loading = states.poll(5, TimeUnit.SECONDS)!!
            assertEquals(AuthScreen.LOADING, loading.screen)
            assertTrue(loading.loading)
            assertNull(loading.user)
            vm.navigate(AuthScreen.LOGIN)
            assertEquals(AuthScreen.LOADING, vm.state.value!!.screen)
            val result = states.poll(10, TimeUnit.SECONDS)!!
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            assertTrue("Loading must remain visible for at least 3 seconds", elapsedMillis >= 3_000L)
            assertEquals(destination, result.screen)
            assertFalse(result.loading)
            if (fail) {
                assertEquals("Login recusado", result.message)
                assertNull(result.user)
            } else {
                assertEquals("Test", result.user!!.name)
            }
        } finally {
            vm.state.removeObserver(observer)
        }
    }

    private class FakeRepository(private val fail: Boolean) : AuthRepository {
        override fun restoreSession(): SignedInUser? = null
        override fun login(email: String, password: String): SignedInUser {
            if (fail) throw IllegalArgumentException("Login recusado")
            return SignedInUser("test", email, UserProfile(
                "test", "Test", "User", LocalDate.of(2000, 1, 1), "", email, "", ""
            ))
        }
        override fun registerAccount(email: String, password: String): SignedInUser = error("Unused")
        override fun register(request: Registration): SignedInUser = error("Unused")
        override fun completeProfile(details: ProfileDetails): SignedInUser = error("Unused")
        override fun requestPasswordReset(email: String) = Unit
        override fun logout() = Unit
    }
}
