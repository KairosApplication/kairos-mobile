package com.example.kairos.home

import com.example.kairos.model.home.StockerDamageReports
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StockerDamageReportsTest {
    private fun alert(id: Long = 91) = JSONObject().put("id", id)
        .put("description", "Relato de cliente - DAMAGED").put("shelf_id", 3)
        .put("product_id", 17).put("extra", JSONObject().put("value", 1))
    private fun report(id: Long = 91) = JSONObject().put("id", id)
        .put("report_id", "b8b80b83-b86d-47c8-b8ec-8c0361fe3aa5")
        .put("problem_type", "DAMAGED").put("report_description", "Broken package")
        .put("product_name", "Product").put("reported_at", "2026-11-05T12:30:00Z")

    @Test fun associatesByAlertIdAndPreservesExistingFieldsWithoutMutatingInputs() {
        val first = alert(); val second = alert(92); val metadata = report()
        val before = first.toString(); val reportBefore = metadata.toString()
        val result = StockerDamageReports.associate(listOf(second, first), listOf(metadata))
        assertFalse(result[0].has("report_id"))
        assertEquals(metadata.getString("report_id"), result[1].getString("report_id"))
        assertEquals(3, result[1].getInt("shelf_id"))
        assertEquals(17, result[1].getInt("product_id"))
        assertEquals(first.getString("description"), result[1].getString("description"))
        result[1].getJSONObject("extra").put("value", 2)
        assertEquals(before, first.toString()); assertEquals(reportBefore, metadata.toString())
    }
    @Test fun unmatchedReportsDoNotCreateAlertsOrClassifyByDescription() {
        val result = StockerDamageReports.associate(listOf(alert()), listOf(report(92)))
        assertEquals(1, result.size); assertFalse(result.single().has("report_id"))
        assertTrue(StockerDamageReports.associate(emptyList(), listOf(report())).isEmpty())
    }
    @Test fun rejectsInvalidTypeUuidTimestampAndDuplicateMetadata() {
        for (row in listOf(report().put("problem_type", "INVENTED"),
            report().put("report_id", "invalid"), report().put("reported_at", "yesterday"))) {
            assertThrows(Exception::class.java) { StockerDamageReports.associate(listOf(alert()), listOf(row)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            StockerDamageReports.associate(listOf(alert()), listOf(report(), report()))
        }
    }
    @Test fun acceptsAllSupportedProblemTypes() {
        for (type in listOf("DAMAGED", "TAMPERED", "EXPIRED", "OTHER")) {
            assertEquals(type, StockerDamageReports.associate(listOf(alert()),
                listOf(report().put("problem_type", type))).single().getString("problem_type"))
        }
    }
}
