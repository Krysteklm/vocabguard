package com.vocabguard.domain

enum class CefrLevel {
    A1, A2, B1, B2;

    fun next(): CefrLevel? = entries.getOrNull(ordinal + 1)

    companion object {
        fun fromStored(value: String?): CefrLevel? = entries.firstOrNull { it.name == value }
    }
}

data class BankWord(val foreign: String, val native: String, val level: CefrLevel)

/**
 * Built-in, offline German -> English word list graded by CEFR level. Used by auto-add to pick
 * new words at the level the user is currently working on. The starter deck's words are included
 * so the user's level can be estimated from them.
 */
object GermanWordBank {

    private fun level(level: CefrLevel, vararg pairs: Pair<String, String>) =
        pairs.map { (foreign, native) -> BankWord(foreign, native, level) }

    val entries: List<BankWord> = buildList {
        addAll(
            level(
                CefrLevel.A1,
                "der Hund" to "the dog", "die Katze" to "the cat", "das Haus" to "the house",
                "das Wasser" to "the water", "das Brot" to "the bread", "der Apfel" to "the apple",
                "die Zeit" to "time", "der Tag" to "the day", "die Nacht" to "the night",
                "die Woche" to "the week", "das Jahr" to "the year", "die Stadt" to "the city",
                "der Zug" to "the train", "das Buch" to "the book", "der Freund" to "the friend",
                "die Familie" to "the family", "das Kind" to "the child", "die Schule" to "the school",
                "die Mutter" to "the mother", "der Vater" to "the father", "der Bruder" to "the brother",
                "die Schwester" to "the sister", "das Auto" to "the car", "die Milch" to "the milk",
                "der Kaffee" to "the coffee", "der Tisch" to "the table", "der Stuhl" to "the chair",
                "das Zimmer" to "the room", "die Straße" to "the street", "die Tür" to "the door",
                "das Fenster" to "the window", "gehen" to "to go", "kommen" to "to come",
                "sehen" to "to see", "essen" to "to eat", "trinken" to "to drink",
                "schlafen" to "to sleep", "kaufen" to "to buy", "wohnen" to "to live (reside)",
                "heißen" to "to be called", "groß" to "big", "klein" to "small", "gut" to "good",
                "schlecht" to "bad", "alt" to "old", "neu" to "new", "heiß" to "hot", "kalt" to "cold",
                "heute" to "today", "morgen" to "tomorrow", "gestern" to "yesterday",
                "immer" to "always", "oft" to "often", "nie" to "never",
                "schnell" to "fast", "langsam" to "slow",
            ),
        )
        addAll(
            level(
                CefrLevel.A2,
                "die Arbeit" to "work", "der Weg" to "the way", "die Welt" to "the world",
                "das Leben" to "life", "die Frage" to "the question", "die Antwort" to "the answer",
                "das Geld" to "money", "der Schlüssel" to "the key", "der Himmel" to "the sky",
                "die Sonne" to "the sun", "der Mond" to "the moon", "der Urlaub" to "the vacation",
                "das Wetter" to "the weather", "die Reise" to "the trip", "der Bahnhof" to "the train station",
                "die Rechnung" to "the bill", "der Arzt" to "the doctor", "das Geschenk" to "the gift",
                "die Nachricht" to "the message", "der Termin" to "the appointment",
                "sprechen" to "to speak", "lernen" to "to learn", "verstehen" to "to understand",
                "vergessen" to "to forget", "versuchen" to "to try", "brauchen" to "to need",
                "glauben" to "to believe", "warten" to "to wait", "finden" to "to find",
                "bleiben" to "to stay", "helfen" to "to help", "fragen" to "to ask",
                "wissen" to "to know (a fact)", "erklären" to "to explain", "bezahlen" to "to pay",
                "vorbereiten" to "to prepare", "schön" to "beautiful", "wichtig" to "important",
                "einfach" to "simple", "schwierig" to "difficult", "müde" to "tired",
                "glücklich" to "happy", "vielleicht" to "maybe", "genug" to "enough",
                "zusammen" to "together", "allein" to "alone", "billig" to "cheap",
                "teuer" to "expensive", "krank" to "sick", "bereit" to "ready",
            ),
        )
        addAll(
            level(
                CefrLevel.B1,
                "die Erfahrung" to "the experience", "die Entscheidung" to "the decision",
                "die Umwelt" to "the environment", "die Gesellschaft" to "society",
                "der Vorteil" to "the advantage", "der Nachteil" to "the disadvantage",
                "die Meinung" to "the opinion", "die Bedingung" to "the condition",
                "die Gelegenheit" to "the opportunity", "der Zusammenhang" to "the connection",
                "die Ausbildung" to "vocational training", "die Bewerbung" to "the job application",
                "der Vertrag" to "the contract", "die Verantwortung" to "responsibility",
                "die Möglichkeit" to "the possibility", "das Ergebnis" to "the result",
                "die Wirkung" to "the effect", "sich erinnern" to "to remember",
                "sich entscheiden" to "to decide", "entwickeln" to "to develop",
                "vergleichen" to "to compare", "vermeiden" to "to avoid", "erwarten" to "to expect",
                "beschreiben" to "to describe", "behaupten" to "to claim", "überzeugen" to "to convince",
                "unterstützen" to "to support", "verbessern" to "to improve",
                "zufrieden" to "satisfied", "ehrlich" to "honest", "geduldig" to "patient",
                "deutlich" to "clear", "ähnlich" to "similar", "wahrscheinlich" to "probably",
                "trotzdem" to "nevertheless", "allerdings" to "however", "sogar" to "even",
                "ziemlich" to "quite", "eigentlich" to "actually", "sofort" to "immediately",
                "gewöhnlich" to "usual",
            ),
        )
        addAll(
            level(
                CefrLevel.B2,
                "die Herausforderung" to "the challenge", "die Voraussetzung" to "the prerequisite",
                "der Aufwand" to "the effort", "die Auswirkung" to "the impact",
                "die Nachhaltigkeit" to "sustainability", "der Widerspruch" to "the contradiction",
                "die Einschränkung" to "the restriction", "die Gewohnheit" to "the habit",
                "der Eindruck" to "the impression", "die Absicht" to "the intention",
                "das Vorurteil" to "the prejudice", "die Rücksicht" to "consideration",
                "die Ursache" to "the cause", "der Ansatz" to "the approach",
                "beeinflussen" to "to influence", "berücksichtigen" to "to take into account",
                "bewältigen" to "to cope with", "verzichten" to "to do without",
                "gewährleisten" to "to guarantee", "vernachlässigen" to "to neglect",
                "befürworten" to "to advocate", "erläutern" to "to elucidate", "zögern" to "to hesitate",
                "sich gewöhnen" to "to get used to", "umfassend" to "comprehensive",
                "nachvollziehbar" to "understandable", "vielfältig" to "diverse",
                "erheblich" to "considerable", "anspruchsvoll" to "demanding",
                "selbstverständlich" to "self-evident", "zuverlässig" to "reliable",
                "vorsichtig" to "careful", "eindeutig" to "unambiguous", "angemessen" to "appropriate",
                "gelegentlich" to "occasionally", "keineswegs" to "by no means", "dennoch" to "yet",
                "hingegen" to "on the other hand", "zunehmend" to "increasingly",
                "überwiegend" to "predominantly",
            ),
        )
    }
}
