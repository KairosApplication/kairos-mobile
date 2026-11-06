package com.example.kairos.customer

import com.example.kairos.model.cliente.ReceiptAiResult
import org.junit.Assert.*
import org.junit.Test

class ReceiptAiResultTest {
    @Test fun retainsFractionalQuantityAndUnverifiedStatusWithoutExtraPersonalFields() {
        val result=ReceiptAiResult.parse("""{"accessKey":null,"cpf":"discard","source":"CONFIRMED","items":[{"description":"Arroz","quantity":0.5,"amount":12.50,"productId":99}]}""")
        assertEquals("AI_SUGGESTION_UNVERIFIED",result.getString("source"))
        assertFalse(result.has("cpf"))
        assertFalse(result.getJSONArray("items").getJSONObject(0).has("productId"))
        assertEquals(0.5,result.getJSONArray("items").getJSONObject(0).getDouble("quantity"),0.0)
    }
    @Test fun invalidAmountsAndQuantitiesAreLeftUnknownRatherThanInvented() {
        val result=ReceiptAiResult.parse("""{"items":[{"description":"Item","quantity":-1,"amount":1.234}],"accessKey":"invalid"}""")
        val item=result.getJSONArray("items").getJSONObject(0)
        assertTrue(item.isNull("quantity")); assertTrue(item.isNull("amount")); assertTrue(result.isNull("accessKey"))
    }
    @Test fun emptyReceiptRemainsEmpty() {
        assertEquals(0,ReceiptAiResult.parse("""{"items":[],"accessKey":null}""").getJSONArray("items").length())
    }
    @Test fun oversizedResultIsRejected() {
        try { ReceiptAiResult.parse("x".repeat(256*1024+1)); fail() } catch(_:IllegalArgumentException) { }
    }
}
