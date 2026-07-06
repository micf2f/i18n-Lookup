package com.github.micf2f.i18nlookup.hover

import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem

class I18nInlayActionHandler : InlayActionHandler {

    override fun handleClick(e: EditorMouseEvent, payload: InlayActionPayload) {
        if (payload !is StringInlayActionPayload) return
        val raw = payload.text
        val separator = raw.lastIndexOf('|')
        if (separator < 0) return
        val path = raw.substring(0, separator)
        val offset = raw.substring(separator + 1).toIntOrNull() ?: return
        val project = e.editor.project ?: return
        val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return
        OpenFileDescriptor(project, file, offset).navigate(true)
    }

    companion object {
        const val HANDLER_ID = "i18n-lookup.goto"
    }
}
