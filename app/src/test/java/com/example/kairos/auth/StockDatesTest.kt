package com.example.kairos.auth

import com.example.kairos.model.home.StockDates
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class StockDatesTest {
    @Test fun acceptsThePopulatedPostgresAlertResponseWithoutMovingItToThePreviousDay() {
        val day = StockDates.alertDate("2026-11-02T00:00:00.000Z")
        assertEquals(LocalDate.of(2026, 11, 2), day)
        assertEquals(day, day.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()
            .atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate())
    }
    @Test fun acceptsCanonicalCalendarDates() {
        assertEquals(LocalDate.of(2026, 11, 2), StockDates.alertDate("2026-11-02"))
    }
}
