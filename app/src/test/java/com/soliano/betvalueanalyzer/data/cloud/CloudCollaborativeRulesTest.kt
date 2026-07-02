package com.soliano.betvalueanalyzer.data.cloud

import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.data.local.UpcomingEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCollaborativeRulesTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `un resultat local est envoye dans une forme Firestore legere`() {
        val cloud = prediction().toCloudSharedResult("4.70.0", "anon-device", now)

        assertNotNull(cloud)
        val map = cloud!!.toFirestoreMap()
        assertEquals("event-1", map["eventId"])
        assertEquals("soccer", map["sport"])
        assertEquals("Coupe du monde FIFA", map["competition"])
        assertEquals(83, map["reliability"])
        assertTrue((map["calculatedResults"] as String).contains("Argentine"))
        assertTrue((map["probabilities"] as String).contains("consensus"))
        assertTrue(map.containsKey("aiAnalysis"))
        assertTrue(map.containsKey("aiDiagnostic"))
        assertEquals(1_800_000_000_000L - 10_000L, map["aiGeneratedAt"])
    }

    @Test
    fun `un resultat Firestore recent est recupere comme donnee cloud`() {
        val original = prediction().toCloudSharedResult("4.70.0", "anon-device", now)!!
        val recovered = cloudSharedResultFromMap(original.toFirestoreMap())
        val merge = mergeCloudResults(emptyList(), listOf(recovered!!), now)

        assertEquals(1, merge.predictionsToUpsert.size)
        assertTrue(merge.predictionsToUpsert.single().sourceName.contains("Cloud collaboratif"))
        assertTrue(merge.predictionsToUpsert.single().aiAnalysis.contains("lectureRapide"))
    }

    @Test
    fun `Firestore indisponible reste un rapport non bloquant`() {
        val report = CloudSyncReport(
            enabled = true,
            firebaseAvailable = false,
            errorMessage = "Firebase non configuré ou indisponible",
            lastSyncEpoch = now,
        )

        assertFalse(report.firebaseAvailable)
        assertTrue(report.errorMessage.contains("Firebase"))
        assertEquals(0, report.mergedCount)
    }

    @Test
    fun `donnee cloud ancienne non utilisee comme fraiche`() {
        val oldCloud = prediction().toCloudSharedResult("4.70.0", "anon-device", now)!!
            .copy(updatedAt = now - 25 * 60 * 60 * 1000L)

        assertFalse(oldCloud.isCoherent(now))
        assertEquals(0, mergeCloudResults(emptyList(), listOf(oldCloud), now).predictionsToUpsert.size)
    }

    @Test
    fun `donnee vide non envoyee`() {
        val empty = prediction().copy(selection = "")

        assertEquals(null, empty.toCloudSharedResult("4.70.0", "anon-device", now))
    }

    @Test
    fun `upload ne rajeunit pas artificiellement une analyse ancienne`() {
        val sourceTime = now - 2 * 60 * 60 * 1000L
        val cloud = prediction()
            .copy(sourceLastUpdate = sourceTime)
            .toCloudSharedResult("4.70.0", "anon-device", now)

        assertEquals(sourceTime, cloud!!.updatedAt)
    }

    @Test
    fun `analyse locale trop ancienne non envoyee meme si upload lance maintenant`() {
        val stale = prediction()
            .copy(sourceLastUpdate = now - 25 * 60 * 60 * 1000L)

        assertEquals(null, stale.toCloudSharedResult("4.70.0", "anon-device", now))
    }

    @Test
    fun `donnee locale recente non remplacee par un cloud plus ancien`() {
        val local = prediction().copy(sourceLastUpdate = now)
        val olderCloud = prediction().toCloudSharedResult("4.70.0", "other-device", now - 5_000L)!!

        val merge = mergeCloudResults(listOf(local), listOf(olderCloud), now)

        assertEquals(0, merge.predictionsToUpsert.size)
    }

    @Test
    fun `local plus recent sans IA importe uniquement la vraie IA cloud`() {
        val local = prediction().copy(
            sourceLastUpdate = now,
            statSummary = "Stats locales recentes a conserver",
            sourceName = "ESPN local recent",
            aiAnalysis = "",
            aiDiagnostic = "",
            aiGeneratedAt = 0L,
        )
        val cloud = prediction().copy(
            sourceLastUpdate = now - 5_000L,
            statSummary = "Stats cloud anciennes a ne pas ecraser",
            aiAnalysis = validAiAnalysis("Lecture IA cloud importee"),
            aiDiagnostic = """{"iaRepondues":["GitHub Models via Actions"]}""",
            aiGeneratedAt = now - 4_000L,
        ).toCloudSharedResult("5.2.0", "github-actions", now)!!

        val merge = mergeCloudResults(listOf(local), listOf(cloud), now)
        val merged = merge.predictionsToUpsert.single()

        assertEquals(local.id, merged.id)
        assertEquals("Stats locales recentes a conserver", merged.statSummary)
        assertEquals("ESPN local recent", merged.sourceName)
        assertTrue(merged.aiAnalysis.contains("Lecture IA cloud importee"))
        assertTrue(merged.aiAnalysis.hasValidCloudAiAnalysis())
    }

    @Test
    fun `eventId different mais meme match date rattache IA au local`() {
        val local = prediction().copy(
            eventId = "local-provider:abc",
            sourceLastUpdate = now,
            aiAnalysis = "",
            aiDiagnostic = "",
            aiGeneratedAt = 0L,
        )
        val cloud = prediction().copy(
            eventId = "espn:tennis:atp:188-2026:177378",
            sourceLastUpdate = now - 5_000L,
            aiAnalysis = validAiAnalysis("IA rattachee malgre eventId different"),
            aiDiagnostic = """{"iaRepondues":["GitHub Models via Actions"]}""",
            aiGeneratedAt = now - 4_000L,
        ).toCloudSharedResult("5.2.0", "github-actions", now)!!

        val merge = mergeCloudResults(listOf(local), listOf(cloud), now)
        val merged = merge.predictionsToUpsert.single()

        assertEquals(local.id, merged.id)
        assertEquals("local-provider:abc", merged.eventId)
        assertTrue(merged.aiAnalysis.contains("IA rattachee"))
    }

    @Test
    fun `validation IA refuse fallback provider zero et JSON invalide`() {
        assertTrue(validAiAnalysis("Vraie IA").hasValidCloudAiAnalysis())
        assertFalse("""{"source":"local-preanalysis","providerCount":1,"lectureRapide":"fallback"}""".hasValidCloudAiAnalysis())
        assertFalse("""{"source":"multi-ai-cloud","providerCount":0,"lectureRapide":"zero"}""".hasValidCloudAiAnalysis())
        assertFalse(
            """{"source":"multi-ai-cloud","providerCount":1,"lectureRapide":"Ce que ca change : pas juste repris du tableau","avantageFavori":"signal present dans les donnees","dangerOutsider":"signal present dans les donnees","matchUpCle":"signal present dans les donnees","scenarioPrincipal":"doit etre lu comme une conclusion","scenarioAlternatif":"signal present dans les donnees"}"""
                .hasValidCloudAiAnalysis()
        )
        assertFalse("""{pas json}""".hasValidCloudAiAnalysis())
    }

    @Test
    fun `favori cree une demande IA prioritaire sans exposer la liste des favoris`() {
        val request = prediction().copy(aiAnalysis = "", aiGeneratedAt = 0L).toCloudAiAnalysisRequest(
            appVersion = "5.2.0",
            deviceId = "anon-device",
            now = now,
            favoriteSports = setOf("soccer"),
            favoriteCompetitions = setOf("soccer:coupe-du-monde-fifa"),
        )

        assertNotNull(request)
        assertTrue(request!!.priority > 100)
        assertEquals("competition_and_sport_priority", request.reason)
        val map = request.toFirestoreMap()
        assertEquals("ai_request", map["documentType"])
        assertEquals("pending", map["status"])
        val keys = map.keys.joinToString("|").lowercase()
        assertFalse(keys.contains("favorite"))
        assertFalse(keys.contains("favori"))
        assertFalse(keys.contains("history"))
    }

    @Test
    fun `non favori ne cree pas de demande IA prioritaire`() {
        val request = prediction().copy(aiAnalysis = "", aiGeneratedAt = 0L).toCloudAiAnalysisRequest(
            appVersion = "5.2.0",
            deviceId = "anon-device",
            now = now,
            favoriteSports = emptySet(),
            favoriteCompetitions = emptySet(),
        )

        assertEquals(null, request)
    }

    @Test
    fun `fiche ouverte cree une demande IA meme sans favori`() {
        val request = prediction().copy(aiAnalysis = "", aiGeneratedAt = 0L).toCloudAiAnalysisRequest(
            appVersion = "5.2.0",
            deviceId = "anon-device",
            now = now,
            favoriteSports = emptySet(),
            favoriteCompetitions = emptySet(),
            forceOpenedPriority = true,
        )

        assertNotNull(request)
        assertEquals("sport_priority", request!!.reason)
        assertTrue(request.priority >= 180)
        assertEquals("pending", request.status)
    }

    @Test
    fun `evenement calendrier ouvert cree une demande IA prioritaire`() {
        val request = upcomingEvent().toCloudAiAnalysisRequest(
            appVersion = "5.2.0",
            deviceId = "anon-device",
            now = now,
            favoriteSports = emptySet(),
            favoriteCompetitions = emptySet(),
            forceOpenedPriority = true,
        )

        assertNotNull(request)
        assertEquals("sport_priority", request!!.reason)
        assertTrue(request.priority >= 180)
    }

    @Test
    fun `aucune donnee personnelle nest envoyee`() {
        val keys = prediction().toCloudSharedResult("4.70.0", "anon-device", now)!!
            .toFirestoreMap()
            .keys
            .joinToString("|")
            .lowercase()

        assertFalse(keys.contains("favorite"))
        assertFalse(keys.contains("favori"))
        assertFalse(keys.contains("history"))
        assertFalse(keys.contains("historique"))
        assertFalse(keys.contains("bankroll"))
        assertFalse(keys.contains("log"))
        assertFalse(keys.contains("account"))
    }

    @Test
    fun `plusieurs appareils contribuent au meme document evenement`() {
        val first = prediction().toCloudSharedResult("4.70.0", "device-a", now)!!
        val second = prediction().toCloudSharedResult("4.70.0", "device-b", now + 1_000L)!!

        assertEquals(cloudDocumentIdFor(first.eventId), cloudDocumentIdFor(second.eventId))
        assertEquals("event-1", first.eventId)
        assertEquals("event-1", second.eventId)
    }

    @Test
    fun `un evenement calendrier sans pronostic est synchronisable sans devenir une prediction`() {
        val event = upcomingEvent().toCloudSharedCalendarEvent("4.77.0", "anon-device", now)

        assertNotNull(event)
        val map = event!!.toFirestoreMap()
        assertEquals("calendar_event", map["documentType"])
        assertEquals("event-1", map["eventId"])
        assertEquals("Volley-ball", map["sportTitle"])
        assertEquals("FIVB Volleyball Mens Nations League", map["competition"])
        assertEquals(null, cloudSharedResultFromMap(map))
    }

    @Test
    fun `diagnostic cloud exportable sans identifiant personnel brut`() {
        val report = CloudSyncReport(
            enabled = true,
            firebaseAvailable = true,
            fetchedCount = 3,
            lastSyncEpoch = now,
            diagnosticPath = "cache/cloud_collaboratif_diagnostic.txt",
        )

        assertTrue(report.diagnosticPath.endsWith("cloud_collaboratif_diagnostic.txt"))
        assertEquals(3, report.fetchedCount)
    }

    @Test
    fun `diagnostic github conserve sources vides et erreurs detaillees`() {
        val diagnostic = cloudJobDiagnosticFromMap(
            mapOf(
                "status" to "success",
                "configuredSports" to listOf("soccer", "volleyball", "boxing"),
                "eventsBySport" to mapOf("soccer" to 120, "volleyball" to 24),
                "sportsWithoutEvents" to listOf("baseball", "mma"),
                "resultsBySport" to mapOf("soccer" to 90),
                "sourceErrors" to listOf(
                    mapOf("source" to "UCI WorldTour", "error" to "HTTP 503"),
                    "Volleyball World : timeout",
                ),
                "aiFreeEnabled" to listOf("Gemini free tier", "Groq free tier"),
                "aiPaidDisabled" to listOf("OpenAI", "Claude / Anthropic"),
                "aiMode" to "double",
                "aiCalled" to 14,
                "aiResponded" to 12,
                "aiErrors" to listOf("Groq: quota"),
                "aiCacheHits" to 4,
                "aiFusionCount" to 6,
                "aiFallbackUsed" to 2,
                "aiQuotaReached" to true,
            )
        )

        assertEquals(listOf("soccer", "volleyball", "boxing"), diagnostic.configuredSports)
        assertEquals(24, diagnostic.eventsBySport["volleyball"])
        assertEquals(listOf("baseball", "mma"), diagnostic.sportsWithoutEvents)
        assertEquals(90, diagnostic.resultsBySport["soccer"])
        assertEquals(2, diagnostic.sourceErrorsCount)
        assertTrue(diagnostic.sourceErrors.any { it.contains("UCI WorldTour") })
        assertTrue(diagnostic.sourceErrors.any { it.contains("timeout") })
        assertEquals(listOf("Gemini free tier", "Groq free tier"), diagnostic.aiFreeEnabled)
        assertEquals("double", diagnostic.aiMode)
        assertEquals(14, diagnostic.aiCalled)
        assertEquals(12, diagnostic.aiResponded)
        assertEquals(4, diagnostic.aiCacheHits)
        assertEquals(6, diagnostic.aiFusionCount)
        assertEquals(2, diagnostic.aiFallbackUsed)
        assertTrue(diagnostic.aiErrors.single().contains("Groq"))
        assertTrue(diagnostic.aiQuotaReached)
    }

    @Test
    fun `erreur Firestore API desactivee affiche un message clair`() {
        val message = cloudCollaborativeErrorMessage(
            IllegalStateException(
                "PERMISSION_DENIED: Cloud Firestore API has not been used in project betvalue-analyzer before or it is disabled.",
            ),
        )

        assertTrue(message.contains("Firestore"))
        assertTrue(message.contains("Firebase Console"))
        assertFalse(message.contains("googleapis"))
        assertFalse(message.contains("https://"))
    }

    @Test
    fun `quota Firestore gratuit affiche un message clair`() {
        val message = cloudCollaborativeErrorMessage(
            IllegalStateException("RESOURCE_EXHAUSTED: Quota exceeded. HTTP 429"),
        )

        assertTrue(message.contains("Quota Firestore"))
        assertTrue(message.contains("Cache"))
    }

    private fun prediction(): PredictionEntity = PredictionEntity(
        id = "prediction-1",
        eventId = "event-1",
        sportKey = "soccer",
        sportTitle = "Football",
        competitionName = "Coupe du monde FIFA",
        commenceTime = now + 2 * 60 * 60 * 1000L,
        homeTeam = "Brésil",
        awayTeam = "Argentine",
        market = "Résultat final",
        selection = "Argentine",
        referenceOdds = 1.80,
        impliedProbability = 0.55,
        consensusProbability = 0.72,
        valueEdge = 0.17,
        expectedValue = 0.12,
        confidenceScore = 83,
        riskLevel = "Faible",
        category = "safe",
        bookmakerCount = 0,
        sourceName = "ESPN public",
        sourceLastUpdate = now - 10_000L,
        explanation = "Signal déjà calculé localement",
        positiveArguments = "Forme récente solide",
        negativeArguments = "Aucun fait relevé",
        expectedScore = "1 — 2",
        statSummary = "Argentine : 6 matchs, 2,1 buts marqués en moyenne",
        scenarios = "Résultat|Argentine|0.72",
        homeLineupStatus = "",
        homeLineup = "",
        awayLineupStatus = "",
        awayLineup = "",
        playerScenarios = "Messi but ou passe|0.61",
        sourceDetails = "ESPN public",
        contextInsights = "Aucun fait relevé",
        sourceAgreement = 78,
        aiAnalysis = validAiAnalysis("Argentine legerement devant mais a verifier."),
        aiDiagnostic = """{"iaGratuitesActivees":["Gemini free tier"],"iaRepondues":["Gemini free tier"],"coutEstime":"0 €"}""",
        aiGeneratedAt = now - 10_000L,
    )

    private fun validAiAnalysis(lecture: String): String =
        """{"source":"multi-ai-cloud","providerCount":1,"titreAnalyse":"Analyse IA test","lectureRapide":"$lecture. Lecture contextualisee avec rythme, forme et derniere information disponible.","avantageFavori":"Le favori garde une logique si sa qualite de creation, sa forme recente et son effectif confirme se recoupent.","dangerOutsider":"L outsider devient credible si le match se ferme, si le favori subit la transition ou si une absence cle pese.","matchUpCle":"Le duel decisif oppose la maitrise du rythme du favori a la capacite adverse a casser le tempo.","scenarioPrincipal":"Scenario principal : avantage court du favori apres un match serre et ajuste tactiquement.","scenarioAlternatif":"Scenario alternatif : match nul ou bascule outsider si les dernieres infos contredisent la compo attendue.","confianceIA":68,"modeleUtilise":"GitHub Models via Actions"}"""

    private fun upcomingEvent(): UpcomingEventEntity = UpcomingEventEntity(
        id = "event-1",
        sportKey = "volleyball",
        sportTitle = "Volley-ball",
        competitionKey = "volleyball:fivb-volleyball-mens-nations-league",
        competitionName = "FIVB Volleyball Mens Nations League",
        commenceTime = now + 2 * 60 * 60 * 1000L,
        eventName = "Canada Volleyball — Argentina Volleyball",
        participantA = "Canada Volleyball",
        participantB = "Argentina Volleyball",
        eventType = "MATCH",
        sourceName = "TheSportsDB",
        analysisId = null,
    )
}
