package com.example.kairos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.auth.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Uses a separate demo FirebaseApp against local emulators; never touches the configured project. */
@RunWith(AndroidJUnit4::class)
class FirebaseAccountIntegrationTest {
    @Test fun editsRealSdkDataAndVerifiesEmailBeforeUpdatingTheProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setProjectId("demo-kairos").setApplicationId("1:123456789:android:accounttest")
            .setApiKey("fake-emulator-key").build(), "account-integration-${System.nanoTime()}")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9099) }
        val db = FirebaseFirestore.getInstance(app).apply {
            useEmulator("10.0.2.2", 8080)
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        }
        val repo = FirebaseAuthRepository(FirebaseAuthGateway(auth), FirestoreProfileStore(db))
        val unique = System.nanoTime().toString()
        val oldEmail = "old-$unique@example.com"
        val newEmail = "new-$unique@example.com"
        val cpf = unique.takeLast(11).padStart(11, '0')
        try {
            val user = repo.register(Registration("João", "Silva", LocalDate.of(1985, 10, 22),
                cpf, oldEmail, "old-password-test", "12345678", "basico"))
            val renamed = repo.updateName(user.uid, "Maria Alice", "de Souza")
            assertEquals("Maria Alice", renamed.profile!!.name)
            assertEquals("de Souza", repo.restoreSession()!!.profile!!.lastName)
            val requested = repo.requestEmailChange(user.uid, newEmail, "old-password-test")
            assertEquals(oldEmail, requested.email)
            assertEquals(oldEmail, repo.restoreSession()!!.profile!!.email)
            val connection = URL("http://10.0.2.2:9099/emulator/v1/projects/demo-kairos/oobCodes")
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val codes = try { JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("oobCodes") }
                finally { connection.disconnect() }
            val code = (0 until codes.length()).map { codes.getJSONObject(it) }
                .last { it.optString("requestType") == "VERIFY_AND_CHANGE_EMAIL" }.getString("oobCode")
            Tasks.await(auth.applyActionCode(code), 30, TimeUnit.SECONDS)
            val verified = try { repo.restoreSession() ?: repo.login(newEmail, "old-password-test") }
                catch (_: AccountChangedException) { repo.login(newEmail, "old-password-test") }
            assertEquals(newEmail, verified.email)
            assertEquals(newEmail, verified.profile!!.email)
            assertEquals(user.uid, verified.uid)
            repo.changePassword(user.uid, "old-password-test", "new-password-test")
            repo.logout()
            assertEquals(user.uid, repo.login(newEmail, "new-password-test").uid)
            repo.logout()
            try { repo.login(newEmail, "old-password-test"); fail("Old password must fail") }
            catch (_: IllegalArgumentException) { }
            repo.login(newEmail, "new-password-test")
            val stored = Tasks.await(db.collection("users").document(user.uid).get(), 30, TimeUnit.SECONDS)
            assertEquals("Maria Alice", stored.getString("name"))
            assertEquals(cpf, stored.getString("cpf"))
            assertEquals("1985-10-22", stored.getString("birthDate"))
            assertFalse(stored.contains("password"))
            assertEquals(8, stored.data!!.size)
        } finally {
            auth.signOut()
            Tasks.await(db.terminate(), 30, TimeUnit.SECONDS)
            app.delete()
        }
    }
}
