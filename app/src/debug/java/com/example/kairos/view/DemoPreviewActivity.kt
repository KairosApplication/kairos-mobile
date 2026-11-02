package com.example.kairos.view

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.example.kairos.R
import com.example.kairos.model.auth.SignedInUser
import com.example.kairos.model.auth.UserProfile
import com.example.kairos.model.home.DemoStockerHomeRepository
import com.example.kairos.viewmodel.StockerHomeUiState
import java.time.LocalDate

/** Debug-only preview: no Firebase access, HTTP requests, account edits or inventory writes. */
class DemoPreviewActivity : AppCompatActivity() {
    private lateinit var home: StockerHomeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.home_header_top))
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        banner.addView(TextView(this).apply {
            text = "DEMONSTRAÇÃO — dados fictícios"
            textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = ResourcesCompat.getFont(this@DemoPreviewActivity, R.font.montserrat_semibold)
        })
        banner.addView(Button(this).apply {
            id = R.id.demo_preview_exit
            text = "Sair da demonstração"; isAllCaps = false
            typeface = ResourcesCompat.getFont(this@DemoPreviewActivity, R.font.montserrat_medium)
            setOnClickListener { finish() }
        })
        root.addView(banner)
        setContentView(root)
        val uid = "local-demo-preview"
        val user = SignedInUser(uid, "demonstracao@kairos.local", UserProfile(uid, "Demonstração", "", LocalDate.of(2000, 1, 1),
            "", "demonstracao@kairos.local", "", "preview"))
        home = StockerHomeView(this, onRetry = {}, onLogout = { finish() })
        root.addView(home, LinearLayout.LayoutParams(-1, 0, 1f))
        home.updateSession(user, false, "")
        home.bind(StockerHomeUiState(data = DemoStockerHomeRepository().load(uid)))
        home.restore(savedInstanceState?.getBundle("previewHome"))
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!home.handleBack()) finish() }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("previewHome", home.save())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() { home.close(); super.onDestroy() }
}
