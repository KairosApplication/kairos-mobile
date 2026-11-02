package com.example.kairos.view

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import android.graphics.BitmapFactory
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import com.example.kairos.R
import com.example.kairos.model.scanner.*
import com.example.kairos.model.home.*
import com.example.kairos.viewmodel.*
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Capture → shared result screen → physical replenishment → explicit atomic finalization. */
class ScannerActivity : AppCompatActivity() {
    private val ui by lazy { UiKit(this) }
    private lateinit var vm: ScannerViewModel
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var resultView: StockerHomeView
    private var pendingPhoto: File? = null
    private var owner: String? = null
    private var confirmation: AlertDialog? = null
    private var renderedPhase: String? = null
    private data class Entry(var inventory: Long? = null, var quantity: String = "", var damages: String = "0")
    private val entries = linkedMapOf<Long, Entry>()
    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        if (owner != null && auth.currentUser?.uid != owner) { confirmation?.dismiss(); resultView.close(); vm.reset(); finish() }
    }
    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val file = pendingPhoto; pendingPhoto = null
        if (result.resultCode == RESULT_OK && file != null && file.exists()) vm.photo(file)
        else { file?.delete(); vm.error("Foto cancelada. Você pode continuar a reposição sem ela.") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StockerPreferences.restoreTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        vm = ViewModelProvider(this)[ScannerViewModel::class.java]
        owner = FirebaseAuth.getInstance().currentUser?.uid
        if (owner == null) { finish(); return }
        StockerPreferences(this, owner!!).applyTheme(this)
        val store = getSharedPreferences("pending-restocking", MODE_PRIVATE)
        val saved = runCatching { JSONObject(store.getString(owner, null) ?: "") }.getOrNull()
        val savedRequests = saved?.optJSONArray("requests")?.let { rows ->
            (0 until rows.length()).mapNotNull { index -> runCatching { parseRequest(rows.getJSONObject(index)) }.getOrNull() }
        }.orEmpty()
        if (savedRequests.isNotEmpty()) vm.recoverBatch(owner!!, savedRequests)
        else if (saved?.has("commandId") == true) runCatching { vm.recover(owner!!, parseRequest(saved)) }
        val entryData = savedInstanceState?.getString("entries") ?: saved?.optJSONObject("entries")?.toString()
        restoreEntries(entryData)
        val directory = File(cacheDir, "scanner").apply { mkdirs() }
        if (savedInstanceState == null) directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        if (savedInstanceState != null && savedInstanceState.getString("owner") == owner) {
            savedInstanceState.getString("pendingPhoto")?.takeIf { it.matches(Regex("[a-f0-9-]+\\.jpg")) }
                ?.let { pendingPhoto = File(directory, it) }
        }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        resultView = StockerHomeView(this, onRetry = {}, onLogout = {},
            onRestock = { records -> vm.startSelected(records.mapNotNull { it.productId }) },
            onResultBack = { vm.reviewPhoto() }).apply { visibility = View.GONE }
        setContentView(FrameLayout(this).apply {
            addView(scroll, FrameLayout.LayoutParams(-1,-1))
            addView(resultView, FrameLayout.LayoutParams(-1,-1))
        })
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom); insets
        }
        FirebaseAuth.getInstance().addAuthStateListener(authListener)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (vm.state.value?.loading == true) return
                if (vm.state.value?.step == ScannerStep.RESULT) vm.reviewPhoto() else leave()
            }
        })
        vm.state.observe(this, ::render)
        val savedTargets = saved?.optJSONArray("sessionProducts")?.let { rows -> (0 until rows.length()).map { rows.getLong(it) } }.orEmpty()
        val initialShelf = saved?.optLong("sessionShelf",0)?.takeIf { it > 0 }
            ?: intent.getLongExtra("restockShelfId",0).takeIf { it > 0 }
        val targets = if (savedTargets.isNotEmpty()) savedTargets else intent.getLongArrayExtra("restockProductIds")?.toList()
            ?: listOfNotNull(intent.getLongExtra("restockProductId",0).takeIf { it > 0 })
        vm.account(owner!!, initialShelf, initialProducts = targets)
    }

    private fun parseRequest(row: JSONObject) = ConfirmedRestocking(row.getString("commandId"),row.getLong("shelfId"),
        row.getLong("inventoryId"),row.getLong("productId"),row.getInt("quantity"),row.getInt("damages"))
    private fun requestJson(request: ConfirmedRestocking) = JSONObject().put("commandId",request.commandId).put("shelfId",request.shelfId)
        .put("inventoryId",request.inventoryId).put("productId",request.productId).put("quantity",request.quantity).put("damages",request.damages)
    private fun entryJson() = JSONObject().also { root -> entries.forEach { (id,value) ->
        root.put(id.toString(),JSONObject().put("inventory",value.inventory).put("quantity",value.quantity).put("damages",value.damages))
    } }
    private fun restoreEntries(value: String?) {
        runCatching { JSONObject(value ?: "").let { data -> data.keys().forEach { key ->
            val row=data.getJSONObject(key)
            entries[key.toLong()]=Entry(if(row.isNull("inventory")) null else row.getLong("inventory"),row.optString("quantity"),row.optString("damages","0"))
        } } }
    }
    private fun persist(state: ScannerState): Boolean {
        val store=getSharedPreferences("pending-restocking",MODE_PRIVATE)
        if(state.step==ScannerStep.COMPLETE) return store.edit().remove(owner).commit()
        if(state.selectedProductIds.isEmpty()) return true
        return store.edit().putString(owner,JSONObject().put("sessionShelf",state.shelfId)
            .put("sessionProducts",JSONArray(state.selectedProductIds)).put("entries",entryJson())
            .put("requests",JSONArray().also { rows -> state.pendingBatch.forEach { rows.put(requestJson(it)) } }).toString()).commit()
    }
    override fun onPause() { if(::vm.isInitialized) vm.state.value?.let(::persist); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("owner",owner); outState.putString("pendingPhoto",pendingPhoto?.name)
        outState.putString("entries",entryJson().toString()); super.onSaveInstanceState(outState)
    }
    private fun label(text: String, title: Boolean=false) = ui.text(text,if(title) 25f else 14f,title,!title).apply {
        setPadding(0,ui.dp(10),0,ui.dp(12))
    }
    private fun button(text: String, primary: Boolean=true, enabled: Boolean=true, action:()->Unit) {
        body.addView(ui.button(text,primary,action).apply { isEnabled=enabled; alpha=if(enabled) 1f else .5f },
            LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(12) })
    }
    private fun photoPreview(file: File) {
        body.addView(ImageView(this).apply {
            setImageBitmap(BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=8 }))
            scaleType=ImageView.ScaleType.CENTER_CROP; background=ui.shape(); clipToOutline=true
        },LinearLayout.LayoutParams(-1,ui.dp(220)).apply { topMargin=ui.dp(12); bottomMargin=ui.dp(12) })
    }
    private fun choices(parent: LinearLayout, title: String, values: List<CatalogItem>, selected: Long?, changed:(Long?)->Unit) {
        parent.addView(ui.text(title,12f,muted=true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(16) })
        val all=listOf(CatalogItem(0,"Selecione o estoque"))+values
        parent.addView(Spinner(this).apply {
            minimumHeight=ui.dp(54); background=ui.shape(border=true)
            adapter=object:ArrayAdapter<CatalogItem>(this@ScannerActivity,android.R.layout.simple_spinner_dropdown_item,all) {
                override fun getView(position:Int,convertView:View?,parent:android.view.ViewGroup):View =
                    (super.getView(position,convertView,parent) as TextView).apply { setTextColor(ui.color(R.color.home_ink)); typeface=ui.text("").typeface; textSize=14f }
                override fun getDropDownView(position:Int,convertView:View?,parent:android.view.ViewGroup):View =
                    (super.getDropDownView(position,convertView,parent) as TextView).apply {
                        setTextColor(ui.color(R.color.home_ink)); setBackgroundColor(ui.color(R.color.home_surface))
                        setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16))
                    }
            }
            setSelection(all.indexOfFirst { it.id==selected }.coerceAtLeast(0))
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent:AdapterView<*>?)=Unit
                override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { changed(all[position].id.takeIf { it>0 }) }
            }
        },LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(6) })
    }
    private fun number(parent:LinearLayout,title:String,value:String,changed:(String)->Unit) {
        parent.addView(ui.text(title,12f,muted=true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(14) })
        parent.addView(EditText(this).apply {
            contentDescription=title; inputType=InputType.TYPE_CLASS_NUMBER; isSaveEnabled=false
            typeface=ui.text("").typeface; textSize=16f; setTextColor(ui.color(R.color.home_ink))
            background=ui.shape(border=true); setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14)); setText(value)
            filters=arrayOf(android.text.InputFilter.LengthFilter(8))
            addTextChangedListener(object:android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
                override fun afterTextChanged(s:android.text.Editable?)=Unit
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { changed(s.toString()) }
            })
        },LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(6) })
    }
    private fun takePhoto() {
        persist(vm.state.value!!)
        val file=File(File(cacheDir,"scanner"),"${UUID.randomUUID()}.jpg")
        pendingPhoto?.delete(); pendingPhoto=file
        capture.launch(android.content.Intent(this,ShelfCameraActivity::class.java).putExtra("filename",file.name))
        UiMotion.open(this)
    }
    private fun analysis(state:ScannerState):ShelfAnalysis {
        val shelf=state.shelfId!!; val result=state.result!!; val catalog=state.catalog!!
        val location=catalog.shelves.first { it.id==shelf }
        val pending=state.marketData?.restockingAlerts?.filter { it.shelfCode==shelf.toString() }.orEmpty()
        val observed=result.observations.filter { !it.unrecognized && it.candidates.size==1 }.mapNotNull { observation ->
            catalog.products.firstOrNull { it.id==observation.candidates.single() }?.let { product ->
                RestockingRecord("photo:${product.id}",product.label,"",shelf.toString(),state.analyzedAt ?: java.time.Instant.now(),
                    RestockingStatus.OBSERVED,productId=product.id)
            }
        }.distinctBy { it.productId }.filter { candidate -> pending.none { it.productId==candidate.productId } }
        val notes=result.warnings + result.observations.map {
            "${it.name} ${it.brand}".trim()+" · "+(it.visibleQuantity?.let { q -> "$q unidades visíveis (estimativa)" } ?: "quantidade incerta")
        } + listOf("Confira os produtos antes de selecionar. Os alertas de estoque são os registros do mercado.")
        return ShelfAnalysis(shelf.toString(),location.aisle ?: "—",location.sector ?: "Não informado",
            result.observations.size,pending+observed,"photo-result:$shelf",fromPhoto=true,observations=notes)
    }
    private fun render(state:ScannerState) {
        if(FirebaseAuth.getInstance().currentUser?.uid!=owner) return
        if(state.step==ScannerStep.RESULT && state.result!=null && !state.loading && state.pendingBatch.isEmpty()) {
            scroll.visibility=View.GONE; resultView.visibility=View.VISIBLE
            window.statusBarColor=ui.color(R.color.home_header_top)
            resultView.showStandaloneResult(owner!!,FirebaseAuth.getInstance().currentUser?.email.orEmpty(),analysis(state))
            return
        }
        resultView.visibility=View.GONE; scroll.visibility=View.VISIBLE
        body.removeAllViews(); body.setPadding(ui.dp(20),ui.dp(16),ui.dp(20),ui.dp(28))
        body.setBackgroundColor(ui.color(R.color.home_background_middle))
        window.statusBarColor=ui.color(R.color.home_background_middle); window.navigationBarColor=ui.color(R.color.home_background_middle)
        val light=resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK != android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window,window.decorView).apply { isAppearanceLightStatusBars=light; isAppearanceLightNavigationBars=light }
        val phase="${state.step}:${state.loading}"
        if(renderedPhase!=phase) { UiMotion.reveal(body); renderedPhase=phase; scroll.scrollTo(0,0) }
        if(state.loading) {
            body.addView(AnalysisProgressView(this,state.pendingBatch.isNotEmpty()),LinearLayout.LayoutParams(-1,resources.displayMetrics.heightPixels-ui.dp(80)))
            return
        }
        body.addView(ui.button("‹  Voltar",false) { leave() },LinearLayout.LayoutParams(-2,ui.dp(48)))
        body.addView(label(when(state.step) {
            ScannerStep.IN_PROGRESS,ScannerStep.CONFIRMING -> "Reposição em andamento"
            ScannerStep.COMPLETE -> "Reposição finalizada"
            ScannerStep.PREVIEW -> "Confira a foto"
            else -> "Verificar gôndola"
        },true))
        if(state.error.isNotBlank()) body.addView(ui.notice(R.drawable.home_warning,"Vamos conferir",state.error).apply {
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        })
        if(state.step==ScannerStep.COMPLETE) {
            setResult(RESULT_OK)
            persist(state)
            body.addView(ui.empty(R.drawable.home_check,"Tudo registrado","A movimentação foi confirmada no mercado. O histórico e os alertas serão atualizados."))
            button("Voltar ao início") { setResult(RESULT_OK); finish() }
            return
        }
        val catalog=state.catalog
        if(catalog==null) { button("Carregar dados novamente") { vm.retryCatalog() }; return }
        if(state.step in setOf(ScannerStep.IN_PROGRESS,ScannerStep.CONFIRMING)) {
            renderOngoing(state,catalog); return
        }
        body.addView(ui.empty(R.drawable.home_camera,"Um novo olhar para o estoque","Escolha a gôndola e tire uma foto nítida. Depois, confira o resultado da análise."))
        choices(body,"Gôndola",catalog.shelves,state.shelfId,vm::shelf)
        if(state.photo!=null) {
            photoPreview(state.photo)
            button("Analisar foto",enabled=state.shelfId!=null) { vm.analyze() }
            button("Fotografar novamente",false) { takePhoto() }
        } else button("Abrir câmera",enabled=state.shelfId!=null) { takePhoto() }
        body.addView(label("A imagem será enviada ao Groq para análise, sem armazenamento permanente no Kairos."))
    }
    private fun renderOngoing(state:ScannerState,catalog:StockCatalog) {
        val count=state.selectedProductIds.size
        body.addView(label("Gôndola ${state.shelfId} · $count "+if(count==1) "produto selecionado" else "produtos selecionados"))
        body.addView(ui.text("Faça a reposição na gôndola. Ao terminar, informe as quantidades abaixo e toque em Finalizar reposição.",15f,muted=true))
        if(state.pendingBatch.isNotEmpty()) {
            state.pendingBatch.forEach { request ->
                body.addView(ui.notice(R.drawable.home_check,catalog.products.firstOrNull { it.id==request.productId }?.label.orEmpty(),
                    "${request.quantity} unidades · ${request.damages} avarias"),LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(12) })
            }
            body.addView(ui.notice(R.drawable.home_warning,"Confirmação pendente","Tente novamente para conferir o registro com o mercado. Os mesmos dados serão usados, sem duplicar a reposição."),
                LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(16) })
            button("Tentar finalizar novamente") { vm.finalizeBatch(state.pendingBatch) }
            return
        }
        state.selectedProductIds.forEach { id ->
            val available=catalog.balances.filter { it.productId==id && it.quantity>0 }.map { it.inventoryId }.toSet()
            val origins=if(catalog.balances.isEmpty()) catalog.inventories else catalog.inventories.filter { it.id in available }
            val entry=entries.getOrPut(id) { Entry(origins.singleOrNull()?.id) }
            val card=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL; background=ui.shape(border=true); setPadding(ui.dp(18),ui.dp(18),ui.dp(18),ui.dp(18))
                addView(ui.text(catalog.products.first { it.id==id }.label,17f,true))
            }
            choices(card,"Estoque de origem",origins,entry.inventory) { entry.inventory=it }
            number(card,"Unidades retiradas do estoque",entry.quantity) { entry.quantity=it }
            number(card,"Avarias incluídas no total",entry.damages) { entry.damages=it }
            body.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(18) })
        }
        state.photo?.let(::photoPreview)
        button("Finalizar reposição") { reviewFinalization(state) }
        button(if(state.photo==null) "Adicionar foto de conferência (opcional)" else "Trocar foto de conferência",false) { takePhoto() }
        button("Cancelar reposição",false) {
            confirmation=InfoCardDialog.confirm(this,"Cancelar reposição?",
                "Os itens desta reposição serão descartados do aparelho. Se você já moveu produtos, confira os saldos físicos antes de cancelar.",
                "Cancelar reposição","Continuar repondo",R.drawable.home_warning) {
                    getSharedPreferences("pending-restocking",MODE_PRIVATE).edit().remove(owner).commit(); vm.reset(); finish()
                }
        }
        persist(state)
    }
    private fun reviewFinalization(state:ScannerState) {
        try {
            val requests=state.selectedProductIds.map { id ->
                val entry=entries[id]!!
                ConfirmedRestocking(UUID.randomUUID().toString(),state.shelfId!!,entry.inventory ?: 0,id,
                    entry.quantity.toIntOrNull() ?: 0,entry.damages.toIntOrNull() ?: -1).also { it.validate() }
            }
            requests.forEach { request -> state.catalog!!.balances.firstOrNull {
                it.inventoryId==request.inventoryId && it.productId==request.productId
            }?.let { require(request.quantity<=it.quantity) { "Saldo insuficiente no estoque de origem." } } }
            val description=requests.joinToString("\n\n") { request ->
                state.catalog!!.products.first { it.id==request.productId }.label+"\n${request.quantity-request.damages} unidades para a gôndola · ${request.damages} avarias"
            }
            confirmation=InfoCardDialog.confirm(this,"Tudo pronto na gôndola?",
                "Confira as quantidades abaixo. Ao confirmar, o estoque e o histórico do mercado serão atualizados.",
                "Confirmar e finalizar","Revisar quantidades",details=description) {
                    if(persist(state.copy(pendingBatch=requests))) vm.finalizeBatch(requests)
                    else vm.error("Não foi possível guardar a confirmação neste aparelho. Tente novamente.")
                }
        } catch(_:IllegalArgumentException) { vm.error("Informe o estoque e uma quantidade positiva para cada produto. As avarias devem estar entre zero e o total.") }
    }
    private fun leave() {
        val state=vm.state.value ?: return
        if(state.loading) return
        if(state.step in setOf(ScannerStep.IN_PROGRESS,ScannerStep.CONFIRMING)) {
            if(!persist(state)) {
                vm.error("Não foi possível salvar o andamento neste aparelho. Tente novamente antes de sair.")
                return
            }
            val pending=state.pendingBatch.isNotEmpty()
            confirmation=InfoCardDialog.confirm(this,if(pending) "Confirmação em aberto" else "Continue quando quiser",
                if(pending) "Os dados enviados ficam salvos neste aparelho. Ao voltar, tente novamente para conferir se o mercado recebeu a confirmação."
                else "Seus produtos e quantidades ficam salvos neste aparelho. Ao voltar, você retoma de onde parou.",
                "Salvar e sair","Continuar repondo",R.drawable.home_clock,
                details=if(pending) "Uma tentativa já foi enviada. Confira o registro antes de repetir a reposição física."
                else "O estoque só será atualizado quando você finalizar a reposição.") { finish() }
        } else finish()
    }
    override fun finish() { super.finish(); UiMotion.close(this) }
    override fun onDestroy() {
        confirmation?.dismiss()
        if(::resultView.isInitialized) resultView.close()
        FirebaseAuth.getInstance().removeAuthStateListener(authListener)
        if(isFinishing) pendingPhoto?.delete()
        super.onDestroy()
    }
}
