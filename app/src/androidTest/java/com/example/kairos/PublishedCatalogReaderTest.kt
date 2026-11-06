package com.example.kairos

import com.example.kairos.model.cliente.PublishedCatalogReader
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigDecimal

class PublishedCatalogReaderTest {
    @Test fun catalogLargerThanOldResponseLimitLoadsMobileFieldsWithoutKeepingUnusedData() {
        val json="[{\"id\":42,\"name\":\"Produto teste\",\"brand\":null,\"price\":12.50,"+
            "\"category\":{\"unused\":\""+"x".repeat(4*1024*1024+100)+"\"}},"+
            "{\"id\":43,\"name\":\"Outro produto\",\"brand\":\"Marca\",\"price\":null}]"
        val result=PublishedCatalogReader.read(ByteArrayInputStream(json.toByteArray()))
        assertEquals(2,result.products.size)
        assertEquals(BigDecimal("12.50"),result.products.first().price)
        assertEquals("",result.products.first().brand)
        assertEquals("Outro produto",result.products.last().name)
        assertNull(result.products.last().price)
        assertFalse(result.truncated)
    }
    @Test fun catalogLimitIsExplicitRatherThanPretendingTheListIsComplete() {
        val json=(1..3).joinToString(",","[","]") { "{\"id\":$it,\"name\":\"Produto $it\",\"price\":1}" }
        val result=PublishedCatalogReader.read(ByteArrayInputStream(json.toByteArray()),maxProducts=2)
        assertEquals(2,result.products.size)
        assertTrue(result.truncated)
    }
    @Test fun invalidProductIsNotSilentlyShownAsAnEmptyCatalog() {
        try {
            PublishedCatalogReader.read(ByteArrayInputStream("[{\"id\":42,\"price\":1}]".toByteArray()))
            fail("Missing name must not create a product")
        } catch(error:IllegalArgumentException) { assertTrue(error.message!!.contains("identificação válida")) }
    }
}
