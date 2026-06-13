package com.liiceberg.ui

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.liiceberg.model.PluralResource
import com.liiceberg.model.StringEntity
import com.liiceberg.model.SuggestionType
import com.liiceberg.strings.SearchUtil
import com.liiceberg.strings.detector.Pattern
import com.liiceberg.strings.detector.PatternType
import com.liiceberg.strings.service.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.TitledBorder

class ExistingResourcesAnalysisDialog(
    private val project: Project,
    private val report: ExistingResourcesAnalysisReport,
) : DialogWrapper(project) {

    private val moduleManager = ModuleManager.getInstance(project)
    private val duplicateSelections = mutableMapOf<Int, DuplicateGroupSelection>()
    private val patternSelections = mutableMapOf<String, PatternActionSelection>()
    private val duplicateGroups by lazy { buildDuplicateGroups() }

    init {
        title = "Existing Resources Analysis"
        setOKButtonText("Apply Changes")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8)).apply {
            preferredSize = Dimension(1000, 720)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        panel.add(JLabel(buildSummary()), BorderLayout.NORTH)
        panel.add(createContentPanel(), BorderLayout.CENTER)
        return panel
    }

    private fun createContentPanel(): JComponent {
        val patternFindings = report.findings.filter {
            SuggestionType.TEMPLATE in it.suggestions || SuggestionType.PLURAL in it.suggestions
        }

        val duplicatesPanel = createDuplicatesPanel(duplicateGroups)
        val patternsPanel = createPatternsPanel(patternFindings)

        return JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JBScrollPane(duplicatesPanel),
            JBScrollPane(patternsPanel),
        ).apply {
            resizeWeight = 0.55
            dividerLocation = 390
            border = BorderFactory.createEmptyBorder()
        }
    }

    override fun doOKAction() {
        val duplicateDecisions = buildDuplicateMergeDecisions()
        val patternDecisions = buildPatternTransformDecisions()
        if (duplicateDecisions.isEmpty() && patternDecisions.isEmpty()) {
            Messages.showInfoMessage(project, "No changes selected to apply.", "String-Wielder")
            return
        }
        super.doOKAction()

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, "Applying existing resource changes", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Applying duplicate merges"
                    val duplicateResult = ExistingResourceDuplicateMergeService(project).apply(duplicateDecisions)
                    indicator.text = "Applying template and plural changes"
                    val patternResult = ExistingResourcePatternApplyService(project).apply(patternDecisions)
                    indicator.text = "Existing resource changes completed"

                    SwingUtilities.invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "Updated ${duplicateResult.replacedFiles + patternResult.changedCodeFiles} code files, " +
                                    "changed ${patternResult.updatedResourceEntries} resource entries and " +
                                    "deleted ${duplicateResult.deletedResources} resources." +
                                    skippedMessage(duplicateResult.skippedDecisions),
                            "String-Wielder",
                        )
                    }
                }

                override fun onThrowable(error: Throwable) {
                    Messages.showErrorDialog(
                        project,
                        error.message ?: "Failed to apply existing resource changes.",
                        "String-Wielder",
                    )
                }
            })
    }

    private fun buildSummary(): String {
        return "Analyzed ${report.analyzedCount} resources. " +
                "Found ${report.findings.size} issues " +
                "(duplicates: ${report.duplicateCount}, templates: ${report.templateCount}, plurals: ${report.pluralCount})."
    }

    private fun createDuplicatesPanel(groups: List<DuplicateGroup>): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = TitledBorder("Duplicates (${groups.size} groups)")
        }

        if (groups.isEmpty()) {
            panel.add(emptyLabel("No duplicate groups found."))
            return panel
        }

        groups.forEachIndexed { index, group ->
            panel.add(createDuplicateGroupPanel(index, group))
            panel.add(Box.createVerticalStrut(10))
        }

        return panel
    }

    private fun createDuplicateGroupPanel(index: Int, group: DuplicateGroup): JComponent {
        val selection = duplicateSelections.getOrPut(index) {
            DuplicateGroupSelection(
                mergeResourceIds = mutableSetOf(),
                keepResourceId = group.items.first().id,
            )
        }

        val panel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createTitledBorder("Group ${index + 1}")
            alignmentX = JComponent.LEFT_ALIGNMENT
        }

        val keepGroup = ButtonGroup()
        val constraints = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = JBUI.insets(4, 8)
        }

        group.items.forEachIndexed { row, item ->
            val mergeBox = JCheckBox("Merge").apply {
                isSelected = item.id in selection.mergeResourceIds
                addActionListener {
                    if (isSelected) {
                        selection.mergeResourceIds.add(item.id)
                    } else {
                        selection.mergeResourceIds.remove(item.id)
                    }
                }
            }
            val keepButton = JRadioButton("Keep").apply {
                isSelected = selection.keepResourceId == item.id
                addActionListener {
                    selection.keepResourceId = item.id
                }
            }
            keepGroup.add(keepButton)

            constraints.gridx = 0
            constraints.gridy = row
            constraints.weightx = 0.0
            panel.add(mergeBox, constraints)

            constraints.gridx = 1
            panel.add(keepButton, constraints)

            constraints.gridx = 2
            constraints.weightx = 1.0
            panel.add(createDuplicateItemLabel(item), constraints)
        }

        return panel
    }

    private fun createDuplicateItemLabel(item: DuplicateResourceItem): JComponent {
        val packagePart = item.packageName?.let { " | Package: $it" }.orEmpty()
        return JLabel(
            "<html><b>${escapeHtml(item.key)}</b> (${item.resourceType.name.lowercase()})" +
                    " | Module: ${escapeHtml(item.moduleName)}$packagePart<br>" +
                    escapeHtml(item.value) +
                    "</html>"
        )
    }

    private fun createPatternsPanel(findings: List<ExistingResourceFinding>): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = TitledBorder("Templates and Plurals (${findings.size})")
        }

        if (findings.isEmpty()) {
            panel.add(emptyLabel("No template or plural candidates found."))
            return panel
        }

        findings.forEach { finding ->
            panel.add(createPatternFindingPanel(finding))
            panel.add(Box.createVerticalStrut(10))
        }

        return panel
    }

    private fun createPatternFindingPanel(finding: ExistingResourceFinding): JComponent {
        val selectionKey = finding.selectionKey()
        val statusLabel = JLabel(patternSelections[selectionKey]?.label ?: "No action selected").apply {
            foreground = JBColor.GRAY
        }

        val titleBorder = BorderFactory.createTitledBorder(finding.key)
        val panel = JPanel(BorderLayout(8, 8)).apply {
            border = titleBorder
            alignmentX = JComponent.LEFT_ALIGNMENT
        }

        val info = JLabel(
            "<html>${finding.resourceType.name.lowercase()} | Module: ${escapeHtml(finding.moduleName)}<br>" +
                    escapeHtml(finding.value) +
                    "</html>"
        )

        val buttons = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JButton("Extract as template").apply {
                addActionListener {
                    openTemplateDialog(finding, statusLabel)
                }
            })
            add(Box.createHorizontalStrut(8))
            add(JButton("Extract as plural").apply {
                addActionListener {
                    openPluralDialog(finding, statusLabel, titleBorder, panel)
                }
            })
            add(Box.createHorizontalStrut(12))
            add(statusLabel)
        }

        panel.add(info, BorderLayout.CENTER)
        panel.add(buttons, BorderLayout.SOUTH)
        return panel
    }

    private fun openTemplateDialog(
        finding: ExistingResourceFinding,
        statusLabel: JLabel,
    ) {
        val entity = createEntity(finding) ?: return
        entity.patterns.addAll(findRawNumberTemplatePatterns(finding.value))

        if (entity.patterns.isEmpty()) {
            Messages.showInfoMessage(project, "No raw numeric values found.", "String-Wielder")
            return
        }

        if (TemplateConfirmDialog(project, entity).showAndGet()) {
            val templatePatterns = entity.patterns
                .filter { it.type == PatternType.TEMPLATE }
                .sortedBy { it.range.first }
            patternSelections[finding.selectionKey()] = PatternActionSelection.Template(
                key = finding.key,
                value = buildTemplateResourceValue(entity),
                templateFormats = templatePatterns.map { it.templateFormat },
                arguments = buildTemplateArguments(templatePatterns),
            )
            statusLabel.text = patternSelections.getValue(finding.selectionKey()).label
        }
    }

    private fun openPluralDialog(
        finding: ExistingResourceFinding,
        statusLabel: JLabel,
        titleBorder: TitledBorder,
        panel: JComponent,
    ) {
        val entity = createEntity(finding) ?: return

        if (PluralDialog(project, entity).showAndGet()) {
            val plural = entity.pluralForm ?: return
            patternSelections[finding.selectionKey()] = PatternActionSelection.Plural(
                key = entity.key,
                plural = plural,
            )
            titleBorder.title = entity.key
            statusLabel.text = patternSelections.getValue(finding.selectionKey()).label
            panel.revalidate()
            panel.repaint()
        }
    }

    private fun createEntity(finding: ExistingResourceFinding): StringEntity? {
        val module = moduleManager.findModuleByName(finding.moduleName)
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(finding.filePath)

        if (module == null || virtualFile == null) {
            Messages.showErrorDialog(
                project,
                "Cannot resolve resource file or module for ${finding.key}.",
                "String-Wielder",
            )
            return null
        }

        return StringEntity(
            key = finding.key,
            value = finding.value,
            isSelected = false,
            virtualFile = virtualFile,
            module = module,
            sourceValue = finding.value,
        )
    }

    private fun buildDuplicateGroups(): List<DuplicateGroup> {
        val groups = mutableListOf<MutableList<DuplicateResourceItem>>()

        report.findings
            .filter { SuggestionType.DUPLICATE in it.suggestions }
            .forEach { finding ->
                val items = buildDuplicateItems(finding)
                val identities = items.map { it.id }.toSet()
                val matchingGroups = groups.filter { group ->
                    group.any { item -> item.id in identities }
                }

                if (matchingGroups.isEmpty()) {
                    groups += items.toMutableList()
                    return@forEach
                }

                val merged = (matchingGroups.flatten() + items)
                    .distinctBy { it.id }
                    .toMutableList()
                groups.removeAll(matchingGroups.toSet())
                groups += merged
            }

        return groups
            .mapIndexed { index, items ->
                DuplicateGroup(index, items.sortedWith(compareBy({ it.value }, { it.key })))
            }
            .filter { it.items.size > 1 }
            .distinctBy { group ->
                group.items.map { it.id }.sorted().joinToString("|")
            }
    }

    private fun buildDuplicateItems(finding: ExistingResourceFinding): List<DuplicateResourceItem> {
        val current = DuplicateResourceItem(
            moduleName = finding.moduleName,
            packageName = null,
            key = finding.key,
            value = finding.value,
            resourceType = finding.resourceType,
        )

        val duplicates = finding.duplicates.map { duplicate ->
            DuplicateResourceItem(
                moduleName = duplicate.module.name,
                packageName = duplicate.packageName,
                key = duplicate.key,
                value = duplicate.value,
                resourceType = duplicate.resourceType,
            )
        }

        return (listOf(current) + duplicates).distinctBy { it.id }
    }

    private fun buildDuplicateMergeDecisions(): List<DuplicateMergeDecision> {
        return duplicateGroups.mapIndexedNotNull { index, group ->
            val selection = duplicateSelections[index] ?: return@mapIndexedNotNull null
            val keepResource = group.items.firstOrNull { it.id == selection.keepResourceId }
                ?: return@mapIndexedNotNull null
            val resourcesToRemove = group.items.filter { item ->
                item.id in selection.mergeResourceIds && item.id != keepResource.id
            }

            if (resourcesToRemove.isEmpty()) {
                return@mapIndexedNotNull null
            }

            DuplicateMergeDecision(
                keepResource = keepResource.toResourceRef(),
                resourcesToRemove = resourcesToRemove.map { it.toResourceRef() },
            )
        }
    }

    private fun buildPatternTransformDecisions(): List<ExistingResourcePatternDecision> {
        return report.findings.mapNotNull { finding ->
            val selection = patternSelections[finding.selectionKey()] ?: return@mapNotNull null
            val action = when (selection) {
                is PatternActionSelection.Template -> ExistingResourcePatternAction.Template(
                    value = selection.value,
                    templateFormats = selection.templateFormats,
                    arguments = selection.arguments,
                )

                is PatternActionSelection.Plural -> ExistingResourcePatternAction.Plural(
                    plural = selection.plural,
                )
            }

            ExistingResourcePatternDecision(
                moduleName = finding.moduleName,
                filePath = finding.filePath,
                sourceKey = finding.key,
                targetKey = selection.key,
                action = action,
            )
        }
    }

    private fun DuplicateResourceItem.toResourceRef(): DuplicateResourceRef {
        return DuplicateResourceRef(
            moduleName = moduleName,
            key = key,
            resourceType = resourceType,
        )
    }

    private fun findRawNumberTemplatePatterns(text: String): List<Pattern> {
        val placeholderRanges = FORMAT_PLACEHOLDER_REGEX.findAll(text).map { it.range }.toList()
        return RAW_NUMBER_REGEX.findAll(text)
            .filterNot { match ->
                placeholderRanges.any { range ->
                    match.range.first >= range.first && match.range.last <= range.last
                }
            }
            .map { match ->
                Pattern(
                    type = PatternType.TEMPLATE,
                    value = match.value,
                    range = match.range,
                    templateFormat = "%d",
                )
            }
            .toList()
    }

    private fun buildTemplateResourceValue(entity: StringEntity): String {
        var result = entity.sourceValue
        entity.patterns
            .filter { it.type == PatternType.TEMPLATE }
            .sortedByDescending { it.range.first }
            .forEach { pattern ->
                result = result.replaceRange(
                    pattern.range,
                    pattern.templateFormat ?: pattern.value,
                )
            }
        return result
    }

    private fun buildTemplateArguments(patterns: List<Pattern>): List<String> {
        return patterns.mapNotNull { pattern ->
            when (pattern.templateFormat) {
                "%s" -> "\"${escapeKotlinString(pattern.value)}\""
                "%d" -> pattern.value
                "%f" -> "${pattern.value}.0"
                else -> null
            }
        }
    }

    private fun escapeKotlinString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    private fun emptyLabel(text: String): JComponent {
        return JLabel(text, SwingConstants.LEFT).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            foreground = JBColor.GRAY
        }
    }

    private fun ExistingResourceFinding.selectionKey(): String {
        return listOf(moduleName, filePath, key, resourceType.name, value).joinToString("|")
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun skippedMessage(skippedDecisions: Int): String {
        return if (skippedDecisions == 0) {
            ""
        } else {
            " Skipped $skippedDecisions incompatible merge selections."
        }
    }

    private data class DuplicateGroup(
        val index: Int,
        val items: List<DuplicateResourceItem>,
    )

    private data class DuplicateResourceItem(
        val moduleName: String,
        val packageName: String?,
        val key: String,
        val value: String,
        val resourceType: SearchUtil.ResourceType,
    ) {
        val id: String = listOf(moduleName, key, resourceType.name).joinToString("|")
    }

    private data class DuplicateGroupSelection(
        val mergeResourceIds: MutableSet<String>,
        var keepResourceId: String,
    )

    private sealed interface PatternActionSelection {
        val key: String
        val label: String

        data class Template(
            override val key: String,
            val value: String,
            val templateFormats: List<String?>,
            val arguments: List<String>,
        ) : PatternActionSelection {
            override val label: String = "Template selected: $key = \"$value\""
        }

        data class Plural(
            override val key: String,
            val plural: PluralResource,
        ) : PatternActionSelection {
            override val label: String = "Plural selected: $key"
        }
    }

    private companion object {
        private val RAW_NUMBER_REGEX = Regex("""\b\d+\b""")
        private val FORMAT_PLACEHOLDER_REGEX = Regex("""%([0-9]\$)?[sdf]""")
    }
}
