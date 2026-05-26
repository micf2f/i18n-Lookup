package com.github.micf2f.i18nlookup.core

class I18nKeyMatcher(
    functionNames: List<String>,
    customRegexes: List<String>,
) {

    data class Match(val start: Int, val end: Int, val key: String)

    private data class Compiled(val regex: Regex, val keyGroups: List<Int>)

    private val compiled: List<Compiled> = buildList {
        for (fn in functionNames) {
            if (fn.isBlank()) continue
            val pattern = "(?<![\\w$])" + Regex.escape(fn) +
                "\\s*\\(\\s*([\"'`])(.*?)\\1(?:\\s*\\)\\s*\\.([\\w$]+(?:\\.[\\w$]+)*))?"
            runCatching { Regex(pattern) }.getOrNull()?.let { add(Compiled(it, listOf(2, 3))) }
        }
        for (raw in customRegexes) {
            if (raw.isBlank()) continue
            val regex = runCatching { Regex(raw) }.getOrNull() ?: continue
            val groupCount = runCatching { regex.toPattern().matcher("").groupCount() }.getOrDefault(0)
            add(Compiled(regex, listOf(if (groupCount >= 1) 1 else 0)))
        }
    }

    val isEmpty: Boolean get() = compiled.isEmpty()

    fun findMatches(text: CharSequence): List<Match> {
        if (compiled.isEmpty()) return emptyList()
        val out = ArrayList<Match>()
        val seen = HashSet<Long>()
        for (c in compiled) {
            for (m in c.regex.findAll(text)) {
                val present = c.keyGroups.mapNotNull { m.groups[it] }
                    .filter { it.value.isNotEmpty() }
                if (present.isEmpty()) continue
                val key = present.joinToString(".") { it.value }
                val start = present.first().range.first
                val end = present.last().range.last + 1
                val id = (start.toLong() shl 32) or (end.toLong() and 0xffffffffL)
                if (seen.add(id)) out.add(Match(start, end, key))
            }
        }
        return out
    }
}
