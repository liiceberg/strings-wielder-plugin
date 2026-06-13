package com.liiceberg.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.liiceberg.model.StringEntity
import com.liiceberg.strings.detector.Pattern
import com.liiceberg.strings.detector.PatternType
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

class TemplateConfirmDialog(
    project: Project,
    private val entity: StringEntity,
) : DialogWrapper(project) {

    data class TemplateArgument(
        val id: String?,
        val text: String,
        var selected: Boolean = false,
    )

    private val variants = mutableMapOf<Int, List<TemplateArgument>>()

    init {
        title = "Extract as String Template"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 12))
        root.preferredSize = Dimension(650, 450)
        root.border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        val message = JLabel(
            "<html>" +
                    "<b>This string contains format arguments.</b><br>" +
                    "Do you want to extract it as a string template?" +
                    "</html>"
        )
        message.horizontalAlignment = SwingConstants.LEFT

        val textPane = JTextPane()
        textPane.isEditable = false
        textPane.font = Font("JetBrains Mono", Font.PLAIN, 13)
        textPane.background = JBColor.PanelBackground
        textPane.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        val templates = entity.patterns.filter { it.type == PatternType.TEMPLATE }
        val ranges = templates.map { it.range }

        fillWithHighlight(textPane, entity.value, ranges)

        val scrollPane = JBScrollPane(textPane)
        scrollPane.preferredSize = Dimension(600, 80)

        val argumentsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        }

        templates.forEachIndexed { index, pattern ->
            argumentsPanel.add(createArgumentPanel(index + 1, pattern))
            argumentsPanel.add(Box.createVerticalStrut(6))
        }

        val argumentsScroll = JBScrollPane(argumentsPanel).apply {
            preferredSize = Dimension(600, 200)
            border = BorderFactory.createEmptyBorder()
        }

        root.add(message, BorderLayout.NORTH)
        root.add(scrollPane, BorderLayout.CENTER)
        root.add(argumentsScroll, BorderLayout.SOUTH)

        return root
    }

    override fun doOKAction() {
        val templates = entity.patterns.filter { it.type == PatternType.TEMPLATE }
        templates.forEachIndexed { index, pattern ->
            val selectedVariant = variants[index + 1]
                ?.firstOrNull { it.selected }
            pattern.templateFormat = selectedVariant?.id
        }
        super.doOKAction()
    }

    private fun createArgumentPanel(number: Int, pattern: Pattern): JComponent {
        addArgumentsAccordedIndex(number, pattern.templateFormat)

        val panel = JPanel(BorderLayout())
        panel.border = TitledBorder("Argument $number")

        val label = JLabel(pattern.value).apply {
            foreground = JBColor.GRAY
        }

        val wrapper = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
        }

        wrapper.add(label)
        wrapper.add(Box.createVerticalStrut(4))

        val buttonGroup = ButtonGroup()

        variants[number]?.forEach { arg ->
            val radio = JRadioButton(arg.text).apply {
                isSelected = arg.selected
                addActionListener {
                    variants[number]?.forEach { it.selected = false }
                    arg.selected = true
                }
            }

            buttonGroup.add(radio)
            wrapper.add(radio)
        }

        panel.add(wrapper, BorderLayout.CENTER)
        return panel
    }

    private fun fillWithHighlight(
        pane: JTextPane,
        text: String,
        ranges: List<IntRange>
    ) {
        val doc: StyledDocument = pane.styledDocument

        val normal = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, JBColor.foreground())
        }

        val highlight = SimpleAttributeSet().apply {
            StyleConstants.setForeground(
                this,
                JBColor(
                    Color(0x00, 0x5F, 0xC8),   // light theme
                    Color(0x6A, 0xA9, 0xFF),    // dark theme
                )
            )
            StyleConstants.setBold(this, true)
            StyleConstants.setBackground(
                this,
                JBColor(
                    Color(0xDD, 0xEC, 0xFF),   // light theme
                    Color(0x2B, 0x3F, 0x5C),    // dark theme
                )
            )
        }


        var cursor = 0

        for (range in ranges.sortedBy { it.first }) {
            if (range.first > cursor) {
                doc.insertString(
                    doc.length,
                    text.substring(cursor, range.first),
                    normal,
                )
            }

            doc.insertString(
                doc.length,
                text.substring(range.first, range.last + 1),
                highlight,
            )

            cursor = range.last + 1
        }

        if (cursor < text.length) {
            doc.insertString(
                doc.length,
                text.substring(cursor),
                normal,
            )
        }
    }

    private fun addArgumentsAccordedIndex(index: Int, currentSelection: String?) {
        variants[index] = listOf(
            TemplateArgument(id = "%s", text = "Apply string arg (%s)"),
            TemplateArgument(id = "%d", text = "Apply digit arg (%d)"),
            TemplateArgument(id = "%f", text = "Apply float arg (%f)"),
            TemplateArgument(id = null, text = "Keep as literal text"),
        )
        val arguments = variants[index] ?: return
        val selectedId = currentSelection ?: "%s"
        val hasMatch = arguments.any { it.id == selectedId }

        arguments.forEach { argument ->
            argument.selected = if (hasMatch) argument.id == selectedId else argument.id == "%s"
        }
    }
}
