package com.example.kairos.view

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.kairos.model.cliente.ReceiptItem
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID

class ReceiptScannerActivity : CustomerActivity() {
    override val pageTitle="Escanear nota fiscal"
    private lateinit var qr:EditText
    private lateinit var previewNotice:TextView
    private lateinit var check:CheckBox
    private lateinit var items:LinearLayout
    private lateinit var cameraBox:FrameLayout
    private var camera:QrCamera?=null
    private var accessKey:String?=null
    private var photoName:String?=null
    private var pendingPhoto:File?=null
    private val rows=mutableListOf<Row>()
    private var photoSuggestions=JSONArray()
    private var command=UUID.randomUUID().toString()
    override fun loadData()=repository.receiptContext(owner)
    private data class Row(val card:LinearLayout,val product:Spinner,val quantity:EditText,val amount:EditText)
    private val cameraPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) startQr() else feedback.text="A câmera não foi autorizada. Você pode colar o QR Code ou digitar a chave de acesso."
    }
    private val photograph=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val file=photoName?.let { File(File(cacheDir,"scanner"),it) }
        photoName=null
        if(result.resultCode==RESULT_OK && file?.exists()==true) {
            pendingPhoto=file
            if(::items.isInitialized && !busy) readPendingPhoto()
        } else file?.delete()
    }

    private fun readPendingPhoto() {
        val file=pendingPhoto ?: return
        pendingPhoto=null
        execute({
            try {
                val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                android.graphics.BitmapFactory.decodeFile(file.path,options)
                options.inSampleSize=1
                while(options.outWidth/options.inSampleSize>1600 || options.outHeight/options.inSampleSize>1600) options.inSampleSize*=2
                options.inJustDecodeBounds=false
                val image=android.graphics.BitmapFactory.decodeFile(file.path,options) ?: error("Foto inválida")
                val output=java.io.ByteArrayOutputStream()
                image.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,output); image.recycle()
                repository.interpret(owner,output.toByteArray())
            } finally { file.delete() }
        }) { suggestions ->
            val key=suggestions.optString("accessKey","")
            if(::qr.isInitialized && key.isNotBlank()) { qr.setText(key); accessKey=null }
            val suggested=suggestions.optJSONArray("items") ?: JSONArray()
            if("receipts" in data!!.unavailableFeatures) {
                items.removeAllViews(); photoSuggestions=suggested
                for(i in 0 until suggested.length()) showSuggestion(suggested.getJSONObject(i))
            } else for(i in 0 until suggested.length()) addItem(suggested.getJSONObject(i))
            feedback.text=if(suggested.length()==0) "Não foram identificados itens legíveis. Tente uma foto mais próxima e nítida."
                else "Sugestões da IA, ainda não verificadas. Confira descrições, quantidades e valores com a nota."
        }
    }

    override fun ready(saved:Bundle?) {
        body.removeAllViews(); controls.clear(); rows.clear()
        command=saved?.getString("command") ?: command
        if("receipts" in data!!.unavailableFeatures) {
            add(ui.text("Fotografe os itens da nota fiscal. A leitura pode conter erros: confira os dados com o documento. Esta leitura não registra uma compra nem altera o estoque.",14f,muted=true))
            items=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            if(data!!.receiptPhotoAvailable) {
                action("Fotografar nota fiscal",true) { openPhoto() }
            } else add(ui.text("A leitura de notas ainda depende da configuração do Firebase neste aplicativo.",15f,true))
            add(ui.text("Itens para conferência",21f,true),24)
            add(items)
            photoName=saved?.getString("photoName")?.takeIf { it.matches(Regex("[a-f0-9-]+\\.jpg")) }
            photoSuggestions=runCatching { JSONArray(saved?.getString("photoSuggestions") ?: "[]") }.getOrDefault(JSONArray())
            for(i in 0 until photoSuggestions.length()) showSuggestion(photoSuggestions.getJSONObject(i))
            action("Concluir leitura") { finish() }
            if(pendingPhoto!=null && data!!.receiptPhotoAvailable) readPendingPhoto()
            return
        }
        add(ui.text("Fotografe a nota e confira os itens antes de salvar a compra. O QR Code ou a chave de 44 dígitos são opcionais; a compra é um registro informado por você, sem validação fiscal.",14f,muted=true))
        cameraBox=FrameLayout(this).apply { visibility=View.GONE }
        body.addView(cameraBox,LinearLayout.LayoutParams(-1,ui.dp(240)).apply { topMargin=ui.dp(16) })
        action("Ler QR Code com a câmera",true) {
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startQr()
            else cameraPermission.launch(Manifest.permission.CAMERA)
        }
        action("Parar câmera") { camera?.close(); camera=null; cameraBox.visibility=View.GONE }
        qr=input("QR Code ou chave de acesso (opcional)")
        qr.setText(saved?.getString("qr").orEmpty())
        photoName=saved?.getString("photoName")?.takeIf { it.matches(Regex("[a-f0-9-]+\\.jpg")) }
        previewNotice=ui.text("Nenhuma nota conferida.",14f,muted=true); add(previewNotice)
        action("Validar chave da nota") { validateKey() }
        action("Ler foto da nota com IA") {
            openPhoto()
        }
        add(ui.text("Itens para conferência",21f,true),24)
        add(ui.text("Informe o total de cada linha, não o preço unitário. Produtos vendidos por peso ainda precisam ser conferidos fora deste fluxo.",13f,muted=true))
        items=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; add(items)
        val restored=runCatching { JSONArray(saved?.getString("items") ?: "[]") }.getOrDefault(JSONArray())
        for(i in 0 until restored.length()) addItem(restored.getJSONObject(i))
        action("Adicionar produto da compra") { addItem() }
        check=CheckBox(this).apply {
            typeface=androidx.core.content.res.ResourcesCompat.getFont(this@ReceiptScannerActivity,com.example.kairos.R.font.montserrat_regular)
            text="Conferi os produtos, quantidades e totais com a nota fiscal."
            setTextColor(ui.color(com.example.kairos.R.color.home_ink))
        }
        controls.add(check); add(check)
        action("Confirmar e salvar compra",true) {
            val key=qr.text.toString().trim()
            if(key.isNotEmpty() && (accessKey==null || key!=accessKey)) { feedback.text="Valide a chave informada antes de confirmar, ou deixe o campo vazio."; return@action }
            if(!check.isChecked) { feedback.text="Confira os itens e marque a confirmação."; return@action }
            try {
                require(rows.isNotEmpty()) { "Adicione os produtos da compra." }
                val confirmedItems=rows.map { row ->
                    val product=data!!.products.getOrNull(row.product.selectedItemPosition-1)
                        ?: throw IllegalArgumentException("Selecione o produto de cada linha.")
                    ReceiptItem(product.id,row.quantity.text.toString().replace(',','.').toBigDecimalOrNull()?.let {
                        try { it.intValueExact() } catch(error:ArithmeticException) { null }
                    } ?: throw IllegalArgumentException("Informe uma quantidade inteira. Compras por peso ainda não são suportadas."),
                        row.amount.text.toString().replace(',','.').toBigDecimalOrNull() ?: throw IllegalArgumentException("Informe o total de cada linha.")).also { it.json() }
                }
                require(confirmedItems.map { it.productId }.distinct().size==confirmedItems.size) { "Agrupe as linhas do mesmo produto." }
                execute({ repository.confirmReceipt(owner,key,confirmedItems,command) }) { confirmed(it,"Compra registrada com sucesso") }
            } catch(error:Exception) { feedback.text=error.message ?: "Confira os dados da compra." }
        }
        if(pendingPhoto!=null) readPendingPhoto()
    }
    private fun openPhoto() {
        InfoCardDialog.confirm(this,"Ler foto com IA",
            "A foto da nota será enviada ao Google pelo Firebase AI Logic para sugerir os itens. Evite fotografar CPF ou outros dados pessoais. A leitura pode conter erros.",
            "Fotografar","Cancelar") {
            camera?.close(); camera=null
            if(::cameraBox.isInitialized) cameraBox.visibility=View.GONE
            photoName=UUID.randomUUID().toString()+".jpg"
            photograph.launch(Intent(this,ShelfCameraActivity::class.java).putExtra("filename",photoName).putExtra("captureMode","receipt"))
        }
    }
    private fun showSuggestion(item:JSONObject) {
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; background=ui.shape(border=true)
            setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16))
        }
        card.addView(ui.text(item.optString("description","Item não identificado"),17f,true))
        val quantity=if(item.isNull("quantity")) "Não identificada" else item.get("quantity").toString()
        val amount=if(item.isNull("amount")) "Não identificado" else item.get("amount").toString().toBigDecimalOrNull()?.let {
            java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("pt-BR")).format(it)
        } ?: "Não identificado"
        card.addView(ui.text("Quantidade: $quantity\nTotal da linha: $amount",14f,muted=true))
        items.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(12) })
    }
    private fun validateKey() {
        val value=qr.text.toString().trim()
        execute({ repository.preview(owner,value) }) { result ->
            accessKey=result.getString("accessKey")
            qr.setText(accessKey)
            previewNotice.text=result.getString("message")
            check.isChecked=false
        }
    }
    private fun startQr() {
        camera?.close()
        val preview=PreviewView(this)
        cameraBox.removeAllViews(); cameraBox.addView(preview); cameraBox.visibility=View.VISIBLE
        camera=QrCamera(this,preview,{ value ->
            cameraBox.visibility=View.GONE; qr.setText(value); accessKey=null; validateKey()
        },{ feedback.text="Não foi possível abrir a câmera. Informe a chave manualmente."; cameraBox.visibility=View.GONE }).also { it.start() }
    }
    private fun addItem(suggestion:JSONObject?=null) {
        if(rows.size>=100) { feedback.text="Limite de 100 produtos por nota."; return }
        val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=ui.shape(border=true); setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(14)) }
        if(suggestion?.optString("description")?.isNotBlank()==true)
            card.addView(ui.text("Sugestão da IA · ${suggestion.getString("description")}",14f,true))
        val product=ui.spinner(listOf("Selecione o produto")+data!!.products.map { it.name }).apply { contentDescription="Produto da compra" }
        val productId=suggestion?.optLong("productId",-1)
        if(productId!=null) product.setSelection((data!!.products.indexOfFirst { it.id==productId }+1).coerceAtLeast(0))
        card.addView(product)
        fun edit(label:String,value:String):EditText {
            card.addView(ui.text(label,13f,true))
            val input=EditText(this).apply {
                typeface=androidx.core.content.res.ResourcesCompat.getFont(this@ReceiptScannerActivity,com.example.kairos.R.font.montserrat_regular)
                hint=label; contentDescription=label; setSingleLine(true); isSaveEnabled=false
                inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setTextColor(ui.color(com.example.kairos.R.color.home_ink)); setHintTextColor(ui.color(com.example.kairos.R.color.home_secondary))
                setText(value); background=ui.shape(com.example.kairos.R.color.home_subtle_tile,12,true)
                setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12))
                filters=arrayOf(android.text.InputFilter.LengthFilter(16))
            }
            controls.add(input)
            card.addView(input,LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(6); bottomMargin=ui.dp(10) })
            return input
        }
        val quantity=edit("Quantidade inteira",if(suggestion==null || suggestion.isNull("quantity")) "" else suggestion.get("quantity").toString())
        val amount=edit("Total desta linha (R$)",if(suggestion==null || suggestion.isNull("amount")) "" else suggestion.get("amount").toString())
        val row=Row(card,product,quantity,amount); rows.add(row); controls.add(product)
        card.addView(ui.button("Remover linha") { if(!busy) { rows.remove(row); items.removeView(card); controls.removeAll(listOf(product,quantity,amount)) } })
        items.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(12) })
    }
    override fun onSaveInstanceState(out:Bundle) {
        out.putString("command",command)
        out.putString("photoSuggestions",photoSuggestions.toString())
        out.putString("photoName",photoName)
        if(::qr.isInitialized) {
            out.putString("qr",qr.text.toString())
            out.putString("photoName",photoName)
            out.putString("items",JSONArray().apply { rows.forEach { row -> put(JSONObject()
                .put("productId",data?.products?.getOrNull(row.product.selectedItemPosition-1)?.id ?: -1)
                .put("quantity",row.quantity.text.toString()).put("amount",row.amount.text.toString())) } }.toString())
        }
        super.onSaveInstanceState(out)
    }
    override fun onPause() { camera?.close(); camera=null; if(::cameraBox.isInitialized) cameraBox.visibility=View.GONE; super.onPause() }
    override fun onDestroy() {
        camera?.close()
        if(isFinishing) {
            pendingPhoto?.delete()
            photoName?.let { File(File(cacheDir,"scanner"),it).delete() }
        }
        super.onDestroy()
    }
}
