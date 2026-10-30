package com.vocabguard.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Lenient comparison for typed answers: ignores case, accents and Polish/German special letters,
 * punctuation, text in parentheses and a leading "the/a/an/to". Accepts any of several meanings
 * separated by "/", ";" or ",". One typo is tolerated in answers of 5+ letters.
 */
object AnswerChecker {

    enum class Verdict { Correct, Almost, Wrong }

    private val ARTICLES = listOf("the ", "a ", "an ", "to ")
    private val PARENTHESES = Regex("\\(.*?\\)")
    private val PUNCTUATION = Regex("[\\p{Punct}’‘“”«»„–—]")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")

    fun check(input: String, expected: String): Verdict {
        val given = normalize(input)
        if (given.isEmpty()) return Verdict.Wrong
        val accepted = acceptedForms(expected)
        return when {
            given in accepted -> Verdict.Correct
            accepted.any { it.length >= 5 && withinOneEdit(it, given) } -> Verdict.Almost
            else -> Verdict.Wrong
        }
    }

    private fun acceptedForms(expected: String): Set<String> =
        (expected.split('/', ';', ',') + expected)
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .toSet()

    fun normalize(raw: String): String {
        var s = raw.lowercase(Locale.ROOT)
            .replace(PARENTHESES, " ")
            .replace("ß", "ss")
            .replace("ł", "l")
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        s = s.replace(PUNCTUATION, " ").replace(WHITESPACE, " ").trim()
        ARTICLES.firstOrNull { s.startsWith(it) }?.let { s = s.removePrefix(it) }
        return s.trim()
    }

    private fun withinOneEdit(a: String, b: String): Boolean {
        if (abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++
                j++
                continue
            }
            edits++
            if (edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> {
                    i++
                    j++
                }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}
