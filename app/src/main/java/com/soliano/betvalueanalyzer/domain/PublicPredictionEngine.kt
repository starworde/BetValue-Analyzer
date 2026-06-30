package com.soliano.betvalueanalyzer.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

private val contextDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault())

data class PublicEvent(
    val eventId: String,
    val sportKey: String,
    val sportTitle: String,
    val competitionName: String,
    val dataSourceName: String = "ESPN public",
    val commenceTime: Long,
    val homeTeam: String,
    val awayTeam: String,
    val homeProfile: TeamStatProfile? = null,
    val awayProfile: TeamStatProfile? = null,
    val homeLineup: TeamLineup? = null,
    val awayLineup: TeamLineup? = null,
    val informationSources: List<String> = emptyList(),
    val contextSignals: List<MatchContextSignal> = emptyList(),
    val standingSignals: List<CompetitionStandingSignal> = emptyList(),
    val homeForm: String? = null,
    val awayForm: String? = null,
    val homeRecord: String? = null,
    val awayRecord: String? = null,
    val provider: String? = null,
    val homeOdds: Double? = null,
    val awayOdds: Double? = null,
    val drawOdds: Double? = null,
    val totalLine: Double? = null,
    val overOdds: Double? = null,
    val underOdds: Double? = null,
    val homeSpreadLine: Double? = null,
    val awaySpreadLine: Double? = null,
    val homeSpreadOdds: Double? = null,
    val awaySpreadOdds: Double? = null,
)

data class PublicPrediction(
    val id: String,
    val market: String,
    val selection: String,
    val referenceOdds: Double,
    val impliedProbability: Double,
    val estimatedProbability: Double,
    val valueEdge: Double,
    val expectedValue: Double,
    val confidenceScore: Int,
    val riskLevel: String,
    val category: String,
    val sourceName: String,
    val explanation: String,
    val positiveArguments: List<String>,
    val negativeArguments: List<String>,
    val expectedScore: String? = null,
    val statSummary: List<String> = emptyList(),
    val scenarios: List<ProbabilityScenario> = emptyList(),
    val homeLineup: TeamLineup? = null,
    val awayLineup: TeamLineup? = null,
    val playerScenarios: List<ProbabilityScenario> = emptyList(),
    val sourceDetails: List<String> = emptyList(),
    val contextInsights: List<String> = emptyList(),
    val sourceAgreement: Int = 0,
)

data class ProbabilityScenario(
    val label: String,
    val probability: Double,
    val type: String,
)

object PublicPredictionEngine {
    fun analyze(event: PublicEvent): List<PublicPrediction> {
        if (!RemovedSports.isAllowedSportKey(event.sportKey)) return emptyList()
        val predictions = mutableListOf<PublicPrediction>()
        winnerPrediction(event)?.let(predictions::add)
        listOfNotNull(totalPrediction(event), spreadPrediction(event))
            .maxByOrNull { it.confidenceScore }
            ?.let(predictions::add)
        val playerScenarios = playerScenarios(event)
        val sources = event.informationSources.distinct()
        val agreement = listOfNotNull(event.homeProfile?.sourceAgreement, event.awayProfile?.sourceAgreement)
            .takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
        val context = event.contextSignals.map { signal ->
            val publication = signal.publishedAt.takeIf { it > 0 }?.let {
                " · publié le ${contextDateFormat.format(Instant.ofEpochMilli(it))}"
            }.orEmpty()
            "${signal.teamName} · ${signal.category}$publication : ${signal.title} (${signal.publishers.joinToString(", ")})"
        } + event.standingSignals.map { "${it.teamName} · Enjeu classement : ${it.description}" }
        val sportIntelligence = SportIntelligenceCatalog.profile(event.sportKey)
        return predictions.map {
            it.copy(
                homeLineup = event.homeLineup,
                awayLineup = event.awayLineup,
                playerScenarios = playerScenarios
                    .distinctBy { scenario -> scenario.type + scenario.label }
                    .sortedByDescending { scenario -> scenario.probability },
                sourceName = if (sources.size >= 2) "Consensus maison · ${sources.size} sources" else it.sourceName,
                sourceDetails = sources,
                contextInsights = context,
                sourceAgreement = agreement,
                statSummary = (it.statSummary + sportIntelligence.summaryLines)
                    .distinct(),
                scenarios = (it.scenarios + sportIntelligence.probabilityScenarios)
                    .distinctBy { scenario -> scenario.type + scenario.label }
                    .sortedByDescending { scenario -> scenario.probability },
            )
        }
    }

    private fun playerScenarios(event: PublicEvent): List<ProbabilityScenario> {
        val candidates = listOfNotNull(
            event.homeProfile?.let { it to event.homeLineup },
            event.awayProfile?.let { it to event.awayLineup },
        ).flatMap { (profile, lineup) ->
            val lineupNames = lineup?.players?.map { normalizePlayer(it.name) }?.toSet().orEmpty()
            profile.playerProfiles
                .filter { it.appearances >= 1 && it.availabilityNote == null }
                .map { player ->
                    val inDisplayedLineup = normalizePlayer(player.name) in lineupNames
                    PlayerCandidate(
                        stats = player,
                        likelyStarter = if (lineupNames.isNotEmpty()) inDisplayedLineup else player.starts >= 2,
                    )
                }
                .sortedWith(
                    compareByDescending<PlayerCandidate> { it.likelyStarter }
                        .thenByDescending { it.stats.starts }
                        .thenByDescending { it.stats.appearances }
                )
                .take(18)
        }
        val scenarios = when {
            event.sportKey.startsWith("soccer") -> candidates.flatMap(::soccerPlayerScenarios)
            event.sportKey.startsWith("basketball") -> candidates.flatMap { basketballPlayerScenarios(it.stats) }
            event.sportKey.startsWith("baseball") -> candidates.flatMap { baseballPlayerScenarios(it.stats) }
            event.sportKey.startsWith("rugby") -> candidates.flatMap { scoringPlayerScenarios(it.stats, "essai", "Joueur · Essai") }
            event.sportKey.startsWith("handball") -> candidates.flatMap { handballPlayerScenarios(it.stats) }
            event.sportKey.startsWith("hockey") ->
                candidates.flatMap { scoringPlayerScenarios(it.stats, "but", "Joueur · But") }
            event.sportKey.startsWith("volleyball") -> candidates.flatMap { pointsPlayerScenarios(it.stats, "Joueur · Points", "points") }
            event.sportKey.startsWith("football") ->
                candidates.flatMap { pointsPlayerScenarios(it.stats, "Joueur · Points", "points") }
            else -> emptyList()
        }
        val minimumProbability = if (event.sportKey.startsWith("soccer")) 0.025 else 0.08
        val ranked = scenarios
            .filter { it.probability in minimumProbability..0.99 }
            .sortedByDescending { it.probability }
            .distinctBy { it.label }
        val diversified = when {
            event.sportKey.startsWith("soccer") -> listOf(
                "Joueur · But" to 4,
                "Joueur · Passe" to 3,
                "Joueur · But ou passe" to 2,
                "Joueur · But + passe" to 2,
            ).flatMap { (type, limit) -> ranked.filter { it.type == type }.take(limit) }
            event.sportKey.startsWith("basketball") -> listOf(
                "Joueur · Points" to 5,
                "Joueur · Rebonds" to 3,
                "Joueur · Passes" to 3,
            ).flatMap { (type, limit) -> ranked.filter { it.type == type }.take(limit) }
            event.sportKey.startsWith("baseball") -> listOf(
                "Joueur · Coups sûrs" to 5,
                "Joueur · Home run" to 3,
            ).flatMap { (type, limit) -> ranked.filter { it.type == type }.take(limit) }
            event.sportKey.startsWith("rugby") -> listOf(
                "Joueur · Essai" to 5,
                "Joueur · Passe" to 3,
            ).flatMap { (type, limit) -> ranked.filter { it.type == type }.take(limit) }
            event.sportKey.startsWith("handball") -> listOf(
                "Joueur · Buts handball" to 6,
            ).flatMap { (type, limit) -> ranked.filter { it.type == type }.take(limit) }
            event.sportKey.startsWith("hockey") -> listOf(
                "Joueur · But" to 5,
                "Joueur · Passe" to 3,
            ).flatMap { (type, limit) -> ranked.filter { it.type == type }.take(limit) }
            else -> ranked.take(8)
        }
        return diversified.sortedByDescending { it.probability }
    }

    private fun soccerPlayerScenarios(candidate: PlayerCandidate): List<ProbabilityScenario> {
        val player = candidate.stats
        val games = player.appearances.coerceAtLeast(1).toDouble()
        val recentGoalsPerGame = player.goals / games
        val recentAssistsPerGame = player.assists / games
        val goalsPerGame = if (player.sourceCount >= 2) {
            recentGoalsPerGame * 0.72 + (player.secondaryGoals / 6.0) * 0.28
        } else recentGoalsPerGame
        val assistsPerGame = if (player.sourceCount >= 2) {
            recentAssistsPerGame * 0.72 + (player.secondaryAssists / 6.0) * 0.28
        } else recentAssistsPerGame
        val shotsOnTargetPerGame = player.shotsOnTarget / games
        val trendMultiplier = (1.0 + player.formTrend * 0.18).coerceIn(0.85, 1.15)
        val goalLambda = ((goalsPerGame * 0.72 + shotsOnTargetPerGame * 0.28 * 0.30) * trendMultiplier).coerceIn(0.0, 1.6)
        val assistLambda = (assistsPerGame * trendMultiplier).coerceIn(0.0, 1.4)
        val appearanceFactor = if (candidate.likelyStarter) 1.0 else {
            (player.appearances / 3.0).coerceIn(0.30, 0.80)
        }
        val workload = player.workloadMultiplier()
        val role = if (candidate.likelyStarter) "" else " · remplaçant possible"
        val evidence = if (player.sourceCount >= 2) " · confirmé par ${player.sourceCount} sources" else ""
        val load = player.workloadNote()
        val trend = when {
            player.formTrend >= 0.18 -> " · forme en hausse"
            player.formTrend <= -0.18 -> " · forme en baisse"
            else -> ""
        }
        val goalIfPlaying = poissonAtLeast(1, goalLambda)
        val assistIfPlaying = poissonAtLeast(1, assistLambda)
        return buildList {
            if (player.goals > 0 || player.secondaryGoals > 0 || shotsOnTargetPerGame >= 0.45) {
                add(ProbabilityScenario(
                    "${player.name} marque 1+ but$role$evidence$trend$load",
                    goalIfPlaying * appearanceFactor * workload,
                    "Joueur · But",
                ))
                if (goalLambda >= 0.70) {
                    add(ProbabilityScenario(
                        "${player.name} marque 2+ buts$role$load",
                        poissonAtLeast(2, goalLambda) * appearanceFactor * workload,
                        "Joueur · But",
                    ))
                }
            }
            if (player.assists > 0 || player.secondaryAssists > 0) {
                add(ProbabilityScenario(
                    "${player.name} fait 1+ passe décisive$role$evidence$trend$load",
                    assistIfPlaying * appearanceFactor * workload,
                    "Joueur · Passe",
                ))
                if (assistLambda >= 0.70) {
                    add(ProbabilityScenario(
                        "${player.name} fait 2+ passes décisives$role$load",
                        poissonAtLeast(2, assistLambda) * appearanceFactor * workload,
                        "Joueur · Passe",
                    ))
                }
            }
            if ((player.goals > 0 || player.secondaryGoals > 0 || shotsOnTargetPerGame >= 0.45) &&
                (player.assists > 0 || player.secondaryAssists > 0)) {
                add(ProbabilityScenario(
                    "${player.name} marque ou fait une passe décisive$role$load",
                    (goalIfPlaying + assistIfPlaying - goalIfPlaying * assistIfPlaying) * appearanceFactor * workload,
                    "Joueur · But ou passe",
                ))
                add(ProbabilityScenario(
                    "${player.name} marque et fait une passe décisive$role$load",
                    goalIfPlaying * assistIfPlaying * appearanceFactor * workload,
                    "Joueur · But + passe",
                ))
            }
        }
    }

    private fun basketballPlayerScenarios(player: PlayerStatProfile): List<ProbabilityScenario> {
        val games = player.appearances.coerceAtLeast(1).toDouble()
        val points = player.points / games
        val assists = player.assists / games
        val rebounds = player.rebounds / games
        val workload = player.workloadMultiplier()
        val load = player.workloadNote()
        return buildList {
            if (points >= 8.0) {
                val threshold = (floor(points / 5.0) * 5.0).coerceAtLeast(5.0).toInt()
                add(ProbabilityScenario(
                    "${player.name} inscrit $threshold+ points · ${decimal(points)} / match$load",
                    normalOver(threshold - 0.5, points, maxOf(4.0, sqrt(points) * 1.35)) * workload,
                    "Joueur · Points",
                ))
            }
            if (rebounds >= 4.0) {
                val threshold = floor(rebounds).toInt().coerceAtLeast(4)
                add(ProbabilityScenario(
                    "${player.name} prend $threshold+ rebonds · ${decimal(rebounds)} / match$load",
                    normalOver(threshold - 0.5, rebounds, maxOf(2.0, sqrt(rebounds) * 1.2)) * workload,
                    "Joueur · Rebonds",
                ))
            }
            if (assists >= 2.0) {
                val threshold = floor(assists).toInt().coerceAtLeast(2)
                add(ProbabilityScenario(
                    "${player.name} délivre $threshold+ passes · ${decimal(assists)} / match$load",
                    poissonAtLeast(threshold, assists) * workload,
                    "Joueur · Passes",
                ))
            }
        }
    }

    private fun baseballPlayerScenarios(player: PlayerStatProfile): List<ProbabilityScenario> {
        val games = player.appearances.coerceAtLeast(1).toDouble()
        val hits = player.hits / games
        val homeRuns = player.homeRuns / games
        val workload = player.workloadMultiplier()
        val load = player.workloadNote()
        return buildList {
            if (hits >= 0.45) {
                add(ProbabilityScenario(
                    "${player.name} réussit 1+ coup sûr · ${decimal(hits)} / match$load",
                    poissonAtLeast(1, hits.coerceAtMost(3.0)) * workload,
                    "Joueur · Coups sûrs",
                ))
            }
            if (player.homeRuns > 0) {
                add(ProbabilityScenario(
                    "${player.name} frappe un home run · ${player.homeRuns.toInt()} / ${player.appearances} matchs$load",
                    poissonAtLeast(1, homeRuns.coerceAtMost(1.2)) * workload,
                    "Joueur · Home run",
                ))
            }
        }
    }

    private fun scoringPlayerScenarios(player: PlayerStatProfile, action: String, type: String): List<ProbabilityScenario> {
        val games = player.appearances.coerceAtLeast(1).toDouble()
        val scoringPerGame = (player.goals / games).coerceAtMost(3.0)
        val assistsPerGame = (player.assists / games).coerceAtMost(3.0)
        val workload = player.workloadMultiplier()
        val load = player.workloadNote()
        return buildList {
            if (scoringPerGame >= 0.25) {
                add(ProbabilityScenario(
                    "${player.name} marque 1+ $action · ${decimal(scoringPerGame)} / match$load",
                    poissonAtLeast(1, scoringPerGame) * workload,
                    type,
                ))
            }
            if (scoringPerGame >= 0.85) {
                add(ProbabilityScenario(
                    "${player.name} marque 2+ ${action}s · ${decimal(scoringPerGame)} / match$load",
                    poissonAtLeast(2, scoringPerGame) * workload,
                    type,
                ))
            }
            if (assistsPerGame >= 0.35) {
                add(ProbabilityScenario(
                    "${player.name} délivre 1+ passe décisive · ${decimal(assistsPerGame)} / match$load",
                    poissonAtLeast(1, assistsPerGame) * workload,
                    "Joueur · Passe",
                ))
            }
        }
    }

