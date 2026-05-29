package com.liiceberg.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.liiceberg.strings.analysis.ExistingResourceFinding
import com.liiceberg.strings.analysis.ExistingResourcesAnalysisReport
import com.liiceberg.ui.entity.SuggestionType
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ExistingResourcesAnalysisDialog(
    private val project: Project,
    private val report: ExistingResourcesAnalysisReport,
) : DialogWrapper(project) {

    init {
        title = "Existing Resources Analysis"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8)).apply {
            preferredSize = Dimension(900, 620)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        val summaryLabel = JLabel(buildSummary())
        val textArea = JBTextArea(buildDetailsText()).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            caretPosition = 0
        }

        panel.add(summaryLabel, BorderLayout.NORTH)
        panel.add(JBScrollPane(textArea), BorderLayout.CENTER)
        return panel
    }

    private fun buildSummary(): String {
        return "Analyzed ${report.analyzedCount} resources. " +
            "Found ${report.findings.size} issues " +
            "(duplicates: ${report.duplicateCount}, templates: ${report.templateCount}, plurals: ${report.pluralCount})."
    }

    private fun buildDetailsText(): String {
        if (report.findings.isEmpty()) {
            return "No issues found."
        }

        val duplicateFindings = report.findings.filter { SuggestionType.DUPLICATE in it.suggestions }
        val patternFindings = report.findings.filter {
            SuggestionType.TEMPLATE in it.suggestions || SuggestionType.PLURAL in it.suggestions
        }

        val sections = mutableListOf<String>()
        sections += buildSection(
            title = "Duplicates (${duplicateFindings.size})",
            findings = duplicateFindings,
            includeDuplicateTargets = true,
        )
        sections += buildSection(
            title = "Templates and Plurals (${patternFindings.size})",
            findings = patternFindings,
            includeDuplicateTargets = false,
        )

        return sections.joinToString(separator = "\n\n")
    }

    private fun buildSection(
        title: String,
        findings: List<ExistingResourceFinding>,
        includeDuplicateTargets: Boolean,
    ): String {
        if (findings.isEmpty()) {
            return "$title\nNo issues found."
        }

        val details = findings.joinToString(separator = "\n\n") { finding ->
            renderFinding(
                finding = finding,
                includeDuplicateTargets = includeDuplicateTargets,
            )
        }

        return "$title\n\n$details"
    }

    private fun renderFinding(
        finding: ExistingResourceFinding,
        includeDuplicateTargets: Boolean,
    ): String {
        val suggestions = finding.suggestions
            .sortedBy { it.name }
            .joinToString { suggestionLabel(it) }
        val base = buildString {
            append("Module: ").append(finding.moduleName).append('\n')
            append("File: ").append(finding.filePath).append('\n')
            append("Key: ").append(finding.key).append('\n')
            append("Type: ").append(finding.resourceType.name.lowercase()).append('\n')
            append("Value: ").append(finding.value).append('\n')
            append("Suggestions: ").append(suggestions)
        }

        if (!includeDuplicateTargets || finding.duplicates.isEmpty()) {
            return base
        }

        val duplicates = finding.duplicates.joinToString(separator = "; ") { duplicate ->
            "${duplicate.module.name}:${duplicate.key} (${duplicate.resourceType.name.lowercase()})"
        }
        return "$base\nPossible duplicates: $duplicates"
    }

    private fun suggestionLabel(type: SuggestionType): String {
        return when (type) {
            SuggestionType.DUPLICATE -> "duplicate"
            SuggestionType.TEMPLATE -> "template"
            SuggestionType.PLURAL -> "plural"
        }
    }
}
