package com.example.kairos.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.kairos.R
import com.example.kairos.model.auth.*
import com.example.kairos.model.cliente.*
import com.example.kairos.model.home.StockerPreferences
import com.example.kairos.view.settings.*
import com.example.kairos.viewmodel.CustomerUiState
import java.text.NumberFormat
import java.util.Locale

class CustomerHomeView(context:Context,private val scan:()->Unit,private val report:()->Unit,
    private val logout:()->Unit,private val retry:()->Unit,private val favorite:(CustomerProduct)->Unit,
    private val onAccountEdit:((AccountEditRequest)->Unit)?=null):LinearLayout(context) {
    private val ui=UiKit(context)
    private val title=ui.text("Olá, cliente!",26f,true)
    private val subtitle=ui.text("Seu dia a dia no mercado, mais simples.",13f)
    private val feedback=ui.text("",13f,muted=true)
    private val body=LinearLayout(context).apply { orientation=VERTICAL }
    private val settingsBody=LinearLayout(context).apply { orientation=VERTICAL }
    private val actions=LinearLayout(context).apply { orientation=VERTICAL }
    private val scroll=ScrollView(context).apply { isFillViewport=true;clipToPadding=false }
    private val content=LinearLayout(context).apply { orientation=VERTICAL;setPadding(ui.dp(20),ui.dp(20),ui.dp(20),ui.dp(24)) }
    private var state=CustomerUiState()
    private var user:SignedInUser?=null
    private var mode="Início"
    private var settingsDestination=SettingsDestination.ROOT
    private var pendingRestore:Bundle?=null
    private var accountBusy=false
    private var accountMessage=""
    private var dialog:androidx.appcompat.app.AlertDialog?=null
    private var accountEditor:AccountEditorDialog.Editor?=null
    private val navigation=linkedMapOf<String,Button>()
    private val back=ui.text("Voltar",14f,true).apply {
        id=R.id.customer_settings_back
        gravity=Gravity.CENTER_VERTICAL
        minHeight=ui.dp(48)
        setTextColor(Color.WHITE)
        setCompoundDrawablesWithIntrinsicBounds(R.drawable.account_back,0,0,0)
        compoundDrawableTintList=ColorStateList.valueOf(Color.WHITE)
        compoundDrawablePadding=ui.dp(6)
        contentDescription="Voltar nas configurações"
        setOnClickListener { handleBack() }
    }
    private val search=EditText(context).apply {
        hint="Buscar produto ou marca";isSingleLine=true;textSize=14f
        typeface=androidx.core.content.res.ResourcesCompat.getFont(context,R.font.montserrat_medium)
        setTextColor(ui.color(R.color.home_ink));setHintTextColor(ui.color(R.color.home_secondary))
        background=null
        setPadding(ui.dp(12),ui.dp(14),ui.dp(12),ui.dp(14))
        contentDescription="Buscar produtos";isSaveEnabled=false
    }
    private val searchBox=LinearLayout(context).apply {
        gravity=Gravity.CENTER_VERTICAL
        background=ui.shape(radius=16,border=true)
        setPadding(ui.dp(16),0,ui.dp(4),0)
        addView(icon(R.drawable.menu_search,ui.color(R.color.home_secondary)),LayoutParams(ui.dp(21),ui.dp(21)))
        addView(search,LayoutParams(0,-2,1f))
    }
    private val scanButton=ui.button("Escanear nota fiscal",true,scan)
    private val reportButton=ui.button("Reportar avaria",false,report)
    private val reportNotice=ui.text("O envio de relatos estará disponível em breve.",12f,muted=true)
    private val refresh=ui.button("Atualizar",false,retry)

    init {
        orientation=VERTICAL
        setBackgroundColor(ui.color(R.color.customer_background))
        val column=LinearLayout(context).apply { orientation=VERTICAL }
        val header=LinearLayout(context).apply {
            orientation=VERTICAL
            setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(28))
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(ui.color(R.color.home_header_top),ui.color(R.color.home_header_bottom))).apply {
                val radius=ui.dp(28).toFloat()
                cornerRadii=floatArrayOf(0f,0f,0f,0f,radius,radius,radius,radius)
            }
        }
        header.addView(back,LayoutParams(-2,-2))
        header.addView(ui.text("K A I R O S",11f,true).apply { setTextColor(0xCCFFFFFF.toInt()) },
            LayoutParams(-1,-2).apply { topMargin=ui.dp(12) })
        title.setTextColor(Color.WHITE);subtitle.setTextColor(0xE6FFFFFF.toInt())
        header.addView(title,LayoutParams(-1,-2).apply { topMargin=ui.dp(12) })
        header.addView(subtitle,LayoutParams(-1,-2).apply { topMargin=ui.dp(8) })
        column.addView(header,LayoutParams(-1,-2))
        column.addView(content,LayoutParams(-1,-2))
        column.addView(settingsBody,LayoutParams(-1,-2))
        scroll.addView(column)
        addView(scroll,LayoutParams(-1,0,1f))
        val hero=LinearLayout(context).apply {
            orientation=VERTICAL;background=ui.shape(radius=22,border=true)
            setPadding(ui.dp(20),ui.dp(20),ui.dp(20),ui.dp(20))
            addView(LinearLayout(context).apply {
                gravity=Gravity.TOP
                addView(LinearLayout(context).apply {
                    orientation=VERTICAL
                    addView(ui.text("SUA NOTA FISCAL",10f,true,true))
                    addView(ui.text("Confira os itens\nda sua nota",23f,true),LayoutParams(-1,-2).apply { topMargin=ui.dp(10) })
                    addView(ui.text("Fotografe e revise a leitura com IA.",13f,muted=true),LayoutParams(-1,-2).apply { topMargin=ui.dp(8) })
                },LayoutParams(0,-2,1f))
                addView(FrameLayout(context).apply {
                    background=ui.shape(R.color.home_subtle_tile,16)
                    addView(icon(R.drawable.customer_receipt),FrameLayout.LayoutParams(ui.dp(28),ui.dp(28),Gravity.CENTER))
                },LayoutParams(ui.dp(52),ui.dp(52)).apply { marginStart=ui.dp(12) })
            })
            addView(scanButton,LayoutParams(-1,-2).apply { topMargin=ui.dp(20) })
        }
        actions.addView(hero,LayoutParams(-1,-2))
        actions.addView(reportButton,LayoutParams(-1,-2).apply { topMargin=ui.dp(12) })
        actions.addView(reportNotice,LayoutParams(-1,-2).apply { topMargin=ui.dp(8);bottomMargin=ui.dp(20) })
        content.addView(actions,LayoutParams(-1,-2))
        content.addView(searchBox,LayoutParams(-1,-2))
        feedback.accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        content.addView(feedback,LayoutParams(-1,-2).apply { topMargin=ui.dp(12) })
        content.addView(body,LayoutParams(-1,-2))
        content.addView(refresh,LayoutParams(-1,-2).apply { topMargin=ui.dp(20) })
        addView(View(context).apply { setBackgroundColor(ui.color(R.color.home_border)) },LayoutParams(-1,ui.dp(1)))
        val nav=LinearLayout(context).apply {
            setPadding(ui.dp(10),ui.dp(7),ui.dp(10),ui.dp(8))
            setBackgroundColor(ui.color(R.color.home_surface))
        }
        val items=listOf(
            Triple("Início",R.drawable.menu_house_active,R.id.customer_nav_home),
            Triple("Favoritos",R.drawable.customer_favorite,R.id.customer_nav_favorites),
            Triple("Compras",R.drawable.customer_receipt,R.id.customer_nav_purchases),
            Triple("Ajustes",R.drawable.menu_settings_active,R.id.customer_nav_settings))
        items.forEach { (label,resource,viewId) ->
            val button=ui.button(label) { navigate(label) }.apply {
                id=viewId;textSize=11.5f
                minHeight=ui.dp(62);minimumHeight=ui.dp(62)
                setPadding(ui.dp(2),ui.dp(8),ui.dp(2),ui.dp(8))
                maxLines=1
                val drawable=androidx.appcompat.content.res.AppCompatResources.getDrawable(context,resource)!!.mutate()
                drawable.setBounds(0,0,ui.dp(23),ui.dp(23))
                setCompoundDrawables(null,drawable,null,null)
                compoundDrawablePadding=ui.dp(5)
                contentDescription=label
            }
            navigation[label]=button
            nav.addView(button,LayoutParams(0,-2,1f).apply { marginStart=ui.dp(3);marginEnd=ui.dp(3) })
        }
        addView(nav,LayoutParams(-1,-2))
        ViewCompat.setOnApplyWindowInsetsListener(this) { view,insets ->
            val safe=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom);insets
        }
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { render(false) }
            override fun afterTextChanged(s:Editable?) {}
        })
        render()
    }

    private fun icon(resource:Int,tint:Int=ui.color(R.color.home_icon_accent))=ImageView(context).apply {
        setImageResource(resource);imageTintList=ColorStateList.valueOf(tint)
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun navigate(destination:String) {
        if(accountBusy) return
        close()
        mode=destination
        if(destination=="Ajustes") settingsDestination=SettingsDestination.ROOT
        render();scroll.scrollTo(0,0)
    }
    fun session(value:SignedInUser?,busy:Boolean=false,message:String="",operation:AccountOperation?=null,succeeded:Boolean?=null) {
        val changed=user!=value || accountBusy!=busy || accountMessage!=message
        if(user!=null && user?.uid!=value?.uid) {
            close();mode="Início";settingsDestination=SettingsDestination.ROOT;state=CustomerUiState()
            search.setText("")
        }
        user=value;accountBusy=busy;accountMessage=message
        accountEditor?.bind(value,busy,message,operation,succeeded)
        applyRestore()
        if(changed) render()
    }
    fun bind(value:CustomerUiState) {
        state=value
        render(settingsDestination==SettingsDestination.PROFILE || settingsBody.childCount==0)
    }
    fun reset() {
        close();state=CustomerUiState();user=null;mode="Início";settingsDestination=SettingsDestination.ROOT
        pendingRestore=null;accountBusy=false;accountMessage="";settingsBody.removeAllViews()
        search.setText("");render()
    }
    fun close() { dialog?.dismiss();dialog=null;accountEditor=null }
    fun save()=Bundle().apply {
        putString("owner",user?.uid);putString("mode",mode);putString("settings",settingsDestination.name)
        putString("query",search.text.toString())
    }
    fun restore(saved:Bundle?) { pendingRestore=saved;applyRestore();render() }
    private fun applyRestore() {
        val saved=pendingRestore ?: return
        val owner=user?.uid ?: return
        pendingRestore=null
        if(saved.getString("owner")!=owner) return
        mode=saved.getString("mode")?.takeIf { it in navigation.keys } ?: "Início"
        settingsDestination=runCatching { SettingsDestination.valueOf(saved.getString("settings").orEmpty()) }.getOrDefault(SettingsDestination.ROOT)
        if(settingsDestination==SettingsDestination.SUPPORT) settingsDestination=SettingsDestination.HELP
        search.setText(saved.getString("query").orEmpty())
    }
    fun handleBack():Boolean {
        if(accountBusy) return true
        if(mode=="Ajustes" && settingsDestination!=SettingsDestination.ROOT) {
            settingsDestination=settingsDestination.parent
            render();scroll.scrollTo(0,0);return true
        }
        if(mode!="Início") { navigate("Início");return true }
        return false
    }
    private fun add(view:View,top:Int=14) { body.addView(view,LayoutParams(-1,-2).apply { topMargin=ui.dp(top) }) }
    private fun money(value:java.math.BigDecimal?)=value?.let {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(it)
    } ?: "Preço não informado"
    private fun render(refreshSettings:Boolean=true) {
        val settings=mode=="Ajustes"
        content.visibility=if(settings) GONE else VISIBLE
        settingsBody.visibility=if(settings) VISIBLE else GONE
        actions.visibility=if(mode=="Início") VISIBLE else GONE
        searchBox.visibility=if(mode in listOf("Início","Favoritos")) VISIBLE else GONE
        back.visibility=if(settings && settingsDestination!=SettingsDestination.ROOT) VISIBLE else GONE
        title.text=when(mode) {
            "Ajustes" -> if(settingsDestination==SettingsDestination.ROOT) "Configurações" else context.getString(settingsDestination.title)
            "Favoritos" -> "Seus favoritos"
            "Compras" -> "Suas compras"
            else -> "Olá, ${user?.name?.substringBefore(' ')?.ifBlank { "cliente" } ?: "cliente"}!"
        }
        subtitle.text=when(mode) {
            "Ajustes" -> "Sua conta, do seu jeito."
            "Favoritos" -> "Os produtos que você quer ter por perto."
            "Compras" -> "Suas notas e seus registros em um só lugar."
            else -> user?.business?.companyName?.takeIf { it.isNotBlank() } ?: "Seu dia a dia no mercado, mais simples."
        }
        navigation.forEach { (label,button) ->
            val selected=label==mode
            button.isSelected=selected;button.isEnabled=!accountBusy
            button.background=ui.shape(if(selected) R.color.home_subtle_tile else R.color.home_surface,16)
            button.setTextColor(ui.color(if(selected) R.color.home_ink else R.color.home_secondary))
            button.compoundDrawableTintList=ColorStateList.valueOf(ui.color(if(selected) R.color.home_icon_accent else R.color.home_secondary))
        }
        if(settings) {
            if(refreshSettings || settingsBody.childCount==0) renderSettings()
            return
        }
        body.removeAllViews()
        feedback.text=if(state.loading) "Atualizando seu mercado…" else state.message
        feedback.visibility=if(feedback.text.isBlank()) GONE else VISIBLE
        refresh.isEnabled=!state.loading && !accountBusy
        val data=state.data
        scanButton.isEnabled=!state.loading && !accountBusy && data!=null && ("receipts" !in data.unavailableFeatures || data.receiptPhotoAvailable)
        reportButton.isEnabled=!state.loading && !accountBusy && data!=null && "reports" !in data.unavailableFeatures
        reportNotice.visibility=if(data!=null && "reports" in data.unavailableFeatures) VISIBLE else GONE
        if(data==null) {
            add(ui.empty(R.drawable.menu_search,if(state.loading) "Preparando seu mercado" else "Não foi possível carregar",
                if(state.loading) "Estamos buscando os produtos para você." else "Toque em Atualizar para tentar novamente."))
            return
        }
        if(data.catalogNotice.isNotBlank()) add(ui.notice(R.drawable.settings_info,"Sobre o catálogo",data.catalogNotice))
        if(data.catalogTruncated) add(ui.text("Exibindo ${data.products.size} produtos do catálogo. Use a busca para encontrar um item.",12f,muted=true))
        if(mode=="Compras") { renderPurchases(data);return }
        val query=search.text.toString().trim()
        val products=data.products.filter { it.name.contains(query,true) || it.brand.contains(query,true) }
        if(mode=="Favoritos") {
            add(ui.text("Favoritos salvos neste aparelho, separados por conta.",12f,muted=true))
            section("Seus favoritos",products.filter { it.favorite },true)
        } else if(query.isNotEmpty()) section("Resultados da busca",products)
        else {
            if("recommendations" !in data.unavailableFeatures && data.recommendedIds.isNotEmpty())
                section("Para você",products.filter { it.id in data.recommendedIds })
            section("Produtos do mercado",products)
        }
    }
    private fun section(heading:String,products:List<CustomerProduct>,favorites:Boolean=false) {
        add(LinearLayout(context).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(ui.text(heading,20f,true).apply { ViewCompat.setAccessibilityHeading(this,true) },LayoutParams(0,-2,1f))
            addView(ui.text(products.size.toString(),12f,true,true).apply {
                background=ui.shape(R.color.home_subtle_tile,10)
                setPadding(ui.dp(10),ui.dp(6),ui.dp(10),ui.dp(6))
            })
        },22)
        if(products.isEmpty()) {
            add(ui.empty(if(favorites) R.drawable.customer_favorite else R.drawable.menu_search,
                if(favorites) "Seus favoritos começam aqui" else "Nenhum produto encontrado",
                if(favorites) "Toque em Favoritar nos produtos que você gosta." else "Tente buscar por outro produto ou marca."))
            return
        }
        if(products.size>40) add(ui.text("Exibindo 40 de ${products.size} produtos. Refine sua busca para encontrar outros itens.",12f,muted=true),8)
        products.take(40).forEach { product ->
            val card=LinearLayout(context).apply {
                orientation=VERTICAL;background=ui.shape(radius=20,border=true)
                setPadding(ui.dp(18),ui.dp(18),ui.dp(18),ui.dp(16))
                addView(ui.text(product.name,17f,true))
                if(product.brand.isNotBlank()) addView(ui.text(product.brand,12f,muted=true),LayoutParams(-1,-2).apply { topMargin=ui.dp(5) })
                addView(ui.text(money(product.price),22f,true).apply { setTextColor(ui.color(R.color.home_icon_accent)) },
                    LayoutParams(-1,-2).apply { topMargin=ui.dp(16) })
                addView(View(context).apply { setBackgroundColor(ui.color(R.color.home_border)) },
                    LayoutParams(-1,ui.dp(1)).apply { topMargin=ui.dp(16);bottomMargin=ui.dp(12) })
                addView(LinearLayout(context).apply {
                    addView(ui.button("Ver detalhes") { details(product) },LayoutParams(0,-2,1f).apply { marginEnd=ui.dp(6) })
                    addView(ui.button(if(product.favorite) "Remover favorito" else "Favoritar") { if(!state.loading && !accountBusy) favorite(product) }.apply {
                        isEnabled=!state.loading && !accountBusy && "favorites" !in (state.data?.unavailableFeatures ?: emptySet())
                        textSize=12f
                    },LayoutParams(0,-2,1f).apply { marginStart=ui.dp(6) })
                })
            }
            add(card,12)
        }
    }
    private fun renderPurchases(data:CustomerData) {
        if("receipts" in data.unavailableFeatures) {
            add(ui.empty(R.drawable.home_clock,"Seu histórico estará aqui",
                "O histórico de compras ainda não está disponível. Você já pode fotografar e conferir os itens da sua nota."))
            if(data.receiptPhotoAvailable) add(ui.button("Ler uma nota fiscal",true,scan))
        } else {
        add(ui.text("Histórico de compras",22f,true))
        if(data.receipts.isEmpty()) add(ui.empty(R.drawable.home_clock,"Seu histórico começa aqui","Suas compras confirmadas aparecerão nesta tela."))
        data.receipts.forEach { receipt ->
            add(ui.button("Compra · ${receipt.optString("confirmed_at").substringBefore(' ')}\n${money(receipt.opt("total")?.toString()?.toBigDecimalOrNull())}") {
                val items=receipt.getJSONArray("items")
                val lines=(0 until items.length()).joinToString("\n") { index -> items.getJSONObject(index).let {
                    "${it.getString("name")} · ${it.getInt("quantity")} un. · ${money(it.get("amount").toString().toBigDecimalOrNull())}" } }
                showInfo("Compra confirmada","Itens informados e confirmados pelo cliente.\n\n$lines\n\nProtocolo: ${receipt.getString("id")}")
            })
        }
        }
        if("reports" !in data.unavailableFeatures) {
            add(ui.text("Seus reportes",20f,true),24)
            if(data.reports.isEmpty()) add(ui.text("Você ainda não enviou reportes.",14f,muted=true))
            data.reports.forEach { row ->
                add(ui.button("${row.getString("name")} · ${status(row.getString("status"))}") {
                    showInfo("Acompanhamento da avaria","Produto: ${row.getString("name")}\nTipo: ${problem(row.getString("problem_type"))}\nDescrição: ${row.getString("description")}\nStatus: ${status(row.getString("status"))}\n\nProtocolo: ${row.getString("id")}")
                })
            }
        }
    }
    private fun details(product:CustomerProduct) {
        val places=state.data?.locations?.filter { it.productId==product.id }.orEmpty()
        val location=if(state.data?.unavailableFeatures?.contains("locations")==true) "A localização ainda não está disponível."
            else if(places.isEmpty()) "Localização não informada." else places.joinToString("\n")
        showInfo(product.name,listOf(product.brand,money(product.price),location).filter { it.isNotBlank() }.joinToString("\n\n"))
    }
    private fun openSettings(destination:SettingsDestination) {
        if(accountBusy) return
        close();settingsDestination=if(destination==SettingsDestination.SUPPORT) SettingsDestination.HELP else destination
        render();scroll.scrollTo(0,0)
    }
    private fun renderSettings() {
        settingsBody.removeAllViews()
        val activity=context as AppCompatActivity
        when(settingsDestination) {
            SettingsDestination.ROOT -> {
                settingsBody.addView(SettingsMenuView(context,1f,accountBusy,::openSettings,user?.business?.apiAuthenticated==true),LayoutParams(-1,-2))
                settingsBody.addView(ui.button("Sair da conta",false) { if(!accountBusy) logout() }.apply {
                    isEnabled=!accountBusy;setTextColor(ui.color(R.color.home_danger))
                },LayoutParams(-1,-2).apply { setMargins(ui.dp(20),0,ui.dp(20),ui.dp(24)) })
            }
            SettingsDestination.PROFILE -> {
                val data=state.data
                val count=user?.uid?.let { CustomerFavorites(context,it).ids().size } ?: data?.products?.count { it.favorite }
                settingsBody.addView(ProfileView(context,1f,user,null,false,accountBusy,
                    R.string.account_saving,accountMessage,{ openSettings(SettingsDestination.SECURITY) },logout,
                    CustomerProfileSummary(count,if(data==null || "receipts" in data.unavailableFeatures) null else data.receipts.size)))
            }
            SettingsDestination.SECURITY -> settingsBody.addView(SecurityView(context,1f,user,accountBusy,accountMessage,::editAccount))
            SettingsDestination.ABOUT -> settingsBody.addView(AboutView(context,1f) { openSettings(SettingsDestination.HELP) })
            else -> {
                val uid=user?.uid ?: return
                val preferences=StockerPreferences(context,uid)
                settingsBody.addView(SettingsContentView(context,1f,settingsDestination,preferences,accountBusy,
                    { preferences.applyTheme(activity) },{},::showInfo,isCustomer=true,showSupport=false))
            }
        }
    }
    private fun editAccount(field:AccountEditField) {
        val current=user ?: return
        if(accountBusy || current.business?.apiAuthenticated==true) return
        val submit=onAccountEdit ?: return
        close()
        accountEditor=AccountEditorDialog.show(context as AppCompatActivity,1f,current,field) { request ->
            require(user?.uid==request.uid && !accountBusy) { "A sessão mudou. Entre novamente." }
            submit(request)
        }
        dialog=accountEditor?.dialog
    }
    private fun showInfo(heading:String,message:String) {
        close()
        dialog=InfoCardDialog.show(context as AppCompatActivity,1f,heading,message)
    }
    companion object {
        fun problem(code:String)=mapOf("DAMAGED" to "Produto danificado","TAMPERED" to "Embalagem violada","EXPIRED" to "Produto vencido","OTHER" to "Outro")[code] ?: code
        fun status(code:String)=mapOf("OPEN" to "Em análise","RESOLVED" to "Resolvido","CANCELLED" to "Cancelado")[code] ?: code
    }
}
