package com.soliano.betvalueanalyzer.data

import com.soliano.betvalueanalyzer.data.local.AnalysisDao
import com.soliano.betvalueanalyzer.data.local.SportDao
import com.soliano.betvalueanalyzer.data.local.SportEntity
import com.soliano.betvalueanalyzer.domain.RemovedSports
import kotlinx.coroutines.flow.Flow

class AnalysisRepository(
    private val analysisDao: AnalysisDao,
    private val sportDao: SportDao,
) {
    val sports: Flow<List<SportEntity>> = sportDao.observeEnabled()

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
        sportDao.deleteByNames(
            listOf(
                "Hockey sur gazon",
                "Snooker",
                "Football australien",
                "Fléchettes",
                "Fléchettes / Darts",
                "Darts",
                "Cricket",
            )
        )
        analysisDao.deleteDemoRecords()
    }

    private fun defaultSports(): List<SportEntity> {
        val groups = linkedMapOf(
            "Collectifs" to listOf(
                "Football", "Basketball", "Rugby à XV", "Rugby à XIII", "Football américain",
                "Baseball", "Futsal", "Handball",
                "Hockey sur glace", "Volley-ball", "Beach-volley", "Water-polo",
            ),
            "Raquette et précision" to listOf(
                "Tennis", "Tennis de table", "Badminton", "Padel", "Squash", "Golf",
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
                "Curling", "Courses hippiques", "Sports virtuels", "Paris spéciaux",
                "Événements spéciaux",
            ),
        )
        return groups.flatMap { (category, sports) ->
            sports.filterNot(RemovedSports::isRemovedSportName).map { SportEntity(name = it, category = category) }
        }
    }

}
