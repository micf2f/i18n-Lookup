package com.github.micf2f.i18nlookup.settings

import com.github.micf2f.i18nlookup.core.TranslationService
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class I18nSettingsConfigurable(private val project: Project) : Configurable {

    private val settings = I18nSettings.getInstance(project)
    private val model = settings.state.copy()
    private var panel = buildPanel()

    private fun buildPanel() = panel {
        row("Translation file or folder:") {
            textFieldWithBrowseButton(
                "Select Translation File Or Folder",
                project,
                FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor(),
            ) { it.path }
                .bindText(model::translationFilePath)
                .align(AlignX.FILL)
        }
        row {
            comment(
                "Select a JSON file to display one language. To compare multiple languages, select the " +
                    "folder containing all your translation files with a <code>*.json</code> extension.",
            )
        }

        group("Translations view") {
            buttonsGroup {
                row {
                    radioButton("Tooltip on hover", I18nSettings.DisplayMode.TOOLTIP)
                        .comment("Displays each translation in a popup when you hover over the key. Click a language to open its JSON file.")
                }
                row {
                    radioButton("Inline in code", I18nSettings.DisplayMode.INLINE)
                        .comment("Displays each translation as an inlay badge beside the key. Use Ctrl+Click on a badge to open its JSON file.")
                }
            }.bind(model::displayMode)
        }

        group("Recognized call formats") {
            row { checkBox("t('key')").bindSelected(model::enableT) }
            row { checkBox("i18n.t('key')").bindSelected(model::enableI18nT) }
            row { checkBox("\$t('key')").bindSelected(model::enableDollarT) }
            row { checkBox("translate('key')").bindSelected(model::enableTranslate) }
        }

        group("Custom regex (optional)") {
            row {
                textField()
                    .bindText(model::customRegex)
                    .align(AlignX.FILL)
            }
            row {
                comment(
                    "If your code calls translations in a way the formats above don't cover " +
                        "(e.g. <code>__('key')</code> or <code>tr('key')</code>). Write a regex " +
                        "that matches the call and wrap the key in the first parentheses " +
                        "<code>(&hellip;)</code> &mdash; that captured part is used as the key.",
                )
            }
            row {
                comment(
                    "Example: <code>__\\(\\s*['\"]([^'\"]+)['\"]</code> matches <code>__('greeting.hi')</code> " +
                        "and captures <code>greeting.hi</code>.",
                )
            }
        }
    }

    override fun getDisplayName(): String = "i18n Lookup"

    override fun createComponent(): JComponent {
        panel = buildPanel()
        return panel
    }

    override fun isModified(): Boolean = panel.isModified()

    override fun apply() {
        panel.apply()
        settings.loadState(model.copy())
        TranslationService.getInstance(project).invalidate()
        PsiManager.getInstance(project).dropPsiCaches()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    override fun reset() {
        panel.reset()
    }
}
