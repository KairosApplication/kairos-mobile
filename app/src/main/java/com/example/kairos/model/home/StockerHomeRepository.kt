package com.example.kairos.model.home

import java.time.Instant

enum class HomeEventType { RESTOCK_REQUIRED, LOW_STOCK, ANALYSIS_COMPLETED, RESTOCK_COMPLETED }

data class ProfileWorkSummary(val restocksThisWeek: Int, val damagesThisWeek: Int, val role: String, val market: String)

data class ShelfAnalysis(val shelfCode: String, val aisle: String, val sector: String,
                         val analyzedProducts: Int, val products: List<RestockingRecord>,
                         val id: String = shelfCode,
                         val fromPhoto: Boolean = true,
                         val observations: List<String> = emptyList())

data class HomeEvent(
    val id: String,
    val type: HomeEventType,
    val shelfCode: String,
    val occurredAt: Instant,
    val productCount: Int = 0,
    val analysisId: String? = null,
    val title: String? = null
)

data class StockerHomeData(
    val pendingThisWeek: Int,
    val completedThisWeek: Int,
    val priorities: List<HomeEvent>,
    val alerts: List<HomeEvent>,
    val history: List<HomeEvent>,
    val isDemo: Boolean,
    val restockingAlerts: List<RestockingRecord> = emptyList(),
    val restockingHistory: List<RestockingRecord> = emptyList(),
    val profileWorkSummary: ProfileWorkSummary? = null,
    val shelfAnalysis: ShelfAnalysis? = null,
    val analyses: List<ShelfAnalysis> = listOfNotNull(shelfAnalysis)
) {
    fun analysis(id: String?): ShelfAnalysis? = if (id == null) null
        else analyses.firstOrNull { it.id == id } ?: shelfAnalysis?.takeIf { it.id == id }

    fun analysisFor(record: RestockingRecord): ShelfAnalysis? {
        val candidate = if (record.analysisId != null) analysis(record.analysisId)
            else analyses.firstOrNull { entry -> entry.products.any { it.id == record.id } }
        return candidate?.takeIf { it.shelfCode == record.shelfCode }
    }

    fun analysisFor(event: HomeEvent): ShelfAnalysis? =
        analysis(event.analysisId)?.takeIf { it.shelfCode == event.shelfCode }
}

/** Blocking reads run outside the UI thread. The API adapter will implement this contract. */
fun interface StockerHomeRepository {
    fun load(userId: String): StockerHomeData
    fun resolveAlert(userId:String,id:String) { throw UnsupportedOperationException("Resolução indisponível") }
}
