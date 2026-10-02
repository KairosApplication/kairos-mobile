package com.example.kairos

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelStore
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.auth.SignedInUser
import com.example.kairos.model.auth.UserProfile
import com.example.kairos.model.home.DemoStockerHomeRepository
import com.example.kairos.model.home.StockerHomeRepository
import com.example.kairos.view.MainActivity
import com.example.kairos.view.StockerHomeView
import com.example.kairos.viewmodel.StockerHomeUiState
import com.example.kairos.viewmodel.StockerHomeViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StockerHomeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun texts(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun navigationRetryAndLogoutAreAvailableWithoutChangingTheRealSession() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(2800)
            lateinit var home: StockerHomeView
            var logout = false
            var retry = false
            scenario.onActivity { activity ->
                home = StockerHomeView(activity, { retry = true }, { logout = true })
                home.updateSession(SignedInUser("demo", "demo@example.com", UserProfile(
                    "demo", "João", "Silva", LocalDate.of(1990, 1, 1), "", "demo@example.com", "", ""
                )), false, "")
                home.bind(StockerHomeUiState(data = DemoStockerHomeRepository().load("demo")))
                activity.setContentView(home)
            }
            instrumentation.waitForIdleSync()
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(instrumentation.targetContext.cacheDir, "home-preview.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            scenario.onActivity {
                assertTrue(texts(home).any { it.text.toString() == "Olá, João" })
                assertTrue(texts(home).any { it.text.toString() == "Dados de demonstração" })
                val nav = home.findViewById<View>(R.id.home_nav_start)
                val position = IntArray(2)
                nav.getLocationInWindow(position)
                val systemBottom = ViewCompat.getRootWindowInsets(home)
                    ?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
                assertTrue("The menu must stay above Android navigation controls",
                    position[1] + nav.height <= home.height - systemBottom)
                home.findViewById<View>(R.id.home_pending).performClick()
                assertTrue(home.findViewById<View>(R.id.home_nav_alerts).isSelected)
                assertTrue(home.handleBack())
                home.findViewById<View>(R.id.home_completed).performClick()
                assertTrue(home.findViewById<View>(R.id.home_nav_history).isSelected)
                val saved = home.save()
                home.reset()
                home.restore(saved)
                assertTrue(home.findViewById<View>(R.id.home_nav_history).isSelected)
                home.findViewById<View>(R.id.home_nav_config).performClick()
                home.findViewById<View>(R.id.home_logout).performClick()
                assertTrue(logout)
                home.handleBack()
                home.bind(StockerHomeUiState(failed = true))
                texts(home).single { it.text.toString() == "Tentar novamente" }.performClick()
                assertTrue(retry)
                val empty = DemoStockerHomeRepository().load("demo").copy(priorities = emptyList())
                home.bind(StockerHomeUiState(data = empty))
                assertTrue(texts(home).any { it.text.toString() == "Nenhuma prioridade no momento." })
            }
        }
    }

    @Test fun switchingAccountsDiscardsAnOlderInFlightResult() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loaded = CountDownLatch(1)
        val store = ViewModelStore()
        lateinit var vm: StockerHomeViewModel
        instrumentation.runOnMainSync {
            vm = StockerHomeViewModel(StockerHomeRepository { userId ->
                if (userId == "old") {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                DemoStockerHomeRepository().load(userId).copy(pendingThisWeek = if (userId == "old") 99 else 7)
            })
            store.put("home", vm)
            vm.state.observeForever { if (it.data?.pendingThisWeek == 7) loaded.countDown() }
            vm.load("old")
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                vm.reset()
                assertNull(vm.state.value?.data)
                vm.load("new")
            }
            release.countDown()
            assertTrue(loaded.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { assertEquals(7, vm.state.value?.data?.pendingThisWeek) }
        } finally {
            release.countDown()
            instrumentation.runOnMainSync { store.clear() }
        }
    }
}
