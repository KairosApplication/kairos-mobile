package com.example.kairos.view

import android.os.Bundle
import android.content.Intent
import android.widget.*
import android.view.View
import android.graphics.Bitmap
import androidx.activity.result.contract.ActivityResultContracts
import com.example.kairos.model.cliente.*
import com.example.kairos.model.scanner.CapturedPhoto
import com.example.kairos.R
import java.io.File
import java.util.UUID

class DamageReportActivity : CustomerActivity() {
    override val pageTitle="Reportar avaria"
    private lateinit var product:Spinner
    private lateinit var location:Spinner
    private lateinit var type:Spinner
    private lateinit var description:EditText
    private var command=UUID.randomUUID().toString()
    private var locations=emptyList<CustomerLocation>()
    private var restored:Bundle?=null
    private var photoName:String?=null
    private var pendingPhotoName:String?=null
    private var previewBitmap:Bitmap?=null
    private lateinit var photoPreview:ImageView
    private lateinit var photoCaption:TextView
    private lateinit var removePhoto:View
    override fun loadData()=repository.reportContext(owner)
    private fun validName(value:String?)=value?.takeIf { it.matches(Regex("[a-f0-9-]+\\.jpg")) }
    private fun photoFile(name:String?=photoName)=validName(name)?.let { File(File(cacheDir,"scanner"),it) }
    private val photograph=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val name=pendingPhotoName
        val file=photoFile(name)
        pendingPhotoName=null
        if(result.resultCode==RESULT_OK && file?.isFile==true) {
            photoFile()?.delete()
            photoName=name
            if(::photoPreview.isInitialized) renderPhoto()
        } else file?.delete()
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        photoName=validName(savedInstanceState?.getString("damagePhoto"))
        pendingPhotoName=validName(savedInstanceState?.getString("damagePendingPhoto"))
        super.onCreate(savedInstanceState)
    }
    override fun ready(saved:Bundle?) {
        body.removeAllViews(); controls.clear()
        command=saved?.getString("command") ?: command
        restored=saved
        add(ui.text("Ajude a equipe a cuidar dos produtos. Seu relato será conferido e não altera o estoque.",14f,muted=true))
        if(data!!.catalogTruncated) add(ui.text("A lista de produtos está limitada aos primeiros 1.000 itens.",14f,muted=true))
        if(data!!.catalogNotice.isNotBlank()) add(ui.text(data!!.catalogNotice,14f,muted=true))
        val products=data!!.products
        if(products.isEmpty()) { add(ui.empty(com.example.kairos.R.drawable.menu_search,"Catálogo indisponível","Não há produtos cadastrados para este mercado.")); return }
        product=ui.spinner(listOf("Selecione o produto")+products.map { it.name })
        product.contentDescription="Produto afetado"
        add(ui.text("Produto afetado",14f,true)); add(product); controls.add(product)
        location=ui.spinner(listOf("Localização não informada")); add(ui.text("Localização",14f,true)); add(location); controls.add(location)
        type=ui.spinner(listOf("Selecione o problema","Produto danificado","Embalagem violada","Produto vencido","Outro"))
        type.contentDescription="Tipo de problema"
        add(ui.text("Tipo de problema",14f,true)); add(type); controls.add(type)
        description=input("Descrição complementar (opcional)",true)
        description.setText(saved?.getString("description").orEmpty())
        if(data!!.reportPhotoAvailable) photoCard()
        type.setSelection(saved?.getInt("problem",0) ?: 0)
        product.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?) {}
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) {
                locations=if(position==0) emptyList() else data!!.locations.filter { it.productId==products[position-1].id }
                location.adapter=ui.spinnerAdapter(listOf("Localização não informada")+locations.map { it.toString() })
                val shelf=restored?.getLong("shelf")
                if(shelf!=null) location.setSelection((locations.indexOfFirst { it.shelfId==shelf }+1).coerceAtLeast(0))
                restored=null
            }
        }
        val savedId=saved?.getLong("product")
        product.setSelection((products.indexOfFirst { it.id==savedId }+1).coerceAtLeast(0))
        action("Enviar reporte",true) {
            val selected=products.getOrNull(product.selectedItemPosition-1)
            val codes=listOf("DAMAGED","TAMPERED","EXPIRED","OTHER")
            val problem=codes.getOrNull(type.selectedItemPosition-1)
            if(selected==null || problem==null) { feedback.text="Selecione o produto e o tipo de problema."; return@action }
            val shelf=locations.getOrNull(location.selectedItemPosition-1)?.shelfId
            val text=description.text.toString().trim()
            InfoCardDialog.confirm(this,"Enviar relato?","A equipe do mercado receberá este reporte. Nenhum estoque será alterado.",
                "Enviar reporte","Revisar",details="${selected.name}\n${CustomerHomeView.problem(problem)}") {
                val file=photoFile()
                execute({ repository.report(owner,command,selected.id,shelf,problem,text,
                    file?.let { CapturedPhoto.jpeg(it,3*1024*1024) }) }) {
                    photoFile()?.delete();photoName=null
                    confirmed(it,"Avaria registrada com sucesso","Acompanhe o atendimento em Compras → Seus reportes.")
                }
            }
        }
    }
    private fun photoCard() {
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            background=ui.shape(R.color.home_surface,20,true)
            setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16))
        }
        card.addView(ui.text("Foto do produto",18f,true))
        card.addView(ui.text("Opcional. Mostre o problema e evite incluir pessoas. A foto só será enviada ao confirmar o relato.",13f,muted=true),
            LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(8) })
        photoPreview=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="Foto anexada à avaria" }
        card.addView(photoPreview,LinearLayout.LayoutParams(-1,ui.dp(180)).apply { topMargin=ui.dp(12) })
        photoCaption=ui.text("Nenhuma foto anexada.",13f,muted=true)
        card.addView(photoCaption)
        val capture=ui.button("Fotografar produto") {
            if(!busy) {
                pendingPhotoName=UUID.randomUUID().toString()+".jpg"
                photograph.launch(Intent(this,ShelfCameraActivity::class.java)
                    .putExtra("filename",pendingPhotoName).putExtra("captureMode","damage"))
            }
        }
        card.addView(capture,LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(12) });controls.add(capture)
        removePhoto=ui.button("Remover foto") { if(!busy) { photoFile()?.delete();photoName=null;renderPhoto() } }
        card.addView(removePhoto,LinearLayout.LayoutParams(-1,-2));controls.add(removePhoto)
        add(card)
        renderPhoto()
    }
    private fun renderPhoto() {
        photoPreview.setImageDrawable(null)
        previewBitmap?.recycle();previewBitmap=null
        val file=photoFile()?.takeIf { it.isFile }
        if(file!=null) {
            previewBitmap=runCatching { CapturedPhoto.preview(file) }.getOrNull()
            if(previewBitmap==null) { file.delete();photoName=null }
        }
        val attached=previewBitmap!=null
        photoPreview.setImageBitmap(previewBitmap)
        photoPreview.visibility=if(attached) View.VISIBLE else View.GONE
        removePhoto.visibility=if(attached) View.VISIBLE else View.GONE
        photoCaption.text=if(attached) "Foto pronta para envio. Confira o enquadramento antes de confirmar." else "Nenhuma foto anexada."
    }
    override fun onSaveInstanceState(out:Bundle) {
        out.putString("command",command)
        out.putString("damagePhoto",photoName)
        out.putString("damagePendingPhoto",pendingPhotoName)
        if(::product.isInitialized) {
            data?.products?.getOrNull(product.selectedItemPosition-1)?.let { out.putLong("product",it.id) }
            locations.getOrNull(location.selectedItemPosition-1)?.let { out.putLong("shelf",it.shelfId) }
            out.putInt("problem",type.selectedItemPosition)
            out.putString("description",description.text.toString())
        }
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() {
        if(::photoPreview.isInitialized) photoPreview.setImageDrawable(null)
        previewBitmap?.recycle();previewBitmap=null
        if(isFinishing) { photoFile()?.delete();photoFile(pendingPhotoName)?.delete() }
        super.onDestroy()
    }
}
