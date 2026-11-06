package com.example.kairos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.home.HttpStockerHomeRepository
import com.example.kairos.model.network.ApiDependencies
import com.example.kairos.model.home.StockerListReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
class StockerListReaderTest {
    @Test fun loadsPublishedHomeForSignedInStockerWhenExplicitlyRequested() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("stockerHomeLive")=="true")
        ApiDependencies.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val uid=ApiDependencies.currentUid()
        assertNotNull("Sign into the stocker test account before running this check",uid)
        val data=HttpStockerHomeRepository().load(uid!!)
        assertFalse(data.isDemo)
        assertTrue(data.pendingThisWeek>=0)
        assertTrue(data.completedThisWeek>=0)
        assertEquals("Repositor",data.profileWorkSummary?.role)
    }

    @Test fun readsMoreThanFourMegabytesAndKeepsOnlyOpenAlertsForCurrentEmployee() {
        val ignored="x".repeat(600)
        val json=buildString {
            append('[')
            repeat(8_000) { index ->
                if(index>0) append(',')
                append("""{"id":$index,"employeeId":99,"status":"OPEN","description":"$ignored","nested":{"other":[1,2]}}""")
            }
            append(""",{"id":8001,"employeeId":157,"status":"CLOSED"},{"id":8002,"employeeId":157,"status":"OPEN","shelfId":12,"description":"Alerta de teste","stockoutDate":"2026-11-05"}]""")
        }.toByteArray()
        assertTrue(json.size>4*1024*1024)
        val alerts=StockerListReader.alerts(ByteArrayInputStream(json),157)
        assertEquals(1,alerts.size)
        assertEquals(8002,alerts.single().getInt("id"))
        assertEquals(12,alerts.single().getInt("shelfId"))
        assertFalse(alerts.single().has("nested"))
    }

    @Test fun locationLookupRetainsOnlyRequestedShelfAndFields() {
        val json="""[{"id":1,"aisleId":2,"ignored":{"items":[1]}},{"id":3,"aisleId":4,"optional":null}]"""
        val shelves=StockerListReader.read(json.byteInputStream(),setOf("id","aisleId")) { it.getLong("id")==3L }
        assertEquals(1,shelves.size)
        assertEquals(4,shelves.single().getInt("aisleId"))
        assertEquals(2,shelves.single().length())
    }

    @Test fun rejectsRetainedListsBeyondDisplayLimitInsteadOfSilentlyTruncating() {
        val json=(1..10_001).joinToString(",","[","]") { "{\"id\":$it}" }
        try {
            StockerListReader.read(json.byteInputStream(),setOf("id")) { true }
            fail("Expected explicit list limit failure")
        } catch(error:IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("registros demais"))
        }
    }
    @Test fun readsDamageMetadataWithoutRetainingPrivateFields() {
        val json="""[{"id":91,"report_id":"b8b80b83-b86d-47c8-b8ec-8c0361fe3aa5","problem_type":"DAMAGED","report_description":"Broken","product_name":"Product","reported_at":"2026-11-05T12:00:00Z","photo":"ignored","customer":{"email":"private@invalid"}}]"""
        val metadata=StockerListReader.read(json.byteInputStream(),
            com.example.kairos.model.home.StockerDamageReports.fields) { true }
        assertEquals(6,metadata.single().length())
        assertFalse(metadata.single().has("photo"))
        assertFalse(metadata.single().has("customer"))
    }

}
