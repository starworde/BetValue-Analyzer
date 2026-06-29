package com.soliano.betvalueanalyzer.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object EspnFallbackParser {
    fun parse(json: String): List<EspnEventDto> = runCatching {
        JsonParser.parseString(json).asJsonObject.array("events").mapNotNull { element ->
            val event = element.asObject() ?: return@mapNotNull null
            val id = event.text("id") ?: return@mapNotNull null
            EspnEventDto(
                id = id,
                uid = event.text("uid"),
                date = event.text("date"),
                endDate = event.text("endDate"),
                name = event.text("name"),
                shortName = event.text("shortName"),
                competitions = event.array("competitions").mapNotNull(::competition),
                groupings = event.array("groupings").mapNotNull(::grouping),
                status = status(event.obj("status")),
            )
        }
    }.getOrDefault(emptyList())

    private fun grouping(element: JsonElement): EspnGroupingDto? {
        val value = element.asObject() ?: return null
        val grouping = value.obj("grouping")
        return EspnGroupingDto(
            grouping = grouping?.let {
                EspnGroupingInfoDto(
                    id = it.text("id"),
                    slug = it.text("slug"),
                    displayName = it.text("displayName"),
                )
            },
            competitions = value.array("competitions").mapNotNull(::competition),
        )
    }

    private fun competition(element: JsonElement): EspnCompetitionDto? {
        val value = element.asObject() ?: return null
        return EspnCompetitionDto(
            id = value.text("id"),
            uid = value.text("uid"),
            date = value.text("date"),
            startDate = value.text("startDate"),
            type = value.obj("type")?.let { type ->
                EspnCompetitionTypeDto(
                    id = type.text("id"),
                    abbreviation = type.text("abbreviation"),
                )
            },
            round = value.obj("round")?.let { round ->
                EspnRoundDto(
                    id = round.text("id"),
                    displayName = round.text("displayName"),
                )
            },
            competitors = value.array("competitors").mapNotNull(::competitor),
            odds = emptyList(),
            status = status(value.obj("status")),
        )
    }

    private fun competitor(element: JsonElement): EspnCompetitorDto? {
        val value = element.asObject() ?: return null
        return EspnCompetitorDto(
            id = value.text("id"),
            order = value.int("order"),
            homeAway = value.text("homeAway"),
            team = value.obj("team")?.text("displayName")?.let(::EspnParticipantDto),
            athlete = value.obj("athlete")?.text("displayName")?.let(::EspnParticipantDto),
            form = value.text("form"),
            score = value.text("score"),
            winner = value.bool("winner"),
            records = value.array("records").mapNotNull { record ->
                record.asObject()?.text("summary")?.let(::EspnRecordDto)
            },
            statistics = value.array("statistics").mapNotNull { statistic ->
                val stat = statistic.asObject() ?: return@mapNotNull null
                EspnStatisticDto(
                    name = stat.text("name"),
                    displayName = stat.text("displayName"),
                    shortDisplayName = stat.text("shortDisplayName"),
                    abbreviation = stat.text("abbreviation"),
                    displayValue = stat.text("displayValue"),
                    value = stat.number("value"),
                )
            },
        )
    }

    private fun status(value: JsonObject?): EspnStatusDto? {
        val type = value?.obj("type") ?: return null
        return EspnStatusDto(
            clock = value.number("clock"),
            displayClock = value.text("displayClock"),
            period = value.int("period"),
            type = EspnStatusTypeDto(
                completed = type.bool("completed") ?: false,
                state = type.text("state"),
                name = type.text("name"),
                description = type.text("description"),
                detail = type.text("detail"),
                shortDetail = type.text("shortDetail"),
            ),
        )
    }
}

data class ExternalScheduleEvent(
    val id: String,
    val timestamp: String,
    val eventName: String,
    val sport: String,
    val leagueId: String,
    val leagueName: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeTeamId: String?,
    val awayTeamId: String?,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val status: String? = null,
    val progress: String? = null,
)

