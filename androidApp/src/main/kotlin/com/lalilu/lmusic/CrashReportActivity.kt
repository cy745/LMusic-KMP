package com.lalilu.lmusic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** A small native Android UI that remains usable after the main app process crashes. */
class CrashReportActivity : ComponentActivity() {
    private lateinit var reportId: String
    private lateinit var reportText: String
    private lateinit var colors: CrashReportColors
    private var hasReport: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        colors = CrashReportColors.from(resources.configuration)
        loadReport(intent)
        configureWindow()
        renderContent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadReport(intent)
        renderContent()
    }

    private fun loadReport(sourceIntent: Intent) {
        val requestedReportId = sourceIntent.getStringExtra(EXTRA_REPORT_ID).orEmpty()
        val requestedReport = CrashReportStore.readReport(this, requestedReportId)
        reportId = if (requestedReport != null) {
            requestedReportId
        } else {
            CrashReportStore.latestReportId(this).orEmpty()
        }
        reportText = CrashReportStore.readReport(this, reportId).orEmpty()
        hasReport = reportText.isNotBlank()
        if (hasReport) {
            CrashReportStore.markViewed(this, reportId)
        } else {
            reportText = getString(R.string.crash_report_missing)
        }
    }

    private fun renderContent() {
        val content = createContentView()
        setContentView(content)
        applySystemBarInsets(content)
    }

    private fun configureWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = if (
            !colors.isDark && Build.VERSION.SDK_INT < Build.VERSION_CODES.O
        ) {
            Color.BLACK
        } else {
            colors.background
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !colors.isDark
            isAppearanceLightNavigationBars = !colors.isDark
        }
    }

    private fun applySystemBarInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun createContentView(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(colors.background)
        }
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(24))
        }

        page.addView(createHeader())
        if (hasReport) {
            page.addView(createLocalOnlyBadge(), matchWidth(topMargin = 22))
            page.addView(
                bodyText(R.string.crash_report_description),
                matchWidth(topMargin = 12),
            )
            page.addView(createSummaryCard(), matchWidth(topMargin = 22))
            page.addView(createPrivacyCard(), matchWidth(topMargin = 12))
            page.addView(createTechnicalDetailsCard(), matchWidth(topMargin = 12))
            page.addView(createPrimaryAction(), matchWidth(topMargin = 20))
            page.addView(createSecondaryActions(), matchWidth(topMargin = 10))
            page.addView(createCloseAction(), matchWidth(topMargin = 8))
        } else {
            page.addView(
                bodyText(R.string.crash_report_empty_description),
                matchWidth(topMargin = 22),
            )
            page.addView(createEmptyStateCard(), matchWidth(topMargin = 22))
            page.addView(createOpenAppAction(), matchWidth(topMargin = 20))
            page.addView(createCloseAction(), matchWidth(topMargin = 8))
        }

        scrollView.addView(
            page,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            scrollView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return root
    }

    private fun createHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        addView(
            TextView(this@CrashReportActivity).apply {
                text = "!"
                gravity = Gravity.CENTER
                textSize = 28f
                setTextColor(colors.accent)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                includeFontPadding = false
                background = roundedBackground(colors.accentContainer, 26)
            },
            LinearLayout.LayoutParams(dp(52), dp(52)),
        )

        addView(
            LinearLayout(this@CrashReportActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@CrashReportActivity).apply {
                    text = getString(R.string.crash_report_eyebrow)
                    textSize = 12f
                    letterSpacing = 0.08f
                    setTextColor(colors.accent)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    includeFontPadding = false
                })
                addView(TextView(this@CrashReportActivity).apply {
                    text = getString(
                        if (hasReport) {
                            R.string.crash_report_title
                        } else {
                            R.string.crash_report_empty_title
                        },
                    )
                    textSize = 27f
                    setTextColor(colors.textPrimary)
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    includeFontPadding = false
                    setPadding(0, dp(4), 0, 0)
                })
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(16)
            },
        )
    }

    private fun createLocalOnlyBadge(): View = TextView(this).apply {
        text = getString(R.string.crash_report_local_only)
        textSize = 13f
        setTextColor(colors.localOnlyText)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = roundedBackground(colors.localOnlyContainer, 12)
    }

    private fun createEmptyStateCard(): View = card().apply {
        addView(TextView(this@CrashReportActivity).apply {
            text = getString(R.string.crash_report_empty_card_title)
            textSize = 17f
            setTextColor(colors.textPrimary)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        })
        addView(TextView(this@CrashReportActivity).apply {
            text = getString(R.string.crash_report_empty_card_description)
            textSize = 14f
            setTextColor(colors.textSecondary)
            setLineSpacing(0f, 1.12f)
            setPadding(0, dp(9), 0, 0)
        })
    }

    private fun createSummaryCard(): View = card().apply {
        addView(sectionLabel(R.string.crash_report_summary_title))
        addView(TextView(this@CrashReportActivity).apply {
            text = exceptionSummary()
            textSize = 16f
            setTextColor(colors.textPrimary)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setLineSpacing(0f, 1.12f)
            setPadding(0, dp(10), 0, 0)
        })

        if (reportId.isNotBlank()) {
            addView(TextView(this@CrashReportActivity).apply {
                text = getString(
                    R.string.crash_report_event_id,
                    reportId.take(EVENT_ID_PREVIEW_LENGTH),
                )
                textSize = 12f
                setTextColor(colors.textSecondary)
                typeface = Typeface.MONOSPACE
                includeFontPadding = false
                setPadding(0, dp(12), 0, 0)
            })
        }
    }

    private fun createPrivacyCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = roundedBackground(colors.surfaceVariant, 16)

        addView(TextView(this@CrashReportActivity).apply {
            text = "i"
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(colors.textPrimary)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            includeFontPadding = false
            background = roundedBackground(colors.infoIconContainer, 10)
        }, LinearLayout.LayoutParams(dp(20), dp(20)))

        addView(LinearLayout(this@CrashReportActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@CrashReportActivity).apply {
                text = getString(R.string.crash_report_privacy_title)
                textSize = 14f
                setTextColor(colors.textPrimary)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
            })
            addView(TextView(this@CrashReportActivity).apply {
                text = getString(R.string.crash_report_privacy_description)
                textSize = 13f
                setTextColor(colors.textSecondary)
                setLineSpacing(0f, 1.12f)
                setPadding(0, dp(5), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
        })
    }

    private fun createTechnicalDetailsCard(): View {
        val card = card(horizontalPadding = 0, verticalPadding = 0)
        val details = TextView(this).apply {
            text = reportText
            textSize = 11.5f
            setTextColor(colors.textSecondary)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(16), dp(14), dp(16), dp(18))
            visibility = View.GONE
        }
        val divider = View(this).apply {
            setBackgroundColor(colors.border)
            visibility = View.GONE
        }
        val toggleLabel = TextView(this).apply {
            text = getString(R.string.crash_report_details_expand)
            textSize = 13f
            setTextColor(colors.accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        }
        val toggle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumHeight = dp(54)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            // The outer card owns the outline. A second rounded background here would leave
            // inward-facing bottom corners and a doubled border while details are expanded.
            background = rippleBackground(Color.TRANSPARENT, 0)
            contentDescription = getString(R.string.crash_report_details_expand)

            addView(sectionLabel(R.string.crash_report_details_title), LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ))
            addView(toggleLabel)

            setOnClickListener {
                val expanded = details.visibility != View.VISIBLE
                details.visibility = if (expanded) View.VISIBLE else View.GONE
                divider.visibility = details.visibility
                toggleLabel.text = getString(
                    if (expanded) {
                        R.string.crash_report_details_collapse
                    } else {
                        R.string.crash_report_details_expand
                    },
                )
                contentDescription = toggleLabel.text
                announceForAccessibility(toggleLabel.text)
            }
        }

        card.addView(toggle)
        card.addView(divider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1),
        ))
        card.addView(details)
        return card
    }

    private fun createPrimaryAction(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = dp(58)
        isClickable = true
        isFocusable = true
        setPadding(dp(16), dp(11), dp(16), dp(11))
        background = rippleBackground(colors.accent, 17, colors.onAccent)
        contentDescription = getString(R.string.crash_report_share_accessibility)
        setOnClickListener { shareFeedback() }

        addView(TextView(this@CrashReportActivity).apply {
            text = getString(R.string.crash_report_share)
            textSize = 16f
            setTextColor(colors.onAccent)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        })
        addView(TextView(this@CrashReportActivity).apply {
            text = getString(R.string.crash_report_share_hint)
            textSize = 12f
            setTextColor(colors.onAccentSecondary)
            includeFontPadding = false
            setPadding(0, dp(3), 0, 0)
        })
    }

    private fun createOpenAppAction(): View = TextView(this).apply {
        text = getString(R.string.crash_report_open_app)
        textSize = 16f
        setTextColor(colors.onAccent)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        minimumHeight = dp(56)
        isClickable = true
        isFocusable = true
        background = rippleBackground(colors.accent, 17, colors.onAccent)
        setOnClickListener { restartApp() }
    }

    private fun createSecondaryActions(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(
            actionButton(R.string.crash_report_copy) { copyReport() },
            LinearLayout.LayoutParams(0, dp(52), 1f),
        )
        addView(
            actionButton(R.string.crash_report_restart) { restartApp() },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginStart = dp(10)
            },
        )
    }

    private fun createCloseAction(): View = TextView(this).apply {
        text = getString(
            if (hasReport) R.string.crash_report_close else R.string.crash_report_close_plain,
        )
        textSize = 14f
        setTextColor(colors.textSecondary)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        minimumHeight = dp(48)
        isClickable = true
        isFocusable = true
        background = rippleBackground(Color.TRANSPARENT, 16)
        setOnClickListener { finishAndRemoveTask() }
    }

    private fun actionButton(label: Int, onClick: () -> Unit): View = TextView(this).apply {
        text = getString(label)
        textSize = 14f
        setTextColor(colors.textPrimary)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        background = rippleBackground(colors.surface, 16)
        setOnClickListener { onClick() }
    }

    private fun card(
        horizontalPadding: Int = 18,
        verticalPadding: Int = 18,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        clipToOutline = true
        setPadding(
            dp(horizontalPadding),
            dp(verticalPadding),
            dp(horizontalPadding),
            dp(verticalPadding),
        )
        background = roundedBackground(colors.surface, 18, colors.border)
    }

    private fun sectionLabel(label: Int): TextView = TextView(this).apply {
        text = getString(label)
        textSize = 13f
        setTextColor(colors.textSecondary)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
    }

    private fun bodyText(label: Int): TextView = TextView(this).apply {
        text = getString(label)
        textSize = 15f
        setTextColor(colors.textSecondary)
        setLineSpacing(0f, 1.12f)
    }

    private fun exceptionSummary(): String = reportText
        .lineSequence()
        .firstOrNull { it.startsWith(EXCEPTION_PREFIX) }
        ?.removePrefix(EXCEPTION_PREFIX)
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: getString(R.string.crash_report_summary_unknown)

    private fun roundedBackground(
        color: Int,
        radius: Int,
        strokeColor: Int? = null,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }

    private fun rippleBackground(
        color: Int,
        radius: Int,
        rippleBase: Int = colors.textPrimary,
    ): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(rippleColor(rippleBase)),
        roundedBackground(color, radius, colors.border.takeIf { color == colors.surface }),
        null,
    )

    private fun rippleColor(color: Int): Int = Color.argb(
        if (colors.isDark) 54 else 32,
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun matchWidth(topMargin: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            this.topMargin = dp(topMargin)
        }

    private fun shareFeedback() {
        val archive = CrashReportStore.createFeedbackArchive(this, reportId)
        if (archive == null) {
            Toast.makeText(this, R.string.crash_report_share_failed, Toast.LENGTH_LONG).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.crash-files",
            archive,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_share_subject))
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, archive.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.crash_report_share)))
    }

    private fun copyReport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("LMusic crash report", reportText))
        Toast.makeText(this, R.string.crash_report_copied, Toast.LENGTH_SHORT).show()
    }

    private fun restartApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(launchIntent)
        }
        finishAndRemoveTask()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_REPORT_ID = "crash_report_id"
        private const val EXCEPTION_PREFIX = "Exception:"
        private const val EVENT_ID_PREVIEW_LENGTH = 8

        fun intent(context: Context, reportId: String): Intent =
            Intent(context, CrashReportActivity::class.java).apply {
                putExtra(EXTRA_REPORT_ID, reportId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
            }
    }
}

