package com.soliano.betvalueanalyzer.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.remote.EspnCompetitionDto
import com.soliano.betvalueanalyzer.data.remote.EspnEventDto
import com.soliano.betvalueanalyzer.data.remote.EspnSeasonDto
import com.soliano.betvalueanalyzer.data.remote.EspnStatusDto
import com.soliano.betvalueanalyzer.data.remote.EspnStatusTypeDto
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class CyclingCalendarProvider(private val api: PublicSportsApiService) {
    suspend fun load(today: LocalDate = LocalDate.now(ZoneOffset.UTC)): List<EspnEventDto> {
        val maxDate = today.plusDays(365)
        val uciEvents = UCI_FEEDS.flatMap { feed ->
            getRaw(feed.url)?.let { raw -> parseUci(raw, feed.series, today, maxDate) }.orEmpty()
        }
        val sportsDbEvents = getRaw("https://www.thesportsdb.com/api/v1/json/123/eventsnextleague.php?id=4465")
            ?.let { parseTheSportsDb(it, today, maxDate) }.orEmpty()
        return (uciEvents + sportsDbEvents)
            .filter { event -> event.date?.let(::parseDate)?.let { it in today..maxDate } == true }
            .distinctBy { event -> event.uid ?: event.name.orEmpty().lowercase() + event.date.orEmpty() }
            .sortedBy { it.date }
            .take(160)
    }

    private suspend fun getRaw(url: String): String? {
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return response.body()?.string()
    }

    private fun parseUci(json: String, series: String, today: LocalDate, maxDate: LocalDate): List<EspnEventDto> = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        root.array("items").flatMap { month ->
            month.asObject()?.array("items").orEmpty()
        }.flatMap { day ->
            val date = day.asObject()?.text("competitionDate") ?: return@flatMap emptyList()
            val dayDate = parseDate(date) ?: return@flatMap emptyList()
            if (dayDate !in today..maxDate) return@flatMap emptyList()
            day.asObject()?.array("items").orEmpty().mapNotNull { item ->
                val event = item.asObject() ?: return@mapNotNull null
                val name = event.text("name") ?: return@mapNotNull null
                if (!isUsefulCyclingRace(name, series)) return@mapNotNull null
                val details = event.obj("detailsLink")
                val detailsId = details?.text("url")?.substringAfterLast('/') ?: stableId("$name-$date-${event.text("country").orEmpty()}")
                val venue = event.text("venue").orEmpty()
                val country = event.text("country").orEmpty()
                val dates = event.text("dates").orEmpty()
                val display = listOf(name, venue.takeIf { it.isNotBlank() }, country.takeIf { it.isNotBlank() })
                    .filterNotNull().joinToString(" · ")
                EspnEventDto(
                    id = "uci-$detailsId",
                    uid = "uci:$detailsId",
                    date = date,
                    name = display,
                    shortName = if (dates.isNotBlank()) "$name · $dates" else name,
                    competitions = listOf(
                        EspnCompetitionDto(
                            id = detailsId,
                            date = date,
                            competitors = emptyList(),
                            odds = emptyList(),
                            status = EspnStatusDto(type = EspnStatusTypeDto(completed = false, state = "pre", name = "STATUS_SCHEDULED")),
                        )
                    ),
                    status = EspnStatusDto(type = EspnStatusTypeDto(completed = false, state = "pre", name = "STATUS_SCHEDULED")),
                    season = EspnSeasonDto(slug = name),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun parseTheSportsDb(json: String, today: LocalDate, maxDate: LocalDate): List<EspnEventDto> = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        root.array("events").mapNotNull { element ->
            val event = element.asObject() ?: return@mapNotNull null
            val id = event.text("idEvent") ?: return@mapNotNull null
            val timestamp = event.text("strTimestamp")?.let { if (it.endsWith("Z")) it else "${it}Z" } ?: return@mapNotNull null
            val date = parseDate(timestamp) ?: return@mapNotNull null
            if (date !in today..maxDate) return@mapNotNull null
            val name = event.text("strEvent") ?: return@mapNotNull null
            val city = event.text("strCity").orEmpty()
            val country = event.text("strCountry").orEmpty()
            val display = listOf(name, city.takeIf { it.isNotBlank() }, country.takeIf { it.isNotBlank() })
                .filterNotNull().joinToString(" · ")
            EspnEventDto(
                id = "sportsdb-cycling-$id",
                uid = "sportsdb:cycling:$id",
                date = timestamp,
                name = display,
                shortName = name,
                competitions = listOf(
                    EspnCompetitionDto(
                        id = id,
                        date = timestamp,
                        competitors = emptyList(),
                        odds = emptyList(),
                        status = EspnStatusDto(type = EspnStatusTypeDto(completed = false, state = "pre", name = "STATUS_SCHEDULED")),
                    )
                ),
                status = EspnStatusDto(type = EspnStatusTypeDto(completed = false, state = "pre", name = "STATUS_SCHEDULED")),
                season = EspnSeasonDto(slug = name.substringBefore(" Stage").trim()),
            )
        }
    }.getOrDefault(emptyList())

    private fun isUsefulCyclingRace(name: String, series: String): Boolean {
        if (series != "UCI Road") return true
        val normalized = name.lowercase()
        return MAJOR_RACE_TERMS.any { normalized.contains(it) } &&
            listOf("national championships", "championnats nationaux").none { normalized.contains(it) }
    }

    private fun parseDate(value: String): LocalDate? {
        val normalized = when {
            value.endsWith("Z") -> value
            "T" in value -> "${value}Z"
            else -> "${value}T00:00:00Z"
        }
        return runCatching { Instant.parse(normalized).atZone(ZoneOffset.UTC).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(value.substringBefore("T")) }.getOrNull()
    }

    private fun stableId(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(80)

    private data class UciFeed(val series: String, val url: String)

    private companion object {
        val UCI_FEEDS = listOf(
            UciFeed("UCI WorldTour", "https://www.uci.org/api/calendar/upcoming?discipline=ROA&seasonId=1056"),
            UciFeed("UCI Women's WorldTour", "https://www.uci.org/api/calendar/upcoming?discipline=ROA&seasonId=1057"),
            UciFeed("UCI ProSeries", "https://www.uci.org/api/calendar/upcoming?discipline=ROA&seasonId=1068"),
            UciFeed("UCI Events", "https://www.uci.org/api/calendar/upcoming?discipline=ROA&seasonId=1055"),
            UciFeed("UCI Road", "https://www.uci.org/api/calendar/upcoming?discipline=ROA"),
        )
        val MAJOR_RACE_TERMS = listOf(
            "tour de france",
            "giro d'italia",
            "giro d’italia",
            "la vuelta",
            "paris - roubaix",
            "paris-roubaix",
            "liège",
            "liege",
            "flèche wallonne",
            "fleche wallonne",
            "ronde van vlaanderen",
            "tour des flandres",
            "milano-sanremo",
            "milan-san remo",
            "il lombardia",
            "world championships",
            "championnats du monde",
            "grand prix cycliste",
            "critérium du dauphiné",
            "criterium du dauphine",
            "paris - nice",
            "tirreno-adriatico",
        )
    }
}

private fun JsonElement.asObject(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.obj(name: String): JsonObject? = get(name)?.asObject()
private fun JsonObject.array(name: String): List<JsonElement> =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.text(name: String): String? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
}.getOrNull()
