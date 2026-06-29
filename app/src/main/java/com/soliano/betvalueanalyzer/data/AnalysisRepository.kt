package com.soliano.betvalueanalyzer.data

import com.soliano.betvalueanalyzer.data.local.AnalysisDao
import com.soliano.betvalueanalyzer.data.local.AnalysisRecordEntity
import com.soliano.betvalueanalyzer.data.local.SportDao
import com.soliano.betvalueanalyzer.data.local.SportEntity
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.domain.AnalysisEngine
import com.soliano.betvalueanalyzer.domain.ManualAnalysisInput
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AnalysisRepository(
    private val analysisDao: AnalysisDao,
    private val sportDao: SportDao,
) {
    val analyses: Flow<List<AnalysisRecordEntity>> = analysisDao.observeAll()
    val sports: Flow<List<SportEntity>> = sportDao.observeEnabled()

    suspend fun create(input: ManualAnalysisInput): Long {
        val result = AnalysisEngine.analyze(input)
        return analysisDao.insert(
            AnalysisRecordEntity(
                sport = input.sport,
                competition = input.competition.trim(),
                participantA = input.participantA.trim(),
                participantB = input.participantB.trim(),
                eventDate = input.eventDate.trim(),
                market = input.market.trim(),
                selection = input.selection.trim(),
                odds = input.odds,
                formA = input.formA,
                formB = input.formB,
                dataQuality = input.dataQuality,
                uncertainty = input.uncertainty,
                homeAdvantage = input.homeAdvantage,
                contextNote = input.contextNote.trim(),
                impliedProbability = result.impliedProbability,
                estimatedProbability = result.estimatedProbability,
                valueEdge = result.valueEdge,
                expectedValue = result.expectedValue,
                confidenceScore = result.confidenceScore,
                confidenceLabel = result.confidenceLabel,
                riskLevel = result.riskLevel,
                valueLevel = result.valueLevel,
                category = result.category,
                positiveArguments = result.positiveArguments.joinToString("\n"),
                negativeArguments = result.negativeArguments.joinToString("\n"),
                explanation = result.explanation,
                suggestedStakePercent = result.suggestedStakePercent,
            )
        )
    }

    suspend fun updateOutcome(id: Long, outcome: String) = analysisDao.updateOutcome(id, outcome)

    suspend fun trackPrediction(prediction: PredictionEntity): Long {
        val eventDate = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(prediction.commenceTime))
        val stake = when {
            prediction.expectedValue <= 0.0 || prediction.confidenceScore < 45 -> 0.0
            prediction.riskLevel == "Élevé" -> 0.25
            prediction.confidenceScore >= 90 -> 1.5
            prediction.confidenceScore >= 75 -> 1.0
            prediction.confidenceScore >= 60 -> 0.5
            else -> 0.25
        }
        return analysisDao.insert(
            AnalysisRecordEntity(
                sport = prediction.sportTitle,
                competition = "Flux Internet · ${prediction.sourceName}",
                participantA = prediction.homeTeam,
                participantB = prediction.awayTeam,
                eventDate = eventDate,
                market = prediction.market,
                selection = prediction.selection,
                odds = prediction.betclicOdds,
                formA = 50,
                formB = 50,
                dataQuality = (prediction.bookmakerCount * 10).coerceIn(20, 95),
                uncertainty = (100 - prediction.confidenceScore).coerceIn(5, 80),
                homeAdvantage = false,
                contextNote = "Prédiction automatique calculée depuis ${prediction.sourceName}. La cote affichée est un repère public.",
                impliedProbability = prediction.impliedProbability,
                estimatedProbability = prediction.consensusProbability,
                valueEdge = prediction.valueEdge,
                expectedValue = prediction.expectedValue,
                confidenceScore = prediction.confidenceScore,
                confidenceLabel = com.soliano.betvalueanalyzer.domain.confidenceLabel(prediction.confidenceScore),
                riskLevel = prediction.riskLevel,
                valueLevel = when {
                    prediction.expectedValue >= 0.12 -> "Forte"
                    prediction.expectedValue >= 0.04 -> "Positive"
                    prediction.expectedValue >= -0.02 -> "Faible"
                    else -> "Négative"
                },
                category = prediction.category,
                positiveArguments = prediction.positiveArguments,
                negativeArguments = prediction.negativeArguments,
                explanation = prediction.explanation,
                suggestedStakePercent = stake,
            )
        )
    }

    suspend fun delete(id: Long) = analysisDao.delete(id)

    suspend fun addCustomSport(name: String) {
        if (name.isNotBlank()) {
            sportDao.insert(
                SportEntity(
                    name = name.trim(),
                    category = "Personnalisé",
                    isCustom = true,
                )
            )
        }
    }

    suspend fun seedIfNeeded() {
        if (sportDao.count() == 0) sportDao.insertAll(defaultSports())
        analysisDao.deleteDemoRecords()
    }

    private fun defaultSports(): List<SportEntity> {
        val groups = linkedMapOf(
            "Collectifs" to listOf(
                "Football", "Basketball", "Rugby à XV", "Rugby à XIII", "Football américain",
                "Football australien", "Baseball", "Futsal", "Handball", "Hockey sur gazon",
                "Hockey sur glace", "Volley-ball", "Beach-volley", "Water-polo",
            ),
            "Raquette et précision" to listOf(
                "Tennis", "Tennis de table", "Badminton", "Padel", "Squash", "Golf",
                "Snooker", "Fléchettes / Darts",
            ),
            "Endurance et hiver" to listOf(
                "Cyclisme", "Biathlon", "Ski alpin", "Ski de fond", "Saut à ski", "Athlétisme",
                "Natation", "Voile",
            ),
            "Mécaniques" to listOf(
                "Formule 1", "Formule 2", "MotoGP", "NASCAR", "Rallye", "Speedway",
            ),
            "Combat" to listOf("MMA", "Boxe", "Sports de combat"),
            "E-sport" to listOf(
                "E-sport", "Counter-Strike 2", "League of Legends", "Dota 2", "Valorant",
                "King of Glory / KoG",
            ),
            "Autres" to listOf(
                "Cricket", "Curling", "Courses hippiques", "Sports virtuels", "Paris spéciaux",
                "Événements spéciaux",
            ),
        )
        return groups.flatMap { (category, sports) ->
            sports.map { SportEntity(name = it, category = category) }
        }
    }

}
