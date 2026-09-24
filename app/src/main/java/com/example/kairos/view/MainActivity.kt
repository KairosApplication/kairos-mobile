package com.example.kairos.view

import android.os.Bundle
import android.animation.ValueAnimator
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.kairos.R
import androidx.lifecycle.ViewModelProvider
import com.example.kairos.model.auth.*
import com.example.kairos.viewmodel.*
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Entrada do Figma; recuperação, perfil e destino provisório usam os fluxos existentes. */
class MainActivity : AppCompatActivity() {
    private lateinit var vm: AuthViewModel
    private lateinit var form: LinearLayout
    private lateinit var message: TextView
    private lateinit var progress: ProgressBar
    private lateinit var authEntry: AuthEntryView
    private lateinit var formScroll: ScrollView
    private val fields = linkedMapOf<String, EditText>()
    private val controls = mutableListOf<View>()
    private var renderedScreen: AuthScreen? = null
    private var splashCompleted = false
    private var splashRoot: FrameLayout? = null
    private var splashView: View? = null
    private var dismissSplash: Runnable? = null
    private var onboardingRoot: View? = null
    private var onboardingPage = 0
    private var onboardingCompleted = false

    private data class OnboardingPage(
        val illustration: Int,
        val title: Int
    )

    private val onboardingPages = listOf(
        OnboardingPage(R.drawable.ic_onboarding_1, R.string.onboarding_title_time),
        OnboardingPage(R.drawable.ic_onboarding_2, R.string.onboarding_title_restocking),
        OnboardingPage(R.drawable.ic_onboarding_3, R.string.onboarding_title_connected)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        splashCompleted = savedInstanceState?.getBoolean("splashCompleted", false) ?: false
        onboardingCompleted = savedInstanceState?.getBoolean("onboardingCompleted", false) ?: false
        onboardingPage = savedInstanceState?.getInt("onboardingPage", 0)
            ?.coerceIn(onboardingPages.indices) ?: 0
        enableEdgeToEdge()
        vm = ViewModelProvider(this, AuthViewModel.Factory(AuthDependencies.repository(applicationContext)))[AuthViewModel::class.java]
        val scroll = ScrollView(this)
        formScroll = scroll
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        scroll.addView(form)
        val root = FrameLayout(this)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        authEntry = AuthEntryView(this, vm)
        root.addView(authEntry, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        showOnboarding(root)
        showSplash(root)
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
        authEntry.restore(savedInstanceState?.getBundle("authEntry"))
        vm.state.observe(this) { state ->
            if (renderedScreen != state.screen) render(state)
            authEntry.update(state)
            if (authEntry.visibility != View.VISIBLE) {
                message.text = state.message
                progress.visibility = if (state.loading) View.VISIBLE else View.GONE
                controls.forEach { it.isEnabled = !state.loading }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (onboardingRoot != null) {
                    if (onboardingPage > 0) {
                        onboardingPage--
                        renderOnboarding()
                    } else {
                        finish()
                    }
                    return
                }
                if (vm.state.value?.loading == true) return
                if (vm.state.value?.screen == AuthScreen.LOGIN) finish()
                else vm.navigate(AuthScreen.LOGIN)
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("authEntry", authEntry.save())
        outState.putBoolean("splashCompleted", splashCompleted)
        outState.putBoolean("onboardingCompleted", onboardingCompleted)
        outState.putInt("onboardingPage", onboardingPage)
        outState.putBundle("form", Bundle().apply {
            fields.filterKeys { it !in setOf("password", "confirmation", "code") }
                .forEach { (key, field) -> putString(key, field.text.toString()) }
        })
        super.onSaveInstanceState(outState)
    }

    private fun showOnboarding(root: FrameLayout) {
        if (onboardingCompleted || vm.state.value?.screen != AuthScreen.LOGIN) return
        val onboarding = layoutInflater.inflate(R.layout.view_onboarding, root, false)
        onboardingRoot = onboarding
        root.addView(onboarding, FrameLayout.LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(onboarding) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        onboarding.findViewById<Button>(R.id.onboarding_next).setOnClickListener {
            if (onboardingPage < onboardingPages.lastIndex) {
                onboardingPage++
                renderOnboarding()
            } else {
                onboardingCompleted = true
                root.removeView(onboarding)
                onboardingRoot = null
            }
        }
        renderOnboarding()
    }

    private fun renderOnboarding() {
        val root = onboardingRoot ?: return
        val page = onboardingPages[onboardingPage]
        root.findViewById<ImageView>(R.id.onboarding_illustration)
            .setImageResource(page.illustration)
        root.findViewById<TextView>(R.id.onboarding_title).setText(page.title)
        root.findViewById<TextView>(R.id.onboarding_description)
            .setText(R.string.onboarding_description)
        root.findViewById<TextView>(R.id.onboarding_counter).text = getString(
            R.string.onboarding_counter,
            onboardingPage + 1,
            onboardingPages.size
        )
        listOf(R.id.onboarding_dot_1, R.id.onboarding_dot_2, R.id.onboarding_dot_3)
            .forEachIndexed { index, id ->
                root.findViewById<View>(id).setBackgroundResource(
                    if (index == onboardingPage) R.drawable.onboarding_dot_active
                    else R.drawable.onboarding_dot_inactive
                )
            }
        root.findViewById<Button>(R.id.onboarding_next).setText(
            if (onboardingPage == onboardingPages.lastIndex) {
                R.string.onboarding_start
            } else {
                R.string.onboarding_next
            }
        )
    }

    private fun showSplash(root: FrameLayout) {
        if (splashCompleted) return
        splashRoot = root
        val originalBackground = window.decorView.background
        window.setBackgroundDrawableResource(R.drawable.splash_background)
        val splash = layoutInflater.inflate(R.layout.view_splash, root, false)
        splashView = splash
        root.addView(splash)
        val bars = WindowCompat.getInsetsController(window, root)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
        dismissSplash = Runnable {
            dismissSplash = null
            // Restaura o fundo da próxima tela enquanto a splash ainda está opaca.
            window.setBackgroundDrawable(originalBackground)
            val finishTransition = Runnable {
                splashCompleted = true
                root.removeView(splash)
                bars.show(WindowInsetsCompat.Type.systemBars())
                splashView = null
                splashRoot = null
            }
            if (ValueAnimator.areAnimatorsEnabled()) {
                splash.animate()
                    .alpha(0f)
                    .setDuration(450L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction(finishTransition)
                    .start()
            } else {
                finishTransition.run()
            }
        }
        // Só conta o tempo depois que o layout está pronto para ser exibido.
        // A inicialização do Firebase não pode consumir a duração da splash.
        splash.doOnPreDraw {
            dismissSplash?.let { root.postDelayed(it, 2000L) }
        }
    }

    override fun onDestroy() {
        authEntry.close()
        dismissSplash?.let { splashRoot?.removeCallbacks(it) }
        splashView?.animate()?.withEndAction(null)?.cancel()
        splashView = null
        splashRoot = null
        dismissSplash = null
        onboardingRoot = null
        super.onDestroy()
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
        val isEntry = state.screen == AuthScreen.LOGIN || state.screen == AuthScreen.REGISTER
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            !isEntry && (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK !=
                android.content.res.Configuration.UI_MODE_NIGHT_YES)
        authEntry.visibility = if (isEntry) View.VISIBLE else View.GONE
        formScroll.visibility = if (isEntry) View.GONE else View.VISIBLE
        if (isEntry) {
            fields.clear()
            controls.clear()
            authEntry.update(state)
            return
        }
        authEntry.close()
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
        when (state.screen) {
            AuthScreen.LOGIN, AuthScreen.REGISTER -> Unit // Rendered by AuthEntryView above.
            AuthScreen.COMPLETE_PROFILE -> {
                field("name", "Nome", max = 100)
                field("lastName", "Sobrenome", max = 100)
                field("birthDate", "Nascimento (AAAA-MM-DD)", max = 10)
                field("cpf", "CPF", max = 14)
                form.addView(TextView(this).apply { text = state.user?.email.orEmpty() })
                field("zipCode", "CEP", max = 9)
                field("plan", "Plano", max = 20)
                button("Concluir cadastro") {
                    try {
                        vm.completeProfile(ProfileDetails(
                            value("name").trim(), value("lastName").trim(), LocalDate.parse(value("birthDate").trim()),
                            value("cpf").trim(), value("zipCode").trim(), value("plan").trim()
                        ))
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
