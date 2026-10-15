package com.example.kairos.home

import com.example.kairos.model.home.DemoStockerHomeRepository
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ShelfAnalysisMappingTest {
    private val data = DemoStockerHomeRepository(Clock.fixed(
        Instant.parse("2026-10-14T15:00:00Z"), ZoneOffset.UTC)).load("demo")

    @Test fun everyRecordOpensItsOwnAnalysisAndNeverMixesShelves() {
        (data.restockingAlerts + data.restockingHistory).forEach { record ->
            val analysis = requireNotNull(data.analysisFor(record))
            assertEquals(record.analysisId, analysis.id)
            assertEquals(record.shelfCode, analysis.shelfCode)
            assertTrue(analysis.products.any { it.id == record.id })
            assertTrue(analysis.products.all { it.shelfCode == analysis.shelfCode })
        }
        val a12 = data.restockingAlerts.first { it.shelfCode == "A12" }
        val b02 = data.restockingAlerts.first { it.shelfCode == "B02" }
        assertNotEquals(data.analysisFor(a12)?.id, data.analysisFor(b02)?.id)
    }

    @Test fun homeEventsResolveTheSameShelfAsTheirAnalysis() {
        (data.priorities + data.alerts + data.history).forEach { event ->
            assertEquals(event.shelfCode, requireNotNull(data.analysis(event.analysisId)).shelfCode)
        }
    }

    @Test fun aMissingExplicitLinkDoesNotFallBackToAnUnrelatedDemo() {
        val record = data.restockingAlerts.first().copy(analysisId = "missing-analysis")
        assertNull(data.analysisFor(record))
        assertNull(data.analysis("missing-analysis"))
        assertNull(data.analysis(null))
        val otherShelf = data.restockingAlerts.first { it.shelfCode == "B02" }
        assertNull(data.analysisFor(otherShelf.copy(analysisId = "pending-A12")))
    }
}
