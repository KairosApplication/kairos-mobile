package com.example.kairos

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.auth.EncryptedSessionStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class EncryptedSessionStoreTest {
    @Test fun keystoreEncryptsRestoresAndClearsSessionWithoutPlaintext() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(base.cacheDir,"session-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context=object:ContextWrapper(base) { override fun getNoBackupFilesDir()=directory }
        try {
            val value="{\"access\":\"fictitious-session-test\",\"cookies\":{\"refresh\":\"fictitious-refresh\"}}"
            val store=EncryptedSessionStore(context)
            store.write(value)
            val file=File(directory,"spring-session.enc")
            assertTrue(file.exists())
            assertFalse(String(file.readBytes(),Charsets.ISO_8859_1).contains("fictitious"))
            assertEquals(value,EncryptedSessionStore(context).read())
            store.write(null)
            assertNull(store.read()); assertFalse(file.exists())
            file.writeText("unreadable-test-session")
            assertNull(store.read()); assertFalse(file.exists())
        } finally { directory.deleteRecursively() }
    }
}
