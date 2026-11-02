package com.example.kairos.model.home

import com.example.kairos.model.network.*
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class HttpStockerHomeRepository : StockerHomeRepository {
    override fun load(userId: String): StockerHomeData {
        val api = ApiDependencies.spring()
        val identity = HttpBusinessSession(api).session(userId)
        if (!identity.canRestock) throw ApiFailure(403)
        val week = JSONObject(api.request(userId, "/api/v1/restockings/week?limit=500"))
        check(week.getLong("companyId") == identity.companyId && week.getLong("employeeId") == identity.employeeId)
        val items = week.getJSONArray("items").objects()
        val summary = JSONObject(api.request(userId, "/api/v1/mobile/work-summary"))
        val records = items.map {
            check(it.getLong("company_id") == identity.companyId && it.getLong("employee_id") == identity.employeeId)
            RestockingRecord(it.getLong("restocking_id").toString(), it.getString("product_name"), "",
                it.getLong("shelf_id").toString(), OffsetDateTime.parse(it.getString("restocking_date")).toInstant(), RestockingStatus.COMPLETED,
                restockedQuantity = it.getInt("restocked_quantity"), damagedQuantity = it.getInt("qnt_avarias"))
        }
        val alerts = JSONArray(api.request(userId, "/api/v1/mobile/alerts")).objects()
        val events = alerts.map {
            HomeEvent(it.getLong("id").toString(), HomeEventType.RESTOCK_REQUIRED, it.getLong("shelf_id").toString(),
                if (it.isNull("stockout_date")) java.time.Instant.EPOCH else
                    StockDates.alertDate(it.getString("stockout_date")).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(),
                title = it.getString("description"))
        }
        val history = records.map { HomeEvent(it.id, HomeEventType.RESTOCK_COMPLETED, it.shelfCode, it.occurredAt) }
        return StockerHomeData(alerts.size, week.getInt("total"), events.take(3), events, history,
            isDemo = false, restockingHistory = records,
            restockingAlerts = alerts.map { row ->
                RestockingRecord(row.getLong("id").toString(), row.optString("product_name", row.getString("description")), "",
                    row.getLong("shelf_id").toString(),
                    if (!row.isNull("stockout_date")) StockDates.alertDate(row.getString("stockout_date"))
                        .atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant() else java.time.Instant.EPOCH,
                    RestockingStatus.REQUIRED)
            }, profileWorkSummary = ProfileWorkSummary(summary.getInt("restocks"), summary.getInt("damages"),
                if (identity.role == "MANAGER") "Gerente" else "Repositor", identity.companyName))
    }
}
