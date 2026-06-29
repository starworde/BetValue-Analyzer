package com.soliano.betvalueanalyzer.domain

data class TeamStatProfile(
    val teamName: String,
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val averageScored: Double,
    val averageConceded: Double,
    val averageShots: Double? = null,
    val averageShotsOnTarget: Double? = null,
    val averageCorners: Double? = null,
    val averagePossession: Double? = null,
    val recentLineup: TeamLineup? = null,
    val playerProfiles: List<PlayerStatProfile> = emptyList(),
    val sourceNames: List<String> = emptyList(),
    val sourceSnapshots: List<StatSourceSnapshot> = emptyList(),
    val sourceAgreement: Int = 50,
    val coachName: String? = null,
    val formTrend: Double = 0.0,
) {
    val winRate: Double get() = if (games > 0) wins.toDouble() / games else 0.5
    val drawRate: Double get() = if (games > 0) draws.toDouble() / games else 0.0
}

data class StatSourceSnapshot(
    val source: String,
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val averageScored: Double,
    val averageConceded: Double,
)

data class TeamLineup(
    val formation: String? = null,
    val players: List<LineupPlayer>,
    val status: LineupStatus,
)

data class LineupPlayer(
    val name: String,
    val position: String? = null,
    val jersey: String? = null,
)

enum class LineupStatus {
    OFFICIAL,
    PROBABLE,
}

data class PlayerStatProfile(
    val name: String,
    val appearances: Int,
    val starts: Int,
    val goals: Double = 0.0,
    val assists: Double = 0.0,
    val shots: Double = 0.0,
    val shotsOnTarget: Double = 0.0,
    val points: Double = 0.0,
    val rebounds: Double = 0.0,
    val hits: Double = 0.0,
    val homeRuns: Double = 0.0,
    val sourceCount: Int = 1,
    val secondaryGoals: Double = 0.0,
    val secondaryAssists: Double = 0.0,
    val availabilityNote: String? = null,
    val goalTrend: Double = 0.0,
    val assistTrend: Double = 0.0,
    val formTrend: Double = 0.0,
    val totalMinutes: Double = 0.0,
    val lastMatchMinutes: Double = 0.0,
    val averageMinutes: Double = 0.0,
    val heavyRecentLoadCount: Int = 0,
)

data class MatchContextSignal(
    val teamName: String,
    val title: String,
    val publishers: List<String>,
    val impact: Double,
    val category: String,
    val publishedAt: Long = 0L,
)

data class CompetitionStandingSignal(
    val teamName: String,
    val position: Int,
    val teamCount: Int,
    val points: Int,
    val gapToLeader: Int,
    val matchesRemaining: Int,
    val importance: Double,
    val description: String,
)

data class TeamProfileRequest(
    val sport: String,
    val league: String,
    val teamName: String,
    val suggestedTeamId: String? = null,
)

fun teamProfileKey(sport: String, teamName: String): String =
    "$sport:${teamName.lowercase().replace(Regex("[^a-z0-9à-ÿ]+"), "").trim()}"
