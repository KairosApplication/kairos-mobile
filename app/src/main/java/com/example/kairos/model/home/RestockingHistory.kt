package com.example.kairos.model.home

import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.util.Locale

enum class RestockingStatus { REQUIRED, LOW_STOCK, COMPLETED }

data class RestockingRecord(
    val id: String,
    val productName: String,
    val imageKey: String,
    val shelfCode: String,
    val occurredAt: Instant,
    val status: RestockingStatus
)

enum class HistoryPeriod { ALL, TODAY, YESTERDAY, LAST_SEVEN_DAYS }

data class HistoryFilter(
    val query: String = "",
    val period: HistoryPeriod = HistoryPeriod.ALL,
    val shelfCode: String? = null,
    val oldestFirst: Boolean = false
) {
    fun apply(records: List<RestockingRecord>, today: LocalDate, zone: ZoneId): List<RestockingRecord> {
        val search = normalized(query.trim())
        val filtered = records.filter { record ->
            val date = record.occurredAt.atZone(zone).toLocalDate()
            val inPeriod = when (period) {
                HistoryPeriod.ALL -> true
                HistoryPeriod.TODAY -> date == today
                HistoryPeriod.YESTERDAY -> date == today.minusDays(1)
                HistoryPeriod.LAST_SEVEN_DAYS -> date in today.minusDays(6)..today
            }
            record.status == RestockingStatus.COMPLETED && inPeriod &&
                (shelfCode == null || record.shelfCode == shelfCode) &&
                (search.isEmpty() || normalized(record.productName).contains(search) ||
                    normalized(record.shelfCode).contains(search))
        }
        return if (oldestFirst) filtered.sortedBy { it.occurredAt }
        else filtered.sortedByDescending { it.occurredAt }
    }

    private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
}