object TheSportsDbFallbackParser {
    fun parse(json: String): List<ExternalScheduleEvent> = runCatching {
        JsonParser.parseString(json).asJsonObject.array("events").mapNotNull { element ->
            val event = element.asObject() ?: return@mapNotNull null
            val id = event.text("idEvent") ?: return@mapNotNull null
            val timestamp = event.text("strTimestamp") ?: event.fallbackTimestamp() ?: return@mapNotNull null
            val name = event.text("strEvent") ?: return@mapNotNull null
            val sport = event.text("strSport") ?: return@mapNotNull null
            val league = event.text("strLeague") ?: "Compétition"
            ExternalScheduleEvent(
                id = id,
                timestamp = timestamp.normalizedExternalTimestamp(),
                eventName = name,
                sport = sport,
                leagueId = event.text("idLeague") ?: league,
                leagueName = league,
                homeTeam = event.text("strHomeTeam").orEmpty(),
                awayTeam = event.text("strAwayTeam").orEmpty(),
                homeTeamId = event.text("idHomeTeam"),
                awayTeamId = event.text("idAwayTeam"),
                homeScore = event.text("intHomeScore")?.toIntOrNull(),
                awayScore = event.text("intAwayScore")?.toIntOrNull(),
                status = event.text("strStatus"),
                progress = event.text("strProgress") ?: event.text("strResult"),
            )
        }
    }.getOrDefault(emptyList())
}

object VolleyballWorldFallbackParser {
    fun parse(json: String): List<ExternalScheduleEvent> = runCatching {
        JsonParser.parseString(json).asJsonObject.array("matches").mapNotNull { element ->
            val match = element.asObject() ?: return@mapNotNull null
            val id = match.text("matchNo") ?: return@mapNotNull null
            val timestamp = match.text("matchDateUtc") ?: return@mapNotNull null
            val teams = match.volleyballWorldTeams()
            if (teams.first.isBlank() || teams.second.isBlank()) return@mapNotNull null
            val competition = listOf(
                match.text("competitionFullName") ?: match.text("competitionShortName") ?: "Volleyball Nations League",
                match.text("genderText") ?: match.text("gender"),
                match.text("roundName"),
                match.obj("pool")?.text("name"),
            ).filter { !it.isNullOrBlank() }.joinToString(" · ")
            ExternalScheduleEvent(
                id = "vw-$id",
                timestamp = timestamp.normalizedExternalTimestamp(),
                eventName = "${teams.first} vs ${teams.second}",
                sport = "Volleyball",
                leagueId = "volleyball-world-vnl",
                leagueName = competition,
                homeTeam = teams.first,
                awayTeam = teams.second,
                homeTeamId = match.text("teamANo"),
                awayTeamId = match.text("teamBNo"),
                homeScore = match.text("teamAScore")?.toIntOrNull()?.takeIf { it >= 0 },
                awayScore = match.text("teamBScore")?.toIntOrNull()?.takeIf { it >= 0 },
                status = match.text("matchStatus"),
                progress = match.text("currentSetNo")?.let { "Set $it" },
            )
        }
    }.getOrDefault(emptyList())

    private fun JsonObject.volleyballWorldTeams(): Pair<String, String> {
        val encoded = text("matchCenterUrl")
            ?.substringAfter("match=", "")
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
        val decoded = runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
        val parts = decoded.split(Regex("\\s*-vs-\\s*|\\s+vs\\s+", RegexOption.IGNORE_CASE), limit = 2)
        return parts.getOrNull(0).orEmpty().trim() to parts.getOrNull(1).orEmpty().trim()
    }
}

private fun JsonObject.fallbackTimestamp(): String? {
    val date = text("dateEvent") ?: text("dateEventLocal") ?: return null
    val rawTime = text("strTime") ?: text("strTimeLocal") ?: "00:00:00"
    val time = rawTime.takeIf { it.contains(':') } ?: "00:00:00"
    return "${date}T${time.removeSuffix("Z")}Z"
}

private fun String.normalizedExternalTimestamp(): String {
    val value = trim()
    if (value.endsWith("Z", ignoreCase = true)) return value
    if (Regex("[+-]\\d{2}:?\\d{2}$").containsMatchIn(value)) return value
    return "${value}Z"
}

private fun JsonElement.asObject(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.obj(name: String): JsonObject? = get(name)?.asObject()
private fun JsonObject.array(name: String): List<JsonElement> =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.text(name: String): String? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
}.getOrNull()
private fun JsonObject.bool(name: String): Boolean? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asBoolean
}.getOrNull()
private fun JsonObject.number(name: String): Double? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asDouble
}.getOrNull()
private fun JsonObject.int(name: String): Int? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asInt
}.getOrNull()
