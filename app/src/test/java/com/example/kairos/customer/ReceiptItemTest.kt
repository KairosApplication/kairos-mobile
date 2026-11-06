package com.example.kairos.customer

import com.example.kairos.model.cliente.ReceiptItem
import java.math.BigDecimal
import org.junit.Test
import org.junit.Assert.*

class ReceiptItemTest {
    @Test fun confirmedItemPreservesTheCustomerEnteredLineTotal() {
        val json=ReceiptItem(1,2,BigDecimal("12.50")).json()
        assertEquals(2,json.getInt("quantity"))
        assertEquals("12.50",json.get("amount").toString())
    }
    @Test fun invalidQuantitiesAndAmountsAreNotSubmitted() {
        for(item in listOf(ReceiptItem(1,0,BigDecimal.ONE),ReceiptItem(1,1,BigDecimal("-1")),
            ReceiptItem(1,1,BigDecimal("1.123")),ReceiptItem(0,1,BigDecimal.ONE))) {
            try { item.json(); fail("Invalid item accepted") } catch(_:IllegalArgumentException) {}
        }
    }
}
