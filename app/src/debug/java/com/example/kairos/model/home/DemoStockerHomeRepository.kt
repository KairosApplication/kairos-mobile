package com.example.kairos.model.home

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Local examples only: no API calls, remote writes or inferred employee identity. */
class DemoStockerHomeRepository(private val clock: Clock = Clock.systemDefaultZone()) : StockerHomeRepository {
    override fun load(userId: String): StockerHomeData {
        require(userId.isNotBlank())
        val now = clock.instant()
        val alerts = listOf(
            HomeEvent("demo-restock-a12", HomeEventType.RESTOCK_REQUIRED, "A12", now.minus(47, ChronoUnit.MINUTES), 1, "pending-A12"),
            HomeEvent("demo-low-a12", HomeEventType.LOW_STOCK, "A12", now.minus(32, ChronoUnit.MINUTES), 1, "pending-A12"),
            HomeEvent("demo-restock-b02", HomeEventType.RESTOCK_REQUIRED, "B02", now.minus(2, ChronoUnit.HOURS), 1, "pending-B02")
        )
        val history = listOf(
            HomeEvent("demo-analysis-c03", HomeEventType.ANALYSIS_COMPLETED, "C03", now.minus(1, ChronoUnit.DAYS), analysisId = "yesterday-water"),
            HomeEvent("demo-analysis-b11", HomeEventType.ANALYSIS_COMPLETED, "B11", now.minus(1, ChronoUnit.DAYS), analysisId = "yesterday-biscuits")
        )
        val today = now.atZone(clock.zone).toLocalDate()
        fun minutesAgo(minutes: Long) = now.minus(minutes, ChronoUnit.MINUTES)
            .coerceAtLeast(today.atStartOfDay(clock.zone).toInstant())
        fun record(id: String, name: String, image: String, shelf: String, time: Instant,
                   status: RestockingStatus = RestockingStatus.COMPLETED) =
            RestockingRecord(id, name, image, shelf, time, status,
                if (status == RestockingStatus.COMPLETED) id else "pending-$shelf")
        val restockingAlerts = listOf(
            record("alert-cola", "Coca Cola - 2 Litros", "cola", "A12", minutesAgo(2), RestockingStatus.REQUIRED),
            record("alert-biscuits", "Biscoito Animados", "biscuits", "B02", minutesAgo(10), RestockingStatus.REQUIRED),
            record("alert-water", "Água Mineral", "water", "A12", minutesAgo(90), RestockingStatus.LOW_STOCK)
        )
        val restockingHistory = listOf(
            record("done-cola", "Coca Cola - 2 Litros", "cola", "A12", minutesAgo(47)),
            record("done-biscuits", "Biscoito Animados", "biscuits", "B11", minutesAgo(58)),
            record("done-water", "Água Mineral", "water", "C03", minutesAgo(180)),
            record("yesterday-cola", "Coca Cola - 2 Litros", "cola", "A15",
                today.minusDays(1).atTime(17, 52).atZone(clock.zone).toInstant()),
            record("yesterday-biscuits", "Biscoito Animados", "biscuits", "B11",
                today.minusDays(1).atTime(16, 1).atZone(clock.zone).toInstant()),
            record("yesterday-water", "Água Mineral", "water", "C03",
                today.minusDays(1).atTime(10, 25).atZone(clock.zone).toInstant())
        )
        val analyses = restockingAlerts.groupBy { it.shelfCode }.map { (shelf, products) ->
            ShelfAnalysis(shelf, if (shelf.startsWith("A")) "3" else "4", shelf.take(1),
                18, products, "pending-$shelf")
        } + restockingHistory.map { record ->
            ShelfAnalysis(record.shelfCode, if (record.shelfCode.startsWith("A")) "3" else "4",
                record.shelfCode.take(1), 18, listOf(record), record.analysisId!!)
        }
        return StockerHomeData(3, 2, alerts.take(2) + history.first(), alerts, history,
            isDemo = true, restockingAlerts = restockingAlerts, restockingHistory = restockingHistory,
            profileWorkSummary = ProfileWorkSummary(9, 8, "Repositor", "Aurora"),
            shelfAnalysis = analyses.first { it.id == "pending-A12" }, analyses = analyses)
    }
}
