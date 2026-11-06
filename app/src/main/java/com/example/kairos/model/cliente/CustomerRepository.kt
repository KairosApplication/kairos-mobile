package com.example.kairos.model.cliente

import com.example.kairos.model.network.*
import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal

data class CustomerProduct(val id: Long, val name: String, val brand: String, val price: BigDecimal?, val favorite: Boolean) {
    override fun toString() = name
}
data class CustomerLocation(val productId: Long, val shelfId: Long, val aisle: String, val sector: String) {
    override fun toString() = "Gôndola $shelfId · corredor $aisle · $sector"
}
data class CustomerOffer(val productId: Long, val kind: String, val description: String, val discount: BigDecimal)
data class CustomerData(val companyId: Long?, val companyName: String, val products: List<CustomerProduct>,
    val locations: List<CustomerLocation>, val offers: List<CustomerOffer>, val recommendedIds: Set<Long>,
    val receipts: List<JSONObject>, val reports: List<JSONObject>, val unavailableFeatures: Set<String> = emptySet(),
    val catalogTruncated: Boolean = false, val receiptPhotoAvailable: Boolean = false,
    val catalogNotice: String = "", val reportPhotoAvailable: Boolean = false)
data class ReceiptItem(val productId: Long, val quantity: Int, val amount: BigDecimal) {
    fun json(): JSONObject {
        require(productId > 0 && quantity in 1..100000 && amount.signum() >= 0 && amount.scale() <= 2 && amount <= BigDecimal("1000000"))
        return JSONObject().put("productId",productId).put("quantity",quantity).put("amount",amount)
    }
}
interface CustomerRepository {
    fun home(uid: String): CustomerData
    fun receiptContext(uid: String): CustomerData = home(uid)
    fun reportContext(uid: String): CustomerData = home(uid)
    fun favorite(uid: String, productId: Long, enabled: Boolean)
    fun preview(uid: String, qr: String): JSONObject
    fun confirmReceipt(uid: String, qr: String, items: List<ReceiptItem>, command: String): JSONObject
    fun report(uid: String, command: String, productId: Long, shelfId: Long?, type: String, description: String,
               image: ByteArray? = null): JSONObject
    fun interpret(uid: String, image: ByteArray): JSONObject
}
class HttpCustomerRepository : CustomerRepository {
    private fun call(uid: String, path: String, method: String = "GET", body: JSONObject? = null) =
        ApiDependencies.spring().request(uid,"/api/v1/mobile/customer/$path",method,body)
    override fun home(uid: String): CustomerData {
        val data=JSONObject(call(uid,"home"))
        return CustomerData(data.getLong("companyId"),data.getString("companyName"),
            data.getJSONArray("products").objects().map { CustomerProduct(it.getLong("id"),it.getString("name"),
                it.optString("brand",""),if(it.isNull("price")) null else BigDecimal(it.get("price").toString()),it.getBoolean("favorite")) },
            data.getJSONArray("locations").objects().map { CustomerLocation(it.getLong("product_id"),it.getLong("shelf_id"),
                it.get("aisle").toString(),it.getString("sector")) },
            data.getJSONArray("offers").objects().map { CustomerOffer(it.getLong("product_id"),it.getString("kind"),
                it.getString("description"),BigDecimal(it.get("discount_percentage").toString())) },
            data.getJSONArray("recommendations").objects().map { it.getLong("productId") }.toSet(),
            data.getJSONArray("receipts").objects(),data.getJSONArray("reports").objects())
    }
    override fun favorite(uid: String, productId: Long, enabled: Boolean) {
        call(uid,"favorites/$productId","PUT",JSONObject().put("enabled",enabled))
    }
    override fun preview(uid: String, qr: String) = JSONObject(call(uid,"receipts/preview","POST",JSONObject().put("qrCode",qr)))
    override fun confirmReceipt(uid: String, qr: String, items: List<ReceiptItem>, command: String): JSONObject {
        require(items.isNotEmpty() && items.size<=100 && items.map { it.productId }.distinct().size==items.size)
        return JSONObject(call(uid,"receipts","POST",JSONObject().put("commandId",command).put("qrCode",qr).put("items",JSONArray().apply { items.forEach { put(it.json()) } })))
    }
    override fun report(uid: String, command: String, productId: Long, shelfId: Long?, type: String, description: String,
                        image: ByteArray?):JSONObject {
        require(image==null) { "Este adaptador não oferece envio de fotos de avaria." }
        return JSONObject(call(uid,"damage-reports","POST",JSONObject().put("commandId",command).put("productId",productId)
            .put("shelfId",shelfId ?: JSONObject.NULL).put("problemType",type).put("description",description)))
    }
    override fun interpret(uid: String, image: ByteArray): JSONObject {
        require(image.isNotEmpty() && image.size<=3*1024*1024)
        return JSONObject(call(uid,"receipts/interpret","POST",JSONObject().put("imageBase64",
            android.util.Base64.encodeToString(image,android.util.Base64.NO_WRAP))))
    }
}