    private fun handballPlayerScenarios(player: PlayerStatProfile): List<ProbabilityScenario> {
        val games = player.appearances.coerceAtLeast(1).toDouble()
        val goalsPerGame = (player.goals / games).coerceAtMost(8.0)
        if (goalsPerGame < 1.2) return emptyList()
        val workload = player.workloadMultiplier()
        val load = player.workloadNote()
        val threshold = when {
            goalsPerGame >= 5.5 -> 4
            goalsPerGame >= 3.5 -> 3
            else -> 2
        }
        return buildList {
            add(ProbabilityScenario(
                "${player.name} marque ${threshold}+ buts · ${decimal(goalsPerGame)} / match$load",
                normalOver(threshold - 0.5, goalsPerGame, maxOf(1.6, sqrt(goalsPerGame) * 1.15)) * workload,
                "Joueur · Buts handball",
            ))
            if (goalsPerGame >= 4.8) {
                add(ProbabilityScenario(
                    "${player.name} marque ${threshold + 1}+ buts · gros volume récent$load",
                    normalOver(threshold + 0.5, goalsPerGame, maxOf(1.8, sqrt(goalsPerGame) * 1.2)) * workload,
                    "Joueur · Buts handball",
                ))
            }
        }
    }

    private fun pointsPlayerScenarios(player: PlayerStatProfile, type: String, unit: String): List<ProbabilityScenario> {
        val games = player.appearances.coerceAtLeast(1).toDouble()
        val pointsPerGame = player.points / games
        if (pointsPerGame < 2.0) return emptyList()
        val threshold = when {
            pointsPerGame >= 20.0 -> (floor(pointsPerGame / 5.0) * 5.0).toInt()
            pointsPerGame >= 8.0 -> floor(pointsPerGame).toInt()
            else -> 2
        }.coerceAtLeast(2)
        return listOf(
            ProbabilityScenario(
                "${player.name} inscrit $threshold+ $unit · ${decimal(pointsPerGame)} / match${player.workloadNote()}",
                normalOver(threshold - 0.5, pointsPerGame, maxOf(2.0, sqrt(pointsPerGame) * 1.4)) * player.workloadMultiplier(),
                type,
            )
        )
    }

    private fun PlayerStatProfile.workloadMultiplier(): Double = when {
        averageMinutes <= 0.0 -> 1.0
        heavyRecentLoadCount >= 3 -> 0.86
        heavyRecentLoadCount == 2 -> 0.91
        lastMatchMinutes >= averageMinutes + 30.0 -> 0.88
        lastMatchMinutes >= averageMinutes + 18.0 -> 0.93
        else -> 1.0
    }

    private fun PlayerStatProfile.workloadNote(): String = when {
        averageMinutes <= 0.0 -> ""
        heavyRecentLoadCount >= 2 -> " · charge au-dessus de sa moyenne saison"
        lastMatchMinutes >= averageMinutes + 30.0 -> " · dernier match très au-dessus de son volume habituel"
        lastMatchMinutes >= averageMinutes + 18.0 -> " · minutes récentes au-dessus de sa moyenne"
        else -> ""
    }

    fun americanToDecimal(value: String?): Double? {
        val american = value?.trim()?.replace("+", "")?.toDoubleOrNull() ?: return null
        if (american == 0.0) return null
        return if (american > 0) 1.0 + american / 100.0 else 1.0 + 100.0 / -american
    }

    private fun winnerPrediction(event: PublicEvent): PublicPrediction? {
        if (event.homeProfile != null && event.awayProfile != null) {
            return statisticalPrediction(event, event.homeProfile, event.awayProfile)
        }
        val candidates = buildList {
            validOdds(event.homeOdds)?.let { add(Candidate(event.homeTeam, it, TeamSide.Home)) }
            validOdds(event.awayOdds)?.let { add(Candidate(event.awayTeam, it, TeamSide.Away)) }
            validOdds(event.drawOdds)?.let { add(Candidate("Match nul", it, TeamSide.Draw)) }
        }
        if (candidates.size < 2) return formOnlyPrediction(event)

        val raw = candidates.associateWith { 1.0 / it.odds }
        val fairTotal = raw.values.sum()
        if (fairTotal <= 0.0) return null
        val fair = raw.mapValues { it.value / fairTotal }.toMutableMap()

        val homePerformance = performance(event.homeForm, event.homeRecord)
        val awayPerformance = performance(event.awayForm, event.awayRecord)
        val formDelta = if (homePerformance != null && awayPerformance != null) {
            (homePerformance - awayPerformance).coerceIn(-0.6, 0.6)
        } else 0.0
        if (formDelta != 0.0) {
            val adjustment = formDelta * if (candidates.size == 3) 0.08 else 0.10
            candidates.firstOrNull { it.side == TeamSide.Home }?.let { fair[it] = (fair.getValue(it) + adjustment).coerceAtLeast(0.03) }
            candidates.firstOrNull { it.side == TeamSide.Away }?.let { fair[it] = (fair.getValue(it) - adjustment).coerceAtLeast(0.03) }
            val adjustedTotal = fair.values.sum()
            fair.keys.forEach { fair[it] = fair.getValue(it) / adjustedTotal }
        }
        val venue = venueEdge(event)
        venue?.let { edge ->
            val homeCandidate = candidates.firstOrNull { it.side == TeamSide.Home }
            val awayCandidate = candidates.firstOrNull { it.side == TeamSide.Away }
            if (homeCandidate != null && awayCandidate != null) {
                fair[homeCandidate] = (fair.getValue(homeCandidate) + edge.marketShift).coerceAtLeast(0.03)
                fair[awayCandidate] = (fair.getValue(awayCandidate) - edge.marketShift).coerceAtLeast(0.03)
                val adjustedTotal = fair.values.sum()
                fair.keys.forEach { fair[it] = fair.getValue(it) / adjustedTotal }
            }
        }

        val selected = fair.maxByOrNull { it.value } ?: return null
        val candidate = selected.key
        val estimated = selected.value
        val implied = raw.getValue(candidate).coerceIn(0.01, 0.99)
        val sorted = fair.values.sortedDescending()
        val margin = (sorted.firstOrNull() ?: estimated) - (sorted.getOrNull(1) ?: 0.0)
        val confidence = (50 + margin * 48 + if (formDelta != 0.0) 4 else 0)
            .roundToInt().coerceIn(45, 92)
        val source = sourceName(event.provider)
        val formText = when {
            formDelta > 0.08 && candidate.side == TeamSide.Home -> "La forme récente favorise aussi ${event.homeTeam}."
            formDelta < -0.08 && candidate.side == TeamSide.Away -> "La forme récente favorise aussi ${event.awayTeam}."
            else -> "La forme disponible ne contredit pas fortement ce signal."
        }

        return PublicPrediction(
            id = "${event.eventId}:winner:${candidate.side.name.lowercase()}",
            market = if (candidates.size == 3) "Résultat du match (1N2)" else "Vainqueur du match",
            selection = candidate.name,
            referenceOdds = candidate.odds,
            impliedProbability = implied,
            estimatedProbability = estimated,
            valueEdge = estimated - implied,
            expectedValue = estimated * candidate.odds - 1.0,
            confidenceScore = confidence,
            riskLevel = riskLevel(estimated),
            category = category(confidence),
            sourceName = source,
            explanation = "Le marché public est corrigé de sa marge puis légèrement ajusté avec la forme et le bilan disponibles. " +
                "Le modèle estime ${candidate.name} à ${percent(estimated)} de probabilité.",
            positiveArguments = listOf(
                "${candidate.name} ressort comme l'issue la plus probable du marché public.",
                formText,
            ) + listOfNotNull(venue?.positiveArgument(event)),
            negativeArguments = listOf(
                "Une estimation statistique ne garantit jamais le résultat.",
                "Blessures, compositions et changements de dernière minute peuvent ne pas être intégrés.",
            ),
        )
    }

    private fun statisticalPrediction(
        event: PublicEvent,
        home: TeamStatProfile,
        away: TeamStatProfile,
    ): PublicPrediction {
        return when {
            event.sportKey.startsWith("soccer") -> soccerStatisticalPrediction(event, home, away)
            event.sportKey.startsWith("handball") -> handballStatisticalPrediction(event, home, away)
            event.sportKey.startsWith("volleyball") -> volleyballStatisticalPrediction(event, home, away)
            else -> twoWayStatisticalPrediction(event, home, away)
        }
    }

    private fun handballStatisticalPrediction(
        event: PublicEvent,
        home: TeamStatProfile,
        away: TeamStatProfile,
    ): PublicPrediction {
        val copy = sportStatCopy(event)
        val venue = venueEdge(event)
        val homeDiff = home.averageScored - home.averageConceded
        val awayDiff = away.averageScored - away.averageConceded
        val homeSituation = (contextImpact(event, event.homeTeam) + availabilityImpact(home) + momentumImpact(home) + standingImpact(event, event.homeTeam))
            .coerceIn(-0.08, 0.06)
        val awaySituation = (contextImpact(event, event.awayTeam) + availabilityImpact(away) + momentumImpact(away) + standingImpact(event, event.awayTeam))
            .coerceIn(-0.08, 0.06)
        val strength = (home.winRate - away.winRate) * 2.0 + (homeDiff - awayDiff) / copy.scoreScale + (venue?.strengthShift ?: 0.0) +
            (homeSituation - awaySituation) * 1.6
        val homeWin = logistic(strength).coerceIn(0.18, 0.82)
        val awayWin = 1.0 - homeWin
        val selectedHome = homeWin >= awayWin
        val probability = maxOf(homeWin, awayWin)
        val selection = if (selectedHome) event.homeTeam else event.awayTeam
        val homeGoals = (((home.averageScored + away.averageConceded) / 2.0) * (1.0 + homeSituation + (venue?.homeScoringBoost ?: 0.0))).coerceAtLeast(12.0)
        val awayGoals = (((away.averageScored + home.averageConceded) / 2.0) * (1.0 + awaySituation - (venue?.awayScoringDrag ?: 0.0))).coerceAtLeast(12.0)
        val totalMean = homeGoals + awayGoals
        val totalSd = copy.fixedTotalSd ?: sqrt(totalMean.coerceAtLeast(1.0)) * copy.sdMultiplier
        val totalLine = floor(totalMean / 2.0) * 2.0 + 0.5
        val overProbability = normalOver(totalLine, totalMean, totalSd)
        val projectedMargin = kotlin.math.abs(homeGoals - awayGoals)
        val selectedMarginProbability = normalOver(copy.marginLine, projectedMargin, (totalSd * 0.45).coerceAtLeast(2.4))
        val closeGameProbability = (1.0 - normalOver(copy.closeLine, projectedMargin, (totalSd * 0.55).coerceAtLeast(2.8))).coerceIn(0.01, 0.99)
        val sample = minOf(home.games, away.games)
        val agreement = (home.sourceAgreement + away.sourceAgreement) / 2.0
        val confidence = (36 + agreement * 0.30 + kotlin.math.abs(homeWin - awayWin) * 40 + sample.coerceAtMost(8) * 1.2)
            .roundToInt().coerceIn(43, 90)
        val teamSd = (totalSd * 0.58).coerceAtLeast(2.2)
        val homeLine = teamLine(homeGoals, "buts")
        val awayLine = teamLine(awayGoals, "buts")
        val expectedScore = "${homeGoals.roundToInt()} — ${awayGoals.roundToInt()}"
        val scenarios = buildList {
            val drawOrNeutralProbability = (0.07 + closeGameProbability * 0.10).coerceIn(0.07, 0.18)
            add(ProbabilityScenario("Vainqueur temps réglementaire : ${event.homeTeam}", homeWin, "Vainqueur temps réglementaire"))
            add(ProbabilityScenario("Vainqueur temps réglementaire : ${event.awayTeam}", awayWin, "Vainqueur temps réglementaire"))
            if (closeGameProbability >= 0.48) {
                add(ProbabilityScenario("Double chance handball : $selection ou nul", (probability + drawOrNeutralProbability).coerceAtMost(0.92), "Double chance handball"))
            }
            add(ProbabilityScenario("Plus de ${decimal(totalLine)} buts", overProbability, "Total buts"))
            add(ProbabilityScenario("Moins de ${decimal(totalLine)} buts", 1.0 - overProbability, "Total buts"))
            add(ProbabilityScenario("${event.homeTeam} plus de ${decimal(homeLine)} buts", normalOver(homeLine, homeGoals, teamSd), "Total buts équipe"))
            add(ProbabilityScenario("${event.awayTeam} plus de ${decimal(awayLine)} buts", normalOver(awayLine, awayGoals, teamSd), "Total buts équipe"))
            add(ProbabilityScenario("$selection handicap -${decimal(copy.marginLine)} buts", selectedMarginProbability, "Handicap buts"))
            add(ProbabilityScenario("Match serré : écart de ${decimal(copy.closeLine)} buts ou moins", closeGameProbability, "Écart handball"))
            add(ProbabilityScenario("Écart probable autour de ${projectedMargin.roundToInt()} buts", (1.0 - normalOver(copy.marginLine + 1.0, projectedMargin, teamSd)).coerceIn(0.01, 0.99), "Marge probable"))
            add(ProbabilityScenario("Chaque équipe atteint 25+ buts", teamAtLeast(homeGoals, 25.0, teamSd) * teamAtLeast(awayGoals, 25.0, teamSd), "Total buts équipe"))
            addAll(listOfNotNull(venue?.scenario(event)))
        }.filter { it.probability in 0.01..0.99 }
            .distinctBy { it.type + it.label }
            .sortedByDescending { it.probability }
        val summary = buildList {
            add("${event.homeTeam} : ${home.wins}V-${home.losses}D, ${decimal(home.averageScored)} buts marqués / ${decimal(home.averageConceded)} buts encaissés par match")
            add("${event.awayTeam} : ${away.wins}V-${away.losses}D, ${decimal(away.averageScored)} buts marqués / ${decimal(away.averageConceded)} buts encaissés par match")
            add("Écart moyen projeté : ${decimal(projectedMargin)} buts ; total projeté : ${decimal(totalMean)} buts.")
            add("Stats handball intégrées : forme 5-10 matchs si disponible, attaque/défense, buts par match, écart, H2H si disponible, domicile/extérieur seulement si la compétition n'est pas neutre.")
            add("Stats handball à renforcer : confrontations directes, arrêts gardien, gardiens disponibles, exclusions 2 minutes, pertes de balle, jets de 7 mètres, efficacité 7 m, contre-attaques, profondeur de banc, fatigue et voyage.")
            add("Marchés adaptés : vainqueur temps réglementaire, handicap buts, total buts, total buts équipe, écart probable, joueur buts seulement si stats fiables, arrêts gardien seulement si source publiée.")
            add("HT/FT handball : masqué tant qu’une source fiable ne donne pas un score ou modèle mi-temps exploitable.")
            neutralVenueSummary(event.sportKey, event.sportTitle, event.competitionName, homeTeam = event.homeTeam, awayTeam = event.awayTeam, commenceTime = event.commenceTime)?.let { add(it) }
            venue?.let { add(it.summary(event)) }
            if (kotlin.math.abs(home.formTrend) >= 0.12) add("Dynamique ${event.homeTeam} : ${trendLabel(home.formTrend)} sur la forme récente.")
            if (kotlin.math.abs(away.formTrend) >= 0.12) add("Dynamique ${event.awayTeam} : ${trendLabel(away.formTrend)} sur la forme récente.")
            val homeUnavailable = unavailablePlayers(home)
            val awayUnavailable = unavailablePlayers(away)
            if (homeUnavailable.isNotEmpty()) add("Absences ${event.homeTeam} : ${homeUnavailable.joinToString(", ")}")
            if (awayUnavailable.isNotEmpty()) add("Absences ${event.awayTeam} : ${awayUnavailable.joinToString(", ")}")
            if (homeUnavailable.isEmpty() && awayUnavailable.isEmpty()) add("Absences/suspensions : aucun fait relevé dans les sources actuelles.")
            event.standingSignals.forEach { add("Enjeu ${it.teamName} : ${it.description}") }
        }
        return PublicPrediction(
            id = "${event.eventId}:stats:handball",
            market = "Vainqueur temps réglementaire",
            selection = selection,
            referenceOdds = 1.0 / probability,
            impliedProbability = probability,
            estimatedProbability = probability,
            valueEdge = 0.0,
            expectedValue = 0.0,
            confidenceScore = confidence,
            riskLevel = riskLevel(probability),
            category = category(confidence),
            sourceName = "${event.dataSourceName} · handball multi-source",
            explanation = "Lecture handball sur le temps réglementaire : buts marqués/encaissés, écart moyen, forme récente, contexte de compétition, puis gardiens, exclusions 2 minutes et jets de 7 mètres quand ces données sont publiées.",
            positiveArguments = listOf(
                "${(home.sourceNames + away.sourceNames).distinct().size} sources statistiques recoupées pour les moyennes handball.",
                "$sample matchs minimum comparés par équipe.",
            ) + listOfNotNull(venue?.positiveArgument(event)),
            negativeArguments = listOf(
                "Si les sources ne donnent pas les arrêts gardien, exclusions 2 minutes, 7 mètres ou pertes de balle, ces points restent marqués à renforcer au lieu d'être inventés.",
            ),
            expectedScore = expectedScore,
            statSummary = summary,
            scenarios = scenarios,
        )
    }

