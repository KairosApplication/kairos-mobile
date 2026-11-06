package com.example.kairos.view

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.kairos.R
import com.example.kairos.model.cliente.*
import com.example.kairos.model.home.StockerPreferences
import com.example.kairos.model.network.ApiDependencies
import org.json.JSONObject
import java.util.concurrent.Executors

/** Shared infrastructure only; the customer has two form destinations, not a second navigation stack. */
abstract class CustomerActivity : AppCompatActivity() {
    internal val ui by lazy { UiKit(this) }
    protected val repository: CustomerRepository=PublishedCustomerRepository()
    protected lateinit var owner: String
    protected lateinit var body: LinearLayout
    protected lateinit var feedback: TextView
    protected val controls=mutableListOf<View>()
    protected var data: CustomerData?=null
    protected var busy=false
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var generation=0L
    private var dialog: androidx.appcompat.app.AlertDialog?=null
    abstract val pageTitle:String
    abstract fun ready(saved:Bundle?)
    protected open fun loadData():CustomerData=repository.home(owner)
    override fun onCreate(savedInstanceState:Bundle?) {
        StockerPreferences.restoreTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        ApiDependencies.initialize(applicationContext)
        owner=ApiDependencies.currentUid() ?: run { finish(); return }
        if(savedInstanceState?.getString("owner")?.let { it!=owner }==true) { finish(); return }
        val column=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(ui.color(R.color.customer_background))
        }
        val scroll=ScrollView(this).apply { isFillViewport=true; addView(column) }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view,insets ->
            val safe=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom); insets
        }
        column.addView(LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(ui.dp(24),ui.dp(8),ui.dp(24),ui.dp(26))
            background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(ui.color(R.color.home_header_top),ui.color(R.color.home_header_bottom))).apply {
                val radius=ui.dp(28).toFloat()
                cornerRadii=floatArrayOf(0f,0f,0f,0f,radius,radius,radius,radius)
            }
            addView(ui.text("Voltar",14f,true).apply {
                minHeight=ui.dp(48);gravity=android.view.Gravity.CENTER_VERTICAL
                setTextColor(android.graphics.Color.WHITE)
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.account_back,0,0,0)
                compoundDrawableTintList=android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                compoundDrawablePadding=ui.dp(6)
                setOnClickListener { if(!busy) finish() }
            },LinearLayout.LayoutParams(-2,-2))
            addView(ui.text("K A I R O S",11f,true).apply { setTextColor(0xCCFFFFFF.toInt()) },
                LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(8) })
            addView(ui.text(pageTitle,25f,true).apply { setTextColor(android.graphics.Color.WHITE) },
                LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(12) })
        },LinearLayout.LayoutParams(-1,-2))
        val page=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(ui.dp(20),ui.dp(18),ui.dp(20),ui.dp(28))
        }
        column.addView(page,LinearLayout.LayoutParams(-1,-2))
        feedback=ui.text("Carregando dados do mercado…",14f,muted=true).apply { accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
        page.addView(feedback,LinearLayout.LayoutParams(-1,-2))
        body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        page.addView(body)
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if(!busy) finish() }
        })
        execute({ loadData() }) { result ->
            data=result
            ready(savedInstanceState?.takeIf { !it.containsKey("company") || it.getLong("company")==result.companyId })
        }
    }
    protected fun add(view:View,top:Int=14) { body.addView(view,LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(top) }) }
    protected fun input(label:String,multiline:Boolean=false):EditText {
        add(ui.text(label,14f,true))
        return EditText(this).apply {
            hint=label; contentDescription=label; textSize=16f
            isSaveEnabled=false; setSingleLine(!multiline)
            typeface=androidx.core.content.res.ResourcesCompat.getFont(this@CustomerActivity,R.font.montserrat_regular)
            setTextColor(ui.color(R.color.home_ink)); setHintTextColor(ui.color(R.color.home_secondary))
            background=ui.shape(R.color.home_surface,14,true)
            setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(14))
            filters=arrayOf(android.text.InputFilter.LengthFilter(if(multiline) 500 else 4096))
        }.also { add(it,7); controls.add(it) }
    }
    protected fun <T> execute(action:()->T,success:(T)->Unit) {
        if(busy) return
        busy=true
        controls.forEach { it.isEnabled=false }
        feedback.text="Aguarde…"
        val ticket=++generation
        worker.execute {
            val result=runCatching {
                check(ApiDependencies.currentUid()==owner)
                action()
            }
            main.post {
                if(ticket!=generation || isFinishing || isDestroyed || ApiDependencies.currentUid()!=owner) return@post
                busy=false
                controls.forEach { it.isEnabled=true }
                feedback.text=""
                result.fold(success) { error ->
                    feedback.text=error.message ?: "Não foi possível concluir. Nenhuma confirmação foi recebida."
                    if(data==null) {
                        body.removeAllViews()
                        add(ui.button("Tentar novamente") {
                            execute({ loadData() }) { resultData -> data=resultData; ready(null) }
                        })
                    }
                }
            }
        }
    }
    protected fun action(label:String,primary:Boolean=false,click:()->Unit) =
        ui.button(label,primary) { if(!busy) click() }.also { controls.add(it); add(it) }
    protected fun confirmed(result:JSONObject,message:String,followUp:String="Acompanhe na Home, em Compras.") {
        val protocol=result.getString("protocol")
        require(protocol.matches(Regex("[a-fA-F0-9-]{36}")))
        // This store is a receipt of a backend success, never an offline substitute.
        val saved=getSharedPreferences("customer-confirmations",MODE_PRIVATE).edit()
            .putString("$owner:${data!!.companyId}:$protocol",JSONObject().put("protocol",protocol)
                .put("status",result.getString("status")).toString()).commit()
        setResult(RESULT_OK)
        feedback.text=if(saved) message else "$message Protocolo $protocol. Não foi possível guardar a confirmação neste aparelho."
        dialog=InfoCardDialog.show(this,1f,message,"Protocolo: $protocol\n$followUp",
            primary="Concluir",action={ finish() })
    }
    override fun onSaveInstanceState(out:Bundle) {
        if(::owner.isInitialized) out.putString("owner",owner)
        data?.companyId?.let { out.putLong("company",it) }
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() {
        generation++; main.removeCallbacksAndMessages(null); worker.shutdownNow()
        dialog?.dismiss(); super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        if(::owner.isInitialized && ApiDependencies.currentUid()!=owner) finish()
    }
}
