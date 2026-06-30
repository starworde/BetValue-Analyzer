package com.soliano.betvalueanalyzer.domain

enum class AnalysisReadiness(
    val label: String,
    val category: String,
    val confidence: Int,
) {
    Reliable("Analyse fiable", "Analyse fiable", 72),
    Medium("Signal moyen", "Signal moyen", 60),
    Weak("Données faibles", "Données faibles", 38),
    WatchOnly("Surveillance seulement", "Surveillance seulement", 30),
    Impossible("Projection impossible pour l’instant", "Surveillance seulement", 22),
}

data class SportAnalysisRule(
    val sportKey: String,
    val displayName: String,
    val readiness: AnalysisReadiness,
    val market: String,
    val selection: String,
    val expectedState: String,
    val explanation: String,
    val requiredStats: List<String>,
    val actorStats: List<String>,
    val contextStats: List<String>,
    val scenarios: List<ProbabilityScenario>,
    val forbiddenVocabulary: List<String> = emptyList(),
) {
    val baselineProbability: Double
        get() = when (readiness) {
            AnalysisReadiness.Reliable -> 0.62
            AnalysisReadiness.Medium -> 0.56
            AnalysisReadiness.Weak -> 0.50
            AnalysisReadiness.WatchOnly -> 0.50
            AnalysisReadiness.Impossible -> 0.50
        }
}

object SportAnalysisRulebook {
    fun rule(rawSportKey: String): SportAnalysisRule {
        val sport = rawSportKey.substringBefore('/')
        return rules[sport] ?: generic(sport)
    }