    private fun volleyballStatisticalPrediction(
        event: PublicEvent,
        home: TeamStatProfile,
        away: TeamStatProfile,
    ): PublicPrediction {
        val venue = venueEdge(event)
        val homeDiff = home.averageScored - home.averageConceded
        val awayDiff = away.averageScored - away.averageConceded
        val homeSituation = (contextImpact(event, event.homeTeam) + availabilityImpact(home) + momentumImpact(home) + standingImpact(event, event.homeTeam))
            .coerceIn(-0.08, 0.06)
        val awaySituation = (contextImpact(event, event.awayTeam) + availabilityImpact(away) + momentumImpact(away) + standingImpact(event, event.awayTeam))
            .coerceIn(-0.08, 0.06)
        val strength = (home.winRate - away.winRate) * 2.0 + (homeDiff - awayDiff) / 2.8 + (venue?.strengthShift ?: 0.0) +
            (homeSituation - awaySituation) * 1.6
        val homeWin = logistic(strength).coerceIn(0.18, 0.82)
        val awayWin = 1.0 - homeWin
        val selectedHome = homeWin >= awayWin
        val probability = maxOf(homeWin, awayWin)
        val selection = if (selectedHome) event.homeTeam else event.awayTeam
        val closeness = (1.0 - kotlin.math.abs(homeWin - awayWin)).coerceIn(0.0, 1.0)
        val sweepProbability = ((probability - 0.50) * 0.95 + (1.0 - closeness) * 0.30 + 0.18).coerceIn(0.16, 0.58)
        val fiveSetProbability = (0.14 + closeness * 0.32 - (probability - 0.50) * 0.12).coerceIn(0.12, 0.46)
        val fourSetProbability = (1.0 - sweepProbability - fiveSetProbability).coerceIn(0.18, 0.56)
        val normalized = sweepProbability + fourSetProbability + fiveSetProbability
        val p30 = sweepProbability / normalized
        val p31 = fourSetProbability / normalized
        val p32 = fiveSetProbability / normalized
        val bestSetScore = listOf(
            "3-0" to p30,
            "3-1" to p31,
            "3-2" to p32,
        ).maxBy { it.second }
        val scoreForHome = if (selectedHome) bestSetScore.first else bestSetScore.first.split("-").reversed().joinToString("-")
        val expectedSets = (3.0 * p30 + 4.0 * p31 + 5.0 * p32).coerceIn(3.0, 5.0)
        val homePointProxy = volleyballPointsPerSet(home, away, expectedSets, homeSituation, venue?.homeScoringBoost ?: 0.0)
        val awayPointProxy = volleyballPointsPerSet(away, home, expectedSets, awaySituation, -(venue?.awayScoringDrag ?: 0.0))
        val totalPointsMean = ((homePointProxy + awayPointProxy) * expectedSets).coerceIn(120.0, 235.0)
        val pointsLine = floor(totalPointsMean / 5.0) * 5.0 + 0.5
        val homeTeamPointsMean = (homePointProxy * expectedSets).coerceIn(55.0, 125.0)
        val awayTeamPointsMean = (awayPointProxy * expectedSets).coerceIn(55.0, 125.0)
        val homeTeamPointsLine = floor(homeTeamPointsMean / 5.0) * 5.0 + 0.5
        val awayTeamPointsLine = floor(awayTeamPointsMean / 5.0) * 5.0 + 0.5
        val projectedPointMargin = kotlin.math.abs(homeTeamPointsMean - awayTeamPointsMean)
        val pointHandicapLine = 5.5
        val sample = minOf(home.games, away.games)
        val agreement = (home.sourceAgreement + away.sourceAgreement) / 2.0
        val confidence = (35 + agreement * 0.30 + kotlin.math.abs(homeWin - awayWin) * 38 + sample.coerceAtMost(8) * 1.2)
            .roundToInt().coerceIn(42, 89)
        val scenarios = buildList {
            add(ProbabilityScenario("Vainqueur volley : ${event.homeTeam}", homeWin, "Vainqueur volley"))
            add(ProbabilityScenario("Vainqueur volley : ${event.awayTeam}", awayWin, "Vainqueur volley"))
            add(ProbabilityScenario("Score en sets probable : $selection $scoreForHome", bestSetScore.second, "Score en sets"))
            add(ProbabilityScenario("Plus de 3,5 sets", p31 + p32, "Total sets"))
            add(ProbabilityScenario("Plus de 4,5 sets", p32, "Total sets"))
            add(ProbabilityScenario("Moins de 4,5 sets", 1.0 - p32, "Total sets"))
            add(ProbabilityScenario("$selection handicap -1,5 set", p30 + p31, "Handicap sets"))
            add(ProbabilityScenario("$selection handicap -${decimal(pointHandicapLine)} points", normalOver(pointHandicapLine, projectedPointMargin, 10.0), "Handicap points"))
            add(ProbabilityScenario("${if (selectedHome) event.awayTeam else event.homeTeam} gagne au moins un set", p31 + p32, "Équipe gagne un set"))
            add(ProbabilityScenario("Total match plus de ${decimal(pointsLine)} points", normalOver(pointsLine, totalPointsMean, 15.0), "Total points match"))
            add(ProbabilityScenario("${event.homeTeam} plus de ${decimal(homeTeamPointsLine)} points", normalOver(homeTeamPointsLine, homeTeamPointsMean, 9.0), "Total points équipe"))
            add(ProbabilityScenario("${event.awayTeam} plus de ${decimal(awayTeamPointsLine)} points", normalOver(awayTeamPointsLine, awayTeamPointsMean, 9.0), "Total points équipe"))
            add(ProbabilityScenario("Projection points par set : ${event.homeTeam} ${decimal(homePointProxy)} / ${event.awayTeam} ${decimal(awayPointProxy)}", 0.68.coerceAtMost(confidence / 100.0), "Points par set"))
            if (closeness >= 0.72) {
                add(ProbabilityScenario("Vainqueur avec prudence : $selection, niveaux proches", probability, "Vainqueur prudent"))
            }
            add(ProbabilityScenario("Match serré : 4 ou 5 sets", p31 + p32, "Match serré"))
            addAll(listOfNotNull(venue?.scenario(event)))
        }.filter { it.probability in 0.01..0.99 }
            .distinctBy { it.type + it.label }
            .sortedByDescending { it.probability }
        val summary = buildList {
            add("${event.homeTeam} : ${home.wins}V-${home.losses}D, repère ${decimal(home.averageScored)} marqués / ${decimal(home.averageConceded)} concédés.")
            add("${event.awayTeam} : ${away.wins}V-${away.losses}D, repère ${decimal(away.averageScored)} marqués / ${decimal(away.averageConceded)} concédés.")
            add("Score en sets projeté : $scoreForHome ; durée probable : ${decimal(expectedSets)} sets.")
            add("Points par set estimés : ${event.homeTeam} ${decimal(homePointProxy)} / ${event.awayTeam} ${decimal(awayPointProxy)} ; total match autour de ${decimal(totalPointsMean)} points.")
            add("Stats volley intégrées : forme 5-10 matchs si disponible, sets gagnés/perdus, points marqués/concédés, moyenne de sets, score 3-0/3-1/3-2, H2H si disponible, domicile/extérieur seulement si non neutre.")
            add("Stats volley à renforcer : confrontations directes, réception, qualité de service, aces, fautes de service, contres, efficacité offensive, rotations, absents/incertains, fatigue, voyage et calendrier.")
            add("Marchés adaptés : vainqueur, score en sets, over/under 3,5 ou 4,5 sets, handicap sets, total points match, points équipe, équipe gagne au moins un set, joueur points/aces/contres seulement si stats fiables.")
            neutralVenueSummary(event.sportKey, event.sportTitle, event.competitionName, homeTeam = event.homeTeam, awayTeam = event.awayTeam, commenceTime = event.commenceTime)?.let { add(it) }
            venue?.let { add(it.summary(event)) }
            if (kotlin.math.abs(home.formTrend) >= 0.12) add("Dynamique ${event.homeTeam} : ${trendLabel(home.formTrend)} sur la forme récente.")
            if (kotlin.math.abs(away.formTrend) >= 0.12) add("Dynamique ${event.awayTeam} : ${trendLabel(away.formTrend)} sur la forme récente.")
            val homeUnavailable = unavailablePlayers(home)
            val awayUnavailable = unavailablePlayers(away)
            if (homeUnavailable.isNotEmpty()) add("Absences ${event.homeTeam} : ${homeUnavailable.joinToString(", ")}")
            if (awayUnavailable.isNotEmpty()) add("Absences ${event.awayTeam} : ${awayUnavailable.joinToString(", ")}")
            if (homeUnavailable.isEmpty() && awayUnavailable.isEmpty()) add("Absences/rotations : aucun fait relevé dans les sources actuelles.")
            event.standingSignals.forEach { add("Enjeu ${it.teamName} : ${it.description}") }
        }
        return PublicPrediction(
            id = "${event.eventId}:stats:volleyball",
            market = "Vainqueur volley",
            selection = selection,
            referenceOdds = 1.0 / probability,
            impliedProbability = probability,
            estimatedProbability = probability,
            valueEdge = 0.0,
            expectedValue = 0.0,
            confidenceScore = confidence,
            riskLevel = riskLevel(probability),
            category = category(confidence),
            sourceName = "${event.dataSourceName} · volley multi-source",
            explanation = "Lecture volley : le modèle transforme les écarts de forme et de points en probabilité de vainqueur, score en sets, durée 3/4/5 sets, points par set et total points. Les aces, contres, réception et rotations ne sont intégrés que si les sources les publient.",
            positiveArguments = listOf(
                "${(home.sourceNames + away.sourceNames).distinct().size} sources statistiques recoupées pour les repères volley.",
                "$sample matchs minimum comparés par équipe.",
            ) + listOfNotNull(venue?.positiveArgument(event)),
            negativeArguments = listOf(
                "Si réception, aces, fautes de service, contres ou rotations ne sont pas publiés, l'app les affiche comme données à renforcer au lieu de les inventer.",
            ),
            expectedScore = scoreForHome,
            statSummary = summary,
            scenarios = scenarios,
        )
    }

    private fun volleyballPointsPerSet(
        team: TeamStatProfile,
        opponent: TeamStatProfile,
        expectedSets: Double,
        situationImpact: Double,
        venueImpact: Double,
    ): Double {
        val raw = (team.averageScored + opponent.averageConceded) / 2.0
        val base = if (raw >= 45.0) raw / expectedSets else raw
        val normalized = when {
            base >= 18.0 -> base
            base >= 3.0 -> 21.0 + (base - 3.0).coerceIn(-2.0, 4.0) * 0.45
            else -> 23.0
        }
        return (normalized * (1.0 + situationImpact * 0.18 + venueImpact * 0.08)).coerceIn(18.0, 27.5)
    }

