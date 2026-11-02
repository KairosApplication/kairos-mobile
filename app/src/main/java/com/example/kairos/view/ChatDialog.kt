package com.example.kairos.view

import android.view.View
import android.widget.*
import android.text.InputFilter
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import com.example.kairos.viewmodel.*
import com.example.kairos.R
import androidx.core.content.res.ResourcesCompat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity

/** Activity-owned ViewModel retains the conversation when this panel closes. No transcript in saved bundles. */
class ChatDialog(private val activity: AppCompatActivity, vm: ChatViewModel) {
    private fun dp(value: Int) = (activity.resources.displayMetrics.density * value).toInt()
    private val font = ResourcesCompat.getFont(activity, R.font.montserrat_medium)
    private fun style(button: Button): Button = button.apply {
        typeface = font; isAllCaps = false; textSize = 15f
        setTextColor(activity.getColor(R.color.home_ink))
        backgroundTintList = null
        background = GradientDrawable().apply { setColor(activity.getColor(R.color.home_surface)); cornerRadius = dp(20).toFloat() }
        setPadding(dp(12), dp(8), dp(12), dp(8)); minimumHeight = dp(56)
    }
    private val body = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(activity.getColor(R.color.home_header_top), activity.getColor(R.color.home_header_middle), activity.getColor(R.color.home_header_bottom)))
    }
    private val input = EditText(activity).apply {
        hint = "Faça uma pergunta…"; isSaveEnabled = false; filters = arrayOf(InputFilter.LengthFilter(10000))
        typeface = font; setText(vm.ui.value?.draft.orEmpty())
        setTextColor(activity.getColor(R.color.home_ink)); setHintTextColor(activity.getColor(R.color.home_secondary))
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = GradientDrawable().apply { setColor(activity.getColor(R.color.home_surface)); cornerRadius = dp(32).toFloat() }
    }
    private val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
    private val feedback = TextView(activity).apply { setTextColor(Color.WHITE); typeface = font; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    private val send = ImageButton(activity).apply {
        setImageResource(R.drawable.chat_send); contentDescription = "Enviar mensagem"
        background = GradientDrawable().apply { setColor(activity.getColor(R.color.home_surface)); cornerRadius = dp(32).toFloat() }
        setOnClickListener { vm.draft(input.text.toString()); vm.send() }
    }
    private val list = style(Button(activity)).apply { text = "Conversas"; setOnClickListener { vm.draft(input.text.toString()); vm.list() } }
    private val new = style(Button(activity)).apply { text = "Nova conversa"; setOnClickListener { vm.draft(input.text.toString()); vm.create() } }
    private val more = style(Button(activity)).apply { text = "Mais conversas"; setOnClickListener { vm.draft(input.text.toString()); vm.list(next = true) } }
    private val dialog = androidx.appcompat.app.AppCompatDialog(activity, R.style.Theme_Kairos).apply {
        supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        setContentView(body, android.view.ViewGroup.LayoutParams(-1, -1))
    }
    init {
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.scanner_back); background = null; contentDescription = "Voltar"
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(TextView(activity).apply { text = "Ajuda e Suporte"; textSize = 24f;
            typeface = ResourcesCompat.getFont(activity, R.font.montserrat_semibold); setTextColor(Color.WHITE) })
        body.addView(header)
        val actions = LinearLayout(activity)
        listOf(list, new).forEach { actions.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(8), dp(4), dp(8)) }) }
        body.addView(actions); body.addView(feedback)
        body.addView(ScrollView(activity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(activity.getColor(R.color.home_subtle_tile), activity.getColor(R.color.home_surface)))
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(more)
        val composer = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, 0) }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(send, LinearLayout.LayoutParams(dp(62), dp(62)).apply { marginStart = dp(8) })
        body.addView(composer)
        val observer = Observer<ChatState> { state ->
            content.removeAllViews()
            if (state.messages.isEmpty() && state.conversations.isEmpty()) {
                listOf("Não consigo escanear a gôndola.", "Como desativo os alertas?", "Como altero minha senha?").forEach { question ->
                    content.addView(style(Button(activity)).apply { text = question; setOnClickListener { input.setText(question) } },
                        LinearLayout.LayoutParams(-1, dp(68)).apply { topMargin = dp(12) })
                }
            }
            state.conversations.forEach { conversation -> content.addView(style(Button(activity)).apply {
                text = conversation.label; isEnabled = !state.loading; setOnClickListener { vm.draft(input.text.toString()); vm.open(conversation.id) }
            }) }
            state.messages.forEach { message -> content.addView(TextView(activity).apply {
                text = buildString {
                    append(if (message.sender == "AI") "Kairos: " else "Você: ")
                    append(message.content)
                    if (message.blocked) append("\nResposta bloqueada pelas regras de acesso.")
                    else if (!message.approved) append("\nResposta não aprovada pelo serviço.")
                    if (message.sources.isNotEmpty()) append("\nFontes: " + message.sources.joinToString(", "))
                }
                typeface = font; textSize = 16f; setTextColor(activity.getColor(R.color.home_ink))
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = GradientDrawable().apply { setColor(activity.getColor(if (message.sender == "AI") R.color.home_surface else R.color.home_subtle_tile)); cornerRadius = dp(20).toFloat() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }) }
            feedback.text = if (state.loading) "Processando…" else state.error
            listOf(send, list, new, more).forEach { it.isEnabled = !state.loading }
            more.visibility = if (state.hasNext) View.VISIBLE else View.GONE
            input.isEnabled = !state.loading
            if (state.draft.isEmpty() && !state.loading && state.error.isEmpty()) input.text.clear()
        }
        vm.ui.observe(activity, observer)
        dialog.setOnDismissListener { vm.draft(input.text.toString()); vm.ui.removeObserver(observer) }
    }
    fun show() {
        dialog.show()
        dialog.window?.apply {
            addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            setLayout(-1, -1)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            statusBarColor = activity.getColor(R.color.home_header_top)
            navigationBarColor = activity.getColor(R.color.home_header_bottom)
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(body) { view, insets ->
            val safe = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(dp(16) + safe.left, dp(12) + safe.top, dp(16) + safe.right, dp(12) + safe.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(body)
    }
    fun close() = dialog.dismiss()
}
