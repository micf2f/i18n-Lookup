package com.github.micf2f.i18nlookup.hover

import com.github.micf2f.i18nlookup.core.I18nKeyMatcher
import com.github.micf2f.i18nlookup.core.TranslationService
import com.github.micf2f.i18nlookup.settings.I18nSettings
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor

class I18nExternalAnnotator : ExternalAnnotator<I18nExternalAnnotator.CollectedInfo, List<I18nExternalAnnotator.Result>>() {

    data class CollectedInfo(val project: Project, val text: String)

    data class Result(val start: Int, val end: Int)

    override fun collectInformation(file: PsiFile): CollectedInfo? {
        val project = file.project
        if (I18nSettings.getInstance(project).state.translationFilePath.isBlank()) return null
        return CollectedInfo(project, file.text)
    }

    override fun doAnnotate(collectedInfo: CollectedInfo): List<Result> {
        val project = collectedInfo.project
        val settings = I18nSettings.getInstance(project)
        val matcher = I18nKeyMatcher(settings.enabledFunctionNames(), settings.customRegexes())
        if (matcher.isEmpty) return emptyList()

        val translations = TranslationService.getInstance(project)
        val results = ArrayList<Result>()
        for (match in matcher.findMatches(collectedInfo.text)) {
            if (translations.lookupAll(match.key).none { it.value != null }) continue
            results.add(Result(match.start, match.end))
        }
        return results
    }

    override fun apply(file: PsiFile, annotationResult: List<Result>, holder: AnnotationHolder) {
        for (result in annotationResult) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(result.start, result.end))
                .enforcedTextAttributes(KEY_ATTRIBUTES)
                .create()
        }
    }

    companion object {
        private val KEY_ATTRIBUTES = TextAttributes().apply {
            effectType = EffectType.BOLD_DOTTED_LINE
            effectColor = JBColor.GRAY
        }
    }
}