    private fun soccerStatisticalPrediction(
        event: PublicEvent,
        home: TeamStatProfile,
        away: TeamStatProfile,
    ): PublicPrediction {
        val venue = venueEdge(event)
        val homeImpact = (contextImpact(event, event.homeTeam) + availabilityImpact(home) + momentumImpact(home) + standingImpact(event, event.homeTeam)).coerceIn(-0.10, 0.07)
        val awayImpact = (contextImpact(event, event.awayTeam) + availabilityImpact(away) + momentumImpact(away) + standingImpact(event, event.awayTeam)).coerceIn(-0.10, 0.07)
        val homeGoals = (((home.averageScored + away.averageConceded) / 2.0) * (1.0 + (venue?.homeScoringBoost ?: 0.0) + homeImpact - awayImpact * 0.25)).coerceIn(0.25, 3.8)
        val awayGoals = (((away.averageScored + home.averageConceded) / 2.0) * (1.0 + awayImpact - homeImpact * 0.25 - (venue?.awayScoringDrag ?: 0.0))).coerceIn(0.25, 3.8)
        var homeWin = 0.0
        var draw = 0.0
        var awayWin = 0.0
        val scoreLines = mutableListOf<ScoreLine>()
        for (homeScore in 0..8) {
            for (awayScore in 0..8) {
                val probability = poisson(homeScore, homeGoals) * poisson(awayScore, awayGoals)
                scoreLines += ScoreLine(homeScore, awayScore, probability)
                when {
                    homeScore > awayScore -> homeWin += probability
                    homeScore == awayScore -> draw += probability
                    else -> awayWin += probability
                }
            }
        }
        val rawHomeWin = homeWin
        val rawDraw = draw
        val rawAwayWin = awayWin
        val outcomeTotal = rawHomeWin + rawDraw + rawAwayWin
        if (outcomeTotal > 0.0) {
            homeWin /= outcomeTotal
            draw /= outcomeTotal
            awayWin /= outcomeTotal
        }
        val resultOutcomes = listOf(
            TeamSide.Home to homeWin,
            TeamSide.Draw to draw,
            TeamSide.Away to awayWin,
        )
        val strongestOutright = resultOutcomes.maxBy { it.second }
        val primary = if (strongestOutright.second >= OUTRIGHT_RECOMMENDATION_MIN) {
            PrimaryOutcome(
                selection = when (strongestOutright.first) {
                    TeamSide.Home -> "${event.homeTeam} gagne"
                    TeamSide.Away -> "${event.awayTeam} gagne"
                    TeamSide.Draw -> "Match nul"
                },
                market = "Issue à 90 minutes · hors prolongations",
                probability = strongestOutright.second,
                allowedSides = setOf(strongestOutright.first),
            )
        } else {
            val homeOrDraw = homeWin + draw
            val awayOrDraw = awayWin + draw
            if (homeOrDraw >= awayOrDraw) {
                PrimaryOutcome("${event.homeTeam} ou match nul (1X)", "Double chance à 90 minutes", homeOrDraw, setOf(TeamSide.Home, TeamSide.Draw))
            } else {
                PrimaryOutcome("${event.awayTeam} ou match nul (X2)", "Double chance à 90 minutes", awayOrDraw, setOf(TeamSide.Away, TeamSide.Draw))
            }
        }
        val selection = primary.selection
        val exactResult = scoreLines
            .filter { it.side in primary.allowedSides }
            .maxBy { it.probability }
        val selectedRawProbability = primary.allowedSides.sumOf { side ->
            when (side) {
                TeamSide.Home -> rawHomeWin
                TeamSide.Draw -> rawDraw
                TeamSide.Away -> rawAwayWin
            }
        }.coerceAtLeast(0.0001)
        val exactScoreConditionalProbability = (exactResult.probability / selectedRawProbability).coerceIn(0.01, 0.99)
        val resultMargin = resultOutcomes.map { it.second }.sortedDescending().let { it[0] - it[1] }
        val sample = minOf(home.games, away.games)
        val agreement = ((home.sourceAgreement + away.sourceAgreement) / 2.0)
        val confidence = (30 + agreement * 0.42 + resultMargin * 45 + sample.coerceAtMost(6) * 1.2)
            .roundToInt().coerceIn(42, 92)
        val totalGoals = homeGoals + awayGoals
        val btts = (1.0 - exp(-homeGoals)) * (1.0 - exp(-awayGoals))
        val over15 = poissonOver(1, totalGoals)
        val over25 = poissonOver(2, totalGoals)
        val over35 = poissonOver(3, totalGoals)
        val exactScore = "${exactResult.home} — ${exactResult.away}"
        val scenarios = mutableListOf(
            ProbabilityScenario(
                "Score le plus probable si « $selection » : $exactScore",
                exactScoreConditionalProbability,
                "Score exact conditionnel",
            ),
            ProbabilityScenario("Victoire ${event.homeTeam}", homeWin, "Résultat"),
            ProbabilityScenario("Match nul", draw, "Résultat"),
            ProbabilityScenario("Victoire ${event.awayTeam}", awayWin, "Résultat"),
            ProbabilityScenario("Plus de 1,5 buts", over15, "Total de buts"),
            ProbabilityScenario("Moins de 1,5 but", 1.0 - over15, "Total de buts"),
            ProbabilityScenario("Plus de 2,5 buts", over25, "Total de buts"),
            ProbabilityScenario("Moins de 2,5 buts", 1.0 - over25, "Total de buts"),
            ProbabilityScenario("Plus de 3,5 buts", over35, "Total de buts"),
            ProbabilityScenario("Moins de 3,5 buts", 1.0 - over35, "Total de buts"),
            ProbabilityScenario("Les deux équipes marquent", btts, "Buts"),
        )
        venue?.let { scenarios += it.scenario(event) }
        addSoccerStatScenarios(scenarios, event, home, away)
        val summary = buildList {
            add("${event.homeTeam} : ${home.games} matchs, ${decimal(home.averageScored)} buts marqués et ${decimal(home.averageConceded)} encaissés en moyenne")
            add("${event.awayTeam} : ${away.games} matchs, ${decimal(away.averageScored)} buts marqués et ${decimal(away.averageConceded)} encaissés en moyenne")
            neutralVenueSummary(event.sportKey, event.sportTitle, event.competitionName, homeTeam = event.homeTeam, awayTeam = event.awayTeam, commenceTime = event.commenceTime)?.let { add(it) }
            venue?.let { add(it.summary(event)) }
            home.averageShots?.let { add("${event.homeTeam} : ${decimal(it)} tirs dont ${decimal(home.averageShotsOnTarget ?: 0.0)} cadrés") }
            away.averageShots?.let { add("${event.awayTeam} : ${decimal(it)} tirs dont ${decimal(away.averageShotsOnTarget ?: 0.0)} cadrés") }
            if (home.averageCorners != null && away.averageCorners != null) {
                add("Corners moyens cumulés : ${decimal(home.averageCorners + away.averageCorners)}")
            }
            add("Accord des sources statistiques : ${agreement.roundToInt()}/100")
            if (kotlin.math.abs(home.formTrend) >= 0.12) add("Dynamique ${event.homeTeam} : ${trendLabel(home.formTrend)} sur les 3 derniers matchs")
            if (kotlin.math.abs(away.formTrend) >= 0.12) add("Dynamique ${event.awayTeam} : ${trendLabel(away.formTrend)} sur les 3 derniers matchs")
            val trendingPlayers = (home.playerProfiles + away.playerProfiles)
                .filter { kotlin.math.abs(it.formTrend) >= 0.18 && it.starts >= 1 }
                .sortedByDescending { kotlin.math.abs(it.formTrend) }
                .take(4)
            trendingPlayers.forEach { add("${it.name} : ${trendLabel(it.formTrend)} (buts et passes, récent vs précédent)") }
            val homeUnavailable = unavailablePlayers(home)
            val awayUnavailable = unavailablePlayers(away)
            if (homeUnavailable.isNotEmpty()) add("Indisponibilités intégrées : ${event.homeTeam} : ${homeUnavailable.joinToString(", ")}")
            if (awayUnavailable.isNotEmpty()) add("Indisponibilités intégrées : ${event.awayTeam} : ${awayUnavailable.joinToString(", ")}")
            event.standingSignals.forEach { add("Enjeu ${it.teamName} : ${it.description}") }
        }
        return PublicPrediction(
            id = "${event.eventId}:stats:result",
            market = primary.market,
            selection = selection,
            referenceOdds = 1.0 / primary.probability.coerceAtLeast(0.05),
            impliedProbability = primary.probability,
            estimatedProbability = primary.probability,
            valueEdge = 0.0,
            expectedValue = 0.0,
            confidenceScore = confidence,
            riskLevel = riskLevel(primary.probability),
            category = category(confidence),
            sourceName = "Consensus statistique maison",
            explanation = "Le modèle maison croise les historiques indépendants, la forme, les absences et les actualités confirmées. Il estime « $selection » à ${percent(primary.probability)} après 90 minutes. Parmi les scores compatibles, $exactScore est le plus probable.",
            positiveArguments = listOf(
                "${(home.sourceNames + away.sourceNames).distinct().size} sources statistiques sont recoupées avec un accord de ${agreement.roundToInt()}/100.",
                if (strongestOutright.second < OUTRIGHT_RECOMMENDATION_MIN) "Le vainqueur sec n'est pas recommandé sous 52 % : une double chance plus robuste est proposée." else "L'issue sèche dépasse le seuil minimal de recommandation.",
            ) + listOfNotNull(venue?.positiveArgument(event)),
            negativeArguments = listOf(
                "Une actualité ou composition publiée après la dernière synchronisation peut encore modifier l'analyse.",
                "Les événements humains sont intégrés avec un impact limité pour éviter de surinterpréter un titre de presse.",
            ),
            expectedScore = exactScore,
            statSummary = summary,
            scenarios = scenarios,
        )
    }

    private fun addSoccerStatScenarios(
        scenarios: MutableList<ProbabilityScenario>,
        event: PublicEvent,
        home: TeamStatProfile,
        away: TeamStatProfile,
    ) {
        if (home.averageShots != null && away.averageShots != null) {
            val total = home.averageShots + away.averageShots
            val cutoff = (floor(total - 0.35 * sqrt(total)).toInt()).coerceAtLeast(5)
            scenarios += ProbabilityScenario("Plus de ${cutoff.toString().replace('.', ',')},5 tirs au total", poissonOver(cutoff, total), "Tirs")
        }
        home.averageShotsOnTarget?.let { mean ->
            val cutoff = floor(mean - 0.25 * sqrt(mean)).toInt().coerceAtLeast(1)
            scenarios += ProbabilityScenario("${event.homeTeam} : plus de $cutoff,5 tirs cadrés", poissonOver(cutoff, mean), "Tirs cadrés")
        }
        away.averageShotsOnTarget?.let { mean ->
            val cutoff = floor(mean - 0.25 * sqrt(mean)).toInt().coerceAtLeast(1)
            scenarios += ProbabilityScenario("${event.awayTeam} : plus de $cutoff,5 tirs cadrés", poissonOver(cutoff, mean), "Tirs cadrés")
        }
        if (home.averageCorners != null && away.averageCorners != null) {
            val mean = home.averageCorners + away.averageCorners
            val cutoff = floor(mean - 0.3 * sqrt(mean)).toInt().coerceAtLeast(2)
            scenarios += ProbabilityScenario("Plus de $cutoff,5 corners", poissonOver(cutoff, mean), "Corners")
        }
    }

    private fun twoWayStatisticalPrediction(
        event: PublicEvent,
        home: TeamStatProfile,
        away: TeamStatProfile,
    ): PublicPrediction {
        val copy = sportStatCopy(event)
        val venue = venueEdge(event)
        val homeDiff = home.averageScored - home.averageConceded
        val awayDiff = away.averageScored - away.averageConceded
        val scale = copy.scoreScale
        val homeSituation = (contextImpact(event, event.homeTeam) + availabilityImpact(home) + momentumImpact(home) + standingImpact(event, event.homeTeam))
            .coerceIn(-0.08, 0.06)
        val awaySituation = (contextImpact(event, event.awayTeam) + availabilityImpact(away) + momentumImpact(away) + standingImpact(event, event.awayTeam))
            .coerceIn(-0.08, 0.06)
        val strength = (home.winRate - away.winRate) * 2.0 + (homeDiff - awayDiff) / scale + (venue?.strengthShift ?: 0.0) +
            (homeSituation - awaySituation) * 1.6
        val homeWin = logistic(strength).coerceIn(0.18, 0.82)
        val awayWin = 1.0 - homeWin
        val homeScore = (((home.averageScored + away.averageConceded) / 2.0) * (1.0 + homeSituation + (venue?.homeScoringBoost ?: 0.0))).coerceAtLeast(0.1)
        val awayScore = (((away.averageScored + home.averageConceded) / 2.0) * (1.0 + awaySituation - (venue?.awayScoringDrag ?: 0.0))).coerceAtLeast(0.1)
        val selectedHome = homeWin >= awayWin
        val probability = maxOf(homeWin, awayWin)
        val selection = if (selectedHome) event.homeTeam else event.awayTeam
        val sample = minOf(home.games, away.games)
        val agreement = (home.sourceAgreement + away.sourceAgreement) / 2.0
        val confidence = (34 + agreement * 0.28 + kotlin.math.abs(homeWin - awayWin) * 42 + sample.coerceAtMost(6) * 1.4)
            .roundToInt().coerceIn(42, 88)
        val totalMean = homeScore + awayScore
        val totalSd = copy.fixedTotalSd ?: sqrt(totalMean.coerceAtLeast(1.0)) * copy.sdMultiplier
        val totalLine = floor(totalMean - 0.25 * totalSd) + 0.5
        val overProbability = normalOver(totalLine, totalMean, totalSd)
        val projectedMargin = kotlin.math.abs(homeScore - awayScore)
        val selectedMarginProbability = normalOver(copy.marginLine, projectedMargin, (totalSd * 0.45).coerceAtLeast(1.0))
        val closeGameProbability = (1.0 - normalOver(copy.closeLine, projectedMargin, (totalSd * 0.55).coerceAtLeast(1.0))).coerceIn(0.01, 0.99)
        val scenarios = (
            listOf(
            ProbabilityScenario("Victoire ${event.homeTeam}", homeWin, copy.resultType),
            ProbabilityScenario("Victoire ${event.awayTeam}", awayWin, copy.resultType),
            ProbabilityScenario("Plus de ${decimal(totalLine)} ${copy.totalUnit}", overProbability, "Total de ${copy.totalUnit}"),
            ProbabilityScenario("Moins de ${decimal(totalLine)} ${copy.totalUnit}", 1.0 - overProbability, "Total de ${copy.totalUnit}"),
            ProbabilityScenario("$selection gagne de ${decimal(copy.marginLine)} ${copy.marginUnit} ou plus", selectedMarginProbability, "Écart"),
            ProbabilityScenario("Match serré : écart de ${decimal(copy.closeLine)} ${copy.marginUnit} ou moins", closeGameProbability, "Écart"),
            ) + listOfNotNull(venue?.scenario(event)) + teamPerformanceScenarios(event, copy, homeScore, awayScore, totalSd, closeGameProbability)
        ).sortedByDescending { it.probability }
        var projectedHome = homeScore.roundToInt()
        var projectedAway = awayScore.roundToInt()
        if (projectedHome == projectedAway) {
            if (selectedHome) projectedHome += 1 else projectedAway += 1
        }
        return PublicPrediction(
            id = "${event.eventId}:stats:result",
            market = copy.winnerMarket,
            selection = selection,
            referenceOdds = 1.0 / probability,
            impliedProbability = probability,
            estimatedProbability = probability,
            valueEdge = 0.0,
            expectedValue = 0.0,
            confidenceScore = confidence,
            riskLevel = riskLevel(probability),
            category = category(confidence),
            sourceName = "${event.dataSourceName} · ${copy.sourceSuffix}",
            explanation = "Projection ${copy.displayName} fondée sur les taux de victoire, ${copy.explanationMetrics}, la forme et les signaux récents disponibles.",
            positiveArguments = listOf("Écart de performance récent intégré.", "$sample matchs minimum comparés par équipe.") + listOfNotNull(venue?.positiveArgument(event)),
            negativeArguments = listOf(copy.warning),
            expectedScore = "$projectedHome — $projectedAway",
            statSummary = listOf(
                "${event.homeTeam} : ${home.wins}V-${home.losses}D, ${decimal(home.averageScored)} ${copy.scoredLabel} / ${decimal(home.averageConceded)} ${copy.concededLabel}",
                "${event.awayTeam} : ${away.wins}V-${away.losses}D, ${decimal(away.averageScored)} ${copy.scoredLabel} / ${decimal(away.averageConceded)} ${copy.concededLabel}",
            ) + listOfNotNull(
                neutralVenueSummary(event.sportKey, event.sportTitle, event.competitionName, homeTeam = event.homeTeam, awayTeam = event.awayTeam, commenceTime = event.commenceTime),
                venue?.summary(event),
            ) + event.standingSignals.map { "Enjeu ${it.teamName} : ${it.description}" } + listOfNotNull(
                home.formTrend.takeIf { kotlin.math.abs(it) >= 0.12 }?.let { "Dynamique ${event.homeTeam} : ${trendLabel(it)}" },
                away.formTrend.takeIf { kotlin.math.abs(it) >= 0.12 }?.let { "Dynamique ${event.awayTeam} : ${trendLabel(it)}" },
            ),
            scenarios = scenarios,
        )
    }

