package com.soliano.betvalueanalyzer.data

import com.soliano.betvalueanalyzer.data.local.PredictionDao
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.data.local.LiveEventDao
import com.soliano.betvalueanalyzer.data.local.LiveEventEntity
import com.soliano.betvalueanalyzer.data.local.UpcomingEventDao
import com.soliano.betvalueanalyzer.data.local.UpcomingEventEntity
import com.soliano.betvalueanalyzer.data.remote.EspnCompetitionDto
import com.soliano.betvalueanalyzer.data.remote.EspnEventDto
import com.soliano.betvalueanalyzer.data.remote.EspnMarketSideDto
import com.soliano.betvalueanalyzer.data.remote.EspnOddsDto
import com.soliano.betvalueanalyzer.data.remote.EspnStatusDto
import com.soliano.betvalueanalyzer.data.remote.EspnStatusTypeDto
import com.soliano.betvalueanalyzer.data.remote.EspnFallbackParser
import com.soliano.betvalueanalyzer.data.remote.EspnCompetitorDto
import com.soliano.betvalueanalyzer.data.remote.EspnLineScoreDto
import com.soliano.betvalueanalyzer.data.remote.ExternalScheduleEvent
import com.soliano.betvalueanalyzer.data.remote.EspnParticipantDto
import com.soliano.betvalueanalyzer.data.remote.TheSportsDbFallbackParser
import com.soliano.betvalueanalyzer.data.remote.VolleyballWorldFallbackParser
import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.domain.PublicEvent
import com.soliano.betvalueanalyzer.domain.PublicPrediction
import com.soliano.betvalueanalyzer.domain.RemovedSports
import com.soliano.betvalueanalyzer.domain.PublicPredictionEngine
import com.soliano.betvalueanalyzer.domain.LiveEventSnapshot
import com.soliano.betvalueanalyzer.domain.LiveProjectionEngine
import com.soliano.betvalueanalyzer.domain.LiveWindowPolicy
import com.soliano.betvalueanalyzer.domain.MatchContextSignal
import com.soliano.betvalueanalyzer.domain.TeamProfileRequest
import com.soliano.betvalueanalyzer.domain.TeamStatProfile
import com.soliano.betvalueanalyzer.domain.teamProfileKey
import com.soliano.betvalueanalyzer.domain.canonicalCompetitionName
import com.soliano.betvalueanalyzer.domain.competitionFavoriteKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class OddsSyncResult(
    val eventsReceived: Int,
    val eventsCataloged: Int,
    val eventsAnalyzed: Int,
    val predictionsSaved: Int,
)

data class OddsLiveResult(
    val eventsReceived: Int,
    val eventsTracked: Int,
    val liveCount: Int,
)

data class SyncPriorities(
    val favoriteSports: Set<String> = emptySet(),
    val favoriteCompetitions: Set<String> = emptySet(),
)

data class DeepAnalysisTarget(
    val id: String,
    val sportKey: String,
    val sportTitle: String,
    val competitionKey: String,
    val competitionName: String,
    val commenceTime: Long,
    val homeTeam: String,
    val awayTeam: String,
)

