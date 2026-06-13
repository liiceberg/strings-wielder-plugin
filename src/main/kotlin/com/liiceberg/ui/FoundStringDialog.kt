package com.liiceberg.ui

import ai.grazie.text.TextRange
import ai.grazie.text.replace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.liiceberg.model.StringEntity
import com.liiceberg.model.SuggestionType
import com.liiceberg.strings.SearchUtil
import com.liiceberg.strings.StringResourceReplacer
import com.liiceberg.strings.detector.PatternType
import com.liiceberg.strings.translator.SupportedAppLanguage
import com.liiceberg.utils.Constants
import com.liiceberg.utils.LocalStorage
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

class FoundStringDialog(
    private val project: Project,
    private val entries: List<StringEntity>,
    private val searchUtil: SearchUtil = SearchUtil(project),
) : DialogWrapper(project) {

    private val duplicateRefreshRequests = mutableMapOf<Int, Int>()
    private val tableModel = FoundStringTableModel(entries, ::onLanguageChanged, ::refreshValidationError)
    private val stringsTable = JBTable(tableModel)
    private val baseLanguageComboBox = ComboBox(
        SupportedAppLanguage.baseLanguageValues.toTypedArray()
    ).apply {
        selectedItem = getSavedBaseLanguage()
        preferredSize = Dimension(180, preferredSize.height)
        maximumSize = Dimension(200, preferredSize.height)
    }
    private val errorLabel = JLabel("").apply {
        foreground = JBColor.RED
    }

    init {
        title = "Hardcoded Strings Found"
        tableModel.addTableModelListener {
            refreshValidationError()
        }
        init()
    }

    override fun dispose() {
        duplicateRefreshRequests.clear()
        super.dispose()
    }

    override fun doOKAction() {
        if (!validateEntries()) {
            return
        }
        val baseLanguage = baseLanguageComboBox.selectedItem as? SupportedAppLanguage
            ?: SupportedAppLanguage.ENGLISH
        LocalStorage.setData(Constants.Preferences.BASE_LANGUAGE, baseLanguage.name)

        super.doOKAction()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Extracting string resources", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Extracting string resources"
                runBlocking {
                    StringResourceReplacer(project, entries, baseLanguage).replace { description ->
                        indicator.text = "Translating localized resources"
                        indicator.text2 = "Translating: ${description.take(80)}"
                    }
                }
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(
                    project,
                    error.message ?: "Failed to extract string resources.",
                    "String-Wielder"
                )
            }
        })
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.minimumSize = Dimension(1000, 600)

        errorLabel.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
        val controlsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 0, 10)
            add(JLabel("Base language for values/strings.xml"))
            add(baseLanguageComboBox)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        stringsTable.apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS

            columnModel.getColumn(0).apply {
                minWidth = 150
                maxWidth = 300
                resizable = false
            }

            columnModel.getColumn(2).apply {
                minWidth = 150
                maxWidth = 220
                cellRenderer = object : DefaultTableCellRenderer() {
                    override fun getTableCellRendererComponent(
                        table: JTable,
                        value: Any?,
                        isSelected: Boolean,
                        hasFocus: Boolean,
                        row: Int,
                        column: Int
                    ): Component {
                        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                        text = (value as? SupportedAppLanguage)?.displayName ?: UNKNOWN_LANGUAGE_LABEL
                        return this
                    }
                }
                cellEditor = DefaultCellEditor(
                    JComboBox(
                        arrayOf(
                            null,
                            *SupportedAppLanguage.dropdownValues.toTypedArray()
                        )
                    ).apply {
                        renderer = object : DefaultListCellRenderer() {
                            override fun getListCellRendererComponent(
                                list: JList<*>?,
                                value: Any?,
                                index: Int,
                                isSelected: Boolean,
                                cellHasFocus: Boolean
                            ): Component {
                                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                                text = (value as? SupportedAppLanguage)?.displayName ?: UNKNOWN_LANGUAGE_LABEL
                                return this
                            }
                        }
                    }
                )
            }

            columnModel.getColumn(3).apply {
                cellRenderer = object : JCheckBox(), TableCellRenderer {

                    init {
                        horizontalAlignment = SwingConstants.CENTER
                        isOpaque = true
                    }

                    override fun getTableCellRendererComponent(
                        table: JTable,
                        value: Any?,
                        isSelected: Boolean,
                        hasFocus: Boolean,
                        row: Int,
                        column: Int
                    ): Component {
                        this.isSelected = value as? Boolean ?: false
                        background = if (isSelected) table.selectionBackground else table.background
                        return this
                    }
                }
                val checkBoxEditor = JCheckBox().apply {
                    horizontalAlignment = SwingConstants.CENTER
                    isOpaque = true
                }
                cellEditor = DefaultCellEditor(checkBoxEditor)
            }

            stringsTable.columnModel.getColumn(4).apply {
                minWidth = 180
                cellRenderer = SuggestionCellRenderer()
            }

            stringsTable.columnModel.getColumn(5).apply {
                cellRenderer = ActionButtonRenderer()
                cellEditor = ActionButtonEditor(this@FoundStringDialog)
                minWidth = 100
                maxWidth = 120
            }

            setDefaultEditor(String::class.java, object : DefaultCellEditor(JTextField()) {
                override fun stopCellEditing(): Boolean {
                    val col = editingColumn
                    val value = (component as JTextField).text
                    val entity = entries.getOrNull(editingRow)
                    val shouldValidateKey = col == 0 && entity?.isSelected == true && entity.existingResource == null

                    return if (shouldValidateKey && !value.matches(Constants.RegexTemplates.KEY_REGEX)) {
                        errorLabel.text = "The key can consist of letters, numbers, and _"
                        false
                    } else {
                        refreshValidationError()
                        super.stopCellEditing()
                    }
                }

            })

            refreshValidationError()

            val scrollPane = JBScrollPane(stringsTable)
            val wrapper = JPanel()
            wrapper.layout = BoxLayout(wrapper, BoxLayout.Y_AXIS)
            wrapper.add(controlsPanel)
            wrapper.add(Box.createVerticalStrut(8))
            wrapper.add(scrollPane)
            wrapper.add(errorLabel)

            panel.add(wrapper, BorderLayout.CENTER)

            return panel
        }

    }

    fun onActionClicked(row: Int) {
        val entity = entries[row]

        when {
            SuggestionType.PLURAL in entity.suggestions -> {
                if (PluralDialog(project, entity).showAndGet()) {
                    entity.suggestions.remove(SuggestionType.PLURAL)
                }
            }

            SuggestionType.DUPLICATE in entity.suggestions -> {
                if (DuplicateDialog(project, entity).showAndGet()) {
                    entity.suggestions.remove(SuggestionType.DUPLICATE)
                }
            }

            SuggestionType.TEMPLATE in entity.suggestions -> {
                if (TemplateConfirmDialog(project, entity).showAndGet()) {
                    entity.value = buildTemplateResourceValue(entity)
                    entity.suggestions.remove(SuggestionType.TEMPLATE)
                }
            }
        }

        tableModel.fireTableRowsUpdated(row, row)
        refreshValidationError()
    }

    private fun onLanguageChanged(row: Int, language: SupportedAppLanguage?) {
        val requestId = (duplicateRefreshRequests[row] ?: 0) + 1
        duplicateRefreshRequests[row] = requestId
        val entity = entries[row]

        ApplicationManager.getApplication().executeOnPooledThread {
            val duplicates = findDuplicates(entity, language)
            ApplicationManager.getApplication().invokeLater({
                if (isDisposed || duplicateRefreshRequests[row] != requestId) {
                    return@invokeLater
                }
                applyDuplicateState(entity, duplicates)
                tableModel.fireTableRowsUpdated(row, row)
            }, ModalityState.stateForComponent(stringsTable))
        }
    }

    private fun findDuplicates(
        entity: StringEntity,
        sourceLanguage: SupportedAppLanguage?,
    ): List<SearchUtil.SearchResult> {
        return searchUtil.deepSearch(
            module = entity.module,
            string = entity.value,
            sourceLanguage = sourceLanguage,
        )
    }

    private fun applyDuplicateState(
        entity: StringEntity,
        duplicates: List<SearchUtil.SearchResult>,
    ) {
        entity.duplicateOf = duplicates.ifEmpty { null }
        if (duplicates.isNotEmpty()) {
            entity.suggestions.add(SuggestionType.DUPLICATE)
        } else {
            entity.suggestions.remove(SuggestionType.DUPLICATE)
        }

        entity.existingResource?.let { selected ->
            val selectedStillAvailable = duplicates.any {
                it.module.name == selected.module.name &&
                        it.packageName == selected.packageName &&
                        it.key == selected.key &&
                        it.resourceType == selected.resourceType
            }
            if (!selectedStillAvailable) {
                entity.existingResource = null
            }
        }
    }

    private fun validateEntries(): Boolean {
        val hasInvalidSelectedEntries = entries.any { entry ->
            entry.isSelected &&
                    entry.existingResource == null &&
                    !entry.key.matches(Constants.RegexTemplates.KEY_REGEX)
        }
        refreshValidationError()
        return !hasInvalidSelectedEntries
    }

    private fun refreshValidationError() {
        val hasInvalidSelectedEntries = entries.any { entry ->
            entry.isSelected &&
                    entry.existingResource == null &&
                    !entry.key.matches(Constants.RegexTemplates.KEY_REGEX)
        }

        errorLabel.text = if (hasInvalidSelectedEntries) {
            "Attention: incorrect keys were found. Edit them before continuing."
        } else ""
    }

    private fun buildTemplateResourceValue(entity: StringEntity): String {
        val templatePatterns = entity.patterns
            .filter { it.type == PatternType.TEMPLATE }
            .sortedByDescending { it.range.first }

        var result = entity.sourceValue
        templatePatterns.forEach { pattern ->
            val replacement = pattern.templateFormat ?: pattern.value
            result = result.replace(
                TextRange(pattern.range.first, pattern.range.last + 1),
                replacement
            )
        }
        return result
    }

    private fun getSavedBaseLanguage(): SupportedAppLanguage {
        val savedName = LocalStorage.getData(Constants.Preferences.BASE_LANGUAGE)
        return savedName
            ?.let { runCatching { SupportedAppLanguage.valueOf(it) }.getOrNull() }
            ?.takeIf { it.isTranslatable }
            ?: SupportedAppLanguage.ENGLISH
    }

}

private const val UNKNOWN_LANGUAGE_LABEL = "Unknown / choose manually"