    private fun teamPerformanceScenarios(
        event: PublicEvent,
        copy: SportStatCopy,
        homeScore: Double,
        awayScore: Double,
        totalSd: Double,
        closeGameProbability: Double,
    ): List<ProbabilityScenario> {
        val sport = event.sportKey.substringBefore('/')
        val teamSd = (totalSd * 0.58).coerceAtLeast(1.0)
        val homeLine = teamLine(homeScore, copy.totalUnit)
        val awayLine = teamLine(awayScore, copy.totalUnit)
        val homeOver = normalOver(homeLine, homeScore, teamSd)
        val awayOver = normalOver(awayLine, awayScore, teamSd)
        val bothLine = minOf(homeLine, awayLine)
        val bothProbability = (homeOver * awayOver).coerceIn(0.01, 0.99)
        return buildList {
            add(ProbabilityScenario("${event.homeTeam} : plus de ${decimal(homeLine)} ${copy.totalUnit}", homeOver, "Performance équipe"))
            add(ProbabilityScenario("${event.awayTeam} : plus de ${decimal(awayLine)} ${copy.totalUnit}", awayOver, "Performance équipe"))
            add(ProbabilityScenario("Les deux équipes à ${decimal(bothLine)}+ ${copy.totalUnit}", bothProbability, "Performance équipes"))
            when (sport) {
                "basketball" -> {
                    add(ProbabilityScenario("Rythme élevé : total supérieur à la projection", normalOver(homeScore + awayScore + 8.5, homeScore + awayScore, totalSd), "Rythme"))
                    add(ProbabilityScenario("Écart inférieur à 10 points", closeGameProbability, "Écart basket"))
                }
                "rugby" -> {
                    add(ProbabilityScenario("Chaque équipe atteint 15+ points", teamAtLeast(homeScore, 15.0, teamSd) * teamAtLeast(awayScore, 15.0, teamSd), "Points rugby"))
                    add(ProbabilityScenario("Match à moins de 8 points d'écart", closeGameProbability, "Écart rugby"))
                }
                "baseball" -> {
                    add(ProbabilityScenario("Chaque équipe marque 2+ runs", teamAtLeast(homeScore, 2.0, teamSd) * teamAtLeast(awayScore, 2.0, teamSd), "Runs"))
                    add(ProbabilityScenario("Match à 1 run d'écart", closeGameProbability, "Écart baseball"))
                }
                "hockey" -> {
                    add(ProbabilityScenario("Chaque équipe marque 2+ buts", teamAtLeast(homeScore, 2.0, teamSd) * teamAtLeast(awayScore, 2.0, teamSd), "Buts"))
                    add(ProbabilityScenario("Match à 1 but d'écart", closeGameProbability, "Écart hockey"))
                }
                "handball" -> {
                    add(ProbabilityScenario("Chaque équipe atteint 25+ buts", teamAtLeast(homeScore, 25.0, teamSd) * teamAtLeast(awayScore, 25.0, teamSd), "Buts handball"))
                    add(ProbabilityScenario("Match à moins de 5 buts d'écart", closeGameProbability, "Écart handball"))
                }
                "volleyball" -> {
                    add(ProbabilityScenario("Match en 4 ou 5 sets possible", closeGameProbability, "Sets"))
                    add(ProbabilityScenario("Victoire nette 3-0 possible", (1.0 - closeGameProbability).coerceIn(0.01, 0.99), "Sets"))
                }
                "football" -> {
                    add(ProbabilityScenario("Chaque équipe atteint 17+ points", teamAtLeast(homeScore, 17.0, teamSd) * teamAtLeast(awayScore, 17.0, teamSd), "Points"))
                    add(ProbabilityScenario("Match à moins d'un touchdown d'écart", closeGameProbability, "Écart"))
                }
            }
        }.filter { it.probability in 0.01..0.99 }
    }

    private fun teamLine(mean: Double, unit: String): Double {
        val step = when (unit) {
            "runs" -> if (mean >= 40.0) 10.0 else 1.0
            "sets" -> 1.0
            "buts" -> if (mean >= 12.0) 5.0 else 1.0
            else -> if (mean >= 60.0) 5.0 else 1.0
        }
        return (floor(mean / step) * step + 0.5).coerceAtLeast(0.5)
    }

    private fun teamAtLeast(mean: Double, threshold: Double, sd: Double): Double =
        normalOver(threshold - 0.5, mean, sd).coerceIn(0.01, 0.99)

    private fun formOnlyPrediction(event: PublicEvent): PublicPrediction {
        val homePerformance = performance(event.homeForm, event.homeRecord)
        val awayPerformance = performance(event.awayForm, event.awayRecord)
        if (homePerformance == null && awayPerformance == null) return limitedPrediction(event)

        val homeScore = homePerformance ?: 0.5
        val awayScore = awayPerformance ?: 0.5
        val venue = venueEdge(event)
        val delta = (homeScore - awayScore + (venue?.formShift ?: 0.0)).coerceIn(-0.8, 0.8)
        val isThreeWay = event.sportKey.startsWith("soccer") || event.sportKey.startsWith("hockey")
        val homeProbability: Double
        val awayProbability: Double
        val drawProbability: Double
        if (isThreeWay) {
            drawProbability = (0.30 - kotlin.math.abs(delta) * 0.05).coerceIn(0.22, 0.30)
            val remaining = 1.0 - drawProbability
            homeProbability = (remaining / 2.0 + delta * 0.28).coerceIn(0.10, remaining - 0.10)
            awayProbability = remaining - homeProbability
        } else {
            drawProbability = 0.0
            homeProbability = (0.5 + delta * 0.35).coerceIn(0.18, 0.82)
            awayProbability = 1.0 - homeProbability
        }

        val outcomes = buildList {
            add(TeamSide.Home to homeProbability)
            add(TeamSide.Away to awayProbability)
            if (isThreeWay) add(TeamSide.Draw to drawProbability)
        }
        val selected = outcomes.maxByOrNull { it.second } ?: return limitedPrediction(event)
        val name = when (selected.first) {
            TeamSide.Home -> event.homeTeam
            TeamSide.Away -> event.awayTeam
            TeamSide.Draw -> "Match nul"
        }
        val probability = selected.second
        val dataComplete = homePerformance != null && awayPerformance != null
        val confidence = (40 + kotlin.math.abs(delta) * 38 + if (dataComplete) 5 else 0)
            .roundToInt().coerceIn(38, 72)
        val formText = when {
            delta > 0.08 -> "La dynamique récente favorise ${event.homeTeam}."
            delta < -0.08 -> "La dynamique récente favorise ${event.awayTeam}."
            else -> "Les dynamiques disponibles sont proches : le signal reste fragile."
        }
        val formScenarios = (
            formOnlyScenarios(event, homeProbability, drawProbability, awayProbability, isThreeWay) +
                listOfNotNull(venue?.scenario(event))
            ).sortedByDescending { it.probability }

        return PublicPrediction(
            id = "${event.eventId}:form:${selected.first.name.lowercase()}",
            market = formOnlyMarket(event, isThreeWay),
            selection = name,
            referenceOdds = 1.0 / probability,
            impliedProbability = probability,
            estimatedProbability = probability,
            valueEdge = 0.0,
            expectedValue = 0.0,
            confidenceScore = confidence,
            riskLevel = riskLevel(probability),
            category = category(confidence),
            sourceName = "${event.dataSourceName} · forme et bilan",
            explanation = "L'analyse compare uniquement la forme récente et le bilan public des deux participants. " +
                "Le scénario le plus probable est « $name » à ${percent(probability)}.",
            positiveArguments = listOf(
                formText,
                "Le calendrier et les statistiques sont récupérés automatiquement.",
            ) + listOfNotNull(venue?.positiveArgument(event)),
            negativeArguments = listOf(
                "Le modèle ne connaît pas toujours les absences et compositions de dernière minute.",
                "Une confiance modérée signifie qu'il faut surtout surveiller l'événement.",
            ),
            expectedScore = formOnlyExpectedState(event, name),
            statSummary = formOnlySummary(event, homePerformance, awayPerformance) + listOfNotNull(venue?.summary(event)),
            scenarios = formScenarios,
        )
    }

    private fun formOnlyMarket(event: PublicEvent, isThreeWay: Boolean): String = when (event.sportKey.substringBefore('/')) {
        "tennis" -> "Vainqueur tennis - forme récente"
        "rugby" -> "Vainqueur temps réglementaire - forme récente"
        "basketball" -> "Vainqueur basket - forme récente"
        "baseball" -> "Vainqueur baseball - forme récente"
        "hockey" -> "Vainqueur hockey - forme récente"
        "handball" -> "Vainqueur handball - forme récente"
        "volleyball" -> "Vainqueur volley - forme récente"
        "football" -> "Vainqueur - forme récente"
        "mma", "boxing" -> "Vainqueur combat - forme récente"
        "cycling" -> "Lecture cyclisme - forme coureurs/équipes"
        "golf" -> "Lecture golf - forme récente"
        "racing", "nascar" -> "Lecture course auto - forme pilote/écurie"
        "athletics" -> "Lecture athlétisme - forme récente"
        else -> if (isThreeWay) "Issue probable (1N2)" else "Issue probable"
    }

    private fun formOnlyExpectedState(event: PublicEvent, selection: String): String = when (event.sportKey.substringBefore('/')) {
        "tennis" -> "Avantage forme : $selection"
        "rugby", "basketball", "baseball", "hockey", "handball", "volleyball", "football" ->
            "Avantage forme : $selection"
        "mma", "boxing" -> "Avantage forme/streak : $selection"
        "cycling" -> "Avantage forme coureur/équipe : $selection"
        "golf" -> "Avantage forme/parcours : $selection"
        "racing", "nascar" -> "Avantage forme pilote/écurie : $selection"
        "athletics" -> "Avantage forme/records saison : $selection"
        else -> ""
    }

    private fun formOnlySummary(event: PublicEvent, homePerformance: Double?, awayPerformance: Double?): List<String> {
        val home = homePerformance?.let { "${event.homeTeam} : indice forme ${percent(it)}" }
        val away = awayPerformance?.let { "${event.awayTeam} : indice forme ${percent(it)}" }
        val sportLine = when (event.sportKey.substringBefore('/')) {
            "tennis" -> "Stats à compléter : surface, service/retour, fatigue, face-à-face"
            "rugby" -> "Stats à compléter : points, essais/buteurs, discipline, compositions"
            "basketball" -> "Stats à compléter : points, rythme, rebonds, passes, rotations"
            "baseball" -> "Stats à compléter : runs, lanceurs, bullpen, lineups"
            "hockey" -> "Stats à compléter : buts, gardiens, supériorités, forme"
            "handball" -> "Stats à compléter : buts, gardiens, exclusions, rotations"
            "volleyball" -> "Stats à compléter : sets, service/réception, forme"
            "mma", "boxing" -> "Stats à compléter : style, méthode, rounds, cardio"
            "cycling" -> "Stats à compléter : startlist, favoris, parcours, météo, rôles d'équipe"
            "golf" -> "Stats à compléter : field, strokes gained, parcours, météo, cut"
            "racing", "nascar" -> "Stats à compléter : essais, qualifications, rythme long run, pneus, fiabilité"
            "athletics" -> "Stats à compléter : startlist, records saison, personal best, séries/finale, vent"
            else -> "Stats à compléter : forme, absences et contexte"
        }
        return listOfNotNull(home, away, sportLine)
    }

    private fun formOnlyScenarios(
        event: PublicEvent,
        homeProbability: Double,
        drawProbability: Double,
        awayProbability: Double,
        isThreeWay: Boolean,
    ): List<ProbabilityScenario> {
        val sport = event.sportKey.substringBefore('/')
        val resultType = when (sport) {
            "tennis" -> "Résultat tennis"
            "rugby" -> "Résultat rugby"
            "basketball" -> "Résultat basket"
            "baseball" -> "Résultat baseball"
            "hockey" -> "Résultat hockey"
            "handball" -> "Résultat handball"
            "volleyball" -> "Résultat volley"
            "mma", "boxing" -> "Résultat combat"
            else -> "Résultat"
        }
        val closeness = (1.0 - kotlin.math.abs(homeProbability - awayProbability)).coerceIn(0.01, 0.99)
        return buildList {
            add(ProbabilityScenario("Victoire ${event.homeTeam}", homeProbability, resultType))
            if (isThreeWay) add(ProbabilityScenario("Match nul", drawProbability, resultType))
            add(ProbabilityScenario("Victoire ${event.awayTeam}", awayProbability, resultType))
            when (sport) {
                "tennis" -> {
                    add(ProbabilityScenario("Match possiblement long / 3 sets", closeness * 0.72, "Sets"))
                    add(ProbabilityScenario("Favori en deux sets à valider par surface", maxOf(homeProbability, awayProbability) * 0.62, "Sets"))
                }
                "volleyball" -> add(ProbabilityScenario("Match en 4 ou 5 sets possible", closeness * 0.74, "Sets"))
                "rugby", "basketball", "handball", "football" ->
                    add(ProbabilityScenario("Match serré à surveiller", closeness * 0.76, "Écart"))
                "baseball" -> add(ProbabilityScenario("Total offensif à recalculer avec lineups/pitch", 0.65, "Total"))
                "hockey" -> add(ProbabilityScenario("Total de buts à recalculer avec gardiens", 0.66, "Total de buts"))
                "mma", "boxing" -> add(ProbabilityScenario("Méthode/durée à recalculer après opposition de styles", 0.62, "Méthode"))
                "cycling" -> {
                    add(ProbabilityScenario("Podium/top 10 à recalculer quand startlist et favoris sont confirmés", 0.66, "Podium/top 10"))
                    add(ProbabilityScenario("Duel coureur à surveiller après parcours et rôles d'équipe", 0.63, "Duel coureur"))
                }
                "golf" -> {
                    add(ProbabilityScenario("Top 10 à recalculer avec field et profil parcours", 0.66, "Top 10"))
                    add(ProbabilityScenario("Passer le cut à surveiller après forme récente", 0.64, "Cut"))
                }
                "racing", "nascar" -> {
                    add(ProbabilityScenario("Podium à recalculer après essais et qualifications", 0.67, "Podium"))
                    add(ProbabilityScenario("Duel pilote à surveiller après rythme long run", 0.64, "Duel pilote"))
                }
                "athletics" -> {
                    add(ProbabilityScenario("Qualification à recalculer après séries/startlist", 0.66, "Qualification"))
                    add(ProbabilityScenario("Podium à surveiller avec records saison et vent", 0.62, "Podium"))
                }
            }
        }.sortedByDescending { it.probability }
    }

    private fun limitedPrediction(event: PublicEvent): PublicPrediction {
        val profile = sportWatchProfile(event)
        val baseline = profile.baselineProbability
        return PublicPrediction(
            id = "${event.eventId}:limited",
            market = profile.market,
            selection = profile.selection,
            referenceOdds = 1.0 / baseline,
            impliedProbability = baseline,
            estimatedProbability = baseline,
            valueEdge = 0.0,
            expectedValue = 0.0,
            confidenceScore = profile.confidence,
            riskLevel = "Élevé",
            category = profile.category,
            sourceName = "${event.dataSourceName} · calendrier public",
            explanation = profile.explanation,
            positiveArguments = profile.positiveArguments,
            negativeArguments = profile.negativeArguments,
            expectedScore = profile.expectedState,
            statSummary = profile.statSummary,
            scenarios = profile.scenarios,
        )
    }

    private fun totalPrediction(event: PublicEvent): PublicPrediction? {
        val line = event.totalLine ?: return null
        val over = validOdds(event.overOdds) ?: return null
        val under = validOdds(event.underOdds) ?: return null
        val overRaw = 1.0 / over
        val underRaw = 1.0 / under
        val total = overRaw + underRaw
        if (total <= 0.0) return null
        val overFair = overRaw / total
        val underFair = underRaw / total
        val isOver = overFair >= underFair
        val estimated = maxOf(overFair, underFair)
        if (estimated < 0.56) return null
        val odds = if (isOver) over else under
        val implied = if (isOver) overRaw else underRaw
        val totalCopy = totalMarketCopy(event)
        val unit = totalCopy.unit
        val lineText = line.toString().replace('.', ',')
        val selection = "${if (isOver) "Plus" else "Moins"} de $lineText $unit"
        val confidence = (47 + (estimated - 0.5) * 100).roundToInt().coerceIn(48, 72)

        return PublicPrediction(
            id = "${event.eventId}:total:${if (isOver) "over" else "under"}",
            market = totalCopy.market,
            selection = selection,
            referenceOdds = odds,
            impliedProbability = implied.coerceIn(0.01, 0.99),
            estimatedProbability = estimated,
            valueEdge = estimated - implied,
            expectedValue = estimated * odds - 1.0,
            confidenceScore = confidence,
            riskLevel = riskLevel(estimated),
            category = "Exotique",
            sourceName = sourceName(event.provider),
            explanation = "Le marché public penche vers « $selection » après retrait de la marge. " +
                "C'est un signal de marché secondaire, à considérer avec plus de prudence que la prédiction principale.",
            positiveArguments = listOf(
                "L'orientation du marché dépasse ${percent(estimated)} après normalisation.",
                "La ligne publique de référence est fixée à $lineText $unit.",
            ),
            negativeArguments = listOf(
                "Le total dépend fortement du rythme réel de la rencontre.",
                "Ce signal ne remplace pas les informations d'effectif de dernière minute.",
            ),
        )
    }

