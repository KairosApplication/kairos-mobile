package com.example.kairos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.auth.SignedInUser
import com.example.kairos.model.auth.UserProfile
import com.example.kairos.view.MainActivity
import com.example.kairos.view.StockerHomeView
import com.example.kairos.view.settings.SettingsDestination
import com.example.kairos.viewmodel.StockerHomeUiState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StockerSettingsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun user(uid: String = "settings-demo") = SignedInUser(uid, "$uid@example.com", UserProfile(
        uid, "João", "Silva", LocalDate.of(1990, 1, 1), "", "$uid@example.com", "", ""
    ))

    private fun title(home: StockerHomeView) = home.findViewById<TextView>(R.id.settings_screen_title).text.toString()

    @Test fun allSettingsDestinationsNavigateAndBackReturnsThroughTheirParents() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(2800)
            lateinit var home: StockerHomeView
            var logoutCalls = 0
            scenario.onActivity { activity ->
                home = StockerHomeView(activity, {}, { logoutCalls++ })
                home.updateSession(user(), false, "")
                // Settings must remain available even when loading Home data fails.
                home.bind(StockerHomeUiState(failed = true))
                activity.setContentView(home)
                home.findViewById<View>(R.id.home_nav_config).performClick()
                assertEquals("Configurações", title(home))
                assertEquals(View.GONE, home.findViewById<View>(R.id.settings_back).visibility)
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(350)
            scenario.onActivity { activity ->
                val bitmap = Bitmap.createBitmap(home.width, home.height, Bitmap.Config.ARGB_8888)
                home.draw(Canvas(bitmap))
                File(activity.filesDir, "settings-preview.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                val destinations = listOf(SettingsDestination.PROFILE, SettingsDestination.SECURITY,
                    SettingsDestination.NOTIFICATIONS, SettingsDestination.APPEARANCE, SettingsDestination.HELP,
                    SettingsDestination.ABOUT)
                for (destination in destinations) {
                    home.findViewById<View>(destination.viewId).performClick()
                    assertEquals(activity.getString(destination.title), title(home))
                    assertNotNull(home.findViewById<View>(R.id.settings_template))
                    assertTrue(home.findViewById<View>(R.id.home_nav_config).isSelected)
                    assertEquals(View.VISIBLE, home.findViewById<View>(R.id.settings_back).visibility)
                    home.findViewById<View>(R.id.settings_back).performClick()
                    assertEquals("Configurações", title(home))
                }
                home.findViewById<View>(R.id.settings_help).performClick()
                home.findViewById<View>(R.id.settings_contact_support).performClick()
                assertEquals("Suporte", title(home))
                assertTrue(home.handleBack())
                assertEquals("Ajuda e Suporte", title(home))
                assertTrue(home.handleBack())
                assertEquals("Configurações", title(home))
                home.findViewById<View>(R.id.settings_security).performClick()
                home.findViewById<View>(R.id.home_nav_alerts).performClick()
                assertTrue(home.findViewById<View>(R.id.home_nav_alerts).isSelected)
                home.findViewById<View>(R.id.home_nav_config).performClick()
                assertEquals("Configurações", title(home))
                home.findViewById<View>(R.id.settings_profile).performClick()
                home.updateSession(user(), true, "")
                assertFalse(home.findViewById<View>(R.id.home_logout).isEnabled)
                assertFalse(home.findViewById<View>(R.id.settings_back).isEnabled)
                home.updateSession(user(), false, "")
                home.findViewById<View>(R.id.home_logout).performClick()
                assertEquals(1, logoutCalls)
                assertTrue(home.handleBack())
                assertEquals("Configurações", title(home))
                assertTrue(home.handleBack())
                assertTrue(home.findViewById<View>(R.id.home_nav_start).isSelected)
                assertFalse(home.handleBack())
            }
        }
    }

    @Test fun nestedSettingsRestoreOnlyForTheirOwnerAndLogoutClearsTheDestination() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(2800)
            scenario.onActivity { activity ->
                val original = StockerHomeView(activity, {}, {})
                original.updateSession(user(), false, "")
                activity.setContentView(original)
                original.findViewById<View>(R.id.home_nav_config).performClick()
                original.findViewById<View>(R.id.settings_help).performClick()
                original.findViewById<View>(R.id.settings_contact_support).performClick()
                val saved = original.save()

                val recreated = StockerHomeView(activity, {}, {})
                recreated.restore(saved)
                assertTrue(recreated.findViewById<View>(R.id.home_nav_start).isSelected)
                val pending = recreated.save()
                recreated.restore(pending)
                recreated.updateSession(user(), false, "")
                assertTrue(recreated.findViewById<View>(R.id.home_nav_config).isSelected)
                assertEquals("Suporte", title(recreated))
                assertTrue(recreated.handleBack())
                assertEquals("Ajuda e Suporte", title(recreated))
                // Tapping the selected bottom item returns to the root of Settings.
                recreated.findViewById<View>(R.id.home_nav_config).performClick()
                assertEquals("Configurações", title(recreated))

                val anotherAccount = StockerHomeView(activity, {}, {})
                anotherAccount.restore(saved)
                anotherAccount.updateSession(user("other-account"), false, "")
                assertTrue(anotherAccount.findViewById<View>(R.id.home_nav_start).isSelected)
                anotherAccount.findViewById<View>(R.id.home_nav_config).performClick()
                assertEquals("Configurações", title(anotherAccount))
                anotherAccount.findViewById<View>(R.id.settings_appearance).performClick()
                anotherAccount.reset()
                anotherAccount.updateSession(user("other-account"), false, "")
                anotherAccount.findViewById<View>(R.id.home_nav_config).performClick()
                assertEquals("Configurações", title(anotherAccount))

                val invalid = android.os.Bundle(saved).apply { putString("settingsDestination", "REMOVED_SCREEN") }
                recreated.restore(invalid)
                assertEquals("Configurações", title(recreated))
            }
        }
    }
}
