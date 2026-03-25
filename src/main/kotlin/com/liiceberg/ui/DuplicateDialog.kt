package com.liiceberg.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.liiceberg.strings.StringsXmlManager
import com.liiceberg.ui.entity.HardcodedStringEntity
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.TitledBorder

class DuplicateDialog(
    project: Project,
    private val entity: HardcodedStringEntity,
) : DialogWrapper(project) {

    private val buttonGroup = ButtonGroup()
    private val keepSeparateButton = JRadioButton("Keep separate resource")


    init {
        title = "POSSIBLE DUPLICATE"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 12)).apply {
            preferredSize = Dimension(700, 450)
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }

        val header = JLabel(
            "<html><b>Possible duplicate found.</b></html>"
        )

        root.add(header, BorderLayout.NORTH)

        val centerPanel = JPanel()
        centerPanel.layout = BoxLayout(centerPanel, BoxLayout.Y_AXIS)

        centerPanel.add(createCurrentPanel())
        centerPanel.add(Box.createVerticalStrut(12))
        centerPanel.add(createExistingPanel())
        centerPanel.add(Box.createVerticalStrut(12))
        centerPanel.add(createQuestionPanel())

        root.add(centerPanel, BorderLayout.CENTER)

        return root
    }

    private fun createCurrentPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = TitledBorder("Current")

        val c = GridBagConstraints().apply {
            insets = JBUI.insets(4, 8)
            anchor = GridBagConstraints.WEST
        }

        c.gridx = 0
        c.gridy = 0
        panel.add(JLabel("\"${entity.value}\""), c)

        c.gridy = 1
        panel.add(JLabel("Key: ${entity.key}").apply {
            foreground = JBColor.GRAY
        }, c)

        return panel
    }

    private fun createExistingPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = TitledBorder("Existing")

        val listPanel = JPanel()
        listPanel.layout = BoxLayout(listPanel, BoxLayout.Y_AXIS)

        entity.duplicateOf?.forEach {

            val key = it.tag.getAttributeValue(StringsXmlManager.NAME_TAG_ATTRIBUTE) ?: ""
            val value = when (it.tag.name) {
                StringsXmlManager.STRING_TAG -> {
                    it.tag.value.text
                }
                StringsXmlManager.PLURAL_TAG -> {
                    val builder = StringBuilder()
                    builder.append("(plural) ")
                    for (item in it.tag.findSubTags(StringsXmlManager.ITEM_TAG)) {
                        item.getAttributeValue(StringsXmlManager.QUANTITY_TAG_ATTRIBUTE)?.let { attr ->
                            builder.append("$attr = ${item.value.text}")
                            builder.append(", ")
                        }
                    }
                    builder.toString().trim().trim(',')
                }
                else -> ""
            }

            val radio = JRadioButton("Use existing resource: \"${value}\"")
            radio.addActionListener {
                entity.key = key
                entity.value = value
            }
            buttonGroup.add(radio)

            val label = JLabel("Key: $key").apply {
                foreground = JBColor.GRAY
            }

            val wrapper = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
                alignmentX = JComponent.LEFT_ALIGNMENT
            }

            radio.alignmentX = JComponent.LEFT_ALIGNMENT
            label.alignmentX = JComponent.LEFT_ALIGNMENT

            wrapper.add(radio)
            wrapper.add(Box.createVerticalStrut(2))
            wrapper.add(label)

            listPanel.add(wrapper)
        }

        val scrollPane = JBScrollPane(listPanel).apply {
            preferredSize = Dimension(600, 200)
        }

        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun createQuestionPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = TitledBorder("Create")

        keepSeparateButton.isSelected = true

        buttonGroup.add(keepSeparateButton)
        panel.add(keepSeparateButton)

        return panel
    }

}
