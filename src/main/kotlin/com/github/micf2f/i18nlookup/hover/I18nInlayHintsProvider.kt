package com.github.micf2f.i18nlookup.hover

import com.github.micf2f.i18nlookup.core.I18nKeyMatcher
import com.github.micf2f.i18nlookup.core.TranslationService
import com.github.micf2f.i18nlookup.settings.I18nSettings
import com.intellij.codeInsight.hints.declarative.HintFontSize
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.HintMarginPadding
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.OwnBypassCollector
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class I18nInlayHintsProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        val settings = I18nSettings.getInstance(file.project)
        if (settings.state.displayMode != I18nSettings.DisplayMode.INLINE) return null
        if (settings.state.translationFilePath.isBlank()) return null
        return Collector(file.project)
    }

    private class Collector(private val project: Project) : OwnBypassCollector {

        private val hintFormat = HintFormat.default
            .withFontSize(HintFontSize.ABitSmallerThanInEditor)
            .withHorizontalMargin(HintMarginPadding.MarginAndSmallerPadding)

        override fun collectHintsForFile(file: PsiFile, sink: InlayTreeSink) {
            val settings = I18nSettings.getInstance(project)
            val matcher = I18nKeyMatcher(settings.enabledFunctionNames(), settings.customRegexes())
            if (matcher.isEmpty) return
            val service = TranslationService.getInstance(project)
            val textLength = file.textLength

            for (match in matcher.findMatches(file.text)) {
                val results = service.lookupAll(match.key)
                val offset = (match.end + 1).coerceIn(0, textLength)
                val singleFile = results.size == 1

                if (results.none { it.value != null }) {
                    sink.addPresentation(
                        InlineInlayPosition(offset, true, 0),
                        emptyList(),
                        null,
                        hintFormat,
                    ) {
                        text("no translation")
                    }
                    continue
                }

                results.forEachIndexed { order, result ->
                    val lang = result.file.nameWithoutExtension
                    val value = result.value
                    if (value != null) {
                        val payload = StringInlayActionPayload("${result.file.path}|${result.offset}")
                        val action = InlayActionData(payload, I18nInlayActionHandler.HANDLER_ID)
                        val label = if (singleFile) shorten(value) else "$lang: ${shorten(value)}"
                        sink.addPresentation(
                            InlineInlayPosition(offset, true, -order),
                            emptyList(),
                            result.file.name,
                            hintFormat,
                        ) {
                            text(label, action)
                        }
                    } else {
                        val payload = StringInlayActionPayload("${result.file.path}|0")
                        val action = InlayActionData(payload, I18nInlayActionHandler.HANDLER_ID)
                        sink.addPresentation(
                            InlineInlayPosition(offset, true, -order),
                            emptyList(),
                            result.file.name,
                            hintFormat,
                        ) {
                            text("$lang: no translation", action)
                        }
                    }
                }
            }
        }

        private fun shorten(value: String): String {
            val singleLine = value.replace('\n', ' ').replace('\r', ' ')
            return if (singleLine.length > MAX_LEN) singleLine.take(MAX_LEN - 1) + "…" else singleLine
        }
    }

    companion object {
        private const val MAX_LEN = 50
    }
}
