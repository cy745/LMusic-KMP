package com.lalilu.lmusic

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.GridLayout
import java.awt.MouseInfo
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder
import kotlin.io.path.name
import kotlin.system.exitProcess

internal data class DesktopCrashReportRequest(val reportId: String?)

/** A small Swing-only UI that does not initialize Compose, Koin, the database, or the player. */
internal object DesktopCrashReporter {
    fun parseRequest(arguments: Array<String>): DesktopCrashReportRequest? {
        val inline = arguments.firstOrNull { it.startsWith("--crash-report=") }
        if (inline != null) {
            return DesktopCrashReportRequest(inline.substringAfter('=').takeUnless { it == "latest" })
        }

        val flagIndex = arguments.indexOf("--crash-report")
        if (flagIndex < 0) return null
        return DesktopCrashReportRequest(arguments.getOrNull(flagIndex + 1)?.takeUnless { it == "latest" })
    }

    fun show(request: DesktopCrashReportRequest) {
        if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
            System.setProperty("apple.awt.application.appearance", "system")
        }
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

        EventQueue.invokeLater {
            val reportId = request.reportId
                ?.takeIf { DesktopCrashReportStore.readReport(it) != null }
                ?: DesktopCrashReportStore.latestReportId(onlyUnviewed = false)
            val reportText = reportId?.let(DesktopCrashReportStore::readReport)
            DesktopCrashReportFrame(reportId, reportText).isVisible = true
        }
    }
}

