package com.soliano.betvalueanalyzer.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class F1DriverModel(
    val name: String,
    val driverId: String,
    val team: String,
    val score: Double,
    val recentForm: Double,
    val seasonForm: Double,
    val qualifying: Double,
    val reliability: Double,
    val circuitFit: Double,
    val pitExecution: Double,
    val trend: Double,
)

data class F1AnalysisSnapshot(
    val target: DeepAnalysisTarget,
    val raceName: String,
    val circuitName: String,
    val circuitDescription: String,
    val models: List<F1DriverModel>,
    val sources: List<String>,
) {
    fun newsSubjects(): List<NewsSubject> = buildList {
        models.take(8).forEach { model ->
            add(NewsSubject("${model.name} ${model.team} $raceName interview performance", model.name, model.name, "F1_DRIVER"))
            add(NewsSubject("${model.name} compte officiel Instagram Facebook X Twitter F1", model.name, model.name, "F1_DRIVER_SOCIAL"))
        }
        models.map { it.team }.distinct().take(6).forEach { team ->
            add(NewsSubject("$team F1 directeur équipe interview amélioration fiabilité", team, team, "F1_TEAM"))
            add(NewsSubject("$team compte officiel Instagram Facebook X Twitter F1", team, team, "F1_TEAM_SOCIAL"))
        }
        add(NewsSubject("$raceName $circuitName météo pneus stratégie qualifications", null, circuitName, "F1_CIRCUIT"))
        add(NewsSubject("$raceName conférence de presse pilotes équipes F1", null, raceName, "F1_PRESS"))
    }.distinctBy { it.query }

    fun toPrediction(report: NewsContextReport, updateTime: Long): PredictionEntity {
        val adjusted = models.map { model ->
            val context = report.signals.filter { signal ->
                normalized(signal.teamName) == normalized(model.name) || normalized(signal.teamName) == normalized(model.team)
            }.sumOf { it.impact }.coerceIn(-0.05, 0.04)
            model to model.score + context * 1.4
        }
        val maxScore = adjusted.maxOf { it.second }
        val weights = adjusted.map { (model, score) -> model to exp((score - maxScore) * 4.2) }
        val total = weights.sumOf { it.second }.coerceAtLeast(0.0001)
        val probabilities = weights.map { (model, weight) -> model to weight / total }.sortedByDescending { it.second }
        val favorite = probabilities.first()
        val sourcesAll = (sources + report.checkedSources).filter { it.isNotBlank() }.distinct()
        val contextLines = report.signals.map { signal ->
            "${signal.teamName} · ${signal.category} : ${signal.title} (${signal.publishers.joinToString(", ")})"
        }
        val scenarios = probabilities.take(10).flatMap { (driver, probability) ->
            val podium = (1.0 - (1.0 - probability).pow(3.0)).coerceIn(probability, 0.82)
            listOf(
                "Pilote · Victoire|${driver.name} gagne le Grand Prix|$probability",
                "Pilote · Podium|${driver.name} termine sur le podium|$podium",
            )
        }.joinToString("\n")
        val top = probabilities.take(5)
        val stats = buildList {
            add("Circuit : $circuitName · $circuitDescription")
            top.forEach { (driver, probability) ->
                add(
                    "${driver.name} (${driver.team}) : victoire ${(probability * 100).roundToInt()} % · " +
                        "forme ${percent(driver.recentForm)}, qualifs ${percent(driver.qualifying)}, " +
                        "fiabilité ${percent(driver.reliability)}, affinité circuit ${percent(driver.circuitFit)}, pits ${percent(driver.pitExecution)}"
                )
            }
            val rising = models.filter { it.trend >= 0.12 }.sortedByDescending { it.trend }.take(3)
            if (rising.isNotEmpty()) add("Pilotes en progression saisonnière : ${rising.joinToString { it.name }}")
        }.joinToString("\n")
        val confidence = (54 + sources.size * 5 + if (models.size >= 18) 8 else 0).coerceIn(55, 88)
        return PredictionEntity(
            id = "${target.id}:deep:f1:winner",
            eventId = target.id,
            sportKey = "racing/f1",
            sportTitle = "Formule 1",
            competitionName = raceName,
            commenceTime = target.commenceTime,
            homeTeam = raceName,
            awayTeam = "Grille F1",
            market = "Vainqueur probable du Grand Prix",
            selection = favorite.first.name,
            betclicOdds = 1.0 / favorite.second.coerceAtLeast(0.01),
            impliedProbability = favorite.second,
            consensusProbability = favorite.second,
            valueEdge = 0.0,
            expectedValue = 0.0,
            confidenceScore = confidence,
            riskLevel = if (favorite.second >= 0.30) "Modéré" else "Élevé",
            category = if (confidence >= 75) "Safe" else "Mitigé",
            bookmakerCount = 0,
            sourceName = "Modèle F1 maison · ${sourcesAll.size} sources",
            sourceLastUpdate = updateTime,
            explanation = "Le modèle F1 combine championnat, forme course par course, qualifications, fiabilité, niveau du constructeur, circuits similaires et exécution des arrêts. ${favorite.first.name} ressort à ${percent(favorite.second)} de victoire parmi toute la grille.",
            positiveArguments = "Affinité avec le profil du circuit : ${percent(favorite.first.circuitFit)}\nFiabilité récente : ${percent(favorite.first.reliability)}",
            negativeArguments = "Une voiture de sécurité, la météo, un incident ou une stratégie atypique peuvent bouleverser une course.",
            expectedScore = "",
            statSummary = stats,
            scenarios = scenarios,
            homeLineupStatus = "",
            homeLineup = "",
            awayLineupStatus = "",
            awayLineup = "",
            playerScenarios = "",
            sourceDetails = sourcesAll.joinToString("\n"),
            contextInsights = contextLines.joinToString("\n"),
            sourceAgreement = (65 + sources.size * 4).coerceAtMost(90),
        )
    }

    private fun percent(value: Double): String = "${(value.coerceIn(0.0, 1.0) * 100).roundToInt()} %"
}

