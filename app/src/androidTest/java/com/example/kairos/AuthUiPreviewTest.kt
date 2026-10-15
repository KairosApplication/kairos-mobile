package com.example.kairos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.auth.SignedInUser
import com.example.kairos.view.AccountTestActivity
import com.example.kairos.view.OnboardingStyle
import com.example.kairos.view.settings.AccountEditField
import com.example.kairos.view.settings.AccountEditorDialog
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AuthUiPreviewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun capture(view: View, name: String) {
        assertTrue(view.width > 0 && view.height > 0)
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val folder = File(view.context.getExternalFilesDir(null), "auth-ui-previews").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    @Test fun renderOnboardingAndAllEditorCardsWithOnlyFictitiousData() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            lateinit var onboarding: View
            scenario.onActivity { activity ->
                onboarding = activity.layoutInflater.inflate(R.layout.view_onboarding, null)
                OnboardingStyle.apply(onboarding)
                onboarding.findViewById<TextView>(R.id.onboarding_title).setText(R.string.onboarding_title_restocking)
                onboarding.findViewById<TextView>(R.id.onboarding_description).setText(R.string.onboarding_description)
                onboarding.findViewById<TextView>(R.id.onboarding_counter).text = "2 / 3"
                onboarding.findViewById<TextView>(R.id.onboarding_next).setText(R.string.onboarding_next)
                onboarding.findViewById<ImageView>(R.id.onboarding_illustration).setImageResource(R.drawable.ic_onboarding_2)
                ViewCompat.setOnApplyWindowInsetsListener(onboarding) { view, insets ->
                    val bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    insets
                }
                activity.setContentView(onboarding)
                ViewCompat.requestApplyInsets(onboarding)
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(350)
            scenario.onActivity { capture(onboarding, "onboarding") }
            for (field in AccountEditField.entries) {
                lateinit var editor: AccountEditorDialog.Editor
                scenario.onActivity { activity ->
                    editor = AccountEditorDialog.show(activity, 1f, SignedInUser("preview", "demo@example.com", null), field) {}
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(350)
                scenario.onActivity {
                    // Draw only the isolated test window, not the user's screen or account.
                    capture(editor.dialog.window!!.decorView, "editor-${field.name.lowercase()}")
                    editor.dialog.dismiss()
                }
            }
        }
    }
}
