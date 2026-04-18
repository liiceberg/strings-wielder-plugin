package com.liiceberg.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.liiceberg.model.PluralResource
import com.liiceberg.strings.detector.PluralDetector
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.utils.Constants
import java.awt.*
import javax.swing.*

class PluralDialog(
    project: Project,
    private val entity: HardcodedStringEntity
) : DialogWrapper(project) {

    private val zeroField = JTextField()
    private val oneField = JTextField()
    private val fewField = JTextField()
    private val manyField = JTextField()
    private val otherField = JTextField()
    private val currentNumberField = JTextField(PluralDetector.getNumber(entity.value))

    private val keyField = JTextField(entity.key)

    private val errorLabel = JLabel("").apply {
        foreground = JBColor.RED
    }

    init {
        title = "EXTRACT AS <plurals>"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 10))
        root.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        root.minimumSize = Dimension(400, 400)

        val originalPanel = JPanel(BorderLayout())
        originalPanel.add(JLabel("Original string:"), BorderLayout.NORTH)
        originalPanel.add(
            JLabel("\"${entity.value}\"").apply {
                border = BorderFactory.createEmptyBorder(4, 10, 8, 0)
            },
            BorderLayout.CENTER
        )

        val pluralPanel = createPluralFormsPanel()

        val keyPanel = JPanel(GridLayout(1, 2, 8, 8))
        keyPanel.border = BorderFactory.createTitledBorder("Key")
        keyPanel.add(keyField)

        val errorPanel = JPanel(BorderLayout())
        errorPanel.add(errorLabel, BorderLayout.CENTER)

        val center = JPanel()
        center.layout = BoxLayout(center, BoxLayout.Y_AXIS)
        center.add(originalPanel)
        center.add(Box.createVerticalStrut(8))
        center.add(pluralPanel)
        center.add(Box.createVerticalStrut(8))
        center.add(keyPanel)
        center.add(Box.createVerticalStrut(8))
        center.add(errorPanel)

        root.add(center, BorderLayout.CENTER)
        return root
    }

    override fun doOKAction() {
        errorLabel.text = ""

        if (otherField.text.isBlank()) {
            errorLabel.text = "Field 'other' required for any plural resource."
            return
        }

        if (!keyField.text.matches(Constants.RegexTemplates.KEY_REGEX)) {
            errorLabel.text = "Invalid resource key format."
            return
        }

        if (currentNumberField.text.isBlank()) {
            errorLabel.text = "Field 'current number' cannot be empty."
            return
        }

        if (!currentNumberField.text.matches(Constants.RegexTemplates.DIGIT_REGEX)) {
            errorLabel.text = "Invalid number format."
            return
        }

        entity.key = keyField.text
        entity.pluralForm = PluralResource(
            zero = zeroField.text,
            one = oneField.text,
            few = fewField.text,
            many = manyField.text,
            other = otherField.text,
            currentNumber = currentNumberField.text.trim().toInt(),
        )

        super.doOKAction()
    }

    private fun createPluralFormsPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder("Plural forms")

        val c = GridBagConstraints().apply {
            insets = JBUI.insets(4, 12)
            anchor = GridBagConstraints.WEST
        }

        fun addRow(row: Int, label: String, field: JTextField, required: Boolean = false) {
            c.gridx = 0
            c.gridy = row
            c.weightx = 0.0
            c.fill = GridBagConstraints.NONE
            panel.add(
                JLabel(
                    if (required) "$label*" else label
                ).apply {
                    horizontalAlignment = SwingConstants.RIGHT
                },
                c
            )

            c.gridx = 1
            c.gridy = row
            c.weightx = 1.0
            c.fill = GridBagConstraints.HORIZONTAL
            panel.add(field, c)
        }

        addRow(0, "zero:", zeroField)
        addRow(1, "one:", oneField)
        addRow(2, "few:", fewField)
        addRow(3, "many:", manyField)
        addRow(4, "other:", otherField, required = true)
        addRow(5,"current number:", currentNumberField, required = true)

        return panel
    }

}