    private fun spreadPrediction(event: PublicEvent): PublicPrediction? {
        val homeLine = event.homeSpreadLine ?: return null
        val awayLine = event.awaySpreadLine ?: return null
        val homeOdds = validOdds(event.homeSpreadOdds) ?: return null
        val awayOdds = validOdds(event.awaySpreadOdds) ?: return null
        val homeRaw = 1.0 / homeOdds
        val awayRaw = 1.0 / awayOdds
        val total = homeRaw + awayRaw
        if (total <= 0.0) return null
        val homeFair = homeRaw / total
        val awayFair = awayRaw / total
        val chooseHome = homeFair >= awayFair
        val estimated = maxOf(homeFair, awayFair)
        if (estimated < 0.56) return null
        val team = if (chooseHome) event.homeTeam else event.awayTeam
        val line = if (chooseHome) homeLine else awayLine
        val odds = if (chooseHome) homeOdds else awayOdds
        val implied = if (chooseHome) homeRaw else awayRaw
        val lineText = formatLine(line)
        val confidence = (47 + (estimated - 0.5) * 100).roundToInt().coerceIn(48, 72)

        return PublicPrediction(
            id = "${event.eventId}:spread:${if (chooseHome) "home" else "away"}",
            market = spreadMarket(event),
            selection = "$team $lineText",
            referenceOdds = odds,
            impliedProbability = implied.coerceIn(0.01, 0.99),
            estimatedProbability = estimated,
            valueEdge = estimated - implied,
            expectedValue = estimated * odds - 1.0,
            confidenceScore = confidence,
            riskLevel = riskLevel(estimated),
            category = "Exotique",
            sourceName = sourceName(event.provider),
            explanation = "Après retrait de la marge, le marché public favorise le handicap « $team $lineText ». " +
                "Ce signal secondaire sert surtout d'alternative au scénario vainqueur.",
            positiveArguments = listOf(
                "Le handicap offre une lecture plus nuancée que le vainqueur sec.",
                "L'orientation normalisée atteint ${percent(estimated)}.",
            ),
            negativeArguments = listOf(
                "Un handicap peut perdre même si l'équipe choisie réalise un bon match.",
                "La ligne peut évoluer avant le début de l'événement.",
            ),
        )
    }

    private fun totalMarketCopy(event: PublicEvent): TotalMarketCopy {
        return when (event.sportKey.substringBefore('/')) {
            "soccer" -> TotalMarketCopy("buts", "Total de buts")
            "basketball" -> TotalMarketCopy("points", "Total de points")
            "rugby" -> TotalMarketCopy("points", "Total de points rugby")
            "baseball" -> TotalMarketCopy("runs", "Total de runs")
            "hockey" -> TotalMarketCopy("buts", "Total de buts")
            "handball" -> TotalMarketCopy("buts", "Total de buts handball")
            "volleyball" -> TotalMarketCopy("sets", "Total de sets")
            "football" -> TotalMarketCopy("points", "Total de points")
            "tennis" -> TotalMarketCopy("jeux", "Total de jeux")
            "mma", "boxing" -> TotalMarketCopy("rounds", "Total de rounds")
            "racing", "nascar" -> TotalMarketCopy("places", "Total classement pilote")
            "cycling" -> TotalMarketCopy("places", "Total classement coureur")
            "golf" -> TotalMarketCopy("coups", "Total de coups")
            "athletics" -> TotalMarketCopy("rang/temps", "Total rang/temps")
            else -> TotalMarketCopy(sportStatCopy(event).totalUnit, "Total de ${sportStatCopy(event).totalUnit}")
        }
    }

    private fun spreadMarket(event: PublicEvent): String {
        return when (event.sportKey.substringBefore('/')) {
            "soccer" -> "Handicap buts"
            "basketball" -> "Handicap points"
            "rugby" -> "Handicap points rugby"
            "baseball" -> "Run line"
            "hockey" -> "Handicap buts"
            "handball" -> "Handicap buts"
            "volleyball" -> "Handicap sets"
            "football" -> "Handicap points"
            "tennis" -> "Handicap jeux"
            "mma", "boxing" -> "Handicap rounds"
            "racing", "nascar" -> "Duel/handicap pilote"
            "cycling" -> "Duel/handicap coureur"
            "golf" -> "Duel/handicap score"
            "athletics" -> "Duel/handicap athlète"
            else -> "Handicap"
        }
    }

    private fun performance(form: String?, record: String?): Double? {
        val formValue = form?.uppercase()?.filter { it in "WDL" }?.takeLast(8)?.takeIf { it.isNotEmpty() }?.let { sequence ->
            sequence.sumOf { when (it) { 'W' -> 1.0; 'D' -> 0.5; else -> 0.0 } } / sequence.length
        }
        val recordValue = record?.let { value ->
            Regex("(\\d+)-(\\d+)(?:-(\\d+))?").find(value)?.destructured?.let { (wins, losses, draws) ->
                val w = wins.toDoubleOrNull() ?: 0.0
                val l = losses.toDoubleOrNull() ?: 0.0
                val d = draws.toDoubleOrNull() ?: 0.0
                val games = w + l + d
                if (games > 0) (w + d * 0.5) / games else null
            }
        }
        return listOfNotNull(formValue, recordValue).takeIf { it.isNotEmpty() }?.average()
    }

    private fun validOdds(value: Double?): Double? = value?.takeIf { it in 1.01..100.0 }
    private fun contextImpact(event: PublicEvent, teamName: String): Double = event.contextSignals
        .filter { normalizePlayer(it.teamName) == normalizePlayer(teamName) }
        .sumOf { it.impact }
        .coerceIn(-0.06, 0.04)

    private fun standingImpact(event: PublicEvent, teamName: String): Double = event.standingSignals
        .firstOrNull { normalizePlayer(it.teamName) == normalizePlayer(teamName) }
        ?.let { (it.importance * 0.015).coerceIn(0.0, 0.015) } ?: 0.0

    private fun availabilityImpact(profile: TeamStatProfile): Double = -profile.playerProfiles
        .filter { it.availabilityNote != null }
        .sumOf { player ->
            if (player.starts >= 2 || player.goals + player.secondaryGoals + player.assists + player.secondaryAssists >= 2.0) 0.018 else 0.007
        }.coerceIn(0.0, 0.055)

    private fun momentumImpact(profile: TeamStatProfile): Double {
        val keyPlayers = profile.playerProfiles.filter { it.starts >= 1 || it.goals + it.secondaryGoals >= 1.0 }
            .sortedByDescending { it.starts + it.goals + it.secondaryGoals + it.assists + it.secondaryAssists }
            .take(5)
        val playerTrend = keyPlayers.takeIf { it.isNotEmpty() }?.map { it.formTrend }?.average() ?: 0.0
        return (profile.formTrend * 0.025 + playerTrend * 0.018).coerceIn(-0.035, 0.035)
    }

    private fun trendLabel(value: Double): String = when {
        value >= 0.45 -> "forte hausse"
        value >= 0.12 -> "hausse"
        value <= -0.45 -> "forte baisse"
        value <= -0.12 -> "baisse"
        else -> "stable"
    }

    private fun unavailablePlayers(profile: TeamStatProfile): List<String> = profile.playerProfiles
        .filter { it.availabilityNote != null }
        .sortedByDescending { it.starts + it.goals + it.secondaryGoals }
        .take(4)
        .map { "${it.name} (${it.availabilityNote})" }
    private fun sourceName(provider: String?): String = provider?.takeIf { it.isNotBlank() }?.let { "ESPN · $it" } ?: "ESPN public"
    private fun riskLevel(probability: Double): String = when {
        probability >= 0.72 -> "Faible"
        probability >= 0.55 -> "Modéré"
        else -> "Élevé"
    }
    private fun category(confidence: Int): String = when {
        confidence >= 75 -> "Safe"
        confidence >= 60 -> "Mitigé"
        else -> "Exotique"
    }
    private fun percent(value: Double): String = "${(value * 100).roundToInt()} %"
    private fun decimal(value: Double): String = String.format(java.util.Locale.FRANCE, "%.1f", value)
    private fun logistic(value: Double): Double = 1.0 / (1.0 + exp(-value))
    private fun poisson(k: Int, lambda: Double): Double {
        var factorial = 1.0
        for (index in 2..k) factorial *= index
        return exp(-lambda) * lambda.pow(k) / factorial
    }
    private fun poissonOver(cutoff: Int, lambda: Double): Double =
        (1.0 - (0..cutoff).sumOf { poisson(it, lambda) }).coerceIn(0.01, 0.99)
    private fun poissonAtLeast(threshold: Int, lambda: Double): Double = poissonOver(threshold - 1, lambda)

    private fun normalizePlayer(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9à-ÿ]+"), "")

    private fun normalOver(threshold: Double, mean: Double, standardDeviation: Double): Double {
        val z = (threshold - mean) / standardDeviation.coerceAtLeast(0.5)
        return (1.0 - normalCdf(z)).coerceIn(0.01, 0.99)
    }

