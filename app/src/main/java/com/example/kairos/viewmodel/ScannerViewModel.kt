package com.example.kairos.viewmodel

import androidx.lifecycle.*
import android.os.Handler
import android.os.Looper
import com.example.kairos.model.scanner.*
import com.example.kairos.model.auth.AccountChangedException
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

enum class ScannerStep { START, PREVIEW, RESULT, IN_PROGRESS, CONFIRMING, COMPLETE }
data class ScannerState(val step: ScannerStep = ScannerStep.START, val loading: Boolean = false,
                        val catalog: StockCatalog? = null, val shelfId: Long? = null,
                        val result: ShelfResult? = null, val error: String = "", val photo: File? = null,
                        val pending: ConfirmedRestocking? = null, val draft: ConfirmedRestocking? = null,
                        val manualEntry: Boolean = false,
                        val selectedProductIds: List<Long> = emptyList(),
                        val pendingBatch: List<ConfirmedRestocking> = emptyList(),
                        val marketData: com.example.kairos.model.home.StockerHomeData? = null,
                        val analyzedAt: java.time.Instant? = null)

class ScannerViewModel(private val repository: ScannerRepository = HttpScannerRepository()) : ViewModel() {
    private val mutable = MutableLiveData(ScannerState())
    val state: LiveData<ScannerState> = mutable
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newCachedThreadPool()
    private var active: java.util.concurrent.Future<*>? = null
    private var uid: String? = null
    private var generation = 0L
    private var recovered: ConfirmedRestocking? = null
    private var recoveryOwner: String? = null
    private var recoveredBatch = emptyList<ConfirmedRestocking>()

    fun recover(owner: String, request: ConfirmedRestocking?) { recoveryOwner = owner; recovered = request }
    fun recoverBatch(owner: String, requests: List<ConfirmedRestocking>) { recoveryOwner = owner; recoveredBatch = requests }

