package com.example.kairos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.scanner.CapturedPhoto
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CapturedPhotoTest {
    @Test fun correctsCameraOrientationAndRemovesMetadataFromJpeg() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File.createTempFile("damage-photo-test-",".jpg",context.cacheDir)
        try {
            val bitmap=Bitmap.createBitmap(800,400,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.GREEN)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) }
            bitmap.recycle()
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_ARTIST,"FICTITIOUS-TEST-METADATA")
                saveAttributes()
            }
            val image=CapturedPhoto.jpeg(file,3*1024*1024)
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(image,0,image.size,bounds)
            assertEquals(400,bounds.outWidth)
            assertEquals(800,bounds.outHeight)
            assertFalse(String(image,Charsets.ISO_8859_1).contains("FICTITIOUS-TEST-METADATA"))
        } finally { file.delete() }
    }
    @Test fun rejectsUnreadableImageWithoutPretendingItIsAttached() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File.createTempFile("damage-invalid-test-",".jpg",context.cacheDir)
        try {
            file.writeText("not an image")
            try { CapturedPhoto.jpeg(file);fail() }
            catch(_:IllegalArgumentException) { }
        } finally { file.delete() }
    }
}
