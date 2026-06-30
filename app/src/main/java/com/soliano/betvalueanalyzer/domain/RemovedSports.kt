package com.soliano.betvalueanalyzer.domain

object RemovedSports {
    val keys = setOf(
        "snooker",
        "australian_football",
        "darts",
        "cricket",
        "field_hockey",
    )

    private val names = setOf(
        "snooker",
        "football australien",
        "australian football",
        "australian rules football",
        "flechettes",
        "fléchettes",
        "flechettes darts",
        "fléchettes darts",
        "darts",
        "cricket",
        "hockey sur gazon",
        "field hockey",
    )

    fun isRemovedSportKey(value: String): Boolean {
        val base = value.substringBefore('/').substringBefore(':').trim().lowercase(java.util.Locale.US)
        val normalizedBase = base.replace(Regex("[^a-z0-9]+"), "_").trim('_')
        return base in keys || normalizedBase in keys
    }

    fun isRemovedCompetitionKey(value: String): Boolean =
        keys.any { key -> value.startsWith("$key:", ignoreCase = true) || value.startsWith("$key/", ignoreCase = true) }

    fun isRemovedSportName(value: String): Boolean =
        value.normalizedRemovedKey() in names

    fun isAllowedSportKey(value: String): Boolean = !isRemovedSportKey(value)
}

private fun String.normalizedRemovedKey(): String =
    java.text.Normalizer.normalize(trim().lowercase(java.util.Locale.FRANCE), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
