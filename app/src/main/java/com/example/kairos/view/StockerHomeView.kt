package com.example.kairos.view

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.animation.AccelerateDecelerateInterpolator
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
import com.example.kairos.model.home.HistoryFilter
import com.example.kairos.model.home.HistoryPeriod
import com.example.kairos.model.home.RestockingRecord
import com.example.kairos.model.home.RestockingStatus
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
    private val headerEdgePaint = Paint().apply { color = 0xFF0A6A4F.toInt() }
    private val restockingBackground = object : Drawable() {
        private val surface = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shape = Path()
        override fun onBoundsChange(bounds: Rect) {
            surface.shader = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
                intArrayOf(0xFFE5EFEC.toInt(), 0xFFF4EFEF.toInt(), 0xFFDEDEDE.toInt()),
                floatArrayOf(0f, .86538f, 1f), Shader.TileMode.CLAMP)
            val radius = px(10f).toFloat()
            shape.reset()
            shape.addRoundRect(RectF(bounds), floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
        }
        override fun draw(canvas: Canvas) { canvas.drawPath(shape, surface) }
        override fun setAlpha(alpha: Int) { surface.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { surface.colorFilter = colorFilter; invalidateSelf() }
        @Suppress("DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        clipToPadding = false
        addView(body)
    }
    private val nav = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val navFrame = FrameLayout(context)
    private val navIndicator = View(context).apply {
        id = R.id.home_nav_indicator
        background = rounded(color(R.color.home_active), 50f)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val navIcons = mutableMapOf<Tab, ImageView>()
    private val navLabels = mutableMapOf<Tab, TextView>()
    private val sectionHeader by lazy {
        label("", 24f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF0D6249.toInt(), 0xFF0A6A4F.toInt()))
            ViewCompat.setAccessibilityHeading(this, true)
        }
    }
    private var indicatorReady = false
    private var historyFilter = HistoryFilter()
    private var pendingRestoredState: Bundle? = null
    private var historyResults: LinearLayout? = null
    private var filterButton: FrameLayout? = null
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
            addView(sectionHeader, LinearLayout.LayoutParams(-1, px(80f)))
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            navFrame.background = rounded(color(R.color.home_nav), 20f)
            navFrame.addView(nav, LayoutParams(-1, -2))
            navFrame.addView(navIndicator, LayoutParams(px(65f), px(4f), Gravity.BOTTOM or Gravity.START).apply {
                bottomMargin = px(5f)
            })
            addView(navFrame, LinearLayout.LayoutParams(-1, -2))
        }
        nav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> moveNavIndicator(false) }
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
        if (tab != Tab.CONFIG) {
            canvas.drawRect(0f, 0f, width.toFloat(), topInset.toFloat(), statusBarPaint)
        }
        if (tab == Tab.ALERTS || tab == Tab.HISTORY) {
            canvas.drawRect(0f, sectionHeader.bottom.toFloat(), width.toFloat(),
                (sectionHeader.bottom + px(10f)).toFloat(), headerEdgePaint)
        }
        super.onDraw(canvas)
    }

    fun bind(value: StockerHomeUiState) {
        if (state == value) return
        state = value
        render()
    }

    fun updateSession(value: SignedInUser?, busy: Boolean, message: String) {
        if (user != null && user?.uid != value?.uid) reset()
        val restored = applyRestoredState(value?.uid)
        if (!restored && user == value && signingOut == busy && sessionMessage == message) return
        user = value
        signingOut = busy
        sessionMessage = message
        render()
    }

    fun refreshDate() { if (visibility == VISIBLE) render() }
    // Keep the pending owner when recreation happens before authentication finishes.
    fun save(): Bundle = pendingRestoredState?.let { Bundle(it) } ?: Bundle().apply {
        putString("ownerUid", user?.uid)
        putString("tab", tab.name)
        putString("historyQuery", historyFilter.query)
        putString("historyPeriod", historyFilter.period.name)
        putString("historyShelf", historyFilter.shelfCode)
        putBoolean("historyOldestFirst", historyFilter.oldestFirst)
    }
    fun restore(saved: Bundle?) {
        close()
        tab = Tab.START
        historyFilter = HistoryFilter()
        pendingRestoredState = saved?.takeIf { !it.getString("ownerUid").isNullOrBlank() }?.let { Bundle(it) }
        indicatorReady = false
        applyRestoredState(user?.uid)
        render()
    }

    private fun applyRestoredState(uid: String?): Boolean {
        val saved = pendingRestoredState ?: return false
        if (uid == null) return false
        pendingRestoredState = null
        if (saved.getString("ownerUid") != uid) {
            clearAccountUi()
            return true
        }
        tab = Tab.entries.firstOrNull { it.name == saved.getString("tab") } ?: Tab.START
        historyFilter = HistoryFilter(
            saved.getString("historyQuery").orEmpty(),
            HistoryPeriod.entries.firstOrNull { it.name == saved.getString("historyPeriod") } ?: HistoryPeriod.ALL,
            saved.getString("historyShelf"), saved.getBoolean("historyOldestFirst"))
        indicatorReady = false
        return true
    }

    fun reset() {
        clearAccountUi()
        user = null
        signingOut = false
        sessionMessage = ""
        render()
    }

    private fun clearAccountUi() {
        close()
        tab = Tab.START
        historyFilter = HistoryFilter()
        pendingRestoredState = null
        indicatorReady = false
        navIndicator.animate().cancel()
        state = StockerHomeUiState()
        scroll.scrollTo(0, 0)
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
    override fun onDetachedFromWindow() {
        close()
        navIndicator.animate().cancel()
        navIcons.values.forEach { it.animate().cancel() }
        super.onDetachedFromWindow()
    }

    private fun select(value: Tab) {
        if (signingOut || tab == value) return
        close()
        (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(windowToken, 0)
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
                isAppearanceLightStatusBars = tab == Tab.CONFIG
                isAppearanceLightNavigationBars = true
            }
        }
        val oldSearch = findViewById<EditText>(R.id.history_search)
        val restoreSearchFocus = tab == Tab.HISTORY && oldSearch?.hasFocus() == true
        val searchSelection = oldSearch?.selectionStart ?: 0
        body.removeAllViews()
        historyResults = null
        filterButton = null
        body.setPadding(0, 0, 0, px(20f))
        sectionHeader.visibility = if (tab == Tab.ALERTS || tab == Tab.HISTORY) VISIBLE else GONE
        sectionHeader.text = str(if (tab == Tab.ALERTS) R.string.restocking_alerts_title else R.string.restocking_history_title)
        scroll.background = if (sectionHeader.visibility == VISIBLE) restockingBackground else null
        if (tab == Tab.START) addHero() else if (tab == Tab.CONFIG) heading(R.string.home_config, 32f)
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
                Tab.ALERTS -> renderAlerts()
                Tab.HISTORY -> renderHistory()
                Tab.CONFIG -> Unit
            }
            if (data.isDemo) add(label(str(R.string.home_demo), 12f, secondary).apply { gravity = Gravity.CENTER }, 18f)
        }
        renderNav()
        if (restoreSearchFocus) findViewById<EditText>(R.id.history_search)?.apply {
            requestFocus()
            setSelection(searchSelection.coerceIn(0, text.length))
        }
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
        if (nav.childCount == 0) Tab.entries.forEach { item ->
            val column = LinearLayout(context).apply {
                id = item.viewId
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = px(76f).coerceAtLeast((48 * density).toInt())
                setPadding(0, px(9f), 0, px(8f))
                isFocusable = true
                contentDescription = str(if (item == Tab.CONFIG) R.string.home_config else item.label)
                background = RippleDrawable(ColorStateList.valueOf(0x14266D57), null, rounded(Color.WHITE, 20f))
                setOnClickListener { select(item) }
            }
            val image = icon(item.icon).also { navIcons[item] = it; it.tag = item.icon }
            column.addView(image, LinearLayout.LayoutParams(px(32f), px(33f)))
            val caption = label(str(item.label), 12f, color(R.color.home_green)).also { navLabels[item] = it }
            column.addView(caption,
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(3f) })
            nav.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        }
        Tab.entries.forEach { item ->
            val selected = tab == item
            findViewById<View>(item.viewId).apply { isSelected = selected; isEnabled = !signingOut }
            navLabels[item]?.typeface = Typeface.create(montserrat, if (selected) 700 else 500, false)
            navLabels[item]?.fontVariationSettings = "'wght' ${if (selected) 700 else 500}"
            val resource = when {
                item == Tab.START && selected -> R.drawable.menu_house_active
                item == Tab.CONFIG && selected -> R.drawable.menu_settings_active
                item == Tab.ALERTS && selected -> R.drawable.menu_bell_active
                item == Tab.ALERTS -> R.drawable.menu_bell
                item == Tab.HISTORY && selected -> R.drawable.menu_clock_selected
                else -> item.icon
            }
            navIcons[item]?.let { image ->
                if (image.tag != resource) {
                    image.tag = resource
                    image.animate().withEndAction(null).cancel()
                    image.setImageResource(resource)
                    if (indicatorReady && isAttachedToWindow) {
                        image.alpha = .65f
                        image.animate().alpha(1f).setDuration(180).withEndAction(null).start()
                    } else {
                        image.alpha = 1f
                    }
                }
            }
        }
        moveNavIndicator(true)
    }

    private fun moveNavIndicator(animate: Boolean) {
        val selected = nav.findViewById<View>(tab.viewId) ?: return
        if (selected.width == 0) return
        val indicatorWidth = navIndicator.layoutParams.width
        val physicalLeft = nav.left + selected.left + (selected.width - indicatorWidth) / 2f
        val target = physicalLeft - if (navFrame.layoutDirection == LAYOUT_DIRECTION_RTL) navFrame.width - indicatorWidth else 0
        if (animate && indicatorReady) {
            navIndicator.animate().translationX(target).setDuration(280)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        } else {
            navIndicator.animate().cancel()
            navIndicator.translationX = target
        }
        indicatorReady = true
    }

    private fun sectionLabel(text: String, top: Float, parent: LinearLayout = body) {
        parent.addView(label(text, 20f, 0xCC0F2B21.toInt()).apply {
            ViewCompat.setAccessibilityHeading(this, true)
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(px(25f), px(top), px(19f), 0) })
    }

    private fun renderAlerts() {
        val data = state.data ?: return
        sectionLabel(str(R.string.restocking_pending), 24f)
        if (data.restockingAlerts.isEmpty()) add(label(str(R.string.home_empty_alerts), 16f, secondary), 12f)
        data.restockingAlerts.forEach { add(productCard(it), 10f) }
        sectionLabel(str(R.string.restocking_recent), 16f)
        val today = LocalDate.now()
        val completed = HistoryFilter(period = HistoryPeriod.TODAY)
            .apply(data.restockingHistory, today, ZoneId.systemDefault())
        if (completed.isEmpty()) add(label(str(R.string.restocking_empty_recent), 16f, secondary), 12f)
        completed.take(2).forEach { add(productCard(it), 10f) }
    }

    private fun productCard(record: RestockingRecord): View {
        val (statusColor, statusText, statusIcon) = when (record.status) {
            RestockingStatus.REQUIRED -> Triple(0xFFDE3129.toInt(), R.string.restocking_required, R.drawable.home_stop)
            RestockingStatus.LOW_STOCK -> Triple(0xFFE8A825.toInt(), R.string.restocking_low, R.drawable.home_warning)
            RestockingStatus.COMPLETED -> Triple(0xFF05843B.toInt(), R.string.restocking_done, R.drawable.home_check)
        }
        val date = record.occurredAt.atZone(ZoneId.systemDefault())
        val elapsed = Duration.between(record.occurredAt, Instant.now()).toMinutes()
        val time = if (date.toLocalDate() == LocalDate.now() && elapsed in 0..59) relativeTime(record.occurredAt)
            else date.format(DateTimeFormatter.ofPattern("HH:mm", locale))
        val subtitle = str(R.string.home_event_subtitle, record.shelfCode, time)
        val row = LinearLayout(context).apply {
            minimumHeight = px(92f)
            gravity = Gravity.CENTER_VERTICAL
            clipToOutline = true
            contentDescription = "${record.productName}. $subtitle. ${str(statusText)}"
        }
        card(row) { showInfo(record.productName, "$subtitle\n${str(statusText)}") }
        val thumbnail = FrameLayout(context).apply {
            background = rounded(Color.WHITE, 20f, true)
            val image = when (record.imageKey) {
                "cola" -> R.drawable.restock_cola
                "biscuits" -> R.drawable.restock_biscuits
                "water" -> R.drawable.restock_water
                else -> R.drawable.home_camera
            }
            addView(icon(image).apply { if (record.imageKey == "biscuits") rotation = -15f },
                LayoutParams(px(if (record.imageKey == "biscuits") 57f else 53f),
                    px(if (record.imageKey == "biscuits") 57f else 53f), Gravity.CENTER))
        }
        row.addView(thumbnail, LinearLayout.LayoutParams(px(74f), px(73f)).apply {
            setMargins(px(10f), px(9f), 0, px(9f))
        })
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(15f), px(12f), px(6f), px(12f))
            addView(label(record.productName, 15.5f))
            addView(label(subtitle, 14f, 0xFF617D70.toInt()).apply {
                typeface = Typeface.create(montserrat, 400, false)
                fontVariationSettings = "'wght' 400"
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4f) })
            addView(label(str(statusText), 14f, statusColor),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(3f) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val colors = when (record.status) {
            RestockingStatus.REQUIRED -> intArrayOf(0xFFDE4138.toInt(), 0xFFE0382E.toInt())
            RestockingStatus.LOW_STOCK -> intArrayOf(0xFFE0AB3D.toInt(), 0xFFE8A825.toInt())
            RestockingStatus.COMPLETED -> intArrayOf(0xFF0C8449.toInt(), 0xFF0A874A.toInt())
        }
        row.addView(FrameLayout(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)
            addView(icon(statusIcon), LayoutParams(px(21f), px(21f), Gravity.CENTER))
        }, LinearLayout.LayoutParams(px(55f), -1))
        return row
    }

    private fun renderHistory() {
        val searchBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(62f).coerceAtLeast((48 * density).toInt())
            setPadding(px(24f), px(5f), px(10f), px(5f))
            background = rounded(Color.WHITE, 100f, true)
            elevation = px(5f).toFloat()
            outlineAmbientShadowColor = 0x1A05291A
            outlineSpotShadowColor = 0x1A05291A
        }
        searchBar.addView(icon(R.drawable.menu_search), LinearLayout.LayoutParams(px(30f), px(30f)))
        val search = EditText(context).apply {
            id = R.id.history_search
            hint = str(R.string.history_search)
            contentDescription = str(R.string.history_search)
            textSize = 15.5f * scale
            typeface = Typeface.create(montserrat, 500, false)
            fontVariationSettings = "'wght' 500"
            setTextColor(ink)
            setHintTextColor(0xFF636F6A.toInt())
            background = null
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setPadding(px(20f), 0, px(8f), 0)
            setText(historyFilter.query)
            setOnEditorActionListener { _, action, _ ->
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    clearFocus()
                    (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(windowToken, 0)
                    true
                } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    historyFilter = historyFilter.copy(query = s?.toString().orEmpty())
                    renderHistoryResults()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        searchBar.addView(search, LinearLayout.LayoutParams(0, -1, 1f))
        filterButton = FrameLayout(context).apply {
            id = R.id.history_filter
            isFocusable = true
            addView(icon(R.drawable.menu_filter), LayoutParams(px(20f), px(20f), Gravity.CENTER))
            setOnClickListener { if (!signingOut) showHistoryFilters() }
        }
        searchBar.addView(filterButton, LinearLayout.LayoutParams(px(48f), px(48f)))
        add(searchBar, 23f)
        historyResults = LinearLayout(context).apply {
            id = R.id.history_results
            orientation = LinearLayout.VERTICAL
        }
        body.addView(historyResults, LinearLayout.LayoutParams(-1, -2))
        renderHistoryResults()
    }

    private fun periodLabels() = arrayOf(str(R.string.history_all_dates), str(R.string.history_today),
        str(R.string.home_yesterday), str(R.string.history_last_seven))

    private fun renderHistoryResults() {
        val container = historyResults ?: return
        container.removeAllViews()
        val active = historyFilter.period != HistoryPeriod.ALL || historyFilter.shelfCode != null || historyFilter.oldestFirst
        filterButton?.apply {
            isSelected = active
            contentDescription = str(if (active) R.string.history_filters_active else R.string.history_filters)
            background = RippleDrawable(ColorStateList.valueOf(0x14266D57),
                rounded(if (active) 0xFFD4E7DE.toInt() else 0xFFDBDBDB.toInt(), 100f), null)
        }
        if (active) container.addView(label(str(R.string.history_active_summary,
            periodLabels()[historyFilter.period.ordinal], historyFilter.shelfCode ?: str(R.string.history_all_shelves),
            str(if (historyFilter.oldestFirst) R.string.history_oldest else R.string.history_newest)), 12f, secondary),
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(px(25f), px(12f), px(19f), 0) })
        val records = historyFilter.apply(state.data?.restockingHistory.orEmpty(), LocalDate.now(), ZoneId.systemDefault())
        container.contentDescription = resources.getQuantityString(R.plurals.history_result_count, records.size, records.size)
        if (records.isEmpty()) {
            container.addView(label(str(if (historyFilter == HistoryFilter()) R.string.history_empty else R.string.history_no_results),
                16f, secondary), LinearLayout.LayoutParams(-1, -2).apply { setMargins(px(25f), px(24f), px(19f), 0) })
            return
        }
        records.groupBy { it.occurredAt.atZone(ZoneId.systemDefault()).toLocalDate() }.forEach { (date, group) ->
            val title = when (date) {
                LocalDate.now() -> str(R.string.history_today)
                LocalDate.now().minusDays(1) -> str(R.string.home_yesterday)
                else -> date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale))
            }
            sectionLabel(title, 16f, container)
            group.forEach { record ->
                container.addView(productCard(record), LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(px(19f), px(10f), px(19f), 0)
                })
            }
        }
    }

    private fun showHistoryFilters() {
        close()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24f), px(12f), px(24f), px(12f))
        }
        fun choices(title: Int, viewId: Int, values: Array<String>, selected: Int): Spinner {
            panel.addView(label(str(title), 16f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12f) })
            return Spinner(context, Spinner.MODE_DROPDOWN).apply {
                id = viewId
                adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, values) {
                    init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                        super.getView(position, convertView, parent).also(::applyMontserrat)
                    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                        super.getDropDownView(position, convertView, parent).also(::applyMontserrat)
                }
                setSelection(selected)
                panel.addView(this, LinearLayout.LayoutParams(-1, px(48f)))
            }
        }
        val shelves = state.data?.restockingHistory.orEmpty().map { it.shelfCode }.distinct().sorted()
        val shelfLabels = (listOf(str(R.string.history_all_shelves)) + shelves).toTypedArray()
        val period = choices(R.string.history_period, R.id.history_period, periodLabels(), historyFilter.period.ordinal)
        val shelf = choices(R.string.history_shelf, R.id.history_shelf, shelfLabels,
            (shelves.indexOf(historyFilter.shelfCode) + 1).coerceAtLeast(0))
        val order = choices(R.string.history_order, R.id.history_order,
            arrayOf(str(R.string.history_newest), str(R.string.history_oldest)), if (historyFilter.oldestFirst) 1 else 0)
        dialog = AlertDialog.Builder(activity).setTitle(R.string.history_filters).setView(panel)
            .setPositiveButton(R.string.history_apply) { _, _ ->
                historyFilter = historyFilter.copy(period = HistoryPeriod.entries[period.selectedItemPosition],
                    shelfCode = shelves.getOrNull(shelf.selectedItemPosition - 1), oldestFirst = order.selectedItemPosition == 1)
                renderHistoryResults()
            }
            .setNeutralButton(R.string.history_clear) { _, _ ->
                historyFilter = HistoryFilter()
                findViewById<EditText>(R.id.history_search)?.setText("")
                renderHistoryResults()
            }
            .setNegativeButton(R.string.history_cancel, null).show()
        dialog?.window?.decorView?.let(::applyMontserrat)
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
