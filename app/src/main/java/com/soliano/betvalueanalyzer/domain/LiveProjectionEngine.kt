package com.soliano.betvalueanalyzer.domain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

data class LiveEventSnapshot(
    val sportKey: String,
    val sportTitle: String,
    val competitionName: String = "",
    val commenceTime: Long = 0L,
    val homeName: String,
    val awayName: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val statusState: String,
    val statusDescription: String,
    val displayClock: String,
    val period: Int?,
    val resultSummary: String = "",
)

data class LiveProjection(
    val statSummary: List<String>,
    val scenarios: List<ProbabilityScenario>,
)

object LiveProjectionEngine {
    fun analyze(event: LiveEventSnapshot): LiveProjection {
        val sport = event.sportKey.substringBefore('/')
        val profile = liveSportProfile(sport)
        val venue = if (isNeutralVenueCompetition(
                sportKey = event.sportKey,
                sportTitle = event.sportTitle,
                competitionName = event.competitionName,
                eventName = event.statusDescription,
                homeTeam = event.homeName,
                awayTeam = event.awayName,
                commenceTime = event.commenceTime,
            )
        ) null else liveVenueEdge(sport, event.homeName, event.awayName)
        val intelligence = SportIntelligenceCatalog.profile(sport)
        val homeScore = event.homeScore
        val awayScore = event.awayScore
        val scoreKnown = homeScore != null && awayScore != null
        val scoreDiff = if (scoreKnown) homeScore!! - awayScore!! else 0
        val elapsed = event.progress()
        val remaining = (1.0 - elapsed).coerceIn(0.0, 1.0)
        val leader = when {
            !scoreKnown || scoreDiff == 0 -> null
            scoreDiff > 0 -> event.homeName
            else -> event.awayName
        }
        val trailing = when {
            !scoreKnown || scoreDiff == 0 -> null
            scoreDiff > 0 -> event.awayName
            else -> event.homeName
        }
        val comebackProbability = if (scoreKnown && leader != null) {
            sigmoid(0.95 - abs(scoreDiff) / profile.closeMargin - elapsed * 1.15).coerceIn(0.05, 0.72)
        } else {
            (0.34 + remaining * 0.22).coerceIn(0.25, 0.58)
        }
        val leaderHolds = if (leader != null) (1.0 - comebackProbability * 0.72).coerceIn(0.42, 0.94) else 0.50
        val totalScore = listOfNotNull(homeScore, awayScore).sum()
        val projectedTotal = if (scoreKnown && elapsed > 0.08) {
            (totalScore / elapsed).coerceAtMost(totalScore + profile.expectedLateTotal)
        } else {
            profile.defaultTotal
        }
        val nextScoreHome = if (!scoreKnown) 0.50 else {
            val pressure = if (scoreDiff < 0) 0.08 else if (scoreDiff > 0) -0.03 else 0.0
            (0.50 + pressure + profile.homeLiveEdge).coerceIn(0.28, 0.72)
        }
        val summary = buildList {
            val liveHeader = if (profile.versusActions) "Score live" else "Classement/top 3 live"
            val liveValue = if (profile.versusActions) event.scoreText() else event.raceResultText()
            add("$liveHeader : $liveValue · ${event.statusDescription.ifBlank { "statut en cours" }} ${event.displayClock.takeIf { it.isNotBlank() }?.let { "· $it" }.orEmpty()}")
            add("Synthèse live : score/classement, stats clés et scénarios recalculés selon le sport.")
            add("À surveiller ${profile.displayName} : ${livePdfMarkets(sport)}.")
            add("Stats clés ${profile.displayName} : ${profile.keyStats}.")
            if (profile.versusActions) {
                if (scoreKnown) {
                    add("Score réel confirmé : ${event.scoreText()} · utilisé comme base du recalcul live.")
                } else {
                    add("Score réel : attente d'un flux public confirmé ; les probabilités restent prudentes.")
                }
                add("Projection live : total estimé autour de ${decimal(projectedTotal)} ${profile.unit}, marge actuelle ${abs(scoreDiff)} ${profile.marginUnit}.")
            } else {
                if (event.resultSummary.isNotBlank()) {
                    add("Top 3 / classement public : ${event.resultSummary}.")
                } else {
                    add("Top 3 / classement : attente du classement officiel public ; aucun faux score n'est affiché.")
                }
                add("Projection live : classement/podium à recalculer avec les infos officielles, écarts, météo/incidents et rôles.")
            }
            add("À recouper pendant le live : ${profile.contextStats}.")
            venue?.let { add(it.summary) }
            addAll(intelligence.summaryLines)
        }
        val scenarioPool = buildList {
            if (leader != null) {
                add(ProbabilityScenario("$leader garde l’avantage", leaderHolds, profile.resultType))
                add(ProbabilityScenario("$trailing revient dans le match", comebackProbability, profile.momentumType))
            } else {
                add(ProbabilityScenario("Match/événement encore ouvert", 0.50 + remaining * 0.10, profile.resultType))
                add(ProbabilityScenario("Bascule possible sur le prochain temps fort", 0.55, profile.momentumType))
            }
            if (profile.versusActions) {
                add(ProbabilityScenario("${event.homeName} prochain ${profile.nextAction}", nextScoreHome, profile.nextActionType))
                add(ProbabilityScenario("${event.awayName} prochain ${profile.nextAction}", 1.0 - nextScoreHome, profile.nextActionType))
            } else {
                val topNames = event.raceTopNames()
                if (topNames.isNotEmpty()) {
                    val leaderWin = raceLeaderHoldProbability(elapsed, remaining)
                    add(ProbabilityScenario("${topNames[0]} termine vainqueur / P1", leaderWin, "Vainqueur live"))
                    topNames.getOrNull(1)?.let { add(ProbabilityScenario("$it termine sur le podium", (leaderWin - 0.06).coerceIn(0.52, 0.86), "Podium live")) }
                    topNames.getOrNull(2)?.let { add(ProbabilityScenario("$it conserve le top 3", (leaderWin - 0.12).coerceIn(0.48, 0.80), "Top 3 live")) }
                    val stableTop3 = (0.46 + elapsed * 0.32).coerceIn(0.48, 0.82)
                    add(ProbabilityScenario("Top 3 identique à l’arrivée", stableTop3, "Top 3 live"))
                    add(ProbabilityScenario("Changement dans le top 3", (1.0 - stableTop3).coerceIn(0.18, 0.52), "Podium live"))
                }
            }
            if (profile.versusActions) {
                add(ProbabilityScenario("Total final supérieur à ${decimal(profile.mainLine(projectedTotal))} ${profile.unit}", totalOverProbability(projectedTotal, profile.mainLine(projectedTotal), profile.variance), profile.totalType))
                add(ProbabilityScenario("Fin serrée dans la marge ${decimal(profile.closeMargin)} ${profile.marginUnit}", closeFinishProbability(abs(scoreDiff), remaining, profile.closeMargin), profile.closeType))
            } else {
                val topNames = event.raceTopNames()
                if (topNames.size >= 2) {
                    add(ProbabilityScenario("${topNames[0]} devant ${topNames[1]} à l’arrivée", raceLeaderHoldProbability(elapsed, remaining), "Duel classement"))
                }
            }
            venue?.let { add(it.scenario) }
            addAll(profile.extraScenarios(event, elapsed, remaining, scoreKnown))
            addAll(intelligence.probabilityScenarios)
            addAll(intelligence.playerProbabilityScenarios)
        }.filter(::isConcreteLiveScenario)
            .distinctBy { it.type + it.label }
        val scenarios = (scenarioPool + concreteFallbackScenarios(event, profile, projectedTotal, nextScoreHome, elapsed, remaining))
            .filter(::isConcreteLiveScenario)
            .distinctBy { it.type + it.label }
            .sortedByDescending { it.probability }
            .take(30)
        return LiveProjection(summary, scenarios)
    }

