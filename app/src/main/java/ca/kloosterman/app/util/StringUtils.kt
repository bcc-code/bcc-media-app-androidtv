package tv.brunstad.app.util

/**
 * Words that are lowercase in English title case (Chicago Manual of Style rules)
 * unless they appear as the first or last word.
 */
private val lowercaseWords = setOf(
    // Articles
    "a", "an", "the",
    // Coordinating conjunctions
    "and", "but", "or", "nor", "for", "so", "yet",
    // Prepositions
    "as", "at", "by", "from", "if", "in", "into",
    "of", "off", "on", "onto", "out", "over",
    "than", "to", "up", "via", "with"
)

/**
 * Converts a string to English title case using Chicago Manual of Style rules:
 * - First and last words always capitalised
 * - Articles, coordinating conjunctions, and short prepositions lowercase
 * - All other words capitalised
 * - Letters after the first are left as-is (preserves acronyms like "BCC")
 */
fun String.toEnglishTitleCase(): String {
    val words = this.trim().split(Regex("\\s+"))
    return words.mapIndexed { index, word ->
        if (word.isEmpty()) return@mapIndexed word
        val lower = word.lowercase()
        if (index == 0 || index == words.lastIndex || lower !in lowercaseWords) {
            word.replaceFirstChar { it.uppercaseChar() }
        } else {
            lower
        }
    }.joinToString(" ")
}

/**
 * Applies English title case when the active language is "en"; otherwise returns the string unchanged.
 */
fun String.titleCaseForLanguage(language: String): String =
    if (language == "en") toEnglishTitleCase() else this
