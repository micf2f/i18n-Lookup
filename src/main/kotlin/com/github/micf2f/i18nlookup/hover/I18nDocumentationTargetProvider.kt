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
import java.awt.Color

class I18nDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val project = file.project
        val settings = I18nSettings.getInstance(project)
        if (settings.state.displayMode != I18nSettings.DisplayMode.TOOLTIP) return emptyList()
        if (settings.state.translationFilePath.isBlank()) return emptyList()

        val matcher = I18nKeyMatcher(settings.enabledFunctionNames(), settings.customRegexes())
        if (matcher.isEmpty) return emptyList()

        val match = matcher.findMatches(file.text).firstOrNull { offset >= it.start && offset <= it.end }
            ?: return emptyList()

        val results = TranslationService.getInstance(project).lookupAll(match.key)
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

    override fun computeDocumentation(): DocumentationResult {
        val html = when {
            results.size == 1 && results[0].value == null -> singleFileNotFoundHtml(results[0])
            results.none { it.value != null } -> multiFileNotFoundHtml()
            results.size == 1 -> singleFileHtml(results[0])
            else -> rowsHtml()
        }
        return DocumentationResult.documentation(html)
    }

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
            append("<icon src='AllIcons.General.Warning'/>&nbsp;")
            append("No translation found")
            append("</div>")
            append("</div>")
        }
    }

    private fun singleFileHtml(result: TranslationService.LangResult): String {
        val keyColor = ColorUtil.toHex(jsonKeyColor())
        val safeKey = StringUtil.escapeXmlEntities(key)
        val safeName = StringUtil.escapeXmlEntities(result.file.name)
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
            append("<icon src='AllIcons.FileTypes.Json'/>&nbsp;")
            append("<a href='").append(NAV_PREFIX).append("0'>")
            append(safeName)
            append("</a>")
            append("</div>")
        }
    }

    private fun singleFileNotFoundHtml(result: TranslationService.LangResult): String {
        val yellowColor = ColorUtil.toHex(WARNING_KEY_COLOR)
        val safeKey = StringUtil.escapeXmlEntities(key)
        val safeName = StringUtil.escapeXmlEntities(result.file.name)
        return buildString {
            append("<div style='padding:3px 2px;'>")
            append("<code style='color:#").append(yellowColor).append(";'>")
            append(safeKey)
            append("</code>")
            append("<div style='padding:6px 0 8px 0;'>")
            append("<icon src='AllIcons.General.Warning'/>&nbsp;")
            append("No translation found")
            append("</div>")
            append("<hr/>")
            append("<icon src='AllIcons.FileTypes.Json'/>&nbsp;")
            append("<a href='").append(NAV_PREFIX).append("0'>")
            append(safeName)
            append("</a>")
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
                val safeName = StringUtil.escapeXmlEntities(result.file.name)
                if (index > 0) {
                    append("<tr><td colspan='2' style='padding:0;'><hr/></td></tr>")
                }
                append("<tr>")
                append("<td nowrap valign='top' width='120' style='padding:3px 16px 3px 0;'>")
                append("<a href='").append(NAV_PREFIX).append(index).append("'>")
                append("<icon src='AllIcons.FileTypes.Json'/>&nbsp;")
                append(safeName.replace(".", "⁠.⁠"))
                append("</a>")
                append("</td>")
                append("<td valign='top' style='padding:3px 0;'>")
                if (result.value != null) {
                    append(StringUtil.escapeXmlEntities(result.value))
                } else {
                    append("<icon src='AllIcons.General.Warning'/>&nbsp;")
                    append("No translation found")
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
