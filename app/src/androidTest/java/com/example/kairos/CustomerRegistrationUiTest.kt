package com.example.kairos

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.auth.*
import com.example.kairos.view.AccountTestActivity
import com.example.kairos.view.AuthEntryView
import com.example.kairos.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CustomerRegistrationUiTest {
    @Test fun completeScrollableFormSubmitsCustomerContractWithoutCreatingRemoteAccount() {
        val submitted=CountDownLatch(1)
        var received:CustomerRegistration?=null
        val store=ViewModelStore()
        val repository=object:AuthRepository {
            override fun restoreSession():SignedInUser?=null
            override fun login(email:String,password:String):SignedInUser=error("Not used")
            override fun registerAccount(email:String,password:String):SignedInUser=error("Legacy registration must not be used")
            override fun register(request:Registration):SignedInUser=error("Not used")
            override fun completeProfile(details:ProfileDetails):SignedInUser=error("Not used")
            override fun logout()=Unit
            override fun registerCustomerAccount(request:CustomerRegistration):SignedInUser {
                request.validate(); received=request; submitted.countDown()
                return SignedInUser("spring:42",request.email,null,
                    business=BusinessIdentity(42,null,null,"CUSTOMER","",apiAuthenticated=true))
            }
        }
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            lateinit var vm:AuthViewModel
            lateinit var observer:Observer<AuthUiState>
            scenario.onActivity { activity ->
                vm=AuthViewModel(repository); store.put("registration",vm)
                val entry=AuthEntryView(activity,vm)
                activity.setContentView(entry)
                observer=Observer { entry.update(it) }
                vm.state.observe(activity,observer)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { vm.navigate(AuthScreen.REGISTER) }
            fun field(label:String,value:String) {
                onView(withContentDescription(label)).inRoot(isDialog()).perform(scrollTo(),replaceText(value),closeSoftKeyboard())
            }
            try {
                field("Nome","Cliente")
                field("Sobrenome","Teste")
                onView(withContentDescription("Nascimento (DD/MM/AAAA)")).inRoot(isDialog())
                    .perform(scrollTo(),object:androidx.test.espresso.ViewAction {
                        override fun getConstraints()=isAssignableFrom(android.widget.EditText::class.java)
                        override fun getDescription()="Focus the numeric date field before keyboard input"
                        override fun perform(controller:androidx.test.espresso.UiController,view:android.view.View) {
                            view.requestFocus()
                            controller.loopMainThreadUntilIdle()
                        }
                    })
                onView(withContentDescription("Nascimento (DD/MM/AAAA)")).inRoot(isDialog())
                    .perform(typeTextIntoFocusedView("01012000"),closeSoftKeyboard())
                    .check(matches(withText("01/01/2000")))
                field("CPF (11 dígitos)","52998224725")
                field("Código do mercado","17")
                field("Email","customer@example.invalid")
                field("Senha","test-password")
                field("Confirmar senha","test-password")
                onView(withText("Continuar")).inRoot(isDialog()).perform(scrollTo(),click())
                assertTrue(submitted.await(5,TimeUnit.SECONDS))
                assertEquals(17L,received!!.companyId)
                assertEquals("2000-01-01",received!!.birthDate.toString())
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            } finally {
                scenario.onActivity { vm.state.removeObserver(observer); store.clear() }
            }
        }
    }
}
