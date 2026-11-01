package com.vocabguard.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Compares typed answers in two passes.
 *
 * Strict (counts as Correct): ignores case, punctuation, text in parentheses, extra spaces and a
 * leading English "the/a/an/to". Several meanings separated by "/", ";" or "," are all accepted.
 *
 * Loose (counts as Almost: accepted, but the lock screen shows the exact spelling):
 * - missing accents and special letters ("Schlussel" for "Schlüssel", "zolw" for "żółw", ss for ß)
 * - a missing or different German article ("Hund" for "der Hund") or a missing "sich"
 * - one typo in answers of 5+ letters
 */
object AnswerChecker {

    enum class Verdict { Correct, Almost, Wrong }

    private val ENGLISH_PARTICLES = listOf("the ", "a ", "an ", "to ")
    private val GERMAN_ARTICLES = listOf("der ", "die ", "das ", "den ", "dem ", "des ", "ein ", "eine ", "sich ")
    private val PARENTHESES = Regex("\\(.*?\\)")
    private val PUNCTUATION = Regex("[\\p{Punct}’‘“”«»„–—]")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")

    fun check(input: String, expected: String): Verdict {
        val forms = (expected.split('/', ';', ',') + expected).filter { it.isNotBlank() }

        val givenStrict = strict(input)
        if (givenStrict.isEmpty()) return Verdict.Wrong
        if (forms.any { strict(it) == givenStrict }) return Verdict.Correct

        val givenLoose = loose(input)
        val acceptedLoose = forms.map(::loose).filter { it.isNotEmpty() }
        return when {
            givenLoose.isNotEmpty() && givenLoose in acceptedLoose -> Verdict.Almost
            acceptedLoose.any { it.length >= 5 && withinOneEdit(it, givenLoose) } -> Verdict.Almost
            else -> Verdict.Wrong
        }
    }

    private fun strict(raw: String): String {
        var s = raw.lowercase(Locale.ROOT)
            .replace(PARENTHESES, " ")
            .replace(PUNCTUATION, " ")
            .replace(WHITESPACE, " ")
            .trim()
        ENGLISH_PARTICLES.firstOrNull { s.startsWith(it) }?.let { s = s.removePrefix(it) }
        return s.trim()
    }

    private fun loose(raw: String): String {
        var s = strict(raw).replace("ß", "ss").replace("ł", "l")
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        GERMAN_ARTICLES.firstOrNull { s.startsWith(it) }?.let { s = s.removePrefix(it) }
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