    val rules: Map<String, SportAnalysisRule> = mapOf(
        "soccer" to SportAnalysisRule(
            sportKey = "soccer",
            displayName = "football",
            readiness = AnalysisReadiness.Weak,
            market = "Analyse football - données à compléter",
            selection = "Attendre forme, compositions, xG/tirs, absences et contexte",
            expectedState = "Projection non validée sans données football recoupées",
            explanation = "Le match est détecté. Avant de sortir un signal, l’app doit croiser forme récente, tirs/xG, compositions, absences, fatigue, enjeu, météo et terrain neutre/domicile confirmé.",
            requiredStats = listOf("buts marqués/encaissés", "xG si disponible", "tirs", "tirs cadrés", "corners", "possession", "clean sheets"),
            actorStats = listOf("buts joueur", "passes décisives", "temps de jeu", "titulaire/remplaçant", "retour blessure", "carton/suspension"),
            contextStats = listOf("compositions", "absences", "fatigue calendrier", "enjeu classement", "météo", "arbitre", "terrain neutre"),
            scenarios = listOf(
                ProbabilityScenario("Surveillance : résultat à 90 minutes seulement après forme et compositions", 0.50, "Données faibles"),
                ProbabilityScenario("Score exact à activer uniquement avec xG, tirs cadrés et compositions", 0.50, "Score exact conditionnel"),
                ProbabilityScenario("Buteur/passeur à activer seulement si titulaire et rôle offensif confirmés", 0.50, "Joueurs football"),
                ProbabilityScenario("Total buts à recalculer avec rythme, météo et absences offensives", 0.50, "Total buts"),
                ProbabilityScenario("Corners/cartons à surveiller si style, arbitre et enjeu concordent", 0.50, "Corners / discipline"),
            ),
        ),
        "basketball" to SportAnalysisRule(
            sportKey = "basketball",
            displayName = "basketball",
            readiness = AnalysisReadiness.Weak,
            market = "Analyse basketball - données à compléter",
            selection = "Attendre rythme, rotations, adresse, rebonds et blessures",
            expectedState = "Projection non validée sans pace, rotations et état joueurs",
            explanation = "Le match est détecté. Le basket doit être lu avec pace, adresse, rebonds, pertes de balle, rotations, blessures, back-to-back et profondeur de banc.",
            requiredStats = listOf("pace", "points marqués/encaissés", "adresse 3 points", "rebonds", "passes", "pertes de balle", "fautes"),
            actorStats = listOf("points", "rebonds", "passes", "minutes", "usage rate", "fatigue/back-to-back"),
            contextStats = listOf("rotations", "blessures", "back-to-back", "matchup défense", "garbage time", "déplacement"),
            scenarios = listOf(
                ProbabilityScenario("Surveillance : vainqueur à attendre sans rotations et blessures recoupées", 0.50, "Données faibles"),
                ProbabilityScenario("Total points à recalculer avec pace et adresse extérieure", 0.50, "Total points"),
                ProbabilityScenario("Handicap points seulement si écart moyen et rotations concordent", 0.50, "Handicap points"),
                ProbabilityScenario("Joueurs points/rebonds/passes seulement avec minutes prévues fiables", 0.50, "Joueurs basket"),
                ProbabilityScenario("Fin de match serrée à surveiller si l’écart projeté reste faible", 0.50, "Money time"),
            ),
        ),
        "tennis" to SportAnalysisRule(
            sportKey = "tennis",
            displayName = "tennis",
            readiness = AnalysisReadiness.Weak,
            market = "Analyse tennis - données à compléter",
            selection = "Attendre surface, service/retour, fatigue, classement et H2H",
            expectedState = "Projection non validée sans profil surface et forme des deux joueurs",
            explanation = "Le match est détecté. Le tennis doit comparer les deux joueurs : surface, classement ATP/WTA, forme 365 jours, service, retour, fatigue du tournoi, face-à-face et historique sur la surface.",
            requiredStats = listOf("surface", "classement ATP/WTA", "forme 365 jours", "aces", "doubles fautes", "premières balles", "break points", "H2H"),
            actorStats = listOf("service", "retour", "fatigue dernier match", "victoires sur surface", "blessure/abandon récent", "face-à-face"),
            contextStats = listOf("surface rapide/lente", "indoor/outdoor", "enchaînement de matchs", "météo/vent", "format sets", "tournoi"),
            scenarios = listOf(
                ProbabilityScenario("Surveillance : vainqueur à éviter sans surface et forme récentes", 0.50, "Données faibles"),
                ProbabilityScenario("Tie-break à recalculer avec qualité de service et surface rapide", 0.50, "Tie-break"),
                ProbabilityScenario("Breaks à surveiller avec retour et secondes balles", 0.50, "Breaks"),
                ProbabilityScenario("Sets/jeux à surveiller après lecture surface, service et retour", 0.50, "Sets et jeux"),
                ProbabilityScenario("Handicap jeux à attendre tant que fatigue et H2H surface manquent", 0.50, "Handicap jeux"),
                ProbabilityScenario("Sets 2-0/2-1 ou 3-1/3-2 à recalculer selon format et endurance", 0.50, "Sets"),
                ProbabilityScenario("Aces joueur seulement si historique service fiable", 0.50, "Joueurs tennis"),
            ),
            forbiddenVocabulary = listOf("buts", "90 minutes", "prolongation", "équipe à domicile"),
        ),
        "rugby" to SportAnalysisRule(
            sportKey = "rugby",
            displayName = "rugby",
            readiness = AnalysisReadiness.Weak,
            market = "Analyse rugby - données à compléter",
            selection = "Attendre compositions, conquête, discipline, buteurs et météo",
            expectedState = "Projection non validée sans conquête, discipline et buteurs",
            explanation = "Le match est détecté. Le rugby doit lire puissance, mêlée, touche, occupation, discipline, cartons, buteur titulaire, météo, banc et terrain neutre si finale ou compétition internationale.",
            requiredStats = listOf("points", "essais", "transformations", "pénalités", "mêlées gagnées", "touches réussies", "cartons"),
            actorStats = listOf("essais joueur", "buteur", "pénalités réussies", "transformations", "plaquages", "retour blessure", "suspension"),
            contextStats = listOf("composition", "charnière", "discipline", "météo", "arbitre", "puissance mêlée", "banc", "terrain neutre"),
            scenarios = listOf(
                ProbabilityScenario("Surveillance : vainqueur à attendre sans compositions et discipline recoupées", 0.50, "Données faibles"),
                ProbabilityScenario("Total points à recalculer avec météo, buteurs et cartons", 0.50, "Total points rugby"),
                ProbabilityScenario("Essais équipe à surveiller avec occupation et mètres gagnés", 0.50, "Essais"),
                ProbabilityScenario("Pénalités/transformations à activer seulement avec buteur confirmé", 0.50, "Buteur rugby"),
                ProbabilityScenario("Conquête : touches et mêlées peuvent inverser la lecture", 0.50, "Conquête"),
            ),
            forbiddenVocabulary = listOf("corners", "score exact football", "90 minutes"),
        ),
        "cycling" to SportAnalysisRule(
            sportKey = "cycling",
            displayName = "cyclisme",
            readiness = AnalysisReadiness.WatchOnly,
            market = "Analyse cyclisme - surveillance",
            selection = "Surveiller startlist officielle, parcours, météo et rôles d’équipe",
            expectedState = "Pas de vainqueur/podium chiffré sans startlist et favoris confirmés",
            explanation = "La course est détectée. En cyclisme, aucun signal gagnant ne doit être affiché sans startlist, profil du parcours, météo, forme des coureurs, rôle leader/équipier et stratégie des équipes.",
            requiredStats = listOf("startlist", "parcours", "dénivelé", "météo", "vent", "forme coureurs", "rôles d’équipe", "abandons"),
            actorStats = listOf("leader/équipier", "sprinteur", "grimpeur", "CLM", "historique course", "retour blessure"),
            contextStats = listOf("profil étape", "échappée", "contrôle peloton", "météo", "chutes", "objectifs d’équipe"),
            scenarios = listOf(
                ProbabilityScenario("Course confirmée au calendrier public", 1.0, "Calendrier"),
                ProbabilityScenario("Signal vainqueur/podium à éviter sans startlist", 0.50, "Podium/vainqueur"),
                ProbabilityScenario("Sprint massif à évaluer si profil plat et trains confirmés", 0.50, "Profil course"),
                ProbabilityScenario("Échappée gagnante à surveiller selon parcours, vent et contrôle peloton", 0.50, "Scénario course"),
                ProbabilityScenario("Top 10 ou duel coureur à calculer après favoris recoupés", 0.50, "Coureurs"),
                ProbabilityScenario("Risque chute/abandon à intégrer avec météo, pavés ou descente technique", 0.50, "Risque course"),
            ),
            forbiddenVocabulary = listOf("buts", "score exact", "90 minutes", "prolongation"),
        ),
        "racing" to motorRule("racing", "F1", "Analyse F1 - surveillance grille"),
        "nascar" to motorRule("nascar", "NASCAR", "Analyse NASCAR - surveillance grille"),
        "golf" to SportAnalysisRule(
            sportKey = "golf",
            displayName = "golf",
            readiness = AnalysisReadiness.WatchOnly,
            market = "Analyse golf - surveillance",
            selection = "Attendre field, parcours, météo, putting et forme récente",
            expectedState = "Pas de vainqueur chiffré sans field et profil parcours",
            explanation = "Le tournoi est détecté. Le golf se lit avec field engagé, profil du parcours, météo/vent, forme récente, strokes gained, putting, précision et historique sur parcours similaire.",
            requiredStats = listOf("field", "strokes gained", "driving accuracy", "greens en régulation", "putting", "scrambling", "météo", "cut"),
            actorStats = listOf("golfeur", "top 5/top 10/top 20", "passer le cut", "birdies", "bogeys", "historique parcours"),
            contextStats = listOf("profil parcours", "vent", "forme putting", "approche", "pression cut", "fatigue voyage"),
            scenarios = listOf(
                ProbabilityScenario("Tournoi confirmé au calendrier public", 1.0, "Calendrier golf"),
                ProbabilityScenario("Vainqueur à éviter sans field et forme parcours", 0.50, "Vainqueur"),
                ProbabilityScenario("Passer le cut à surveiller avec régularité tee-to-green", 0.50, "Cut"),
                ProbabilityScenario("Top 20 à recalculer avant top 10 si field incomplet", 0.50, "Top 20"),
                ProbabilityScenario("Duel de joueurs à attendre après strokes gained et historique parcours", 0.50, "Duel joueur"),
            ),
            forbiddenVocabulary = listOf("buts", "sets", "90 minutes"),
        ),
        "handball" to teamIndoorRule(
            sportKey = "handball",
            displayName = "handball",
            market = "Analyse handball - données à compléter",
            selection = "Attendre buts/match, gardiens, exclusions, 7m et rotations",
            unit = "buts",
            required = listOf("buts par match", "écart moyen", "arrêts gardiens", "exclusions 2 minutes", "jets de 7m", "pertes de balle", "rotations"),
            scenarios = listOf(
                ProbabilityScenario("Projection impossible : attendre buts/match, gardiens et exclusions 2 minutes", 0.50, "Données faibles"),
                ProbabilityScenario("Vainqueur temps réglementaire à recalculer avec écart moyen fiable", 0.50, "Vainqueur temps réglementaire"),
                ProbabilityScenario("Total buts à activer seulement avec rythme et gardiens recoupés", 0.50, "Total buts"),
                ProbabilityScenario("Handicap buts à activer seulement avec écart moyen fiable", 0.50, "Handicap buts"),
                ProbabilityScenario("Joueur buts à activer seulement avec temps de jeu fiable", 0.50, "Joueur buts"),
            ),
        ),
        "volleyball" to teamIndoorRule(
            sportKey = "volleyball",
            displayName = "volley-ball",
            market = "Analyse volley - données à compléter",
            selection = "Attendre sets, points par set, service/réception, contres et rotations",
            unit = "sets",
            required = listOf("sets gagnés/perdus", "points par set", "aces", "fautes de service", "réception", "contres", "efficacité attaque", "rotations"),
            scenarios = listOf(
                ProbabilityScenario("Projection impossible : attendre points par set, service/réception et rotations", 0.50, "Données faibles"),
                ProbabilityScenario("Score en sets 3-0/3-1/3-2 à activer avec forme fiable", 0.50, "Score en sets"),
                ProbabilityScenario("Over/under 3,5 ou 4,5 sets à activer seulement avec équilibre fiable", 0.50, "Total sets"),
                ProbabilityScenario("Handicap sets à recalculer avec efficacité attaque", 0.50, "Handicap sets"),
                ProbabilityScenario("Joueur points/aces/contres seulement avec stats individuelles", 0.50, "Joueurs volley"),
            ),
        ),
        "baseball" to SportAnalysisRule(
            sportKey = "baseball",
            displayName = "baseball",
            readiness = AnalysisReadiness.Weak,
            market = "Analyse baseball - données à compléter",
            selection = "Attendre lanceurs probables, lineups, bullpen et runs récents",
            expectedState = "Projection non validée sans pitcher, lineup et bullpen",
            explanation = "Le match est détecté. Le baseball dépend surtout des lanceurs probables, du bullpen, des lineups, de la forme offensive, du stade et de la météo.",
            requiredStats = listOf("lanceurs probables", "ERA/FIP", "bullpen", "hits", "home runs", "walks", "strikeouts", "stade/météo"),
            actorStats = listOf("coups sûrs", "home runs", "RBI", "runs", "strikeouts lanceur", "lineup spot"),
            contextStats = listOf("lanceur partant", "repos bullpen", "vent", "dimensions stade", "lineup confirmé", "forme batteurs"),
            scenarios = listOf(
                ProbabilityScenario("Surveillance : vainqueur à attendre sans lanceur probable confirmé", 0.50, "Données faibles"),
                ProbabilityScenario("Total runs à recalculer avec lanceurs et météo", 0.50, "Total runs"),
                ProbabilityScenario("Run line à vérifier avec bullpen et starter", 0.50, "Run line"),
                ProbabilityScenario("Home run possible seulement si matchup batteur/stade concorde", 0.50, "Home runs"),
                ProbabilityScenario("Strikeouts lanceur à surveiller avec lineup adverse", 0.50, "Strikeouts"),
            ),
            forbiddenVocabulary = listOf("buts", "sets", "90 minutes"),
        ),
        "hockey" to SportAnalysisRule(
            sportKey = "hockey",
            displayName = "hockey sur glace",
            readiness = AnalysisReadiness.Weak,
            market = "Analyse hockey - données à compléter",
            selection = "Attendre gardiens titulaires, power play, tirs et absences",
            expectedState = "Projection non validée sans gardien et unités spéciales",
            explanation = "Le match est détecté. Le hockey demande gardiens titulaires, volume de tirs, power play, penalty kill, repos/back-to-back, absences et discipline.",
            requiredStats = listOf("buts", "tirs", "gardiens titulaires", "power play", "penalty kill", "pénalités", "repos", "xG si disponible"),
            actorStats = listOf("buts", "assists", "tirs", "temps de glace", "power play unit", "gardien confirmé"),
            contextStats = listOf("gardien titulaire", "back-to-back", "blessures", "supériorités", "discipline", "forme offensive"),
            scenarios = listOf(
                ProbabilityScenario("Surveillance : vainqueur à attendre sans gardien titulaire confirmé", 0.50, "Données faibles"),
                ProbabilityScenario("Total buts à recalculer après gardien titulaire", 0.50, "Total buts"),
                ProbabilityScenario("Power play peut peser si pénalités adverses élevées", 0.50, "Power play"),
                ProbabilityScenario("Tirs équipe à surveiller avec volume offensif récent", 0.50, "Tirs"),
                ProbabilityScenario("But en supériorité à surveiller selon unités spéciales", 0.50, "Supériorité"),
            ),
        ),
        "football" to SportAnalysisRule(
            sportKey = "football",
            displayName = "football américain",
            readiness = AnalysisReadiness.Weak,
            market = "Analyse football US - données à compléter",
            selection = "Attendre QB, blessures, turnovers, météo et rythme offensif",
            expectedState = "Projection non validée sans QB et blessures confirmées",
            explanation = "Le match est détecté. Le football US se lit avec quarterback, ligne offensive, météo, turnovers, red zone, blessures, snap counts et rythme offensif.",
            requiredStats = listOf("touchdowns", "yards", "quarterback", "turnovers", "3rd down", "red zone", "sacks", "météo"),
            actorStats = listOf("TD joueur", "yards passe/course/réception", "réceptions", "interceptions", "touches", "snap count"),
            contextStats = listOf("QB titulaire", "ligne offensive", "blessures", "météo", "rythme", "turnovers", "jeu au sol"),
            scenarios = listOf(
                ProbabilityScenario("Surveillance : vainqueur à attendre sans QB/blessures recoupés", 0.50, "Données faibles"),
                ProbabilityScenario("Total points à recalculer avec QB, météo et turnovers", 0.50, "Total points"),
                ProbabilityScenario("Touchdown joueur à surveiller avec rôle red zone", 0.50, "Touchdowns"),
                ProbabilityScenario("Yards QB/RB/WR à recalculer avec matchup", 0.50, "Yards"),
                ProbabilityScenario("Turnovers peuvent inverser handicap et total", 0.50, "Turnovers"),
            ),
        ),
        "mma" to combatRule("mma", "MMA"),
        "boxing" to combatRule("boxing", "boxe"),
        "athletics" to SportAnalysisRule(
            sportKey = "athletics",
            displayName = "athlétisme",
            readiness = AnalysisReadiness.WatchOnly,
            market = "Analyse athlétisme - surveillance startlist",
            selection = "Attendre startlist, records saison, séries/finale, couloir et météo",
            expectedState = "Pas de podium/médaille chiffré sans startlist officielle",
            explanation = "L’épreuve est détectée. L’athlétisme demande engagés, records saison, personal best, séries/finale, couloir, vent, météo, fatigue et forfaits.",
            requiredStats = listOf("startlist", "records saison", "personal best", "séries/finale", "couloir", "vent", "météo", "forme récente"),
            actorStats = listOf("athlète", "temps/distance", "record saison", "qualification", "podium", "duel", "fatigue séries"),
            contextStats = listOf("vent", "couloir", "densité plateau", "forfaits", "calendrier séries", "conditions piste"),
            scenarios = listOf(
                ProbabilityScenario("Épreuve confirmée au calendrier public", 1.0, "Calendrier athlétisme"),
                ProbabilityScenario("Médaille/podium à éviter sans startlist", 0.50, "Podium"),
                ProbabilityScenario("Qualification à calculer après séries et records saison", 0.50, "Qualification"),
                ProbabilityScenario("Finale à recalculer avec séries, couloir et densité du plateau", 0.50, "Finale"),
                ProbabilityScenario("Record saison/personnel possible si météo favorable", 0.50, "Records"),
                ProbabilityScenario("Duel d’athlètes à attendre après startlist officielle", 0.50, "Duel"),
            ),
            forbiddenVocabulary = listOf("buts", "sets", "90 minutes"),
        ),
    )

