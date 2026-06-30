package com.soliano.betvalueanalyzer.domain

data class CatalogCompetition(
    val key: String,
    val sportKey: String,
    val name: String,
)

data class CatalogSport(
    val key: String,
    val name: String,
    val competitions: List<CatalogCompetition>,
)

object SportsCatalog {
    val sports = listOf(
        sport("soccer", "Football", listOf(
            "Premier League", "Ligue 1", "LaLiga", "Bundesliga", "Serie A",
            "UEFA Champions League", "UEFA Europa League", "UEFA Conference League", "Coupe du monde FIFA", "MLS",
        ), "all"),
        CatalogSport("basketball", "Basketball", listOf(
            competition("basketball", "nba", "NBA"),
            competition("basketball", "wnba", "WNBA"),
        )),
        CatalogSport("tennis", "Tennis", listOf(
            competition("tennis", "atp", "ATP · Australian Open"),
            competition("tennis", "atp", "ATP · Roland Garros"),
            competition("tennis", "atp", "ATP · Wimbledon"),
            competition("tennis", "atp", "ATP · US Open"),
            competition("tennis", "wta", "WTA · Australian Open"),
            competition("tennis", "wta", "WTA · Roland Garros"),
            competition("tennis", "wta", "WTA · Wimbledon"),
            competition("tennis", "wta", "WTA · US Open"),
        )),
        CatalogSport("rugby", "Rugby", listOf(
            competition("rugby", "all", "Top 14"),
            competition("rugby", "all", "Six Nations"),
            competition("rugby", "all", "Premiership Rugby"),
            competition("rugby", "all", "United Rugby Championship"),
            competition("rugby", "all", "Champions Cup"),
            competition("rugby", "all", "Coupe du monde de rugby"),
        )),
        CatalogSport("cycling", "Cyclisme", listOf(
            competition("cycling", "uci", "UCI WorldTour"),
            competition("cycling", "uci", "UCI Women's WorldTour"),
            competition("cycling", "uci", "UCI ProSeries"),
            competition("cycling", "uci", "Tour de France"),
            competition("cycling", "uci", "Giro d'Italia"),
            competition("cycling", "uci", "La Vuelta"),
            competition("cycling", "uci", "Paris-Roubaix"),
            competition("cycling", "uci", "Championnats du monde UCI"),
        )),
        CatalogSport("racing", "Formule 1", emptyList()),
        CatalogSport("nascar", "NASCAR", listOf(competition("nascar", "nascar-premier", "NASCAR Cup Series"))),
        CatalogSport("golf", "Golf", listOf(
            competition("golf", "pga", "PGA Tour"),
            competition("golf", "lpga", "LPGA Tour"),
            competition("golf", "tsdb-4758", "European Challenge Tour"),
        )),
        CatalogSport("mma", "MMA", listOf(competition("mma", "ufc", "UFC"))),
        CatalogSport("boxing", "Boxe", emptyList()),
        CatalogSport("australian_football", "Football australien", listOf(competition("australian_football", "tsdb-4456", "Australian AFL"))),
        CatalogSport("handball", "Handball", listOf(
            competition("handball", "tsdb-4980", "EHF Champions League"),
            competition("handball", "tsdb-5275", "EHF European League"),
            competition("handball", "tsdb-4536", "French LNH Division 1"),
        )),
        CatalogSport("volleyball", "Volley-ball", listOf(
            competition("volleyball", "volleyball-world-vnl", "Volleyball Nations League"),
            competition("volleyball", "tsdb-5083", "FIVB Volleyball Mens Nations League"),
            competition("volleyball", "tsdb-5084", "FIVB Volleyball Womens Nations League"),
            competition("volleyball", "tsdb-5848", "European Volleyball League"),
            competition("volleyball", "tsdb-5613", "Championnat d'Europe masculin"),
            competition("volleyball", "tsdb-5614", "CEV Challenge Cup"),
        )),
        CatalogSport("darts", "Fléchettes", listOf(competition("darts", "tsdb-4554", "PDC Darts"))),
        CatalogSport("cricket", "Cricket", listOf(
            competition("cricket", "tsdb-4461", "Big Bash League"),
            competition("cricket", "tsdb-5176", "Caribbean Premier League"),
            competition("cricket", "tsdb-5529", "Bangladesh Premier League"),
            competition("cricket", "tsdb-5530", "Sheffield Shield"),
        )),
        CatalogSport("athletics", "Athlétisme", listOf(
            competition("athletics", "tsdb-5007", "World Athletics Championships"),
            competition("athletics", "tsdb-5788", "World Athletics Ultimate Championship"),
            competition("athletics", "tsdb-5785", "World Athletics Indoor Tour Gold"),
        )),
        CatalogSport("baseball", "Baseball", listOf(competition("baseball", "mlb", "MLB"))),
        CatalogSport("hockey", "Hockey", listOf(competition("hockey", "nhl", "NHL"))),
        CatalogSport("football", "Football américain", listOf(competition("football", "nfl", "NFL"))),
    )

    @Suppress("UNUSED_PARAMETER")
    private fun sport(key: String, name: String, competitionNames: List<String>, league: String): CatalogSport =
        CatalogSport(key, name, competitionNames.map { competition(key, league, it) })

    private fun competition(sport: String, league: String, name: String): CatalogCompetition {
        return CatalogCompetition(competitionFavoriteKey(sport, name), sport, canonicalCompetitionName(name))
    }
}
