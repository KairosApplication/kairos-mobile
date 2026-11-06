package com.example.kairos.customer

import com.example.kairos.model.cliente.CustomerDamageReports
import com.example.kairos.model.auth.AccountChangedException
import com.example.kairos.model.network.ApiFailure
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CustomerDamageReportsTest {
    private val command="258e5269-c619-4a1c-ad50-b53342e0162a"
    private val response="""{"protocol":"b8b80b83-b86d-47c8-b8ec-8c0361fe3aa5","alertId":91,"status":"OPEN"}"""
    @Test fun sendsPhotoAndStableCommandOnlyToCustomerReportRoute() {
        val requests=mutableListOf<JSONObject>()
        val reports=CustomerDamageReports(send={ uid,path,method,body ->
            assertEquals("spring:42",uid)
            assertEquals("/api/v1/customer/damage-reports",path)
            assertEquals("POST",method)
            requests.add(body!!)
            response
        },owner={})
        val photo=byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0xd9.toByte())
        repeat(2) { reports.report("spring:42",command,17,null,"DAMAGED","  Embalagem aberta  ",photo) }
        assertEquals(requests[0].toString(),requests[1].toString())
        assertEquals(command,requests[0].getString("commandId"))
        assertTrue(requests[0].isNull("shelfId"))
        assertEquals("Embalagem aberta",requests[0].getString("description"))
        assertFalse(requests[0].has("employeeId"))
        assertFalse(requests[0].has("companyId"))
        assertFalse(requests[0].has("restockedQuantity"))
        assertArrayEquals(photo,java.util.Base64.getDecoder().decode(requests[0].getString("imageBase64")))
    }
    @Test fun deniedMutationIsNeverAutomaticallyRetried() {
        var calls=0
        val reports=CustomerDamageReports(send={ _,_,_,_ -> calls++;throw ApiFailure(403) },owner={})
        try { reports.report("spring:42",command,17,3,"EXPIRED","",null);fail() }
        catch(error:ApiFailure) { assertEquals(403,error.status) }
        assertEquals(1,calls)
    }
    @Test fun changedAccountCannotReceiveAnotherAccountsConfirmation() {
        var account="spring:42"
        val reports=CustomerDamageReports(send={ _,_,_,_ -> account="spring:43";response },
            owner={ if(it!=account) throw AccountChangedException() })
        try { reports.report("spring:42",command,17,3,"EXPIRED","",null);fail() }
        catch(_:AccountChangedException) { }
    }
    @Test fun validatesReportBeforeSendingAndRequiresPersistedAlertConfirmation() {
        var calls=0
        val reports=CustomerDamageReports(send={ _,_,_,_ -> calls++;"{}" },owner={})
        try { reports.report("spring:42",command,17,3,"INVENTED","",null);fail() }
        catch(_:IllegalArgumentException) { }
        assertEquals(0,calls)
        try { reports.report("spring:42",command,17,3,"OTHER","",null);fail() }
        catch(_:org.json.JSONException) { }
        assertEquals(1,calls)
    }
}
