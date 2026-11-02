package com.example.kairos.model.scanner

import com.example.kairos.model.network.*
import org.json.JSONObject
import android.util.Base64

data class CatalogItem(val id: Long, val label: String, val aisle: String? = null, val sector: String? = null) { override fun toString() = label }
data class StockBalance(val inventoryId: Long, val productId: Long, val quantity: Int)
data class StockCatalog(val shelves: List<CatalogItem>, val inventories: List<CatalogItem>, val products: List<CatalogItem>,
                        val balances: List<StockBalance> = emptyList())
data class ShelfObservation(val name: String, val brand: String, val visibleQuantity: Int?, val confidence: String,
                            val unrecognized: Boolean, val candidates: List<Long>)
data class ShelfResult(val shelfId: Long, val observations: List<ShelfObservation>, val warnings: List<String>)
data class ConfirmedRestocking(val commandId: String, val shelfId: Long, val inventoryId: Long,
                              val productId: Long, val quantity: Int, val damages: Int) {
    fun validate() { require(shelfId > 0 && inventoryId > 0 && productId > 0 && quantity > 0 && damages in 0..quantity) }
}

interface ScannerRepository {
    fun catalog(uid: String): StockCatalog
    fun analyze(uid: String, shelfId: Long, image: ByteArray): ShelfResult
    fun confirm(uid: String, request: ConfirmedRestocking)
    fun resultContext(uid: String): com.example.kairos.model.home.StockerHomeData? = null
    fun confirmBatch(uid: String, requests: List<ConfirmedRestocking>) {
        require(requests.size == 1)
        confirm(uid, requests.single())
    }
}

class HttpScannerRepository : ScannerRepository {
    override fun catalog(uid: String): StockCatalog {
        val data = JSONObject(ApiDependencies.spring().request(uid, "/api/v1/mobile/catalog"))
        return StockCatalog(data.getJSONArray("shelves").objects().map {
            CatalogItem(it.getLong("id"), "Gôndola ${it.getLong("id")} · corredor ${it.getInt("aisle")} · ${it.getString("sector")}",
                it.getInt("aisle").toString(), it.getString("sector"))
        }, data.getJSONArray("inventories").objects().map { CatalogItem(it.getLong("id"), "Estoque ${it.getLong("id")} · ${it.optString("sector", "")}") },
            data.getJSONArray("products").objects().map { CatalogItem(it.getLong("id"), "${it.getString("name")} · ${it.optString("brand", "")}") },
            data.optJSONArray("balances")?.objects()?.map { StockBalance(it.getLong("inventory_id"),it.getLong("product_id"),it.getInt("product_quantity")) }.orEmpty())
    }
    override fun analyze(uid: String, shelfId: Long, image: ByteArray): ShelfResult {
        require(image.isNotEmpty() && image.size <= 4 * 1024 * 1024 && shelfId > 0)
        val request = JSONObject().put("shelfId", shelfId).put("mimeType", "image/jpeg")
            .put("imageBase64", Base64.encodeToString(image, Base64.NO_WRAP))
        val data = JSONObject(ApiDependencies.chat().request(uid, "/api/v1/me/shelf-analyses", "POST", request))
        check(data.getLong("shelfId") == shelfId && !data.getBoolean("stockChanged"))
        return ShelfResult(shelfId, data.getJSONArray("candidates").objects().map { row ->
            val ids = row.getJSONArray("candidateProductIds")
            ShelfObservation(if (row.isNull("name")) "Produto não identificado" else row.getString("name"),
                if (row.isNull("brand")) "" else row.getString("brand"), if (row.isNull("visibleQuantityEstimate")) null else row.getInt("visibleQuantityEstimate"),
                row.getString("confidence"), row.getBoolean("unrecognized"), (0 until ids.length()).map { ids.getLong(it) })
        }, data.getJSONArray("warnings").let { rows -> (0 until rows.length()).map { rows.getString(it) } })
    }
    override fun confirm(uid: String, request: ConfirmedRestocking) {
        request.validate()
        ApiDependencies.spring().request(uid, "/api/v1/mobile/restockings/registration", "POST", JSONObject()
            .put("commandId", request.commandId).put("shelfId", request.shelfId).put("inventoryId", request.inventoryId)
            .put("productId", request.productId).put("restockedQuantity", request.quantity).put("damagedQuantity", request.damages))
    }
    override fun resultContext(uid: String) = com.example.kairos.model.home.HttpStockerHomeRepository().load(uid)
    override fun confirmBatch(uid: String, requests: List<ConfirmedRestocking>) {
        require(requests.isNotEmpty() && requests.size <= 25)
        val items = org.json.JSONArray()
        requests.forEach { request ->
            request.validate()
            items.put(JSONObject().put("commandId", request.commandId).put("inventoryId", request.inventoryId)
                .put("shelfId", request.shelfId).put("productId", request.productId)
                .put("restockedQuantity", request.quantity).put("damagedQuantity", request.damages))
        }
        ApiDependencies.spring().request(uid, "/api/v1/mobile/restockings/batch", "POST", JSONObject().put("items", items))
    }
}