class OddsRepository(
    private val predictionDao: PredictionDao,
    private val upcomingEventDao: UpcomingEventDao,
    private val liveEventDao: LiveEventDao,
    private val syncMetadataStore: SyncMetadataStore,
    private val api: PublicSportsApiService,
    private val contextSignalStore: ContextSignalStore = NoOpContextSignalStore,
) {
    @Volatile private var preferredHost = "https://site.web.api.espn.com/"
    private val statisticsProvider = MultiSourceStatisticsProvider(api)
    private val lineupProvider = MatchLineupProvider(api)
    private val newsContextProvider = NewsContextProvider(api)
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    val upcomingPredictions: Flow<List<PredictionEntity>> =
        combine(predictionDao.observeAll(), clock) { predictions, now ->
            predictions.filter { it.commenceTime > now && RemovedSports.isAllowedSportKey(it.sportKey) }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    val upcomingEvents: Flow<List<UpcomingEventEntity>> =
        combine(upcomingEventDao.observeAll(), clock) { events, now ->
            events.filter { event ->
                RemovedSports.isAllowedSportKey(event.sportKey) &&
                    if (event.eventType == "MATCH") event.commenceTime > now
                    else event.commenceTime > now - ACTIVE_EVENT_LOOKBACK_MS
            }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    val liveEvents: Flow<List<LiveEventEntity>> =
        combine(liveEventDao.observeAll(), clock) { events, now ->
            events.filter { event -> RemovedSports.isAllowedSportKey(event.sportKey) && LiveWindowPolicy.shouldShow(event, now) }
                .sortedWith(compareByDescending<LiveEventEntity> { it.isLive }.thenBy { it.commenceTime })
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    suspend fun clearPredictions() {
        predictionDao.deleteAll()
        upcomingEventDao.deleteAll()
        liveEventDao.deleteAll()
    }

    suspend fun cleanupRemovedSports() {
        RemovedSports.keys.forEach { sport ->
            val prefix = "$sport/%"
            predictionDao.deleteBySportPrefix(sport, prefix)
            upcomingEventDao.deleteBySportPrefix(sport, prefix)
            liveEventDao.deleteBySportPrefix(sport, prefix)
        }
    }

    suspend fun analyzeDeep(
        target: DeepAnalysisTarget,
        onProgress: (Double, String) -> Unit = { _, _ -> },
    ): PredictionEntity {
        val sport = target.sportKey.substringBefore('/')
        require(RemovedSports.isAllowedSportKey(sport)) {
            "Ce sport a été retiré de l'application."
        }
        if (sport == "racing") return analyzeF1Deep(target, onProgress)
        if (sport == "cycling") return analyzeCyclingDeep(target, onProgress)
        if (sport == "tennis") return analyzeTennisDeep(target, onProgress)
        if (sport !in TEAM_SPORTS) {
            return analyzeStandaloneDeep(target, onProgress)
        }
        require(target.homeTeam.isNotBlank() && target.awayTeam.isNotBlank()) {
            "Cet événement n'a pas encore deux participants confirmés."
        }
        val now = System.currentTimeMillis()
        onProgress(0.08, "Ouverture de l'analyse ${target.sportTitle} sur 90 jours")
        val requests = listOf(target.homeTeam, target.awayTeam).map { team ->
            TeamProfileRequest(sport, target.competitionName, team)
        }
        val profiles = MultiSourceStatisticsProvider(api, deepAnalysis = true).load(requests)
        val homeProfile = profiles[teamProfileKey(sport, target.homeTeam)]
        val awayProfile = profiles[teamProfileKey(sport, target.awayTeam)]
        if (homeProfile == null || awayProfile == null) {
            throw OddsSyncException("Les historiques multi-sources sont insuffisants pour recalculer ce match.")
        }
        onProgress(0.52, "Tendances ${target.sportTitle} des équipes et joueurs calculées")
        val standingSignals = if (sport == "soccer") {
            FifaCompetitionContextProvider(api).load(target)
        } else {
            EspnCompetitionContextProvider(api).load(target)
        }
        onProgress(0.60, "Classement, forme et enjeu ${target.sportTitle} évalués")

        val newsRequest = NewsContextRequest(
            key = "deep:${target.id}",
            homeTeam = target.homeTeam,
            awayTeam = target.awayTeam,
            commenceTime = target.commenceTime,
            subjects = deepNewsSubjects(target, homeProfile, awayProfile),
            lookbackDays = 90,
        )
        val freshReport = NewsContextProvider(api).load(listOf(newsRequest))
        val mergedContext = ContextSignalCache.merge(
            requests = listOf(newsRequest),
            fresh = freshReport,
            stored = contextSignalStore.loadContextSignals(),
            now = now,
        )
        contextSignalStore.saveContextSignals(mergedContext.persisted)
        val report = mergedContext.reports[newsRequest.key] ?: NewsContextReport()
        onProgress(0.82, "Presse, conférences et comptes sociaux publics recoupés")

        val publicEvent = PublicEvent(
            eventId = "${target.id}:deep",
            sportKey = if ('/' in target.sportKey) target.sportKey else "$sport/deep",
            sportTitle = target.sportTitle,
            competitionName = target.competitionName,
            dataSourceName = "Analyse approfondie 90 jours",
            commenceTime = target.commenceTime,
            homeTeam = target.homeTeam,
            awayTeam = target.awayTeam,
            homeProfile = homeProfile,
            awayProfile = awayProfile,
            homeLineup = homeProfile.recentLineup,
            awayLineup = awayProfile.recentLineup,
            informationSources = (
                homeProfile.sourceNames + awayProfile.sourceNames +
                    (if (standingSignals.isNotEmpty()) {
                        listOf(if (sport == "soccer") "FIFA · classement officiel" else "ESPN · classement officiel")
                    } else emptyList()) +
                    report.checkedSources
                ).filter { it.isNotBlank() }.distinct(),
            contextSignals = report.signals,
            standingSignals = standingSignals,
        )
        val calculated = PublicPredictionEngine.analyze(publicEvent)
        if (calculated.isEmpty()) throw OddsSyncException("Le modèle n'a pas reçu assez de données exploitables.")
        val entities = calculated.map { it.toEntity(publicEvent, System.currentTimeMillis()) }
        predictionDao.upsertAll(entities)
        upcomingEventDao.setAnalysisId(target.id, entities.first().id)
        onProgress(1.0, "Probabilités ${target.sportTitle} recalculées et vérifiées")
        return entities.first()
    }

    private suspend fun analyzeTennisDeep(
        target: DeepAnalysisTarget,
        onProgress: (Double, String) -> Unit,
    ): PredictionEntity {
        require(target.homeTeam.isNotBlank() && target.awayTeam.isNotBlank()) {
            "Ce match tennis n'a pas encore deux joueurs confirmés."
        }
        val now = System.currentTimeMillis()
        onProgress(0.04, "Ouverture analyse tennis ATP/WTA et sources adaptées")
        val snapshot = TennisAnalysisProvider(api).load(target, onProgress)
        onProgress(0.78, "Recherche infos joueur : blessure, forfait, retour, fatigue et H2H")
        val newsRequest = NewsContextRequest(
            key = "deep:tennis:${normalizeIdentity(target.id)}",
            homeTeam = target.homeTeam,
            awayTeam = target.awayTeam,
            commenceTime = target.commenceTime,
            subjects = tennisNewsSubjects(target, snapshot.surface),
            lookbackDays = 90,
        )
        val freshReport = NewsContextProvider(api).load(listOf(newsRequest))
        val mergedContext = ContextSignalCache.merge(
            requests = listOf(newsRequest),
            fresh = freshReport,
            stored = contextSignalStore.loadContextSignals(),
            now = now,
        )
        contextSignalStore.saveContextSignals(mergedContext.persisted)
        val report = mergedContext.reports[newsRequest.key] ?: NewsContextReport()
        onProgress(0.90, "Projection tennis : ranking, surface, forme et H2H")
        val basePrediction = snapshot.toPredictionEntity(System.currentTimeMillis())
        val contextLines = report.signals.map { signal -> contextLine(signal) }
            .ifEmpty {
                listOf(
                    "${snapshot.playerA.name} : Aucun fait relevé",
                    "${snapshot.playerB.name} : Aucun fait relevé",
                )
            }
        val sourceLines = (basePrediction.sourceDetails.lines() + report.checkedSources)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val prediction = basePrediction.copy(
            contextInsights = contextLines.joinToString("\n"),
            sourceDetails = sourceLines.joinToString("\n"),
        )
        predictionDao.upsertAll(listOf(prediction))
        upcomingEventDao.setAnalysisId(target.id, prediction.id)
        onProgress(1.0, "Analyse tennis consolidée")
        return prediction
    }

    private suspend fun analyzeStandaloneDeep(
        target: DeepAnalysisTarget,
        onProgress: (Double, String) -> Unit,
    ): PredictionEntity {
        val sport = target.sportKey.substringBefore('/')
        val now = System.currentTimeMillis()
        val eventName = target.homeTeam.ifBlank { target.awayTeam.ifBlank { target.competitionName.ifBlank { target.sportTitle } } }
        val opponent = target.awayTeam.ifBlank { target.sportTitle.ifBlank { "Contexte" } }
        onProgress(0.10, "Ouverture de l'analyse ${target.sportTitle} sans clé API")
        val request = NewsContextRequest(
            key = "deep:$sport:${normalizeIdentity(target.id)}",
            homeTeam = eventName,
            awayTeam = opponent,
            commenceTime = target.commenceTime,
            subjects = standaloneNewsSubjects(sport, eventName, target.competitionName, target.sportTitle),
            lookbackDays = 90,
        )
        val fresh = NewsContextProvider(api).load(listOf(request))
        val merged = ContextSignalCache.merge(
            requests = listOf(request),
            fresh = fresh,
            stored = contextSignalStore.loadContextSignals(),
            now = now,
        )
        contextSignalStore.saveContextSignals(merged.persisted)
        val report = merged.reports[request.key] ?: NewsContextReport()
        onProgress(0.72, "Actualités, sources publiques et contexte ${target.sportTitle} recoupés")
        val publicEvent = PublicEvent(
            eventId = "${target.id}:deep",
            sportKey = if ('/' in target.sportKey) target.sportKey else "$sport/deep",
            sportTitle = target.sportTitle,
            competitionName = target.competitionName,
            dataSourceName = "Analyse approfondie ${target.sportTitle}",
            commenceTime = target.commenceTime,
            homeTeam = eventName,
            awayTeam = opponent,
            informationSources = (listOf(target.sportTitle, target.competitionName) + report.checkedSources)
                .filter { it.isNotBlank() }
                .distinct(),
            contextSignals = report.signals,
        )
        val calculated = PublicPredictionEngine.analyze(publicEvent)
        if (calculated.isEmpty()) throw OddsSyncException("Le modèle n'a pas reçu assez de données exploitables.")
        val entities = calculated.map { it.toEntity(publicEvent, System.currentTimeMillis()) }
        predictionDao.upsertAll(entities)
        upcomingEventDao.setAnalysisId(target.id, entities.first().id)
        onProgress(1.0, "Analyse ${target.sportTitle} consolidée")
        return entities.first()
    }

    private suspend fun analyzeF1Deep(
        target: DeepAnalysisTarget,
        onProgress: (Double, String) -> Unit,
    ): PredictionEntity {
        val now = System.currentTimeMillis()
        onProgress(0.06, "Identification du Grand Prix et du profil du circuit")
        val snapshot = F1AnalysisProvider(api).load(target, onProgress)
        onProgress(0.62, "Voitures comparées sur des circuits aux contraintes similaires")
        val request = NewsContextRequest(
            key = "deep:f1:${target.id}",
            homeTeam = snapshot.raceName,
            awayTeam = "Formula 1",
            commenceTime = target.commenceTime,
            subjects = snapshot.newsSubjects(),
            lookbackDays = 90,
        )
        val fresh = NewsContextProvider(api).load(listOf(request))
        val merged = ContextSignalCache.merge(
            requests = listOf(request),
            fresh = fresh,
            stored = contextSignalStore.loadContextSignals(),
            now = now,
        )
        contextSignalStore.saveContextSignals(merged.persisted)
        val report = merged.reports[request.key] ?: NewsContextReport()
        onProgress(0.88, "Presse F1, équipes, pilotes et réseaux publics recoupés")
        val prediction = snapshot.toPrediction(report, System.currentTimeMillis())
        predictionDao.upsertAll(listOf(prediction))
        upcomingEventDao.setAnalysisId(target.id, prediction.id)
        onProgress(1.0, "Probabilités F1 recalculées pour toute la grille")
        return prediction
    }

    private suspend fun analyzeCyclingDeep(
        target: DeepAnalysisTarget,
        onProgress: (Double, String) -> Unit,
    ): PredictionEntity {
        val now = System.currentTimeMillis()
        val raceName = target.homeTeam.ifBlank { target.competitionName.ifBlank { "Course cycliste" } }
        onProgress(0.08, "Calendrier UCI et course identifies")
        val request = NewsContextRequest(
            key = "deep:cycling:${target.id}",
            homeTeam = raceName,
            awayTeam = "Cyclisme",
            commenceTime = target.commenceTime,
            subjects = cyclingNewsSubjects(raceName),
            lookbackDays = 90,
        )
        val fresh = NewsContextProvider(api).load(listOf(request))
        onProgress(0.55, "Startlist, parcours, meteo, favoris et presse recherches")
        val merged = ContextSignalCache.merge(
            requests = listOf(request),
            fresh = fresh,
            stored = contextSignalStore.loadContextSignals(),
            now = now,
        )
        contextSignalStore.saveContextSignals(merged.persisted)
        val report = merged.reports[request.key] ?: NewsContextReport()
        onProgress(0.86, "Equipes, coureurs et reseaux publics recoupes")

        val raceDate = Instant.ofEpochMilli(target.commenceTime).atZone(ZoneOffset.UTC).toLocalDate()
        val sources = (listOf("UCI calendrier officiel", "TheSportsDB - UCI World Tour") + report.checkedSources)
            .filter { it.isNotBlank() }.distinct()
        val contextLines = report.signals.map { signal -> contextLine(signal) }
        val confidence = if (sources.size >= 6) 46 else 34
        val prediction = PredictionEntity(
            id = "${target.id}:deep:cycling:watch",
            eventId = "${target.id}:deep",
            sportKey = if ('/' in target.sportKey) target.sportKey else "cycling/deep",
            sportTitle = target.sportTitle.ifBlank { "Cyclisme" },
            competitionName = target.competitionName.ifBlank { raceName },
            commenceTime = target.commenceTime,
            homeTeam = raceName,
            awayTeam = "Peloton",
            market = "Analyse course cycliste - surveillance",
            selection = "Surveiller startlist officielle, favoris et parcours",
            betclicOdds = 1.0,
            impliedProbability = 0.50,
            consensusProbability = 0.50,
            valueEdge = 0.0,
            expectedValue = 0.0,
            confidenceScore = confidence,
            riskLevel = "Élevé",
            category = "Données à compléter",
            bookmakerCount = 0,
            sourceName = "Analyse cyclisme · ${sources.size} sources vérifiées",
            sourceLastUpdate = System.currentTimeMillis(),
            explanation = "La course est détectée dans le calendrier public. L'app ne force pas un vainqueur tant que la startlist, les rôles d'équipe, le parcours, la météo et la forme récente ne donnent pas un signal assez solide.",
            positiveArguments = listOf(
                "Course confirmée au calendrier public avec une date exploitable.",
                "Actualités, équipes, presse et réseaux publics sont recoupés avant de proposer un favori.",
            ).joinToString("\n"),
            negativeArguments = listOf(
                "En cyclisme, un abandon, une chute, un changement de rôle ou la météo peuvent renverser le scénario.",
                "Sans startlist ou signal de forme robuste, l'app doit rester en surveillance plutôt que proposer un signal fragile.",
            ).joinToString("\n"),
            expectedScore = "Course prévue le $raceDate",
            statSummary = listOf(
                "Course : $raceName",
                "Date détectée : $raceDate",
                "Sources consultées : ${sources.size}",
                "Statut : surveillance jusqu'à startlist/favoris/parcours consolidés",
            ).joinToString("\n"),
            scenarios = listOf(
                "Calendrier|Course confirmée au calendrier public|1.0",
                "Point de vigilance|Signal vainqueur/podium à éviter sans startlist et favoris recoupés|0.5",
                "Déclencheur|Recalcul utile quand startlist, rôles d'équipe, parcours ou météo tombent|0.8",
            ).joinToString("\n"),
            homeLineupStatus = "",
            homeLineup = "",
            awayLineupStatus = "",
            awayLineup = "",
            playerScenarios = "",
            sourceDetails = sources.joinToString("\n"),
            contextInsights = contextLines.joinToString("\n"),
            sourceAgreement = if (sources.size >= 6) 62 else 45,
        )
        predictionDao.upsertAll(listOf(prediction))
        upcomingEventDao.setAnalysisId(target.id, prediction.id)
        onProgress(1.0, "Analyse course consolidee")
        return prediction
    }

    private fun cyclingNewsSubjects(raceName: String): List<NewsSubject> = listOf(
        NewsSubject("$raceName cyclisme startlist favoris parcours meteo", raceName, raceName, "CYCLING_RACE"),
        NewsSubject("$raceName UCI equipes coureurs engages liste officielle", raceName, raceName, "CYCLING_UCI"),
        NewsSubject("$raceName favoris forme blessure forfait chute abandon", raceName, raceName, "CYCLING_FORM"),
        NewsSubject("$raceName interview directeur sportif coureur avant course", raceName, raceName, "CYCLING_INTERVIEW"),
        NewsSubject("$raceName compte officiel Instagram Facebook X Twitter", raceName, raceName, "SOCIAL_CYCLING"),
    ).distinctBy { it.query }

    private fun tennisNewsSubjects(target: DeepAnalysisTarget, surface: String): List<NewsSubject> {
        val match = "${target.homeTeam} ${target.awayTeam}"
        val competition = target.competitionName.ifBlank { "tennis" }
        return listOf(
            NewsSubject("${target.homeTeam} tennis blessure forfait retour fatigue dernier match", target.homeTeam, target.homeTeam, "PLAYER_HEALTH"),
            NewsSubject("${target.awayTeam} tennis blessure forfait retour fatigue dernier match", target.awayTeam, target.awayTeam, "PLAYER_HEALTH"),
            NewsSubject("${target.homeTeam} ATP WTA ITF profile ranking service return aces double faults", target.homeTeam, target.homeTeam, "PLAYER_STATS"),
            NewsSubject("${target.awayTeam} ATP WTA ITF profile ranking service return aces double faults", target.awayTeam, target.awayTeam, "PLAYER_STATS"),
            NewsSubject("${target.homeTeam} Tennis Abstract Ultimate Tennis Statistics Tennis Explorer form surface", target.homeTeam, target.homeTeam, "PLAYER_STATS_PUBLIC"),
            NewsSubject("${target.awayTeam} Tennis Abstract Ultimate Tennis Statistics Tennis Explorer form surface", target.awayTeam, target.awayTeam, "PLAYER_STATS_PUBLIC"),
            NewsSubject("$match head to head tennis H2H $surface", null, match, "H2H"),
            NewsSubject("$match $competition tennis preview surface forme", null, match, "MATCH_PREVIEW"),
            NewsSubject("$competition order of play $match", null, competition, "ORDER_OF_PLAY"),
        ).distinctBy { it.query }
    }

    private fun standaloneNewsSubjects(
        sport: String,
        eventName: String,
        competition: String,
        sportTitle: String,
    ): List<NewsSubject> {
        val label = eventName.ifBlank { competition.ifBlank { sportTitle } }
        val base = sportTitle.ifBlank { sport }
        val queries = when (sport) {
            "tennis" -> listOf(
                "$label tennis surface forme fatigue blessure",
                "$label tennis service retour face a face",
                "$label tennis tirage tableau abandon forfait",
            )
            "golf" -> listOf(
                "$label golf field joueurs engages parcours meteo",
                "$label golf forme recente strokes gained cut top 10",
                "$label golf favoris forfait blessure",
            )
            "mma" -> listOf(
                "$label MMA weigh in poids forme blessure",
                "$label MMA striking grappling takedown cardio camp",
                "$label MMA methode KO submission decision prediction",
            )
            "boxing" -> listOf(
                "$label boxe weigh in poids reach forme blessure",
                "$label boxe KO TKO decision rounds cardio",
                "$label boxe camp entrainement adversaire style",
            )
            "nascar" -> listOf(
                "$label NASCAR grille qualifications essais rythme long run",
                "$label NASCAR pneus cautions meteo fiabilite pilote",
                "$label NASCAR favoris top 10 duel pilote",
            )
            "athletics" -> listOf(
                "$label athletics startlist season best personal best",
                "$label athletisme qualification finale podium meteo vent",
            )
            else -> listOf(
                "$label $base actualite forme blessure favoris",
                "$label $base statistiques recentes contexte",
                "$competition $base calendrier participants",
            )
        }
        return queries.map { NewsSubject(it, label, label, "STANDALONE_${sport.uppercase()}") }
            .distinctBy { it.query }
    }

    private fun contextLine(signal: MatchContextSignal): String {
        val source = signal.publishers.joinToString(", ").ifBlank { "source publique" }
        return "${signal.teamName} - ${signal.category}: ${signal.title} ($source)"
    }

    private fun deepNewsSubjects(
        target: DeepAnalysisTarget,
        homeProfile: TeamStatProfile,
        awayProfile: TeamStatProfile,
    ): List<NewsSubject> = buildList {
        addAll(regularNewsSubjects(
            target.homeTeam,
            target.awayTeam,
            target.competitionName,
            homeProfile,
            awayProfile,
            favorite = true,
            sportTerm = target.sportTitle,
        ))
        listOf(target.homeTeam to homeProfile, target.awayTeam to awayProfile).forEach { (team, profile) ->
            add(NewsSubject("$team entraînement vestiaire conférence de presse ${target.sportTitle}", team, team, "TEAM_DEEP"))
            add(NewsSubject("$team officiel Instagram ${target.sportTitle}", team, team, "INSTAGRAM_OFFICIAL"))
            add(NewsSubject("$team officiel Facebook ${target.sportTitle}", team, team, "FACEBOOK_OFFICIAL"))
            add(NewsSubject("$team compte officiel X Twitter ${target.sportTitle}", team, team, "X_OFFICIAL"))
            profile.coachName?.let { coach ->
                add(NewsSubject("$coach conférence de presse avant match $team", team, coach, "COACH_PRESS"))
                add(NewsSubject("$coach interview entraînement choix tactique $team", team, coach, "COACH_INTERVIEW"))
            }
            profile.playerProfiles
                .filter { it.availabilityNote == null }
                .sortedByDescending { it.starts + it.goals + it.secondaryGoals + it.assists + it.secondaryAssists }
                .take(6)
                .forEach { player ->
                    add(NewsSubject("${player.name} $team forme fatigue blessure confiance", team, player.name, "PLAYER_DEEP"))
                    add(NewsSubject("${player.name} compte officiel Instagram Facebook X Twitter", team, player.name, "PLAYER_SOCIAL"))
                }
        }
        add(NewsSubject(
            "${target.competitionName} classement enjeu qualification doit gagner actualité",
            null,
            target.competitionName,
            "COMPETITION_DEEP",
        ))
    }.distinctBy { it.query }

    suspend fun syncUpcoming(priorities: SyncPriorities = SyncPriorities()): OddsSyncResult {
        val now = System.currentTimeMillis()
        val previousPredictions = predictionDao.getUpcoming(now)
        val previousCatalogEvents = upcomingEventDao.getActive(now, now - ACTIVE_EVENT_LOOKBACK_MS)
        val retainedDeepPredictions = previousPredictions.filter { it.id.contains(":deep:") }
        val today = LocalDate.now(ZoneOffset.UTC)
        val dates = "${today.format(DateTimeFormatter.BASIC_ISO_DATE)}-${today.plusDays(365).format(DateTimeFormatter.BASIC_ISO_DATE)}"

        // Les appels sont volontairement séquentiels : certains CDN mobiles bloquent les rafales parallèles.
        // Deux noms d'hôte officiels sont essayés pour résister aux problèmes DNS ou CDN d'un opérateur.
        var successful = PUBLIC_SOURCES.mapNotNull { source ->
            val events = fetchEvents(source, dates, today) ?: return@mapNotNull null
            source to events
        }
        // La seconde source est toujours fusionnée : elle garantit un calendrier minimal
        // même si le fournisseur principal répond 200 avec des données vides selon la région.
        successful = mergeFeeds(
            successful +
                fetchBackupFeeds(today, priorities) +
                fetchTheSportsDbLeagueFeeds(today) +
                fetchVolleyballWorldFeeds(today)
        )
        if (successful.isEmpty()) {
            if (previousCatalogEvents.isNotEmpty() || previousPredictions.isNotEmpty()) {
                return OddsSyncResult(
                    eventsReceived = 0,
                    eventsCataloged = previousCatalogEvents.size,
                    eventsAnalyzed = previousPredictions.map { it.eventId }.distinct().size,
                    predictionsSaved = previousPredictions.size,
                )
            }
            throw OddsSyncException("Impossible de joindre les calendriers sportifs publics. Vérifiez la connexion Internet.")
        }

        val syncTime = System.currentTimeMillis()
        val received = successful.sumOf { it.second.size }
        val profiles = statisticsProvider.load(successful.profileRequests(now, priorities))
        val officialLineups = lineupProvider.load(successful.lineupRequests(now, priorities))
        val contextRequests = successful.newsRequests(now, profiles, priorities)
        val freshNewsContexts = newsContextProvider.load(contextRequests)
        val mergedContext = ContextSignalCache.merge(
            requests = contextRequests,
            fresh = freshNewsContexts,
            stored = contextSignalStore.loadContextSignals(),
            now = now,
        )
        contextSignalStore.saveContextSignals(mergedContext.persisted)
        val newsContexts = mergedContext.reports
        val calendarSources = successful.calendarSourceMap(now)
        val calculatedPredictions = successful.flatMap { (source, events) ->
            if (source.sport !in ANALYZABLE_SPORTS || !RemovedSports.isAllowedSportKey(source.sport)) return@flatMap emptyList()
            events.asSequence()
                .flatMap { event ->
                    runCatching {
                        event.toPublicEvents(source, now, profiles, officialLineups, newsContexts, calendarSources)
                    }.getOrElse { emptySequence() }
                }
                .sortedBy { it.commenceTime }
                .take(source.maxEvents)
                .flatMap { event ->
                    PublicPredictionEngine.analyze(event).asSequence().map { prediction ->
                        prediction.toEntity(event, syncTime)
                    }
                }
                .toList()
        }.filter { it.commenceTime > now && RemovedSports.isAllowedSportKey(it.sportKey) }
            .sortedByDescending { it.statSummary.isNotBlank() }
            .distinctBy { predictionIdentity(it) }
        val predictions = mergePredictions(
            fresh = (retainedDeepPredictions + calculatedPredictions).distinctBy { predictionIdentity(it) },
            previous = previousPredictions,
            now = now,
        ).filter { RemovedSports.isAllowedSportKey(it.sportKey) }

        val freshCatalogEvents = (
            successful.flatMap { (source, events) ->
                if (!RemovedSports.isAllowedSportKey(source.sport)) return@flatMap emptyList()
                events.mapNotNull { event ->
                    runCatching { event.toUpcomingEvent(source, now, predictions) }.getOrNull()
                }
            } + curatedMajorCalendarEvents(now, predictions)
        ).filter { RemovedSports.isAllowedSportKey(it.sportKey) }
            .sortedByDescending { it.analysisId != null }
            .distinctBy { catalogIdentity(it) }
            .sortedBy { it.commenceTime }
        val catalogEvents = mergeCatalogEvents(
            fresh = freshCatalogEvents,
            previous = previousCatalogEvents,
            now = now,
        ).filter { RemovedSports.isAllowedSportKey(it.sportKey) }

        if (freshCatalogEvents.isEmpty() && previousCatalogEvents.isEmpty()) {
            throw OddsSyncException("Les sources Internet n'ont renvoyé aucun événement exploitable. Les anciennes données ont été conservées.")
        }
        predictionDao.replaceAll(predictions.filter { RemovedSports.isAllowedSportKey(it.sportKey) })
        upcomingEventDao.replaceAll(catalogEvents.filter { RemovedSports.isAllowedSportKey(it.sportKey) })
        val prioritizeF1 = "racing" in priorities.favoriteSports ||
            priorities.favoriteCompetitions.any { it.startsWith("racing:") }
        if (prioritizeF1) {
            catalogEvents.firstOrNull { it.sportKey == "racing" && it.eventType == "GP" && it.analysisId == null }?.let { event ->
                runCatching {
                    analyzeF1Deep(
                        DeepAnalysisTarget(
                            id = event.id,
                            sportKey = event.sportKey,
                            sportTitle = event.sportTitle,
                            competitionKey = event.competitionKey,
                            competitionName = event.competitionName,
                            commenceTime = event.commenceTime,
                            homeTeam = event.eventName,
                            awayTeam = "Grille F1",
                        )
                    ) { _, _ -> }
                }
            }
        }
        syncMetadataStore.updateSyncMetadata(syncTime)
        return OddsSyncResult(
            eventsReceived = received,
            eventsCataloged = catalogEvents.size,
            eventsAnalyzed = predictions.map { it.eventId }.distinct().size,
            predictionsSaved = predictions.size,
        )
    }

    suspend fun syncLive(priorities: SyncPriorities = SyncPriorities()): OddsLiveResult {
        val now = System.currentTimeMillis()
        val today = LocalDate.now(ZoneOffset.UTC)
        val dates = "${today.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)}-${today.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)}"
        val successful = PUBLIC_SOURCES.mapNotNull { source ->
            delay(45)
            val events = fetchEvents(source, dates, today) ?: return@mapNotNull null
            source to events
        } + fetchLiveBackupFeeds(today, priorities)
        val syncTime = System.currentTimeMillis()
        val monitoredEvents = buildLiveMonitorEvents(now, syncTime, priorities)
        if (successful.isEmpty() && monitoredEvents.isEmpty()) {
            throw OddsSyncException("Impossible de joindre les flux live publics. Les derniers lives connus sont conservés.")
        }
        val received = successful.sumOf { it.second.size }
        val liveEvents = (successful
            .flatMap { (source, events) ->
                if (!RemovedSports.isAllowedSportKey(source.sport)) return@flatMap emptyList()
                events.mapNotNull { event ->
                    runCatching { event.toLiveEvent(source, now, syncTime) }.getOrNull()
                }
            }
            + monitoredEvents)
            .filter { RemovedSports.isAllowedSportKey(it.sportKey) }
            .mergeLiveDuplicates()
            .sortedWith(liveEventComparator(priorities))
        if (liveEvents.isEmpty()) {
            throw OddsSyncException("Aucun événement live exploitable pour l'instant. Les derniers lives connus sont conservés.")
        }
        liveEventDao.replaceAll(liveEvents)
        return OddsLiveResult(
            eventsReceived = received,
            eventsTracked = liveEvents.size,
            liveCount = liveEvents.count { it.isLive },
        )
    }

    private suspend fun buildLiveMonitorEvents(
        now: Long,
        syncTime: Long,
        priorities: SyncPriorities,
    ): List<LiveEventEntity> {
        val predictions = predictionDao.getUpcoming(now)
            .filter { RemovedSports.isAllowedSportKey(it.sportKey) }
            .mapNotNull { it.toLiveMonitorEvent(now, syncTime) }
        val catalogEvents = upcomingEventDao.getActive(now, now - ACTIVE_EVENT_LOOKBACK_MS)
            .filter { RemovedSports.isAllowedSportKey(it.sportKey) }
            .mapNotNull { it.toLiveMonitorEvent(now, syncTime) }
        val curatedEvents = curatedMajorLiveEvents(now, syncTime)
        return (predictions + catalogEvents + curatedEvents)
            .mergeLiveDuplicates()
            .sortedWith(liveEventComparator(priorities))
            .groupBy { it.sportKey.substringBefore('/') }
            .flatMap { (sport, events) ->
                events.take(if (sport in priorities.favoriteSports) 72 else 48)
            }
            .take(900)
    }

    private fun PredictionEntity.toLiveMonitorEvent(
        now: Long,
        syncTime: Long,
    ): LiveEventEntity? {
        if (!withinLiveMonitorWindow(commenceTime, now)) return null
        val snapshot = LiveEventSnapshot(
            sportKey = sportKey,
            sportTitle = sportTitle,
            competitionName = competitionName,
            commenceTime = commenceTime,
            homeName = homeTeam.ifBlank { selection.ifBlank { eventId } },
            awayName = awayTeam.ifBlank { competitionName },
            homeScore = null,
            awayScore = null,
            statusState = "pre",
            statusDescription = liveMonitorStatus(commenceTime, now),
            displayClock = "",
            period = null,
        )
        val projection = LiveProjectionEngine.analyze(snapshot)
        val sourceLines = buildList {
            add("Surveillance live interne")
            add("Pronostic pré-match conservé jusqu'au départ")
            add(sourceName)
            sourceDetails.lines().filter { it.isNotBlank() }.take(10).forEach(::add)
            if (contextInsights.isNotBlank()) add("Actualités/cache contexte déjà recoupés")
            add("Recalcul automatique dès qu'un score, statut ou incident public apparaît")
        }.distinct()
        val statLines = buildList {
            add("Statut live : ${liveMonitorStatus(commenceTime, now)}")
            add("Signal pré-match repris : $selection · confiance $confidenceScore/100")
            add("Probabilité pré-match : ${(consensusProbability * 100).toInt()} % · risque $riskLevel")
            addAll(projection.statSummary)
            statSummary.lines().filter { it.isNotBlank() }.take(14).forEach(::add)
            if (playerScenarios.isNotBlank()) add("Angles joueurs pré-live disponibles : buts/passes/points/essais selon le sport")
        }.distinct()
        val scenarioLines = (
            projection.scenarios.map { "${it.type}|${it.label}|${it.probability}" } +
                scenarios.lines().filter { it.isNotBlank() } +
                playerScenarios.lines().filter { it.isNotBlank() }
            ).distinct()
        return LiveEventEntity(
            id = "watch:prediction:$id",
            sportKey = sportKey,
            sportTitle = sportTitle,
            competitionName = competitionName,
            commenceTime = commenceTime,
            eventName = listOf(homeTeam, awayTeam).filter { it.isNotBlank() }.joinToString(" — ").ifBlank { selection },
            homeName = homeTeam.ifBlank { selection },
            awayName = awayTeam.ifBlank { competitionName },
            homeScore = null,
            awayScore = null,
            statusState = "pre",
            statusDescription = liveMonitorStatus(commenceTime, now),
            displayClock = "",
            period = null,
            isLive = false,
            sourceName = "Surveillance live · pré-match",
            sourceDetails = sourceLines.joinToString("\n"),
            lastUpdate = syncTime,
            statSummary = statLines.joinToString("\n"),
            scenarios = scenarioLines.joinToString("\n"),
        )
    }

    private fun UpcomingEventEntity.toLiveMonitorEvent(
        now: Long,
        syncTime: Long,
    ): LiveEventEntity? {
        if (!withinLiveMonitorWindow(commenceTime, now)) return null
        val home = participantA.ifBlank { eventName }
        val away = participantB.ifBlank { competitionName.ifBlank { sportTitle } }
        val snapshot = LiveEventSnapshot(
            sportKey = sportKey,
            sportTitle = sportTitle,
            competitionName = competitionName,
            commenceTime = commenceTime,
            homeName = home,
            awayName = away,
            homeScore = null,
            awayScore = null,
            statusState = "pre",
            statusDescription = liveMonitorStatus(commenceTime, now),
            displayClock = "",
            period = null,
        )
        val projection = LiveProjectionEngine.analyze(snapshot)
        val sourceLines = buildList {
            add("Calendrier public")
            add(sourceName)
            add("Surveillance live tous sports")
            add("Recalcul dès publication score/statut/startlist/classement")
        }.distinct()
        val statLines = buildList {
            add("Statut live : ${liveMonitorStatus(commenceTime, now)}")
            add("Événement calendrier détecté : $eventName")
            addAll(projection.statSummary)
        }.distinct()
        return LiveEventEntity(
            id = "watch:event:$id",
            sportKey = sportKey,
            sportTitle = sportTitle,
            competitionName = competitionName,
            commenceTime = commenceTime,
            eventName = eventName,
            homeName = home,
            awayName = away,
            homeScore = null,
            awayScore = null,
            statusState = "pre",
            statusDescription = liveMonitorStatus(commenceTime, now),
            displayClock = "",
            period = null,
            isLive = false,
            sourceName = "Surveillance live · calendrier",
            sourceDetails = sourceLines.joinToString("\n"),
            lastUpdate = syncTime,
            statSummary = statLines.joinToString("\n"),
            scenarios = projection.scenarios.joinToString("\n") { "${it.type}|${it.label}|${it.probability}" },
        )
    }

    private fun withinLiveMonitorWindow(
        commenceTime: Long,
        now: Long,
    ): Boolean = LiveWindowPolicy.startsSoon(commenceTime, now)

    private fun curatedMajorCalendarEvents(
        now: Long,
        predictions: List<PredictionEntity>,
    ): List<UpcomingEventEntity> = CURATED_MAJOR_EVENTS
        .asSequence()
        .filter { event -> event.commenceTime > now - liveMonitorLookbackMs(event.sportKey) }
        .map { event ->
            UpcomingEventEntity(
                id = event.id,
                sportKey = event.sportKey,
                sportTitle = event.sportTitle,
                competitionKey = competitionFavoriteKey(event.sportKey, event.competitionName),
                competitionName = event.competitionName,
                commenceTime = event.commenceTime,
                eventName = event.eventName,
                participantA = event.homeName,
                participantB = event.awayName,
                eventType = event.eventType,
                sourceName = event.sourceName,
                analysisId = predictions.firstOrNull { prediction ->
                    prediction.commenceTime == event.commenceTime &&
                        setOf(normalizeIdentity(prediction.homeTeam), normalizeIdentity(prediction.awayTeam)) ==
                        setOf(normalizeIdentity(event.homeName), normalizeIdentity(event.awayName))
                }?.id,
            )
        }
        .toList()

    private fun curatedMajorLiveEvents(
        now: Long,
        syncTime: Long,
    ): List<LiveEventEntity> = CURATED_MAJOR_EVENTS
        .asSequence()
        .filter { event -> withinLiveMonitorWindow(event.commenceTime, now) }
        .map { event ->
            val snapshot = LiveEventSnapshot(
                sportKey = event.sportKey,
                sportTitle = event.sportTitle,
                competitionName = event.competitionName,
                commenceTime = event.commenceTime,
                homeName = event.homeName,
                awayName = event.awayName,
                homeScore = null,
                awayScore = null,
                statusState = "pre",
                statusDescription = liveMonitorStatus(event.commenceTime, now),
                displayClock = "",
                period = null,
            )
            val projection = LiveProjectionEngine.analyze(snapshot)
            LiveEventEntity(
                id = "watch:curated:${event.id}",
                sportKey = event.sportKey,
                sportTitle = event.sportTitle,
                competitionName = event.competitionName,
                commenceTime = event.commenceTime,
                eventName = event.eventName,
                homeName = event.homeName,
                awayName = event.awayName,
                homeScore = null,
                awayScore = null,
                statusState = "pre",
                statusDescription = liveMonitorStatus(event.commenceTime, now),
                displayClock = "",
                period = null,
                isLive = false,
                sourceName = "Surveillance live - evenement majeur",
                sourceDetails = listOf(
                    event.sourceName,
                    "Filet de securite calendrier interne",
                    "Score/statut remplace automatiquement des qu'un flux live public confirme apparait",
                ).joinToString("\n"),
                lastUpdate = syncTime,
                statSummary = buildList {
                    add("Statut live : ${liveMonitorStatus(event.commenceTime, now)}")
                    add("Evenement majeur suivi : ${event.eventName}")
                    addAll(projection.statSummary)
                }.joinToString("\n"),
                scenarios = projection.scenarios.joinToString("\n") { "${it.type}|${it.label}|${it.probability}" },
            )
        }
        .toList()

    private fun liveMonitorStatus(commenceTime: Long, now: Long): String {
        val delta = commenceTime - now
        if (delta <= 0L) return "Départ passé · attente du flux live public"
        val minutes = (delta / 60_000L).coerceAtLeast(1L)
        return "Départ proche · dans ${minutes} min"
    }

    private fun liveEventComparator(priorities: SyncPriorities): Comparator<LiveEventEntity> =
        compareByDescending<LiveEventEntity> { it.isLive }
            .thenByDescending { it.hasLiveResultPayload() }
            .thenByDescending { it.isFavoriteLive(priorities) }
            .thenBy { it.commenceTime }
            .thenBy { it.sportTitle }

    private fun LiveEventEntity.isFavoriteLive(priorities: SyncPriorities): Boolean {
        val sport = sportKey.substringBefore('/')
        return sport in priorities.favoriteSports ||
            competitionFavoriteKey(sport, competitionName) in priorities.favoriteCompetitions
    }

    private fun EspnEventDto.toLiveEvent(source: PublicSource, now: Long, syncTime: Long): LiveEventEntity? {
        val resultBoardEvent = source.sport in RESULT_BOARD_LIVE_SPORTS
        val competition = if (resultBoardEvent) raceLiveCompetition(source.sport, now) else competitions.orEmpty().firstOrNull()
        val competitionStatus = competition?.status
        val eventStatus = this.status
        val status = if (resultBoardEvent && eventStatus?.isLiveStatus() == true && competitionStatus?.isLiveStatus() != true) {
            eventStatus
        } else {
            competitionStatus ?: eventStatus ?: liveStatus()
        }
        val statusType = status?.type
        val commence = parseInstant(competition?.date ?: competition?.startDate ?: date ?: return null) ?: return null
        val participants = competition?.competitors.orEmpty()
        val hasScorePayload = competition?.hasScorePayload() == true
        val raceTopSummary = if (resultBoardEvent && statusType?.state != "pre") competition?.raceTopSummary().orEmpty() else ""
        val teamCompletedResult = !resultBoardEvent &&
            statusType?.completed == true &&
            participants.any { it.score.asScore() != null } &&
            LiveWindowPolicy.finishedRecentlyBySchedule(source.sport, commence, now)
        val freshCompletedResult = resultBoardEvent &&
            statusType?.completed == true &&
            raceTopSummary.isNotBlank() &&
            LiveWindowPolicy.finishedRecentlyBySchedule(source.sport, commence, now)
        if (statusType?.completed == true && !freshCompletedResult && !teamCompletedResult) return null
        val tennisScoreLive = source.sport == "tennis" &&
            statusType?.completed != true &&
            hasScorePayload &&
            commence <= now &&
            commence >= now - TENNIS_LIVE_SCORE_WINDOW_MS
        val isLive = status?.isLiveStatus() == true || tennisScoreLive
        val startsSoon = LiveWindowPolicy.startsSoon(commence, now)
        if (
            !isLive &&
            !startsSoon &&
            !freshCompletedResult &&
            !teamCompletedResult
        ) return null
        val home = participants.firstOrNull { it.homeAway.equals("home", true) } ?: participants.getOrNull(0)
        val away = participants.firstOrNull { it.homeAway.equals("away", true) } ?: participants.getOrNull(1)
        val eventDisplayName = displayNameForStandalone() ?: name ?: shortName ?: source.title
        val sessionLabel = if (resultBoardEvent) competition?.sessionLabel().orEmpty() else ""
        val homeName = if (resultBoardEvent) {
            eventDisplayName
        } else {
            home?.team?.displayName ?: home?.athlete?.displayName ?: eventDisplayName
        }
        val awayName = if (resultBoardEvent) {
            sessionLabel.ifBlank { source.title }
        } else {
            away?.team?.displayName ?: away?.athlete?.displayName ?: source.title
        }
        val statusCandidate = listOfNotNull(
            statusType?.shortDetail,
            statusType?.detail,
            statusType?.description,
            statusType?.name,
        ).firstOrNull { value -> value.isNotBlank() }
        val rawStatusDescription = when {
            tennisScoreLive && statusCandidate.orEmpty().contains("scheduled", ignoreCase = true) -> "En direct · score tennis public"
            tennisScoreLive && statusCandidate.isNullOrBlank() -> "En direct · score tennis public"
            else -> statusCandidate ?: if (isLive) "En direct" else "À surveiller"
        }
        val statusDescription = if (sessionLabel.isNotBlank() && !rawStatusDescription.contains(sessionLabel, ignoreCase = true)) {
            "$sessionLabel · $rawStatusDescription"
        } else {
            rawStatusDescription
        }
        val statusState = when {
            isLive && statusType?.state.isNullOrBlank() -> "in"
            isLive && statusType?.state.equals("pre", true) -> "in"
            else -> statusType?.state.orEmpty()
        }
        val homeScore = if (resultBoardEvent) null else home?.score.asScore()
        val awayScore = if (resultBoardEvent) null else away?.score.asScore()
        val snapshot = LiveEventSnapshot(
            sportKey = "${source.sport}/${source.league}",
            sportTitle = source.title,
            competitionName = competitionName(source),
            commenceTime = commence,
            homeName = homeName,
            awayName = awayName,
            homeScore = homeScore,
            awayScore = awayScore,
            statusState = statusState,
            statusDescription = statusDescription,
            displayClock = status?.displayClock.orEmpty(),
            period = status?.period,
            resultSummary = raceTopSummary,
        )
        val projection = LiveProjectionEngine.analyze(snapshot)
        val realTimeStats = participants.flatMap { participant ->
            val participantName = participant.team?.displayName ?: participant.athlete?.displayName ?: return@flatMap emptyList()
            participant.statistics.orEmpty().mapNotNull { stat ->
                val label = stat.displayName ?: stat.shortDisplayName ?: stat.abbreviation ?: stat.name ?: return@mapNotNull null
                val value = stat.displayValue ?: stat.value?.toString() ?: return@mapNotNull null
                "$participantName · $label : $value"
            }
        }.take(40)
        val tennisSetStats = if (source.sport == "tennis") {
            participants.mapNotNull { participant ->
                val participantName = participant.team?.displayName ?: participant.athlete?.displayName ?: return@mapNotNull null
                val sets = participant.linescores.orEmpty()
                    .mapIndexedNotNull { index, line -> line.cleanLineScore()?.let { "S${index + 1} $it" } }
                if (sets.isEmpty()) null else "$participantName · ${sets.joinToString(" · ")}"
            }
        } else {
            emptyList()
        }
        val sourceDetails = buildList {
            add(source.sourceName)
            if (source.sourceName.contains("TheSportsDB", ignoreCase = true)) {
                add("TheSportsDB score/statut public")
            } else {
                add("Scoreboard ESPN public")
            }
            if (raceTopSummary.isNotBlank()) add("Classement/top 3 public")
            if (realTimeStats.isNotEmpty()) add("Statistiques live publiques")
            if (tennisSetStats.isNotEmpty()) add("Scores par set publics")
            competition?.odds.safeOdds().mapNotNull { it.provider?.name }.distinct().forEach { add("Marchés publics $it") }
        }.distinct()
        val resultLines = buildList {
            if (resultBoardEvent) {
                if (sessionLabel.isNotBlank()) add("Session suivie : $sessionLabel")
                resultBoardProgressSummary(
                    sport = source.sport,
                    sessionLabel = sessionLabel,
                    statusDescription = statusDescription,
                    displayClock = status?.displayClock.orEmpty(),
                    period = status?.period,
                    isLive = isLive,
                    competition = competition,
                )?.let { add("Avancement course : $it") }
                if (raceTopSummary.isNotBlank()) add("Top 3 / classement public : $raceTopSummary")
                else add("Top 3 / classement : attente du classement officiel public")
            }
        }
        return LiveEventEntity(
            id = "live:${source.sport}-${source.league}:$id",
            sportKey = "${source.sport}/${source.league}",
            sportTitle = source.title,
            competitionName = competitionName(source),
            commenceTime = commence,
            eventName = eventDisplayName,
            homeName = homeName,
            awayName = awayName,
            homeScore = homeScore,
            awayScore = awayScore,
            statusState = statusState,
            statusDescription = statusDescription,
            displayClock = status?.displayClock.orEmpty(),
            period = status?.period,
            isLive = isLive,
            sourceName = if (sourceDetails.size >= 2) "Live multi-signaux · ${sourceDetails.size} points" else source.sourceName,
            sourceDetails = sourceDetails.joinToString("\n"),
            lastUpdate = syncTime,
            statSummary = (resultLines + projection.statSummary + tennisSetStats + realTimeStats).joinToString("\n"),
            scenarios = projection.scenarios.joinToString("\n") { "${it.type}|${it.label}|${it.probability}" },
        )
    }

    private fun EspnEventDto.liveStatus(): EspnStatusDto? =
        competitions.orEmpty().firstNotNullOfOrNull { it.status } ?: status

    private fun EspnEventDto.raceLiveCompetition(sport: String, now: Long): EspnCompetitionDto? {
        val available = competitions.orEmpty()
            .filter { it.commenceMillis() != null }
        if (available.isEmpty()) return null

        return available.firstOrNull { it.status?.isLiveStatus() == true }
            ?: available
                .filter { competition ->
                    competition.status?.type?.completed == true &&
                        competition.raceTopSummary().orEmpty().isNotBlank() &&
                        competition.commenceMillis()?.let { LiveWindowPolicy.finishedRecentlyBySchedule(sport, it, now) } == true
                }
                .maxByOrNull { it.commenceMillis() ?: 0L }
            ?: available
                .filter { competition ->
                    competition.status?.type?.completed != true &&
                        competition.commenceMillis()?.let { LiveWindowPolicy.startsSoon(it, now) } == true
                }
                .minByOrNull { competition ->
                    val delta = (competition.commenceMillis() ?: now) - now
                    if (delta >= 0) delta else -delta
                }
    }

    private fun EspnStatusDto.isLiveStatus(): Boolean {
        val raw = listOfNotNull(type?.state, type?.name, type?.description, type?.detail, type?.shortDetail)
            .joinToString(" ")
            .lowercase()
        return type?.completed != true && (
            type?.state.equals("in", true) ||
                "in_progress" in raw ||
                "in progress" in raw ||
                "live" in raw ||
                "1st set" in raw ||
                "2nd set" in raw ||
                "3rd set" in raw ||
                "4th set" in raw ||
                "5th set" in raw ||
                "halftime" in raw ||
                (clock != null && clock > 0.0 && !type?.state.equals("pre", true))
            )
    }

    private fun EspnCompetitionDto.hasScorePayload(): Boolean =
        competitors.orEmpty().any { competitor ->
            competitor.score.asScore() != null ||
                competitor.linescores.orEmpty().any { it.hasPayload() }
        }

    private fun EspnLineScoreDto.hasPayload(): Boolean =
        value != null ||
            displayValue?.isNotBlank() == true ||
            linescores.orEmpty().any { it.hasPayload() }

    private fun EspnLineScoreDto.cleanLineScore(): String? =
        displayValue?.takeIf { it.isNotBlank() }
            ?: value?.let { lineValue ->
                if (lineValue % 1.0 == 0.0) lineValue.toInt().toString() else lineValue.toString()
            }

    private fun String?.asScore(): Int? = this?.substringBefore('.')?.toIntOrNull()

    private fun EspnCompetitionDto.commenceMillis(): Long? {
        val raw = date ?: startDate ?: return null
        return parseInstant(raw)
    }

    private fun EspnCompetitionDto.sessionLabel(): String {
        val abbreviation = type?.abbreviation?.takeIf { it.isNotBlank() } ?: return ""
        return when (abbreviation.lowercase()) {
            "fp1" -> "Essais libres 1"
            "fp2" -> "Essais libres 2"
            "fp3" -> "Essais libres 3"
            "qual", "quali", "qualification", "qualifying" -> "Qualifications"
            "sprint" -> "Sprint"
            "race" -> "Course"
            else -> abbreviation
        }
    }

    private fun resultBoardProgressSummary(
        sport: String,
        sessionLabel: String,
        statusDescription: String,
        displayClock: String,
        period: Int?,
        isLive: Boolean,
        competition: EspnCompetitionDto?,
    ): String? {
        val clock = displayClock.trim().takeIf { it.isNotBlank() && it != "0:00" }
        val golfProgress = if (sport == "golf") competition?.golfProgressSummary() else null
        val periodLabel = period
            ?.takeIf { it > 0 && isLive }
            ?.let { resultBoardPeriodLabel(sport, it) }
        val status = statusDescription.trim().takeIf { it.isNotBlank() }
        val parts = listOfNotNull(sessionLabel.takeIf { it.isNotBlank() }, golfProgress, periodLabel, clock, status)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        return parts.joinToString(" · ").takeIf { it.isNotBlank() }
    }

    private fun EspnCompetitionDto.golfProgressSummary(): String? {
        val roundRows = competitors.orEmpty().flatMap { it.linescores.orEmpty() }
        if (roundRows.isEmpty()) return null
        val playedRounds = roundRows.filter { it.value != null || it.displayValue?.isNotBlank() == true }
        val latestPlayedRound = playedRounds.mapNotNull { it.period }.maxOrNull()
        val latestRoundHoles = playedRounds
            .filter { it.period == latestPlayedRound }
            .flatMap { it.linescores.orEmpty() }
            .mapNotNull { it.period }
            .maxOrNull()
        val nextRound = roundRows
            .mapNotNull { it.period }
            .filter { latestPlayedRound == null || it > latestPlayedRound }
            .minOrNull()
        val parts = buildList {
            latestPlayedRound?.let { add("round $it") }
            latestRoundHoles?.let { add("$it trous joues") }
            nextRound?.let { add("round $it a venir") }
        }
        return parts.joinToString(" · ").takeIf { it.isNotBlank() }
    }

    private fun resultBoardPeriodLabel(sport: String, period: Int): String =
        when (sport) {
            "racing", "nascar" -> "tour $period"
            "golf" -> "round $period"
            "cycling" -> "section $period"
            "athletics" -> "serie $period"
            else -> "periode $period"
        }

    private fun EspnCompetitionDto.raceTopSummary(limit: Int = 3): String? {
        val ranked = competitors.orEmpty()
            .mapIndexedNotNull { index, competitor ->
                val name = competitor.resultBoardName().takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                val position = competitor.order?.takeIf { it > 0 } ?: (index + 1)
                RaceStanding(position, name, competitor.score.orEmpty(), competitor.winner == true)
            }
            .sortedBy { it.position }
            .take(limit)
        if (ranked.isEmpty()) return null
        val prefix = sessionLabel().takeIf { it.isNotBlank() }?.let { "$it : " }.orEmpty()
        return prefix + ranked.joinToString(" · ") { standing ->
            buildString {
                append("${standing.position}. ${standing.name}")
                if (standing.score.isNotBlank()) append(" (${standing.score})")
                if (standing.winner) append(" ✓")
            }
        }
    }

    private fun EspnCompetitorDto.resultBoardName(): String =
        team?.displayName ?: athlete?.displayName.orEmpty()

    private fun liveEventKeepMs(sport: String): Long = when (sport) {
        "soccer", "rugby", "football" -> TEAM_MATCH_KEEP_MS
        "basketball", "handball", "volleyball", "hockey" -> TEAM_MATCH_KEEP_MS
        "baseball", "tennis", "mma", "boxing" -> 5L * 60 * 60 * 1000
        "cycling", "racing", "nascar", "golf", "athletics" -> RACE_RESULT_KEEP_MS
        else -> LIVE_EVENT_KEEP_MS
    }

    private fun liveMonitorLookbackMs(sport: String): Long = when (sport) {
        "soccer", "rugby", "football" -> TEAM_MATCH_KEEP_MS
        "basketball", "handball", "volleyball", "hockey" -> TEAM_MATCH_KEEP_MS
        "baseball", "tennis", "mma", "boxing" -> 5L * 60 * 60 * 1000
        "cycling", "racing", "nascar", "golf", "athletics" -> RACE_RESULT_KEEP_MS
        else -> LIVE_EVENT_KEEP_MS
    }

    private fun List<LiveEventEntity>.mergeLiveDuplicates(): List<LiveEventEntity> =
        groupBy(::liveIdentity).values.map { group ->
            val best = group.maxWith(
                compareBy<LiveEventEntity> { it.isLive }
                    .thenBy { it.homeScore != null && it.awayScore != null }
                    .thenBy { it.hasLiveResultPayload() }
                    .thenBy { it.statSummary.length }
                    .thenBy { it.sourceDetails.lines().count { line -> line.isNotBlank() } }
            )
            val allSources = group.flatMap { it.sourceDetails.lines() + it.sourceName }
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("Live multi-", ignoreCase = true) }
                .distinct()
            val allStats = group.flatMap { it.statSummary.lines() }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            val allScenarios = group.flatMap { it.scenarios.lines() }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            best.copy(
                sourceName = if (allSources.size >= 2) "Live multi-sources · ${allSources.size} signaux" else best.sourceName,
                sourceDetails = allSources.joinToString("\n"),
                statSummary = allStats.joinToString("\n"),
                scenarios = allScenarios.joinToString("\n"),
            )
        }

    private fun liveIdentity(value: LiveEventEntity): String = buildString {
        append(value.sportKey.substringBefore('/'))
        append('|')
        val participants = listOf(value.homeName, value.awayName).filter { it.isNotBlank() && it != value.sportTitle }
        append(if (participants.isNotEmpty()) participants.map(::normalizeIdentity).sorted().joinToString("-") else normalizeIdentity(value.eventName))
        append('|')
        append(value.commenceTime / (6 * 60 * 60 * 1000L))
    }

    private fun LiveEventEntity.hasLiveResultPayload(): Boolean =
        homeScore != null && awayScore != null ||
            statSummary.lines().any { line ->
                val normalized = line.livePayloadKey()
                normalized.startsWith("top 3 / classement public") ||
                    normalized.startsWith("classement public") ||
                    normalized.startsWith("resultat course") ||
                    normalized.startsWith("résultat course")
            }

    private fun String.livePayloadKey(): String =
        lowercase()
            .replace('\u00e9', 'e')
            .replace('\u00e8', 'e')
            .replace('\u00ea', 'e')
            .replace('\u00eb', 'e')
            .replace('\u00e0', 'a')
            .replace('\u00e2', 'a')
            .replace('\u00f9', 'u')
            .replace('\u00fb', 'u')
            .replace('\u00ee', 'i')
            .replace('\u00ef', 'i')
            .replace('\u00f4', 'o')
            .replace('\u00e7', 'c')

    private fun EspnEventDto.toUpcomingEvent(
        source: PublicSource,
        now: Long,
        predictions: List<PredictionEntity>,
    ): UpcomingEventEntity? {
        if (status?.type?.completed == true) return null
        val commence = parseInstant(date ?: return null) ?: return null
        val lookback = if (effectiveEventType(source) == "MATCH") 0L else ACTIVE_EVENT_LOOKBACK_MS
        if (commence <= now - lookback) return null

        val competition = competitionName(source)
        val catalogId = "${source.sport}-${source.league}:$id"
        val participants = competitions.orEmpty()
            .firstOrNull { it.competitors.orEmpty().size >= 2 }
            ?.competitors.orEmpty()
        val participantA = participants.firstOrNull { it.homeAway.equals("home", true) }
            ?: participants.getOrNull(0)
        val participantB = participants.firstOrNull { it.homeAway.equals("away", true) }
            ?: participants.getOrNull(1)
        val nameA = participantA?.team?.displayName ?: participantA?.athlete?.displayName.orEmpty()
        val nameB = participantB?.team?.displayName ?: participantB?.athlete?.displayName.orEmpty()
        val displayName = name ?: shortName ?: listOf(nameA, nameB).filter { it.isNotBlank() }.joinToString(" — ")
        if (displayName.isBlank()) return null

        return UpcomingEventEntity(
            id = catalogId,
            sportKey = source.sport,
            sportTitle = source.title,
            competitionKey = competitionKey(source, competition),
            competitionName = competition,
            commenceTime = commence,
            eventName = displayName,
            participantA = nameA,
            participantB = nameB,
            eventType = effectiveEventType(source),
            sourceName = source.sourceName,
            analysisId = predictions.firstOrNull { it.eventId.startsWith(catalogId) }?.id,
        )
    }

    private fun EspnEventDto.effectiveEventType(source: PublicSource): String = eventTypeOverride ?: source.eventType

    private suspend fun fetchEvents(source: PublicSource, dates: String, today: LocalDate): List<EspnEventDto>? {
        if (source.sport == "cycling") {
            return CyclingCalendarProvider(api).load(today)
        }
        val path = "apis/site/v2/sports/${source.apiSport}/${source.league}/scoreboard?dates=$dates&limit=500"
        val hosts = listOf(preferredHost, "https://site.web.api.espn.com/", "https://site.api.espn.com/").distinct()
        var successfulEmptyResponse = false
        hosts.forEach { host ->
            val url = host + path
            val response = runCatching { api.getScoreboardUrl(url) }.getOrNull()
            if (response != null && response.isSuccessful) {
                val events = response.body()?.events.orEmpty().expandedScoreboardEvents(source)
                if (events.isNotEmpty()) {
                    preferredHost = host
                    return events
                }
                successfulEmptyResponse = true
            }
            val raw = runCatching { api.getRawUrl(url) }.getOrNull()
            if (raw != null && raw.isSuccessful) {
                val events = raw.body()?.string()?.let(EspnFallbackParser::parse).orEmpty().expandedScoreboardEvents(source)
                if (events.isNotEmpty()) {
                    preferredHost = host
                    return events
                }
                successfulEmptyResponse = true
            }
        }
        return if (successfulEmptyResponse) emptyList() else null
    }

    private fun List<EspnEventDto>.expandedScoreboardEvents(source: PublicSource): List<EspnEventDto> {
        if (source.sport != "tennis") return this
        return flatMap { event ->
            val tournamentName = event.name ?: event.shortName
            val matchEvents = event.groupings.orEmpty()
                .flatMap groupings@{ grouping ->
                    val groupingName = grouping.grouping?.displayName.orEmpty()
                    if (!source.isAllowedTennisGrouping(groupingName)) return@groupings emptyList()
                    grouping.competitions.orEmpty().mapNotNull { competition ->
                        val names = competition.competitorNames()
                        if (names.size < 2 || names.any(::isPlaceholderTennisParticipant)) return@mapNotNull null
                        val matchName = names.joinToString(" — ")
                        val round = competition.round?.displayName?.takeIf { it.isNotBlank() }
                        event.copy(
                            id = listOf(event.id, competition.id ?: competition.uid ?: matchName).joinToString("-"),
                            uid = competition.uid ?: event.uid,
                            date = competition.date ?: competition.startDate ?: event.date,
                            name = matchName,
                            shortName = matchName,
                            competitions = listOf(competition),
                            status = competition.status ?: event.status,
                            tournamentName = listOfNotNull(tournamentName, grouping.grouping?.displayName, round)
                                .distinct()
                                .joinToString(" · ")
                                .ifBlank { tournamentName },
                            eventTypeOverride = "MATCH",
                        )
                    }
                }
            matchEvents.ifEmpty {
                val directCompetition = event.competitions.orEmpty().firstOrNull { competition ->
                    val names = competition.competitorNames()
                    names.size >= 2 && names.none(::isPlaceholderTennisParticipant)
                }
                if (directCompetition == null) {
                    listOf(event.copy(tournamentName = tournamentName, eventTypeOverride = "TOURNAMENT"))
                } else {
                    val names = directCompetition.competitorNames()
                    val matchName = names.take(2).joinToString(" — ")
                    listOf(
                        event.copy(
                            name = matchName,
                            shortName = matchName,
                            competitions = listOf(directCompetition),
                            status = directCompetition.status ?: event.status,
                            tournamentName = tournamentName,
                            eventTypeOverride = "MATCH",
                        )
                    )
                }
            }
        }
    }

    private fun PublicSource.isAllowedTennisGrouping(groupingName: String): Boolean {
        val normalized = groupingName.lowercase()
        return when (league) {
            "atp" -> "men" in normalized && "women" !in normalized
            "wta" -> "women" in normalized
            else -> true
        }
    }

    private fun EspnCompetitionDto.competitorNames(): List<String> =
        competitors.orEmpty()
            .sortedWith(compareBy<EspnCompetitorDto> { it.order ?: Int.MAX_VALUE })
            .mapNotNull { it.team?.displayName ?: it.athlete?.displayName }
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun isPlaceholderTennisParticipant(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized == "tbd" ||
            normalized == "bye" ||
            normalized == "qualifier" ||
            normalized.startsWith("winner ") ||
            normalized.startsWith("unknown")
    }

    private suspend fun fetchLiveBackupFeeds(today: LocalDate, priorities: SyncPriorities): List<Pair<PublicSource, List<EspnEventDto>>> {
        val defaultSports = listOf(
            "Soccer",
            "Basketball",
            "Baseball",
            "Rugby",
            "Tennis",
            "Handball",
            "Volleyball",
            "Ice Hockey",
            "American Football",
            "Golf",
            "Cycling",
            "MMA",
            "Boxing",
            "Athletics",
            "Motorsport",
        )
        val favoriteSports = priorities.favoriteSports.mapNotNull(::externalQuerySport)
        val external = mutableListOf<com.soliano.betvalueanalyzer.data.remote.ExternalScheduleEvent>()
        val requests = buildList {
            (-1L..1L).forEach { dayOffset ->
                defaultSports.forEach { sport -> add(dayOffset to sport) }
                favoriteSports.forEach { sport -> add(dayOffset to sport) }
            }
        }.distinct()
        requests.forEach { (dayOffset, sport) ->
            delay(35)
            val date = today.plusDays(dayOffset).toString()
            val url = "https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=$date&s=${sport.replace(" ", "%20")}"
            val response = runCatching { api.getRawUrl(url) }.getOrNull()
            if (response != null && response.isSuccessful) {
                response.body()?.string()?.let(TheSportsDbFallbackParser::parse)?.let(external::addAll)
            }
        }
        return external.groupBy { "${it.sport}:${it.leagueId}" }.values.mapNotNull { group ->
            val first = group.firstOrNull() ?: return@mapNotNull null
            val mapping = externalSport(first.sport) ?: return@mapNotNull null
            val source = PublicSource(
                sport = mapping.first,
                league = "live-${first.leagueId}",
                title = mapping.second,
                maxEvents = 50,
                competitionName = first.leagueName,
                eventType = externalEventType(mapping.first),
                sourceName = "TheSportsDB live public",
            )
            source to group.map { item -> item.toEspnEventDto("tsdb-live") }
        }
    }

    private suspend fun fetchBackupFeeds(today: LocalDate, priorities: SyncPriorities): List<Pair<PublicSource, List<EspnEventDto>>> {
        val external = mutableListOf<com.soliano.betvalueanalyzer.data.remote.ExternalScheduleEvent>()
        val defaultSports = listOf(
            "Soccer",
            "Basketball",
            "Baseball",
            "Rugby",
            "Tennis",
            "Handball",
            "Volleyball",
            "Ice Hockey",
            "American Football",
            "Golf",
            "Cycling",
            "MMA",
            "Boxing",
            "Athletics",
            "Motorsport",
        )
        val favoriteSports = priorities.favoriteSports.mapNotNull(::externalQuerySport)
        val requests = buildList {
            (0L..2L).forEach { dayOffset ->
                defaultSports.forEach { sport -> add(dayOffset to sport) }
            }
            (0L..6L).forEach { dayOffset ->
                favoriteSports.forEach { sport -> add(dayOffset to sport) }
            }
        }.distinct()
        requests.forEach { (dayOffset, sport) ->
            delay(55)
            val date = today.plusDays(dayOffset).toString()
            run {
                val url = "https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=$date&s=${sport.replace(" ", "%20")}"
                val response = runCatching { api.getRawUrl(url) }.getOrNull()
                if (response != null && response.isSuccessful) {
                    response.body()?.string()?.let(TheSportsDbFallbackParser::parse)?.let(external::addAll)
                }
            }
        }
        return external.groupBy { "${it.sport}:${it.leagueId}" }.values.mapNotNull { group ->
            val first = group.firstOrNull() ?: return@mapNotNull null
            val mapping = externalSport(first.sport) ?: return@mapNotNull null
            val source = PublicSource(
                sport = mapping.first,
                league = "backup-${first.leagueId}",
                title = mapping.second,
                maxEvents = 50,
                competitionName = first.leagueName,
                eventType = externalEventType(mapping.first),
                sourceName = "TheSportsDB calendrier public",
            )
            source to group.map { item -> item.toEspnEventDto("backup") }
        }
    }

    private fun ExternalScheduleEvent.toEspnEventDto(prefix: String): EspnEventDto {
        val status = toEspnStatus()
        val (resolvedHome, resolvedAway) = resolvedParticipants()
        return EspnEventDto(
            id = "$prefix-$id",
            uid = "$prefix:$id",
            date = timestamp,
            name = eventName,
            shortName = eventName,
            competitions = listOf(
                EspnCompetitionDto(
                    id = id,
                    date = timestamp,
                    competitors = listOf(
                        EspnCompetitorDto(
                            id = homeTeamId,
                            homeAway = "home",
                            team = EspnParticipantDto(resolvedHome),
                            score = homeScore?.toString(),
                        ),
                        EspnCompetitorDto(
                            id = awayTeamId,
                            homeAway = "away",
                            team = EspnParticipantDto(resolvedAway),
                            score = awayScore?.toString(),
                        ),
                    ),
                    odds = emptyList(),
                    status = status,
                )
            ),
            status = status,
        )
    }

    private fun ExternalScheduleEvent.resolvedParticipants(): Pair<String, String> {
        if (homeTeam.isNotBlank() && awayTeam.isNotBlank()) return homeTeam to awayTeam
        val inferred = inferParticipantsFromEventName(eventName)
        return homeTeam.ifBlank { inferred.first } to awayTeam.ifBlank { inferred.second }
    }

    private fun inferParticipantsFromEventName(value: String): Pair<String, String> {
        val parts = value.split(Regex("\\s+(?:vs\\.?|v)\\s+", RegexOption.IGNORE_CASE), limit = 2)
        if (parts.size < 2) return "" to ""
        return trimCompetitionPrefix(parts[0]) to parts[1].trim()
    }

    private fun trimCompetitionPrefix(value: String): String {
        val markers = listOf(
            " international ",
            " open ",
            " classic ",
            " masters ",
            " championships ",
            " championship ",
            " cup ",
            " invitational ",
            " finals ",
        )
        val padded = " ${value.trim()} "
        val lower = padded.lowercase()
        val marker = markers.mapNotNull { marker ->
            val index = lower.lastIndexOf(marker)
            if (index >= 0) index + marker.length to marker else null
        }.maxByOrNull { it.first }
        return marker?.let { padded.substring(it.first).trim() }
            ?.takeIf { it.isNotBlank() }
            ?: value.trim().split(Regex("\\s+")).takeLast(2).joinToString(" ")
    }

    private fun ExternalScheduleEvent.toEspnStatus(): EspnStatusDto {
        val raw = listOfNotNull(status, progress).joinToString(" ").lowercase()
        val completed = raw.contains("ft") ||
            raw.contains("finished") ||
            raw.contains("match finished") ||
            raw.contains("full time") ||
            raw.contains("final")
        val live = !completed && (
            raw.contains("live") ||
                raw.contains("in progress") ||
                raw.contains("playing") ||
                Regex("\\b[1-5](?:st|nd|rd|th) set\\b").containsMatchIn(raw) ||
                raw.contains("half") ||
                Regex("\\b\\d{1,3}'\\b").containsMatchIn(raw)
            )
        val state = when {
            completed -> "post"
            live -> "in"
            else -> "pre"
        }
        val description = status ?: progress ?: if (live) "En direct" else "Scheduled"
        return EspnStatusDto(
            displayClock = progress,
            type = EspnStatusTypeDto(
                completed = completed,
                state = state,
                name = if (live) "STATUS_IN_PROGRESS" else if (completed) "STATUS_FINAL" else "STATUS_SCHEDULED",
                description = description,
                detail = description,
                shortDetail = description,
            ),
        )
    }

    private suspend fun fetchTheSportsDbLeagueFeeds(today: LocalDate): List<Pair<PublicSource, List<EspnEventDto>>> {
        val maxDate = today.plusDays(365)
        return THE_SPORTS_DB_LEAGUES.mapNotNull { league ->
            if (!RemovedSports.isAllowedSportKey(league.sport)) return@mapNotNull null
            val external = fetchTheSportsDbLeagueEvents(league, today, maxDate)
                .filter { item ->
                    val date = parseInstant(item.timestamp)?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    date != null && date in today..maxDate
                }
                .distinctBy { it.id }
            if (external.isEmpty()) return@mapNotNull null
            val source = PublicSource(
                sport = league.sport,
                league = "tsdb-${league.id}",
                title = league.title,
                maxEvents = league.maxEvents,
                competitionName = league.competitionName,
                eventType = league.eventType,
                dynamicCompetition = false,
                sourceName = "TheSportsDB public",
            )
            source to external.map { item -> item.toEspnEventDto("tsdb-${league.id}") }
        }
    }

    private suspend fun fetchVolleyballWorldFeeds(today: LocalDate): List<Pair<PublicSource, List<EspnEventDto>>> {
        val maxDate = today.plusDays(365)
        val from = today.format(DateTimeFormatter.ISO_DATE)
        val to = maxDate.format(DateTimeFormatter.ISO_DATE)
        val url = "https://en.volleyballworld.com/api/v1/volley-tournament/$from/$to/1661;1662"
        val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: return emptyList()
        if (!response.isSuccessful) return emptyList()
        val external = response.body()?.string()
            ?.let(VolleyballWorldFallbackParser::parse)
            .orEmpty()
            .filter { item ->
                val date = parseInstant(item.timestamp)?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                date != null && date in today..maxDate
            }
            .distinctBy { it.id }
        if (external.isEmpty()) return emptyList()
        val source = PublicSource(
            sport = "volleyball",
            league = "volleyball-world-vnl",
            title = "Volley-ball",
            maxEvents = 180,
            competitionName = "Volleyball Nations League",
            eventType = "MATCH",
            dynamicCompetition = false,
            sourceName = "Volleyball World officiel",
        )
        return listOf(source to external.map { item -> item.toEspnEventDto("volleyball-world") })
    }

    private suspend fun fetchTheSportsDbLeagueEvents(
        league: ExternalLeagueSource,
        today: LocalDate,
        maxDate: LocalDate,
    ): List<ExternalScheduleEvent> {
        val urls = buildList {
            add("https://www.thesportsdb.com/api/v1/json/123/eventsnextleague.php?id=${league.id}")
            if (league.sport in SPORTS_DB_SEASON_EXPANDED_SPORTS) {
                sportsDbSeasonCandidates(today).forEach { season ->
                    add("https://www.thesportsdb.com/api/v1/json/123/eventsseason.php?id=${league.id}&s=$season")
                }
            }
        }.distinct()
        val output = mutableListOf<ExternalScheduleEvent>()
        for (url in urls) {
            delay(450)
            val response = runCatching { api.getRawUrl(url) }.getOrNull() ?: continue
            if (!response.isSuccessful) continue
            val events = response.body()?.string()
                ?.let(TheSportsDbFallbackParser::parse)
                .orEmpty()
                .filter { item ->
                    val date = parseInstant(item.timestamp)?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    date != null && date in today..maxDate
                }
            output += events
        }
        return output.distinctBy { it.id }
    }

    private fun sportsDbSeasonCandidates(today: LocalDate): List<String> {
        val year = today.year
        return listOf(
            year.toString(),
            "${year - 1}-$year",
            "$year-${year + 1}",
        ).distinct()
    }

    private fun externalSport(value: String): Pair<String, String>? = when (value.lowercase()) {
        "soccer" -> "soccer" to "Football"
        "basketball" -> "basketball" to "Basketball"
        "baseball" -> "baseball" to "Baseball"
        "rugby" -> "rugby" to "Rugby"
        "tennis" -> "tennis" to "Tennis"
        "handball" -> "handball" to "Handball"
        "volleyball" -> "volleyball" to "Volley-ball"
        "ice hockey", "hockey" -> "hockey" to "Hockey sur glace"
        "american football" -> "football" to "Football américain"
        "golf" -> "golf" to "Golf"
        "cycling" -> "cycling" to "Cyclisme"
        "athletics" -> "athletics" to "Athlétisme"
        "motorsport", "motor sport", "formula 1" -> "racing" to "Sports mécaniques"
        "nascar" -> "nascar" to "NASCAR"
        "mma", "mixed martial arts" -> "mma" to "MMA"
        "boxing" -> "boxing" to "Boxe"
        else -> null
    }

    private fun externalQuerySport(value: String): String? = when (value.substringBefore('/')) {
        "soccer" -> "Soccer"
        "basketball" -> "Basketball"
        "baseball" -> "Baseball"
        "rugby" -> "Rugby"
        "tennis" -> "Tennis"
        "handball" -> "Handball"
        "volleyball" -> "Volleyball"
        "hockey" -> "Ice Hockey"
        "football" -> "American Football"
        "golf" -> "Golf"
        "cycling" -> "Cycling"
        "athletics" -> "Athletics"
        "racing", "nascar" -> "Motorsport"
        "mma" -> "MMA"
        "boxing" -> "Boxing"
        else -> null
    }

    private fun externalEventType(sport: String): String = when (sport.substringBefore('/')) {
        "cycling" -> "RACE"
        "racing" -> "GP"
        "golf" -> "TOURNAMENT"
        "tennis" -> "MATCH"
        "mma", "boxing", "athletics" -> "EVENT"
        else -> "MATCH"
    }

    private fun mergeFeeds(feeds: List<Pair<PublicSource, List<EspnEventDto>>>): List<Pair<PublicSource, List<EspnEventDto>>> =
        feeds.groupBy { "${it.first.sport}/${it.first.league}" }.values.map { matches ->
            matches.first().first to matches.flatMap { it.second }.distinctBy { it.id }
        }

    private fun List<Pair<PublicSource, List<EspnEventDto>>>.profileRequests(
        now: Long,
        priorities: SyncPriorities,
    ): List<TeamProfileRequest> = flatMap { (source, events) ->
        if (source.sport !in PROFILE_SPORTS) return@flatMap emptyList()
        events.mapNotNull { event ->
            val commence = event.date?.let(::parseInstant) ?: return@mapNotNull null
            if (commence <= now) return@mapNotNull null
            EnrichmentCandidate(source, event, commence, eventPriority(source, event, priorities))
        }
    }.sortedWith(compareByDescending<EnrichmentCandidate> { it.priority }.thenBy { it.commence })
        .distinctBy { candidateIdentity(it) }
        .take(MAX_ENRICHED_MATCHES)
        .flatMap { candidate ->
            candidate.event.competitions.orEmpty().firstOrNull()?.competitors.orEmpty().take(2).mapNotNull { competitor ->
                val name = competitor.team?.displayName ?: competitor.athlete?.displayName ?: return@mapNotNull null
                TeamProfileRequest(
                    sport = candidate.source.sport,
                    league = if (candidate.source.sport == "soccer") candidate.event.competitionName(candidate.source) else candidate.source.competitionName,
                    teamName = name,
                    suggestedTeamId = competitor.id.takeIf { candidate.source.sourceName == "ESPN public" },
                )
            }
        }.distinctBy { teamProfileKey(it.sport, it.teamName) }

    private fun List<Pair<PublicSource, List<EspnEventDto>>>.lineupRequests(
        now: Long,
        priorities: SyncPriorities,
    ): List<MatchLineupRequest> =
        flatMap { (source, events) ->
            if (source.sourceName != "ESPN public" || source.sport !in TEAM_SPORTS) return@flatMap emptyList()
            events.mapNotNull { event ->
                val commence = event.date?.let(::parseInstant) ?: return@mapNotNull null
                if (commence <= now) return@mapNotNull null
                val participants = event.competitions.orEmpty().firstOrNull()?.competitors.orEmpty()
                val home = participants.firstOrNull { it.homeAway.equals("home", true) } ?: participants.getOrNull(0)
                val away = participants.firstOrNull { it.homeAway.equals("away", true) } ?: participants.getOrNull(1)
                val homeName = home?.team?.displayName ?: return@mapNotNull null
                val awayName = away?.team?.displayName ?: return@mapNotNull null
                MatchLineupRequest(
                    key = lineupKey(source.sport, source.league, event.id),
                    sport = source.sport,
                    league = source.league,
                    eventId = event.id,
                    homeTeam = homeName,
                    awayTeam = awayName,
                ) to EnrichmentCandidate(source, event, commence, eventPriority(source, event, priorities))
            }
        }.sortedWith(compareByDescending<Pair<MatchLineupRequest, EnrichmentCandidate>> { it.second.priority }.thenBy { it.second.commence })
            .take(MAX_ENRICHED_MATCHES).map { it.first }

    private fun List<Pair<PublicSource, List<EspnEventDto>>>.newsRequests(
        now: Long,
        profiles: Map<String, TeamStatProfile>,
        priorities: SyncPriorities,
    ): List<NewsContextRequest> =
        flatMap { (source, events) ->
            if (source.sport == "cycling") {
                return@flatMap events.mapNotNull { event ->
                    val commence = event.date?.let(::parseInstant) ?: return@mapNotNull null
                    if (commence <= now) return@mapNotNull null
                    val raceName = event.name ?: event.shortName ?: return@mapNotNull null
                    NewsContextRequest(
                        key = "cycling:${normalizeIdentity(raceName)}:${commence / (12 * 60 * 60 * 1000L)}",
                        homeTeam = raceName,
                        awayTeam = "Cyclisme",
                        commenceTime = commence,
                        subjects = cyclingNewsSubjects(raceName),
                        lookbackDays = 90,
                    ) to EnrichmentCandidate(source, event, commence, eventPriority(source, event, priorities))
                }
            }
            if (source.sport in STANDALONE_SPORTS) {
                return@flatMap events.mapNotNull { event ->
                    val commence = event.date?.let(::parseInstant) ?: return@mapNotNull null
                    if (commence <= now) return@mapNotNull null
                    val eventName = event.displayNameForStandalone() ?: return@mapNotNull null
                    val competition = event.competitionName(source)
                    NewsContextRequest(
                        key = eventSourceKey(source.sport, eventName, commence),
                        homeTeam = eventName,
                        awayTeam = source.title,
                        commenceTime = commence,
                        subjects = standaloneNewsSubjects(source.sport, eventName, competition, source.title),
                    ) to EnrichmentCandidate(source, event, commence, eventPriority(source, event, priorities))
                }
            }
            if (source.sport !in TEAM_SPORTS) return@flatMap emptyList()
            events.mapNotNull { event ->
                val commence = event.date?.let(::parseInstant) ?: return@mapNotNull null
                if (commence <= now) return@mapNotNull null
                val participants = event.competitions.orEmpty().firstOrNull()?.competitors.orEmpty()
                val home = participants.firstOrNull { it.homeAway.equals("home", true) } ?: participants.getOrNull(0)
                val away = participants.firstOrNull { it.homeAway.equals("away", true) } ?: participants.getOrNull(1)
                val homeName = home?.team?.displayName ?: return@mapNotNull null
                val awayName = away?.team?.displayName ?: return@mapNotNull null
                val competition = event.competitionName(source)
                val favorite = source.sport in priorities.favoriteSports ||
                    competitionFavoriteKey(source.sport, competition) in priorities.favoriteCompetitions
                val subjects = regularNewsSubjects(
                    homeName = homeName,
                    awayName = awayName,
                    competition = competition,
                    homeProfile = profiles[teamProfileKey(source.sport, homeName)],
                    awayProfile = profiles[teamProfileKey(source.sport, awayName)],
                    favorite = favorite,
                    sportTerm = source.title,
                )
                NewsContextRequest(
                    key = matchSourceKey(source.sport, homeName, awayName, commence),
                    homeTeam = homeName,
                    awayTeam = awayName,
                    commenceTime = commence,
                    subjects = subjects,
                ) to EnrichmentCandidate(source, event, commence, eventPriority(source, event, priorities))
            }
        }.sortedWith(compareByDescending<Pair<NewsContextRequest, EnrichmentCandidate>> { it.second.priority }.thenBy { it.second.commence })
            .distinctBy { it.first.key }.take(MAX_NEWS_MATCHES).map { it.first }

    private fun regularNewsSubjects(
        homeName: String,
        awayName: String,
        competition: String,
        homeProfile: TeamStatProfile?,
        awayProfile: TeamStatProfile?,
        favorite: Boolean,
        sportTerm: String = "football",
    ): List<NewsSubject> = buildList {
        listOf(homeName to homeProfile, awayName to awayProfile).forEach { (team, profile) ->
            add(NewsSubject("$team $sportTerm actualité forme fatigue confiance", team, team, "TEAM"))
            add(NewsSubject("$team compte officiel $sportTerm site:instagram.com OR site:facebook.com OR site:x.com", team, team, "SOCIAL"))
            profile?.coachName?.let { coach ->
                add(NewsSubject("$coach $team conférence de presse interview", team, coach, "COACH"))
                add(NewsSubject("$coach $team officiel site:instagram.com OR site:facebook.com OR site:x.com", team, coach, "SOCIAL_COACH"))
            }
            val playerLimit = if (favorite) 4 else 1
            profile?.playerProfiles.orEmpty()
                .filter { it.availabilityNote == null }
                .sortedByDescending { it.starts + it.goals + it.secondaryGoals + it.assists + it.secondaryAssists }
                .take(playerLimit)
                .forEach { player ->
                    add(NewsSubject("${player.name} $team $sportTerm forme blessure interview", team, player.name, "PLAYER"))
                    if (favorite) {
                        add(NewsSubject("${player.name} officiel site:instagram.com OR site:facebook.com OR site:x.com", team, player.name, "SOCIAL_PLAYER"))
                    }
                }
        }
        add(NewsSubject("$competition $sportTerm actualité classement enjeu", null, competition, "COMPETITION"))
    }.distinctBy { it.query }

    private fun eventPriority(source: PublicSource, event: EspnEventDto, priorities: SyncPriorities): Int {
        val sportFavorite = source.sport in priorities.favoriteSports
        val competitionFavorite = competitionFavoriteKey(source.sport, event.competitionName(source)) in priorities.favoriteCompetitions
        return (if (competitionFavorite) 2 else 0) + (if (sportFavorite) 1 else 0)
    }

    private fun candidateIdentity(candidate: EnrichmentCandidate): String {
        val participants = candidate.event.competitions.orEmpty().firstOrNull()?.competitors.orEmpty()
            .mapNotNull { it.team?.displayName ?: it.athlete?.displayName }
            .map(::normalizeIdentity).sorted().joinToString("-")
        return "${candidate.source.sport}|$participants|${candidate.commence / (12 * 60 * 60 * 1000L)}"
    }

    private fun List<Pair<PublicSource, List<EspnEventDto>>>.calendarSourceMap(now: Long): Map<String, List<String>> =
        flatMap { (source, events) ->
            events.mapNotNull { event ->
                val commence = event.date?.let(::parseInstant) ?: return@mapNotNull null
                if (commence <= now) return@mapNotNull null
                if (source.sport in STANDALONE_SPORTS || source.sport == "cycling" || source.sport == "racing") {
                    val eventName = event.displayNameForStandalone() ?: return@mapNotNull null
                    return@mapNotNull eventSourceKey(source.sport, eventName, commence) to source.sourceName
                }
                val participants = event.competitions.orEmpty().firstOrNull()?.competitors.orEmpty()
                val home = participants.firstOrNull { it.homeAway.equals("home", true) } ?: participants.getOrNull(0)
                val away = participants.firstOrNull { it.homeAway.equals("away", true) } ?: participants.getOrNull(1)
                val homeName = home?.team?.displayName ?: return@mapNotNull null
                val awayName = away?.team?.displayName ?: return@mapNotNull null
                matchSourceKey(source.sport, homeName, awayName, commence) to source.sourceName
            }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, names) -> names.distinct() }

    private fun EspnEventDto.displayNameForStandalone(): String? {
        val participants = competitions.orEmpty()
            .firstOrNull { it.competitors.orEmpty().isNotEmpty() }
            ?.competitors.orEmpty()
        val participantNames = participants.mapNotNull { it.team?.displayName ?: it.athlete?.displayName }
            .filter { it.isNotBlank() }
        return (name ?: shortName ?: participantNames.takeIf { it.isNotEmpty() }?.joinToString(" — "))
            ?.takeIf { it.isNotBlank() }
    }

    private fun EspnEventDto.toPublicEvents(
        source: PublicSource,
        now: Long,
        profiles: Map<String, TeamStatProfile>,
        officialLineups: Map<String, MatchLineups>,
        newsContexts: Map<String, NewsContextReport>,
        calendarSources: Map<String, List<String>>,
    ): Sequence<PublicEvent> {
        if (status?.type?.completed == true) return emptySequence()
        if (source.sport in STANDALONE_SPORTS || source.sport == "cycling" || source.sport == "racing") {
            return standalonePublicEvent(source, now, newsContexts, calendarSources)?.let { sequenceOf(it) } ?: emptySequence()
        }
        return competitions.orEmpty().asSequence().mapNotNull { competition ->
            runCatching {
                competition.toPublicEvent(this, source, now, profiles, officialLineups, newsContexts, calendarSources)
            }.getOrNull()
        }
    }

    private fun EspnEventDto.standalonePublicEvent(
        source: PublicSource,
        now: Long,
        newsContexts: Map<String, NewsContextReport>,
        calendarSources: Map<String, List<String>>,
    ): PublicEvent? {
        if (status?.type?.completed == true) return null
        val commence = parseInstant(date ?: return null) ?: return null
        val lookback = if (effectiveEventType(source) == "MATCH") 0L else ACTIVE_EVENT_LOOKBACK_MS
        if (commence <= now - lookback) return null
        val eventName = displayNameForStandalone() ?: return null
        val participants = competitions.orEmpty()
            .firstOrNull { it.competitors.orEmpty().isNotEmpty() }
            ?.competitors.orEmpty()
        val names = participants.mapNotNull { it.team?.displayName ?: it.athlete?.displayName }
            .filter { it.isNotBlank() }
        val homeName = names.getOrNull(0) ?: eventName
        val awayName = names.getOrNull(1) ?: source.title
        val sourceKey = eventSourceKey(source.sport, eventName, commence)
        val contextReport = newsContexts[sourceKey] ?: NewsContextReport()
        val informationSources = buildList {
            addAll(calendarSources[sourceKey].orEmpty().map { it.removeSuffix(" public") })
            addAll(contextReport.checkedSources)
        }.distinct()
        return PublicEvent(
            eventId = "${source.sport}-${source.league}:$id:standalone",
            sportKey = "${source.sport}/${source.league}",
            sportTitle = source.title,
            competitionName = competitionName(source),
            dataSourceName = source.sourceName,
            commenceTime = commence,
            homeTeam = homeName,
            awayTeam = awayName,
            informationSources = informationSources,
            contextSignals = contextReport.signals,
        )
    }

    private fun EspnCompetitionDto.toPublicEvent(
        event: EspnEventDto,
        source: PublicSource,
        now: Long,
        profiles: Map<String, TeamStatProfile>,
        officialLineups: Map<String, MatchLineups>,
        newsContexts: Map<String, NewsContextReport>,
        calendarSources: Map<String, List<String>>,
    ): PublicEvent? {
        if (status?.type?.completed == true) return null
        val commence = parseInstant(date ?: event.date ?: return null) ?: return null
        if (commence <= now) return null

        val participants = competitors.orEmpty()
        val home = participants.firstOrNull { it.homeAway.equals("home", ignoreCase = true) }
            ?: participants.getOrNull(0)
        val away = participants.firstOrNull { it.homeAway.equals("away", ignoreCase = true) }
            ?: participants.getOrNull(1)
        val homeParticipant = home ?: return null
        val awayParticipant = away ?: return null
        val homeName = homeParticipant.team?.displayName ?: homeParticipant.athlete?.displayName ?: return null
        val awayName = awayParticipant.team?.displayName ?: awayParticipant.athlete?.displayName ?: return null
        if (homeName == awayName) return null

        val quote = odds.safeOdds().firstOrNull { it.moneyline != null }
        val moneyline = quote?.moneyline
        val homeOdds = moneyline?.home.decimalOdds()
        val awayOdds = moneyline?.away.decimalOdds()

        val homeProfile = profiles[teamProfileKey(source.sport, homeName)]
        val awayProfile = profiles[teamProfileKey(source.sport, awayName)]
        val matchLineups = officialLineups[lineupKey(source.sport, source.league, event.id)]
        val sourceKey = matchSourceKey(source.sport, homeName, awayName, commence)
        val contextReport = newsContexts[sourceKey] ?: NewsContextReport()
        val contextSignals = contextReport.signals
        val informationSources = buildList {
            addAll(calendarSources[sourceKey].orEmpty().map { it.removeSuffix(" public") })
            addAll(homeProfile?.sourceNames.orEmpty())
            addAll(awayProfile?.sourceNames.orEmpty())
            addAll(contextReport.checkedSources)
        }.distinct()
        return PublicEvent(
            eventId = "${source.sport}-${source.league}:${event.id}:${id ?: "main"}",
            sportKey = "${source.sport}/${source.league}",
            sportTitle = source.title,
            competitionName = event.competitionName(source),
            dataSourceName = source.sourceName,
            commenceTime = commence,
            homeTeam = homeName,
            awayTeam = awayName,
            homeProfile = homeProfile,
            awayProfile = awayProfile,
            homeLineup = matchLineups?.home ?: homeProfile?.recentLineup,
            awayLineup = matchLineups?.away ?: awayProfile?.recentLineup,
            informationSources = informationSources,
            contextSignals = contextSignals,
            homeForm = homeParticipant.form,
            awayForm = awayParticipant.form,
            homeRecord = homeParticipant.records.orEmpty().firstOrNull()?.summary,
            awayRecord = awayParticipant.records.orEmpty().firstOrNull()?.summary,
            provider = quote?.provider?.name,
            homeOdds = homeOdds,
            awayOdds = awayOdds,
            drawOdds = moneyline?.draw.decimalOdds(),
            totalLine = quote?.overUnder ?: quote?.total?.over.lineValue() ?: quote?.total?.under.lineValue(),
            overOdds = quote?.total?.over.decimalOdds(),
            underOdds = quote?.total?.under.decimalOdds(),
            homeSpreadLine = quote?.pointSpread?.home.lineValue(),
            awaySpreadLine = quote?.pointSpread?.away.lineValue(),
            homeSpreadOdds = quote?.pointSpread?.home.decimalOdds(),
            awaySpreadOdds = quote?.pointSpread?.away.decimalOdds(),
        )
    }

    private fun EspnMarketSideDto?.decimalOdds(): Double? {
        val value = this?.close?.odds ?: this?.open?.odds
        return PublicPredictionEngine.americanToDecimal(value)
    }

    private fun EspnMarketSideDto?.lineValue(): Double? {
        val value = this?.close?.line ?: this?.open?.line ?: return null
        return value.lowercase().replace("o", "").replace("u", "").replace("+", "").toDoubleOrNull()
    }

    private fun List<EspnOddsDto?>?.safeOdds(): List<EspnOddsDto> =
        orEmpty().mapNotNull { it }

    private fun PublicPrediction.toEntity(event: PublicEvent, updateTime: Long): PredictionEntity = PredictionEntity(
        id = id,
        eventId = event.eventId,
        sportKey = event.sportKey,
        sportTitle = event.sportTitle,
        competitionName = event.competitionName,
        commenceTime = event.commenceTime,
        homeTeam = event.homeTeam,
        awayTeam = event.awayTeam,
        market = market,
        selection = selection,
        betclicOdds = referenceOdds,
        impliedProbability = impliedProbability,
        consensusProbability = estimatedProbability,
        valueEdge = valueEdge,
        expectedValue = expectedValue,
        confidenceScore = confidenceScore,
        riskLevel = riskLevel,
        category = category,
        bookmakerCount = if (event.homeOdds != null && event.awayOdds != null) 1 else 0,
        sourceName = sourceName,
        sourceLastUpdate = updateTime,
        explanation = explanation,
        positiveArguments = positiveArguments.joinToString("\n"),
        negativeArguments = negativeArguments.joinToString("\n"),
        expectedScore = expectedScore.orEmpty(),
        statSummary = statSummary.joinToString("\n"),
        scenarios = scenarios.joinToString("\n") { "${it.type}|${it.label}|${it.probability}" },
        homeLineupStatus = homeLineup?.displayStatus().orEmpty(),
        homeLineup = homeLineup?.serializePlayers().orEmpty(),
        awayLineupStatus = awayLineup?.displayStatus().orEmpty(),
        awayLineup = awayLineup?.serializePlayers().orEmpty(),
        playerScenarios = playerScenarios.joinToString("\n") { "${it.type}|${it.label}|${it.probability}" },
        sourceDetails = sourceDetails.joinToString("\n"),
        contextInsights = contextInsights.joinToString("\n"),
        sourceAgreement = sourceAgreement,
    )

    private fun parseInstant(value: String): Long? {
        val normalized = when {
            Regex("T\\d{2}:\\d{2}Z$").containsMatchIn(value) -> value.replace("Z", ":00Z")
            "T" in value && !value.endsWith("Z") -> "${value}Z"
            else -> value
        }
        return runCatching { Instant.parse(normalized).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDate.parse(value.substringBefore("T")).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
    }

    private fun lineupKey(sport: String, league: String, eventId: String): String = "$sport/$league:$eventId"

    private fun matchSourceKey(sport: String, home: String, away: String, commence: Long): String = buildString {
        append(sport)
        append('|')
        append(listOf(normalizeIdentity(home), normalizeIdentity(away)).sorted().joinToString("-"))
        append('|')
        append(commence / (12 * 60 * 60 * 1000L))
    }

    private fun eventSourceKey(sport: String, eventName: String, commence: Long): String = buildString {
        append(sport)
        append('|')
        append(normalizeIdentity(eventName))
        append('|')
        append(commence / (12 * 60 * 60 * 1000L))
    }

    private fun com.soliano.betvalueanalyzer.domain.TeamLineup.displayStatus(): String = buildString {
        append(if (status == com.soliano.betvalueanalyzer.domain.LineupStatus.OFFICIAL) "Officielle" else "Probable · dernier match")
        formation?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    }

    private fun com.soliano.betvalueanalyzer.domain.TeamLineup.serializePlayers(): String = players.joinToString("\n") {
        listOfNotNull(it.jersey?.takeIf(String::isNotBlank)?.let { number -> "#$number" }, it.name, it.position?.takeIf(String::isNotBlank))
            .joinToString("|")
    }

    private fun EspnEventDto.competitionName(source: PublicSource): String {
        return canonicalCompetitionName(competitionNameRaw(source))
    }

    private fun EspnEventDto.competitionNameRaw(source: PublicSource): String {
        if (source.dynamicCompetition) {
            val dynamicName = name ?: shortName
            if (source.sport == "soccer") {
                val leagueId = uid?.substringAfter("~l:", "")?.substringBefore("~")
                return SOCCER_COMPETITIONS[leagueId] ?: source.competitionName
            }
            if (source.sport == "rugby") {
                return RUGBY_COMPETITIONS[season?.slug] ?: season?.slug?.replace('-', ' ')
                    ?.replaceFirstChar(Char::uppercase) ?: source.competitionName
            }
            if (source.sport == "cycling") {
                return season?.slug?.takeIf { it.isNotBlank() } ?: dynamicName ?: source.competitionName
            }
            if (source.sport == "tennis") {
                val tournament = tournamentName?.takeIf { it.isNotBlank() } ?: dynamicName
                return if (tournament.isNullOrBlank()) source.competitionName else "${source.competitionName} · $tournament"
            }
            if (!dynamicName.isNullOrBlank()) {
                return if (source.competitionName.isBlank()) dynamicName else "${source.competitionName} · $dynamicName"
            }
        }
        return source.competitionName
    }

    private fun competitionKey(source: PublicSource, competitionName: String): String {
        return competitionFavoriteKey(source.sport, competitionName)
    }

    private fun predictionIdentity(value: PredictionEntity): String = buildString {
        append(value.sportTitle.lowercase())
        append('|')
        append(listOf(normalizeIdentity(value.homeTeam), normalizeIdentity(value.awayTeam)).sorted().joinToString("-"))
        append('|')
        append(value.commenceTime / (12 * 60 * 60 * 1000L))
        append('|')
        append(value.market.lowercase())
    }

    private fun mergePredictions(
        fresh: List<PredictionEntity>,
        previous: List<PredictionEntity>,
        now: Long,
    ): List<PredictionEntity> {
        val freshIdentities = fresh.map(::predictionIdentity).toSet()
        return (fresh + previous.filter { it.commenceTime > now && predictionIdentity(it) !in freshIdentities })
            .distinctBy { predictionIdentity(it) }
            .sortedWith(compareBy<PredictionEntity> { it.commenceTime }.thenByDescending { it.confidenceScore })
    }

    private fun mergeCatalogEvents(
        fresh: List<UpcomingEventEntity>,
        previous: List<UpcomingEventEntity>,
        now: Long,
    ): List<UpcomingEventEntity> {
        val previousActive = previous.filter { it.isCatalogActive(now) }
        val previousByIdentity = previousActive.associateBy(::catalogIdentity)
        val freshWithPreservedAnalysis = fresh
            .filter { it.isCatalogActive(now) }
            .map { event ->
                val previousEvent = previousByIdentity[catalogIdentity(event)]
                if (event.analysisId == null && previousEvent?.analysisId != null) {
                    event.copy(analysisId = previousEvent.analysisId)
                } else {
                    event
                }
            }
        val freshIdentities = freshWithPreservedAnalysis.map(::catalogIdentity).toSet()
        return (freshWithPreservedAnalysis + previousActive.filter { catalogIdentity(it) !in freshIdentities })
            .distinctBy { catalogIdentity(it) }
            .sortedBy { it.commenceTime }
    }

    private fun UpcomingEventEntity.isCatalogActive(now: Long): Boolean {
        val lookback = if (eventType == "MATCH") 0L else ACTIVE_EVENT_LOOKBACK_MS
        return commenceTime > now - lookback
    }

    private fun catalogIdentity(value: UpcomingEventEntity): String = buildString {
        append(value.sportKey)
        append('|')
        val participants = listOf(value.participantA, value.participantB).filter { it.isNotBlank() }
        append(if (participants.isNotEmpty()) participants.map(::normalizeIdentity).sorted().joinToString("-") else normalizeIdentity(value.eventName))
        append('|')
        append(value.commenceTime / (12 * 60 * 60 * 1000L))
    }

    private fun normalizeIdentity(value: String): String = value.lowercase()
        .replace("united states", "usa")
        .replace("stade toulousain", "toulouse")
        .replace("montpellier herault rugby", "montpellier")
        .replace("montpellier herault", "montpellier")
        .replace("montpellier hérault rugby", "montpellier")
        .replace("montpellier hérault", "montpellier")
        .replace(Regex("[^a-z0-9à-ÿ]+"), "")

    private data class PublicSource(
        val sport: String,
        val league: String,
        val title: String,
        val maxEvents: Int,
        val competitionName: String,
        val eventType: String = "MATCH",
        val dynamicCompetition: Boolean = false,
        val sourceName: String = "ESPN public",
        val apiSport: String = sport,
    )

    private data class EnrichmentCandidate(
        val source: PublicSource,
        val event: EspnEventDto,
        val commence: Long,
        val priority: Int,
    )

    private data class ExternalLeagueSource(
        val id: String,
        val sport: String,
        val title: String,
        val competitionName: String,
        val eventType: String,
        val maxEvents: Int = 18,
    )

    private data class RaceStanding(
        val position: Int,
        val name: String,
        val score: String,
        val winner: Boolean,
    )

    private data class CuratedMajorEvent(
        val id: String,
        val sportKey: String,
        val sportTitle: String,
        val competitionName: String,
        val eventName: String,
        val homeName: String,
        val awayName: String,
        val commenceTime: Long,
        val eventType: String = "MATCH",
        val sourceName: String,
    )

    private companion object {
        val PUBLIC_SOURCES = listOf(
            PublicSource("soccer", "all", "Football", 80, "Compétition football", dynamicCompetition = true),
            PublicSource("basketball", "wnba", "Basketball", 30, "WNBA"),
            PublicSource("basketball", "nba", "Basketball", 30, "NBA"),
            PublicSource("baseball", "mlb", "Baseball", 30, "MLB"),
            PublicSource("hockey", "nhl", "Hockey", 30, "NHL"),
            PublicSource("football", "nfl", "Football américain", 30, "NFL"),
            PublicSource("golf", "pga", "Golf", 30, "PGA Tour", "TOURNAMENT", true),
            PublicSource("golf", "lpga", "Golf", 30, "LPGA Tour", "TOURNAMENT", true),
            PublicSource("tennis", "atp", "Tennis", 120, "ATP", "MATCH", true),
            PublicSource("tennis", "wta", "Tennis", 120, "WTA", "MATCH", true),
            PublicSource("rugby", "all", "Rugby", 60, "Compétition rugby", dynamicCompetition = true),
            PublicSource("cycling", "uci", "Cyclisme", 120, "Calendrier UCI", "RACE", true, "UCI + TheSportsDB public"),
            PublicSource("racing", "f1", "Formule 1", 20, "", "GP", true),
            PublicSource("nascar", "nascar-premier", "NASCAR", 20, "NASCAR Cup Series", "RACE", true, "ESPN public", "racing"),
            PublicSource("mma", "ufc", "MMA", 20, "UFC", "EVENT"),
        )

        const val ACTIVE_EVENT_LOOKBACK_MS = 14L * 24 * 60 * 60 * 1000
        const val LIVE_EVENT_KEEP_MS = 6L * 60 * 60 * 1000
        const val LIVE_PRE_WINDOW_MS = 2L * 60 * 60 * 1000
        const val LIVE_MONITOR_PRE_WINDOW_MS = 6L * 60 * 60 * 1000
        const val LIVE_TEAM_PRE_WINDOW_MS = 18L * 60 * 60 * 1000
        const val LIVE_LONG_PRE_WINDOW_MS = 36L * 60 * 60 * 1000
        const val LIVE_FAVORITE_PRE_WINDOW_MS = 72L * 60 * 60 * 1000
        const val TEAM_MATCH_KEEP_MS = 18L * 60 * 60 * 1000
        const val TEAM_RESULT_KEEP_MS = 24L * 60 * 60 * 1000
        const val RACE_RESULT_KEEP_MS = 18L * 60 * 60 * 1000
        const val TENNIS_LIVE_SCORE_WINDOW_MS = 6L * 60 * 60 * 1000
        const val MAX_ENRICHED_MATCHES = 24
        const val MAX_NEWS_MATCHES = 12

        val TEAM_SPORTS = setOf(
            "soccer",
            "basketball",
            "baseball",
            "hockey",
            "football",
            "rugby",
            "handball",
            "volleyball",
        )
        val STANDALONE_SPORTS = setOf(
            "tennis",
            "golf",
            "mma",
            "boxing",
            "nascar",
            "athletics",
        )
        val ANALYZABLE_SPORTS = TEAM_SPORTS + STANDALONE_SPORTS + setOf("cycling", "racing")
        val PROFILE_SPORTS = TEAM_SPORTS
        val LONG_LIVE_MONITOR_SPORTS = setOf("cycling", "racing", "nascar", "golf", "tennis", "athletics", "mma", "boxing")
        val RESULT_BOARD_LIVE_SPORTS = setOf("cycling", "racing", "nascar", "golf", "athletics")
        val SPORTS_DB_SEASON_EXPANDED_SPORTS = setOf(
            "tennis",
            "volleyball",
            "handball",
        )

        private val PARIS_ZONE: ZoneId = ZoneId.of("Europe/Paris")

        private fun parisMillis(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
        ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, PARIS_ZONE)
            .toInstant()
            .toEpochMilli()

        val CURATED_MAJOR_EVENTS = listOf(
            CuratedMajorEvent(
                id = "curated:top14-final-2026-toulouse-montpellier",
                sportKey = "rugby",
                sportTitle = "Rugby",
                competitionName = "Top 14",
                eventName = "Stade Toulousain - Montpellier Herault",
                homeName = "Stade Toulousain",
                awayName = "Montpellier Herault",
                commenceTime = parisMillis(2026, 6, 27, 21, 5),
                sourceName = "Calendrier Top 14 - finale 2026",
            ),
        )

        val THE_SPORTS_DB_LEAGUES = listOf(
            ExternalLeagueSource("4405", "football", "Football américain", "CFL", "MATCH"),
            ExternalLeagueSource("5063", "football", "Football américain", "European League of Football", "MATCH"),
            ExternalLeagueSource("4980", "handball", "Handball", "EHF Champions League", "MATCH"),
            ExternalLeagueSource("5275", "handball", "Handball", "EHF European League", "MATCH"),
            ExternalLeagueSource("4536", "handball", "Handball", "French LNH Division 1", "MATCH"),
            ExternalLeagueSource("5083", "volleyball", "Volley-ball", "FIVB Volleyball Mens Nations League", "MATCH"),
            ExternalLeagueSource("5084", "volleyball", "Volley-ball", "FIVB Volleyball Womens Nations League", "MATCH"),
            ExternalLeagueSource("5613", "volleyball", "Volley-ball", "Championnat d'Europe masculin", "MATCH"),
            ExternalLeagueSource("5848", "volleyball", "Volley-ball", "European Volleyball League", "MATCH"),
            ExternalLeagueSource("5614", "volleyball", "Volley-ball", "CEV Challenge Cup", "MATCH"),
            ExternalLeagueSource("4464", "tennis", "Tennis", "ATP World Tour", "MATCH"),
            ExternalLeagueSource("4517", "tennis", "Tennis", "WTA Tour", "MATCH"),
            ExternalLeagueSource("4581", "tennis", "Tennis", "Laver Cup", "TOURNAMENT"),
            ExternalLeagueSource("4761", "golf", "Golf", "PGA Tour of Australasia", "TOURNAMENT"),
            ExternalLeagueSource("4758", "golf", "Golf", "European Challenge Tour", "TOURNAMENT"),
            ExternalLeagueSource("5007", "athletics", "Athlétisme", "World Athletics Championships", "EVENT"),
            ExternalLeagueSource("5788", "athletics", "Athlétisme", "World Athletics Ultimate Championship", "EVENT"),
            ExternalLeagueSource("5785", "athletics", "Athlétisme", "World Athletics Indoor Tour Gold", "EVENT"),
            ExternalLeagueSource("4933", "hockey", "Hockey sur glace", "Austrian ICE Hockey League", "MATCH"),
            ExternalLeagueSource("5159", "hockey", "Hockey sur glace", "Canadian OHL", "MATCH"),
            ExternalLeagueSource("5161", "hockey", "Hockey sur glace", "Canadian QMJHL", "MATCH"),
            ExternalLeagueSource("4465", "cycling", "Cyclisme", "UCI World Tour", "RACE"),
        )

        val SOCCER_COMPETITIONS = mapOf(
            "606" to "Coupe du monde FIFA",
            "700" to "Premier League",
            "740" to "LaLiga",
            "720" to "Bundesliga",
            "730" to "Serie A",
            "710" to "Ligue 1",
            "775" to "UEFA Champions League",
            "776" to "UEFA Europa League",
            "20296" to "UEFA Conference League",
            "3918" to "FA Cup",
            "3920" to "Carabao Cup",
            "770" to "MLS",
            "760" to "Liga MX",
            "8301" to "NWSL",
            "19868" to "NWSL",
            "5330" to "Scottish League Cup",
            "4002" to "USL Championship",
            "19915" to "USL League One",
            "5672" to "Compétitions internationales",
        )

        val RUGBY_COMPETITIONS = mapOf(
            "top-14" to "Top 14",
            "six-nations" to "Six Nations",
            "premiership-rugby" to "Premiership Rugby",
            "united-rugby-championship" to "United Rugby Championship",
            "european-rugby-champions-cup" to "Champions Cup",
            "rugby-world-cup" to "Coupe du monde de rugby",
        )
    }
}

class OddsSyncException(message: String) : Exception(message)
