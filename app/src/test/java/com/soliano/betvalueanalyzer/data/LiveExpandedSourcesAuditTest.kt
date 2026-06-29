package com.soliano.betvalueanalyzer.data

import com.soliano.betvalueanalyzer.data.remote.PublicSportsApiService
import com.soliano.betvalueanalyzer.domain.TeamProfileRequest
import com.soliano.betvalueanalyzer.domain.teamProfileKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveExpandedSourcesAuditTest {
    @Test
    fun `official FIFA and OpenLiga enrich team and player consensus`() = runBlocking {
        val teams = listOf("France", "Morocco", "Arsenal", "Real Madrid")
        val profiles = MultiSourceStatisticsProvider(PublicSportsApiService.create()).load(
            teams.map { TeamProfileRequest("soccer", "all", it) }
        )
        teams.forEach { team ->
            val profile = profiles.getValue(teamProfileKey("soccer", team))
            println("EXPANDED team=$team sources=${profile.sourceNames} agreement=${profile.sourceAgreement} snapshots=${profile.sourceSnapshots}")
            println("EXPANDED_PLAYERS team=$team ${profile.playerProfiles.filter { it.goals + it.assists + it.secondaryGoals + it.secondaryAssists > 0 }.take(8).map { "${it.name}:${it.sourceCount}:${it.goals}/${it.assists}:${it.secondaryGoals}/${it.secondaryAssists}" }}")
            assertTrue("FIFA missing for $team", "FIFA officiel" in profile.sourceNames)
        }
        val arsenal = profiles.getValue(teamProfileKey("soccer", "Arsenal"))
        val realMadrid = profiles.getValue(teamProfileKey("soccer", "Real Madrid"))
        assertTrue("OpenLigaDB" in arsenal.sourceNames)
        assertTrue("OpenLigaDB" in realMadrid.sourceNames)
        assertTrue(profiles.values.any { profile -> profile.playerProfiles.any { it.sourceCount >= 2 } })
    }
}
