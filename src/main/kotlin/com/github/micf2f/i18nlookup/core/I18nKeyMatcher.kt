package com.github.micf2f.i18nlookup.core

class I18nKeyMatcher(
    functionNames: List<String>,
    customRegexes: List<String>,
) {

    data class Match(val start: Int, val end: Int, val key: String)

    private data class Compiled(val regex: Regex, val keyGroup: Int)

    private val compiled: List<Compiled> = buildList {
        for (fn in functionNames) {
            if (fn.isBlank()) continue
            val pattern = "(?<![\\w$])" + Regex.escape(fn) + "\\s*\\(\\s*([\"'`])(.*?)\\1"
            runCatching { Regex(pattern) }.getOrNull()?.let { add(Compiled(it, 2)) }
        }
        for (raw in customRegexes) {
            if (raw.isBlank()) continue
            val regex = runCatching { Regex(raw) }.getOrNull() ?: continue
            val groupCount = runCatching { regex.toPattern().matcher("").groupCount() }.getOrDefault(0)
            add(Compiled(regex, if (groupCount >= 1) 1 else 0))
        }
    }

    val isEmpty: Boolean get() = compiled.isEmpty()

    fun findMatches(text: CharSequence): List<Match> {
        if (compiled.isEmpty()) return emptyList()
        val out = ArrayList<Match>()
        val seen = HashSet<Long>()
        for (c in compiled) {
            for (m in c.regex.findAll(text)) {
                val group = m.groups[c.keyGroup] ?: continue
                val key = group.value
                if (key.isEmpty()) continue
                val start = group.range.first
                val end = group.range.last + 1
                val id = (start.toLong() shl 32) or (end.toLong() and 0xffffffffL)
                if (seen.add(id)) out.add(Match(start, end, key))
            }
        }
        return out
    }
}
