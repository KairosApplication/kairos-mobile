package com.example.kairos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.auth.BusinessIdentity
import com.example.kairos.model.auth.SignedInUser
import com.example.kairos.model.cliente.*
import com.example.kairos.view.AccountTestActivity
import com.example.kairos.view.CustomerHomeView
import com.example.kairos.viewmodel.CustomerUiState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class CustomerDesignTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun user(uid:String)=SignedInUser(uid,"cliente@example.test",null,
        business=BusinessIdentity(901,null,1,"CUSTOMER","Mercado de teste",5,true))
    private fun data()=CustomerData(1,"Mercado de teste",listOf(
        CustomerProduct(1,"Arroz integral 1 kg","Sabor da Terra",BigDecimal("12.50"),false),
        CustomerProduct(2,"Leite integral 1 L","Fazenda",BigDecimal("6.49"),true),
        CustomerProduct(3,"Café torrado e moído 500 g","Bom Dia",BigDecimal("18.90"),false)),
        emptyList(),emptyList(),emptySet(),emptyList(),emptyList(),
        setOf("receipts","reports","recommendations","locations"),receiptPhotoAvailable=true)
    private fun texts(view:View):List<String> = when(view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun capture(scenario:ActivityScenario<AccountTestActivity>,home:()->CustomerHomeView,name:String) {
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(200)
        scenario.onActivity { activity ->
            val view=home()
            assertTrue(view.width>0 && view.height>0)
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(activity.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    @Test fun customerReusesSettingsWithoutStockerCountersOrChat() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            lateinit var home:CustomerHomeView
            val uid="customer-design-${System.nanoTime()}"
            val favorites=CustomerFavorites(instrumentation.targetContext,uid)
            favorites.set(2,true)
            try {
                scenario.onActivity { it.delegate.localNightMode=AppCompatDelegate.MODE_NIGHT_NO }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    home=CustomerHomeView(activity,{}, {}, {}, {}, {})
                    home.session(user(uid));home.bind(CustomerUiState(data=data()))
                    activity.setContentView(home)
                }
                capture(scenario,{home},"customer-design-home-light.png")
                scenario.onActivity { home.findViewById<View>(R.id.customer_nav_favorites).performClick() }
                capture(scenario,{home},"customer-design-favorites-light.png")
                scenario.onActivity { home.findViewById<View>(R.id.customer_nav_settings).performClick() }
                capture(scenario,{home},"customer-design-settings-light.png")
                scenario.onActivity {
                    assertNotNull(home.findViewById<View>(R.id.settings_security))
                    assertNotNull(home.findViewById<View>(R.id.settings_appearance))
                    home.findViewById<View>(R.id.settings_profile).performClick()
                    assertTrue(texts(home).contains("Cliente"))
                    assertFalse(texts(home).contains(it.getString(R.string.account_restocks)))
                    assertFalse(texts(home).contains(it.getString(R.string.account_damages)))
                }
                capture(scenario,{home},"customer-design-profile-light.png")
                scenario.onActivity {
                    assertTrue(home.handleBack())
                    home.findViewById<View>(R.id.settings_security).performClick()
                    assertNotNull(home.findViewById<View>(R.id.account_security_data))
                    assertNull(home.findViewById<View>(R.id.account_edit_password))
                }
                capture(scenario,{home},"customer-design-security-light.png")
                scenario.onActivity {
                    home.handleBack();home.findViewById<View>(R.id.settings_help).performClick()
                    assertNull(home.findViewById<View>(R.id.settings_contact_support))
                    home.findViewById<EditText>(R.id.help_search).setText("favoritos")
                    assertTrue(texts(home).contains(it.getString(R.string.customer_help_favorites)))
                    assertFalse(texts(home).contains(it.getString(R.string.help_restock)))
                    home.findViewById<EditText>(R.id.help_search).setText("")
                }
                capture(scenario,{home},"customer-design-help-light.png")
                scenario.onActivity {
                    home.handleBack();home.findViewById<View>(R.id.settings_notifications).performClick()
                    assertTrue(texts(home).contains(it.getString(R.string.customer_activity_notifications)))
                    assertFalse(texts(home).contains(it.getString(R.string.preference_restocking)))
                    val toggle=home.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.preference_restocking)
                    toggle.performClick()
                    assertFalse(com.example.kairos.model.home.StockerPreferences(it,uid).enabled("customer_activity"))
                    home.handleBack();home.findViewById<View>(R.id.settings_notifications).performClick()
                    assertFalse(home.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.preference_restocking).isChecked)
                }
                capture(scenario,{home},"customer-design-notifications-light.png")
            } finally { favorites.set(2,false) }
        }
    }
    @Test fun settingsRestoreBelongsToSameAccountOnly() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val original=CustomerHomeView(activity,{}, {}, {}, {}, {})
                original.session(user("customer-restore-a"));original.bind(CustomerUiState(data=data()))
                original.findViewById<View>(R.id.customer_nav_settings).performClick()
                original.findViewById<View>(R.id.settings_appearance).performClick()
                val saved=original.save()
                val restored=CustomerHomeView(activity,{}, {}, {}, {}, {})
                restored.session(user("customer-restore-a"));restored.restore(saved)
                assertNotNull(restored.findViewById<View>(R.id.settings_appearance_content))
                val other=CustomerHomeView(activity,{}, {}, {}, {}, {})
                other.session(user("customer-restore-b"));other.restore(saved)
                assertTrue(other.findViewById<View>(R.id.customer_nav_home).isSelected)
                assertNull(other.findViewById<View>(R.id.settings_appearance_content))
                original.close();restored.close();other.close()
            }
        }
    }
    @Test fun customerHomeAndSettingsRenderWithDarkTheme() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            lateinit var home:CustomerHomeView
            scenario.onActivity { it.delegate.localNightMode=AppCompatDelegate.MODE_NIGHT_YES }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                home=CustomerHomeView(activity,{}, {}, {}, {}, {})
                home.session(user("customer-dark-preview"));home.bind(CustomerUiState(data=data()))
                activity.setContentView(home)
            }
            capture(scenario,{home},"customer-design-home-dark.png")
            scenario.onActivity { home.findViewById<View>(R.id.customer_nav_settings).performClick() }
            capture(scenario,{home},"customer-design-settings-dark.png")
            scenario.onActivity { home.findViewById<View>(R.id.settings_security).performClick() }
            capture(scenario,{home},"customer-design-security-dark.png")
        }
    }
}
