package com.example.kairos.viewmodel

import android.graphics.BitmapFactory
import androidx.lifecycle.*
import android.os.Handler
import android.os.Looper
import com.example.kairos.model.scanner.*
import com.example.kairos.model.auth.AccountChangedException
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.Executors

enum class ScannerStep { START, PREVIEW, RESULT, IN_PROGRESS, CONFIRMING, COMPLETE }
data class ScannerState(val step: ScannerStep = ScannerStep.START, val loading: Boolean = false,
                        val catalog: StockCatalog? = null, val shelfId: Long? = null,
                        val result: ShelfResult? = null, val error: String = "", val photo: File? = null,
                        val pending: ConfirmedRestocking? = null, val draft: ConfirmedRestocking? = null,
                        val manualEntry: Boolean = false)

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

    fun recover(owner: String, request: ConfirmedRestocking?) { recoveryOwner = owner; recovered = request }

    fun account(owner: String, initialShelf: Long? = null, initialProduct: Long? = null) {
        if (uid == owner) return
        val recovery = recovered.takeIf { recoveryOwner == owner }
        reset(); uid = owner; recovered = recovery
        run { before ->
            val catalog = repository.catalog(owner)
            val pending = recovered
            if (pending == null && initialShelf != null) {
                require(catalog.shelves.any { it.id == initialShelf }) { "A gôndola não está mais disponível para sua conta." }
                require(initialProduct == null || catalog.products.any { it.id == initialProduct }) { "O produto não está mais no catálogo do mercado." }
                before.copy(catalog = catalog, step = ScannerStep.RESULT, shelfId = initialShelf,
                    result = ShelfResult(initialShelf, emptyList(), emptyList()), manualEntry = true)
            } else if (pending == null) before.copy(catalog = catalog) else before.copy(catalog = catalog,
                step = ScannerStep.RESULT, shelfId = pending.shelfId, pending = pending,
                result = ShelfResult(pending.shelfId, emptyList(), emptyList()))
        }
    }
    fun error(message: String) { mutable.value = mutable.value!!.copy(error = message) }
    fun shelf(id: Long?) { if (!mutable.value!!.loading) mutable.value = mutable.value!!.copy(shelfId = id, result = null) }
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
            require(file.length() in 1..(24L * 1024 * 1024)) { "Foto inválida ou maior que 24 MiB. Repita a captura." }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000) { "Resolução inválida." }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IllegalArgumentException("Não foi possível ler a foto.")
            val orientation = android.media.ExifInterface(file.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
            val rotation = when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val bitmap = if (rotation == 0f) decoded else android.graphics.Bitmap.createBitmap(decoded, 0, 0,
                decoded.width, decoded.height, android.graphics.Matrix().apply { postRotate(rotation) }, true)
                .also { if (it !== decoded) decoded.recycle() }
            val image = try { ByteArrayOutputStream().use { output -> bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output); output.toByteArray() } }
                finally { bitmap.recycle() }
            require(image.size <= 4 * 1024 * 1024) { "A imagem excede 4 MiB. Repita a captura." }
            val result = repository.analyze(owner, shelf, image)
            before.copy(step = ScannerStep.RESULT, result = result, error = "")
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
    fun reset() {
        generation++; active?.cancel(true); mutable.value?.photo?.delete()
        mutable.value = ScannerState(); uid = null; recovered = null; recoveryOwner = null
    }
    override fun onCleared() { generation++; mutable.value?.photo?.delete(); worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
}
