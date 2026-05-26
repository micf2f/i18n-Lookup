package com.github.micf2f.i18nlookup.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "I18nLookupSettings", storages = [Storage("i18nLookup.xml")])
class I18nSettings : PersistentStateComponent<I18nSettings.State> {

    data class State(
        var translationFilePath: String = "",
        var enableT: Boolean = true,
        var enableI18nT: Boolean = true,
        var enableDollarT: Boolean = true,
        var enableTranslate: Boolean = true,
        var customRegex: String = "",
        var displayMode: DisplayMode = DisplayMode.TOOLTIP,
    )

    enum class DisplayMode { TOOLTIP, INLINE }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(newState: State) {
        XmlSerializerUtil.copyBean(newState, state)
    }

    fun enabledFunctionNames(): List<String> = buildList {
        if (state.enableT) add("t")
        if (state.enableI18nT) add("i18n.t")
        if (state.enableDollarT) add("\$t")
        if (state.enableTranslate) add("translate")
    }

    fun customRegexes(): List<String> =
        state.customRegex.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()

    companion object {
        fun getInstance(project: Project): I18nSettings = project.service()
    }
}