class F1AnalysisProvider(private val api: PublicSportsApiService) {
    suspend fun load(target: DeepAnalysisTarget, onProgress: (Double, String) -> Unit): F1AnalysisSnapshot = coroutineScope {
        val year = Instant.ofEpochMilli(target.commenceTime).atZone(ZoneOffset.UTC).year
        onProgress(0.15, "Championnat pilotes et constructeurs")
        val standingsDeferred = async { getJson("https://api.jolpi.ca/ergast/f1/$year/driverstandings.json") }
        val constructorsDeferred = async { getJson("https://api.jolpi.ca/ergast/f1/$year/constructorstandings.json") }
        val scheduleDeferred = async { getJson("https://api.jolpi.ca/ergast/f1/$year.json") }
        val resultsDeferred = async { loadJolpicaRaces(year, "results") }
        val qualifyingDeferred = async { loadJolpicaRaces(year, "qualifying") }
        val openF1Deferred = async { loadOpenF1PitExecution(year) }
        val standingsRoot = standingsDeferred.await()
        val constructorRoot = constructorsDeferred.await()
        val scheduleRoot = scheduleDeferred.await()
        val resultRaces = resultsDeferred.await()
        val qualifyingRaces = qualifyingDeferred.await()
        val pitScores = openF1Deferred.await()
        onProgress(0.48, "Courses, qualifications, fiabilité et pit-stops comparés")

        val schedule = scheduleRoot.jolpicaRaces()
        val targetRace = schedule.minByOrNull { race ->
            val date = race.obj("date")?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            val epoch = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE / 2
            val namePenalty = if (normalized(race.obj("raceName").orEmpty()).contains(normalized(target.homeTeam))) 0L else 14L * DAY_MS
            kotlin.math.abs(epoch - target.commenceTime) + namePenalty
        } ?: throw OddsSyncException("Calendrier F1 officiel indisponible.")
        val raceName = targetRace.obj("raceName") ?: target.homeTeam
        val circuit = targetRace.objectValue("Circuit")
        val circuitId = circuit?.obj("circuitId").orEmpty()
        val circuitName = circuit?.obj("circuitName") ?: raceName
        val circuitProfile = CIRCUITS[circuitId] ?: CircuitProfile("circuit permanent équilibré", 0.55, 0.55, 0.55, 0.55)
        val targetRound = targetRace.obj("round")?.toIntOrNull()

        val driverStandings = standingsRoot.jolpicaStandings("DriverStandings")
        val constructorStandings = constructorRoot.jolpicaStandings("ConstructorStandings")
        val constructorMaxPoints = constructorStandings.maxOfOrNull { it.obj("points")?.toDoubleOrNull() ?: 0.0 }?.coerceAtLeast(1.0) ?: 1.0
        val constructors = constructorStandings.associate { item ->
            val constructor = item.objectValue("Constructor")
            normalized(constructor?.obj("name").orEmpty()) to ((item.obj("points")?.toDoubleOrNull() ?: 0.0) / constructorMaxPoints)
        }
        val allResultRows = resultRaces.flatMap { race ->
            val profile = CIRCUITS[race.objectValue("Circuit")?.obj("circuitId").orEmpty()]
                ?: CircuitProfile("équilibré", 0.55, 0.55, 0.55, 0.55)
            race.array("Results").mapNotNull { row -> row.asObjectOrNull()?.let { Triple(race, it, profile) } }
        }
        val qualifyingByDriver = qualifyingRaces.flatMap { race ->
            race.array("QualifyingResults").mapNotNull { row ->
                val item = row.asObjectOrNull() ?: return@mapNotNull null
                val id = item.objectValue("Driver")?.obj("driverId") ?: return@mapNotNull null
                val round = race.obj("round")?.toIntOrNull()
                Triple(id, round, item.obj("position")?.toDoubleOrNull() ?: 22.0)
            }
        }.groupBy { it.first }

        val models = driverStandings.mapNotNull { standing ->
            val driver = standing.objectValue("Driver") ?: return@mapNotNull null
            val driverId = driver.obj("driverId") ?: return@mapNotNull null
            val name = listOfNotNull(driver.obj("givenName"), driver.obj("familyName")).joinToString(" ")
            val team = standing.array("Constructors").firstOrNull()?.asObjectOrNull()?.obj("name") ?: "Équipe inconnue"
            val rows = allResultRows.filter { (_, row, _) -> row.objectValue("Driver")?.obj("driverId") == driverId }
                .sortedByDescending { (race, _, _) -> race.obj("round")?.toIntOrNull() ?: 0 }
            val finishes = rows.map { (_, row, _) ->
                val position = row.obj("position")?.toDoubleOrNull() ?: 22.0
                val status = row.obj("status").orEmpty()
                if (status.contains("Finished", true) || status.startsWith("+")) (23.0 - position) / 22.0 else 0.0
            }
            val recent = finishes.take(4).averageOr(0.35)
            val season = finishes.averageOr(0.35)
            val trend = ((recent - finishes.drop(4).averageOr(season)) * (finishes.size / 10.0).coerceIn(0.25, 1.0)).coerceIn(-1.0, 1.0)
            val reliability = rows.take(8).map { (_, row, _) ->
                val status = row.obj("status").orEmpty()
                if (status.contains("Finished", true) || status.startsWith("+")) 1.0 else 0.0
            }.averageOr(0.65)
            val quals = qualifyingByDriver[driverId].orEmpty().sortedByDescending { it.second ?: 0 }
            val seasonQual = quals.take(6).map { (23.0 - it.third) / 22.0 }.averageOr(0.4)
            val directQual = quals.firstOrNull { it.second == targetRound }?.let { (23.0 - it.third) / 22.0 }
            val qualifying = directQual?.let { it * 0.72 + seasonQual * 0.28 } ?: seasonQual
            val circuitRows = rows.map { (_, row, profile) ->
                val similarity = circuitProfile.similarity(profile)
                val pos = row.obj("position")?.toDoubleOrNull() ?: 22.0
                similarity to ((23.0 - pos) / 22.0)
            }
            val similarityWeight = circuitRows.sumOf { it.first }.coerceAtLeast(0.001)
            val circuitFit = circuitRows.sumOf { it.first * it.second } / similarityWeight
            val standingPosition = standing.obj("position")?.toDoubleOrNull() ?: 22.0
            val championship = (23.0 - standingPosition) / 22.0
            val constructor = constructors[normalized(team)] ?: 0.35
            val pit = pitScores[normalized(team)] ?: 0.5
            val score = championship * 0.18 + recent * 0.25 + qualifying * 0.18 + reliability * 0.12 +
                constructor * 0.10 + circuitFit * 0.12 + pit * 0.05
            F1DriverModel(name, driverId, team, score, recent, season, qualifying, reliability, circuitFit, pit, trend)
        }.sortedByDescending { it.score }
        if (models.size < 10) throw OddsSyncException("Données pilotes F1 insuffisantes.")
        val sources = buildList {
            if (standingsRoot != null || resultRaces.isNotEmpty()) add("Jolpica F1 · Ergast")
            if (pitScores.isNotEmpty()) add("OpenF1 · télémétrie et pit-stops")
            add("ESPN · calendrier F1")
        }
        F1AnalysisSnapshot(target, raceName, circuitName, circuitProfile.description, models, sources)
    }

