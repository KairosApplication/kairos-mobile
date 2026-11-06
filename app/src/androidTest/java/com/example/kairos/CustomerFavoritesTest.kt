package com.example.kairos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.cliente.CustomerFavorites
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerFavoritesTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun localFavoritesSurviveNewStoreAndStaySeparatedByAccountAndService() {
        val uid="favorites-test-${System.nanoTime()}"
        val store=CustomerFavorites(context,uid,"https://market-a.test")
        try {
            store.set(42,true)
            assertEquals(setOf(42L),CustomerFavorites(context,uid,"https://market-a.test").ids())
            assertTrue(CustomerFavorites(context,"$uid-other","https://market-a.test").ids().isEmpty())
            assertTrue(CustomerFavorites(context,uid,"https://market-b.test").ids().isEmpty())
        } finally { store.set(42,false) }
    }
    @Test fun localFavoriteChangesAreIdempotentAndDoNotEraseOtherProducts() {
        val uid="favorites-test-${System.nanoTime()}"
        val store=CustomerFavorites(context,uid)
        try {
            store.set(1,true); store.set(1,true); store.set(2,true)
            assertEquals(setOf(1L,2L),store.ids())
            store.set(1,false); store.set(1,false)
            assertEquals(setOf(2L),store.ids())
        } finally { store.set(1,false);store.set(2,false) }
    }
    @Test fun invalidProductCannotBePersisted() {
        val store=CustomerFavorites(context,"favorites-test-${System.nanoTime()}")
        try { store.set(0,true); fail("Invalid product must not be saved") }
        catch(expected:IllegalArgumentException) { assertTrue(store.ids().isEmpty()) }
    }
}
