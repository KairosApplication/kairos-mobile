package com.example.kairos.view

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.kairos.model.auth.*
import com.example.kairos.viewmodel.*
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** UI provisória; operações e estado ficam no ViewModel. */
class MainActivity : AppCompatActivity() {
    private lateinit var vm: AuthViewModel
    private lateinit var form: LinearLayout
    private lateinit var message: TextView
    private lateinit var progress: ProgressBar
    private val fields = linkedMapOf<String, EditText>()
    private val controls = mutableListOf<View>()
    private var renderedScreen: AuthScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = ViewModelProvider(this, AuthViewModel.Factory(AuthDependencies.repository(applicationContext)))[AuthViewModel::class.java]
        val scroll = ScrollView(this)
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        scroll.addView(form)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        // Renderização inicial permite restaurar os campos antes de STARTED.
        render(vm.state.value!!)
        savedInstanceState?.getBundle("form")?.let { saved ->
            fields.forEach { (key, field) -> saved.getString(key)?.let { field.setText(it) } }
        }
        vm.state.observe(this) { state ->
            if (renderedScreen != state.screen) render(state)
            message.text = state.message
            progress.visibility = if (state.loading) View.VISIBLE else View.GONE
            controls.forEach { it.isEnabled = !state.loading }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (vm.state.value?.loading == true) return
                if (vm.state.value?.screen == AuthScreen.LOGIN) finish()
                else vm.navigate(AuthScreen.LOGIN)
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("form", Bundle().apply {
            fields.filterKeys { it !in setOf("password", "confirmation", "code") }
                .forEach { (key, field) -> putString(key, field.text.toString()) }
        })
        super.onSaveInstanceState(outState)
    }

    private fun field(key: String, label: String, type: Int = InputType.TYPE_CLASS_TEXT, max: Int = 255) {
        form.addView(TextView(this).apply { text = label })
        val input = EditText(this).apply {
            hint = label
            inputType = type
            filters = arrayOf(InputFilter.LengthFilter(max))
            isSaveEnabled = false
            setSingleLine(true)
        }
        fields[key] = input
        controls.add(input)
        form.addView(input)
    }

    private fun value(key: String) = fields.getValue(key).text.toString()
    private fun button(label: String, action: () -> Unit) {
        val button = Button(this).apply { text = label; setOnClickListener { action() } }
        controls.add(button)
        form.addView(button)
    }

    private fun render(state: AuthUiState) {
        renderedScreen = state.screen
        form.removeAllViews()
        fields.clear()
        controls.clear()
        form.addView(TextView(this).apply {
            text = "Kairos — cadastro e acesso"
        })
        form.addView(TextView(this).apply {
            textSize = 24f
            text = when (state.screen) {
                AuthScreen.LOGIN -> "Login"
                AuthScreen.REGISTER -> "Cadastrar usuário"
                AuthScreen.RECOVERY -> "Recuperar senha"
                AuthScreen.COMPLETE_PROFILE -> "Completar cadastro"
                AuthScreen.HOME -> "Bem-vindo, ${state.user?.name.orEmpty()}"
            }
        })
        message = TextView(this).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        form.addView(message)
        progress = ProgressBar(this)
        form.addView(progress)
        val emailType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val passwordType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        when (state.screen) {
            AuthScreen.LOGIN -> {
                field("email", "Email", emailType)
                field("password", "Senha", passwordType)
                button("Entrar") { vm.login(value("email").trim(), value("password")) }
                button("Cadastrar") { vm.navigate(AuthScreen.REGISTER) }
                button("Esqueci minha senha") { vm.navigate(AuthScreen.RECOVERY) }
            }
            AuthScreen.REGISTER, AuthScreen.COMPLETE_PROFILE -> {
                field("name", "Nome", max = 100)
                field("lastName", "Sobrenome", max = 100)
                field("birthDate", "Nascimento (AAAA-MM-DD)", max = 10)
                field("cpf", "CPF", max = 14)
                if (state.screen == AuthScreen.REGISTER) {
                    field("email", "Email", emailType)
                    field("password", "Senha (mínimo 6 caracteres)", passwordType)
                    field("confirmation", "Confirmar senha", passwordType)
                } else {
                    form.addView(TextView(this).apply { text = state.user?.email.orEmpty() })
                }
                field("zipCode", "CEP", max = 9)
                field("plan", "Plano", max = 20)
                button("Concluir cadastro") {
                    try {
                        if (state.screen == AuthScreen.COMPLETE_PROFILE) {
                            vm.completeProfile(ProfileDetails(
                                value("name").trim(), value("lastName").trim(), LocalDate.parse(value("birthDate").trim()),
                                value("cpf").trim(), value("zipCode").trim(), value("plan").trim()
                            ))
                        } else vm.register(Registration(
                            value("name").trim(), value("lastName").trim(), LocalDate.parse(value("birthDate").trim()),
                            value("cpf").trim(), value("email").trim(), value("password"),
                            value("zipCode").trim(), value("plan").trim()
                        ), value("confirmation"))
                    } catch (_: DateTimeParseException) {
                        vm.showError("Informe uma data válida no formato AAAA-MM-DD.")
                    }
                }
            }
            AuthScreen.RECOVERY -> {
                field("email", "Email cadastrado", emailType)
                button("Enviar link de recuperação") { vm.requestResetLink(value("email").trim()) }
            }
            AuthScreen.HOME -> {
                form.addView(TextView(this).apply { text = state.user?.email.orEmpty() })
                button("Sair") { vm.logout() }
            }
        }
        if (state.screen !in listOf(AuthScreen.LOGIN, AuthScreen.HOME)) {
            button("Voltar ao login") { vm.navigate(AuthScreen.LOGIN) }
        }
    }
}