    private suspend fun loadJolpicaRaces(year: Int, type: String): List<JsonObject> {
        val pages = listOf(0, 100).mapNotNull { offset ->
            getJson("https://api.jolpi.ca/ergast/f1/$year/$type.json?limit=100&offset=$offset")
        }
        return pages.flatMap { it.jolpicaRaces() }
    }

    private suspend fun loadOpenF1PitExecution(year: Int): Map<String, Double> {
        val sessions = getElement("https://api.openf1.org/v1/sessions?year=$year&session_name=Race")
            ?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.asObjectOrNull() }.orEmpty()
        val now = System.currentTimeMillis()
        val latest = sessions.filter { session ->
            session.obj("date_end")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }?.let { it < now } == true
        }.maxByOrNull { it.obj("date_end").orEmpty() } ?: return emptyMap()
        val sessionKey = latest.obj("session_key") ?: return emptyMap()
        val drivers = getElement("https://api.openf1.org/v1/drivers?session_key=$sessionKey")
            ?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.asObjectOrNull() }.orEmpty()
        val teamByNumber = drivers.associate { it.obj("driver_number").orEmpty() to it.obj("team_name").orEmpty() }
        val pits = getElement("https://api.openf1.org/v1/pit?session_key=$sessionKey")
            ?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.asObjectOrNull() }.orEmpty()
        val averages = pits.mapNotNull { pit ->
            val team = teamByNumber[pit.obj("driver_number").orEmpty()]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val stop = pit.obj("stop_duration")?.toDoubleOrNull()?.takeIf { it in 1.5..8.0 } ?: return@mapNotNull null
            normalized(team) to stop
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.average() }
        if (averages.isEmpty()) return emptyMap()
        val best = averages.minOf { it.value }
        val worst = averages.maxOf { it.value }
        return averages.mapValues { (_, value) -> if (worst == best) 0.5 else (1.0 - (value - best) / (worst - best)).coerceIn(0.0, 1.0) }
    }

    private suspend fun getJson(url: String): JsonObject? = getElement(url)?.asObjectOrNull()

    private suspend fun getElement(url: String): JsonElement? {
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return response.body()?.string()?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
    }

    private data class CircuitProfile(
        val description: String,
        val corners: Double,
        val straights: Double,
        val braking: Double,
        val traction: Double,
    ) {
        fun similarity(other: CircuitProfile): Double = (1.0 - (
            kotlin.math.abs(corners - other.corners) + kotlin.math.abs(straights - other.straights) +
                kotlin.math.abs(braking - other.braking) + kotlin.math.abs(traction - other.traction)
            ) / 4.0).coerceIn(0.15, 1.0)
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        val CIRCUITS = mapOf(
            "albert_park" to CircuitProfile("semi-urbain rapide, freinages appuyés et relances", 0.62, 0.70, 0.72, 0.65),
            "bahrain" to CircuitProfile("permanent, forte traction, longues lignes droites et gros freinages", 0.50, 0.78, 0.86, 0.84),
            "jeddah" to CircuitProfile("urbain très rapide, longues courbes et faible marge d'erreur", 0.88, 0.78, 0.48, 0.52),
            "suzuka" to CircuitProfile("permanent très technique, enchaînements rapides et appui élevé", 0.95, 0.55, 0.48, 0.60),
            "shanghai" to CircuitProfile("mixte, long virage d'appui, très longue ligne droite et freinage", 0.68, 0.86, 0.78, 0.66),
            "miami" to CircuitProfile("semi-urbain, longues lignes droites, section lente et traction", 0.58, 0.82, 0.80, 0.78),
            "imola" to CircuitProfile("permanent étroit, technique, vibreurs et changements de direction", 0.80, 0.56, 0.70, 0.72),
            "monaco" to CircuitProfile("urbain très lent, virages serrés, traction et qualification cruciales", 0.96, 0.18, 0.72, 0.94),
            "catalunya" to CircuitProfile("permanent équilibré, longues courbes et fort appui aérodynamique", 0.88, 0.58, 0.56, 0.62),
            "villeneuve" to CircuitProfile("semi-urbain stop-and-go, longues lignes droites et freinages violents", 0.42, 0.91, 0.94, 0.80),
            "red_bull_ring" to CircuitProfile("permanent court, longues lignes droites, fortes accélérations, peu de virages et gros freinages", 0.38, 0.90, 0.88, 0.82),
            "silverstone" to CircuitProfile("permanent très rapide, grandes courbes et appui aérodynamique", 0.94, 0.66, 0.38, 0.48),
            "spa" to CircuitProfile("permanent très rapide, longues lignes droites, dénivelé et courbes rapides", 0.84, 0.93, 0.58, 0.54),
            "hungaroring" to CircuitProfile("permanent sinueux, peu de lignes droites, appui et traction", 0.94, 0.28, 0.54, 0.84),
            "zandvoort" to CircuitProfile("permanent vallonné, virages relevés et rythme technique", 0.91, 0.38, 0.48, 0.66),
            "monza" to CircuitProfile("permanent ultra-rapide, très longues lignes droites et chicanes de freinage", 0.24, 1.0, 0.96, 0.68),
            "baku" to CircuitProfile("urbain, immense ligne droite, virages à 90 degrés et gros freinages", 0.58, 1.0, 0.96, 0.82),
            "marina_bay" to CircuitProfile("urbain lent et bosselé, nombreux virages, chaleur et traction", 0.98, 0.30, 0.78, 0.92),
            "americas" to CircuitProfile("permanent complet, esses rapides, épingles et longue ligne droite", 0.84, 0.72, 0.70, 0.70),
            "rodriguez" to CircuitProfile("permanent en altitude, faible appui réel, lignes droites et section stade", 0.62, 0.86, 0.76, 0.72),
            "interlagos" to CircuitProfile("permanent court et vallonné, mixte rapide-lent et météo changeante", 0.76, 0.68, 0.64, 0.74),
            "las_vegas" to CircuitProfile("urbain très rapide, longues lignes droites, froid et gros freinages", 0.38, 1.0, 0.94, 0.64),
            "losail" to CircuitProfile("permanent fluide, très longues courbes rapides et pneus sollicités", 0.95, 0.58, 0.36, 0.48),
            "yas_marina" to CircuitProfile("permanent stop-and-go, longues lignes droites et traction lente", 0.60, 0.84, 0.86, 0.82),
        )
    }
}

private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.obj(name: String): String? = runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
private fun JsonObject.objectValue(name: String): JsonObject? = get(name)?.asObjectOrNull()
private fun JsonObject.array(name: String): List<JsonElement> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
private fun JsonObject.path(vararg names: String): JsonObject? = names.fold(this as JsonObject?) { current, name -> current?.objectValue(name) }
private fun JsonObject?.jolpicaRaces(): List<JsonObject> = this?.path("MRData", "RaceTable")?.array("Races").orEmpty().mapNotNull { it.asObjectOrNull() }
private fun JsonObject?.jolpicaStandings(name: String): List<JsonObject> =
    this?.path("MRData", "StandingsTable")?.array("StandingsLists")?.firstOrNull()?.asObjectOrNull()
        ?.array(name).orEmpty().mapNotNull { it.asObjectOrNull() }
private fun List<Double>.averageOr(default: Double): Double = if (isEmpty()) default else average()
private fun normalized(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), "")
