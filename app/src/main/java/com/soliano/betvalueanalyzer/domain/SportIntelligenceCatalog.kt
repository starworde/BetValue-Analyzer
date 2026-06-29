package com.soliano.betvalueanalyzer.domain

data class SportIntelligenceProfile(
    val sportKey: String,
    val watchedStats: List<String>,
    val playerStats: List<String>,
    val contextStats: List<String>,
    val probabilityScenarios: List<ProbabilityScenario>,
    val playerProbabilityScenarios: List<ProbabilityScenario>,
) {
    val summaryLines: List<String>
        get() = listOf(
            "Stats clés surveillées : ${watchedStats.joinToString(", ")}",
            "Stats joueurs suivies : ${playerStats.joinToString(", ")}",
            "Contexte à recouper : ${contextStats.joinToString(", ")}",
        )
}

object SportIntelligenceCatalog {
    fun profile(rawSportKey: String): SportIntelligenceProfile {
        val sport = rawSportKey.substringBefore('/')
        return profiles[sport] ?: genericProfile(sport)
    }

    val profiles: Map<String, SportIntelligenceProfile> = mapOf(
        "soccer" to profile(
            sportKey = "soccer",
            watchedStats = listOf("buts", "xG si disponible", "tirs", "tirs cadrés", "corners", "possession", "cartons", "clean sheet"),
            playerStats = listOf("buts", "passes décisives", "temps de jeu", "forme récente", "retour de blessure", "rôle titulaire/remplaçant"),
            contextStats = listOf("compositions", "absences", "fatigue calendrier", "enjeu classement", "météo", "arbitre"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Score exact à recalculer avec xG, tirs cadrés et compositions", 0.62, "Score exact"),
                ProbabilityScenario("Plus/Moins buts à recouper avec rythme, météo et absences offensives", 0.66, "Total buts"),
                ProbabilityScenario("Les deux équipes marquent à vérifier avec tirs cadrés et clean sheets", 0.58, "BTTS"),
                ProbabilityScenario("Corners à surveiller selon centres, domination territoriale et style", 0.55, "Corners"),
                ProbabilityScenario("Cartons à intégrer avec enjeu, arbitre et rivalité", 0.50, "Discipline"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Buteur probable à recalculer après composition officielle", 0.60, "Joueur · But"),
                ProbabilityScenario("Passeur probable à recalculer après rôle offensif confirmé", 0.54, "Joueur · Passe"),
            ),
        ),
        "basketball" to profile(
            sportKey = "basketball",
            watchedStats = listOf("pace", "points", "rebonds", "passes", "pertes de balle", "adresse 3 points", "lancers francs", "fautes"),
            playerStats = listOf("points", "rebonds", "passes", "3 points marqués", "minutes prévues", "usage rate", "fautes personnelles"),
            contextStats = listOf("rotations", "blessures", "back-to-back", "matchup défense", "rythme adverse", "garbage time"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Total points à recalculer avec pace et adresse extérieure", 0.68, "Total points"),
                ProbabilityScenario("Handicap points à vérifier avec rotations et absences", 0.62, "Handicap"),
                ProbabilityScenario("Rebonds équipe à surveiller selon taille et rythme", 0.57, "Rebonds"),
                ProbabilityScenario("Pertes de balle élevées possibles si pression défensive forte", 0.52, "Turnovers"),
                ProbabilityScenario("Money time serré si écart projeté sous 8 points", 0.60, "Fin de match"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Joueur clé : points à recalculer avec minutes et usage", 0.64, "Joueur · Points"),
                ProbabilityScenario("Joueur intérieur : rebonds à surveiller selon matchup", 0.58, "Joueur · Rebonds"),
                ProbabilityScenario("Meneur : passes à recalculer avec rythme et adresse coéquipiers", 0.57, "Joueur · Passes"),
            ),
        ),
        "tennis" to profile(
            sportKey = "tennis",
            watchedStats = listOf("surface", "aces", "doubles fautes", "premières balles", "points gagnés sur 1re/2e balle", "break points", "retour", "tie-breaks"),
            playerStats = listOf("main gauche/droite", "forme sur surface", "fatigue du tableau", "historique blessures", "face-à-face", "qualité service/retour"),
            contextStats = listOf("surface rapide/lente", "indoor/outdoor", "enchaînement de matchs", "météo/vent", "abandon récent", "format sets"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Aces à recalculer avec surface et puissance au service", 0.59, "Aces"),
                ProbabilityScenario("Breaks à surveiller avec retour et secondes balles", 0.58, "Breaks"),
                ProbabilityScenario("Tie-break possible si les deux services tiennent", 0.54, "Tie-break"),
                ProbabilityScenario("Victoire 2-0/2-1 à recalculer avec fatigue et surface", 0.56, "Sets"),
                ProbabilityScenario("Handicap jeux à vérifier avec historique de breaks", 0.53, "Handicap jeux"),
                ProbabilityScenario("Doubles fautes à intégrer si vent, pression ou retour agressif", 0.49, "Doubles fautes"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Joueur : 5+ aces à recalculer avec surface/service", 0.55, "Joueur · Aces"),
                ProbabilityScenario("Joueur : break concédé à surveiller avec seconde balle", 0.52, "Joueur · Break"),
            ),
        ),
        "rugby" to profile(
            sportKey = "rugby",
            watchedStats = listOf("essais", "transformations", "pénalités", "touches réussies", "mêlées gagnées", "plaquages", "turnovers", "cartons"),
            playerStats = listOf("essais joueur", "buteur", "pénalités réussies", "transformations réussies", "plaquages", "mètres gagnés", "retour de blessure"),
            contextStats = listOf("composition", "charnière", "météo", "arbitre", "discipline", "puissance mêlée", "qualité touche", "banc"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Total points à recalculer avec météo, buteurs et discipline", 0.66, "Total points rugby"),
                ProbabilityScenario("Essais équipe à surveiller avec occupation et mètres gagnés", 0.62, "Essais"),
                ProbabilityScenario("Pénalités réussies importantes si défense indisciplinée", 0.58, "Pénalités"),
                ProbabilityScenario("Transformations réussies à intégrer selon buteur titulaire", 0.56, "Transformations"),
                ProbabilityScenario("Touches réussies/mêlées gagnées peuvent créer le prochain avantage", 0.57, "Conquête"),
                ProbabilityScenario("Carton jaune à surveiller selon arbitre et historique discipline", 0.48, "Discipline"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Buteur : pénalités + transformations à recalculer après composition", 0.60, "Joueur · Pied"),
                ProbabilityScenario("Finisseur : essai à surveiller selon aile/centre titulaire", 0.55, "Joueur · Essai"),
                ProbabilityScenario("Avant : volume plaquages à surveiller selon temps de jeu", 0.54, "Joueur · Plaquages"),
            ),
        ),
        "baseball" to profile(
            sportKey = "baseball",
            watchedStats = listOf("lanceurs probables", "ERA/FIP", "bullpen", "hits", "home runs", "walks", "strikeouts", "stade/météo"),
            playerStats = listOf("coups sûrs", "home runs", "RBI", "runs", "strikeouts lanceur", "lineup spot"),
            contextStats = listOf("lanceur partant", "repos bullpen", "vent", "dimensions stade", "lineup confirmé", "forme batteurs"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Total runs à recalculer avec lanceurs et météo", 0.66, "Total runs"),
                ProbabilityScenario("Run line à vérifier avec bullpen et starter", 0.60, "Run line"),
                ProbabilityScenario("Home run possible si matchup batteur/stade concorde", 0.51, "Home runs"),
                ProbabilityScenario("Strikeouts lanceur à surveiller avec lineup adverse", 0.57, "Strikeouts"),
                ProbabilityScenario("Late game instable si bullpen fatigué", 0.55, "Bullpen"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Batteur : 1+ coup sûr à recalculer avec lineup", 0.60, "Joueur · Coups sûrs"),
                ProbabilityScenario("Batteur : home run à surveiller avec météo/stade", 0.42, "Joueur · Home run"),
                ProbabilityScenario("Lanceur : strikeouts à recalculer avec lineup adverse", 0.56, "Joueur · Strikeouts"),
            ),
        ),
        "hockey" to profile(
            sportKey = "hockey",
            watchedStats = listOf("buts", "tirs", "gardiens titulaires", "power play", "penalty kill", "pénalités", "repos", "xG si disponible"),
            playerStats = listOf("buts", "assists", "tirs", "temps de glace", "power play unit", "gardien confirmé"),
            contextStats = listOf("gardien titulaire", "back-to-back", "blessures", "supériorités", "discipline", "forme offensive"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Total buts à recalculer après gardien titulaire", 0.68, "Total buts"),
                ProbabilityScenario("Power play peut peser si pénalités adverses élevées", 0.58, "Power play"),
                ProbabilityScenario("Match à 1 but d’écart probable si gardiens solides", 0.57, "Écart"),
                ProbabilityScenario("Tirs équipe à surveiller avec volume offensif récent", 0.56, "Tirs"),
                ProbabilityScenario("But en supériorité à surveiller selon unités spéciales", 0.52, "Supériorité"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Attaquant : but/assist à recalculer avec ligne et power play", 0.55, "Joueur · Point"),
                ProbabilityScenario("Gardien : arrêts à surveiller selon volume de tirs", 0.58, "Joueur · Arrêts"),
            ),
        ),
        "field_hockey" to profile(
            sportKey = "field_hockey",
            watchedStats = listOf("buts", "tirs cadrés", "penalty corners", "cartons", "possession", "gardien", "efficacité offensive"),
            playerStats = listOf("buts", "penalty corners provoqués", "passes décisives", "cartons", "temps de jeu"),
            contextStats = listOf("forme récente", "surface", "discipline", "gardien", "efficacité penalty corner", "météo"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Penalty corners à recalculer avec domination territoriale", 0.60, "Penalty corners"),
                ProbabilityScenario("Total buts à surveiller selon gardiens et tirs cadrés", 0.58, "Total buts"),
                ProbabilityScenario("Cartons peuvent changer la possession et le total", 0.49, "Cartons"),
                ProbabilityScenario("Clean sheet possible si gardien et défense dominent", 0.47, "Clean sheet"),
                ProbabilityScenario("Écart d’un but à surveiller si niveaux proches", 0.55, "Écart"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Joueur : but ou assist à recalculer avec rôle sur penalty corner", 0.52, "Joueur · But/assist"),
            ),
        ),
        "handball" to profile(
            sportKey = "handball",
            watchedStats = listOf("buts", "arrêts gardiens", "exclusions 2 minutes", "pertes de balle", "efficacité ailes", "jeu rapide", "jets de 7m", "rotations"),
            playerStats = listOf("buts joueur", "passes décisives", "jets de 7m", "temps de jeu", "arrêts gardien", "exclusions"),
            contextStats = listOf("gardien titulaire", "rythme", "défense 6-0/5-1", "fatigue", "rotations", "discipline"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Total buts à recalculer avec gardiens et rythme", 0.67, "Total buts"),
                ProbabilityScenario("Exclusions 2 minutes peuvent créer des séries rapides", 0.55, "Discipline"),
                ProbabilityScenario("Jets de 7m à surveiller selon agressivité défensive", 0.52, "7 mètres"),
                ProbabilityScenario("Écart inférieur à 5 buts si gardiens équilibrés", 0.58, "Écart"),
                ProbabilityScenario("Score équipe 25+ buts à vérifier avec rythme", 0.60, "Performance équipe"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Arrière/ailier : buts à recalculer avec temps de jeu", 0.58, "Joueur · Buts"),
                ProbabilityScenario("Gardien : arrêts à surveiller selon volume tirs", 0.54, "Joueur · Arrêts"),
            ),
        ),
        "volleyball" to profile(
            sportKey = "volleyball",
            watchedStats = listOf("sets", "aces", "réception", "blocks", "efficacité attaque", "erreurs directes", "rotations", "points par set"),
            playerStats = listOf("points", "aces", "blocks", "réception", "attaques gagnantes", "rôle titulaire"),
            contextStats = listOf("passeur", "réception", "service agressif", "fatigue tournoi", "rotation", "matchup au filet"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Match en 4 ou 5 sets si réception équilibrée", 0.62, "Sets"),
                ProbabilityScenario("Aces à surveiller avec service agressif/réception fragile", 0.54, "Aces"),
                ProbabilityScenario("Handicap sets à recalculer avec efficacité attaque", 0.56, "Handicap sets"),
                ProbabilityScenario("Blocks élevés possibles si mismatch au filet", 0.51, "Blocks"),
                ProbabilityScenario("Total points set à vérifier avec erreurs directes", 0.55, "Total points"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Attaquant : points à recalculer avec volume d’attaques", 0.58, "Joueur · Points"),
                ProbabilityScenario("Central : blocks à surveiller selon matchup", 0.50, "Joueur · Blocks"),
            ),
        ),
        "cricket" to profile(
            sportKey = "cricket",
            watchedStats = listOf("runs", "wickets", "overs", "run rate", "toss", "pitch", "météo", "powerplay"),
            playerStats = listOf("runs batteur", "wickets lanceur", "strike rate", "economy rate", "boundaries", "ordre de batte"),
            contextStats = listOf("format", "toss", "état du pitch", "rosée", "météo", "lineups", "wickets en main"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Total runs à recalculer après toss et lecture pitch", 0.67, "Total runs"),
                ProbabilityScenario("Wickets powerplay à surveiller selon swing/pitch", 0.54, "Wickets"),
                ProbabilityScenario("Run rate requis peut créer un collapse tardif", 0.52, "Run rate"),
                ProbabilityScenario("Batteur 30+/50+ runs à surveiller avec ordre de batte", 0.56, "Runs joueur"),
                ProbabilityScenario("Lanceur 2+ wickets à recalculer selon matchup", 0.50, "Wickets joueur"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Batteur : 30+ runs à recalculer avec ordre de batte", 0.55, "Joueur · Runs"),
                ProbabilityScenario("Lanceur : 2+ wickets à surveiller avec pitch", 0.48, "Joueur · Wickets"),
            ),
        ),
        "football" to profile(
            sportKey = "football",
            watchedStats = listOf("touchdowns", "yards", "quarterback", "turnovers", "3rd down", "red zone", "sacks", "météo"),
            playerStats = listOf("TD joueur", "yards passe/course/réception", "réceptions", "interceptions", "touches", "snap count"),
            contextStats = listOf("QB titulaire", "ligne offensive", "blessures", "météo", "rythme", "turnovers", "jeu au sol"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Total points à recalculer avec QB, météo et turnovers", 0.65, "Total points"),
                ProbabilityScenario("Touchdown joueur à surveiller avec red zone usage", 0.56, "Touchdowns"),
                ProbabilityScenario("Yards QB/RB/WR à recalculer avec matchup", 0.58, "Yards"),
                ProbabilityScenario("Turnovers peuvent inverser handicap et total", 0.52, "Turnovers"),
                ProbabilityScenario("Match à moins d’un touchdown si styles équilibrés", 0.58, "Écart"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Joueur offensif : touchdown à recalculer avec rôle red zone", 0.54, "Joueur · Touchdown"),
                ProbabilityScenario("QB/RB/WR : yards à surveiller selon volume prévu", 0.58, "Joueur · Yards"),
            ),
        ),
        "australian_football" to profile(
            sportKey = "australian_football",
            watchedStats = listOf("goals/behinds", "disposals", "marks", "tackles", "inside 50", "clearances", "météo", "efficacité tir"),
            playerStats = listOf("goals joueur", "disposals", "marks", "tackles", "rôle milieu/avant"),
            contextStats = listOf("météo", "terrain", "forme effectif", "matchup", "pression", "efficacité conversion"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Total points à recalculer avec météo et efficacité conversion", 0.63, "Total points AFL"),
                ProbabilityScenario("Goals joueur à surveiller avec rôle offensif", 0.55, "Goals joueur"),
                ProbabilityScenario("Disposals/marks à recalculer avec rôle milieu", 0.56, "Disposals"),
                ProbabilityScenario("Tackles élevés possibles si match sous pression", 0.52, "Tackles"),
                ProbabilityScenario("Écart serré si efficacité goals/behinds proche", 0.57, "Écart"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Avant : 2+ goals à recalculer avec rôle offensif", 0.50, "Joueur · Goals"),
                ProbabilityScenario("Milieu : disposals à surveiller selon temps au centre", 0.58, "Joueur · Disposals"),
            ),
        ),
        "cycling" to raceProfile(
            sportKey = "cycling",
            watchedStats = listOf("startlist", "parcours", "dénivelé", "météo", "vent", "forme coureurs", "rôles d’équipe", "abandons"),
            playerStats = listOf("forme coureur", "rôle leader/équipier", "sprint", "grimpeur", "CLM", "historique course", "retour blessure"),
            contextStats = listOf("profil étape", "échappée", "contrôle peloton", "météo", "chutes", "objectifs d’équipe"),
            resultType = "Course cycliste",
            playerType = "Coureur",
        ),
        "racing" to raceProfile(
            sportKey = "racing",
            watchedStats = listOf("essais libres", "qualifications", "grille", "rythme long run", "pneus", "dégradation", "météo", "safety car"),
            playerStats = listOf("pilote", "rythme course", "qualif", "pneus", "fiabilité", "pénalités", "duels coéquipiers"),
            contextStats = listOf("stratégie", "trafic", "météo", "safety car", "fiabilité moteur", "track position"),
            resultType = "Course F1",
            playerType = "Pilote",
        ),
        "nascar" to raceProfile(
            sportKey = "nascar",
            watchedStats = listOf("qualifications", "track position", "rythme long run", "cautions", "pneus", "pit stops", "draft", "fiabilité"),
            playerStats = listOf("pilote", "top 5/top 10", "duels", "historique piste", "restart", "incidents"),
            contextStats = listOf("cautions", "restart", "stratégie carburant", "usure pneus", "météo", "trafic"),
            resultType = "Course NASCAR",
            playerType = "Pilote",
        ),
        "golf" to raceProfile(
            sportKey = "golf",
            watchedStats = listOf("field", "strokes gained", "driving accuracy", "greens en régulation", "putting", "scrambling", "météo", "cut"),
            playerStats = listOf("golfeur", "top 5/top 10/top 20", "passer le cut", "birdies", "bogeys", "historique parcours"),
            contextStats = listOf("profil parcours", "vent", "forme putting", "approche", "pression cut", "fatigue voyage"),
            resultType = "Tournoi golf",
            playerType = "Golfeur",
        ),
        "mma" to combatProfile("mma", "MMA", listOf("striking", "takedowns", "défense takedown", "soumissions", "contrôle", "cardio", "reach", "pesée")),
        "boxing" to combatProfile("boxing", "boxe", listOf("volume de coups", "jab", "knockdowns", "KO/TKO", "reach", "cardio", "défense", "pesée")),
        "athletics" to profile(
            sportKey = "athletics",
            watchedStats = listOf("startlist", "records saison", "personal best", "séries/finale", "couloir", "vent", "météo", "forme récente"),
            playerStats = listOf("athlète", "temps/distance", "record saison", "qualification", "podium", "duel", "fatigue séries"),
            contextStats = listOf("vent", "couloir", "densité plateau", "forfaits", "calendrier séries", "conditions piste"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Qualification finale à recalculer avec séries et densité", 0.64, "Qualification"),
                ProbabilityScenario("Podium à surveiller avec season best et startlist", 0.55, "Podium"),
                ProbabilityScenario("Record saison/personnel possible si météo favorable", 0.48, "Records"),
                ProbabilityScenario("Duel athlètes à recalculer après séries", 0.58, "Duel"),
                ProbabilityScenario("Vent/couloir peut modifier temps et classement", 0.56, "Contexte"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Athlète : qualification finale à surveiller", 0.60, "Athlète · Qualification"),
                ProbabilityScenario("Athlète : podium/médaille à recalculer avec startlist", 0.50, "Athlète · Podium"),
            ),
        ),
        "darts" to profile(
            sportKey = "darts",
            watchedStats = listOf("moyenne 3 fléchettes", "checkout", "180", "doubles", "legs/sets", "ranking", "forme", "face-à-face"),
            playerStats = listOf("180 joueur", "checkout", "moyenne", "legs gagnés", "pression doubles", "format sets"),
            contextStats = listOf("format court/long", "scène", "ranking", "forme récente", "historique direct", "pression public"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Total 180 à recalculer avec moyenne récente", 0.58, "180"),
                ProbabilityScenario("Checkout élevé à surveiller avec réussite doubles", 0.52, "Checkout"),
                ProbabilityScenario("Handicap legs/sets à vérifier avec format", 0.57, "Handicap"),
                ProbabilityScenario("Over legs si niveaux proches et format long", 0.60, "Total legs"),
                ProbabilityScenario("Moyenne 3 fléchettes peut signaler le favori réel", 0.62, "Moyenne"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Joueur : 180 à surveiller avec volume scoring", 0.52, "Joueur · 180"),
                ProbabilityScenario("Joueur : checkout à recalculer avec pression doubles", 0.50, "Joueur · Checkout"),
            ),
        ),
        "snooker" to profile(
            sportKey = "snooker",
            watchedStats = listOf("ranking", "frames", "centuries", "breaks 50+", "safety", "long pot", "forme", "format"),
            playerStats = listOf("century joueur", "break 50+", "frames gagnées", "sécurité", "historique direct", "forme scoring"),
            contextStats = listOf("format court/long", "table", "pression", "ranking", "fatigue tournoi", "jeu de sécurité"),
            probabilityScenarios = listOf(
                ProbabilityScenario("Centuries/breaks élevés à recalculer avec forme scoring", 0.55, "Centuries"),
                ProbabilityScenario("Total frames over si niveaux proches", 0.60, "Total frames"),
                ProbabilityScenario("Handicap frames à vérifier avec format", 0.56, "Handicap frames"),
                ProbabilityScenario("Sécurité/tactical frames peut ralentir le score", 0.52, "Sécurité"),
                ProbabilityScenario("Break 50+ à surveiller avec scoring récent", 0.57, "Breaks"),
            ),
            playerProbabilityScenarios = listOf(
                ProbabilityScenario("Joueur : century/break 50+ à surveiller", 0.51, "Joueur · Breaks"),
                ProbabilityScenario("Joueur : handicap frames à recalculer avec format", 0.55, "Joueur · Frames"),
            ),
        ),
    )

    private fun profile(
        sportKey: String,
        watchedStats: List<String>,
        playerStats: List<String>,
        contextStats: List<String>,
        probabilityScenarios: List<ProbabilityScenario>,
        playerProbabilityScenarios: List<ProbabilityScenario>,
    ): SportIntelligenceProfile {
        val universalWatchedStats = listOf("charge récente", "forme saison", "blessure/retour", "info live officielle")
        val universalPlayerStats = listOf("volume récent vs moyenne saison", "état physique", "rôle confirmé", "fatigue spécifique")
        val universalContextStats = listOf("calendrier récent", "actualité confirmée", "sources officielles", "écart avec moyenne saison")
        val universalScenarios = listOf(
            ProbabilityScenario("Signal à recalculer si une info blessure/retour est confirmée", 0.62, "État joueur/équipe"),
            ProbabilityScenario("Charge récente à comparer à la moyenne saison avant signal fort", 0.60, "Fatigue"),
            ProbabilityScenario("Probabilité renforcée seulement si plusieurs sources publiques concordent", 0.70, "Fiabilité multi-source"),
        )
        val universalPlayerScenarios = listOf(
            ProbabilityScenario("Joueur : performance à ajuster si charge récente au-dessus de sa moyenne saison", 0.58, "Joueur · Fatigue"),
            ProbabilityScenario("Joueur : retour de blessure à intégrer avant validation", 0.55, "Joueur · État physique"),
            ProbabilityScenario("Joueur : rôle titulaire/remplaçant à confirmer avant pari performance", 0.57, "Joueur · Rôle"),
        )
        return SportIntelligenceProfile(
            sportKey = sportKey,
            watchedStats = (watchedStats + universalWatchedStats).distinct(),
            playerStats = (playerStats + universalPlayerStats).distinct(),
            contextStats = (contextStats + universalContextStats).distinct(),
            probabilityScenarios = (probabilityScenarios + universalScenarios).distinctBy { it.type + it.label },
            playerProbabilityScenarios = (playerProbabilityScenarios + universalPlayerScenarios).distinctBy { it.type + it.label },
        )
    }

    private fun raceProfile(
        sportKey: String,
        watchedStats: List<String>,
        playerStats: List<String>,
        contextStats: List<String>,
        resultType: String,
        playerType: String,
    ): SportIntelligenceProfile = profile(
        sportKey = sportKey,
        watchedStats = watchedStats,
        playerStats = playerStats,
        contextStats = contextStats,
        probabilityScenarios = listOf(
            ProbabilityScenario("$resultType : vainqueur/podium à éviter sans données complètes", 0.50, "Vainqueur/podium"),
            ProbabilityScenario("Top 10/top 20 à recalculer avec forme et contexte", 0.64, "Top classement"),
            ProbabilityScenario("Duel $playerType à surveiller avec historique direct", 0.58, "Duel"),
            ProbabilityScenario("Risque incident/météo/abandon à intégrer", 0.54, "Risque"),
            ProbabilityScenario("Signal fort seulement si au moins deux sources concordent", 0.70, "Fiabilité"),
        ),
        playerProbabilityScenarios = listOf(
            ProbabilityScenario("$playerType : podium/top 10 à recalculer après infos officielles", 0.58, "$playerType · Classement"),
            ProbabilityScenario("$playerType : duel à surveiller si matchup clair", 0.55, "$playerType · Duel"),
        ),
    )

    private fun combatProfile(sportKey: String, display: String, watchedStats: List<String>): SportIntelligenceProfile = profile(
        sportKey = sportKey,
        watchedStats = watchedStats,
        playerStats = listOf("méthode victoire", "KO/TKO", "soumission", "décision", "rounds", "reach", "cardio", "blessure/cut"),
        contextStats = listOf("pesée", "camp", "changement adversaire", "opposition styles", "cardio", "blessure", "juge/arbitre"),
        probabilityScenarios = listOf(
            ProbabilityScenario("$display : vainqueur à recalculer avec styles et forme", 0.52, "Vainqueur"),
            ProbabilityScenario("Méthode KO/TKO ou soumission à vérifier avec taux de finish", 0.55, "Méthode"),
            ProbabilityScenario("Over rounds/décision à surveiller avec cardio et défense", 0.58, "Rounds"),
            ProbabilityScenario("Finish rapide possible si mismatch striking/grappling", 0.48, "Finish"),
            ProbabilityScenario("Pesée/blessure/cut peuvent inverser la projection", 0.56, "Pesée"),
        ),
        playerProbabilityScenarios = listOf(
            ProbabilityScenario("Combattant : méthode de victoire à recalculer après pesée", 0.52, "Combattant · Méthode"),
            ProbabilityScenario("Combattant : distance/over rounds à surveiller avec cardio", 0.56, "Combattant · Rounds"),
        ),
    )

    private fun genericProfile(sport: String): SportIntelligenceProfile = profile(
        sportKey = sport,
        watchedStats = listOf("score", "forme récente", "rythme", "absences", "classement", "météo", "discipline", "momentum"),
        playerStats = listOf("temps de jeu", "forme", "blessure", "rôle", "performance récente", "retour de blessure"),
        contextStats = listOf("calendrier", "enjeu", "fatigue", "sources officielles", "actualité", "conditions"),
        probabilityScenarios = listOf(
            ProbabilityScenario("Résultat à recalculer dès données spécifiques disponibles", 0.50, "Résultat"),
            ProbabilityScenario("Total/score à surveiller selon rythme et contexte", 0.58, "Total"),
            ProbabilityScenario("Performance joueur à attendre avec rôle confirmé", 0.55, "Joueurs"),
            ProbabilityScenario("Risque blessure/absence à intégrer", 0.52, "Risque"),
            ProbabilityScenario("Signal fort seulement si plusieurs sources concordent", 0.70, "Fiabilité"),
        ),
        playerProbabilityScenarios = listOf(
            ProbabilityScenario("Joueur clé : performance à recalculer après rôle confirmé", 0.55, "Joueur · Performance"),
        ),
    )
}
