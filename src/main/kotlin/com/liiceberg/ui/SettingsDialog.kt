package com.liiceberg.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.liiceberg.utils.Constants
import com.liiceberg.utils.LocalStorage
import com.liiceberg.utils.ApplicationUtil
import com.liiceberg.utils.files.FileUtil.getAllLayoutXMLFiles
import com.liiceberg.utils.files.FileUtil.getAllSrcJavaFiles
import com.liiceberg.utils.files.FileUtil.getAllSrcKotlinFiles
import com.liiceberg.utils.toRegexOrNull
import java.awt.Dimension
import javax.swing.*

class SettingsDialog(private val project: Project) : DialogWrapper(project) {


    private val includeRegexLabel = JLabel(Constants.Labels.INCLUDE_REGEX_LABEL)
    private val excludeRegexLabel = JLabel(Constants.Labels.EXCLUDE_REGEX_LABEL)
    private val prefixLabel = JLabel(Constants.Labels.PREFIX_LABEL)
    private val includeRegexField = JTextField()
    private val excludeRegexField = JTextField()
    private val prefixField = JTextField()
    private val infoLabel = JLabel("").apply {
        foreground = JBColor.RED
    }

    init {
        title = Constants.Titles.SETTINGS_TABLE
        init()
        loadStoredValues()
    }


    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        panel.add(includeRegexLabel)
        panel.add(includeRegexField)
        panel.add(Box.createVerticalStrut(8))

        panel.add(excludeRegexLabel)
        panel.add(excludeRegexField)
        panel.add(Box.createVerticalStrut(8))

        panel.add(prefixLabel)
        panel.add(prefixField)
        panel.add(Box.createVerticalStrut(8))

        includeRegexField.preferredSize = Dimension(300, 30)
        excludeRegexField.preferredSize = Dimension(300, 30)
        prefixField.preferredSize = Dimension(300, 30)

        panel.add(Box.createVerticalStrut(8))
        panel.add(infoLabel)

        return panel
    }

    private fun loadStoredValues() {
        includeRegexField.text =
            LocalStorage.getData(Constants.Preferences.INCLUDE_REGEX)
                ?: Constants.RegexTemplates.DEFAULT_INCLUDE_REGEX
        excludeRegexField.text = LocalStorage.getData(Constants.Preferences.EXCLUDE_REGEX)
        prefixField.text = LocalStorage.getData(Constants.Preferences.PREFIX)
    }

    override fun doOKAction() {
        val includeRegexUserInput = includeRegexField.text.trim()
        val includeRegex = "$includeRegexUserInput${Constants.RegexTemplates.INCLUDE_REGEX_END}"
        val excludeRegex = excludeRegexField.text.trim()
        val prefix = prefixField.text.trim()

        if (includeRegex.toRegexOrNull() == null) {
            infoLabel.text = Constants.Labels.INVALID_INCLUDE_REGEX_FILED
            return
        } else if (excludeRegex.toRegexOrNull() == null) {
            infoLabel.text = Constants.Labels.INVALID_EXCLUDE_REGEX_FILED
            return
        } else if (Constants.RegexTemplates.PREFIX_REGEX.matches(prefix).not()) {
            infoLabel.text = Constants.Labels.INVALID_PREFIX_FIELD
            return
        } else {
            infoLabel.text = ""
        }

        infoLabel.apply {
            text = Constants.Labels.LOADING_STRINGS
            foreground = JBColor.GREEN
        }

        LocalStorage.setData(Constants.Preferences.PREFIX, prefix)
        LocalStorage.setData(Constants.Preferences.INCLUDE_REGEX, includeRegexUserInput)
        LocalStorage.setData(Constants.Preferences.EXCLUDE_REGEX, excludeRegex)
        LocalStorage.setData(Constants.Preferences.IMPORT_PACKAGE, ApplicationUtil.findApplicationId(project) ?: "")


        val allFiles = mutableListOf<VirtualFile>()
        allFiles.addAll(getAllSrcJavaFiles(project) ?: emptyList())
        allFiles.addAll(getAllSrcKotlinFiles(project) ?: emptyList())

        val filteredFiles = allFiles.filter { file ->
            val name = file.name
            val includeMatch = includeRegex.toRegexOrNull()?.matches(name) ?: true
            val excludeMatch = excludeRegex.toRegexOrNull()?.matches(name) ?: false
            includeMatch && !excludeMatch
        }

        FoundStringDialog(project, filteredFiles + getAllLayoutXMLFiles(project)).show()
        super.doOKAction()
    }

}
