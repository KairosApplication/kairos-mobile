package com.example.kairos.model.home

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

object StockDates {
    /** SQL DATE is a calendar date. Older JDBC JSON encodes it as midnight UTC, not an event instant. */
    fun alertDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        OffsetDateTime.parse(value).toLocalDate()
    }
}
