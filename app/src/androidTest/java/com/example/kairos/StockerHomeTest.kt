package com.example.kairos

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import org.hamcrest.Matchers.equalTo
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor

@RunWith(AndroidJUnit4::class)
class StockerHomeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun demoUser(uid: String = "demo") = SignedInUser(uid, "$uid@example.com", UserProfile(
        uid, "João", "Silva", LocalDate.of(1990, 1, 1), "", "$uid@example.com", "", ""
    ))
    private fun texts(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun capture(name: String, view: View) {
        instrumentation.waitForIdleSync()
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun bottomMenuCornersBlendWithThePageOnEveryTab() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(2800)
            lateinit var home: StockerHomeView
            scenario.onActivity { activity ->
                home = StockerHomeView(activity, {}, {})
                home.updateSession(demoUser(), false, "")
                home.bind(StockerHomeUiState(data = DemoStockerHomeRepository().load("demo")))
                activity.setContentView(home)
            }
            for (tab in listOf(R.id.home_nav_start, R.id.home_nav_alerts, R.id.home_nav_history, R.id.home_nav_config)) {
                scenario.onActivity { home.findViewById<View>(tab).performClick() }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val menu = home.findViewById<View>(R.id.home_nav_start).parent.parent as View
                    val menuPosition = IntArray(2).also { menu.getLocationInWindow(it) }
                    val homePosition = IntArray(2).also { home.getLocationInWindow(it) }
                    val left = menuPosition[0] - homePosition[0]
                    val top = menuPosition[1] - homePosition[1]
                    val bitmap = Bitmap.createBitmap(home.width, home.height, Bitmap.Config.ARGB_8888)
                    try {
                        home.draw(Canvas(bitmap))
                        for (x in listOf(left + 1, left + menu.width - 2)) {
                            val page = bitmap.getPixel(x, top - 1)
                            val corner = bitmap.getPixel(x, top + 2)
                            assertEquals("Opaque background on tab $tab", 255, Color.alpha(corner))
                            assertTrue("Red channel mismatch on tab $tab", kotlin.math.abs(Color.red(page) - Color.red(corner)) <= 2)
                            assertTrue("Green channel mismatch on tab $tab", kotlin.math.abs(Color.green(page) - Color.green(corner)) <= 2)
                            assertTrue("Blue channel mismatch on tab $tab", kotlin.math.abs(Color.blue(page) - Color.blue(corner)) <= 2)
                        }
                    } finally { bitmap.recycle() }
                }
            }
        }
    }

    @Test fun restockingScreensFilterAndRestoreTheirStateAndAnimateTheMenu() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(2800)
            lateinit var home: StockerHomeView
            var startX = 0f
            scenario.onActivity { activity ->
                home = StockerHomeView(activity, {}, {})
                home.updateSession(demoUser(), false, "")
                home.bind(StockerHomeUiState(data = DemoStockerHomeRepository().load("demo")))
                activity.setContentView(home)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                startX = home.findViewById<View>(R.id.home_nav_indicator).translationX
                home.findViewById<View>(R.id.home_nav_alerts).performClick()
                assertTrue(texts(home).any { it.text.toString() == "Pendências e avisos" })
                assertTrue(texts(home).any { it.text.toString() == "Concluídas recentemente" })
                // The indicator starts at its old position; it must travel instead of teleporting.
                assertEquals(startX, home.findViewById<View>(R.id.home_nav_indicator).translationX, 1f)
            }
            SystemClock.sleep(350)
            capture("alerts-preview.png", home)
            scenario.onActivity {
                val indicator = home.findViewById<View>(R.id.home_nav_indicator)
                assertTrue(indicator.translationX > startX)
                val selected = home.findViewById<View>(R.id.home_nav_alerts)
                assertEquals(selected.left + (selected.width - indicator.width) / 2f, indicator.translationX, 1f)
                home.findViewById<View>(R.id.home_nav_history).performClick()
            }
            SystemClock.sleep(350)
            capture("history-preview.png", home)
            scenario.onActivity {
                home.findViewById<EditText>(R.id.history_search).setText("agua")
                val results = home.findViewById<View>(R.id.history_results)
                assertEquals(2, texts(results).count { it.text.toString() == "Água Mineral" })
                assertFalse(texts(results).any { it.text.toString().contains("Coca") })
                home.findViewById<View>(R.id.history_filter).performClick()
            }
            onView(withId(R.id.history_period)).perform(click())
            onData(equalTo("Ontem")).inRoot(isPlatformPopup()).perform(click())
            onView(withId(R.id.history_shelf)).perform(click())
            onData(equalTo("C03")).inRoot(isPlatformPopup()).perform(click())
            onView(withText("Aplicar")).perform(click())
            instrumentation.waitForIdleSync()
            capture("history-filtered-preview.png", home)
            scenario.onActivity {
                val results = home.findViewById<View>(R.id.history_results)
                assertEquals(1, texts(results).count { it.text.toString() == "Água Mineral" })
                assertTrue(home.findViewById<View>(R.id.history_filter).isSelected)
                home.findViewById<View>(R.id.home_nav_alerts).performClick()
                home.findViewById<View>(R.id.home_nav_history).performClick()
                assertEquals("agua", home.findViewById<EditText>(R.id.history_search).text.toString())
                val saved = home.save()
                val replacement = StockerHomeView(it, {}, {})
                replacement.restore(saved)
                replacement.updateSession(demoUser(), false, "")
                replacement.bind(StockerHomeUiState(data = DemoStockerHomeRepository().load("demo")))
                it.setContentView(replacement)
                home = replacement
                assertTrue(home.findViewById<View>(R.id.home_nav_history).isSelected)
                assertEquals(1, texts(home.findViewById(R.id.history_results)).count { it.text.toString() == "Água Mineral" })
                home.findViewById<EditText>(R.id.history_search).setText("inexistente")
                assertTrue(texts(home).any { it.text.toString().startsWith("Nenhuma reposição encontrada") })
                home.findViewById<View>(R.id.history_filter).performClick()
            }
            onView(withText("Limpar filtros")).perform(click())
            scenario.onActivity { activity ->
                assertEquals("", home.findViewById<EditText>(R.id.history_search).text.toString())
                assertFalse(home.findViewById<View>(R.id.history_filter).isSelected)
                assertEquals(6, texts(home.findViewById(R.id.history_results)).count { it.text.toString() == "Concluída!" })
                val icon = activity.getDrawable(R.mipmap.ic_launcher)!!
                val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                icon.setBounds(0, 0, 256, 256)
                icon.draw(Canvas(bitmap))
                File(instrumentation.targetContext.cacheDir, "launcher-preview.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
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
                home.updateSession(demoUser(), false, "")
                assertTrue(home.findViewById<View>(R.id.home_nav_history).isSelected)
                home.findViewById<View>(R.id.home_nav_config).performClick()
                home.findViewById<View>(R.id.settings_profile).performClick()
                home.findViewById<View>(R.id.home_logout).performClick()
                assertTrue(logout)
                home.handleBack()
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

    @Test fun restoredFiltersWaitForTheirOwnerAndAreDiscardedForAnotherAccount() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(2800)
            lateinit var home: StockerHomeView
            scenario.onActivity { activity ->
                home = StockerHomeView(activity, {}, {})
                home.updateSession(demoUser("account-a"), false, "")
                home.bind(StockerHomeUiState(data = DemoStockerHomeRepository().load("account-a")))
                activity.setContentView(home)
                home.findViewById<View>(R.id.home_nav_history).performClick()
                home.findViewById<EditText>(R.id.history_search).setText("agua")
                home.findViewById<View>(R.id.history_filter).performClick()
            }
            onView(withId(R.id.history_period)).perform(click())
            onData(equalTo("Ontem")).inRoot(isPlatformPopup()).perform(click())
            onView(withId(R.id.history_shelf)).perform(click())
            onData(equalTo("C03")).inRoot(isPlatformPopup()).perform(click())
            onView(withId(R.id.history_order)).perform(click())
            onData(equalTo("Mais antigas primeiro")).inRoot(isPlatformPopup()).perform(click())
            onView(withText("Aplicar")).perform(click())
            scenario.onActivity { activity ->
                val saved = home.save()
                assertEquals("account-a", saved.getString("ownerUid"))
                assertEquals("YESTERDAY", saved.getString("historyPeriod"))
                assertEquals("C03", saved.getString("historyShelf"))
                assertTrue(saved.getBoolean("historyOldestFirst"))

                val recreated = StockerHomeView(activity, {}, {})
                recreated.restore(saved)
                assertTrue(recreated.findViewById<View>(R.id.home_nav_start).isSelected)
                assertNull(recreated.findViewById<EditText>(R.id.history_search))
                // A failed restore leaves the identity unknown; recreating again must keep the owner.
                recreated.updateSession(null, false, "Falha ao restaurar sessão")
                val savedWhileUnauthenticated = recreated.save()
                assertEquals("account-a", savedWhileUnauthenticated.getString("ownerUid"))

                val sameAccount = StockerHomeView(activity, {}, {})
                sameAccount.restore(savedWhileUnauthenticated)
                sameAccount.updateSession(demoUser("account-a"), false, "")
                sameAccount.bind(StockerHomeUiState(data = DemoStockerHomeRepository().load("account-a")))
                assertTrue(sameAccount.findViewById<View>(R.id.home_nav_history).isSelected)
                assertEquals("agua", sameAccount.findViewById<EditText>(R.id.history_search).text.toString())
                assertEquals(1, texts(sameAccount.findViewById(R.id.history_results)).count { it.text.toString() == "Concluída!" })
                assertEquals("YESTERDAY", sameAccount.save().getString("historyPeriod"))
                assertEquals("C03", sameAccount.save().getString("historyShelf"))
                assertTrue(sameAccount.save().getBoolean("historyOldestFirst"))
                sameAccount.updateSession(demoUser("account-a").copy(email = "updated@example.com"), false, "")
                assertEquals("agua", sameAccount.findViewById<EditText>(R.id.history_search).text.toString())

                fun assertFreshAccount(view: StockerHomeView, uid: String) {
                    assertTrue(view.findViewById<View>(R.id.home_nav_start).isSelected)
                    val state = view.save()
                    assertEquals(uid, state.getString("ownerUid"))
                    assertEquals("", state.getString("historyQuery"))
                    assertEquals("ALL", state.getString("historyPeriod"))
                    assertNull(state.getString("historyShelf"))
                    assertFalse(state.getBoolean("historyOldestFirst"))
                    view.bind(StockerHomeUiState(data = DemoStockerHomeRepository().load(uid)))
                    view.findViewById<View>(R.id.home_nav_history).performClick()
                    assertEquals("", view.findViewById<EditText>(R.id.history_search).text.toString())
                    assertFalse(view.findViewById<View>(R.id.history_filter).isSelected)
                    assertEquals(6, texts(view.findViewById(R.id.history_results)).count { it.text.toString() == "Concluída!" })
                }

                // Reproduces LOGIN -> HOME with B after process recreation and failed restoration of A.
                val differentAccount = StockerHomeView(activity, {}, {})
                differentAccount.restore(savedWhileUnauthenticated)
                differentAccount.updateSession(demoUser("account-b"), false, "")
                assertFreshAccount(differentAccount, "account-b")
                differentAccount.restore(saved)
                assertFreshAccount(differentAccount, "account-b")

                // A direct account change must also clear the data already bound to the previous account.
                sameAccount.updateSession(demoUser("account-b"), false, "")
                assertFalse(texts(sameAccount).any { it.text.toString() == "Água Mineral" })
                assertFreshAccount(sameAccount, "account-b")

                val legacy = StockerHomeView(activity, {}, {})
                legacy.restore(android.os.Bundle(saved).apply { remove("ownerUid") })
                legacy.updateSession(demoUser("account-a"), false, "")
                assertFreshAccount(legacy, "account-a")

                val loggedOut = StockerHomeView(activity, {}, {})
                loggedOut.restore(saved)
                loggedOut.reset()
                loggedOut.updateSession(demoUser("account-a"), false, "")
                assertFreshAccount(loggedOut, "account-a")
            }
        }
    }

    @Test fun switchingAccountsDiscardsAnOlderInFlightResult() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loaded = CountDownLatch(1)
        val completed = CountDownLatch(2)
        val worker = object : ThreadPoolExecutor(2, 2, 0L, TimeUnit.SECONDS, LinkedBlockingQueue()) {
            override fun afterExecute(task: Runnable, error: Throwable?) {
                super.afterExecute(task, error)
                completed.countDown()
            }
        }
        val store = ViewModelStore()
        lateinit var vm: StockerHomeViewModel
        instrumentation.runOnMainSync {
            vm = StockerHomeViewModel(StockerHomeRepository { userId ->
                if (userId == "old") {
                    started.countDown()
                    // Simulate a blocking dependency that does not support cancellation.
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                    while (release.count > 0) {
                        try {
                            check(release.await((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS))
                        } catch (_: InterruptedException) {
                            // Keep the old call blocked until the new account has loaded.
                        }
                    }
                }
                DemoStockerHomeRepository().load(userId).copy(pendingThisWeek = if (userId == "old") 99 else 7)
            }, worker)
            store.put("home", vm)
            vm.state.observeForever {
                assertNotEquals("An old account result must never be published", 99, it.data?.pendingThisWeek)
                if (it.data?.pendingThisWeek == 7) loaded.countDown()
            }
            vm.load("old")
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                vm.reset()
                assertNull(vm.state.value?.data)
                vm.load("new")
            }
            assertTrue("The new account must load while the old request is blocked", loaded.await(5, TimeUnit.SECONDS))
            release.countDown()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { assertEquals(7, vm.state.value?.data?.pendingThisWeek) }
        } finally {
            release.countDown()
            instrumentation.runOnMainSync { store.clear() }
        }
    }

    @Test fun repositoryFailureCanBeRetriedForTheSameAccount() {
        val failed = CountDownLatch(1)
        val loaded = CountDownLatch(1)
        val store = ViewModelStore()
        val states = mutableListOf<StockerHomeUiState>()
        var calls = 0
        lateinit var vm: StockerHomeViewModel
        instrumentation.runOnMainSync {
            vm = StockerHomeViewModel(StockerHomeRepository { userId ->
                if (++calls == 1) throw IllegalStateException("Temporary failure")
                DemoStockerHomeRepository().load(userId)
            })
            store.put("home", vm)
            vm.state.observeForever {
                states.add(it)
                if (it.failed) failed.countDown()
                if (it.data != null) loaded.countDown()
            }
        }
        try {
            instrumentation.runOnMainSync { vm.load("demo") }
            assertTrue(failed.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertEquals(StockerHomeUiState(failed = true), vm.state.value)
                vm.load("demo", force = true)
            }
            assertTrue(loaded.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertEquals(2, calls)
                assertEquals(listOf(
                    StockerHomeUiState(),
                    StockerHomeUiState(loading = true),
                    StockerHomeUiState(failed = true),
                    StockerHomeUiState(loading = true),
                    vm.state.value
                ), states)
                assertNotNull(vm.state.value?.data)
                assertFalse(vm.state.value!!.failed)
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
    }
}