private class DesktopCrashReportFrame(
    private val reportId: String?,
    private val reportText: String?,
) : JFrame("LMusic 崩溃反馈") {
    private val colors = CrashReportColors.fromSystem()
    private val hasReport = reportId != null && !reportText.isNullOrBlank()

    init {
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) {
                closeReporter(markViewed = false)
            }
        })
        minimumSize = Dimension(560, 620)
        preferredSize = Dimension(680, 820)
        contentPane = buildContent()
        pack()
        placeOnActiveScreen()
    }

    private fun buildContent(): JPanel = JPanel(BorderLayout()).apply {
        background = colors.background

        val page = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = colors.background
            border = EmptyBorder(28, 30, 26, 30)
        }
        page.add(header())
        page.add(verticalSpace(22))

        if (hasReport) {
            page.add(localOnlyBadge())
            page.add(verticalSpace(12))
            page.add(bodyText("应用已安全退出。你可以先检查错误摘要，再决定是否把反馈包发送给开发者。"))
            page.add(verticalSpace(22))
            page.add(summaryCard())
            page.add(verticalSpace(12))
            page.add(privacyCard())
            page.add(verticalSpace(12))
            page.add(technicalDetailsCard())
            page.add(verticalSpace(20))
            page.add(primaryButton())
            page.add(verticalSpace(10))
            page.add(secondaryActions())
            page.add(verticalSpace(8))
            page.add(closeButton())
        } else {
            page.add(bodyText("本机目前没有可以反馈的崩溃记录。"))
            page.add(verticalSpace(22))
            page.add(card(
                title = "一切运行正常",
                body = "发生崩溃后，LMusic 会将最近的诊断记录保存在这里，并尝试打开独立反馈窗口。",
            ))
            page.add(verticalSpace(20))
            page.add(actionButton("打开 LMusic", primary = true) { restartApplication(markViewed = false) })
            page.add(verticalSpace(8))
            page.add(actionButton("关闭") { closeReporter(markViewed = false) })
        }

        val scrollPane = JScrollPane(page).apply {
            border = BorderFactory.createEmptyBorder()
            viewport.background = colors.background
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 16
        }
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun header(): JPanel = transparentPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 62)

        add(RoundedPanel(colors.accentContainer, radius = 26).apply {
            layout = BorderLayout()
            minimumSize = Dimension(52, 52)
            preferredSize = Dimension(52, 52)
            maximumSize = Dimension(52, 52)
            add(label("!", 28f, colors.accent, Font.BOLD, SwingConstants.CENTER))
        })
        add(Box.createHorizontalStrut(16))
        add(transparentPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(label("崩溃反馈", 12f, colors.accent, Font.BOLD).apply {
                alignmentX = LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(4))
            add(label(
                if (hasReport) "LMusic 遇到了问题" else "暂无崩溃记录",
                27f,
                colors.textPrimary,
                Font.BOLD,
            ).apply { alignmentX = LEFT_ALIGNMENT })
        })
        add(Box.createHorizontalGlue())
    }

    private fun localOnlyBadge(): JPanel = RoundedPanel(colors.localOnlyContainer, radius = 12).apply {
        alignmentX = LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 38)
        layout = FlowLayout(FlowLayout.LEFT, 14, 9)
        add(label("●  仅保存在本机 · 尚未发送", 13f, colors.localOnlyText, Font.BOLD))
    }

    private fun summaryCard(): JPanel = cardPanel().apply {
        add(label("发生了什么", 13f, colors.textSecondary, Font.BOLD))
        add(Box.createVerticalStrut(10))
        add(bodyText(exceptionSummary(), 16f, colors.textPrimary, Font.BOLD))
        reportId?.let { id ->
            add(Box.createVerticalStrut(12))
            add(label(
                "事件编号 · ${id.take(8)}",
                12f,
                colors.textSecondary,
                Font.PLAIN,
                family = Font.MONOSPACED,
            ))
        }
    }

    private fun privacyCard(): JPanel = RoundedPanel(colors.surfaceVariant, radius = 16).apply {
        alignmentX = LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        layout = BorderLayout(12, 0)
        border = EmptyBorder(15, 16, 15, 16)

        add(transparentPanel().apply {
            minimumSize = Dimension(22, 22)
            preferredSize = Dimension(22, 22)
            layout = BorderLayout()
            add(CirclePanel(colors.infoIconContainer).apply {
                minimumSize = Dimension(22, 22)
                preferredSize = Dimension(22, 22)
                maximumSize = Dimension(22, 22)
                layout = BorderLayout()
                add(label("i", 14f, colors.textPrimary, Font.BOLD, SwingConstants.CENTER))
            }, BorderLayout.NORTH)
        }, BorderLayout.WEST)
        add(transparentPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(label("分享前请确认", 14f, colors.textPrimary, Font.BOLD).apply {
                alignmentX = LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(5))
            add(bodyText(
                "反馈包包含调用栈、设备与版本信息及最近日志，日志中可能出现本地文件路径或媒体名称。",
                13f,
            ))
        }, BorderLayout.CENTER)
    }

    private fun technicalDetailsCard(): JPanel {
        val details = JTextArea(reportText.orEmpty()).apply {
            isEditable = false
            lineWrap = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            foreground = colors.textSecondary
            background = colors.surface
            border = EmptyBorder(14, 16, 18, 16)
            caretPosition = 0
        }
        val detailsScroll = JScrollPane(details).apply {
            isVisible = false
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, colors.border)
            preferredSize = Dimension(560, 260)
            horizontalScrollBar.unitIncrement = 16
            verticalScrollBar.unitIncrement = 16
        }
        val toggle = JButton("展开").apply {
            foreground = colors.accent
            font = font.deriveFont(Font.BOLD, 13f)
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = EmptyBorder(14, 16, 14, 16)
            horizontalAlignment = SwingConstants.RIGHT
        }
        val header = transparentPanel().apply {
            layout = BorderLayout()
            add(label("技术详情", 13f, colors.textSecondary, Font.BOLD).apply {
                border = EmptyBorder(0, 16, 0, 0)
            }, BorderLayout.CENTER)
            add(toggle, BorderLayout.EAST)
        }
        val result = RoundedPanel(colors.surface, colors.border, radius = 18).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            layout = BorderLayout()
            add(header, BorderLayout.NORTH)
            add(detailsScroll, BorderLayout.CENTER)
        }
        toggle.addActionListener {
            detailsScroll.isVisible = !detailsScroll.isVisible
            toggle.text = if (detailsScroll.isVisible) "收起" else "展开"
            result.revalidate()
            result.repaint()
        }
        return result
    }

    private fun primaryButton(): JButton = actionButton(
        "<html><center><b>打包并导出反馈</b><br><font size='3'>由你选择保存位置和发送渠道</font></center></html>",
        primary = true,
    ) { exportFeedback() }

    private fun secondaryActions(): JPanel = transparentPanel().apply {
        alignmentX = LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 52)
        layout = GridLayout(1, 2, 10, 0)
        add(actionButton("复制详情") { copyReport() })
        add(actionButton("重新打开") { restartApplication(markViewed = true) })
    }

    private fun closeButton(): JButton = actionButton("暂不反馈，关闭") {
        closeReporter(markViewed = true)
    }

    private fun card(title: String, body: String): JPanel = cardPanel().apply {
        add(label(title, 17f, colors.textPrimary, Font.BOLD))
        add(Box.createVerticalStrut(9))
        add(bodyText(body))
    }

    private fun cardPanel(): JPanel = RoundedPanel(colors.surface, colors.border, radius = 18).apply {
        alignmentX = LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = EmptyBorder(18, 18, 18, 18)
    }

    private fun actionButton(
        text: String,
        primary: Boolean = false,
        action: (ActionEvent) -> Unit,
    ): JButton = RoundedButton(
        text = text,
        backgroundColor = if (primary) colors.accent else colors.surface,
        pressedColor = if (primary) colors.accentPressed else colors.surfacePressed,
        borderColor = if (primary) null else colors.border,
        radius = 17,
    ).apply {
        alignmentX = LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, if (primary) 58 else 52)
        preferredSize = Dimension(240, if (primary) 58 else 52)
        foreground = if (primary) colors.onAccent else colors.textPrimary
        font = font.deriveFont(Font.BOLD, if (primary) 16f else 14f)
        addActionListener(action)
    }

    private fun bodyText(
        text: String,
        size: Float = 15f,
        color: Color = colors.textSecondary,
        style: Int = Font.PLAIN,
    ): JTextArea = JTextArea(text).apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        foreground = color
        font = font.deriveFont(style, size)
        border = BorderFactory.createEmptyBorder()
        alignmentX = LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    }

    private fun label(
        text: String,
        size: Float,
        color: Color,
        style: Int,
        horizontalAlignment: Int = SwingConstants.LEFT,
        family: String? = null,
    ): JLabel = JLabel(text, horizontalAlignment).apply {
        foreground = color
        font = Font(family ?: font.family, style, size.toInt())
    }

    private fun transparentPanel(): JPanel = JPanel().apply { isOpaque = false }

    private fun verticalSpace(height: Int) = Box.createRigidArea(Dimension(0, height))

    private fun exceptionSummary(): String = reportText.orEmpty()
        .lineSequence()
        .firstOrNull { it.startsWith("Exception:") }
        ?.removePrefix("Exception:")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: "LMusic 发生了一个未处理的错误。"

    private fun exportFeedback() {
        val id = reportId ?: return
        val archive = DesktopCrashReportStore.createFeedbackArchive(id)
        if (archive == null) {
            showError("无法生成反馈包。")
            return
        }

        val dialog = FileDialog(this, "保存崩溃反馈包", FileDialog.SAVE).apply {
            file = archive.name
            isVisible = true
        }
        val selectedFile = dialog.file ?: return
        val destination = Path.of(dialog.directory, selectedFile).let { path ->
            if (path.name.endsWith(".zip", ignoreCase = true)) path else path.resolveSibling("${path.name}.zip")
        }
        runCatching { DesktopCrashReportStore.copyArchive(archive, destination) }
            .onSuccess {
                DesktopCrashReportStore.markViewed(id)
                JOptionPane.showMessageDialog(
                    this,
                    "反馈包已保存到：\n$destination",
                    "导出成功",
                    JOptionPane.INFORMATION_MESSAGE,
                )
            }
            .onFailure { showError("无法保存反馈包：${it.message.orEmpty()}") }
    }

    private fun copyReport() {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(
            StringSelection(reportText.orEmpty()),
            null,
        )
        JOptionPane.showMessageDialog(
            this,
            "报错信息已复制。",
            "复制成功",
            JOptionPane.INFORMATION_MESSAGE,
        )
    }

    private fun restartApplication(markViewed: Boolean) {
        if (markViewed) reportId?.let(DesktopCrashReportStore::markViewed)
        if (!DesktopAppLauncher.launchMainApplication()) {
            showError("无法重新打开 LMusic，请从系统应用列表手动启动。")
            return
        }
        dispose()
        exitProcess(0)
    }

    private fun closeReporter(markViewed: Boolean) {
        if (markViewed) reportId?.let(DesktopCrashReportStore::markViewed)
        dispose()
        exitProcess(0)
    }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(this, message, "操作失败", JOptionPane.ERROR_MESSAGE)
    }

    private fun placeOnActiveScreen() {
        val device = MouseInfo.getPointerInfo()?.device
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val configuration = device.defaultConfiguration
        val bounds = configuration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        val availableWidth = bounds.width - insets.left - insets.right
        val availableHeight = bounds.height - insets.top - insets.bottom
        val targetWidth = 680.coerceAtMost(availableWidth)
        val targetHeight = 820.coerceAtMost(availableHeight)
        size = Dimension(targetWidth, targetHeight)
        setLocation(
            bounds.x + insets.left + (availableWidth - targetWidth) / 2,
            bounds.y + insets.top + (availableHeight - targetHeight) / 2,
        )
    }
}

