package com.cardvault.scan

import java.time.Year

/** One line of text read off a card, with its height in pixels (bigger = more prominent). */
data class TextLine(val text: String, val height: Int)

data class ScanGuess(
    val player: String = "",
    val year: String = "",
    val brand: String = "",
    val setName: String = "",
    val cardNumber: String = "",
    val team: String = "",
    val position: String = "",
    val isGraded: Boolean = false,
    val grader: String = "",
    val grade: String = "",
    val certNumber: String = "",
)

/**
 * Turns the text read off the front and back of a card into a best guess at its details.
 * The guess is always shown to him to confirm or correct before saving.
 */
object CardTextParser {

    private val gradeWords = Regex(
        "\\b(GEM\\s*M(IN)?T|MINT|NM-MT\\+?|NM-MT|NM|EX-MT|EX|VG-EX|VG|GOOD|FAIR|POOR|AUTHENTIC)\\s*(10|[1-9](\\.5)?)?\\b",
    )
    private val graderWords = Regex("\\b(PSA|BGS|BECKETT|SGC|CGC)\\b", RegexOption.IGNORE_CASE)

    fun parse(front: List<TextLine>, back: List<TextLine>, barcodes: List<String>): ScanGuess {
        val all = front + back
        val allText = all.joinToString("\n") { it.text }
        val lower = allText.lowercase()

        // --- Graded slab? ---
        val frontText = front.joinToString("\n") { it.text }
        val graderMatch = graderWords.find(frontText)
        val gradeMatch = gradeWords.find(frontText.uppercase())
        val certFromBarcode = barcodes.map { it.filter(Char::isDigit) }.firstOrNull { it.length in 7..10 }
        val certFromText = Regex("\\b\\d{8,10}\\b").find(frontText)?.value
        val isGraded = graderMatch != null && (gradeMatch != null || certFromBarcode != null)
        val grader = when (val g = graderMatch?.value?.uppercase()) {
            null -> ""
            "BECKETT" -> "BGS"
            else -> g
        }
        val grade = if (isGraded) gradeMatch?.value?.trim().orEmpty() else ""

        // --- Brand and set ---
        val brand = Catalog.brands.firstOrNull { (_, keys) ->
            keys.any { k -> Regex("\\b${Regex.escape(k)}\\b").containsMatchIn(lower) }
        }?.first.orEmpty()
        val setName = Catalog.sets.firstOrNull { s ->
            Regex("\\b${Regex.escape(s.lowercase())}\\b").containsMatchIn(lower)
        }.orEmpty()

        // --- Year: prefer the copyright year, otherwise the latest year mentioned ---
        val thisYear = Year.now().value
        val copyright = Regex("(©|\\(c\\)|copyright)\\s*(19[4-9]\\d|20[0-4]\\d)", RegexOption.IGNORE_CASE)
            .find(allText)?.groupValues?.get(2)
        val years = Regex("\\b(19[4-9]\\d|20[0-4]\\d)\\b").findAll(allText)
            .map { it.value.toInt() }.filter { it <= thisYear }.toList()
        val year = copyright ?: years.maxOrNull()?.toString().orEmpty()

        // --- Card number ---
        val cardNumber = Regex("(?:\\bNO\\.?|#)\\s*([A-Z]{0,3}-?\\d{1,4}[A-Z]?)\\b", RegexOption.IGNORE_CASE)
            .find(back.joinToString("\n") { it.text })?.groupValues?.get(1)
            ?: Regex("(?:\\bNO\\.?|#)\\s*([A-Z]{0,3}-?\\d{1,4}[A-Z]?)\\b", RegexOption.IGNORE_CASE)
                .find(frontText)?.groupValues?.get(1)
            ?: ""

        // --- Team ---
        val team = Catalog.teams.firstOrNull { (full, nick) ->
            lower.contains(full.lowercase()) ||
                Regex("\\b${Regex.escape(nick.lowercase())}\\b").containsMatchIn(lower)
        }?.first.orEmpty()

        // --- Position (front first: backs list many positions in bios) ---
        fun findPosition(text: String): String? {
            val t = text.lowercase()
            return Catalog.positions.firstOrNull { (_, keys) ->
                keys.any { k -> Regex("(^|[^a-z])${Regex.escape(k)}([^a-z]|$)").containsMatchIn(t) }
            }?.first
        }
        val position = findPosition(frontText) ?: findPosition(back.take(6).joinToString("\n") { it.text }) ?: ""

        // --- Player name: the most prominent name-like line on the front ---
        val player = pickName(front) ?: pickName(back.take(5)) ?: ""

        return ScanGuess(
            player = player,
            year = year,
            brand = brand,
            setName = setName,
            cardNumber = cardNumber,
            team = team,
            position = position,
            isGraded = isGraded,
            grader = if (isGraded) grader else "",
            grade = grade,
            certNumber = if (isGraded) (certFromBarcode ?: certFromText).orEmpty() else "",
        )
    }

    private val notNameWords: Set<String> by lazy {
        val s = mutableSetOf(
            "rookie", "card", "cards", "football", "nfl", "players", "inc", "all", "rights", "reserved",
            "licensed", "official", "printed", "usa", "team", "season", "stats", "career", "totals",
            "gem", "mint", "psa", "bgs", "sgc", "cgc", "beckett", "rc", "draft", "pick", "pro", "bowl",
        )
        Catalog.brands.forEach { (b, keys) -> s += b.lowercase().split(" "); keys.forEach { s += it.split(" ") } }
        Catalog.teams.forEach { (full, nick) -> s += full.lowercase().split(" "); s += nick.lowercase() }
        Catalog.positions.forEach { (_, keys) -> keys.forEach { s += it.split(" ") } }
        Catalog.sets.forEach { s += it.lowercase().split(" ") }
        s
    }

    private fun pickName(lines: List<TextLine>): String? {
        val nameRx = Regex("^[A-Za-z][A-Za-z.'\\-]*(\\s+[A-Za-z][A-Za-z.'\\-]*){1,3}$")
        return lines
            .map { it.copy(text = it.text.trim().replace(Regex("^#?\\d+\\s+"), "")) }
            .filter { nameRx.matches(it.text) }
            .filter { line ->
                val words = line.text.lowercase().split(Regex("\\s+"))
                words.none { it.trim('.', '\'') in notNameWords }
            }
            .maxByOrNull { it.height }
            ?.text
            ?.let(::tidyName)
    }

    private fun tidyName(raw: String): String {
        val isAllCaps = raw == raw.uppercase()
        if (!isAllCaps) return raw
        return raw.lowercase().split(" ").joinToString(" ") { word ->
            word.split("-").joinToString("-") { part ->
                part.replaceFirstChar { it.uppercase() }
                    .let { if (it.startsWith("Mc") && it.length > 2) "Mc" + it[2].uppercase() + it.substring(3) else it }
            }
        }
    }
}
