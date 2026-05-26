package com.github.micf2f.i18nlookup.core

import com.github.micf2f.i18nlookup.settings.I18nSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent

@Service(Service.Level.PROJECT)
class TranslationService(private val project: Project) : Disposable {

    data class LangResult(val file: VirtualFile, val value: String?, val offset: Int)

    private data class Entry(val value: String, val offset: Int)
    private data class Parsed(val stamp: Long, val entries: Map<String, Entry>)

    @Volatile private var cache: Map<String, Parsed> = emptyMap()
    @Volatile private var orderedFiles: List<VirtualFile> = emptyList()

    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { isWatchedTranslationPath(it.path) }) refreshOpenEditors()
                }
            },
        )

        PsiManager.getInstance(project).addPsiTreeChangeListener(
            object : PsiTreeChangeAdapter() {
                override fun childrenChanged(event: PsiTreeChangeEvent) = onPsiChange(event)
                override fun childAdded(event: PsiTreeChangeEvent) = onPsiChange(event)
                override fun childRemoved(event: PsiTreeChangeEvent) = onPsiChange(event)
                override fun childReplaced(event: PsiTreeChangeEvent) = onPsiChange(event)
                override fun propertyChanged(event: PsiTreeChangeEvent) = onPsiChange(event)
                private fun onPsiChange(event: PsiTreeChangeEvent) {
                    val vf = event.file?.virtualFile ?: return
                    if (isWatchedTranslationPath(vf.path)) refreshOpenEditors()
                }
            },
            this,
        )
    }

    fun lookupAll(key: String): List<LangResult> {
        ensureLoaded()
        val parsedByPath = cache
        return orderedFiles.map { file ->
            val entry = parsedByPath[file.path]?.entries?.get(key)
            LangResult(file, entry?.value, entry?.offset ?: -1)
        }
    }

    fun invalidate() {
        cache = emptyMap()
        orderedFiles = emptyList()
    }

    override fun dispose() = Unit

    private fun isWatchedTranslationPath(path: String): Boolean {
        if (!path.endsWith(".json", ignoreCase = true)) return false
        val configured = I18nSettings.getInstance(project).state.translationFilePath
        if (configured.isBlank()) return false
        val root = configured.replace('\\', '/').trimEnd('/')
        return path == root || path.startsWith("$root/")
    }

    private fun refreshOpenEditors() {
        if (project.isDisposed) return
        invalidate()
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            resetDeclarativeInlayPassCache()
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }

    private fun resetDeclarativeInlayPassCache() {
        runCatching {
            val factory = Class.forName(
                "com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory",
            )
            val staticMethod = factory.declaredMethods.firstOrNull { m ->
                m.parameterCount == 0 && m.name.startsWith("resetModificationStamp")
            }
            if (staticMethod != null) {
                staticMethod.isAccessible = true
                staticMethod.invoke(null)
                return@runCatching
            }
            val companion = factory.getDeclaredField("Companion").get(null)
            companion.javaClass.declaredMethods
                .firstOrNull { it.parameterCount == 0 && it.name.startsWith("resetModificationStamp") }
                ?.apply { isAccessible = true }
                ?.invoke(companion)
        }
    }

    @Synchronized
    private fun ensureLoaded() {
        val path = I18nSettings.getInstance(project).state.translationFilePath
        if (path.isBlank()) {
            clear()
            return
        }
        val root = resolve(path)
        if (root == null) {
            clear()
            return
        }
        val files = if (root.isDirectory) {
            root.children
                .filter { !it.isDirectory && it.extension.equals("json", ignoreCase = true) }
                .sortedBy { it.name }
        } else {
            listOf(root)
        }
        if (files.isEmpty()) {
            clear()
            return
        }

        val previous = cache
        val updated = HashMap<String, Parsed>(files.size)
        ReadAction.run<RuntimeException> {
            for (file in files) {
                val stamp = file.modificationStamp
                val cached = previous[file.path]
                updated[file.path] = if (cached != null && cached.stamp == stamp) cached
                else Parsed(stamp, parse(file))
            }
        }
        cache = updated
        orderedFiles = files
    }

    private fun clear() {
        cache = emptyMap()
        orderedFiles = emptyList()
    }

    private fun resolve(path: String): VirtualFile? {
        val normalized = path.replace('\\', '/')
        val lfs = LocalFileSystem.getInstance()
        lfs.findFileByPath(normalized)?.let { return it }
        val base = project.basePath ?: return null
        return lfs.findFileByPath("$base/$normalized")
    }

    private fun parse(file: VirtualFile): Map<String, Entry> {
        val psi = PsiManager.getInstance(project).findFile(file) as? JsonFile ?: return emptyMap()
        val root = psi.topLevelValue as? JsonObject ?: return emptyMap()
        val result = LinkedHashMap<String, Entry>()
        flatten(root, "", result)
        return result
    }

    private fun flatten(obj: JsonObject, prefix: String, out: MutableMap<String, Entry>) {
        for (property in obj.propertyList) {
            val name = property.name
            val fullKey = if (prefix.isEmpty()) name else "$prefix.$name"
            when (val value = property.value) {
                is JsonObject -> flatten(value, fullKey, out)
                is JsonStringLiteral -> out[fullKey] = Entry(value.value, property.nameElement.textOffset)
                null -> {}
                else -> out[fullKey] = Entry(value.text, property.nameElement.textOffset)
            }
        }
    }

    companion object {
        fun getInstance(project: Project): TranslationService = project.service()
    }
}
