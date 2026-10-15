package com.example.kairos

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kairos.model.auth.*
import com.example.kairos.view.AccountTestActivity
import com.example.kairos.view.settings.AccountEditField
import com.example.kairos.view.settings.AccountEditorDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountEditorFeedbackTest {
    @Test fun wrongCurrentPasswordKeepsEachEditorOpenAndAllowsRetryUntilConfirmed() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            val user = SignedInUser("editor-test", "old@example.com", null)
            for (field in listOf(AccountEditField.EMAIL, AccountEditField.PASSWORD)) {
                lateinit var editor: AccountEditorDialog.Editor
                val requests = mutableListOf<AccountEditRequest>()
                scenario.onActivity { activity ->
                    editor = AccountEditorDialog.show(activity, 1f, user, field) { requests.add(it) }
                }
                fun enter(id: Int, text: String) = onView(withId(id)).inRoot(isDialog()).perform(replaceText(text), closeSoftKeyboard())
                if (field == AccountEditField.EMAIL) enter(R.id.account_form_email, "new@example.com")
                else {
                    enter(R.id.account_form_new_password, "new-password")
                    enter(R.id.account_form_confirmation, "new-password")
                }
                enter(R.id.account_form_current_password, "wrong-password")
                onView(withId(R.id.account_form_save)).inRoot(isDialog()).perform(scrollTo(), click())
                scenario.onActivity {
                    assertEquals(1, requests.size)
                    assertTrue(editor.dialog.isShowing)
                    assertFalse(editor.dialog.findViewById<View>(R.id.account_form_save)!!.isEnabled)
                    editor.bind(user, true, "", requests.last().operation, null)
                    editor.bind(user, false, "Senha atual incorreta. Confira a senha e tente novamente.", null, false)
                    assertTrue(editor.dialog.isShowing)
                    val error = editor.dialog.findViewById<TextView>(R.id.account_form_error)!!
                    assertEquals(View.VISIBLE, error.visibility)
                    assertTrue(error.text.startsWith("Senha atual incorreta"))
                    assertTrue(editor.dialog.findViewById<EditText>(R.id.account_form_current_password)!!.text.isEmpty())
                    assertTrue(editor.dialog.findViewById<View>(R.id.account_form_save)!!.isEnabled)
                }
                enter(R.id.account_form_current_password, "correct-password")
                onView(withId(R.id.account_form_save)).inRoot(isDialog()).perform(scrollTo(), click())
                scenario.onActivity {
                    assertEquals(2, requests.size)
                    editor.bind(user, true, "", requests.last().operation, null)
                    editor.bind(user, false, "Alteração confirmada.", null, true)
                    assertFalse(editor.dialog.isShowing)
                }
            }
        }
    }
}
