package com.example.kairos.view

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.kairos.R
import com.example.kairos.viewmodel.AuthScreen
import com.example.kairos.viewmodel.AuthUiState
import com.example.kairos.viewmodel.AuthViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/** Native views translated from Figma 430:1129, 21:52 and 7:261. */
class AuthEntryView(private val activity: AppCompatActivity, private val vm: AuthViewModel) : FrameLayout(activity) {
    private val green = Color.rgb(27, 77, 63)
    private val density = resources.displayMetrics.density
    private val scale get() = (resources.displayMetrics.widthPixels / density / 412f).coerceAtMost(1.3f)
    private fun px(value: Float) = (value * scale * density).toInt()
    private val montserrat = ResourcesCompat.getFont(context, R.font.montserrat)!!
    private val inter = ResourcesCompat.getFont(context, R.font.inter)!!
    private var dialog: BottomSheetDialog? = null
    private var registration = false
    private var replacing = false
    private var loading = false
    private val fields = linkedMapOf<String, EditText>()
    private val controls = mutableListOf<View>()
    private var feedback: TextView? = null
    private var spinner: ProgressBar? = null
    private var consent: CheckBox? = null
    private val landingMessage = label("", 14f, Color.WHITE)
    private val landingControls = mutableListOf<View>()

    init {
        setWillNotDraw(false)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        addView(content, LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        content.addView(Space(context), LinearLayout.LayoutParams(1, 0, 234f))
        content.addView(ImageView(context).apply {
            setImageResource(R.drawable.auth_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Kairos"
        }, LinearLayout.LayoutParams(px(342f), px(265f)))
        content.addView(Space(context), LinearLayout.LayoutParams(1, 0, 181f))
        content.addView(landingMessage, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(px(31f), 0, px(31f), 0)
        })
        listOf("Cadastrar" to true, "Entrar" to false).forEach { (title, register) ->
            val button = action(title, if (register) Color.WHITE else Color.BLACK,
                if (register) Color.BLACK else Color.WHITE, 30f, 500) {
                if (register) vm.navigate(AuthScreen.REGISTER) else openSheet(false)
            }
            landingControls.add(button)
            content.addView(button, LinearLayout.LayoutParams(-1, px(86f)).apply {
                setMargins(px(31f), 0, px(29f), if (register) px(29f) else 0)
            })
        }
        content.addView(Space(context), LinearLayout.LayoutParams(1, 0, 28f))
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.rgb(13, 98, 73), Color.rgb(4, 124, 88), Color.rgb(27, 77, 62)),
                floatArrayOf(0f, .35096f, 1f), Shader.TileMode.CLAMP)
        })
        super.onDraw(canvas)
    }

    private fun label(value: String, size: Float, color: Int = Color.BLACK, weight: Int = 500) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size * scale)
        setTextColor(color)
        typeface = Typeface.create(montserrat, weight, false)
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = px(radius).toFloat()
    }

    private fun action(title: String, background: Int, foreground: Int, size: Float = 28.57f,
                       weight: Int = 600, click: () -> Unit) = androidx.appcompat.widget.AppCompatButton(context).apply {
        text = title
        isAllCaps = false
        setTextColor(foreground)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size * scale)
        typeface = Typeface.create(montserrat, weight, false)
        backgroundTintList = null
        this.background = android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x22000000), rounded(background, 100f), null)
        setPadding(0, 0, 0, 0)
        minHeight = 0
        minimumHeight = 0
        stateListAnimator = null
        setOnClickListener { click() }
    }

    fun update(state: AuthUiState) {
        loading = state.loading
        if (state.screen !in listOf(AuthScreen.LOGIN, AuthScreen.REGISTER)) {
            close()
            return
        }
        if (state.screen == AuthScreen.REGISTER && (dialog == null || !registration)) openSheet(true)
        controls.forEach { it.isEnabled = !state.loading }
        landingControls.forEach { it.isEnabled = !state.loading }
        dialog?.setCancelable(!state.loading)
        dialog?.behavior?.isDraggable = !state.loading
        feedback?.text = state.message
        feedback?.visibility = if (state.message.isEmpty()) GONE else VISIBLE
        spinner?.visibility = if (state.loading) VISIBLE else GONE
        landingMessage.text = if (dialog == null) state.message else ""
        landingMessage.visibility = if (landingMessage.text.isEmpty()) GONE else VISIBLE
    }

    private fun openSheet(register: Boolean) {
        close()
        registration = register
        fields.clear()
        controls.clear()
        val sheet = BottomSheetDialog(activity)
        dialog = sheet
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                val radius = px(36f).toFloat()
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
        }
        fun space(height: Float) { panel.addView(Space(context), LinearLayout.LayoutParams(1, px(height))) }
        space(if (register) 14.5f else 13.5f)
        panel.addView(View(context).apply {
            background = rounded(green, 2.5f)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(px(111f), px(5f)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        space(32f)
        panel.addView(label(if (register) "Cadastro" else "Login", 34.506f, weight = 700).apply {
            typeface = Typeface.create(inter, 700, false)
            ViewCompat.setAccessibilityHeading(this, true)
        }, LinearLayout.LayoutParams(-1, px(42f)))
        space(31.5f)
        fun input(key: String, title: String, password: Boolean) {
            val edit = androidx.appcompat.widget.AppCompatEditText(context).apply {
                hint = title
                contentDescription = title
                inputType = InputType.TYPE_CLASS_TEXT or if (password) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                typeface = Typeface.create(montserrat, 500, false)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * scale)
                setTextColor(Color.BLACK)
                setHintTextColor(Color.rgb(116, 116, 116))
                backgroundTintList = null
                background = rounded(Color.rgb(207, 207, 207), 20f)
                setPadding(px(20f), 0, px(20f), 0)
                setSingleLine(true)
                if (password) transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                isSaveEnabled = false
                filters = arrayOf(InputFilter.LengthFilter(255))
                setAutofillHints(if (!password) AUTOFILL_HINT_EMAIL_ADDRESS else if (register) "newPassword" else AUTOFILL_HINT_PASSWORD)
                imeOptions = if (key == "confirmation" || (!register && password)) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
                setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
                }
            }
            fields[key] = edit
            controls.add(edit)
            panel.addView(edit, LinearLayout.LayoutParams(-1, px(77f)).apply { setMargins(px(29f), 0, px(29f), 0) })
        }
        input("email", "Email", false)
        space(28f)
        input("password", "Senha", true)
        if (register) {
            space(27f)
            input("confirmation", "Confirmar senha", true)
            space(17f)
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            consent = CheckBox(context).apply {
                buttonTintList = ColorStateList.valueOf(Color.BLACK)
                contentDescription = "Concordo com os termos de serviço"
            }
            controls.add(consent!!)
            row.addView(consent, LinearLayout.LayoutParams(px(48f), px(48f)))
            val terms = "Concordo com os termos de serviço"
            row.addView(label(terms, 16f).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                text = SpannableString(terms).apply {
                    val start = terms.indexOf("termos")
                    setSpan(ForegroundColorSpan(green), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(UnderlineSpan(), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                setOnClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(activity)
                        .setTitle("Termos de serviço")
                        .setMessage("Os termos de serviço ainda não foram disponibilizados nesta versão do aplicativo.")
                        .setPositiveButton("Fechar", null).show()
                }
            }, LinearLayout.LayoutParams(0, -1, 1f))
            panel.addView(row, LinearLayout.LayoutParams(-1, px(48f)).apply { setMargins(px(24f), 0, px(24f), 0) })
            space(26f)
        } else {
            space(3f)
            val recovery = label("Esqueci minha senha", 20f, green, 600).apply {
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                setOnClickListener { vm.navigate(AuthScreen.RECOVERY) }
            }
            controls.add(recovery)
            panel.addView(recovery, LinearLayout.LayoutParams(-1, px(48f)))
            space(40f)
        }
        feedback = label("", 14f, Color.rgb(155, 28, 28)).apply {
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = GONE
        }
        panel.addView(feedback, LinearLayout.LayoutParams(-1, -2).apply { setMargins(px(29f), 0, px(29f), px(8f)) })
        spinner = ProgressBar(context).apply { visibility = GONE; indeterminateTintList = ColorStateList.valueOf(green) }
        panel.addView(spinner, LinearLayout.LayoutParams(px(32f), px(32f)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val next = action("Continuar", Color.rgb(231, 175, 43), Color.rgb(55, 22, 22)) { submit() }
        controls.add(next)
        panel.addView(next, LinearLayout.LayoutParams(-1, px(75f)).apply { setMargins(px(50f), 0, px(49f), 0) })
        space(37f)
        val scroll = androidx.core.widget.NestedScrollView(context).apply {
            isFillViewport = false
            isSaveEnabled = false
            addView(panel)
        }
        sheet.setContentView(scroll)
        sheet.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        sheet.setOnShowListener {
            sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
                background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            }
            sheet.behavior.apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
                maxWidth = px(412f)
            }
        }
        sheet.setOnDismissListener {
            if (!replacing) {
                dialog = null
                fields.clear()
                controls.clear()
                feedback = null
                spinner = null
                consent = null
                vm.navigate(AuthScreen.LOGIN)
            }
        }
        sheet.show()
        vm.state.value?.let(::update)
    }

    private fun submit() {
        if (loading) return
        val email = fields["email"]?.text.toString().trim()
        val password = fields["password"]?.text.toString()
        if (registration) {
            vm.registerAccount(email, password, fields["confirmation"]?.text.toString(), consent?.isChecked == true)
        } else vm.login(email, password)
    }

    fun save(): Bundle = Bundle().apply {
        putBoolean("open", dialog != null)
        putBoolean("register", registration)
        putString("email", fields["email"]?.text?.toString())
        putBoolean("consent", consent?.isChecked == true)
    }

    fun restore(saved: Bundle?) {
        if (saved?.getBoolean("open") == true && vm.state.value?.screen in listOf(AuthScreen.LOGIN, AuthScreen.REGISTER)) {
            openSheet(saved.getBoolean("register"))
            fields["email"]?.setText(saved.getString("email"))
            consent?.isChecked = saved.getBoolean("consent")
        }
    }

    fun close() {
        replacing = true
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
        fields.clear()
        controls.clear()
        feedback = null
        spinner = null
        consent = null
        replacing = false
    }
}
