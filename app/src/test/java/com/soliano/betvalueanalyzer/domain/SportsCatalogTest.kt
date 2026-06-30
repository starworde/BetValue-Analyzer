package com.soliano.betvalueanalyzer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsCatalogTest {
    @Test
    fun catalogContainsMajorSportsAndGrandSlams() {
        val keys = SportsCatalog.sports.map { it.key }
        assertTrue(keys.containsAll(listOf(
            "soccer",
            "basketball",
            "tennis",
            "rugby",
            "cycling",
            "racing",
            "nascar",
            "golf",
            "handball",
            "volleyball",
            "cricket",
            "athletics",
            "mma",
        )))
        assertTrue(!keys.contains("field_hockey"))
        assertTrue(!keys.contains("snooker"))

        val tennis = SportsCatalog.sports.first { it.key == "tennis" }
        assertTrue(tennis.competitions.any { it.name.contains("Wimbledon") })
        assertTrue(tennis.competitions.any { it.name.contains("Roland Garros") })

        val cycling = SportsCatalog.sports.first { it.key == "cycling" }
        assertTrue(cycling.competitions.any { it.name.contains("Tour de France") })

        val rugby = SportsCatalog.sports.first { it.key == "rugby" }
        assertTrue(rugby.competitions.any { it.name.contains("Top 14") })

        val volleyball = SportsCatalog.sports.first { it.key == "volleyball" }
        assertTrue(volleyball.competitions.any { it.name.contains("Nations League") })
        assertTrue(volleyball.competitions.any { it.name.contains("European Volleyball League") })

        assertEquals("Athlétisme", SportsCatalog.sports.first { it.key == "athletics" }.name)
        assertEquals("Fléchettes", SportsCatalog.sports.first { it.key == "darts" }.name)
    }

    @Test
    fun catalogIdentifiersAreUnique() {
        val competitions = SportsCatalog.sports.flatMap { it.competitions }
        assertEquals(competitions.size, competitions.map { it.key }.distinct().size)
    }

    @Test
    fun worldCupAliasesShareOneFilter() {
        assertEquals("Coupe du monde FIFA", canonicalCompetitionName("FIFA World Cup"))
        assertEquals(
            competitionFavoriteKey("soccer", "FIFA World Cup"),
            competitionFavoriteKey("soccer", "Coupe du monde FIFA"),
        )
    }
}
