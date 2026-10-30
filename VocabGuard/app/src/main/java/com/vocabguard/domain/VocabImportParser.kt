package com.vocabguard.domain

data class ImportedPair(val foreign: String, val translation: String)

data class ImportPreview(
    val pairs: List<ImportedPair> = emptyList(),
    val skippedLineNumbers: List<Int> = emptyList(),
    val duplicateCount: Int = 0,
    val separatorName: String? = null,
    val truncated: Boolean = false,
)

/**
 * Turns pasted text or a TXT/CSV/TSV file into word pairs.
 *
 * One pair per line; the separator is detected automatically (tab, semicolon, equals sign,
 * spaced dash, colon or comma). Quoted CSV fields, extra columns (e.g. Anki tags), blank lines
 * and lines starting with # or // are handled.
 */
object VocabImportParser {
    const val MAX_LINES = 5_000
    const val MAX_FIELD_LENGTH = 60

    private data class Separator(val token: String, val name: String, val columnar: Boolean)

    private val SEPARATORS = listOf(
        Separator("\t", "tab", columnar = true),
        Separator(";", "semicolon", columnar = true),
        Separator(" = ", "equals sign", columnar = false),
        Separator("=", "equals sign", columnar = false),
        Separator(" – ", "dash", columnar = false),
        Separator(" — ", "dash", columnar = false),
        Separator(" - ", "dash", columnar = false),
        Separator(":", "colon", columnar = false),
        Separator(",", "comma", columnar = true),
    )

    private val WHITESPACE = Regex("\\s+")

    fun parse(raw: String, swapColumns: Boolean = false, skipFirstLine: Boolean = false): ImportPreview {
        val allLines = raw.removePrefix("\uFEFF").lines()
        val truncated = allLines.size > MAX_LINES
        val numbered = allLines.take(MAX_LINES)
            .mapIndexed { index, line -> (index + 1) to line.trim() }
            .filter { (_, line) -> line.isNotEmpty() && !line.startsWith("#") && !line.startsWith("//") }
            .let { if (skipFirstLine) it.drop(1) else it }

        if (numbered.isEmpty()) return ImportPreview(truncated = truncated)

        val separator = detect(numbered.map { it.second })
            ?: return ImportPreview(skippedLineNumbers = numbered.map { it.first }, truncated = truncated)

        val pairs = mutableListOf<ImportedPair>()
        val skipped = mutableListOf<Int>()
        val seen = HashSet<String>()
        var duplicates = 0

        for ((lineNumber, line) in numbered) {
            val fields = split(line, separator)
            if (fields == null) {
                skipped += lineNumber
                continue
            }
            val first = clean(fields.first)
            val second = clean(fields.second)
            if (first.isEmpty() || second.isEmpty() || first.length > MAX_FIELD_LENGTH || second.length > MAX_FIELD_LENGTH) {
                skipped += lineNumber
                continue
            }
            val pair = if (swapColumns) ImportedPair(second, first) else ImportedPair(first, second)
            if (!seen.add(pair.foreign.lowercase())) {
                duplicates++
                continue
            }
            pairs += pair
        }
        return ImportPreview(pairs, skipped, duplicates, separator.name, truncated)
    }

    /** Picks the first separator that splits most lines, else the one that splits the most. */
    private fun detect(lines: List<String>): Separator? {
        val counts = SEPARATORS.map { sep -> sep to lines.count { split(it, sep) != null } }
        val majority = maxOf(1.0, lines.size * 0.6)
        return counts.firstOrNull { it.second >= majority }?.first
            ?: counts.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }

    private fun split(line: String, separator: Separator): Pair<String, String>? {
        val token = separator.token
        val firstEnd: Int
        val secondStart: Int

        if (line.startsWith('"')) {
            val close = closingQuote(line, 0)
            if (close < 0) return null
            val afterQuote = line.substring(close + 1)
            val trimmed = if (token == "\t") afterQuote else afterQuote.trimStart()
            val bare = if (token == "\t") token else token.trim()
            if (!trimmed.startsWith(bare)) return null
            firstEnd = close + 1
            secondStart = line.length - trimmed.length + bare.length
        } else {
            val index = line.indexOf(token)
            if (index <= 0) return null
            firstEnd = index
            secondStart = index + token.length
        }

        var second = line.substring(secondStart)
        if (separator.columnar) second = firstColumn(second, token)
        return line.substring(0, firstEnd) to second
    }

    /** For CSV/TSV: keep only the next column (drops tags, notes, extra columns). */
    private fun firstColumn(rest: String, token: String): String {
        val trimmed = rest.trimStart()
        if (trimmed.startsWith('"')) {
            val close = closingQuote(trimmed, 0)
            return if (close > 0) trimmed.substring(0, close + 1) else trimmed
        }
        val next = rest.indexOf(token)
        return if (next >= 0) rest.substring(0, next) else rest
    }

    private fun closingQuote(text: String, openIndex: Int): Int {
        var i = openIndex + 1
        while (i < text.length) {
            if (text[i] == '"') {
                if (i + 1 < text.length && text[i + 1] == '"') {
                    i += 2
                    continue
                }
                return i
            }
            i++
        }
        return -1
    }

    private fun clean(field: String): String {
        var s = field.trim()
        if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) {
            s = s.substring(1, s.length - 1).replace("\"\"", "\"")
        }
        return s.replace(WHITESPACE, " ").trim()
    }
}
