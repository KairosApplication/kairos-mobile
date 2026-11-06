package com.example.kairos.model.cliente

import com.example.kairos.model.network.ApiDependencies
import com.example.kairos.model.auth.AccountChangedException
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.Base64

/** Dedicated customer contract: neither a replenishment command nor an employee impersonation. */
class CustomerDamageReports(
    private val send:(String,String,String,JSONObject?)->String = { uid,path,method,body ->
        ApiDependencies.spring().request(uid,path,method,body)
    },
    private val owner:(String)->Unit = { if(ApiDependencies.currentUid()!=it) throw AccountChangedException() }
) {
    private val path="/api/v1/customer/damage-reports"
    private fun call(uid:String,suffix:String="",method:String="GET",body:JSONObject?=null):String {
        owner(uid)
        val response=send(uid,path+suffix,method,body)
        owner(uid)
        return response
    }
    fun capabilities(uid:String):JSONObject = JSONObject(call(uid,"/capabilities")).also {
        require(it.getInt("version")==1 && it.getLong("companyId")>0) { "Contrato de relatos incompatível." }
    }
    fun list(uid:String):List<JSONObject> = JSONArray(call(uid)).let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }
    }
    fun context(uid:String):CustomerData {
        val value=JSONObject(call(uid,"/options"))
        val products=value.getJSONArray("products").let { array -> (0 until array.length()).map {
            val row=array.getJSONObject(it)
            CustomerProduct(row.getLong("id"),row.getString("name"),row.optString("brand",""),null,false)
        } }
        val ids=products.map { it.id }.toSet()
        val locations=value.getJSONArray("locations").let { array -> (0 until array.length()).mapNotNull {
            val row=array.getJSONObject(it)
            if(row.getLong("product_id") !in ids) null else CustomerLocation(row.getLong("product_id"),
                row.getLong("shelf_id"),row.get("aisle").toString(),row.getString("sector"))
        } }
        return CustomerData(value.getLong("companyId"),value.getString("companyName"),products,locations,
            emptyList(),emptySet(),emptyList(),emptyList(),setOf("receipts","recommendations"),
            catalogTruncated=value.optBoolean("catalogTruncated"),
            catalogNotice=if(value.optBoolean("locationsTruncated")) "A lista de localizações está limitada. Você pode enviar sem informar a localização." else "",
            reportPhotoAvailable=value.optBoolean("photoAvailable"))
    }
    fun report(uid:String,command:String,productId:Long,shelfId:Long?,type:String,description:String,image:ByteArray?):JSONObject {
        require(UUID.fromString(command).toString()==command.lowercase()) { "Identificador de envio inválido." }
        require(productId>0 && (shelfId==null || shelfId>0)) { "Selecione um produto e uma localização válida." }
        require(type in setOf("DAMAGED","TAMPERED","EXPIRED","OTHER") && description.trim().length<=500) {
            "Confira o tipo de problema e a descrição de até 500 caracteres."
        }
        val body=JSONObject().put("commandId",command).put("productId",productId)
            .put("shelfId",shelfId ?: JSONObject.NULL).put("problemType",type).put("description",description.trim())
        image?.let {
            require(it.size in 2..3*1024*1024 && it[0]==0xff.toByte() && it[1]==0xd8.toByte()) { "Foto JPEG inválida ou maior que 3 MB." }
            body.put("imageBase64",Base64.getEncoder().encodeToString(it))
        }
        return JSONObject(call(uid,method="POST",body=body)).also {
            UUID.fromString(it.getString("protocol"))
            require(it.getLong("alertId")>0 && it.getString("status") in setOf("OPEN","RESOLVED","CANCELLED")) {
                "O servidor não confirmou o encaminhamento da avaria."
            }
        }
    }
}
