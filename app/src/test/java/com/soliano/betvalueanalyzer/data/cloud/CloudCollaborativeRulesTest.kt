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
    }

    @Test
    fun `un resultat Firestore recent est recupere comme donnee cloud`() {
        val original = prediction().toCloudSharedResult("4.70.0", "anon-device", now)!!
        val recovered = cloudSharedResultFromMap(original.toFirestoreMap())
        val merge = mergeCloudResults(emptyList(), listOf(recovered!!), now)

        assertEquals(1, merge.predictionsToUpsert.size)
        assertTrue(merge.predictionsToUpsert.single().sourceName.contains("Cloud collaboratif"))
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
        betclicOdds = 1.80,
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
    )

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