    private fun LiveEventSnapshot.progress(): Double {
        val sport = sportKey.substringBefore('/')
        if (sport in setOf("racing", "nascar")) {
            val lapText = "$statusDescription $displayClock $resultSummary"
            Regex("""(?i)(?:tour|tours|lap|laps)\s*(\d{1,3})(?:\s*/\s*(\d{1,3}))?""")
                .find(lapText)
                ?.let { match ->
                    val current = match.groupValues.getOrNull(1)?.toDoubleOrNull()
                    val total = match.groupValues.getOrNull(2)?.toDoubleOrNull()
                        ?: if (sport == "racing") 70.0 else 200.0
                    if (current != null && total > 0.0) return (current / total).coerceIn(0.08, 0.97)
                }
        }
        val rawClock = displayClock.filter { it.isDigit() || it == ':' || it == '.' }
            .substringBefore(':')
            .toDoubleOrNull()
        val periodValue = period ?: 1
        return when (sport) {
            "soccer", "rugby", "field_hockey" -> (rawClock ?: 0.0) / 90.0
            "basketball" -> ((periodValue - 1) * 12 + (rawClock ?: 0.0)) / 48.0
            "football" -> ((periodValue - 1) * 15 + (rawClock ?: 0.0)) / 60.0
            "baseball" -> periodValue / 9.0
            "hockey" -> ((periodValue - 1) * 20 + (rawClock ?: 0.0)) / 60.0
            "handball" -> (rawClock ?: 0.0) / 60.0
            "volleyball", "tennis", "darts", "snooker" -> periodValue / 5.0
            else -> if (statusState.equals("in", true)) 0.50 else 0.05
        }.coerceIn(0.05, 0.97)
    }

    private fun LiveEventSnapshot.scoreText(): String =
        if (homeScore != null && awayScore != null) "$homeName $homeScore — $awayScore $awayName" else "$homeName — $awayName · score public en attente"

    private fun LiveEventSnapshot.raceResultText(): String =
        resultSummary.ifBlank { "$homeName · classement/top 3 officiel en attente" }

