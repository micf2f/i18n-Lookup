package com.github.micf2f.i18nlookup.hover

import com.github.micf2f.i18nlookup.core.I18nKeyMatcher
import com.github.micf2f.i18nlookup.core.TranslationService
import com.github.micf2f.i18nlookup.settings.I18nSettings
import com.intellij.json.highlighting.JsonSyntaxHighlighterFactory
import com.intellij.model.Pointer
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color

class I18nDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val project = file.project
        val settings = I18nSettings.getInstance(project)
        val service = TranslationService.getInstance(project)
        val effectiveMode = if (service.isMultiSource()) I18nSettings.DisplayMode.TOOLTIP else settings.state.displayMode
        if (effectiveMode != I18nSettings.DisplayMode.TOOLTIP) return emptyList()
        if (settings.state.translationFilePath.isBlank() &&
            settings.state.additionalTranslationFilePath.isBlank()
        ) {
            return emptyList()
        }

        val matcher = I18nKeyMatcher(settings.enabledFunctionNames(), settings.customRegexes())
        if (matcher.isEmpty) return emptyList()

        val match = matcher.findMatches(file.text).firstOrNull { offset >= it.start && offset <= it.end }
            ?: return emptyList()

        val results = service.lookupAll(match.key)
        return listOf(I18nDocumentationTarget(project, match.key, results))
    }
}