    fun account(owner: String, initialShelf: Long? = null, initialProduct: Long? = null, initialProducts: List<Long> = emptyList()) {
        if (uid == owner) return
        val recovery = recovered.takeIf { recoveryOwner == owner }
        val batchRecovery = recoveredBatch.takeIf { recoveryOwner == owner }.orEmpty()
        reset(); uid = owner; recovered = recovery; recoveredBatch = batchRecovery
        run { before ->
            val catalog = repository.catalog(owner)
            val pending = recovered
            val targets = (initialProducts + listOfNotNull(initialProduct)).distinct()
            if (recoveredBatch.isNotEmpty()) before.copy(catalog = catalog, step = ScannerStep.IN_PROGRESS,
                shelfId = recoveredBatch.first().shelfId, selectedProductIds = recoveredBatch.map { it.productId },
                pendingBatch = recoveredBatch, result = ShelfResult(recoveredBatch.first().shelfId, emptyList(), emptyList()))
            else if (pending == null && initialShelf != null) {
                require(catalog.shelves.any { it.id == initialShelf }) { "A gôndola não está mais disponível para sua conta." }
                require(targets.size in 1..25 && targets.all { target -> catalog.products.any { it.id == target } }) { "Confirme os produtos no catálogo do mercado." }
                before.copy(catalog = catalog, step = ScannerStep.IN_PROGRESS, shelfId = initialShelf,
                    result = ShelfResult(initialShelf, emptyList(), emptyList()), manualEntry = true, selectedProductIds = targets)
            } else if (pending == null) before.copy(catalog = catalog) else before.copy(catalog = catalog,
                step = ScannerStep.IN_PROGRESS, shelfId = pending.shelfId, pending = pending,
                pendingBatch = listOf(pending), selectedProductIds = listOf(pending.productId),
                result = ShelfResult(pending.shelfId, emptyList(), emptyList()))
        }
    }
    fun error(message: String) { mutable.value = mutable.value!!.copy(error = message) }
    fun shelf(id: Long?) {
        val before = mutable.value!!
        if (!before.loading && before.shelfId != id) mutable.value = before.copy(shelfId = id, result = null)
    }
    fun photo(file: File) {
        val before = mutable.value!!
        before.photo?.takeIf { it != file }?.delete()
        val verification = before.step in setOf(ScannerStep.IN_PROGRESS, ScannerStep.CONFIRMING)
        mutable.value = before.copy(photo = file, step = if (verification) ScannerStep.CONFIRMING else ScannerStep.PREVIEW,
            result = if (verification) before.result else null, error = "", manualEntry = if (verification) before.manualEntry else false)
    }
    private fun run(action: (ScannerState) -> ScannerState) {
        val before = mutable.value!!
        if (before.loading || uid == null) return
        val version = generation
        mutable.value = before.copy(loading = true, error = "")
        active = worker.submit {
            val next = try { action(before) } catch (error: Exception) {
                before.copy(pending = if (error is com.example.kairos.model.network.ApiFailure && error.status in setOf(400, 403, 404, 422)) null else before.pending,
                    pendingBatch = if (error is com.example.kairos.model.network.ApiFailure && error.status in setOf(400,422)) emptyList() else before.pendingBatch,
                    error = if (error is com.example.kairos.model.network.ApiFailure || error is IllegalArgumentException)
                    error.message.orEmpty() else "Não foi possível concluir. Tente novamente.")
            }
            main.post { if (generation == version) mutable.value = next.copy(loading = false) }
        }
    }
    fun retryCatalog() { val owner = uid ?: return; run { it.copy(catalog = repository.catalog(owner)) } }
    fun analyze() {
        val owner = uid ?: return
        run { before ->
            val shelf = before.shelfId ?: throw IllegalArgumentException("Selecione a gôndola autorizada.")
            val file = before.photo ?: throw IllegalArgumentException("Fotografe a gôndola primeiro.")
            val image = CapturedPhoto.jpeg(file)
            val context = repository.resultContext(owner)
            val result = repository.analyze(owner, shelf, image)
            before.copy(step = ScannerStep.RESULT, result = result, marketData = context, analyzedAt = java.time.Instant.now(), error = "")
        }
    }
    fun prepare(inventory: Long, product: Long, quantity: Int, damages: Int): ConfirmedRestocking {
        val before = mutable.value!!
        require(before.step == ScannerStep.RESULT && before.result != null && !before.loading)
        val request = ConfirmedRestocking(UUID.randomUUID().toString(), before.shelfId!!, inventory, product, quantity, damages)
        request.validate()
        require(before.catalog!!.products.any { it.id == product } && before.catalog.inventories.any { it.id == inventory })
        return request
    }
    fun confirm(request: ConfirmedRestocking) {
        val owner = uid ?: return
        run { before ->
            require(before.result != null && request.shelfId == before.result.shelfId)
            repository.confirm(owner, request)
            before.photo?.delete()
            before.copy(step = ScannerStep.COMPLETE, photo = null, pending = null, error = "")
        }
    }
    fun pending(request: ConfirmedRestocking) { mutable.value = mutable.value!!.copy(pending = request) }
    fun begin(request: ConfirmedRestocking) {
        request.validate()
        require(mutable.value!!.step == ScannerStep.RESULT && mutable.value!!.result?.shelfId == request.shelfId)
        mutable.value = mutable.value!!.copy(step = ScannerStep.IN_PROGRESS, draft = request, error = "")
    }
    fun startSelected(products: List<Long>) {
        val before = mutable.value!!
        require(before.step == ScannerStep.RESULT && !before.loading)
        require(products.isNotEmpty() && products.size <= 25 && products.distinct().size == products.size)
        require(products.all { id -> before.catalog!!.products.any { it.id == id } })
        mutable.value = before.copy(step = ScannerStep.IN_PROGRESS, selectedProductIds = products, error = "")
    }
    fun finalizeBatch(requests: List<ConfirmedRestocking>) {
        val owner = uid ?: return
        val before = mutable.value!!
        require(before.step in setOf(ScannerStep.IN_PROGRESS, ScannerStep.CONFIRMING) && !before.loading)
        require(requests.size in 1..25)
        require(requests.map { it.productId }.toSet() == before.selectedProductIds.toSet())
        require(requests.size == before.selectedProductIds.size)
        require(before.pendingBatch.isEmpty() || before.pendingBatch == requests)
        requests.forEach { request ->
            request.validate()
            require(request.shelfId == before.shelfId && before.catalog!!.inventories.any { it.id == request.inventoryId })
        }
        mutable.value = before.copy(pendingBatch = requests, error = "")
        run { ready ->
            repository.confirmBatch(owner, ready.pendingBatch)
            ready.photo?.delete()
            ready.copy(step = ScannerStep.COMPLETE, photo = null, pendingBatch = emptyList(), pending = null, error = "")
        }
    }
    fun reviewPhoto() {
        val before = mutable.value!!
        if (!before.loading) mutable.value = before.copy(step = if (before.photo == null) ScannerStep.START else ScannerStep.PREVIEW, error = "")
    }
    fun reset() {
        generation++; active?.cancel(true); mutable.value?.photo?.delete()
        mutable.value = ScannerState(); uid = null; recovered = null; recoveryOwner = null; recoveredBatch = emptyList()
    }
    override fun onCleared() { generation++; mutable.value?.photo?.delete(); worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
}
