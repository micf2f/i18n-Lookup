package com.github.micf2f.i18nlookup

import com.github.micf2f.i18nlookup.core.I18nKeyMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class I18nKeyMatcherTest {

    private val builtIns = listOf("t", "i18n.t", "\$t", "translate")

    @Test
    fun findsAllBuiltInFormats() {
        val matcher = I18nKeyMatcher(builtIns, emptyList())
        val text = """
            const a = t('hello.world');
            i18n.t("user.name");
            const b = ${'$'}t('menu.file');
            translate(`errors.notFound`);
        """.trimIndent()

        val keys = matcher.findMatches(text).map { it.key }.toSet()
        assertEquals(setOf("hello.world", "user.name", "menu.file", "errors.notFound"), keys)
    }

    @Test
    fun reportsKeyRangeWithoutQuotes() {
        val matcher = I18nKeyMatcher(listOf("t"), emptyList())
        val text = "x = t('abc')"
        val match = matcher.findMatches(text).single()

        assertEquals("abc", match.key)
        assertEquals("abc", text.substring(match.start, match.end))
    }

    @Test
    fun doesNotMatchUnrelatedFunctions() {
        val matcher = I18nKeyMatcher(listOf("t"), emptyList())
        val text = "format('x'); doStuff('y')"
        assertTrue(matcher.findMatches(text).isEmpty())
    }

    @Test
    fun customRegexUsesFirstCaptureGroup() {
        val matcher = I18nKeyMatcher(emptyList(), listOf("""__\(\s*['"]([^'"]+)['"]"""))
        val keys = matcher.findMatches("__('greeting.hi')").map { it.key }
        assertEquals(listOf("greeting.hi"), keys)
    }

    @Test
    fun emptyConfigMatchesNothing() {
        val matcher = I18nKeyMatcher(emptyList(), emptyList())
        assertTrue(matcher.isEmpty)
        assertTrue(matcher.findMatches("t('x')").isEmpty())
    }
}
