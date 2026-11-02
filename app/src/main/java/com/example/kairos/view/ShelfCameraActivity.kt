package com.example.kairos.view

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.kairos.R
import java.io.File

/** CameraX preview/capture is lifecycle-bound. Only the generated private cache filename crosses activities. */
class ShelfCameraActivity : AppCompatActivity() {
    private lateinit var preview: PreviewView
    private lateinit var feedback: TextView
    private lateinit var shutter: ImageButton
    private var camera: ProcessCameraProvider? = null
    private var capture: ImageCapture? = null
    private var front = false
    private var capturing = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) bindCamera() else {
            feedback.text = "Permita o acesso à câmera nas configurações do aplicativo para fotografar a gôndola."
            shutter.isEnabled = false
        }
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(16), dp(8), dp(24)); fitsSystemWindows = true
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(getColor(R.color.home_header_top), getColor(R.color.home_header_middle), getColor(R.color.home_header_top)))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.scanner_back); background = null; contentDescription = "Voltar"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(TextView(this).apply {
            text = "Fotografar gôndola"; textSize = 20f; setTextColor(Color.WHITE)
            typeface = ResourcesCompat.getFont(this@ShelfCameraActivity, R.font.montserrat_semibold)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        feedback = TextView(this).apply {
            text = "Enquadre a gôndola inteira e toque no botão para fotografar."
            textSize = 16f; setTextColor(0xBFFFFFFF.toInt()); gravity = Gravity.CENTER
            typeface = ResourcesCompat.getFont(this@ShelfCameraActivity, R.font.montserrat_regular)
            setPadding(dp(24), dp(8), dp(24), dp(20))
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(feedback)
        preview = PreviewView(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = FrameLayout(this)
        controls.addView(ImageButton(this).apply {
            setImageResource(R.drawable.scanner_flip); background = null; contentDescription = "Alternar câmera"
            setOnClickListener { if (!capturing) { front = !front; bindCamera() } }
        }, FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = dp(40) })
        shutter = ImageButton(this).apply {
            setImageResource(R.drawable.scanner_shutter); background = null; contentDescription = "Fotografar"
            isEnabled = false; setOnClickListener { photograph() }
        }
        controls.addView(shutter, FrameLayout.LayoutParams(dp(105), dp(105), Gravity.CENTER))
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(180)))
        setContentView(root)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) bindCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }
    private fun bindCamera() {
        shutter.isEnabled = false
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            try {
                val provider = future.get(); camera = provider
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val view = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                val photo = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(preview.display?.rotation ?: android.view.Surface.ROTATION_0).build()
                provider.unbindAll(); provider.bindToLifecycle(this, selector, view, photo)
                capture = photo; shutter.isEnabled = true
                feedback.text = "Enquadre a gôndola inteira e toque no botão para fotografar."
            } catch (_: Exception) { feedback.text = "Câmera indisponível. Volte e tente novamente." }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun photograph() {
        if (capturing) return
        val name = intent.getStringExtra("filename")?.takeIf { it.matches(Regex("[a-f0-9-]+\\.jpg")) }
            ?: run { finish(); return }
        val file = File(File(cacheDir, "scanner").apply { mkdirs() }, name)
        capturing = true; shutter.isEnabled = false
        capture?.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { setResult(RESULT_OK); finish() }
                override fun onError(exception: ImageCaptureException) {
                    file.delete(); capturing = false; shutter.isEnabled = true
                    feedback.text = "Não foi possível fotografar. Tente novamente."
                }
            })
    }
    override fun onDestroy() { camera?.unbindAll(); super.onDestroy() }
}