private class RoundedPanel(
    private val fillColor: Color,
    private val strokeColor: Color? = null,
    private val radius: Int,
) : JPanel() {
    init {
        isOpaque = false
    }

    override fun paintComponent(graphics: Graphics) {
        val canvas = graphics.create() as Graphics2D
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        canvas.color = fillColor
        canvas.fillRoundRect(0, 0, width - 1, height - 1, radius * 2, radius * 2)
        strokeColor?.let { color ->
            canvas.color = color
            canvas.drawRoundRect(0, 0, width - 1, height - 1, radius * 2, radius * 2)
        }
        canvas.dispose()
        super.paintComponent(graphics)
    }
}

private class CirclePanel(
    private val fillColor: Color,
) : JPanel() {
    init {
        isOpaque = false
    }

    override fun paintComponent(graphics: Graphics) {
        val canvas = graphics.create() as Graphics2D
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        canvas.color = fillColor
        val diameter = minOf(width, height) - 1
        canvas.fillOval((width - diameter) / 2, (height - diameter) / 2, diameter, diameter)
        canvas.dispose()
        super.paintComponent(graphics)
    }
}

private class RoundedButton(
    text: String,
    private val backgroundColor: Color,
    private val pressedColor: Color,
    private val borderColor: Color?,
    private val radius: Int,
) : JButton(text) {
    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = EmptyBorder(9, 16, 9, 16)
    }

    override fun paintComponent(graphics: Graphics) {
        val canvas = graphics.create() as Graphics2D
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        canvas.color = if (model.isPressed) pressedColor else backgroundColor
        canvas.fillRoundRect(0, 0, width - 1, height - 1, radius * 2, radius * 2)
        borderColor?.let { color ->
            canvas.color = color
            canvas.drawRoundRect(0, 0, width - 1, height - 1, radius * 2, radius * 2)
        }
        canvas.dispose()
        super.paintComponent(graphics)
    }
}