class I18nDocumentationTarget(
    private val project: Project,
    private val key: String,
    private val results: List<TranslationService.LangResult>,
) : DocumentationTarget {

    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(results.firstOrNull { it.value != null }?.value ?: "No translation for $key")
            .presentation()

    fun navigatableFor(index: Int): Navigatable? {
        val result = results.getOrNull(index) ?: return null
        val offset = if (result.value != null) result.offset else 0
        return OpenFileDescriptor(project, result.file, offset)
    }

    private val duplicatedNames: Set<String> by lazy {
        results.groupingBy { it.file.name }.eachCount().filterValues { it > 1 }.keys
    }

    private fun displayName(result: TranslationService.LangResult): String {
        val name = result.file.name
        return if (name in duplicatedNames) "${result.file.parent?.name.orEmpty()}/$name" else name
    }

    override fun computeDocumentation(): DocumentationResult {
        val html = if (results.any { it.sourceLabel != null }) {
            multiSourceDisplayHtml()
        } else {
            when {
                results.size == 1 && results[0].value == null -> singleFileNotFoundHtml(IndexedResult(0, results[0]), null)
                results.none { it.value != null } -> multiFileNotFoundHtml()
                results.size == 1 -> singleFileHtml(IndexedResult(0, results[0]), null)
                else -> rowsHtml()
            }
        }
        return DocumentationResult.documentation(html)
    }

    private data class IndexedResult(val index: Int, val result: TranslationService.LangResult)

    private fun multiSourceDisplayHtml(): String {
        val indexed = results.mapIndexed { i, r -> IndexedResult(i, r) }
        val sourcesWithValue = indexed
            .filter { it.result.value != null }
            .mapNotNull { it.result.sourceLabel }
            .toSet()
        return when (sourcesWithValue.size) {
            0 -> multiFileNotFoundHtml()
            1 -> {
                val label = sourcesWithValue.first()
                singleSourceHtml(indexed.filter { it.result.sourceLabel == label })
            }
            else -> multiSourceHtml()
        }
    }

    private fun singleSourceHtml(entries: List<IndexedResult>): String {
        val sourceLabel = entries.firstOrNull()?.result?.sourceLabel
        if (entries.size == 1) {
            val entry = entries[0]
            return if (entry.result.value != null) singleFileHtml(entry, sourceLabel) else singleFileNotFoundHtml(entry, sourceLabel)
        }
        val keyColor = ColorUtil.toHex(jsonKeyColor())
        val safeKey = StringUtil.escapeXmlEntities(key)
        return buildString {
            append("<div style='padding:3px 2px;'>")
            append("<code style='color:#").append(keyColor).append(";'>").append(safeKey).append("</code>")
            append("<hr/>")
            append("<table style='border-collapse:collapse;'>")
            entries.forEachIndexed { rowIndex, entry ->
                if (rowIndex > 0) {
                    append("<tr><td colspan='3' style='padding:0;'><hr/></td></tr>")
                }
                appendLangRow(this, entry)
            }
            append("</table>")
            if (sourceLabel != null) {
                append("<hr/>")
                appendSourceLabelLine(this, sourceLabel)
            }
            append("</div>")
        }
    }

    private fun multiSourceHtml(): String {
        val groups = LinkedHashMap<String, MutableList<IndexedResult>>()
        results.forEachIndexed { index, result ->
            groups.getOrPut(result.sourceLabel.orEmpty()) { mutableListOf() }.add(IndexedResult(index, result))
        }
        if (groups.values.none { list -> list.any { it.result.value != null } }) {
            return multiFileNotFoundHtml()
        }

        val keyColor = ColorUtil.toHex(jsonKeyColor())
        val safeKey = StringUtil.escapeXmlEntities(key)
        return buildString {
            append("<div style='padding:3px 2px;'>")
            append("<code style='color:#").append(keyColor).append(";'>").append(safeKey).append("</code>")
            append("<hr/>")
            groups.entries.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) append("<hr/>")
                val list = group.value
                if (list.size == 1) {
                    val entry = list[0]
                    append("<div style='padding:6px 0 4px 0;'>")
                    if (entry.result.value != null) {
                        append(StringUtil.escapeXmlEntities(entry.result.value))
                    } else {
                        append(notFoundHtml())
                    }
                    append("</div>")
                    appendFileLine(this, entry.index, entry.result.file.name, entry.result.sourceLabel)
                } else {
                    append("<table style='border-collapse:collapse;'>")
                    list.forEachIndexed { rowIndex, entry ->
                        if (rowIndex > 0) {
                            append("<tr><td colspan='3' style='padding:0;'><hr/></td></tr>")
                        }
                        appendLangRow(this, entry)
                    }
                    append("</table>")
                    append("<hr/>")
                    appendSourceLabelLine(this, group.key)
                }
            }
            append("</div>")
        }
    }

    private fun appendSourceLabelLine(sb: StringBuilder, label: String) {
        sb.append("<div style='padding:5px 0 2px 0;'>")
        sb.append("<table style='border-collapse:collapse;'><tr>")
        sb.append("<td valign='middle' style='padding:0 5px 0 0;'><icon src='AllIcons.Nodes.Folder'/></td>")
        sb.append("<td valign='middle' style='color:#").append(grayHex()).append(";'>")
        sb.append(StringUtil.escapeXmlEntities(label))
        sb.append("</td></tr></table>")
        sb.append("</div>")
    }

    private fun appendLangRow(sb: StringBuilder, entry: IndexedResult) {
        val safeName = StringUtil.escapeXmlEntities(entry.result.file.name)
        val nav = "$NAV_PREFIX${entry.index}"
        sb.append("<tr>")
        sb.append("<td valign='middle' style='padding:3px 4px 3px 0;'>")
        sb.append("<a href='").append(nav).append("'><icon src='AllIcons.FileTypes.Json'/></a>")
        sb.append("</td>")
        sb.append("<td nowrap valign='middle' width='110' style='padding:3px 16px 3px 0;'>")
        sb.append("<a href='").append(nav).append("'>").append(safeName.replace(".", "⁠.⁠")).append("</a>")
        sb.append("</td>")
        sb.append("<td valign='middle' style='padding:3px 0;'>")
        if (entry.result.value != null) {
            sb.append(StringUtil.escapeXmlEntities(entry.result.value))
        } else {
            sb.append(notFoundHtml())
        }
        sb.append("</td>")
        sb.append("</tr>")
    }

    private fun appendFileLine(sb: StringBuilder, index: Int, fileName: String, sourceLabel: String?) {
        val nav = "$NAV_PREFIX$index"
        sb.append("<table style='border-collapse:collapse;'><tr>")
        sb.append("<td valign='middle' style='padding:0 5px 0 0;'>")
        sb.append("<a href='").append(nav).append("'><icon src='AllIcons.FileTypes.Json'/></a>")
        sb.append("</td>")
        sb.append("<td valign='middle'>")
        sb.append("<a href='").append(nav).append("'>").append(StringUtil.escapeXmlEntities(fileName)).append("</a>")
        sb.append("</td>")
        if (sourceLabel != null) {
            sb.append("<td valign='middle' style='padding:0 5px 0 18px;'><icon src='AllIcons.Nodes.Folder'/></td>")
            sb.append("<td valign='middle' style='color:#").append(grayHex()).append(";'>")
            sb.append(StringUtil.escapeXmlEntities(sourceLabel)).append("</td>")
        }
        sb.append("</tr></table>")
    }

    private fun notFoundHtml(): String =
        "<table style='border-collapse:collapse;'><tr>" +
            "<td valign='middle' style='padding:0 5px 0 0;'><icon src='AllIcons.General.Warning'/></td>" +
            "<td valign='middle'>No translation found</td>" +
            "</tr></table>"

    private fun grayHex(): String = ColorUtil.toHex(UIUtil.getContextHelpForeground())

    private fun multiFileNotFoundHtml(): String {
        val yellowColor = ColorUtil.toHex(WARNING_KEY_COLOR)
        val safeKey = StringUtil.escapeXmlEntities(key)
        return buildString {
            append("<div style='padding:3px 2px;'>")
            append("<code style='color:#").append(yellowColor).append(";'>")
            append(safeKey)
            append("</code>")
            append("<hr/>")
            append("<div style='padding:2px 0;'>")
            append(notFoundHtml())
            append("</div>")
            append("</div>")
        }
    }

    private fun singleFileHtml(entry: IndexedResult, sourceLabel: String?): String {
        val result = entry.result
        val keyColor = ColorUtil.toHex(jsonKeyColor())
        val safeKey = StringUtil.escapeXmlEntities(key)
        val safeValue = StringUtil.escapeXmlEntities(result.value ?: "")
        return buildString {
            append("<div style='padding:3px 2px;'>")
            append("<code style='color:#").append(keyColor).append(";'>")
            append(safeKey)
            append("</code>")
            append("<div style='padding:6px 0 8px 0;'>")
            append(safeValue)
            append("</div>")
            append("<hr/>")
            appendFileLine(this, entry.index, result.file.name, sourceLabel)
            append("</div>")
        }
    }

    private fun singleFileNotFoundHtml(entry: IndexedResult, sourceLabel: String?): String {
        val result = entry.result
        val yellowColor = ColorUtil.toHex(WARNING_KEY_COLOR)
        val safeKey = StringUtil.escapeXmlEntities(key)
        return buildString {
            append("<div style='padding:3px 2px;'>")
            append("<code style='color:#").append(yellowColor).append(";'>")
            append(safeKey)
            append("</code>")
            append("<div style='padding:6px 0 8px 0;'>")
            append(notFoundHtml())
            append("</div>")
            append("<hr/>")
            appendFileLine(this, entry.index, result.file.name, sourceLabel)
            append("</div>")
        }
    }

    private fun rowsHtml(): String {
        val keyColor = ColorUtil.toHex(jsonKeyColor())
        val safeKey = StringUtil.escapeXmlEntities(key)
        return buildString {
            append("<div style='padding:3px 2px;'>")
            append("<code style='color:#").append(keyColor).append(";'>")
            append(safeKey)
            append("</code>")
            append("<hr/>")
            append("<table style='border-collapse:collapse;'>")
            results.forEachIndexed { index, result ->
                val safeName = StringUtil.escapeXmlEntities(displayName(result))
                val nav = "$NAV_PREFIX$index"
                if (index > 0) {
                    append("<tr><td colspan='3' style='padding:0;'><hr/></td></tr>")
                }
                append("<tr>")
                append("<td valign='middle' style='padding:3px 4px 3px 0;'>")
                append("<a href='").append(nav).append("'><icon src='AllIcons.FileTypes.Json'/></a>")
                append("</td>")
                append("<td nowrap valign='middle' width='110' style='padding:3px 16px 3px 0;'>")
                append("<a href='").append(nav).append("'>").append(safeName.replace(".", "⁠.⁠")).append("</a>")
                append("</td>")
                append("<td valign='middle' style='padding:3px 0;'>")
                if (result.value != null) {
                    append(StringUtil.escapeXmlEntities(result.value))
                } else {
                    append(notFoundHtml())
                }
                append("</td>")
                append("</tr>")
            }
            append("</table>")
            append("</div>")
        }
    }

    private fun jsonKeyColor(): Color {
        val scheme = EditorColorsManager.getInstance().globalScheme
        return scheme.getAttributes(JsonSyntaxHighlighterFactory.JSON_PROPERTY_KEY)?.foregroundColor
            ?: scheme.defaultForeground
    }

    companion object {
        const val NAV_PREFIX = "i18n-lookup:goto:"
        private val WARNING_KEY_COLOR = JBColor(Color(0x9F6500), Color(0xF0A732))
    }
}
