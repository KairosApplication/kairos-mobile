package com.example.kairos.home

import com.example.kairos.model.home.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RestockingHistoryTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.of(2026, 10, 7)
    private val data = DemoStockerHomeRepository(Clock.fixed(Instant.parse("2026-10-07T21:00:00Z"), zone)).load("test")

    @Test fun searchIgnoresAccentsAndCombinesWithPeriodAndShelf() {
        val matches = HistoryFilter("  AGUA  ", HistoryPeriod.YESTERDAY, "C03")
            .apply(data.restockingHistory, today, zone)
        assertEquals(listOf("yesterday-water"), matches.map { it.id })
        assertTrue(HistoryFilter("agua", HistoryPeriod.TODAY, "A12")
            .apply(data.restockingHistory, today, zone).isEmpty())
        assertEquals(2, HistoryFilter("b11").apply(data.restockingHistory, today, zone).size)
    }

    @Test fun periodsUseLocalDatesAndSevenDaysIncludeOnlyTheirBoundaries() {
        fun at(day: LocalDate) = day.atStartOfDay(zone).toInstant()
        val source = data.restockingHistory.first()
        val records = listOf(
            source.copy(id = "start", occurredAt = at(today.minusDays(6))),
            source.copy(id = "outside", occurredAt = at(today.minusDays(7))),
            source.copy(id = "future", occurredAt = at(today.plusDays(1))),
            source.copy(id = "today", occurredAt = Instant.parse("2026-10-08T02:59:59Z")),
            source.copy(id = "pending", occurredAt = at(today), status = RestockingStatus.REQUIRED)
        )
        assertEquals(listOf("today", "start"), HistoryFilter(period = HistoryPeriod.LAST_SEVEN_DAYS)
            .apply(records, today, zone).map { it.id })
        assertEquals(listOf("today"), HistoryFilter(period = HistoryPeriod.TODAY)
            .apply(records, today, zone).map { it.id })
    }

    @Test fun orderIsReversibleAndDoesNotModifyOriginalRecords() {
        val original = data.restockingHistory.toList()
        val newest = HistoryFilter().apply(original, today, zone)
        val oldest = HistoryFilter(oldestFirst = true).apply(original, today, zone)
        assertEquals(newest.reversed(), oldest)
        assertEquals(original, data.restockingHistory)
    }
}
