package com.liiceberg.ui

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
import com.liiceberg.strings.SearchUtil
import com.liiceberg.strings.StringResourceReplacer
import com.liiceberg.strings.translator.SupportedAppLanguage
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.ui.entity.SuggestionType
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
    private val entries: List<HardcodedStringEntity>,
    private val isDeepAnalyze: Boolean = true,
    private val searchUtil: SearchUtil = SearchUtil(project),
) : DialogWrapper(project) {

    private val duplicateRefreshRequests = mutableMapOf<Int, Int>()
    private val tableModel = FoundStringTableModel(entries, ::onLanguageChanged)
    private val stringsTable = JBTable(tableModel)
    private val baseLanguageComboBox = ComboBox(
        SupportedAppLanguage.baseLanguageValues.toTypedArray()
    ).apply {
        selectedItem = getSavedBaseLanguage()
        preferredSize = Dimension(220, preferredSize.height)
        maximumSize = Dimension(220, preferredSize.height)
    }
    private val errorLabel = JLabel("").apply {
        foreground = JBColor.RED
    }

    init {
        title = Constants.Titles.HARDCODED_STRINGS_FOUND_TABLE
        init()
    }

    override fun dispose() {
        duplicateRefreshRequests.clear()
        super.dispose()
    }

    override fun doOKAction() {
        if (!validateEntries() ) {
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
        panel.minimumSize = Dimension(800, 600)

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
                        arrayOf<SupportedAppLanguage?>(
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

            stringsTable.columnModel.getColumn(4).cellRenderer = SuggestionCellRenderer()

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
                    return if (col == 0 && !value.matches(Constants.RegexTemplates.KEY_REGEX)) {
                        errorLabel.text = Constants.Labels.INVALID_KEY_FILED
                        false
                    } else {
                        errorLabel.text = ""
                        super.stopCellEditing()
                    }
                }

            })

            if (entries.any { entry -> entry.isSelected && !entry.key.matches(Constants.RegexTemplates.KEY_REGEX) }) {
                errorLabel.text = Constants.Labels.INVALID_KEYS_FOUND
            }

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
                PluralDialog(project, entity).show()
                entity.suggestions.remove(SuggestionType.PLURAL)
            }

            SuggestionType.DUPLICATE in entity.suggestions -> {
                DuplicateDialog(project, entity).show()
                entity.suggestions.remove(SuggestionType.DUPLICATE)
            }

            SuggestionType.TEMPLATE in entity.suggestions -> {
                TemplateConfirmDialog(project, entity).show()
                entity.suggestions.remove(SuggestionType.TEMPLATE)
            }
        }

        tableModel.fireTableRowsUpdated(row, row)
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
        entity: HardcodedStringEntity,
        sourceLanguage: SupportedAppLanguage?,
    ): List<SearchUtil.SearchResult> {

        return if (isDeepAnalyze) {
            searchUtil.deepSearch(
                module = entity.module,
                string = entity.value,
                sourceLanguage = sourceLanguage,
            )
        } else {
            searchUtil.search(
                module = entity.module,
                string = entity.value,
                sourceLanguage = sourceLanguage,
            )?.let(::listOf).orEmpty()
        }
    }

    private fun applyDuplicateState(
        entity: HardcodedStringEntity,
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
            entry.isSelected && !entry.key.matches(Constants.RegexTemplates.KEY_REGEX)
        }
        errorLabel.text = if (hasInvalidSelectedEntries) {
            Constants.Labels.INVALID_KEYS_FOUND
        } else {
            ""
        }
        return !hasInvalidSelectedEntries
    }

    private companion object {
        const val UNKNOWN_LANGUAGE_LABEL = "Unknown / choose manually"
    }

    private fun getSavedBaseLanguage(): SupportedAppLanguage {
        val savedName = LocalStorage.getData(Constants.Preferences.BASE_LANGUAGE)
        return savedName
            ?.let { runCatching { SupportedAppLanguage.valueOf(it) }.getOrNull() }
            ?.takeIf { it.isTranslatable }
            ?: SupportedAppLanguage.ENGLISH
    }

}
