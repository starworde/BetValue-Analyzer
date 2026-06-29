package com.soliano.betvalueanalyzer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicPredictionEngineTest {
    @Test
    fun americanOddsAreConvertedToDecimal() {
        assertEquals(1.50, PublicPredictionEngine.americanToDecimal("-200")!!, 0.0001)
        assertEquals(3.00, PublicPredictionEngine.americanToDecimal("+200")!!, 0.0001)
    }

    @Test
    fun strongestWinnerIsSuggestedFromPublicMarketAndForm() {
        val result = PublicPredictionEngine.analyze(
            event(
                homeForm = "WWWWD",
                awayForm = "LLDWL",
                homeOdds = 1.50,
                awayOdds = 7.0,
                drawOdds = 4.2,
            )
        )

        val winner = result.first()
        assertEquals("France", winner.selection)
        assertEquals("Résultat du match (1N2)", winner.market)
        assertTrue(winner.estimatedProbability > 0.65)
        assertTrue(winner.sourceName.contains("ESPN"))
    }

    @Test
    fun totalSignalIsOnlyAddedWhenMarketLeansClearly() {
        val result = PublicPredictionEngine.analyze(
            event(
                homeOdds = 1.80,
                awayOdds = 2.10,
                totalLine = 2.5,
                overOdds = 2.45,
                underOdds = 1.55,
            )
        )

        assertTrue(result.any { it.market == "Total de buts" && it.selection == "Moins de 2,5 buts" })
    }

    @Test
    fun spreadCanBeTheSecondarySuggestion() {
        val result = PublicPredictionEngine.analyze(
            event(
                homeOdds = 1.65,
                awayOdds = 2.35,
                homeSpreadLine = -1.5,
                awaySpreadLine = 1.5,
                homeSpreadOdds = 2.60,
                awaySpreadOdds = 1.45,
            )
        )

        assertTrue(result.any { it.market == "Handicap buts" && it.selection == "Italie +1,5" })
        assertTrue(result.size <= 2)
    }

    @Test
    fun formAndRecordProduceAnalysisWithoutAnyOdds() {
        val result = PublicPredictionEngine.analyze(
            event(
                homeForm = "WWWWD",
                awayForm = "LLDLL",
                homeOdds = null,
                awayOdds = null,
            )
        )

        assertEquals("France", result.single().selection)
        assertEquals("Source test · forme et bilan", result.single().sourceName)
        assertTrue(result.single().confidenceScore >= 60)
    }

    @Test
    fun eventRemainsVisibleWhenStatisticsAreMissing() {
        val result = PublicPredictionEngine.analyze(
            event(homeOdds = null, awayOdds = null)
        )

        assertTrue(result.single().selection.contains("Attendre forme"))
        assertEquals("Données à compléter", result.single().category)
        assertTrue(result.single().scenarios.any { it.type.contains("Calendrier") })
    }

    @Test
    fun realProfilesProduceDistinctProbabilitiesAndShotScenarios() {
        val france = TeamStatProfile("France", 6, 5, 1, 0, 2.4, 0.6, 15.0, 6.0, 6.0, 57.0)
        val italy = TeamStatProfile("Italie", 6, 2, 2, 2, 1.1, 1.3, 9.0, 3.0, 4.0, 49.0)
        val prediction = PublicPredictionEngine.analyze(
            event(homeOdds = null, awayOdds = null, homeProfile = france, awayProfile = italy)
        ).first()

        assertTrue(kotlin.math.abs(prediction.estimatedProbability - 1.0 / 3.0) > 0.05)
        assertTrue(prediction.statSummary.any { it.contains("tirs") })
        assertTrue(prediction.scenarios.any { it.type == "Tirs" })
        assertTrue(prediction.expectedScore?.matches(Regex("\\d+ — \\d+")) == true)
        assertEquals("France gagne", prediction.selection)
        assertEquals("Issue à 90 minutes · hors prolongations", prediction.market)
        assertTrue(prediction.scenarios.any { it.type == "Score exact conditionnel" })
        val (homeScore, awayScore) = prediction.expectedScore!!.split(" — ").map(String::toInt)
        assertTrue(homeScore > awayScore)
        assertTrue(prediction.scenarios.any { it.label == "Plus de 1,5 buts" })
        assertTrue(prediction.scenarios.any { it.label == "Moins de 1,5 but" })
        assertTrue(prediction.scenarios.any { it.label == "Plus de 2,5 buts" })
        assertTrue(prediction.scenarios.any { it.label == "Moins de 2,5 buts" })
        assertTrue(prediction.scenarios.any { it.label == "Plus de 3,5 buts" })
        assertTrue(prediction.scenarios.any { it.label == "Moins de 3,5 buts" })
        val over25 = prediction.scenarios.single { it.label == "Plus de 2,5 buts" }.probability
        val under25 = prediction.scenarios.single { it.label == "Moins de 2,5 buts" }.probability
        assertEquals(1.0, over25 + under25, 0.0001)
    }

    @Test
    fun weakOutrightProbabilityIsReplacedBySaferDoubleChance() {
        val france = TeamStatProfile("France", 6, 3, 1, 2, 1.4, 1.1, sourceAgreement = 85)
        val italy = TeamStatProfile("Italie", 6, 3, 1, 2, 1.3, 1.1, sourceAgreement = 82)

        val prediction = PublicPredictionEngine.analyze(
            event(homeOdds = null, awayOdds = null, homeProfile = france, awayProfile = italy)
        ).first()

        assertEquals("Double chance à 90 minutes", prediction.market)
        assertTrue(prediction.estimatedProbability > 0.60)
        assertTrue(prediction.selection.contains("match nul"))
    }

    @Test
    fun confirmedRecentContextAdjustsTeamProbabilityWithoutOverridingStatistics() {
        val france = TeamStatProfile("France", 6, 5, 0, 1, 2.2, 0.7, sourceAgreement = 88)
        val italy = TeamStatProfile("Italie", 6, 2, 2, 2, 1.1, 1.4, sourceAgreement = 84)
        val baseEvent = event(homeOdds = null, awayOdds = null, homeProfile = france, awayProfile = italy)
        val base = PublicPredictionEngine.analyze(baseEvent).first()
        val contextual = PublicPredictionEngine.analyze(
            baseEvent.copy(
                contextSignals = listOf(
                    MatchContextSignal(
                        teamName = "France",
                        title = "Le sélectionneur sera absent",
                        publishers = listOf("Média A", "Média B"),
                        impact = -0.05,
                        category = "Encadrement",
                    )
                )
            )
        ).first()
        val baseHome = base.scenarios.single { it.label == "Victoire France" }.probability
        val contextualHome = contextual.scenarios.single { it.label == "Victoire France" }.probability

        assertTrue(contextualHome < baseHome)
    }

    @Test
    fun recentPlayerStatsProduceGoalAssistAndShotScenariosForDisplayedLineup() {
        val mbappe = PlayerStatProfile(
            name = "Kylian Mbappé",
            appearances = 3,
            starts = 3,
            goals = 3.0,
            assists = 1.0,
            shots = 11.0,
            shotsOnTarget = 6.0,
        )
        val replacement = PlayerStatProfile(
            name = "Olivier Giroud",
            appearances = 2,
            starts = 0,
            goals = 2.0,
            shotsOnTarget = 3.0,
        )
        val injured = PlayerStatProfile(
            name = "Joueur Blessé",
            appearances = 3,
            starts = 3,
            goals = 4.0,
            assists = 2.0,
            availabilityNote = "Blessure musculaire",
        )
        val france = TeamStatProfile(
            "France", 6, 5, 1, 0, 2.4, 0.6,
            playerProfiles = listOf(mbappe, replacement, injured),
        )
        val italy = TeamStatProfile("Italie", 6, 2, 2, 2, 1.1, 1.3)
        val lineup = TeamLineup(
            formation = "4-3-3",
            players = listOf(LineupPlayer("Kylian Mbappé", "AG", "10")),
            status = LineupStatus.OFFICIAL,
        )

        val prediction = PublicPredictionEngine.analyze(
            event(homeOdds = null, awayOdds = null, homeProfile = france, awayProfile = italy)
                .copy(homeLineup = lineup)
        ).first()

        assertEquals(LineupStatus.OFFICIAL, prediction.homeLineup?.status)
        assertTrue(prediction.playerScenarios.any { it.label.contains("Mbappé marque 1+ but") })
        assertTrue(prediction.playerScenarios.any { it.label.contains("passe décisive") })
        assertTrue(prediction.playerScenarios.any { it.type == "Joueur · But ou passe" })
        assertTrue(prediction.playerScenarios.any { it.type == "Joueur · But + passe" })
        assertTrue(prediction.playerScenarios.any { it.label.contains("Giroud") && it.label.contains("remplaçant possible") })
        assertFalse(prediction.playerScenarios.any { it.type == "Joueur · Tirs" || it.label.contains("tente") })
        assertFalse(prediction.playerScenarios.any { it.label.contains("Joueur Blessé") })
        assertTrue(prediction.playerScenarios.all { it.probability in 0.0..1.0 })
    }

    @Test
    fun seasonAdjustedPlayerTrendChangesPropsWithoutOverreacting() {
        fun prediction(trend: Double): PublicPrediction {
            val player = PlayerStatProfile(
                name = "Kylian Mbappé",
                appearances = 12,
                starts = 11,
                goals = 7.0,
                assists = 3.0,
                sourceCount = 3,
                secondaryGoals = 3.5,
                secondaryAssists = 1.5,
                formTrend = trend,
            )
            val france = TeamStatProfile("France", 6, 4, 1, 1, 2.0, 0.8, playerProfiles = listOf(player))
            val italy = TeamStatProfile("Italie", 6, 2, 2, 2, 1.1, 1.3)
            return PublicPredictionEngine.analyze(
                event(homeOdds = null, awayOdds = null, homeProfile = france, awayProfile = italy)
            ).first()
        }

        val rising = prediction(0.8).playerScenarios.first { it.label.contains("marque 1+ but") }.probability
        val falling = prediction(-0.8).playerScenarios.first { it.label.contains("marque 1+ but") }.probability

        assertTrue(rising > falling)
        assertTrue(rising - falling < 0.15)
    }

    @Test
    fun standingsUrgencyHasSmallBoundedImpactOnMatchProbability() {
        val france = TeamStatProfile("France", 6, 4, 1, 1, 2.0, 0.8, sourceAgreement = 85)
        val italy = TeamStatProfile("Italie", 6, 2, 2, 2, 1.1, 1.3, sourceAgreement = 82)
        val baseEvent = event(homeOdds = null, awayOdds = null, homeProfile = france, awayProfile = italy)
        val base = PublicPredictionEngine.analyze(baseEvent).first().scenarios.first { it.label == "Victoire France" }.probability
        val urgent = PublicPredictionEngine.analyze(
            baseEvent.copy(
                standingSignals = listOf(
                    CompetitionStandingSignal("France", 3, 4, 3, 3, 1, 0.95, "doit gagner pour se qualifier")
                )
            )
        ).first().scenarios.first { it.label == "Victoire France" }.probability

        assertTrue(urgent > base)
        assertTrue(urgent - base < 0.03)
    }

    @Test
    fun rugbyProfilesUseRugbyVocabularyAndPointScenarios() {
        val toulouse = TeamStatProfile("Toulouse", 6, 5, 0, 1, 31.0, 18.0, sourceAgreement = 84)
        val laRochelle = TeamStatProfile("La Rochelle", 6, 3, 0, 3, 24.0, 22.0, sourceAgreement = 78)

        val prediction = PublicPredictionEngine.analyze(
            event(
                homeOdds = null,
                awayOdds = null,
                homeProfile = toulouse,
                awayProfile = laRochelle,
                sportKey = "rugby/all",
                sportTitle = "Rugby",
                homeTeam = "Toulouse",
                awayTeam = "La Rochelle",
            )
        ).first()

        assertEquals("Vainqueur temps réglementaire", prediction.market)
        assertTrue(prediction.statSummary.any { it.contains("points marqués") })
        assertTrue(prediction.statSummary.any { it.contains("points encaissés") })
        assertTrue(prediction.scenarios.any { it.type == "Résultat rugby" })
        assertTrue(prediction.scenarios.any { it.type == "Total de points" })
        assertFalse(prediction.scenarios.any { it.label.contains("buts") })
        assertFalse(prediction.explanation.contains("90 minutes"))
    }

    @Test
    fun basketballProfilesProduceTeamPerformanceAndRhythmScenarios() {
        val lakers = TeamStatProfile("Lakers", 6, 4, 0, 2, 112.0, 104.0, sourceAgreement = 82)
        val celtics = TeamStatProfile("Celtics", 6, 5, 0, 1, 118.0, 106.0, sourceAgreement = 84)

        val prediction = PublicPredictionEngine.analyze(
            event(
                homeOdds = null,
                awayOdds = null,
                homeProfile = lakers,
                awayProfile = celtics,
                sportKey = "basketball/nba",
                sportTitle = "Basketball",
                homeTeam = "Lakers",
                awayTeam = "Celtics",
            )
        ).first()

        assertEquals("Vainqueur du match", prediction.market)
        assertTrue(prediction.scenarios.any { it.type == "Performance équipe" && it.label.contains("points") })
        assertTrue(prediction.scenarios.any { it.type == "Rythme" })
        assertFalse(prediction.scenarios.any { it.label.contains("buts") })
    }

    @Test
    fun baseballProfilesUseRunsAndNotGenericPoints() {
        val dodgers = TeamStatProfile("Dodgers", 6, 4, 0, 2, 5.1, 3.2, sourceAgreement = 76)
        val mets = TeamStatProfile("Mets", 6, 2, 0, 4, 3.4, 4.8, sourceAgreement = 72)

        val prediction = PublicPredictionEngine.analyze(
            event(
                homeOdds = null,
                awayOdds = null,
                homeProfile = dodgers,
                awayProfile = mets,
                sportKey = "baseball/mlb",
                sportTitle = "Baseball",
                homeTeam = "Dodgers",
                awayTeam = "Mets",
            )
        ).first()

        assertTrue(prediction.scenarios.any { it.type == "Runs" || it.type == "Total de runs" })
        assertTrue(prediction.statSummary.any { it.contains("runs marqués") })
        assertFalse(prediction.scenarios.any { it.label.contains("points") || it.label.contains("buts") })
    }

    @Test
    fun tennisLimitedAnalysisUsesTennisVocabulary() {
        val prediction = PublicPredictionEngine.analyze(
            event(
                homeOdds = null,
                awayOdds = null,
                sportKey = "tennis/atp",
                sportTitle = "Tennis",
                homeTeam = "Joueur A",
                awayTeam = "Joueur B",
            )
        ).single()

        assertEquals("Analyse tennis - données à compléter", prediction.market)
        assertTrue(prediction.statSummary.any { it.contains("surface") })
        assertTrue(prediction.scenarios.any { it.type == "Sets et jeux" })
        assertFalse(prediction.explanation.contains("90 minutes"))
        assertFalse(prediction.scenarios.any { it.label.contains("buts") })
    }

    @Test
    fun tennisFormOnlyAnalysisProducesTennisScenarios() {
        val prediction = PublicPredictionEngine.analyze(
            event(
                homeForm = "WWLWW",
                awayForm = "LLWLW",
                homeOdds = null,
                awayOdds = null,
                sportKey = "tennis/atp",
                sportTitle = "Tennis",
                homeTeam = "Joueur A",
                awayTeam = "Joueur B",
            )
        ).single()

        assertEquals("Vainqueur tennis - forme récente", prediction.market)
        assertTrue(prediction.expectedScore.orEmpty().contains("Avantage forme"))
        assertTrue(prediction.statSummary.any { it.contains("surface") })
        assertTrue(prediction.scenarios.any { it.type == "Sets" })
        assertFalse(prediction.scenarios.any { it.label.contains("buts") })
    }

    @Test
    fun rugbyPlayerDataCanProduceTryAndAssistScenarios() {
        val player = PlayerStatProfile(
            name = "Antoine Dupont",
            appearances = 4,
            starts = 4,
            goals = 2.0,
            assists = 3.0,
        )
        val toulouse = TeamStatProfile(
            "Toulouse", 6, 5, 0, 1, 31.0, 18.0,
            playerProfiles = listOf(player),
        )
        val laRochelle = TeamStatProfile("La Rochelle", 6, 3, 0, 3, 24.0, 22.0)

        val prediction = PublicPredictionEngine.analyze(
            event(
                homeOdds = null,
                awayOdds = null,
                homeProfile = toulouse,
                awayProfile = laRochelle,
                sportKey = "rugby/all",
                sportTitle = "Rugby",
                homeTeam = "Toulouse",
                awayTeam = "La Rochelle",
            )
        ).first()

        assertTrue(prediction.playerScenarios.any { it.type == "Joueur · Essai" && it.label.contains("essai") })
        assertTrue(prediction.playerScenarios.any { it.type == "Joueur · Passe" && it.label.contains("passe décisive") })
    }

    @Test
    fun limitedAnalysisUsesDedicatedVocabularyForEveryCatalogSport() {
        val expectedTokens = mapOf(
            "soccer" to listOf("buts", "corners"),
            "basketball" to listOf("rebonds", "passes"),
            "tennis" to listOf("surface", "service"),
            "rugby" to listOf("essais", "discipline"),
            "cycling" to listOf("startlist", "parcours"),
            "racing" to listOf("qualifications", "pneus"),
            "nascar" to listOf("qualifications", "rythme long run"),
            "golf" to listOf("strokes gained", "parcours"),
            "mma" to listOf("grappling", "soumission"),
            "boxing" to listOf("ko/tko", "rounds"),
            "australian_football" to listOf("goals/behinds", "marks"),
            "handball" to listOf("exclusions", "gardiens"),
            "volleyball" to listOf("réception", "aces"),
            "field_hockey" to listOf("penalty corners", "cartons"),
            "snooker" to listOf("frames", "centuries"),
            "darts" to listOf("checkout", "180"),
            "cricket" to listOf("wickets", "pitch"),
            "athletics" to listOf("records saison", "startlist"),
            "baseball" to listOf("lanceurs", "home runs"),
            "hockey" to listOf("gardiens", "power play"),
            "football" to listOf("quarterback", "touchdowns"),
        )

        SportsCatalog.sports.forEach { sport ->
            val prediction = PublicPredictionEngine.analyze(
                event(
                    homeOdds = null,
                    awayOdds = null,
                    sportKey = "${sport.key}/all",
                    sportTitle = sport.name,
                    homeTeam = "Participant A",
                    awayTeam = "Participant B",
                )
            ).single()
            val text = (
                prediction.market + " " +
                    prediction.selection + " " +
                    prediction.expectedScore.orEmpty() + " " +
                    prediction.explanation + " " +
                    prediction.statSummary.joinToString(" ") + " " +
                    prediction.scenarios.joinToString(" ") { "${it.type} ${it.label}" }
                ).lowercase()

            assertTrue("${sport.key} doit exposer plusieurs scénarios", prediction.scenarios.size >= 5)
            expectedTokens.getValue(sport.key).forEach { token ->
                assertTrue("${sport.key} doit contenir le vocabulaire '$token' dans : $text", text.contains(token))
            }
        }
    }

    @Test
    fun everyCatalogSportReceivesDeepStatPackInPrematchAnalysis() {
        SportsCatalog.sports.forEach { sport ->
            val prediction = PublicPredictionEngine.analyze(
                event(
                    homeOdds = null,
                    awayOdds = null,
                    sportKey = "${sport.key}/all",
                    sportTitle = sport.name,
                    homeTeam = "Participant A",
                    awayTeam = "Participant B",
                )
            ).single()
            val insight = SportIntelligenceCatalog.profile(sport.key)
            val summaryText = prediction.statSummary.joinToString(" ").lowercase()
            val scenarioText = prediction.scenarios.joinToString(" ") { "${it.type} ${it.label}" }.lowercase()

            assertTrue("${sport.key} doit injecter les stats clés", insight.watchedStats.take(3).all { summaryText.contains(it.substringBefore(" ").lowercase()) })
            assertTrue("${sport.key} doit injecter plus de scénarios pré-match", prediction.scenarios.size >= 10)
            assertTrue("${sport.key} doit contenir au moins un scénario métier", insight.probabilityScenarios.any { scenarioText.contains(it.type.lowercase()) })
            assertTrue("${sport.key} ne doit pas inventer d'angle joueur sans profil joueur", prediction.playerScenarios.isEmpty())
        }
    }

    @Test
    fun standaloneSportsExposeRichSportSpecificScenarioFamilies() {
        val checks = mapOf(
            "tennis" to listOf("Tie-break", "Breaks", "Handicap jeux"),
            "cycling" to listOf("Profil course", "Scénario course", "Risque course"),
            "golf" to listOf("Cut", "Top 20", "Duel joueur"),
            "mma" to listOf("Grappling", "Rounds", "Pesée"),
            "boxing" to listOf("Knockdown", "Rounds", "Pesée"),
            "racing" to listOf("Top 10", "Stratégie", "Fiabilité"),
            "nascar" to listOf("Cautions", "Track position", "Fiabilité"),
            "athletics" to listOf("Finale", "Records", "Duel"),
            "darts" to listOf("180", "Checkout", "Moyenne"),
            "snooker" to listOf("Centuries", "Total frames", "Sécurité"),
        )

        checks.forEach { (sportKey, requiredTypes) ->
            val prediction = PublicPredictionEngine.analyze(
                event(
                    homeOdds = null,
                    awayOdds = null,
                    sportKey = "$sportKey/all",
                    sportTitle = SportsCatalog.sports.first { it.key == sportKey }.name,
                    homeTeam = "Participant A",
                    awayTeam = "Participant B",
                )
            ).single()
            val types = prediction.scenarios.map { it.type }.toSet()
            assertTrue("$sportKey doit avoir au moins 6 scénarios", prediction.scenarios.size >= 6)
            requiredTypes.forEach { type ->
                assertTrue("$sportKey doit contenir le type $type parmi $types", type in types)
            }
        }
    }

    @Test
    fun secondaryMarketsUseSportSpecificUnits() {
        val tennisTotal = PublicPredictionEngine.analyze(
            event(
                homeOdds = 1.90,
                awayOdds = 1.90,
                sportKey = "tennis/atp",
                sportTitle = "Tennis",
                homeTeam = "Joueur A",
                awayTeam = "Joueur B",
                totalLine = 22.5,
                overOdds = 1.55,
                underOdds = 2.60,
            )
        ).first { it.market == "Total de jeux" }

        val baseballSpread = PublicPredictionEngine.analyze(
            event(
                homeOdds = 1.90,
                awayOdds = 1.90,
                sportKey = "baseball/mlb",
                sportTitle = "Baseball",
                homeTeam = "Dodgers",
                awayTeam = "Mets",
                homeSpreadLine = -1.5,
                awaySpreadLine = 1.5,
                homeSpreadOdds = 1.55,
                awaySpreadOdds = 2.60,
            )
        ).first { it.id.contains(":spread:") }

        val snookerTotal = PublicPredictionEngine.analyze(
            event(
                homeOdds = 1.90,
                awayOdds = 1.90,
                sportKey = "snooker/all",
                sportTitle = "Snooker",
                homeTeam = "Joueur A",
                awayTeam = "Joueur B",
                totalLine = 9.5,
                overOdds = 1.55,
                underOdds = 2.60,
            )
        ).first { it.market == "Total de frames" }

        assertTrue(tennisTotal.selection.contains("jeux"))
        assertEquals("Run line", baseballSpread.market)
        assertTrue(snookerTotal.selection.contains("frames"))
    }

    @Test
    fun basketballPlayerDataCanProducePointsReboundsAndAssists() {
        val player = PlayerStatProfile(
            name = "Nikola Jokic",
            appearances = 5,
            starts = 5,
            points = 135.0,
            rebounds = 62.0,
            assists = 48.0,
        )
        val nuggets = TeamStatProfile(
            "Nuggets", 6, 5, 0, 1, 116.0, 105.0,
            playerProfiles = listOf(player),
        )
        val suns = TeamStatProfile("Suns", 6, 3, 0, 3, 109.0, 111.0)

        val prediction = PublicPredictionEngine.analyze(
            event(
                homeOdds = null,
                awayOdds = null,
                homeProfile = nuggets,
                awayProfile = suns,
                sportKey = "basketball/nba",
                sportTitle = "Basketball",
                homeTeam = "Nuggets",
                awayTeam = "Suns",
            )
        ).first()

        assertTrue(prediction.playerScenarios.any { it.type == "Joueur · Points" })
        assertTrue(prediction.playerScenarios.any { it.type == "Joueur · Rebonds" && it.label.contains("rebonds") })
        assertTrue(prediction.playerScenarios.any { it.type == "Joueur · Passes" })
    }

    @Test
    fun homeAwayContextIsVisibleAndAffectsTeamSportPrediction() {
        val home = TeamStatProfile("Toulouse", 6, 4, 0, 2, 28.0, 20.0)
        val away = TeamStatProfile("La Rochelle", 6, 4, 0, 2, 28.0, 20.0)

        val prediction = PublicPredictionEngine.analyze(
            event(
                homeOdds = null,
                awayOdds = null,
                homeProfile = home,
                awayProfile = away,
                sportKey = "rugby/all",
                sportTitle = "Rugby",
                homeTeam = "Toulouse",
                awayTeam = "La Rochelle",
            )
        ).first()

        val homeProbability = prediction.scenarios.first { it.label == "Victoire Toulouse" }.probability
        val awayProbability = prediction.scenarios.first { it.label == "Victoire La Rochelle" }.probability

        assertTrue(prediction.statSummary.any { it.contains("Domicile/extérieur") })
        assertTrue(prediction.scenarios.any { it.type == "Domicile/extérieur" })
        assertTrue("le domicile doit départager deux profils égaux", homeProbability > awayProbability)
    }

    private fun event(
        homeForm: String? = null,
        awayForm: String? = null,
        homeOdds: Double?,
        awayOdds: Double?,
        drawOdds: Double? = null,
        totalLine: Double? = null,
        overOdds: Double? = null,
        underOdds: Double? = null,
        homeSpreadLine: Double? = null,
        awaySpreadLine: Double? = null,
        homeSpreadOdds: Double? = null,
        awaySpreadOdds: Double? = null,
        homeProfile: TeamStatProfile? = null,
        awayProfile: TeamStatProfile? = null,
        sportKey: String = "soccer/all",
        sportTitle: String = "Football",
        homeTeam: String = "France",
        awayTeam: String = "Italie",
    ) = PublicEvent(
        eventId = "test:1",
        sportKey = sportKey,
        sportTitle = sportTitle,
        competitionName = "Compétition test",
        dataSourceName = "Source test",
        commenceTime = 2_000_000_000_000,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        homeProfile = homeProfile,
        awayProfile = awayProfile,
        homeForm = homeForm,
        awayForm = awayForm,
        provider = "Marché test",
        homeOdds = homeOdds,
        awayOdds = awayOdds,
        drawOdds = drawOdds,
        totalLine = totalLine,
        overOdds = overOdds,
        underOdds = underOdds,
        homeSpreadLine = homeSpreadLine,
        awaySpreadLine = awaySpreadLine,
        homeSpreadOdds = homeSpreadOdds,
        awaySpreadOdds = awaySpreadOdds,
    )
}