private data class CrashReportColors(
    val isDark: Boolean,
    val background: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val accentContainer: Int,
    val onAccent: Int,
    val onAccentSecondary: Int,
    val border: Int,
    val localOnlyContainer: Int,
    val localOnlyText: Int,
    val infoIconContainer: Int,
) {
    companion object {
        fun from(configuration: Configuration): CrashReportColors {
            val dark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            return if (dark) {
                CrashReportColors(
                    isDark = true,
                    background = Color.rgb(17, 18, 22),
                    surface = Color.rgb(28, 29, 34),
                    surfaceVariant = Color.rgb(37, 38, 44),
                    textPrimary = Color.rgb(246, 242, 247),
                    textSecondary = Color.rgb(195, 191, 200),
                    accent = Color.rgb(255, 133, 142),
                    accentContainer = Color.rgb(73, 33, 38),
                    onAccent = Color.rgb(54, 4, 11),
                    onAccentSecondary = Color.rgb(93, 31, 39),
                    border = Color.rgb(55, 56, 64),
                    localOnlyContainer = Color.rgb(26, 58, 38),
                    localOnlyText = Color.rgb(151, 221, 173),
                    infoIconContainer = Color.rgb(58, 59, 67),
                )
            } else {
                CrashReportColors(
                    isDark = false,
                    background = Color.rgb(247, 247, 250),
                    surface = Color.WHITE,
                    surfaceVariant = Color.rgb(240, 241, 245),
                    textPrimary = Color.rgb(34, 31, 38),
                    textSecondary = Color.rgb(99, 95, 105),
                    accent = Color.rgb(199, 50, 64),
                    accentContainer = Color.rgb(255, 229, 232),
                    onAccent = Color.WHITE,
                    onAccentSecondary = Color.rgb(255, 229, 232),
                    border = Color.rgb(226, 224, 231),
                    localOnlyContainer = Color.rgb(229, 246, 234),
                    localOnlyText = Color.rgb(37, 105, 58),
                    infoIconContainer = Color.rgb(222, 224, 232),
                )
            }
        }
    }
}
