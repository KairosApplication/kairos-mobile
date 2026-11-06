package com.example.kairos.view

import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX infrastructure; decodes only QR codes locally, never opens a scanned URL. */
internal class QrCamera(private val activity:AppCompatActivity,private val preview:PreviewView,
    private val decoded:(String)->Unit,private val failure:()->Unit):AutoCloseable {
    private val executor=Executors.newSingleThreadExecutor()
    private val closed=AtomicBoolean(false)
    private var provider:ProcessCameraProvider?=null
    private val analyzer=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
    fun start() {
        val future=ProcessCameraProvider.getInstance(activity)
        future.addListener({
            if(closed.get()) return@addListener
            try {
                val camera=future.get(); provider=camera
                val view=Preview.Builder().build().also { it.surfaceProvider=preview.surfaceProvider }
                analyzer.setAnalyzer(executor) { image ->
                    try {
                        val plane=image.planes[0]
                        val buffer=plane.buffer
                        val bytes=ByteArray(image.width*image.height)
                        for(y in 0 until image.height) for(x in 0 until image.width)
                            bytes[y*image.width+x]=buffer.get(y*plane.rowStride+x*plane.pixelStride)
                        val source=PlanarYUVLuminanceSource(bytes,image.width,image.height,0,0,image.width,image.height,false)
                        val result=runCatching { QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
                        result?.takeIf { !closed.get() }?.let { value ->
                            ContextCompat.getMainExecutor(activity).execute { if(!closed.get()) { close(); decoded(value) } }
                        }
                    } catch(_:Exception) { /* An unreadable frame is normal; wait for the next one. */ }
                    finally { image.close() }
                }
                camera.bindToLifecycle(activity,CameraSelector.DEFAULT_BACK_CAMERA,view,analyzer)
            } catch(_:Exception) { failure(); close() }
        },ContextCompat.getMainExecutor(activity))
    }
    override fun close() {
        if(closed.compareAndSet(false,true)) {
            analyzer.clearAnalyzer(); provider?.unbindAll(); executor.shutdownNow()
        }
    }
}
