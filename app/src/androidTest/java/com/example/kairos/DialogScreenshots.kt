package com.example.kairos

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Capture all dialog windows, not just the underlying Activity view. */
internal fun captureDialog(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    SystemClock.sleep(350)
    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "dialog-previews").apply { mkdirs() }
    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
}
