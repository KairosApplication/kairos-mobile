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

    @Test fun alertEntryPreparesAnAuthorizedRestockingWithoutCallingAiOrChangingStock() {
        val aiCalls = AtomicInteger()
        val writes = AtomicInteger()
        val repository = object : ScannerRepository {
            override fun catalog(uid: String) = StockCatalog(listOf(CatalogItem(9,"Gôndola")), listOf(CatalogItem(3,"Origem")), listOf(CatalogItem(4,"Produto")))
            override fun analyze(uid: String, shelfId: Long, image: ByteArray): ShelfResult { aiCalls.incrementAndGet(); error("Not expected") }
            override fun confirm(uid: String, request: ConfirmedRestocking) { writes.incrementAndGet() }
        }
        val store = ViewModelStore()
        val states = LinkedBlockingQueue<ScannerState>()
        val observer = Observer<ScannerState> { states.offer(it) }
        lateinit var vm: ScannerViewModel
        try {
            instrumentation.runOnMainSync {
                vm=ScannerViewModel(repository); store.put("alert-entry",vm)
                vm.state.observeForever(observer); vm.account("alert-user",9,4)
            }
            var ready: ScannerState
            do { ready=states.poll(10,TimeUnit.SECONDS) ?: error("Catalog timed out") } while(ready.loading || ready.catalog==null)
            assertEquals(ScannerStep.IN_PROGRESS,ready.step)
            assertEquals(9L,ready.shelfId); assertTrue(ready.manualEntry)
            assertEquals(listOf(4L),ready.selectedProductIds)
            assertEquals(0,aiCalls.get()); assertEquals(0,writes.get())
        } finally { instrumentation.runOnMainSync { vm.state.removeObserver(observer); store.clear() } }
    }
    @Test fun batchCanFinishWithoutPhotoAndPreservesCommandsAfterTimeout() {
        val calls = AtomicInteger()
        val requests = listOf(3L,4L).map { ConfirmedRestocking(java.util.UUID.randomUUID().toString(),1,2,it,5,0) }
        val expected = requests.toList()
        val repository = object : ScannerRepository {
            override fun catalog(uid: String) = StockCatalog(listOf(CatalogItem(1,"Shelf")),listOf(CatalogItem(2,"Inventory")),listOf(CatalogItem(3,"A"),CatalogItem(4,"B")))
            override fun analyze(uid: String,shelfId: Long,image: ByteArray): ShelfResult = error("No AI needed")
            override fun confirm(uid: String,request: ConfirmedRestocking) = error("Must use batch")
            override fun confirmBatch(uid: String,requests: List<ConfirmedRestocking>) {
                assertEquals(expected,requests)
                if(calls.incrementAndGet()==1) throw java.io.IOException("Timeout")
            }
        }
        val store=ViewModelStore()
        val states=LinkedBlockingQueue<ScannerState>()
        val observer=Observer<ScannerState> { states.offer(it) }
        lateinit var vm: ScannerViewModel
        fun awaitReady(completedAttempt: Boolean = false): ScannerState {
            var state: ScannerState
            do { state=states.poll(10,TimeUnit.SECONDS) ?: error("No state") } while(state.loading || state.catalog==null || (completedAttempt && state.error.isEmpty() && state.step!=ScannerStep.COMPLETE))
            return state
        }
        try {
            instrumentation.runOnMainSync { vm=ScannerViewModel(repository);store.put("batch",vm);vm.state.observeForever(observer);vm.account("batch-user",1,initialProducts=listOf(3,4)) }
            assertEquals(ScannerStep.IN_PROGRESS,awaitReady().step);assertEquals(0,calls.get())
            states.clear()
            instrumentation.runOnMainSync { vm.finalizeBatch(requests) }
            val failed=awaitReady(true)
            assertEquals(requests,failed.pendingBatch);assertEquals(ScannerStep.IN_PROGRESS,failed.step)
            states.clear()
            instrumentation.runOnMainSync { vm.finalizeBatch(failed.pendingBatch) }
            assertEquals(ScannerStep.COMPLETE,awaitReady(true).step);assertEquals(2,calls.get())
        } finally { instrumentation.runOnMainSync { vm.state.removeObserver(observer);store.clear() } }
    }

    @Test fun resultStartsWithOneSelectedProductAndDoesNotRequireAll() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val products=listOf(3L,4L).map { id -> com.example.kairos.model.home.RestockingRecord("selection:$id","Product $id","","1",java.time.Instant.now(),com.example.kairos.model.home.RestockingStatus.REQUIRED,productId=id) }
                var selected=emptyList<com.example.kairos.model.home.RestockingRecord>()
                val home=com.example.kairos.view.StockerHomeView(activity,{}, {},onRestock={ selected=it })
                activity.setContentView(home)
                home.showStandaloneResult("selection-test","",com.example.kairos.model.home.ShelfAnalysis("1","A","Sector",2,products))
                val start=home.findViewById<android.view.View>(R.id.analysis_start)
                val first=home.findViewWithTag<android.widget.CheckBox>("select:selection:3")
                val second=home.findViewWithTag<android.widget.CheckBox>("select:selection:4")
                assertFalse(start.isEnabled)
                first.isChecked=true
                assertTrue(start.isEnabled);assertFalse(second.isChecked)
                start.performClick();assertEquals(listOf(products.first()),selected)
                first.isChecked=false;assertFalse(start.isEnabled)
                home.close()
            }
        }
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