    private fun normalCdf(value: Double): Double {
        val x = kotlin.math.abs(value)
        val t = 1.0 / (1.0 + 0.2316419 * x)
        val density = 0.3989423 * exp(-x * x / 2.0)
        val probability = 1.0 - density * t *
            (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))))
        return if (value >= 0) probability else 1.0 - probability
    }
    private fun formatLine(value: Double): String {
        val prefix = if (value > 0) "+" else ""
        return "$prefix${value.toString().replace('.', ',')}"
    }

    private fun teamSportWatchProfile(
        event: PublicEvent,
        sportName: String,
        selection: String,
        expectedStats: String,
        unit: String,
        resultType: String,
        totalType: String,
        playerFocus: String,
        extraScenarios: List<ProbabilityScenario> = emptyList(),
        baselineProbability: Double = 0.50,
        confidence: Int = 31,
    ): SportWatchProfile {
        val participants = listOf(event.homeTeam, event.awayTeam).filter { it.isNotBlank() }
        val eventLabel = participants.takeIf { it.size >= 2 }?.joinToString(" — ") ?: event.homeTeam.ifBlank { event.competitionName }
        val venue = venueEdge(event)
        return SportWatchProfile(
            market = "Analyse $sportName - données à compléter",
            selection = selection,
            baselineProbability = baselineProbability,
            confidence = confidence,
            expectedState = "Match à qualifier avec les données $sportName",
            explanation = "Le match est détecté, mais l'app attend les données propres au $sportName avant de sortir un vrai signal : $expectedStats. Le but est de ne pas recycler une analyse football sur ce sport.",
            positiveArguments = listOf(
                "Match confirmé dans le calendrier public.",
                "Les marchés utiles seront recalculés dès que les sources renvoient les statistiques spécifiques au $sportName.",
            ),
            negativeArguments = listOf(
                "Sans données récentes solides, le vainqueur sec reste une surveillance et pas une vraie recommandation.",
                "Compositions, absences, météo ou contexte de compétition peuvent encore changer la lecture.",
            ),
            statSummary = listOf(
                "Match : $eventLabel",
                "Stats attendues : $expectedStats",
                "Performances joueurs attendues : $playerFocus",
            ) + listOfNotNull(venue?.summary(event)),
            scenarios = (
                listOf(
                    ProbabilityScenario("Match confirmé au calendrier public", 1.0, "Calendrier $sportName"),
                    ProbabilityScenario("Vainqueur à éviter sans forme et effectifs recoupés", 0.50, resultType),
                    ProbabilityScenario("Total de $unit à recalculer avec les données du sport", 0.64, totalType),
                    ProbabilityScenario("Performance équipe à recalculer dès les moyennes récentes disponibles", 0.70, "Performance équipe"),
                    ProbabilityScenario("$playerFocus à surveiller si les données joueurs existent", 0.62, "Joueurs"),
                ) + listOfNotNull(venue?.scenario(event)) + extraScenarios
            ).distinctBy { it.label },
        )
    }

    private fun sportWatchProfile(event: PublicEvent): SportWatchProfile {
        val sport = event.sportKey.substringBefore('/')
        val participants = listOf(event.homeTeam, event.awayTeam).filter { it.isNotBlank() }
        val eventLabel = participants.takeIf { it.size >= 2 }?.joinToString(" — ") ?: event.homeTeam.ifBlank { event.competitionName }
        return when (sport) {
            "soccer" -> teamSportWatchProfile(
                event = event,
                sportName = "football",
                selection = "Attendre forme, compositions, buts attendus, tirs et absences",
                expectedStats = "buts marqués/encaissés, tirs, tirs cadrés, corners, possession, compositions, absences",
                unit = "buts",
                resultType = "Résultat football",
                totalType = "Total de buts",
                playerFocus = "buts et passes décisives",
                extraScenarios = listOf(
                    ProbabilityScenario("Score exact à calculer après buts attendus recoupés", 0.62, "Score exact"),
                    ProbabilityScenario("Buteur/passeur à calculer après compositions", 0.66, "Joueurs"),
                ),
                baselineProbability = 1.0 / 3.0,
            )
            "basketball" -> teamSportWatchProfile(
                event = event,
                sportName = "basketball",
                selection = "Attendre rythme, rotations, points, rebonds et passes",
                expectedStats = "points marqués/encaissés, pace, rebonds, passes, pertes de balle, rotations, blessures",
                unit = "points",
                resultType = "Résultat basket",
                totalType = "Total de points",
                playerFocus = "points, rebonds et passes",
                extraScenarios = listOf(
                    ProbabilityScenario("Rythme élevé à recalculer avec pace et rotations", 0.66, "Rythme"),
                    ProbabilityScenario("Écart/handicap points à surveiller après absences", 0.63, "Écart basket"),
                ),
            )
            "rugby" -> teamSportWatchProfile(
                event = event,
                sportName = "rugby",
                selection = "Attendre compositions, discipline, essais, buteurs et météo",
                expectedStats = "points, essais, transformations/pénalités, discipline, cartons, mêlée/touche, compositions, météo",
                unit = "points",
                resultType = "Résultat rugby",
                totalType = "Total de points",
                playerFocus = "essais, passes décisives et buteurs",
                extraScenarios = listOf(
                    ProbabilityScenario("Essais équipe à recalculer avec compositions et météo", 0.64, "Essais"),
                    ProbabilityScenario("Cartons/discipline à surveiller si arbitre et historiques sont disponibles", 0.58, "Discipline"),
                ),
            )
            "baseball" -> teamSportWatchProfile(
                event = event,
                sportName = "baseball",
                selection = "Attendre lanceurs probables, lineups, bullpen et runs",
                expectedStats = "runs, lanceurs probables, bullpen, hits, home runs, lineups, météo/stade",
                unit = "runs",
                resultType = "Résultat baseball",
                totalType = "Total de runs",
                playerFocus = "coups sûrs et home runs",
                extraScenarios = listOf(
                    ProbabilityScenario("Run line à recalculer avec lanceurs probables", 0.65, "Run line"),
                    ProbabilityScenario("Home run joueur à surveiller avec lineups et stade", 0.57, "Joueurs"),
                ),
            )
            "hockey" -> teamSportWatchProfile(
                event = event,
                sportName = "hockey sur glace",
                selection = "Attendre gardiens, buts, supériorités numériques et absences",
                expectedStats = "buts, tirs, gardiens titulaires, power play, penalty kill, absences, forme récente",
                unit = "buts",
                resultType = "Résultat hockey",
                totalType = "Total de buts",
                playerFocus = "buts et assists",
                extraScenarios = listOf(
                    ProbabilityScenario("Gardien titulaire à confirmer avant total de buts", 0.68, "Gardiens"),
                    ProbabilityScenario("Power play à surveiller si données disponibles", 0.60, "Supériorités"),
                ),
            )
            "handball" -> teamSportWatchProfile(
                event = event,
                sportName = "handball",
                selection = "Données faibles · projection handball impossible pour l’instant",
                expectedStats = "buts marqués/encaissés, goals/match, écart moyen, gardiens disponibles, arrêts gardien, exclusions 2 minutes, pertes de balle, jets de 7 mètres, efficacité 7 m, contre-attaques, rotations, absences, suspensions, banc, fatigue, voyage, calendrier et enjeu",
                unit = "buts",
                resultType = "Vainqueur temps réglementaire",
                totalType = "Total de buts",
                playerFocus = "buts joueurs uniquement si données fiables ; arrêts gardien seulement si source dédiée",
                extraScenarios = listOf(
                    ProbabilityScenario("Projection impossible : attendre buts/match, gardiens et exclusions 2 minutes", 0.50, "Données faibles"),
                    ProbabilityScenario("Total buts à activer seulement avec rythme et gardiens recoupés", 0.50, "Total buts"),
                    ProbabilityScenario("Handicap buts à activer seulement avec écart moyen fiable", 0.50, "Handicap buts"),
                    ProbabilityScenario("Joueur buts à activer seulement avec temps de jeu et moyenne buts fiable", 0.50, "Joueur buts"),
                ),
                confidence = 24,
            )
            "volleyball" -> teamSportWatchProfile(
                event = event,
                sportName = "volley-ball",
                selection = "Données faibles · projection volley impossible pour l’instant",
                expectedStats = "sets gagnés/perdus, score 3-0/3-1/3-2, points par set, points marqués/concédés, total points match, aces, fautes de service, réception, contres, efficacité attaque, rotations, absents/incertains, fatigue, voyage, calendrier, H2H et enjeu",
                unit = "sets",
                resultType = "Résultat volley",
                totalType = "Total de sets",
                playerFocus = "points, aces et contres uniquement si données individuelles fiables",
                extraScenarios = listOf(
                    ProbabilityScenario("Projection impossible : attendre points par set, service/réception et rotations", 0.50, "Données faibles"),
                    ProbabilityScenario("Score en sets à activer seulement avec forme et points par set fiables", 0.50, "Score en sets"),
                    ProbabilityScenario("Over/under 3,5 ou 4,5 sets à activer seulement avec équilibre fiable", 0.50, "Total sets"),
                    ProbabilityScenario("Joueur points/aces/contres à activer seulement avec stats individuelles", 0.50, "Joueurs volley"),
                ),
                confidence = 24,
            )
            "football" -> teamSportWatchProfile(
                event = event,
                sportName = "football américain",
                selection = "Attendre quarterback, blessures, rythme offensif et turnovers",
                expectedStats = "points, yards, touchdowns, turnovers, quarterback, blessures, météo, rythme offensif",
                unit = "points",
                resultType = "Résultat football US",
                totalType = "Total de points",
                playerFocus = "touchdowns, yards et points joueurs",
                extraScenarios = listOf(
                    ProbabilityScenario("Touchdown joueur à calculer avec depth chart et rôle offensif", 0.60, "Touchdowns"),
                    ProbabilityScenario("Turnovers/météo à intégrer avant total points", 0.63, "Contexte"),
                ),
            )
            "tennis" -> SportWatchProfile(
                market = "Analyse tennis - données à compléter",
                selection = "Attendre forme surface, service/retour et fatigue",
                baselineProbability = 0.50,
                confidence = 30,
                expectedState = "Match à qualifier par surface et forme récente",
                explanation = "Le match est détecté, mais il manque encore les signaux tennis essentiels : surface, forme récente, fatigue du tableau, efficacité au service et qualité du retour.",
                positiveArguments = listOf("Match tennis confirmé au calendrier.", "Les marchés utiles seront vainqueur, sets, total de jeux, tie-break et handicap jeux si les données deviennent fiables."),
                negativeArguments = listOf("Sans historique surface/service/retour, un favori serait trop fragile.", "Blessure, abandon récent ou enchaînement de matchs peuvent inverser la lecture."),
                statSummary = listOf("Match : $eventLabel", "Stats attendues : surface, forme 5 matchs, service, retour, fatigue, face-à-face"),
                scenarios = listOf(
                    ProbabilityScenario("Match confirmé au calendrier", 1.0, "Calendrier tennis"),
                    ProbabilityScenario("Vainqueur à éviter sans surface et forme récentes", 0.50, "Vainqueur"),
                    ProbabilityScenario("Recalcul utile dès stats service/retour ou fatigue disponibles", 0.80, "Déclencheur"),
                    ProbabilityScenario("Sets/jeux à surveiller après lecture du profil de surface", 0.65, "Sets et jeux"),
                    ProbabilityScenario("Match en 3 sets possible si niveaux et service sont proches", 0.58, "Sets"),
                    ProbabilityScenario("Tie-break à recalculer avec qualité de service et surface rapide", 0.54, "Tie-break"),
                    ProbabilityScenario("Breaks à surveiller avec efficacité retour et secondes balles", 0.56, "Breaks"),
                    ProbabilityScenario("Handicap jeux à éviter tant que fatigue et historique surface manquent", 0.50, "Handicap jeux"),
                ),
            )
            "cycling" -> SportWatchProfile(
                market = "Analyse course cycliste - surveillance",
                selection = "Surveiller startlist officielle, favoris, parcours et météo",
                baselineProbability = 0.50,
                confidence = 32,
                expectedState = "Course à qualifier par startlist et parcours",
                explanation = "La course est détectée, mais en cyclisme il faut d'abord recouper startlist, rôles d'équipe, parcours, météo, forme récente et objectifs des leaders avant de sortir un vainqueur/podium.",
                positiveArguments = listOf("Course confirmée dans le calendrier public.", "Les bons marchés cyclisme seront vainqueur, podium, top 10 ou duel de coureurs quand les données sont recoupées."),
                negativeArguments = listOf("Chute, abandon, météo, rôle d'équipier ou changement de leader peuvent renverser le scénario.", "Sans startlist fiable, l'app doit rester en surveillance."),
                statSummary = listOf("Course : $eventLabel", "Stats attendues : startlist, favoris, parcours, météo, forme coureurs, rôles d'équipe"),
                scenarios = listOf(
                    ProbabilityScenario("Course confirmée au calendrier public", 1.0, "Calendrier"),
                    ProbabilityScenario("Signal vainqueur/podium à éviter sans startlist", 0.50, "Podium/vainqueur"),
                    ProbabilityScenario("Top 10 ou duel coureur à calculer après favoris recoupés", 0.65, "Coureurs"),
                    ProbabilityScenario("Recalcul utile dès météo, parcours et rôles d'équipe confirmés", 0.80, "Déclencheur"),
                    ProbabilityScenario("Sprint massif à évaluer si profil plat et équipes sprinteurs confirmées", 0.55, "Profil course"),
                    ProbabilityScenario("Échappée gagnante à surveiller selon parcours, vent et contrôle peloton", 0.52, "Scénario course"),
                    ProbabilityScenario("Grimpeur/puncheur à privilégier seulement si dénivelé et favoris concordent", 0.57, "Profil coureur"),
                    ProbabilityScenario("Risque chute/abandon à intégrer avec météo, pavés ou descente technique", 0.60, "Risque course"),
                ),
            )
            "golf" -> SportWatchProfile(
                market = "Analyse tournoi golf - surveillance",
                selection = "Attendre champ de joueurs, forme et profil du parcours",
                baselineProbability = 0.50,
                confidence = 30,
                expectedState = "Tournoi à qualifier par field et parcours",
                explanation = "Le tournoi est détecté, mais un signal golf fiable demande le champ engagé, la forme récente, l'adéquation au parcours, la météo et les statistiques strokes gained si disponibles.",
                positiveArguments = listOf("Tournoi golf confirmé au calendrier.", "Les marchés adaptés seront vainqueur, top 5/top 10, passer le cut ou duel de joueurs."),
                negativeArguments = listOf("Sans field confirmé ni données de parcours, le vainqueur est trop volatil.", "Météo, cut et forme putting/approche changent vite la projection."),
                statSummary = listOf("Tournoi : $eventLabel", "Stats attendues : field, forme récente, parcours, météo, cut, top 10"),
                scenarios = listOf(
                    ProbabilityScenario("Tournoi confirmé au calendrier public", 1.0, "Calendrier golf"),
                    ProbabilityScenario("Vainqueur à éviter sans field et forme parcours", 0.50, "Vainqueur"),
                    ProbabilityScenario("Top 10/cut à calculer quand joueurs engagés confirmés", 0.70, "Top/cut"),
                    ProbabilityScenario("Recalcul utile après météo et profil du parcours", 0.78, "Déclencheur"),
                    ProbabilityScenario("Passer le cut à surveiller avec régularité tee-to-green", 0.66, "Cut"),
                    ProbabilityScenario("Top 20 à recalculer avant top 10 si field incomplet", 0.68, "Top 20"),
                    ProbabilityScenario("Duel de joueurs à attendre après strokes gained et historique parcours", 0.61, "Duel joueur"),
                    ProbabilityScenario("Risque météo/vent à intégrer avant score total et vainqueur", 0.60, "Météo"),
                ),
            )
            "mma" -> SportWatchProfile(
                market = "Analyse MMA - données à compléter",
                selection = "Attendre forme, style, catégorie de poids, grappling et méthode probable",
                baselineProbability = 0.50,
                confidence = 31,
                expectedState = "Combat MMA à qualifier par style et forme",
                explanation = "Le combat est détecté, mais il faut croiser forme récente, opposition striking/grappling, catégorie de poids, camp d'entraînement, reach, cardio, défense takedown et risques de finish avant de proposer vainqueur/méthode.",
                positiveArguments = listOf("Combat MMA confirmé au calendrier.", "Les marchés adaptés seront vainqueur, méthode KO/TKO, soumission ou décision, durée/rounds si les données sont solides."),
                negativeArguments = listOf("Blessure, cut de poids, changement d'adversaire ou opposition de styles peuvent renverser le signal.", "Sans historique de finish/distance, la projection méthode est trop fragile."),
                statSummary = listOf("Combat : $eventLabel", "Stats attendues : striking, takedowns, défense takedown, soumissions, finish/décision, reach, cardio, camp"),
                scenarios = listOf(
                    ProbabilityScenario("Combat MMA confirmé au calendrier public", 1.0, "Calendrier MMA"),
                    ProbabilityScenario("Vainqueur à éviter sans forme et styles recoupés", 0.50, "Vainqueur"),
                    ProbabilityScenario("KO/TKO ou soumission à calculer après profils", 0.62, "Méthode"),
                    ProbabilityScenario("Décision/distance à surveiller après cardio et historique de finish", 0.66, "Durée"),
                    ProbabilityScenario("Avantage grappling à recalculer avec takedowns/défense", 0.58, "Grappling"),
                    ProbabilityScenario("Over rounds à attendre avec cardio, rythme et taux de finish", 0.60, "Rounds"),
                    ProbabilityScenario("Finish rapide à surveiller si puissance ou soumission dominante", 0.55, "Finish"),
                    ProbabilityScenario("Risque cut de poids/blessure à intégrer après pesée officielle", 0.63, "Pesée"),
                ),
            )
            "boxing" -> SportWatchProfile(
                market = "Analyse boxe - données à compléter",
                selection = "Attendre forme, catégorie, reach, puissance et rounds probables",
                baselineProbability = 0.50,
                confidence = 31,
                expectedState = "Combat de boxe à qualifier par style et forme",
                explanation = "Le combat est détecté, mais la boxe demande de croiser forme récente, opposition de styles, catégorie de poids, reach, volume de coups, puissance, menton, cardio et historique KO/décision avant de proposer vainqueur ou méthode.",
                positiveArguments = listOf("Combat de boxe confirmé au calendrier.", "Les marchés adaptés seront vainqueur, KO/TKO, décision ou durée/rounds si les données sont solides."),
                negativeArguments = listOf("Cut de poids, blessure, changement d'adversaire ou style défensif peuvent changer la projection.", "Sans historique KO/décision et cardio, la projection méthode reste fragile."),
                statSummary = listOf("Combat : $eventLabel", "Stats attendues : reach, catégorie, volume de coups, KO/TKO, décisions, knockdowns, cardio, camp"),
                scenarios = listOf(
                    ProbabilityScenario("Combat de boxe confirmé au calendrier public", 1.0, "Calendrier boxe"),
                    ProbabilityScenario("Vainqueur à éviter sans forme et style recoupés", 0.50, "Vainqueur"),
                    ProbabilityScenario("KO/TKO à calculer après puissance et défense", 0.60, "Méthode"),
                    ProbabilityScenario("Décision ou distance à surveiller après cardio et volume", 0.65, "Durée"),
                    ProbabilityScenario("Over rounds à recalculer avec volume, menton et niveau défensif", 0.62, "Rounds"),
                    ProbabilityScenario("Knockdown à surveiller si puissance et écart de gabarit concordent", 0.54, "Knockdown"),
                    ProbabilityScenario("Risque pesée/cut à intégrer avant méthode ou durée", 0.61, "Pesée"),
                ),
            )
            "racing" -> SportWatchProfile(
                market = "Analyse course auto - surveillance grille",
                selection = "Attendre essais, qualifications, rythme long run et fiabilité",
                baselineProbability = 0.50,
                confidence = 33,
                expectedState = "Course à qualifier par grille et rythme",
                explanation = "La course est détectée. Avant un favori fiable, il faut recouper essais libres, qualifications, rythme de course, usure pneus, fiabilité, météo et safety car probable.",
                positiveArguments = listOf("Épreuve confirmée au calendrier.", "Les marchés adaptés seront vainqueur, podium, top 6/top 10 ou duel pilote."),
                negativeArguments = listOf("Sans grille et rythme long run, le vainqueur/podium reste trop incertain.", "Météo, stratégie et safety car peuvent changer toute la lecture."),
                statSummary = listOf("Course : $eventLabel", "Stats attendues : essais, qualifications, rythme long run, pneus, fiabilité, météo"),
                scenarios = listOf(
                    ProbabilityScenario("Course confirmée au calendrier public", 1.0, "Calendrier course"),
                    ProbabilityScenario("Podium/vainqueur à éviter sans qualifications", 0.50, "Podium/vainqueur"),
                    ProbabilityScenario("Duel pilote ou top 10 à recalculer après rythme long run", 0.70, "Pilotes"),
                    ProbabilityScenario("Recalcul utile après essais libres et grille", 0.82, "Déclencheur"),
                    ProbabilityScenario("Top 10 à surveiller après rythme course et dégradation pneus", 0.68, "Top 10"),
                    ProbabilityScenario("Safety car/météo à intégrer avant stratégie et podium", 0.60, "Course"),
                    ProbabilityScenario("Risque DNF/fiabilité à intégrer avec historique moteur et incidents", 0.57, "Fiabilité"),
                    ProbabilityScenario("Arrêt au stand/stratégie à recalculer après grille et pneus", 0.62, "Stratégie"),
                ),
            )
            "nascar" -> SportWatchProfile(
                market = "Analyse NASCAR - surveillance grille",
                selection = "Attendre grille, rythme long run, cautions, pneus et track position",
                baselineProbability = 0.50,
                confidence = 33,
                expectedState = "Course NASCAR à qualifier par grille et rythme",
                explanation = "La course NASCAR est détectée. Avant un favori fiable, il faut recouper qualifications, track position, rythme long run, usure pneus, cautions probables, météo et fiabilité.",
                positiveArguments = listOf("Épreuve NASCAR confirmée au calendrier.", "Les marchés adaptés seront vainqueur, top 5/top 10, duel pilote ou meilleur classement d'équipe."),
                negativeArguments = listOf("Sans grille, rythme long run et scénario de cautions, le vainqueur reste trop incertain.", "Incidents, stratégie et neutralisations peuvent changer toute la lecture."),
                statSummary = listOf("Course : $eventLabel", "Stats attendues : grille, rythme long run, pneus, cautions, track position, fiabilité"),
                scenarios = listOf(
                    ProbabilityScenario("Course NASCAR confirmée au calendrier public", 1.0, "Calendrier NASCAR"),
                    ProbabilityScenario("Vainqueur à éviter sans grille et rythme long run", 0.50, "Vainqueur"),
                    ProbabilityScenario("Top 10 pilote à recalculer après qualifications", 0.70, "Top 10"),
                    ProbabilityScenario("Duel pilote à surveiller après rythme long run", 0.64, "Duel pilote"),
                    ProbabilityScenario("Cautions/incidents à intégrer avant podium ou top 5", 0.62, "Cautions"),
                    ProbabilityScenario("Risque DNF/fiabilité à intégrer avec historique piste", 0.57, "Fiabilité"),
                    ProbabilityScenario("Track position à recalculer après stratégie pneus", 0.61, "Track position"),
                ),
            )
            "athletics" -> SportWatchProfile(
                market = "Analyse athlétisme - séries/finale",
                selection = "Attendre startlist, records saison et météo",
                baselineProbability = 0.50,
                confidence = 29,
                expectedState = "Épreuve à qualifier par startlist",
                explanation = "L'épreuve est détectée, mais il faut les engagés, records saison, forme récente, couloir/vent et format séries/finale pour calculer médaille ou qualification.",
                positiveArguments = listOf("Épreuve confirmée au calendrier.", "Les marchés adaptés seront vainqueur, podium, qualification ou duel d'athlètes."),
                negativeArguments = listOf("Sans startlist et records récents, le podium serait artificiel.", "Vent, couloir, fatigue et forfaits changent vite l'analyse."),
                statSummary = listOf("Épreuve : $eventLabel", "Stats attendues : startlist, season best, personal best, séries/finale, météo"),
                scenarios = listOf(
                    ProbabilityScenario("Épreuve confirmée au calendrier public", 1.0, "Calendrier athlétisme"),
                    ProbabilityScenario("Médaille/podium à éviter sans startlist", 0.50, "Podium"),
                    ProbabilityScenario("Qualification à calculer après séries et records saison", 0.68, "Qualification"),
                    ProbabilityScenario("Finale à recalculer avec séries, couloir et densité du plateau", 0.66, "Finale"),
                    ProbabilityScenario("Record saison/personal best à surveiller avec forme et météo", 0.55, "Records"),
                    ProbabilityScenario("Duel d'athlètes à attendre après startlist officielle", 0.62, "Duel"),
                    ProbabilityScenario("Vent/couloir à intégrer avant temps, podium ou qualification", 0.60, "Météo"),
                ),
            )
            else -> {
                val copy = sportStatCopy(event)
                SportWatchProfile(
                    market = "Analyse ${copy.displayName} - données à compléter",
                    selection = "Aucun favori fiable pour l'instant",
                    baselineProbability = if (sport in setOf("soccer", "hockey")) 1.0 / 3.0 else 0.50,
                    confidence = 27,
                    expectedState = "Événement à qualifier par statistiques",
                    explanation = "L'événement est détecté, mais les données disponibles ne permettent pas encore de dégager un favori sérieux. L'app attend les formes, absences, compositions et statistiques récentes propres au sport.",
                    positiveArguments = listOf("Événement et horaire confirmés dans le calendrier public.", "Les statistiques seront recalculées dès que les sources renvoient des données exploitables."),
                    negativeArguments = listOf("Aucune prédiction fiable ne doit être déduite avec si peu d'informations."),
                    statSummary = listOf("Événement : $eventLabel", "Stats attendues : forme, ${copy.scoredLabel}, ${copy.concededLabel}, absences et contexte"),
                    scenarios = listOf(
                        ProbabilityScenario("Événement confirmé au calendrier public", 1.0, "Calendrier"),
                        ProbabilityScenario("Favori fiable indisponible sans statistiques récentes", 0.50, copy.resultType),
                        ProbabilityScenario("Recalcul utile dès compositions/forme disponibles", 0.75, "Déclencheur"),
                    ),
                )
            }
        }
    }

    private fun sportStatCopy(event: PublicEvent): SportStatCopy {
        return when (event.sportKey.substringBefore('/')) {
            "basketball" -> SportStatCopy(
                displayName = "basket",
                totalUnit = "points",
                scoredLabel = "points marqués",
                concededLabel = "points encaissés",
                marginUnit = "points",
                resultType = "Résultat basket",
                winnerMarket = "Vainqueur du match",
                explanationMetrics = "les points marqués/encaissés, le rythme et les rotations",
                warning = "Rythme du match, fautes, rotations et absences peuvent modifier fortement la projection.",
                scoreScale = 12.0,
                fixedTotalSd = 16.0,
                marginLine = 5.5,
                closeLine = 7.5,
            )
            "baseball" -> SportStatCopy(
                displayName = "baseball",
                totalUnit = "runs",
                scoredLabel = "runs marqués",
                concededLabel = "runs encaissés",
                marginUnit = "runs",
                resultType = "Résultat baseball",
                winnerMarket = "Vainqueur du match",
                explanationMetrics = "les runs marqués/encaissés, la forme offensive et la stabilité défensive",
                warning = "Lanceurs probables, bullpen, lineups et météo peuvent modifier fortement la projection.",
                scoreScale = 2.8,
                marginLine = 1.5,
                closeLine = 1.5,
            )
            "rugby" -> SportStatCopy(
                displayName = "rugby",
                totalUnit = "points",
                scoredLabel = "points marqués",
                concededLabel = "points encaissés",
                marginUnit = "points",
                resultType = "Résultat rugby",
                winnerMarket = "Vainqueur temps réglementaire",
                explanationMetrics = "les points marqués/encaissés, la dynamique, les compositions et la discipline",
                warning = "Compositions, buteurs, cartons, météo et rotation d’effectif peuvent modifier fortement la projection.",
                scoreScale = 10.0,
                sdMultiplier = 1.8,
                marginLine = 6.5,
                closeLine = 7.5,
            )
            "hockey" -> SportStatCopy(
                displayName = "hockey",
                totalUnit = "buts",
                scoredLabel = "buts marqués",
                concededLabel = "buts encaissés",
                marginUnit = "buts",
                resultType = "Résultat hockey",
                winnerMarket = "Vainqueur selon marché disponible",
                explanationMetrics = "les buts marqués/encaissés, la forme et les absences connues",
                warning = "Gardiens, supériorités numériques et prolongation éventuelle peuvent changer la lecture.",
                scoreScale = 2.6,
                marginLine = 1.5,
                closeLine = 1.5,
            )
            "football" -> SportStatCopy(
                displayName = "football",
                totalUnit = "points",
                scoredLabel = "points marqués",
                concededLabel = "points encaissés",
                marginUnit = "points",
                resultType = "Résultat",
                winnerMarket = "Vainqueur du match",
                explanationMetrics = "les points marqués/encaissés, le rythme offensif et les absences",
                warning = "Quarterback, turnovers, blessures et météo peuvent modifier fortement la projection.",
                scoreScale = 10.0,
                fixedTotalSd = 13.0,
                marginLine = 3.5,
                closeLine = 6.5,
            )
            "handball" -> SportStatCopy(
                displayName = "handball",
                totalUnit = "buts",
                scoredLabel = "buts marqués",
                concededLabel = "buts encaissés",
                marginUnit = "buts",
                resultType = "Vainqueur temps réglementaire",
                winnerMarket = "Vainqueur temps réglementaire",
                explanationMetrics = "les buts marqués/encaissés, l’écart moyen, les gardiens, exclusions 2 minutes, pertes de balle, 7 mètres, rotations et fatigue",
                warning = "Gardiens, exclusions 2 minutes, jets de 7 mètres, rotations, banc et fatigue peuvent modifier fortement la projection.",
                scoreScale = 7.0,
                sdMultiplier = 1.5,
                marginLine = 3.5,
                closeLine = 4.5,
            )
            "volleyball" -> SportStatCopy(
                displayName = "volley",
                totalUnit = "sets",
                scoredLabel = "sets ou points gagnés",
                concededLabel = "sets ou points concédés",
                marginUnit = "sets",
                resultType = "Résultat volley",
                winnerMarket = "Vainqueur volley",
                explanationMetrics = "les sets gagnés/perdus, points par set, service, réception, contres, efficacité attaque, rotations et fatigue",
                warning = "Service, réception, contres, fautes directes, rotations et fatigue tournoi peuvent modifier fortement la projection.",
                scoreScale = 1.8,
                marginLine = 1.5,
                closeLine = 1.5,
            )
            else -> SportStatCopy(
                displayName = event.sportTitle.lowercase().ifBlank { "sport" },
                totalUnit = "points",
                scoredLabel = "points marqués",
                concededLabel = "points encaissés",
                marginUnit = "points",
                resultType = "Résultat",
                winnerMarket = "Vainqueur probable",
                explanationMetrics = "les scores récents, la forme et les absences connues",
                warning = "Rotations, absences et contexte de compétition peuvent modifier fortement la projection.",
                scoreScale = 5.0,
                marginLine = 2.5,
                closeLine = 3.5,
            )
        }
    }

    private fun venueEdge(event: PublicEvent): VenueEdge? {
        if (event.homeTeam.isBlank() || event.awayTeam.isBlank()) return null
        if (normalizePlayer(event.homeTeam) == normalizePlayer(event.awayTeam)) return null
        if (isNeutralVenueCompetition(
                sportKey = event.sportKey,
                sportTitle = event.sportTitle,
                competitionName = event.competitionName,
                homeTeam = event.homeTeam,
                awayTeam = event.awayTeam,
                commenceTime = event.commenceTime,
            )
        ) return null
        return when (event.sportKey.substringBefore('/')) {
            "soccer" -> VenueEdge(
                strengthShift = 0.12,
                marketShift = 0.025,
                formShift = 0.045,
                homeScoringBoost = 0.055,
                awayScoringDrag = 0.018,
                label = "avantage domicile football",
            )
            "rugby" -> VenueEdge(
                strengthShift = 0.14,
                marketShift = 0.030,
                formShift = 0.050,
                homeScoringBoost = 0.050,
                awayScoringDrag = 0.020,
                label = "avantage domicile rugby",
            )
            "basketball" -> VenueEdge(
                strengthShift = 0.11,
                marketShift = 0.026,
                formShift = 0.045,
                homeScoringBoost = 0.040,
                awayScoringDrag = 0.014,
                label = "avantage parquet domicile",
            )
            "handball" -> VenueEdge(
                strengthShift = 0.12,
                marketShift = 0.028,
                formShift = 0.048,
                homeScoringBoost = 0.042,
                awayScoringDrag = 0.016,
                label = "avantage salle domicile",
            )
            "football" -> VenueEdge(
                strengthShift = 0.12,
                marketShift = 0.027,
                formShift = 0.046,
                homeScoringBoost = 0.038,
                awayScoringDrag = 0.016,
                label = "avantage terrain domicile",
            )
            "hockey" -> VenueEdge(
                strengthShift = 0.09,
                marketShift = 0.022,
                formShift = 0.036,
                homeScoringBoost = 0.032,
                awayScoringDrag = 0.012,
                label = "avantage domicile",
            )
            "volleyball" -> VenueEdge(
                strengthShift = 0.08,
                marketShift = 0.020,
                formShift = 0.032,
                homeScoringBoost = 0.026,
                awayScoringDrag = 0.010,
                label = "avantage salle domicile",
            )
            "baseball" -> VenueEdge(
                strengthShift = 0.06,
                marketShift = 0.016,
                formShift = 0.026,
                homeScoringBoost = 0.022,
                awayScoringDrag = 0.006,
                label = "avantage stade domicile",
            )
            else -> null
        }
    }

    private fun VenueEdge.summary(event: PublicEvent): String =
        "Domicile/extérieur : ${event.homeTeam} à domicile, ${event.awayTeam} en déplacement · ${label} intégré (${percent(marketShift)})"

    private fun VenueEdge.positiveArgument(event: PublicEvent): String =
        "Le modèle tient compte du contexte domicile/extérieur : ${event.homeTeam} reçoit, ${event.awayTeam} se déplace."

    private fun VenueEdge.scenario(event: PublicEvent): ProbabilityScenario =
        ProbabilityScenario(
            "Avantage domicile ${event.homeTeam} intégré, déplacement ${event.awayTeam} pondéré",
            (0.50 + strengthShift).coerceIn(0.52, 0.72),
            "Domicile/extérieur",
        )

    private data class PrimaryOutcome(
        val selection: String,
        val market: String,
        val probability: Double,
        val allowedSides: Set<TeamSide>,
    )
    private data class SportStatCopy(
        val displayName: String,
        val totalUnit: String,
        val scoredLabel: String,
        val concededLabel: String,
        val marginUnit: String,
        val resultType: String,
        val winnerMarket: String,
        val explanationMetrics: String,
        val warning: String,
        val scoreScale: Double,
        val fixedTotalSd: Double? = null,
        val sdMultiplier: Double = 1.0,
        val marginLine: Double,
        val closeLine: Double,
        val sourceSuffix: String = "résultats récents",
    )
    private data class SportWatchProfile(
        val market: String,
        val selection: String,
        val baselineProbability: Double,
        val confidence: Int,
        val expectedState: String,
        val explanation: String,
        val positiveArguments: List<String>,
        val negativeArguments: List<String>,
        val statSummary: List<String>,
        val scenarios: List<ProbabilityScenario>,
        val category: String = "Données à compléter",
    )
    private data class TotalMarketCopy(
        val unit: String,
        val market: String,
    )
    private data class VenueEdge(
        val strengthShift: Double,
        val marketShift: Double,
        val formShift: Double,
        val homeScoringBoost: Double,
        val awayScoringDrag: Double,
        val label: String,
    )
    private data class PlayerCandidate(val stats: PlayerStatProfile, val likelyStarter: Boolean)
    private data class Candidate(val name: String, val odds: Double, val side: TeamSide)
    private data class ScoreLine(val home: Int, val away: Int, val probability: Double) {
        val side: TeamSide get() = when {
            home > away -> TeamSide.Home
            home < away -> TeamSide.Away
            else -> TeamSide.Draw
        }
    }
    private enum class TeamSide { Home, Away, Draw }

    private const val OUTRIGHT_RECOMMENDATION_MIN = 0.52
}
