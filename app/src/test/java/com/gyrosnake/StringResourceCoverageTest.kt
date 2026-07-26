package com.gyrosnake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the rule that every user-facing string ships in all 21 locales. Reads
 * the XML as text rather than through the resource system, so it runs on the JVM
 * with no emulator and catches a missing translation at commit time instead of
 * on a reviewer's phone.
 */
class StringResourceCoverageTest {

    // Unit tests run with the module directory as the working directory.
    private val resDir = File("src/main/res")

    // Captures the whole opening tag so translatable="false" can be read off it.
    private val nameRegex = Regex("""<string name="([^"]+)"([^>]*)>""")

    /**
     * Keys a locale is expected to carry. Strings marked translatable="false" —
     * pure format strings and ASCII glyphs — are deliberately base-only, so
     * translators are not handed rows that must be copied verbatim.
     */
    private fun keysOf(dir: File): Set<String> =
        nameRegex.findAll(File(dir, "strings.xml").readText(Charsets.UTF_8))
            .filterNot { it.groupValues[2].contains("translatable=\"false\"") }
            .map { it.groupValues[1] }
            .toSet()

    private fun localeDirs(): List<File> =
        resDir.listFiles { f: File -> f.isDirectory && f.name.startsWith("values-") }!!
            .sortedBy { it.name }

    @Test
    fun `every locale defines every base string`() {
        val base = keysOf(File(resDir, "values"))
        assertTrue("no base strings found", base.isNotEmpty())
        for (dir in localeDirs()) {
            val missing = base - keysOf(dir)
            assertEquals("${dir.name} is missing translations", emptySet<String>(), missing)
        }
    }

    @Test
    fun `no locale defines a string the base does not`() {
        // A key only present in a translation is dead weight: nothing resolves it
        // when the app falls back to the base locale.
        val base = keysOf(File(resDir, "values"))
        for (dir in localeDirs()) {
            val extra = keysOf(dir) - base
            assertEquals("${dir.name} defines unknown strings", emptySet<String>(), extra)
        }
    }

    @Test
    fun `apostrophes are escaped`() {
        // Regression: an unescaped apostrophe fails the AAPT2 resource compile
        // with "Invalid unicode escape sequence", which points nowhere useful.
        val bad = Regex("""<string name="[^"]+">[^<]*[^\\]'""")
        val dirs = localeDirs() + File(resDir, "values")
        for (dir in dirs) {
            val text = File(dir, "strings.xml").readText(Charsets.UTF_8)
            val hit = bad.find(text)
            assertTrue("${dir.name}: unescaped apostrophe in ${hit?.value}", hit == null)
        }
    }
}
