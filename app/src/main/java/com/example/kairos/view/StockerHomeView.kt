package com.example.kairos.view

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import com.example.kairos.R
import com.example.kairos.model.auth.SignedInUser
import com.example.kairos.model.home.HomeEvent
import com.example.kairos.model.home.HomeEventType
import com.example.kairos.viewmodel.StockerHomeUiState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Native layout adapted from Figma Home-Repositor, 418:1068. */
class StockerHomeView(
    private val activity: AppCompatActivity,
    private val onRetry: () -> Unit,
    private val onLogout: () -> Unit
) : FrameLayout(activity) {
    private enum class Tab(val label: Int, val icon: Int, val viewId: Int) {
        START(R.string.home_start, R.drawable.home_house, R.id.home_nav_start),
        ALERTS(R.string.home_alerts, R.drawable.home_bell, R.id.home_nav_alerts),
        HISTORY(R.string.home_history, R.drawable.home_clock, R.id.home_nav_history),
        CONFIG(R.string.home_config_short, R.drawable.home_settings, R.id.home_nav_config)
    }

    private val density = resources.displayMetrics.density
    private val scale get() = (resources.displayMetrics.widthPixels / density / 412f).coerceAtMost(1.3f)
    private fun px(value: Float) = (value * density * scale).toInt()
    private fun color(id: Int) = context.getColor(id)
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private val ink = color(R.color.home_ink)
    private val secondary = color(R.color.home_secondary)
    private val montserrat by lazy {
        requireNotNull(ResourcesCompat.getFont(context, R.font.montserrat_semibold))
    }
    private val locale = Locale.forLanguageTag("pt-BR")
    private val backdrop = Paint()
    private val statusBarPaint = Paint().apply { color = 0xFF0D6249.toInt() }
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        clipToPadding = false
        addView(body)
    }
    private val nav = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private var tab = Tab.START
    private var user: SignedInUser? = null
    private var state = StockerHomeUiState()
    private var signingOut = false
    private var sessionMessage = ""
    private var dialog: AlertDialog? = null
    private var topInset = 0

    init {
        id = R.id.stocker_home
        setWillNotDraw(false)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            nav.background = rounded(color(R.color.home_nav), 20f)
            addView(nav, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(px(5f), 0, px(5f), px(5f))
            })
        }
        addView(column, LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topInset = safe.top
            column.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            invalidate()
            insets
        }
        render()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        backdrop.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFFE5EFEC.toInt(), 0xFFF4EFEF.toInt(), 0xFFDEDEDE.toInt()),
            floatArrayOf(0f, .86538f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backdrop)
        if (tab == Tab.START) {
            canvas.drawRect(0f, 0f, width.toFloat(), topInset.toFloat(), statusBarPaint)
        }
        super.onDraw(canvas)
    }

    fun bind(value: StockerHomeUiState) {
        if (state == value) return
        state = value
        render()
    }

    fun updateSession(value: SignedInUser?, busy: Boolean, message: String) {
        if (user == value && signingOut == busy && sessionMessage == message) return
        user = value
        signingOut = busy
        sessionMessage = message
        render()
    }

    fun refreshDate() { if (visibility == VISIBLE) render() }
    fun save() = Bundle().apply { putString("tab", tab.name) }
    fun restore(saved: Bundle?) {
        tab = Tab.entries.firstOrNull { it.name == saved?.getString("tab") } ?: Tab.START
        render()
    }

    fun reset() {
        close()
        user = null
        tab = Tab.START
        signingOut = false
        sessionMessage = ""
        state = StockerHomeUiState()
        render()
    }

    fun handleBack(): Boolean {
        if (tab == Tab.START) return false
        select(Tab.START)
        return true
    }

    fun close() { dialog?.dismiss(); dialog = null }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }
    override fun onDetachedFromWindow() { close(); super.onDetachedFromWindow() }

    private fun select(value: Tab) {
        if (signingOut) return
        tab = value
        render()
        scroll.scrollTo(0, 0)
    }

    private fun label(text: String, size: Float, tint: Int = ink) = TextView(context).apply {
        this.text = text
        textSize = size * scale
        typeface = Typeface.create(montserrat, 600, false)
        setTextColor(tint)
        includeFontPadding = false
    }

    private fun rounded(tint: Int, radius: Float, border: Boolean = false) = GradientDrawable().apply {
        setColor(tint)
        cornerRadius = px(radius).toFloat()
        if (border) setStroke(px(1f).coerceAtLeast(1), color(R.color.home_border))
    }

    private fun icon(resource: Int) = ImageView(context).apply {
        setImageResource(resource)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun card(view: View, action: () -> Unit) {
        view.background = RippleDrawable(ColorStateList.valueOf(0x14266D57), rounded(Color.WHITE, 20f, true), null)
        view.elevation = px(5f).toFloat()
        view.outlineAmbientShadowColor = 0x1A05291A
        view.outlineSpotShadowColor = 0x1A05291A
        view.isFocusable = true
        view.setOnClickListener { if (!signingOut) action() }
    }

    private fun add(view: View, top: Float = 0f, side: Float = 19f) {
        body.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(px(side), px(top), px(side), 0)
        })
    }

    private fun heading(title: Int, top: Float) {
        add(label(str(title), 24f).apply { ViewCompat.setAccessibilityHeading(this, true) }, top, 25f)
    }

    private fun render() {
        if (visibility == VISIBLE) {
            WindowCompat.getInsetsController(activity.window, this).apply {
                isAppearanceLightStatusBars = tab != Tab.START
                isAppearanceLightNavigationBars = true
            }
        }
        body.removeAllViews()
        body.setPadding(0, 0, 0, px(20f))
        if (tab == Tab.START) addHero() else heading(when (tab) {
            Tab.ALERTS -> R.string.home_alerts
            Tab.HISTORY -> R.string.home_history
            else -> R.string.home_config
        }, 32f)
        if (tab == Tab.CONFIG) {
            renderAccount()
        } else if (state.loading || state.data == null && !state.failed) {
            add(ProgressBar(context).apply { contentDescription = str(R.string.home_loading) }, 32f, 170f)
            add(label(str(R.string.home_loading), 16f).apply { gravity = Gravity.CENTER }, 16f)
        } else if (state.failed) {
            add(label(str(R.string.home_error), 18f).apply { accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE }, 32f)
            add(textButton(str(R.string.home_retry), onRetry), 16f)
        } else {
            val data = state.data!!
            when (tab) {
                Tab.START -> {
                    heading(R.string.home_overview, 18f)
                    val summaries = LinearLayout(context)
                    summaries.addView(summary(R.string.home_pending, data.pendingThisWeek, R.id.home_pending, Tab.ALERTS),
                        LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = px(9f) })
                    summaries.addView(summary(R.string.home_completed, data.completedThisWeek, R.id.home_completed, Tab.HISTORY),
                        LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = px(9f) })
                    add(summaries, 17f)
                    heading(R.string.home_priorities, 23f)
                    events(data.priorities, R.string.home_empty_priorities, firstGap = 28f)
                }
                Tab.ALERTS -> events(data.alerts, R.string.home_empty_alerts)
                Tab.HISTORY -> events(data.history, R.string.home_empty_history)
                Tab.CONFIG -> Unit
            }
            if (data.isDemo) add(label(str(R.string.home_demo), 12f, secondary).apply { gravity = Gravity.CENTER }, 18f)
        }
        renderNav()
        invalidate()
    }

    private fun addHero() {
        val hero = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
        val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0D6249.toInt(), 0xFF047C58.toInt(), color(R.color.home_green))).apply {
            val radius = px(24f).toFloat()
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
        }
        hero.addView(View(context).apply { background = gradient }, LayoutParams(-1, px(213f)))
        hero.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(color(R.color.home_green), 0xFF007753.toInt())).apply { cornerRadius = px(23f).toFloat() }
        }, LayoutParams(-1, px(68f), Gravity.BOTTOM).apply { setMargins(px(12f), 0, px(15f), 0) })
        val contents = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, px(41f), 0, px(7f))
        }
        val firstName = user?.name?.trim()?.substringBefore(' ').orEmpty()
        contents.addView(label(if (firstName.isBlank()) str(R.string.home_greeting_fallback) else str(R.string.home_greeting, firstName),
            27f, Color.WHITE), LinearLayout.LayoutParams(-1, -2).apply { setMargins(px(25f), 0, px(25f), 0) })
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale))
            .replaceFirstChar { it.titlecase(locale) }
        contents.addView(label(today, 18f, Color.WHITE), LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(px(25f), px(3f), px(25f), 0)
        })
        val verify = LinearLayout(context).apply {
            id = R.id.home_verify
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(111f)
            setPadding(px(20f), px(16f), px(14f), px(16f))
        }
        card(verify) { showInfo(str(R.string.home_verify), str(R.string.home_camera_unavailable)) }
        val camera = FrameLayout(context).apply {
            background = rounded(color(R.color.home_camera_tile), 18f)
            addView(icon(R.drawable.home_camera), LayoutParams(px(40f), px(40f), Gravity.CENTER))
        }
        verify.addView(camera, LinearLayout.LayoutParams(px(68f), px(68f)))
        val description = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(str(R.string.home_verify), 18f))
            addView(label(str(R.string.home_verify_description), 12f, secondary),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6f) })
        }
        verify.addView(description, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(20f) })
        val arrow = FrameLayout(context).apply {
            addView(icon(R.drawable.home_arrow_small), LayoutParams(-1, -1))
            addView(icon(R.drawable.home_camera_arrow), LayoutParams(px(21f), px(18f), Gravity.CENTER))
        }
        verify.addView(arrow, LinearLayout.LayoutParams(px(36f), px(36f)))
        contents.addView(verify, LinearLayout.LayoutParams(-1, -2).apply { setMargins(px(19f), px(34f), px(21f), 0) })
        hero.addView(contents, LayoutParams(-1, -2))
        add(hero, side = 0f)
    }

    private fun summary(title: Int, count: Int, viewId: Int, destination: Tab): View {
        val column = LinearLayout(context).apply {
            id = viewId
            orientation = LinearLayout.VERTICAL
            minimumHeight = px(111f)
            setPadding(px(24f), px(21f), px(20f), px(16f))
            contentDescription = str(R.string.home_summary_accessibility, str(title), count)
        }
        card(column) { select(destination) }
        column.addView(label(str(title), 16f, secondary))
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(count.toString(), 40f))
        row.addView(label(str(R.string.home_week), 15f, secondary),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(10f) })
        row.addView(icon(R.drawable.home_summary_arrow), LinearLayout.LayoutParams(px(21f), px(18f)))
        column.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4f) })
        return column
    }

    private fun events(values: List<HomeEvent>, empty: Int, firstGap: Float = 28f) {
        if (values.isEmpty()) { add(label(str(empty), 16f, secondary), firstGap); return }
        values.forEachIndexed { index, event -> add(eventCard(event), if (index == 0) firstGap else 18f) }
    }

    private fun eventTitle(event: HomeEvent): String = when (event.type) {
        HomeEventType.RESTOCK_REQUIRED -> resources.getQuantityString(R.plurals.home_restock_title, event.productCount, event.productCount)
        HomeEventType.LOW_STOCK -> resources.getQuantityString(R.plurals.home_low_stock_title, event.productCount, event.productCount)
        HomeEventType.ANALYSIS_COMPLETED -> str(if (tab == Tab.START) R.string.home_last_analysis else R.string.home_analysis_completed)
    }

    private fun relativeTime(time: Instant): String {
        val minutes = Duration.between(time, Instant.now()).toMinutes().coerceAtLeast(0)
        val date = time.atZone(ZoneId.systemDefault()).toLocalDate()
        return when {
            minutes < 1 -> str(R.string.home_now)
            minutes < 60 -> str(R.string.home_minutes_ago, minutes)
            date == LocalDate.now() -> str(R.string.home_hours_ago, minutes / 60)
            date == LocalDate.now().minusDays(1) -> str(R.string.home_yesterday)
            else -> date.format(DateTimeFormatter.ofPattern("dd/MM", locale))
        }
    }

    private fun eventCard(event: HomeEvent): View {
        val title = eventTitle(event)
        val subtitle = str(R.string.home_event_subtitle, event.shelfCode, relativeTime(event.occurredAt))
        val row = LinearLayout(context).apply {
            minimumHeight = px(79f)
            gravity = Gravity.CENTER_VERTICAL
            clipToOutline = true
            contentDescription = "$title. $subtitle"
        }
        card(row) { showInfo(title, subtitle + if (state.data?.isDemo == true) "\n\n" + str(R.string.home_demo_description) else "") }
        val (colors, resource) = when (event.type) {
            HomeEventType.RESTOCK_REQUIRED -> intArrayOf(0xFFC73E36.toInt(), 0xFFE0382E.toInt()) to R.drawable.home_stop
            HomeEventType.LOW_STOCK -> intArrayOf(0xFFE0AB3D.toInt(), 0xFFE8A825.toInt()) to R.drawable.home_warning
            HomeEventType.ANALYSIS_COMPLETED -> intArrayOf(0xFF096D3C.toInt(), 0xFF0A874A.toInt()) to R.drawable.home_check
        }
        row.addView(FrameLayout(context).apply {
            background = GradientDrawable(if (event.type == HomeEventType.LOW_STOCK)
                GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.LEFT_RIGHT, colors)
            addView(icon(resource), LayoutParams(px(21f), px(21f), Gravity.CENTER))
        }, LinearLayout.LayoutParams(px(39f), -1))
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(21f), px(17f), px(12f), px(17f))
            addView(label(title, 15.5f))
            addView(label(subtitle, 15f, secondary), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4f) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun renderNav() {
        nav.removeAllViews()
        Tab.entries.forEach { item ->
            val selected = tab == item
            val column = LinearLayout(context).apply {
                id = item.viewId
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = px(76f).coerceAtLeast((48 * density).toInt())
                setPadding(0, px(9f), 0, px(8f))
                isSelected = selected
                isFocusable = true
                contentDescription = str(if (item == Tab.CONFIG) R.string.home_config else item.label)
                background = RippleDrawable(ColorStateList.valueOf(0x14266D57), null, rounded(Color.WHITE, 20f))
                setOnClickListener { select(item) }
            }
            column.addView(icon(item.icon), LinearLayout.LayoutParams(px(32f), px(33f)))
            column.addView(label(str(item.label), 12f, color(R.color.home_green)),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(3f) })
            column.addView(View(context).apply {
                background = rounded(color(R.color.home_active), 50f)
                visibility = if (selected) VISIBLE else INVISIBLE
            }, LinearLayout.LayoutParams(px(54f), px(3f)).apply { topMargin = px(6f) })
            nav.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun renderAccount() {
        add(label(str(R.string.home_account), 18f), 28f)
        add(label(user?.name.orEmpty(), 18f), 12f)
        add(label(user?.email.orEmpty(), 16f, secondary), 8f)
        if (state.data?.isDemo == true) add(label(str(R.string.home_demo_description), 16f, secondary), 28f)
        if (sessionMessage.isNotBlank()) add(label(sessionMessage, 16f).apply { accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE }, 20f)
        add(textButton(str(if (signingOut) R.string.home_logging_out else R.string.home_logout), onLogout).apply {
            id = R.id.home_logout
            isEnabled = !signingOut
        }, 28f)
    }

    private fun textButton(title: String, action: () -> Unit) = androidx.appcompat.widget.AppCompatButton(context).apply {
        text = title
        isAllCaps = false
        typeface = Typeface.create(montserrat, 600, false)
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(color(R.color.home_green))
        setOnClickListener { action() }
    }

    private fun showInfo(title: String, message: String) {
        close()
        dialog = AlertDialog.Builder(activity).setTitle(title).setMessage(message)
            .setPositiveButton(R.string.home_ok, null).show()
        dialog?.window?.decorView?.let(::applyMontserrat)
    }

    private fun applyMontserrat(view: View) {
        if (view is TextView) {
            view.typeface = Typeface.create(montserrat, 600, false)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyMontserrat(view.getChildAt(index))
        }
    }
}
