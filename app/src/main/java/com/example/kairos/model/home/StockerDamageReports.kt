package com.example.kairos.model.home

import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Enriches only alerts already scoped to the signed-in employee. */
object StockerDamageReports {
    val fields = setOf("id", "report_id", "problem_type", "report_description", "product_name", "reported_at")
    private val types = setOf("DAMAGED", "TAMPERED", "EXPIRED", "OTHER")

    fun associate(alerts: List<JSONObject>, reports: List<JSONObject>): List<JSONObject> {
        val ids = alerts.map { it.getLong("id") }.toSet()
        val matching = reports.filter { it.getLong("id") in ids }
        require(matching.map { it.getLong("id") }.distinct().size == matching.size)
        val byId = matching.associateBy { it.getLong("id") }
        return alerts.map { source ->
            val result = JSONObject(source.toString())
            byId[source.getLong("id")]?.let { report ->
                val protocol = report.getString("report_id")
                require(UUID.fromString(protocol).toString().equals(protocol, ignoreCase = true))
                require(report.getString("problem_type") in types)
                Instant.parse(report.getString("reported_at"))
                for (field in fields - "id") {
                    result.put(field, report.getString(field))
                }
            }
            result
        }
    }
}
