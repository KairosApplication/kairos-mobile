package com.example.kairos.model.cliente

import com.example.kairos.model.network.*
import com.example.kairos.model.auth.AccountChangedException
import org.json.JSONObject

/** Uses the published catalog and Firebase reader; favorites are device preferences. */
class PublishedCustomerRepository : CustomerRepository {
    private val damageReports=CustomerDamageReports()
    private val unavailable=setOf("receipts","reports","recommendations","locations")
    private fun owner(uid:String) { if(ApiDependencies.currentUid()!=uid) throw AccountChangedException() }
    override fun home(uid:String):CustomerData {
        owner(uid)
        val photo=FirebaseReceiptAi.configured()
        var notice=""
        val catalog=try {
            ApiDependencies.spring().read(uid,"/api/v1/products/list") { PublishedCatalogReader.read(it) }
        } catch(error:Exception) {
            if(!photo || error is AccountChangedException || error is ApiFailure && error.status==401) throw error
            notice="Não foi possível carregar os produtos. A leitura de fotos de notas continua disponível. Seus favoritos permanecem salvos neste aparelho."
            PublishedCatalog(emptyList(),false)
        }
        owner(uid)
        val favorites=CustomerFavorites.forUser(uid).ids()
        var reports=emptyList<JSONObject>()
        var features=unavailable
        var companyId:Long?=null
        var companyName=""
        try {
            val capabilities=damageReports.capabilities(uid)
            reports=damageReports.list(uid)
            companyId=capabilities.getLong("companyId")
            companyName=capabilities.getString("companyName")
            features=unavailable-"reports"
        } catch(error:Exception) {
            if(error is AccountChangedException || error is ApiFailure && error.status==401) throw error
            if(error !is ApiFailure || error.status !in setOf(403,404)) {
                notice=listOf(notice,"Não foi possível consultar os relatos de avaria agora. Tente atualizar em instantes.")
                    .filter { it.isNotBlank() }.joinToString(" ")
            }
        }
        owner(uid)
        return CustomerData(companyId,companyName,catalog.products.map { it.copy(favorite=it.id in favorites) },
            emptyList(),emptyList(),emptySet(),emptyList(),reports,features,catalog.truncated,photo,notice)
    }
    override fun receiptContext(uid:String):CustomerData {
        owner(uid)
        return CustomerData(null,"",emptyList(),emptyList(),emptyList(),emptySet(),emptyList(),emptyList(),
            unavailable,receiptPhotoAvailable=FirebaseReceiptAi.configured())
    }
    override fun favorite(uid:String,productId:Long,enabled:Boolean) {
        owner(uid)
        CustomerFavorites.forUser(uid).set(productId,enabled)
        owner(uid)
    }
    override fun preview(uid:String,qr:String):JSONObject = unavailable(uid)
    override fun confirmReceipt(uid:String,qr:String,items:List<ReceiptItem>,command:String):JSONObject = unavailable(uid)
    override fun reportContext(uid:String)=damageReports.context(uid)
    override fun report(uid:String,command:String,productId:Long,shelfId:Long?,type:String,description:String,image:ByteArray?):JSONObject =
        damageReports.report(uid,command,productId,shelfId,type,description,image)
    private fun unavailable(uid:String):Nothing {
        owner(uid)
        throw IllegalArgumentException("Esta operação não está disponível para clientes na API atual. Nenhum dado foi enviado ou salvo.")
    }
    override fun interpret(uid:String,image:ByteArray):JSONObject {
        owner(uid)
        val result=FirebaseReceiptAi.interpret(image)
        owner(uid)
        return result
    }
}
