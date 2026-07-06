package com.github.micf2f.i18nlookup.core

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.Project

internal fun restartCodeHighlighting(project: Project) {
    val daemon = DaemonCodeAnalyzer.getInstance(project)
    runCatching {
        val restartWithReason = daemon.javaClass.getMethod("restart", Any::class.java)
        restartWithReason.invoke(daemon, "i18n-lookup: translation source changed")
        return
    }
    runCatching {
        daemon.javaClass.getMethod("restart").invoke(daemon)
    }
}
