package com.example.kairos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.cliente.FirebaseReceiptAi
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.java.GenerativeModelFutures
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Explicit opt-in: sends only a generated receipt image to the real Firebase service. */
@RunWith(AndroidJUnit4::class)
class FirebaseReceiptLiveTest {
    @Test fun readsSyntheticReceiptThroughRegisteredFirebaseApp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseReceiptLive")=="true")
        assumeTrue(!BuildConfig.APPLICATION_ID.endsWith(".integration"))
        FirebaseReceiptAi.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue("Firebase must be configured",FirebaseReceiptAi.configured())
        try {
            Tasks.await(FirebaseAppCheck.getInstance().getAppCheckToken(true),40,TimeUnit.SECONDS)
        } catch(error:Exception) {
            // Never include provider messages or tokens in test output.
            fail("App Check did not authorize this installation (${error.cause?.javaClass?.simpleName ?: error.javaClass.simpleName})")
        }
        if(InstrumentationRegistry.getArguments().getString("firebasePreflight")=="true") try {
            val model=GenerativeModelFutures.from(FirebaseAI.getInstance(FirebaseApp.getInstance(),GenerativeBackend.googleAI())
                .generativeModel("gemini-3.8-flash"))
            model.generateContent(content { text("Responda apenas OK.") }).get(45,TimeUnit.SECONDS)
        } catch(error:Exception) {
            val causes=generateSequence<Throwable>(error) { it.cause }.take(6).toList()
            val messages=causes.mapNotNull { it.message }.joinToString(" ")
            val known=listOf("SERVICE_DISABLED","API_KEY_SERVICE_BLOCKED","API_KEY_ANDROID_APP_BLOCKED",
                "PERMISSION_DENIED","BILLING_DISABLED","API_KEY_INVALID","UNAUTHENTICATED",
                "NOT_FOUND","RESOURCE_EXHAUSTED","API has not been used","403","404","429",
                "Firebase AI Logic API","Generative Language API","App Check","blocked","not enabled",
                "not supported","API_KEY_HTTP_REFERRER_BLOCKED","quota","timeout")
            val codes=known.filter { messages.contains(it,true) }
            val sanitized=messages
                .replace(Regex("AIza[\\w-]+"),"[redacted-key]")
                .replace(Regex("(?i)bearer\\s+[^\\s\"]+"),"[redacted-bearer]")
                .replace(Regex("[A-Za-z0-9_-]{30,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"),"[redacted-token]")
                .replace(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"),"[redacted-id]")
                .take(1600)
            fail("Firebase AI preflight failed; classes=${causes.map { it.javaClass.simpleName }}; recognized codes=$codes; message=$sanitized")
        }
        val bitmap=Bitmap.createBitmap(1100,700,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK; textSize=36f }
        listOf("NOTA FISCAL - TESTE FICTICIO", "DESCRICAO     QTD     TOTAL R$",
            "ARROZ BRANCO 5KG    2    40,00", "LEITE INTEGRAL 1L    1    6,50",
            "TOTAL R$ 46,50").forEachIndexed { index,line ->
            canvas.drawText(line,40f,80f+index*100f,paint)
        }
        val bytes=ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG,95,output)
            output.toByteArray()
        }
        bitmap.recycle()
        val result=FirebaseReceiptAi.interpret(bytes)
        val items=result.getJSONArray("items")
        assertEquals("Two synthetic items must be recognized",2,items.length())
        assertEquals("AI_SUGGESTION_UNVERIFIED",result.getString("source"))
        val descriptions=(0 until items.length()).map { items.getJSONObject(it).getString("description").uppercase() }
        assertTrue(descriptions.any { it.contains("ARROZ") })
        assertTrue(descriptions.any { it.contains("LEITE") })
    }
}
