package pt.tiago.blocknight

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Visible text lives only in strings.xml (AGENTS.md). This scans the UI sources for string literals that
 * carry words, after removing `$templates`. Internal identifiers (mode and difficulty keys) are allowed
 * by name.
 */
class HardcodedTextTest {
    private val allowed = setOf(
        // Difficulty and mode keys.
        "easy", "medium", "hard", "marathon", "marathon-",
        // Objective types from the level schema.
        "lines", "dig", "targets", "score", "quads", "tspins", "combo",
        // Annotation arguments.
        "DEPRECATION",
    )
    private val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val template = Regex("\\$\\{[^}]*\\}|\\$[A-Za-z_][A-Za-z0-9_]*")
    private val word = Regex("[A-Za-zÀ-ÿ]{2,}")

    @Test
    fun `ui sources contain no hard-coded visible text`() {
        val root = File("src/main/java/pt/tiago/blocknight")
        assertTrue("run from the app module: ${root.absolutePath}", root.isDirectory)
        val dirs = listOf(File(root, "ui"), File(root, "game"))
        val problems = ArrayList<String>()
        dirs.flatMap { d -> d.walkTopDown().filter { it.extension == "kt" }.toList() }.forEach { file ->
            file.readLines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*") ||
                    line.startsWith("import ") || line.startsWith("package ") || line.startsWith("@file:")
                ) return@forEachIndexed
                literal.findAll(raw).forEach { m ->
                    val value = m.groupValues[1]
                    val text = template.replace(value, "")
                    if (value in allowed) return@forEach
                    if (word.containsMatchIn(text)) problems += "${file.relativeTo(root)}:${index + 1}: \"$value\""
                }
            }
        }
        assertTrue("Hard-coded visible text:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
