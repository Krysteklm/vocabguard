package com.vocabguard.domain

/**
 * How long a wrong (or "close enough") answer stays on screen, with the right answer
 * highlighted, before the next question. [MANUAL] waits for the "Next question" button.
 */
enum class ReviewPace(val millis: Long?) {
    QUICK(1_500),
    NORMAL(3_000),
    SLOW(5_000),
    MANUAL(null);

    companion object {
        val DEFAULT = NORMAL

        fun fromStored(value: String?): ReviewPace = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
