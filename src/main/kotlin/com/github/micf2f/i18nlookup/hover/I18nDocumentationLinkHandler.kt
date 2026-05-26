package com.github.micf2f.i18nlookup.hover

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorMouseHoverPopupManager
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import javax.swing.Timer

class I18nDocumentationLinkHandler : DocumentationLinkHandler {

    override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
        if (target !is I18nDocumentationTarget) return null
        if (!url.startsWith(I18nDocumentationTarget.NAV_PREFIX)) return null

        val index = url.removePrefix(I18nDocumentationTarget.NAV_PREFIX).toIntOrNull() ?: return null
        val navigatable = target.navigatableFor(index)
        if (navigatable != null && navigatable.canNavigate()) {
            ApplicationManager.getApplication().invokeLater {
                navigatable.navigate(true)
                closeHoverPopup()
                Timer(250) { closeHoverPopup() }.apply { isRepeats = false; start() }
            }
        }
        return LinkResolveResult.resolvedTarget(target)
    }

    private fun closeHoverPopup() {
        val manager = EditorMouseHoverPopupManager.getInstance()
        runCatching {
            @Suppress("DEPRECATION")
            manager.documentationComponent?.hint?.cancel()
        }
        runCatching {
            val method = manager.javaClass.declaredMethods.firstOrNull { m ->
                m.parameterCount == 0 && m.name.startsWith("cancelProcessingAndCloseHint")
            }
            method?.apply { isAccessible = true }?.invoke(manager)
        }
    }
}
