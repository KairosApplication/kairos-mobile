package com.example.kairos

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kairos.view.AccountTestActivity
import com.example.kairos.view.CustomerHomeView
import com.example.kairos.model.cliente.*
import com.example.kairos.viewmodel.CustomerUiState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class CustomerHomeTest {
    @Test fun favoritesAreAvailableLocallyAndOfferSectionsAreAbsent() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var favorites=0
                val home=CustomerHomeView(activity,{}, {}, {}, {}, { favorites++ })
                activity.setContentView(home)
                home.bind(CustomerUiState(data=CustomerData(null,"",
                    listOf(CustomerProduct(42,"Produto de teste","Marca",BigDecimal.TEN,false)),emptyList(),
                    listOf(CustomerOffer(42,"PROMOTION","Oferta antiga",BigDecimal.TEN)),emptySet(),emptyList(),emptyList(),
                    setOf("receipts","reports","recommendations","locations"))))
                assertFalse(texts(home).any { it.text=="Promoções" || it.text=="Queima de estoque" || it.text.contains("OFF") })
                val favorite=texts(home).first { it.text=="Favoritar" }
                assertTrue(favorite.isEnabled)
                favorite.performClick()
                assertEquals(1,favorites)
                texts(home).first { it.text=="Favoritos" }.performClick()
                assertTrue(texts(home).any { it.text.toString().contains("salvos neste aparelho") })
            }
        }
    }
    @Test fun customerHomeRendersOnTheDeviceWithFictitiousTestDataOnly() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            lateinit var home:CustomerHomeView
            scenario.onActivity { activity ->
                home=CustomerHomeView(activity,{}, {}, {}, {}, {})
                activity.setContentView(home)
                home.bind(CustomerUiState(data=CustomerData(1,"Mercado de teste",
                    listOf(CustomerProduct(1,"Arroz integral","Produto de teste",BigDecimal("12.50"),false)),
                    emptyList(),listOf(CustomerOffer(1,"PROMOTION","Oferta de teste",BigDecimal("10"))),setOf(1),emptyList(),emptyList())))
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            android.os.SystemClock.sleep(350)
            scenario.onActivity { activity ->
                assertTrue(home.width>0 && home.height>0)
                val bitmap=android.graphics.Bitmap.createBitmap(home.width,home.height,android.graphics.Bitmap.Config.ARGB_8888)
                home.draw(android.graphics.Canvas(bitmap))
                java.io.File(activity.getExternalFilesDir(null),"customer-home-preview.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
                bitmap.recycle()
            }
        }
    }
    private fun texts(view:View):List<TextView> = when(view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    @Test fun publishedCustomerCatalogDoesNotRequireCompanyOrPretendMissingFeaturesAreEmpty() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val home=CustomerHomeView(activity,{}, {}, {}, {}, {})
                activity.setContentView(home)
                home.bind(CustomerUiState(data=CustomerData(null,"",
                    listOf(CustomerProduct(42,"Produto publicado","Marca de teste",BigDecimal("9.50"),false)),
                    emptyList(),emptyList(),emptySet(),emptyList(),emptyList(),
                    setOf("receipts","reports","favorites","offers","recommendations","locations"))))
                assertTrue(texts(home).any { it.text=="Produto publicado" })
                assertFalse(texts(home).first { it.text=="Escanear nota fiscal" }.isEnabled)
                assertFalse(texts(home).first { it.text=="Reportar avaria" }.isEnabled)
                assertFalse(texts(home).first { it.text=="Favoritar" }.isEnabled)
                texts(home).first { it.text=="Compras" }.performClick()
                assertTrue(texts(home).any { it.text.toString().contains("histórico de compras ainda não") })
                assertFalse(texts(home).any { it.text=="Seu histórico começa aqui" })
            }
        }
    }
    @Test fun serverCanEnableReceiptPhotosWithoutEnablingPurchaseRegistrationOrDamageReports() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var scans=0
                val home=CustomerHomeView(activity,{scans++}, {}, {}, {}, {})
                activity.setContentView(home)
                home.bind(CustomerUiState(data=CustomerData(null,"",emptyList(),emptyList(),emptyList(),emptySet(),
                    emptyList(),emptyList(),setOf("receipts","reports","favorites","offers","recommendations","locations"),
                    receiptPhotoAvailable=true)))
                val scan=texts(home).first { it.text=="Escanear nota fiscal" }
                assertTrue(scan.isEnabled)
                scan.performClick()
                assertEquals(1,scans)
                assertFalse(texts(home).first { it.text=="Reportar avaria" }.isEnabled)
                texts(home).first { it.text=="Compras" }.performClick()
                assertTrue(texts(home).any { it.text.toString().contains("histórico de compras ainda não") })
            }
        }
    }
    @Test fun realDataStatesFavoritesAndHistoryStayInsideTheHome() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var favorites=0
                var scans=0
                var reports=0
                val home=CustomerHomeView(activity,{ scans++ },{ reports++ },{}, {},{ favorites++ })
                activity.setContentView(home)
                val product=CustomerProduct(1,"Produto do teste","Marca",BigDecimal("12.50"),false)
                val receipt=JSONObject().put("id","00000000-0000-0000-0000-000000000001")
                    .put("confirmed_at","2026-11-05 10:00").put("total",BigDecimal("12.50"))
                    .put("items",org.json.JSONArray().put(JSONObject().put("name",product.name).put("quantity",1).put("amount",BigDecimal("12.50"))))
                val data=CustomerData(1,"Mercado de teste",listOf(product),emptyList(),emptyList(),setOf(1),listOf(receipt),emptyList())
                home.bind(CustomerUiState(data=data))
                texts(home).first { it.text=="Escanear nota fiscal" }.performClick()
                texts(home).first { it.text=="Reportar avaria" }.performClick()
                texts(home).first { it.text=="Favoritar" }.performClick()
                assertEquals(1,scans); assertEquals(1,reports); assertEquals(1,favorites)
                home.bind(CustomerUiState(data=data.copy(products=listOf(product.copy(favorite=true)))))
                texts(home).first { it.text=="Favoritos" }.performClick()
                assertTrue(texts(home).any { it.text==product.name })
                texts(home).first { it.text=="Compras" }.performClick()
                assertTrue(texts(home).any { it.text=="Histórico de compras" })
                assertTrue(texts(home).any { it.text.toString().startsWith("Compra ·") })
                assertTrue(home.handleBack())
                home.reset()
                assertFalse(texts(home).any { it.text==product.name })
            }
        }
    }
}
