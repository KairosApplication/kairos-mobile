package com.example.kairos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.auth.*
import com.example.kairos.model.home.DemoStockerHomeRepository
import com.example.kairos.view.AccountTestActivity
import com.example.kairos.view.StockerHomeView
import com.example.kairos.viewmodel.StockerHomeUiState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AccountScreensTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun dialogView(matcher: org.hamcrest.Matcher<View>) = onView(matcher).inRoot(isDialog())
    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    @Test fun accountScreensUseSessionDataMaskCpfAndSubmitValidatedRequests() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            SystemClock.sleep(2800)
            lateinit var home: StockerHomeView
            var account = SignedInUser("account-ui", "joaosilva@email.com", UserProfile("account-ui", "João", "Silva",
                LocalDate.of(1985, 10, 22), "12364578902", "joaosilva@email.com", "12345678", "basico"))
            val requests = mutableListOf<AccountEditRequest>()
            var logout = false
            scenario.onActivity { activity ->
                home = StockerHomeView(activity, {}, { logout = true }, onAccountEdit = { request ->
                    requests.add(request)
                    if (request is AccountEditRequest.Name) {
                        account = account.copy(profile = account.profile!!.copy(name = request.name, lastName = request.lastName))
                        home.updateSession(account, false, "Nome atualizado com sucesso.")
                    }
                })
                home.updateSession(account, false, "")
                home.bind(StockerHomeUiState(data = DemoStockerHomeRepository().load(account.uid)))
                activity.setContentView(home)
                home.findViewById<View>(R.id.home_nav_config).performClick()
                home.findViewById<View>(R.id.settings_profile).performClick()
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(350)
            fun capture(name: String) {
                scenario.onActivity { activity ->
                    val bitmap = Bitmap.createBitmap(home.width, home.height, Bitmap.Config.ARGB_8888)
                    home.draw(Canvas(bitmap))
                    File(activity.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            capture("profile-preview.png")
            scenario.onActivity {
                assertTrue(texts(home).contains("João Silva"))
                assertTrue(texts(home).contains("9"))
                assertTrue(texts(home).contains("8"))
                assertTrue(texts(home).contains("Aurora"))
                home.findViewById<View>(R.id.account_profile_edit).performClick()
                assertNotNull(home.findViewById<View>(R.id.account_security_data))
                assertTrue(texts(home).contains("***.645.***-02"))
                assertFalse(texts(home).any { it.contains("12364578902") })
                assertTrue(texts(home).contains("22/10/1985"))
            }
            instrumentation.waitForIdleSync()
            capture("security-preview.png")
            onView(withId(R.id.account_edit_name)).perform(click())
            dialogView(withId(R.id.account_form_name)).perform(replaceText(""))
            dialogView(withText("Salvar")).perform(click())
            dialogView(withId(R.id.account_form_error)).check { view, _ -> assertTrue((view as TextView).text.isNotBlank()) }
            assertTrue(requests.isEmpty())
            dialogView(withId(R.id.account_form_name)).perform(replaceText("Maria Alice"))
            dialogView(withId(R.id.account_form_last_name)).perform(replaceText("de Souza"), closeSoftKeyboard())
            dialogView(withText("Salvar")).perform(click())
            scenario.onActivity {
                assertEquals(1, requests.size)
                assertTrue(texts(home).contains("Maria Alice de Souza"))
                home.updateSession(account, false, "")
            }
            onView(withId(R.id.account_edit_email)).perform(click())
            dialogView(withId(R.id.account_form_email)).perform(replaceText("new@example.com"))
            dialogView(withId(R.id.account_form_current_password)).perform(replaceText("current-password"), closeSoftKeyboard())
            dialogView(withText("Enviar confirmação")).perform(click())
            scenario.onActivity {
                val request = requests.last() as AccountEditRequest.Email
                assertEquals(account.uid, request.uid)
                assertEquals("new@example.com", request.email)
                assertTrue(texts(home).contains("joaosilva@email.com"))
            }
            onView(withId(R.id.account_edit_password)).perform(click())
            dialogView(withId(R.id.account_form_current_password)).perform(replaceText("current-password"))
            dialogView(withId(R.id.account_form_new_password)).perform(replaceText("new-password"))
            dialogView(withId(R.id.account_form_confirmation)).perform(replaceText("different-password"), closeSoftKeyboard())
            dialogView(withText("Salvar")).perform(click())
            assertEquals(2, requests.size)
            dialogView(withId(R.id.account_form_confirmation)).perform(replaceText("new-password"), closeSoftKeyboard())
            dialogView(withText("Salvar")).perform(click())
            scenario.onActivity {
                assertTrue(requests.last() is AccountEditRequest.Password)
                val saved = home.save().toString()
                assertFalse(saved.contains("current-password"))
                assertFalse(saved.contains("new-password"))
                home.updateSession(account, true, "", AccountOperation.PASSWORD)
                assertFalse(home.findViewById<View>(R.id.account_edit_password).isEnabled)
                home.updateSession(account, false, "")
                assertTrue(home.handleBack())
                home.findViewById<View>(R.id.settings_profile).performClick()
                assertTrue(texts(home).contains("Maria Alice de Souza"))
                home.findViewById<View>(R.id.home_logout).performClick()
                assertTrue(logout)
            }
        }
    }
}