    private fun LiveEventSnapshot.raceTopNames(): List<String> =
        resultSummary.split('·', ';', '\n')
            .mapNotNull { segment ->
                Regex("""^\s*\d{1,2}\.\s*([^()·;]+)""").find(segment)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            .take(3)

    private fun raceLeaderHoldProbability(elapsed: Double, remaining: Double): Double =
        (0.58 + elapsed * 0.24 - remaining * 0.04).coerceIn(0.58, 0.88)

    private fun concreteFallbackScenarios(
        event: LiveEventSnapshot,
        profile: LiveSportProfile,
        projectedTotal: Double,
        nextScoreHome: Double,
        elapsed: Double,
        remaining: Double,
    ): List<ProbabilityScenario> {
        if (!profile.versusActions) {
            val topNames = event.raceTopNames()
            val leader = topNames.firstOrNull() ?: event.homeName
            val p2 = topNames.getOrNull(1)
            val p3 = topNames.getOrNull(2)
            val leaderHold = raceLeaderHoldProbability(elapsed, remaining)
            return listOfNotNull(
                ProbabilityScenario("$leader finit devant", leaderHold, profile.resultType),
                p2?.let { ProbabilityScenario("$it finit top 3", (leaderHold - 0.07).coerceIn(0.50, 0.84), "Top 3 live") },
                p3?.let { ProbabilityScenario("$it finit top 3", (leaderHold - 0.12).coerceIn(0.46, 0.80), "Top 3 live") },
                ProbabilityScenario("Top 3 reste stable", (0.46 + elapsed * 0.32).coerceIn(0.48, 0.82), "Top 3 live"),
                ProbabilityScenario("Au moins un changement podium", (0.54 - elapsed * 0.20).coerceIn(0.18, 0.54), "Podium live"),
                ProbabilityScenario("Course sans neutralisation majeure", (0.52 + elapsed * 0.18).coerceIn(0.52, 0.78), "Rythme course"),
                ProbabilityScenario("Incident modifie la stratégie", (0.34 + remaining * 0.22).coerceIn(0.22, 0.58), "Risque live"),
                ProbabilityScenario("Duel leader/P2 reste serré", (0.50 + remaining * 0.14).coerceIn(0.42, 0.66), profile.closeType),
                ProbabilityScenario("Meilleur top 10 stable", (0.55 + elapsed * 0.18).coerceIn(0.55, 0.80), profile.totalType),
                ProbabilityScenario("Attaque stratégique dans la fin", (0.42 + remaining * 0.18).coerceIn(0.36, 0.62), profile.nextActionType),
            )
        }
        val line = profile.mainLine(projectedTotal)
        return listOf(
            ProbabilityScenario("${event.homeName} gagne la prochaine séquence", nextScoreHome, profile.nextActionType),
            ProbabilityScenario("${event.awayName} gagne la prochaine séquence", 1.0 - nextScoreHome, profile.nextActionType),
            ProbabilityScenario("${event.homeName} reste dans la marge ${decimal(profile.closeMargin)} ${profile.marginUnit}", (0.55 + remaining * 0.12).coerceIn(0.50, 0.72), profile.closeType),
            ProbabilityScenario("${event.awayName} reste dans la marge ${decimal(profile.closeMargin)} ${profile.marginUnit}", (0.53 + remaining * 0.10).coerceIn(0.48, 0.70), profile.closeType),
            ProbabilityScenario("Total final supérieur à ${decimal(line)} ${profile.unit}", totalOverProbability(projectedTotal, line, profile.variance), profile.totalType),
            ProbabilityScenario("Total final inférieur à ${decimal(line + profile.variance)} ${profile.unit}", 1.0 - totalOverProbability(projectedTotal, line + profile.variance, profile.variance), profile.totalType),
            ProbabilityScenario("Fin serrée", closeFinishProbability(abs((event.homeScore ?: 0) - (event.awayScore ?: 0)), remaining, profile.closeMargin), profile.closeType),
            ProbabilityScenario("Rythme offensif en hausse", (0.42 + remaining * 0.12).coerceIn(0.38, 0.58), "Rythme live"),
            ProbabilityScenario("Rythme offensif contrôlé", (0.58 - remaining * 0.10).coerceIn(0.42, 0.64), "Rythme live"),
            ProbabilityScenario("Momentum change sur prochaine action", (0.44 + remaining * 0.16).coerceIn(0.42, 0.66), profile.momentumType),
        )
    }

    private fun isConcreteLiveScenario(scenario: ProbabilityScenario): Boolean {
        val text = "${scenario.label} ${scenario.type}".lowercase()
        val banned = listOf(
            "attendre",
            "confirmation",
            "confirmer",
            "recalculer",
            "recouper",
            "surveiller",
            "avant pari",
            "seulement si",
            "fiabilité live",
            "fiabilite live",
        )
        return banned.none { it in text }
    }

    private fun livePdfMarkets(sport: String): String = when (sport) {
        "soccer" -> "résultat 90 minutes, score, total buts, prochain but, tirs/corners, cartons et changements tactiques"
        "rugby" -> "résultat, total points, prochain score, essais, pénalités, transformations, conquête, cartons et banc"
        "basketball" -> "vainqueur live, total points, prochain run, écart/handicap, fautes, rebonds, tirs à 3 points et rotations"
        "tennis" -> "vainqueur match, score en sets, total jeux, prochain jeu, break, tie-break, aces et doubles fautes"
        "baseball" -> "vainqueur live, total runs, prochain run, manches, hits, home runs, bullpen et strikeouts"
        "hockey", "field_hockey" -> "vainqueur live, total buts, prochain but, tirs, power play/penalty corner, gardien et discipline"
        "football", "australian_football" -> "vainqueur live, total points, prochain touchdown/score, yards, turnovers, sacks et blessures"
        "handball" -> "vainqueur live, total buts, prochain but, arrêts gardien, exclusions, pertes de balle et rotations"
        "volleyball" -> "vainqueur live, score en sets, total points, prochain break, aces, blocks, réception et erreurs"
        "cricket" -> "vainqueur live, total runs, wickets, run rate, batteurs, lanceurs, overs et météo/pitch"
        "darts" -> "vainqueur live, score legs/sets, 180, checkout, moyenne 3 fléchettes et pression doubles"
        "snooker" -> "vainqueur live, score frames, centuries, breaks 50+, sécurité, erreurs et format"
        "cycling" -> "classement live, top 3/top 10, échappée, attaque, sprint, maillot, abandon, météo et écarts"
        "racing" -> "classement live, top 3, stratégie pneus, pit stops, safety car, pluie, abandon et rythme course"
        "nascar" -> "classement live, top 3/top 10, cautions, restart, pit stops, crash, abandon et track position"
        "golf" -> "leaderboard, top 5/top 10/top 20, cut, birdies, bogeys, score sous le par, vent et putting"
        "athletics" -> "classement live, qualification, médaille/podium, temps/distance, couloir, vent, forfaits et séries"
        "mma", "boxing" -> "issue du combat, round, KO/TKO, soumission/décision, cardio, dégâts, coupures et rythme"
        else -> "résultat global, score/état live, total, momentum, discipline, fatigue et acteurs clés"
    }

    private fun liveSportProfile(sport: String): LiveSportProfile = when (sport) {
        "soccer" -> LiveSportProfile(
            displayName = "football",
            unit = "buts",
            marginUnit = "but(s)",
            keyStats = "buts, tirs, tirs cadrés, xG si disponible, corners, cartons et possession",
            contextStats = "composition réelle, remplacements, cartons, domination territoriale et fatigue",
            nextAction = "but",
            nextActionType = "Prochain but",
            resultType = "Résultat 90 minutes live",
            totalType = "Total buts live",
            momentumType = "Momentum",
            closeType = "Score serré",
            defaultTotal = 2.4,
            expectedLateTotal = 4.2,
            variance = 1.15,
            closeMargin = 1.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("Prochain gros temps fort : tir cadré/corner", (0.42 + remaining * 0.18).coerceIn(0.40, 0.72), "Tirs/corners"),
                ProbabilityScenario("Carton ou changement tactique peut casser le scénario", 0.46, "Discipline"),
            ) },
        )
        "rugby" -> LiveSportProfile(
            displayName = "rugby",
            unit = "points",
            marginUnit = "points",
            keyStats = "essais, transformations, pénalités, touches, mêlées, plaquages, cartons et occupation",
            contextStats = "réussite au pied, mêlée fermée, touche, discipline, mètres après contact et banc",
            nextAction = "score rugby",
            nextActionType = "Prochain score",
            resultType = "Résultat rugby live",
            totalType = "Total points rugby",
            momentumType = "Momentum rugby",
            closeType = "Marge rugby",
            defaultTotal = 46.0,
            expectedLateTotal = 28.0,
            variance = 8.0,
            closeMargin = 7.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("Essai prochain quart-temps à surveiller", (0.30 + remaining * 0.25).coerceIn(0.28, 0.62), "Essais"),
                ProbabilityScenario("Pénalité/transformation décisive si match à une possession", 0.58, "Pied"),
                ProbabilityScenario("Mêlée/touche dominante peut créer la prochaine occasion", 0.55, "Conquête"),
                ProbabilityScenario("Carton jaune ou fatigue défensive à intégrer immédiatement", 0.48, "Discipline"),
            ) },
        )
        "basketball" -> LiveSportProfile(
            displayName = "basket",
            unit = "points",
            marginUnit = "points",
            keyStats = "points, rebonds, passes, interceptions, pertes de balle, fautes et réussite à 3 points",
            contextStats = "fautes des titulaires, rythme, rotations, adresse extérieure et rebond offensif",
            nextAction = "panier/série",
            nextActionType = "Prochaine série",
            resultType = "Vainqueur live",
            totalType = "Total points live",
            momentumType = "Run adverse",
            closeType = "Money time",
            defaultTotal = 168.0,
            expectedLateTotal = 60.0,
            variance = 14.0,
            closeMargin = 8.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("Run de 6-0 ou plus possible", (0.40 + remaining * 0.22).coerceAtMost(0.72), "Runs"),
                ProbabilityScenario("Total dépend fortement de l’adresse à 3 points", 0.57, "Adresse"),
                ProbabilityScenario("Fautes/rotations à surveiller sur meilleurs scoreurs", 0.55, "Joueurs"),
            ) },
        )
        "tennis" -> LiveSportProfile(
            displayName = "tennis",
            unit = "jeux",
            marginUnit = "jeu(x)",
            keyStats = "sets, jeux, breaks, premières balles, aces, doubles fautes et points gagnés au retour",
            contextStats = "surface, fatigue, qualité de service, balles de break et tendance mentale",
            nextAction = "jeu",
            nextActionType = "Prochain jeu",
            resultType = "Vainqueur match live",
            totalType = "Total jeux live",
            momentumType = "Break/momentum",
            closeType = "Set serré",
            defaultTotal = 22.0,
            expectedLateTotal = 14.0,
            variance = 4.5,
            closeMargin = 2.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("Break dans les deux prochains jeux à surveiller", (0.28 + remaining * 0.18).coerceAtMost(0.55), "Break"),
                ProbabilityScenario("Tie-break possible si les jeux de service restent solides", 0.43, "Tie-break"),
                ProbabilityScenario("Aces/service dominant peuvent verrouiller le set", 0.52, "Service"),
            ) },
        )
        "baseball" -> LiveSportProfile(
            displayName = "baseball",
            unit = "runs",
            marginUnit = "run(s)",
            keyStats = "runs, manches, lanceur partant, bullpen, hits, home runs, walks, strikeouts et erreurs",
            contextStats = "lanceur encore sur le monticule, bullpen disponible, ordre de batte, météo/vent et dimensions stade",
            nextAction = "run",
            nextActionType = "Prochain run",
            resultType = "Vainqueur baseball live",
            totalType = "Total runs live",
            momentumType = "Momentum inning",
            closeType = "Match à 1 run",
            defaultTotal = 8.5,
            expectedLateTotal = 6.0,
            variance = 2.6,
            closeMargin = 2.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("Bullpen fatigué peut ouvrir le score en fin de match", (0.40 + remaining * 0.18).coerceAtMost(0.66), "Bullpen"),
                ProbabilityScenario("Home run à surveiller si vent/stade et matchup batteur favorables", 0.46, "Home run"),
                ProbabilityScenario("Strikeouts lanceur à recalculer selon lineup et pitch count", 0.55, "Strikeouts"),
            ) },
        )
        "hockey" -> LiveSportProfile(
            displayName = "hockey",
            unit = "buts",
            marginUnit = "but(s)",
            keyStats = "buts, tirs, tirs cadrés, gardien, power play, penalty kill, pénalités et mise au jeu",
            contextStats = "gardien confirmé, supériorités numériques, fatigue back-to-back, temps de glace et discipline",
            nextAction = "but",
            nextActionType = "Prochain but",
            resultType = "Vainqueur hockey live",
            totalType = "Total buts live",
            momentumType = "Power play/momentum",
            closeType = "Match à 1 but",
            defaultTotal = 5.8,
            expectedLateTotal = 4.0,
            variance = 1.8,
            closeMargin = 1.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("But en supériorité à surveiller", (0.30 + remaining * 0.20).coerceAtMost(0.58), "Power play"),
                ProbabilityScenario("Volume de tirs peut faire basculer le gardien", 0.57, "Tirs"),
                ProbabilityScenario("Pénalité tardive peut inverser total et vainqueur", 0.50, "Discipline"),
            ) },
        )
        "field_hockey" -> LiveSportProfile(
            displayName = "hockey sur gazon",
            unit = "buts",
            marginUnit = "but(s)",
            keyStats = "buts, tirs cadrés, penalty corners, cartons, possession, gardien et efficacité offensive",
            contextStats = "penalty corners, discipline/cartons, surface, gardien, météo et domination territoriale",
            nextAction = "but/penalty corner",
            nextActionType = "Penalty corner",
            resultType = "Vainqueur hockey live",
            totalType = "Total buts live",
            momentumType = "Penalty corners",
            closeType = "Écart hockey",
            defaultTotal = 4.0,
            expectedLateTotal = 3.0,
            variance = 1.5,
            closeMargin = 1.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("Penalty corner peut créer le prochain but", (0.38 + remaining * 0.18).coerceAtMost(0.64), "Penalty corners"),
                ProbabilityScenario("Carton peut changer possession et total", 0.49, "Cartons"),
            ) },
        )
        "football", "australian_football" -> LiveSportProfile(
            displayName = if (sport == "football") "football américain" else "football australien",
            unit = "points",
            marginUnit = "points",
            keyStats = if (sport == "football") {
                "touchdowns, yards, QB, turnovers, sacks, red zone, 3rd down, field goals et météo"
            } else {
                "goals/behinds, disposals, marks, tackles, inside 50, clearances, météo et efficacité au tir"
            },
            contextStats = if (sport == "football") {
                "QB titulaire, ligne offensive, blessures, turnovers, horloge, jeu au sol et météo"
            } else {
                "pression, efficacité goals/behinds, milieu, fatigue, météo et matchups"
            },
            nextAction = if (sport == "football") "touchdown/field goal" else "goal/behind",
            nextActionType = "Prochain score",
            resultType = "Vainqueur live",
            totalType = "Total points live",
            momentumType = "Possession/momentum",
            closeType = "Écart live",
            defaultTotal = if (sport == "football") 45.0 else 165.0,
            expectedLateTotal = if (sport == "football") 28.0 else 70.0,
            variance = if (sport == "football") 9.0 else 18.0,
            closeMargin = if (sport == "football") 7.0 else 12.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("Turnover peut inverser le handicap immédiatement", 0.52, "Turnovers"),
                ProbabilityScenario("Red zone/inside 50 à suivre sur prochaine possession", (0.40 + remaining * 0.18).coerceAtMost(0.66), "Territoire"),
                ProbabilityScenario("Fatigue défensive et météo changent le total", 0.54, "Contexte"),
            ) },
        )
        "handball" -> LiveSportProfile(
            displayName = "handball",
            unit = "buts",
            marginUnit = "but(s)",
            keyStats = "buts, arrêts gardien, exclusions 2 minutes, pertes de balle, jets de 7m, ailes/pivots et jeu rapide",
            contextStats = "gardien chaud/froid, exclusions, rotations, défense 6-0/5-1, fatigue et efficacité jets de 7m",
            nextAction = "but",
            nextActionType = "Prochain but",
            resultType = "Vainqueur handball live",
            totalType = "Total buts live",
            momentumType = "Série rapide",
            closeType = "Écart handball",
            defaultTotal = 58.0,
            expectedLateTotal = 24.0,
            variance = 7.0,
            closeMargin = 5.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("Exclusion 2 minutes peut créer une série", (0.35 + remaining * 0.18).coerceAtMost(0.62), "Exclusions"),
                ProbabilityScenario("Gardien dominant peut faire baisser le total", 0.55, "Arrêts gardien"),
                ProbabilityScenario("Jets de 7m à intégrer si défense agressive", 0.52, "7 mètres"),
            ) },
        )
        "volleyball" -> LiveSportProfile(
            displayName = "volley",
            unit = "points",
            marginUnit = "point(s)",
            keyStats = "sets, points par set, aces, réception, blocks, efficacité attaque, erreurs directes et rotations",
            contextStats = "réception, passeur, service agressif, rotations, fatigue tournoi et matchup au filet",
            nextAction = "point/break",
            nextActionType = "Prochain break",
            resultType = "Vainqueur volley live",
            totalType = "Total points live",
            momentumType = "Set/momentum",
            closeType = "Set serré",
            defaultTotal = 185.0,
            expectedLateTotal = 60.0,
            variance = 18.0,
            closeMargin = 4.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("Set supplémentaire possible si réception équilibrée", (0.42 + remaining * 0.22).coerceAtMost(0.72), "Sets"),
                ProbabilityScenario("Aces/erreurs directes peuvent créer le prochain break", 0.56, "Service"),
                ProbabilityScenario("Blocks au filet à surveiller sur matchup central", 0.51, "Blocks"),
            ) },
        )
        "cricket" -> LiveSportProfile(
            displayName = "cricket",
            unit = "runs",
            marginUnit = "run(s)",
            keyStats = "runs, wickets, overs, run rate, required run rate, pitch, toss, powerplay et boundaries",
            contextStats = "wickets en main, état du pitch, météo/rosée, ordre de batte, lanceurs restants et run rate requis",
            nextAction = "wicket/run",
            nextActionType = "Prochain wicket/run",
            resultType = "Vainqueur cricket live",
            totalType = "Total runs live",
            momentumType = "Run rate/momentum",
            closeType = "Écart cricket",
            defaultTotal = 165.0,
            expectedLateTotal = 80.0,
            variance = 24.0,
            closeMargin = 12.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("Wicket powerplay ou death overs peut changer le match", (0.38 + remaining * 0.18).coerceAtMost(0.64), "Wickets"),
                ProbabilityScenario("Required run rate élevé augmente risque collapse", 0.55, "Run rate"),
                ProbabilityScenario("Batteur 30+/50+ runs à recalculer avec ordre de batte", 0.54, "Batteur"),
            ) },
        )
        "cycling" -> raceProfile(
            displayName = "cyclisme",
            keyStats = "écart échappée/peloton, kilomètres restants, profil, météo, favoris, chutes et rôles d’équipe",
            contextStats = "startlist, jambes des leaders, équipiers disponibles, vent, cols/sprint et abandons",
            resultType = "Scénario course live",
            nextAction = "attaque",
            nextActionType = "Attaque/échappée",
        )
        "racing" -> raceProfile(
            displayName = "Formule 1",
            keyStats = "tours, pneus, arrêts, écarts, DRS, safety car, météo, rythme long run et fiabilité",
            contextStats = "stratégie pneus, trafic, dégradation, incidents, météo et pénalités",
            resultType = "Course F1 live",
            nextAction = "dépassement/arrêt",
            nextActionType = "Stratégie",
        )
        "nascar" -> raceProfile(
            displayName = "NASCAR",
            keyStats = "tours, cautions, track position, pneus, rythme long run, pit stops et incidents",
            contextStats = "neutralisations, restart, stratégie carburant, usure pneus et position en piste",
            resultType = "Course NASCAR live",
            nextAction = "restart/caution",
            nextActionType = "Caution",
        )
        "golf" -> raceProfile(
            displayName = "golf",
            keyStats = "leaderboard, strokes gained, fairways, greens en régulation, putting, météo et cut",
            contextStats = "vent, trous restants, difficulté du parcours, putting chaud/froid et pression du cut",
            resultType = "Tournoi golf live",
            nextAction = "birdie/bogey",
            nextActionType = "Trou suivant",
        )
        "mma", "boxing" -> LiveSportProfile(
            displayName = if (sport == "mma") "MMA" else "boxe",
            unit = "rounds",
            marginUnit = "round(s)",
            keyStats = if (sport == "mma") "striking, takedowns, contrôle, soumissions, cardio, dégâts et défense" else "coups significatifs, knockdowns, jab, défense, cardio et rounds gagnés",
            contextStats = "dommages visibles, cardio, cut/blessure, plan de jeu et dynamique des juges",
            nextAction = if (sport == "mma") "finish/takedown" else "knockdown",
            nextActionType = if (sport == "mma") "Finish/grappling" else "Knockdown",
            resultType = "Combat live",
            totalType = "Durée combat",
            momentumType = "Momentum combat",
            closeType = "Décision",
            defaultTotal = 3.0,
            expectedLateTotal = 2.0,
            variance = 1.2,
            closeMargin = 1.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("Finish avant la limite à surveiller", (0.28 + remaining * 0.20).coerceAtMost(0.58), "Méthode"),
                ProbabilityScenario("Décision probable si cardio et défense tiennent", 0.56, "Décision"),
                ProbabilityScenario("Blessure/cut/dégâts peuvent inverser la projection", 0.50, "Risque combat"),
            ) },
        )
        "darts" -> LiveSportProfile(
            displayName = "fléchettes",
            unit = "legs",
            marginUnit = "leg(s)",
            keyStats = "moyenne 3 fléchettes, 180, checkout, doubles, legs/sets, break de lancer et pression",
            contextStats = "format, doubles sous pression, scoring récent, public, historique direct et fatigue tournoi",
            nextAction = "leg/checkout",
            nextActionType = "Prochain leg",
            resultType = "Vainqueur darts live",
            totalType = "Total legs live",
            momentumType = "Checkout/momentum",
            closeType = "Legs serrés",
            defaultTotal = 9.0,
            expectedLateTotal = 6.0,
            variance = 2.0,
            closeMargin = 2.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("180 prochain à surveiller si moyenne scoring élevée", 0.52, "180"),
                ProbabilityScenario("Checkout raté peut inverser le leg", (0.36 + remaining * 0.18).coerceAtMost(0.60), "Checkout"),
                ProbabilityScenario("Over legs si breaks de lancer et doubles instables", 0.58, "Total legs"),
            ) },
        )
        "snooker" -> LiveSportProfile(
            displayName = "snooker",
            unit = "frames",
            marginUnit = "frame(s)",
            keyStats = "frames, breaks 50+, centuries, safety, long pot, erreurs, ranking et format",
            contextStats = "format, table, sécurité, pression, scoring récent, fatigue tournoi et historique direct",
            nextAction = "frame/break",
            nextActionType = "Prochaine frame",
            resultType = "Vainqueur snooker live",
            totalType = "Total frames live",
            momentumType = "Break/momentum",
            closeType = "Frames serrées",
            defaultTotal = 9.0,
            expectedLateTotal = 5.0,
            variance = 2.2,
            closeMargin = 2.0,
            extraScenarios = { _, _, _, _ -> listOf(
                ProbabilityScenario("Break 50+ ou century à surveiller si scoring fluide", 0.54, "Breaks"),
                ProbabilityScenario("Frame tactique/safety peut ralentir le total", 0.52, "Sécurité"),
                ProbabilityScenario("Handicap frames à recalculer avec format et momentum", 0.56, "Handicap frames"),
            ) },
        )
        "athletics" -> raceProfile(
            displayName = "athlétisme",
            keyStats = "startlist, personal best, season best, séries/finale, couloir, vent, météo, temps/distance et forfaits",
            contextStats = "records saison, densité plateau, vent/couloir, fatigue des séries, blessures et conditions piste",
            resultType = "Épreuve athlétisme live",
            nextAction = "qualification/médaille",
            nextActionType = "Qualification/podium",
        )
        else -> genericProfile(sport)
    }

    private fun raceProfile(
        displayName: String,
        keyStats: String,
        contextStats: String,
        resultType: String,
        nextAction: String,
        nextActionType: String,
    ) = LiveSportProfile(
        displayName = displayName,
        unit = "positions",
        marginUnit = "position(s)",
        keyStats = keyStats,
        contextStats = contextStats,
        nextAction = nextAction,
        nextActionType = nextActionType,
        resultType = resultType,
        totalType = "Classement live",
        momentumType = "Momentum course",
        closeType = "Écart live",
        defaultTotal = 1.0,
        expectedLateTotal = 1.0,
        variance = 1.0,
        closeMargin = 1.0,
        extraScenarios = { _, _, remaining, _ -> listOf(
            ProbabilityScenario("Incident, météo ou neutralisation peut changer la course", (0.45 + remaining * 0.20).coerceAtMost(0.72), "Risque live"),
        ) },
        versusActions = false,
    )

    private fun genericProfile(sport: String): LiveSportProfile {
        val labels = when (sport) {
            "baseball" -> listOf("baseball", "runs, manches, lanceur, bullpen, hits, home runs et erreurs", "lanceurs, bullpen, lineups, météo et état du terrain", "run")
            "hockey", "field_hockey" -> listOf("hockey", "buts, tirs, power play, pénalités, gardiens et possession", "gardiens, supériorités numériques, fatigue et discipline", "but")
            "football", "australian_football" -> listOf("football", "touchdowns, yards, turnovers, quarterback, possessions et météo", "QB, blessures, turnovers, terrain et gestion horloge", "touchdown/score")
            "handball" -> listOf("handball", "buts, arrêts gardien, exclusions, pertes de balle, rythme et efficacité ailes/pivots", "gardiens, exclusions, rotations, jeu rapide et fatigue", "but")
            "volleyball" -> listOf("volley", "sets, points, aces, réception, blocks, efficacité attaque et erreurs", "réception, passeur, rotations, service et fautes directes", "point/break")
            "cricket" -> listOf("cricket", "runs, wickets, overs, run rate, pitch, batteurs et lanceurs", "toss, météo, pitch, wickets en main et run rate requis", "wicket/run")
            "darts" -> listOf("fléchettes", "moyenne 3 fléchettes, checkout, 180, legs/sets et doubles", "checkout, pression, format, legs restants et réussite doubles", "leg/checkout")
            "snooker" -> listOf("snooker", "frames, breaks, centuries, safety, erreurs et format", "frames restants, scoring, sécurité, table et pression", "frame/break")
            "athletics" -> listOf("athlétisme", "startlist, records saison, séries/finale, temps, vent et couloirs", "records, vent, séries, couloir, fatigue et forfaits", "qualification/médaille")
            else -> listOf("sport", "score, rythme, fautes, forme, absences et momentum", "score live, fatigue, rotations et contexte", "score")
        }
        return LiveSportProfile(
            displayName = labels[0],
            unit = if (sport in setOf("baseball", "cricket")) "runs" else "points",
            marginUnit = if (sport in setOf("baseball", "cricket")) "run(s)" else "point(s)",
            keyStats = labels[1],
            contextStats = labels[2],
            nextAction = labels[3],
            nextActionType = "Prochain ${labels[3]}",
            resultType = "Résultat live",
            totalType = "Total live",
            momentumType = "Momentum",
            closeType = "Fin serrée",
            defaultTotal = if (sport in setOf("baseball", "cricket")) 8.0 else 35.0,
            expectedLateTotal = if (sport in setOf("baseball", "cricket")) 6.0 else 20.0,
            variance = if (sport in setOf("baseball", "cricket")) 2.5 else 7.0,
            closeMargin = if (sport in setOf("baseball", "cricket")) 2.0 else 6.0,
            extraScenarios = { _, _, remaining, _ -> listOf(
                ProbabilityScenario("Momentum à recalculer après prochaine possession/phase", (0.48 + remaining * 0.16).coerceAtMost(0.70), "Momentum"),
                ProbabilityScenario("Stats joueurs clés à surveiller dès disponibles", 0.62, "Joueurs"),
                ProbabilityScenario("Discipline/blessure peut inverser la lecture live", 0.50, "Risque live"),
            ) },
            versusActions = sport !in setOf("athletics"),
        )
    }

    private fun totalOverProbability(projected: Double, line: Double, variance: Double): Double =
        sigmoid((projected - line) / variance).coerceIn(0.08, 0.92)

    private fun closeFinishProbability(diff: Int, remaining: Double, margin: Double): Double =
        sigmoid((margin + remaining * margin - diff) / margin).coerceIn(0.15, 0.88)

    private fun LiveSportProfile.mainLine(projected: Double): Double = when {
        projected < 5 -> (projected * 2.0).roundToInt() / 2.0
        projected < 30 -> (projected / 2.0).roundToInt() * 2.0
        else -> (projected / 5.0).roundToInt() * 5.0
    }.coerceAtLeast(0.5)

    private fun sigmoid(value: Double): Double = 1.0 / (1.0 + exp(-value))
    private fun decimal(value: Double): String = "%.1f".format(value).replace('.', ',')

    private fun liveVenueEdge(sport: String, homeName: String, awayName: String): LiveVenueEdge? {
        if (homeName.isBlank() || awayName.isBlank() || homeName == awayName) return null
        val strength = when (sport) {
            "soccer" -> 0.62
            "rugby" -> 0.64
            "basketball" -> 0.61
            "handball" -> 0.62
            "football" -> 0.62
            "australian_football" -> 0.60
            "hockey", "field_hockey" -> 0.59
            "volleyball" -> 0.58
            "baseball" -> 0.56
            "cricket" -> 0.57
            else -> return null
        }
        return LiveVenueEdge(
            summary = "Domicile/extérieur live : $homeName reçoit, $awayName se déplace ; contexte intégré sans écraser le score réel.",
            scenario = ProbabilityScenario("Avantage domicile $homeName à pondérer avec le score live", strength, "Domicile/extérieur"),
        )
    }

    private data class LiveSportProfile(
        val displayName: String,
        val unit: String,
        val marginUnit: String,
        val keyStats: String,
        val contextStats: String,
        val nextAction: String,
        val nextActionType: String,
        val resultType: String,
        val totalType: String,
        val momentumType: String,
        val closeType: String,
        val defaultTotal: Double,
        val expectedLateTotal: Double,
        val variance: Double,
        val closeMargin: Double,
        val homeLiveEdge: Double = 0.02,
        val extraScenarios: (LiveEventSnapshot, Double, Double, Boolean) -> List<ProbabilityScenario>,
        val versusActions: Boolean = true,
    )

    private data class LiveVenueEdge(
        val summary: String,
        val scenario: ProbabilityScenario,
    )
}
