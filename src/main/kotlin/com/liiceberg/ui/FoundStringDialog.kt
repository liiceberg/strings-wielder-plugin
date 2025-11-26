package com.liiceberg.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.utils.Constants
import com.liiceberg.utils.strings.StringResourceReplacer
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.TableCellRenderer

class FoundStringDialog(
    project: Project,
    private val entries: List<HardcodedStringEntity>,
) : DialogWrapper(project) {

    private val replacer =  StringResourceReplacer(project, entries)
    private val tableModel = FoundStringTableModel(entries)
    private val stringsTable = JBTable(tableModel)
    private val errorLabel = JLabel("").apply {
        foreground = JBColor.RED
    }

    init {
        title = Constants.Titles.HARDCODED_STRINGS_FOUND_TABLE
        init()
    }

    override fun doOKAction() {

        if (errorLabel.text.isNotEmpty()) return

        ApplicationManager.getApplication().invokeLater {
            replacer.replace()
            super.doOKAction()
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.minimumSize = Dimension(800, 600)

        errorLabel.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)

        stringsTable.apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS

            columnModel.getColumn(0).apply {
                minWidth = 150
                maxWidth = 300
                resizable = false
            }

            columnModel.getColumn(2).apply {
                val editorCheckBox = JCheckBox().apply {
                    horizontalAlignment = SwingConstants.CENTER
                }
                cellEditor = DefaultCellEditor(editorCheckBox)

                cellRenderer = TableCellRenderer { table, value, isSelected, _, row, column ->
                    JCheckBox().apply {
                        setSelected(value as? Boolean ?: false)
                        horizontalAlignment = SwingConstants.CENTER
                        isOpaque = true
                        background = if (isSelected) table.selectionBackground else table.background
                        isEnabled = table.isEnabled
                        border = BorderFactory.createEmptyBorder(
                            0,
                            (table.getCellRect(row, column, false).width - preferredSize.width) / 2,
                            0,
                            0
                        )
                    }
                }
            }

            setDefaultEditor(String::class.java, object : DefaultCellEditor(JTextField()) {
                override fun stopCellEditing(): Boolean {
                    val col = editingColumn
                    val value = (component as JTextField).text
                    return if (col == 0 && !value.matches(Constants.RegexTemplates.KEY_REGEX)) {
                        errorLabel.text = Constants.Labels.INVALID_KEY_FILED
                        false
                    } else {
                        errorLabel.text = ""
                        super.stopCellEditing()
                    }
                }

            })

            if (entries.any { entry -> !entry.key.matches(Constants.RegexTemplates.KEY_REGEX) }) {
                errorLabel.text = Constants.Labels.INVALID_KEYS_FOUND
            }

            val scrollPane = JBScrollPane(stringsTable)
            val wrapper = JPanel()
            wrapper.layout = BoxLayout(wrapper, BoxLayout.Y_AXIS)
            wrapper.add(scrollPane)
            wrapper.add(errorLabel)

            panel.add(wrapper, BorderLayout.CENTER)

            return panel
        }

    }
}