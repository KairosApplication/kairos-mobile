package com.example.kairos.model.home

import com.example.kairos.model.network.*
import org.json.JSONObject
import org.json.JSONArray
import java.time.OffsetDateTime
import java.time.ZoneId

class HttpStockerHomeRepository : StockerHomeRepository {
    override fun resolveAlert(userId:String,id:String) {
        require(id.toLongOrNull()?.let { it>0 }==true)
        ApiDependencies.spring().request(userId,"/api/v1/alerts/update/$id","PATCH",JSONObject().put("status","RESOLVED"))
    }
    override fun load(userId: String): StockerHomeData {
        val api = ApiDependencies.spring()
        val identity = HttpBusinessSession(api).session(userId)
        if (!identity.canRestock) throw ApiFailure(403)
        val week = JSONObject(api.request(userId, "/api/v1/restockings/week?limit=500"))
        val companyId = week.getLong("companyId")
        val employeeId = week.getLong("employeeId")
        val records = week.getJSONArray("items").objects().map {
            check(it.getLong("company_id") == companyId && it.getLong("employee_id") == employeeId)
            RestockingRecord(it.getLong("restocking_id").toString(), it.getString("product_name"), "",
                it.getLong("shelf_id").toString(), OffsetDateTime.parse(it.getString("restocking_date")).toInstant(), RestockingStatus.COMPLETED,
                analysisId = "restocking:${it.getLong("restocking_id")}",
                restockedQuantity = it.getInt("restocked_quantity"), damagedQuantity = it.getInt("qnt_avarias"), productId = it.getLong("product_id"))
        }
        val alerts = api.read(userId, "/api/v1/alerts/list") { StockerListReader.alerts(it, employeeId) }
            .map { row ->
                JSONObject().put("id",row.getLong("id")).put("shelf_id",row.opt("shelfId"))
                    .put("product_id",row.opt("productId")).put("description",row.getString("description"))
                    .put("stockout_date",row.opt("stockoutDate"))
            }
        val shelfIds = records.mapNotNull { it.shelfCode.toLongOrNull() }.toSet() + alerts.mapNotNull { it.optionalLong("shelf_id") }
        val shelfRows = if (shelfIds.isEmpty()) emptyList() else api.read(userId,"/api/v1/shelves/list") { input ->
            StockerListReader.read(input,setOf("id","aisleId","aislePosition")) { it.getLong("id") in shelfIds }
        }
        val aisleIds = shelfRows.map { it.getLong("aisleId") }.toSet()
        val aisles = if (aisleIds.isEmpty()) emptyMap() else api.read(userId,"/api/v1/aisles/list") { input ->
            StockerListReader.read(input,setOf("id","sectorName")) { it.getLong("id") in aisleIds }
        }.associateBy { it.getLong("id") }
        val shelves = shelfRows.map { shelf ->
            JSONObject().put("id",shelf.getLong("id")).put("aisle",shelf.getInt("aislePosition"))
                .put("sector",aisles[shelf.getLong("aisleId")]?.optString("sectorName") ?: "Não informado")
        }.associateBy { it.getLong("id").toString() }
        val alertRecords = alerts.map { row ->
            val shelf = row.optionalLong("shelf_id")?.toString() ?: "Não informada"
            RestockingRecord(row.getLong("id").toString(), row.optString("product_name", row.getString("description")), "", shelf,
                if (!row.isNull("reported_at")) java.time.Instant.parse(row.getString("reported_at")) else if (!row.isNull("stockout_date")) StockDates.alertDate(row.getString("stockout_date"))
                    .atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant() else java.time.Instant.EPOCH,
                if (!row.isNull("report_id")) RestockingStatus.OBSERVED else if (!row.isNull("shelf_quantity") && row.getInt("shelf_quantity") > 0) RestockingStatus.LOW_STOCK else RestockingStatus.REQUIRED,
                analysisId = if(row.isNull("report_id")) "pending-shelf:$shelf" else null, productId = row.optionalLong("product_id"),
                customerReport = if(row.isNull("report_id")) null else row.toString())
        }
        val events = alerts.zip(alertRecords).map { (row, record) ->
            HomeEvent(record.id, if (record.status == RestockingStatus.LOW_STOCK) HomeEventType.LOW_STOCK else HomeEventType.RESTOCK_REQUIRED,
                record.shelfCode, record.occurredAt, analysisId = record.analysisId, title = row.getString("description"))
        }
        fun details(id: String, shelf: String, products: List<RestockingRecord>): ShelfAnalysis {
            val location = shelves[shelf]
            return ShelfAnalysis(shelf, location?.getInt("aisle")?.toString() ?: "—", location?.getString("sector") ?: "Não informado",
                products.size, products, id, fromPhoto = false)
        }
        val analyses = alertRecords.filter { it.customerReport==null }.groupBy { it.shelfCode }.map { (shelf, products) -> details("pending-shelf:$shelf", shelf, products) } +
            records.map { details(it.analysisId!!, it.shelfCode, listOf(it)) }
        val history = records.map { HomeEvent(it.id, HomeEventType.RESTOCK_COMPLETED, it.shelfCode, it.occurredAt, analysisId = it.analysisId) }
        return StockerHomeData(alerts.size, week.getInt("total"), events.take(3), events, history,
            isDemo = false, restockingHistory = records, restockingAlerts = alertRecords,
            profileWorkSummary = ProfileWorkSummary(week.getInt("total"), records.sumOf { it.damagedQuantity ?: 0 },
                if (identity.role == "MANAGER") "Gerente" else "Repositor", identity.companyName),
            shelfAnalysis = analyses.firstOrNull(), analyses = analyses)
    }
}