private data class CrashReportColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfacePressed: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentContainer: Color,
    val onAccent: Color,
    val border: Color,
    val localOnlyContainer: Color,
    val localOnlyText: Color,
    val infoIconContainer: Color,
) {
    companion object {
        fun fromSystem(): CrashReportColors {
            val systemBackground = UIManager.getColor("Panel.background") ?: Color.WHITE
            val dark = isSystemDark(systemBackground)
            return if (dark) {
                CrashReportColors(
                    background = Color(17, 18, 22),
                    surface = Color(28, 29, 34),
                    surfaceVariant = Color(37, 38, 44),
                    surfacePressed = Color(46, 47, 54),
                    textPrimary = Color(246, 242, 247),
                    textSecondary = Color(195, 191, 200),
                    accent = Color(255, 133, 142),
                    accentPressed = Color(232, 112, 122),
                    accentContainer = Color(73, 33, 38),
                    onAccent = Color(54, 4, 11),
                    border = Color(55, 56, 64),
                    localOnlyContainer = Color(26, 58, 38),
                    localOnlyText = Color(151, 221, 173),
                    infoIconContainer = Color(58, 59, 67),
                )
            } else {
                CrashReportColors(
                    background = Color(247, 247, 250),
                    surface = Color.WHITE,
                    surfaceVariant = Color(240, 241, 245),
                    surfacePressed = Color(235, 234, 239),
                    textPrimary = Color(34, 31, 38),
                    textSecondary = Color(99, 95, 105),
                    accent = Color(199, 50, 64),
                    accentPressed = Color(174, 42, 55),
                    accentContainer = Color(255, 229, 232),
                    onAccent = Color.WHITE,
                    border = Color(226, 224, 231),
                    localOnlyContainer = Color(229, 246, 234),
                    localOnlyText = Color(37, 105, 58),
                    infoIconContainer = Color(222, 224, 232),
                )
            }
        }

        private fun isSystemDark(systemBackground: Color): Boolean {
            if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
                val macAppearance = runCatching {
                    ProcessBuilder("/usr/bin/defaults", "read", "-g", "AppleInterfaceStyle")
                        .redirectErrorStream(true)
                        .start()
                        .inputStream
                        .bufferedReader()
                        .use { it.readText() }
                }.getOrNull()
                if (macAppearance != null) return macAppearance.contains("Dark", ignoreCase = true)
            }
            return systemBackground.red + systemBackground.green + systemBackground.blue < 384
        }
    }
}
