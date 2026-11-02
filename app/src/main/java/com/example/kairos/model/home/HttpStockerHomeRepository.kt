package com.example.kairos.model.home

import com.example.kairos.model.network.*
import org.json.JSONObject
import org.json.JSONArray
import java.time.OffsetDateTime
import java.time.ZoneId

class HttpStockerHomeRepository : StockerHomeRepository {
    override fun load(userId: String): StockerHomeData {
        val api = ApiDependencies.spring()
        val identity = HttpBusinessSession(api).session(userId)
        if (!identity.canRestock) throw ApiFailure(403)
        val week = JSONObject(api.request(userId, "/api/v1/restockings/week?limit=500"))
        check(week.getLong("companyId") == identity.companyId && week.getLong("employeeId") == identity.employeeId)
        val summary = JSONObject(api.request(userId, "/api/v1/mobile/work-summary"))
        val shelves = JSONObject(api.request(userId, "/api/v1/mobile/catalog")).getJSONArray("shelves").objects()
            .associateBy { it.getLong("id").toString() }
        val records = week.getJSONArray("items").objects().map {
            check(it.getLong("company_id") == identity.companyId && it.getLong("employee_id") == identity.employeeId)
            RestockingRecord(it.getLong("restocking_id").toString(), it.getString("product_name"), "",
                it.getLong("shelf_id").toString(), OffsetDateTime.parse(it.getString("restocking_date")).toInstant(), RestockingStatus.COMPLETED,
                analysisId = "restocking:${it.getLong("restocking_id")}",
                restockedQuantity = it.getInt("restocked_quantity"), damagedQuantity = it.getInt("qnt_avarias"), productId = it.getLong("product_id"))
        }
        val alerts = JSONArray(api.request(userId, "/api/v1/mobile/alerts")).objects()
        val alertRecords = alerts.map { row ->
            val shelf = row.getLong("shelf_id").toString()
            RestockingRecord(row.getLong("id").toString(), row.optString("product_name", row.getString("description")), "", shelf,
                if (!row.isNull("stockout_date")) StockDates.alertDate(row.getString("stockout_date"))
                    .atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant() else java.time.Instant.EPOCH,
                if (!row.isNull("shelf_quantity") && row.getInt("shelf_quantity") > 0) RestockingStatus.LOW_STOCK else RestockingStatus.REQUIRED,
                analysisId = "pending-shelf:$shelf", productId = row.optionalLong("product_id"))
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
        val analyses = alertRecords.groupBy { it.shelfCode }.map { (shelf, products) -> details("pending-shelf:$shelf", shelf, products) } +
            records.map { details(it.analysisId!!, it.shelfCode, listOf(it)) }
        val history = records.map { HomeEvent(it.id, HomeEventType.RESTOCK_COMPLETED, it.shelfCode, it.occurredAt, analysisId = it.analysisId) }
        return StockerHomeData(alerts.size, week.getInt("total"), events.take(3), events, history,
            isDemo = false, restockingHistory = records, restockingAlerts = alertRecords,
            profileWorkSummary = ProfileWorkSummary(summary.getInt("restocks"), summary.getInt("damages"),
                if (identity.role == "MANAGER") "Gerente" else "Repositor", identity.companyName),
            shelfAnalysis = analyses.firstOrNull(), analyses = analyses)
    }
}