    private fun motorRule(sportKey: String, displayName: String, market: String): SportAnalysisRule =
        SportAnalysisRule(
            sportKey = sportKey,
            displayName = displayName,
            readiness = AnalysisReadiness.WatchOnly,
            market = market,
            selection = "Attendre essais, qualifications, grille, pneus, rythme long run et météo",
            expectedState = "Pas de podium/vainqueur chiffré sans grille et rythme course",
            explanation = "La course est détectée. Les sports mécaniques doivent croiser circuit, qualifications, rythme long relais, pneus, stratégie, fiabilité, météo et incidents avant de conclure.",
            requiredStats = listOf("essais libres", "qualifications", "grille", "rythme long run", "pneus", "dégradation", "météo", "fiabilité"),
            actorStats = listOf("pilote", "rythme course", "qualif", "pneus", "pénalités", "duels coéquipiers", "top 10"),
            contextStats = listOf("stratégie", "trafic", "météo", "safety car", "fiabilité moteur", "track position"),
            scenarios = listOf(
                ProbabilityScenario("Course confirmée au calendrier public", 1.0, "Calendrier course"),
                ProbabilityScenario("Podium/vainqueur à éviter sans qualifications", 0.50, "Podium/vainqueur"),
                ProbabilityScenario("Top 10 à surveiller après rythme course et dégradation pneus", 0.50, "Top 10"),
                ProbabilityScenario("Duel pilote à recalculer après rythme long run", 0.50, "Duel pilote"),
                ProbabilityScenario("Safety car/météo à intégrer avant stratégie et podium", 0.50, "Course"),
                ProbabilityScenario("Risque DNF/fiabilité à intégrer avec historique moteur et incidents", 0.50, "Fiabilité"),
                ProbabilityScenario("Arrêt au stand/stratégie à recalculer après grille et pneus", 0.50, "Stratégie"),
                ProbabilityScenario("Track position à recalculer après stratégie pneus", 0.50, "Track position"),
                ProbabilityScenario("Cautions/incidents à intégrer avant podium ou top 5", 0.50, "Cautions"),
            ),
            forbiddenVocabulary = listOf("buts", "score exact", "90 minutes"),
        )

