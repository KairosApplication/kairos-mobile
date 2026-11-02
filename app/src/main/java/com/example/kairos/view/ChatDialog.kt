package com.example.kairos.view

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputFilter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.kairos.viewmodel.*
import com.example.kairos.R

/** Activity-owned state keeps drafts and conversations across panel changes. */
class ChatDialog(private val activity: AppCompatActivity, private val vm: ChatViewModel) {
    private val ui = UiKit(activity)
    private fun dp(value: Int) = ui.dp(value)
    private val body = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(ui.color(R.color.home_background_middle))
    }
    private val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20))
    }
    private val feedback = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val input = EditText(activity).apply {
        hint = "Pergunte ao Kairos…"; isSaveEnabled = false; maxLines = 5; minLines = 1
        filters = arrayOf(InputFilter.LengthFilter(10000)); textSize = 15f
        typeface = ui.text("").typeface; setText(vm.ui.value?.draft.orEmpty())
        setTextColor(ui.color(R.color.home_ink)); setHintTextColor(ui.color(R.color.home_secondary))
        background = null; setPadding(dp(8), dp(12), dp(8), dp(12))
    }
    private val send = ImageButton(activity).apply {
        setImageResource(R.drawable.chat_send); contentDescription = "Enviar mensagem"
        imageTintList = android.content.res.ColorStateList.valueOf(ui.color(R.color.home_button_text))
        background = ui.shape(R.color.home_button, 16)
        setOnClickListener { if (input.text.isNotBlank()) { vm.draft(input.text.toString()); vm.send() } }
    }
    private val composer = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(6), dp(8), dp(6))
        background = ui.shape(border = true)
        addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        addView(send, LinearLayout.LayoutParams(dp(48), dp(48)))
    }
    private val list = ui.button("Conversas") { vm.draft(input.text.toString()); vm.list() }
    private val new = ui.button("+ Nova", true) { vm.draft(input.text.toString()); vm.create() }
    private val more = ui.button("Carregar mais") { vm.list(next = true) }
    private val dialog = androidx.appcompat.app.AppCompatDialog(activity, R.style.Theme_Kairos).apply {
        supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        setContentView(body, android.view.ViewGroup.LayoutParams(-1, -1))
    }
    private fun spaced(view: View, top: Int = 12) = content.addView(view,
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    init {
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(20), dp(18))
            setBackgroundColor(ui.color(R.color.home_surface))
        }
        val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.scanner_back); background = null; contentDescription = "Voltar"
            imageTintList = android.content.res.ColorStateList.valueOf(ui.color(R.color.home_ink))
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(44), dp(48)))
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text("Assistente Kairos", 22f, strong = true))
            addView(ui.text("Uma ajuda para a sua rotina", 12f, muted = true))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(row)
        val actions = LinearLayout(activity)
        actions.addView(list, LinearLayout.LayoutParams(0, dp(50), 1f).apply { topMargin = dp(16); marginEnd = dp(10) })
        actions.addView(new, LinearLayout.LayoutParams(0, dp(50), 1f).apply { topMargin = dp(16) })
        header.addView(actions); body.addView(header)
        body.addView(feedback)
        body.addView(ScrollView(activity).apply { isFillViewport = true; addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(12))
            setBackgroundColor(ui.color(R.color.home_surface)); addView(composer)
            addView(ui.text("Confira informações importantes antes de agir.", 10f, muted = true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
            })
        }
        body.addView(bottom)
        val observer = Observer<ChatState> { state ->
            content.removeAllViews(); feedback.removeAllViews()
            if (state.error.isNotBlank()) {
                feedback.setPadding(dp(20), dp(12), dp(20), 0)
                feedback.addView(ui.text("Não foi possível concluir agora", 14f, strong = true))
                feedback.addView(ui.text(state.error, 13f, muted = true).apply {
                    accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                })
            } else feedback.setPadding(0, 0, 0, 0)
            if (state.loading) {
                spaced(LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ProgressBar(activity), LinearLayout.LayoutParams(dp(24), dp(24)))
                    addView(ui.text("Só um instante…", 14f, muted = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
                })
            }
            if (state.browsing) {
                spaced(ui.text("Suas conversas", 20f, strong = true), 0)
                if (!state.loading && state.conversations.isEmpty()) spaced(ui.empty(R.drawable.chat_bubble,
                    "Um novo começo", "Você ainda não tem conversas. Toque em + Nova para começar."))
                state.conversations.forEach { conversation ->
                    spaced(ui.button(conversation.label + "   ›") { vm.draft(input.text.toString()); vm.open(conversation.id) }.apply {
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL; isEnabled = !state.loading
                    })
                }
            } else if (state.messages.isEmpty()) {
                spaced(ui.empty(R.drawable.chat_bubble, "Como posso ajudar?",
                    "Tire dúvidas sobre o estoque e encontre orientação para o seu dia."), 12)
                spaced(ui.text("PARA COMEÇAR", 11f, strong = true, muted = true), 28)
                listOf("Quais produtos precisam de reposição?", "Como escanear uma gôndola?", "Onde encontro meu histórico?").forEach { question ->
                    spaced(ui.button(question + "   ↗") { input.setText(question); input.setSelection(input.length()) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })
                }
            }
            state.messages.forEach { message ->
                val ai = message.sender == "AI"
                spaced(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
                    background = ui.shape(if (ai) R.color.home_surface else R.color.home_subtle_tile, 22, ai)
                    addView(ui.text(if (ai) "KAIROS" else "VOCÊ", 10f, strong = true, muted = true))
                    addView(ui.text(message.content, 15f).apply { setTextIsSelectable(true) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                    if (message.blocked || !message.approved) addView(ui.text("Esta resposta não foi aprovada. Confira as informações antes de usar.", 12f, muted = true))
                    if (message.sources.isNotEmpty()) addView(ui.text("Fontes: " + message.sources.joinToString(", ") {
                        when (it) { "pdf" -> "Manual Kairos"; "database" -> "Dados do mercado"; "memory" -> "Contexto da conversa"; else -> it }
                    }, 11f, muted = true))
                })
            }
            if (state.hasNext && state.browsing) spaced(more)
            listOf(send, list, new, more).forEach { it.isEnabled = !state.loading; it.alpha = if (state.loading) .5f else 1f }
            composer.visibility = if (state.browsing) View.GONE else View.VISIBLE
            input.isEnabled = !state.loading
            if (state.draft.isEmpty() && !state.loading && state.error.isEmpty()) input.text.clear()
        }
        vm.ui.observe(activity, observer)
        dialog.setOnDismissListener { vm.draft(input.text.toString()); vm.ui.removeObserver(observer) }
    }
    fun show() {
        dialog.show()
        UiMotion.reveal(body)
        dialog.window?.apply {
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) setWindowAnimations(R.style.KairosDialogMotion)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); setLayout(-1, -1)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            statusBarColor = ui.color(R.color.home_surface); navigationBarColor = ui.color(R.color.home_surface)
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(body) { view, insets ->
            val safe = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom); insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(body)
    }
    fun close() = dialog.dismiss()
}
