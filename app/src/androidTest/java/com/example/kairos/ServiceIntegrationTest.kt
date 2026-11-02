package com.example.kairos

import android.graphics.Bitmap
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.chat.*
import com.example.kairos.model.scanner.*
import com.example.kairos.view.AccountTestActivity
import com.example.kairos.view.ChatDialog
import com.example.kairos.view.AnalysisProgressView
import com.example.kairos.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Isolated host and fake repositories only. No Firebase login, remote service or real camera capture. */
@RunWith(AndroidJUnit4::class)
class ServiceIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val id = "000000000000000000000001"

    @Test fun chatPreventsDuplicateSendsAndDiscardsPreviousAccountResponse() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val sends = AtomicInteger()
        val repository = object : ChatRepository {
            override fun conversations(uid: String, page: Int) = ConversationPage(emptyList(), false)
            override fun create(uid: String) = "000000000000000000000002"
            override fun messages(uid: String, id: String) = emptyList<ChatMessage>()
            override fun send(uid: String, id: String?, question: String): ChatReply {
                sends.incrementAndGet(); entered.countDown()
                while (release.count > 0) { try { release.await(1, TimeUnit.SECONDS) } catch (_: InterruptedException) { } }
                return ChatReply(this@ServiceIntegrationTest.id, ChatMessage("AI", "Private company A response"))
            }
        }
        val states = LinkedBlockingQueue<ChatState>()
        val observer = Observer<ChatState> { states.offer(it) }
        val store = ViewModelStore()
        lateinit var vm: ChatViewModel
        try {
            instrumentation.runOnMainSync {
                vm = ChatViewModel(repository); store.put("chat", vm); vm.ui.observeForever(observer)
                vm.account("account-A"); vm.draft("Question"); vm.send(); vm.send()
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); assertEquals(1, sends.get())
            states.clear()
            instrumentation.runOnMainSync { vm.account("account-B"); vm.create() }
            var state: ChatState
            do { state = states.poll(5, TimeUnit.SECONDS) ?: error("No state") } while (state.loading || state.conversationId == null)
            assertEquals("000000000000000000000002", state.conversationId)
            release.countDown(); instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue(vm.ui.value!!.messages.isEmpty()); assertEquals("000000000000000000000002", vm.ui.value!!.conversationId)
            }
        } finally {
            release.countDown()
            instrumentation.runOnMainSync { vm.ui.removeObserver(observer); store.clear() }
        }
    }

    @Test fun scannerAnalysisNeverWritesAndOnlyExplicitConfirmationCompletes() {
        val writes = AtomicInteger()
        val repository = object : ScannerRepository {
            override fun catalog(uid: String) = StockCatalog(listOf(CatalogItem(1, "Shelf")), listOf(CatalogItem(2, "Inventory")), listOf(CatalogItem(3, "Product")))
            override fun analyze(uid: String, shelfId: Long, image: ByteArray): ShelfResult {
                assertTrue(image.isNotEmpty())
                return ShelfResult(shelfId, listOf(ShelfObservation("Uncertain", "", null, "low", true, emptyList())), listOf("Confira o catálogo"))
            }
            override fun confirm(uid: String, request: ConfirmedRestocking) { request.validate(); writes.incrementAndGet() }
        }
        val store = ViewModelStore()
        val states = LinkedBlockingQueue<ScannerState>()
        val observer = Observer<ScannerState> { states.offer(it) }
        lateinit var vm: ScannerViewModel
        val file = File(instrumentation.targetContext.cacheDir, "scanner-test-only.jpg")
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        fun awaitStep(step: ScannerStep): ScannerState {
            var result: ScannerState
            do { result = states.poll(10, TimeUnit.SECONDS) ?: error("Scanner did not complete") }
            while (result.loading || result.step != step || result.catalog == null)
            return result
        }
        try {
            instrumentation.runOnMainSync { vm = ScannerViewModel(repository); store.put("scanner", vm); vm.state.observeForever(observer); vm.account("test-A") }
            awaitStep(ScannerStep.START)
            instrumentation.runOnMainSync { vm.shelf(1); vm.photo(file); vm.analyze() }
            val analysis = awaitStep(ScannerStep.RESULT)
            assertNull(analysis.result!!.observations.single().visibleQuantity); assertEquals(0, writes.get())
            instrumentation.runOnMainSync {
                val confirmed = vm.prepare(2, 3, 5, 1)
                vm.begin(confirmed)
                assertEquals(ScannerStep.IN_PROGRESS, vm.state.value!!.step); assertEquals(0, writes.get())
                val verification = File(instrumentation.targetContext.cacheDir, "scanner-verification-test-only.jpg")
                verification.writeBytes(byteArrayOf(1))
                vm.photo(verification)
                assertEquals(ScannerStep.CONFIRMING, vm.state.value!!.step)
                vm.pending(confirmed); assertEquals(0, writes.get()); vm.confirm(confirmed); vm.confirm(confirmed)
            }
            awaitStep(ScannerStep.COMPLETE)
            assertEquals(1, writes.get()); assertFalse(file.exists())
        } finally { file.delete(); instrumentation.runOnMainSync { vm.state.removeObserver(observer); store.clear() } }
    }

    @Test fun supportAndAnalysisRenderWithLocalAssets() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            lateinit var dialog: ChatDialog
            val store = ViewModelStore()
            scenario.onActivity { activity ->
                val view = AnalysisProgressView(activity, false)
                activity.setContentView(view)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(bitmap))
                File(activity.cacheDir, "integration-analysis.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                val vm = ChatViewModel(object : ChatRepository {
                    override fun conversations(uid: String, page: Int) = ConversationPage(emptyList(), false)
                    override fun create(uid: String) = id
                    override fun messages(uid: String, id: String) = emptyList<ChatMessage>()
                    override fun send(uid: String, id: String?, question: String) = ChatReply(this@ServiceIntegrationTest.id, ChatMessage("AI", "Test"))
                })
                store.put("chat", vm); vm.account("local-ui-test")
                dialog = ChatDialog(activity, vm); dialog.show()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
                val screenshot = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(screenshot))
                File(activity.cacheDir, "integration-support.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
                dialog.close(); store.clear()
            }
        }
    }
}
