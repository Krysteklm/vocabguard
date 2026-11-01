package com.vocabguard.domain

enum class QuizMode {
    /** Foreign word -> pick the translation (A/B/C). */
    CHOICE,

    /** Translation -> pick the foreign word (A/B/C). */
    REVERSE_CHOICE,

    /** Foreign word -> type the translation. */
    TYPING,

    /** Translation -> type the foreign word (e.g. see "klucz", write "der Schlüssel"). */
    REVERSE_TYPING,

    /** "Word = meaning?" -> true / false. */
    TRUE_FALSE;

    companion object {
        val DEFAULT: Set<QuizMode> = setOf(CHOICE, REVERSE_CHOICE)

        fun fromStored(values: Set<String>?): Set<QuizMode> =
            values.orEmpty()
                .mapNotNull { name -> entries.firstOrNull { it.name == name } }
                .toSet()
                .ifEmpty { DEFAULT }
    }
}

sealed interface QuizQuestion {
    val wordId: Long
    val prompt: String
    val correctAnswer: String

    /** Built-in fallback questions use negative ids and aren't written back. */
    val isTracked: Boolean get() = wordId > 0

    data class Choice(
        override val wordId: Long,
        override val prompt: String,
        val options: List<String>,
        val correctIndex: Int,
        val reversed: Boolean,
    ) : QuizQuestion {
        override val correctAnswer: String get() = options[correctIndex]
    }

    data class Typing(
        override val wordId: Long,
        override val prompt: String,
        override val correctAnswer: String,
        /** true = the prompt is the translation and the answer is the foreign word. */
        val reversed: Boolean = false,
    ) : QuizQuestion

    data class TrueFalse(
        override val wordId: Long,
        override val prompt: String,
        val proposedTranslation: String,
        val isMatch: Boolean,
        override val correctAnswer: String,
    ) : QuizQuestion
}