    private fun teamIndoorRule(
        sportKey: String,
        displayName: String,
        market: String,
        selection: String,
        unit: String,
        required: List<String>,
        scenarios: List<ProbabilityScenario>,
    ): SportAnalysisRule =
        SportAnalysisRule(
            sportKey = sportKey,
            displayName = displayName,
            readiness = AnalysisReadiness.Impossible,
            market = market,
            selection = selection,
            expectedState = "Projection impossible sans données $displayName spécifiques",
            explanation = "L’événement est détecté, mais l’app doit attendre les données propres au $displayName avant de conclure : ${required.joinToString(", ")}.",
            requiredStats = required,
            actorStats = listOf("temps de jeu", "rôle confirmé", "retour blessure", "suspension", "fatigue", "performance récente"),
            contextStats = listOf("classement", "enjeu", "H2H", "voyage", "calendrier", "rotations", "absences"),
            scenarios = scenarios + ProbabilityScenario("Total de $unit non chiffré tant que les moyennes fiables manquent", 0.50, "Projection impossible"),
            forbiddenVocabulary = if (sportKey == "volleyball") listOf("buts", "90 minutes", "prolongation") else listOf("sets", "90 minutes", "prolongation"),
        )

    private fun combatRule(sportKey: String, displayName: String): SportAnalysisRule =
        SportAnalysisRule(
            sportKey = sportKey,
            displayName = displayName,
            readiness = AnalysisReadiness.Weak,
            market = "Analyse $displayName - données à compléter",
            selection = "Attendre style, forme, cardio, allonge, pesée et blessures",
            expectedState = "Projection non validée sans opposition de styles et pesée",
            explanation = "Le combat est détecté. L’analyse doit comparer styles, allonge, cardio, grappling/striking, historique de finish, camp, pesée, changement d’adversaire et blessure.",
            requiredStats = listOf("forme récente", "striking", "grappling", "allonge/reach", "cardio", "finish/décision", "pesée", "blessure/cut"),
            actorStats = listOf("méthode victoire", "KO/TKO", "soumission", "décision", "rounds", "reach", "cardio", "camp"),
            contextStats = listOf("pesée", "opposition styles", "changement adversaire", "juge/arbitre", "camp", "blessure"),
            scenarios = listOf(
                ProbabilityScenario("Surveillance : vainqueur à éviter sans forme et styles recoupés", 0.50, "Données faibles"),
                ProbabilityScenario("Méthode KO/TKO ou soumission à vérifier avec taux de finish", 0.50, "Méthode"),
                ProbabilityScenario("Décision/over rounds à surveiller avec cardio et défense", 0.50, "Rounds"),
                ProbabilityScenario("Finish rapide possible si mismatch striking/grappling confirmé", 0.50, "Finish"),
                ProbabilityScenario("Pesée/blessure/cut peuvent inverser la projection", 0.50, "Pesée"),
            ) + if (sportKey == "mma") {
                listOf(ProbabilityScenario("Avantage grappling à recalculer avec takedowns/défense", 0.50, "Grappling"))
            } else {
                listOf(ProbabilityScenario("Knockdown à surveiller si puissance et écart de gabarit concordent", 0.50, "Knockdown"))
            },
            forbiddenVocabulary = listOf("buts", "sets", "90 minutes"),
        )

    private fun generic(sport: String): SportAnalysisRule =
        SportAnalysisRule(
            sportKey = sport,
            displayName = sport.ifBlank { "sport" },
            readiness = AnalysisReadiness.WatchOnly,
            market = "Analyse sport - surveillance",
            selection = "Aucun signal fiable pour l’instant",
            expectedState = "Événement à qualifier par données spécifiques",
            explanation = "L’événement est détecté, mais les données disponibles ne suffisent pas à conclure. L’app attend des statistiques propres au sport, des actualités et des sources concordantes.",
            requiredStats = listOf("forme récente", "classement", "score/état", "absences", "actualité", "sources officielles"),
            actorStats = listOf("rôle", "performance récente", "état physique"),
            contextStats = listOf("calendrier", "enjeu", "fatigue", "conditions", "sources"),
            scenarios = listOf(
                ProbabilityScenario("Événement confirmé au calendrier public", 1.0, "Calendrier"),
                ProbabilityScenario("Favori fiable indisponible sans statistiques récentes", 0.50, "Données faibles"),
                ProbabilityScenario("Recalcul utile dès forme et actualités disponibles", 0.50, "Déclencheur"),
            ),
        )
}
