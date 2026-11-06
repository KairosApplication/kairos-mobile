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
    private val receiptMode get() = intent.getStringExtra("captureMode")=="receipt"
    private val damageMode get() = intent.getStringExtra("captureMode")=="damage"
    private val cameraInstructions get() = if(receiptMode) "Enquadre os itens e valores da nota. Evite incluir dados pessoais."
        else if(damageMode) "Mostre o produto e o problema com nitidez. Evite fotografar pessoas."
        else "Enquadre a gôndola inteira e toque no botão para fotografar."
    private lateinit var preview: PreviewView
    private lateinit var feedback: TextView
    private lateinit var shutter: ImageButton
    private var camera: ProcessCameraProvider? = null
    private var capture: ImageCapture? = null
    private var front = false
    private var capturing = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) bindCamera() else {
            feedback.text = "Permita o acesso à câmera nas configurações do aplicativo para fotografar."
            shutter.isEnabled = false
        }
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        com.example.kairos.model.home.StockerPreferences.restoreTheme(this)
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(16)); fitsSystemWindows = true
            setBackgroundColor(0xFF102B23.toInt())
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.scanner_back); background = null; contentDescription = "Voltar"
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(TextView(this).apply {
                text = if(receiptMode) "Fotografar nota fiscal" else if(damageMode) "Fotografar avaria" else "Fotografar gôndola"; textSize = 20f; setTextColor(Color.WHITE)
            typeface = ResourcesCompat.getFont(this@ShelfCameraActivity, R.font.montserrat_semibold)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        feedback = TextView(this).apply {
            text = cameraInstructions
            textSize = 14f; setTextColor(0xDFFFFFFF.toInt()); gravity = Gravity.CENTER
            typeface = ResourcesCompat.getFont(this@ShelfCameraActivity, R.font.montserrat_regular)
            setPadding(dp(24), dp(8), dp(24), dp(20))
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(feedback)
        preview = PreviewView(this)
        root.addView(FrameLayout(this).apply {
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(24).toFloat() }
            clipToOutline = true
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(object : android.view.View(this@ShelfCameraActivity) {
                private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFE1FFEF.toInt(); style = android.graphics.Paint.Style.STROKE; strokeWidth = dp(3).toFloat()
                    strokeCap = android.graphics.Paint.Cap.ROUND
                }
                override fun onDraw(canvas: android.graphics.Canvas) {
                    val edge = dp(26).toFloat(); val length = dp(32).toFloat()
                    for (right in listOf(false, true)) for (bottom in listOf(false, true)) {
                        val x = if (right) width - edge else edge; val y = if (bottom) height - edge else edge
                        canvas.drawLine(x, y, x + if (right) -length else length, y, paint)
                        canvas.drawLine(x, y, x, y + if (bottom) -length else length, paint)
                    }
                }
            }, FrameLayout.LayoutParams(-1, -1))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
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
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(136)))
        root.addView(TextView(this).apply {
            text = if(damageMode) "Boa iluminação • Câmera firme • Problema visível"
                else if(receiptMode) "Boa iluminação • Câmera firme • Itens legíveis"
                else "Boa iluminação • Câmera firme • Gôndola inteira"; textSize = 11f
            gravity = Gravity.CENTER; setTextColor(0xBFFFFFFF.toInt())
            typeface = ResourcesCompat.getFont(this@ShelfCameraActivity, R.font.montserrat_regular)
        })
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
                feedback.text = cameraInstructions
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
