package com.example.kairos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.auth.SignedInUser
import com.example.kairos.model.auth.UserProfile
import com.example.kairos.model.home.DemoStockerHomeRepository
import com.example.kairos.model.home.StockerPreferences
import com.example.kairos.view.AccountTestActivity
import com.example.kairos.view.StockerHomeView
import com.example.kairos.viewmodel.StockerHomeUiState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StockerPreferencesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun user(uid: String) = SignedInUser(uid, "demo@example.com", UserProfile(
        uid, "João", "Silva", LocalDate.of(1990, 1, 1), "", "demo@example.com", "", ""))

    private fun screenshot(home: StockerHomeView, name: String) {
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            if (home.width > 0 && home.height > 0) {
                val bitmap = Bitmap.createBitmap(home.width, home.height, Bitmap.Config.ARGB_8888)
                home.draw(Canvas(bitmap))
                File(home.context.filesDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun notificationChoicesPersistPerUserAndHelpFiltersQuestions() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            lateinit var home: StockerHomeView
            val uid = "preferences-${System.nanoTime()}"
            scenario.onActivity { activity ->
                home = StockerHomeView(activity, {}, {})
                home.updateSession(user(uid), false, "")
                activity.setContentView(home)
                home.findViewById<View>(R.id.home_nav_config).performClick()
                home.findViewById<View>(R.id.settings_notifications).performClick()
                val toggle = home.findViewById<SwitchCompat>(R.id.preference_restocking)
                assertTrue(toggle.isChecked)
                toggle.performClick()
                assertFalse(StockerPreferences(activity, uid).enabled("restocking"))
                assertTrue(StockerPreferences(activity, "$uid-other").enabled("restocking"))
                home.handleBack()
                home.findViewById<View>(R.id.settings_notifications).performClick()
                assertFalse(home.findViewById<SwitchCompat>(R.id.preference_restocking).isChecked)
            }
            screenshot(home, "notifications-preview")
            scenario.onActivity {
                home.handleBack()
                home.findViewById<View>(R.id.settings_appearance).performClick()
                assertNotNull(home.findViewById<View>(R.id.settings_appearance_content))
            }
            screenshot(home, "appearance-preview")
            scenario.onActivity {
                home.handleBack()
                home.findViewById<View>(R.id.settings_help).performClick()
                home.findViewById<EditText>(R.id.help_search).setText("senha")
                val questions = descendants(home).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(questions.contains("Como alterar minha senha?"))
                assertFalse(questions.contains("Como fazer uma reposição?"))
                home.findViewById<EditText>(R.id.help_search).setText("inexistente")
                assertTrue(descendants(home).filterIsInstance<TextView>().any { it.text.toString() == "Nenhuma pergunta encontrada." })
                home.findViewById<EditText>(R.id.help_search).setText("")
            }
            screenshot(home, "help-preview")
        }
    }

    @Test fun analysisSelectionSurvivesRecreationOnlyForItsOwner() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            lateinit var home: StockerHomeView
            val uid = "analysis-demo"
            scenario.onActivity { activity ->
                home = StockerHomeView(activity, {}, {})
                home.updateSession(user(uid), false, "")
                home.bind(StockerHomeUiState(data = DemoStockerHomeRepository().load(uid)))
                activity.setContentView(home)
                home.findViewById<View>(R.id.home_verify).performClick()
            }
            instrumentation.waitForIdleSync()
            // Espresso opens only the explicitly labelled demonstration action.
            captureDialog("shelf-guidance")
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.analysis_demo_open))
                .perform(androidx.test.espresso.action.ViewActions.click())
            scenario.onActivity { activity ->
                assertFalse(home.findViewById<View>(R.id.analysis_start).isEnabled)
                descendants(home).filterIsInstance<CheckBox>().first().performClick()
                assertTrue(home.findViewById<View>(R.id.analysis_start).isEnabled)
                val saved = home.save()
                val recreated = StockerHomeView(activity, {}, {})
                recreated.restore(saved)
                recreated.updateSession(user(uid), false, "")
                recreated.bind(StockerHomeUiState(data = DemoStockerHomeRepository().load(uid)))
                assertTrue(descendants(recreated).filterIsInstance<CheckBox>().first().isChecked)
                assertTrue(recreated.findViewById<View>(R.id.analysis_start).isEnabled)
                val other = StockerHomeView(activity, {}, {})
                other.restore(saved)
                other.updateSession(user("other"), false, "")
                assertNull(other.findViewById<View>(R.id.analysis_start))
                assertTrue(other.findViewById<View>(R.id.home_nav_start).isSelected)
            }
            screenshot(home, "analysis-preview")
            scenario.onActivity { home.findViewById<View>(R.id.analysis_start).performClick() }
            captureDialog("restocking-guidance")
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.dialog_close))
                .perform(androidx.test.espresso.action.ViewActions.click())
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun alertDetailsOpenTheLinkedShelfAndBackReturnsToAlerts() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            lateinit var home: StockerHomeView
            val uid = "linked-alert"
            val data = DemoStockerHomeRepository().load(uid)
            val record = data.restockingAlerts.first { it.shelfCode == "B02" }
            scenario.onActivity { activity ->
                home = StockerHomeView(activity, {}, {})
                home.updateSession(user(uid), false, "")
                home.bind(StockerHomeUiState(data = data))
                activity.setContentView(home)
                home.findViewById<View>(R.id.home_nav_alerts).performClick()
                home.findViewWithTag<View>(record.id).performClick()
            }
            instrumentation.waitForIdleSync()
            captureDialog("alert-analysis")
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.dialog_view_analysis))
                .perform(androidx.test.espresso.action.ViewActions.click())
            scenario.onActivity {
                assertEquals(record.analysisId, home.save().getString("analysisId"))
                assertTrue(descendants(home).filterIsInstance<TextView>().any { it.text.toString() == "Gôndola B02" })
                assertTrue(home.handleBack())
                assertTrue(home.findViewById<View>(R.id.home_nav_alerts).isSelected)
            }
        }
    }
}
