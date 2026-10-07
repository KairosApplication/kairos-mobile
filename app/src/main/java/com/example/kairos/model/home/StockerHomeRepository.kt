package com.example.kairos.model.home

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class HomeEventType { RESTOCK_REQUIRED, LOW_STOCK, ANALYSIS_COMPLETED }

data class HomeEvent(
    val id: String,
    val type: HomeEventType,
    val shelfCode: String,
    val occurredAt: Instant,
    val productCount: Int = 0
)

data class StockerHomeData(
    val pendingThisWeek: Int,
    val completedThisWeek: Int,
    val priorities: List<HomeEvent>,
    val alerts: List<HomeEvent>,
    val history: List<HomeEvent>,
    val isDemo: Boolean
)

/** Blocking reads run outside the UI thread. The API adapter will implement this contract. */
fun interface StockerHomeRepository {
    fun load(userId: String): StockerHomeData
}

/** Local examples only: no API calls, remote writes or inferred employee identity. */
class DemoStockerHomeRepository(private val clock: Clock = Clock.systemDefaultZone()) : StockerHomeRepository {
    override fun load(userId: String): StockerHomeData {
        require(userId.isNotBlank())
        val now = clock.instant()
        val alerts = listOf(
            HomeEvent("demo-restock-a12", HomeEventType.RESTOCK_REQUIRED, "A12", now.minus(47, ChronoUnit.MINUTES), 2),
            HomeEvent("demo-low-a12", HomeEventType.LOW_STOCK, "A12", now.minus(32, ChronoUnit.MINUTES), 1),
            HomeEvent("demo-restock-b04", HomeEventType.RESTOCK_REQUIRED, "B04", now.minus(2, ChronoUnit.HOURS), 1)
        )
        val history = listOf(
            HomeEvent("demo-analysis-c02", HomeEventType.ANALYSIS_COMPLETED, "C02", now.minus(1, ChronoUnit.DAYS)),
            HomeEvent("demo-analysis-b04", HomeEventType.ANALYSIS_COMPLETED, "B04", now.minus(2, ChronoUnit.DAYS))
        )
        return StockerHomeData(3, 2, alerts.take(2) + history.first(), alerts, history, isDemo = true)
    }
}
