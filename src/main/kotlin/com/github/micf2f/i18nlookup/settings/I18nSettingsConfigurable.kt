package com.github.micf2f.i18nlookup.settings

import com.github.micf2f.i18nlookup.core.TranslationService
import com.github.micf2f.i18nlookup.core.restartCodeHighlighting
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import java.awt.Cursor
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class I18nSettingsConfigurable(private val project: Project) : Configurable {

    private val settings = I18nSettings.getInstance(project)
    private val model = settings.state.copy()
    private var panel = buildPanel()

    private fun buildPanel(): DialogPanel {
        lateinit var additionalField: TextFieldWithBrowseButton
        lateinit var inlineRadio: Cell<JBRadioButton>
        lateinit var tooltipRadio: Cell<JBRadioButton>

        fun syncInlineAvailability() {
            val twoPaths = additionalField.text.isNotBlank()
            val inline = inlineRadio.component
            if (twoPaths && inline.isSelected) {
                tooltipRadio.component.isSelected = true
            }
            inlineRadio.enabled(!twoPaths)
            HelpTooltip.dispose(inline)
            if (twoPaths) {
                HelpTooltip()
                    .setDescription("Not available while a second translation source is configured")
                    .installOn(inline)
            }
        }

        val built = panel {
            group("Translation file or folder") {
                row("Path:") {
                    cell(browseField("Select Translation File Or Folder"))
                        .bindText(model::translationFilePath)
                        .align(AlignX.FILL)
                }

                additionalField = browseField("Select Additional Translation File Or Folder")
                lateinit var addRow: Row
                lateinit var additionalRow: Row

                addRow = row {
                    link("Add additional path +") {
                        additionalRow.visible(true)
                        addRow.visible(false)
                    }
                }
                additionalRow = row("Additional path:") {
                    cell(additionalField)
                        .bindText(model::additionalTranslationFilePath)
                        .align(AlignX.FILL)
                        .resizableColumn()
                    val removeButton = JButton(AllIcons.Actions.GC).apply {
                        toolTipText = "Remove additional path"
                        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        addActionListener {
                            additionalField.text = ""
                            additionalRow.visible(false)
                            addRow.visible(true)
                        }
                    }
                    cell(removeButton)
                }

                val hasAdditional = model.additionalTranslationFilePath.isNotBlank()
                addRow.visible(!hasAdditional)
                additionalRow.visible(hasAdditional)

                row {
                    comment(
                        "Select a JSON file to display one language. To compare multiple languages, select the " +
                            "folder containing all your translation files with a <code>*.json</code> extension.",
                    )
                }
            }

            group("Translations view") {
                buttonsGroup {
                    row {
                        tooltipRadio = radioButton("Tooltip on hover", I18nSettings.DisplayMode.TOOLTIP)
                            .comment("Displays each translation in a popup when you hover over the key. Click a language to open its JSON file.")
                    }
                    row {
                        inlineRadio = radioButton("Inline in code", I18nSettings.DisplayMode.INLINE)
                            .comment("Displays each translation as an inlay badge beside the key. Use Ctrl+Click on a badge to open its JSON file. Unavailable when a second source is configured.")
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
        additionalField.textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = syncInlineAvailability()
            override fun removeUpdate(e: DocumentEvent) = syncInlineAvailability()
            override fun changedUpdate(e: DocumentEvent) = syncInlineAvailability()
        })
        syncInlineAvailability()
        return built
    }

    private fun browseField(dialogTitle: String): TextFieldWithBrowseButton {
        val descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
            .withTitle(dialogTitle)
        return TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(project, descriptor)
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
        if (model.additionalTranslationFilePath.isNotBlank() &&
            model.displayMode == I18nSettings.DisplayMode.INLINE
        ) {
            model.displayMode = I18nSettings.DisplayMode.TOOLTIP
        }
        settings.loadState(model.copy())
        TranslationService.getInstance(project).invalidate()
        PsiManager.getInstance(project).dropPsiCaches()
        restartCodeHighlighting(project)
    }

    override fun reset() {
        panel.reset()
    }
}
