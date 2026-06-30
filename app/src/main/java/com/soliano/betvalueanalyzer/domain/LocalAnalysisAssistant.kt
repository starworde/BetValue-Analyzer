package com.soliano.betvalueanalyzer.domain

import com.soliano.betvalueanalyzer.data.local.LiveEventEntity
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class LocalAiReading(
    val status: LocalAiStatus,
    val summary: String,
    val sportVocabulary: String,
    val sections: List<LocalAiSection>,
    val importantSignals: List<String>,
    val contradictions: List<String>,
    val missingData: List<String>,
    val reliability: List<String>,
    val conclusion: String,
)

data class LocalAiSection(
    val title: String,
    val lines: List<String>,
)

enum class LocalAiStatus(val label: String) {
    Solid("Analyse solide"),
    Correct("Analyse correcte"),
    Average("Données moyennes"),
    Weak("Données faibles"),
    WatchOnly("Surveillance seulement"),
    Impossible("Projection impossible"),
}

object LocalAnalysisAssistant {
    private const val WEAK_DATA_CONCLUSION =
        "Les données disponibles sont insuffisantes pour produire une projection fiable. L’événement doit seulement être surveillé."

    fun explain(prediction: PredictionEntity): LocalAiReading {
        val sportKey = prediction.sportKey.substringBefore('/').normalizedKey()
        val profile = sportProfile(sportKey)
        val statLines = prediction.statSummary.cleanAssistantLines()
        val contextLines = prediction.contextInsights.cleanAssistantLines()
        val positiveLines = prediction.positiveArguments.cleanAssistantLines()
        val negativeLines = prediction.negativeArguments.cleanAssistantLines()
        val scenarioLines = prediction.scenarios.cleanAssistantLines()
        val playerLines = prediction.playerScenarios.cleanAssistantLines()
        val sourceLines = prediction.sourceDetails.cleanAssistantLines()
        val lineupLines = listOf(
            prediction.homeLineupStatus,
            prediction.homeLineup,
            prediction.awayLineupStatus,
            prediction.awayLineup,
        ).flatMap { it.cleanAssistantLines() }
        val allEvidence = (statLines + contextLines + positiveLines + negativeLines + lineupLines).distinctBy { it.lineKey() }
        val sourceCount = sourceLines.size + if (prediction.sourceName.isNotBlank()) 1 else 0
        val missingData = missingDataLines(
            profile = profile,
            prediction = prediction,
            statLines = statLines,
            contextLines = contextLines,
            sourceLines = sourceLines,
            lineupLines = lineupLines,
        )
        val contradictions = contradictionLines(prediction, contextLines + sourceLines + negativeLines)
        val importantSignals = importantSignalLines(
            lines = contextLines + lineupLines + allEvidence,
            profile = profile,
        )
        val status = readingStatus(
            confidence = prediction.confidenceScore,
            agreement = prediction.sourceAgreement,
            evidenceCount = allEvidence.size + scenarioLines.size + playerLines.size,
            missingCount = missingData.size,
            contradictionCount = contradictions.size,
            watchOnly = prediction.category.isWatchOnlyCategory() || prediction.selection.isWatchOnlySelection(),
        )

        val participants = prediction.participants()
        val summary = predictionSummary(prediction, profile, status, sourceCount, scenarioLines, playerLines)
        val analysisLines = deepAnalysisLines(
            prediction = prediction,
            profile = profile,
            status = status,
            participants = participants,
            allEvidence = allEvidence,
            scenarioLines = scenarioLines,
            playerLines = playerLines,
            importantSignals = importantSignals,
            missingData = missingData,
            contradictions = contradictions,
        )
        val latestNews = latestNewsLines(contextLines, lineupLines, sourceLines)
        val globalLines = globalReadingLines(
            prediction = prediction,
            profile = profile,
            status = status,
            participants = participants,
            allEvidence = allEvidence,
            importantSignals = importantSignals,
            missingData = missingData,
        )
        val tacticalLines = tacticalAnalysisLines(profile, allEvidence + scenarioLines + playerLines + latestNews)
        val favoriteLines = favoriteFragilityLines(prediction, participants, allEvidence + negativeLines, contradictions, missingData)
        val outsiderLines = outsiderCredibilityLines(prediction, participants, allEvidence + positiveLines, scenarioLines)
        val conclusionLines = conclusionArgumentLines(prediction, status, participants, scenarioLines, missingData, contradictions)
        val transparencyLines = sourceTransparencyLines(prediction, sourceLines, contextLines, missingData, status)
        val sections = buildList {
            add(LocalAiSection("Analyse IA approfondie", analysisLines))
            add(LocalAiSection("Lecture globale du match/événement", globalLines))
            add(LocalAiSection("Analyse tactique / sportive", tacticalLines))
            add(LocalAiSection("Dernières nouvelles", latestNews))
            add(LocalAiSection("Pourquoi le favori peut perdre", favoriteLines))
            add(LocalAiSection("Pourquoi l’outsider peut gagner", outsiderLines))
            add(LocalAiSection("Conclusion argumentée", conclusionLines))
            add(LocalAiSection("Sources & transparence", transparencyLines))
            add(LocalAiSection("Résumé rapide", listOf(summary)))
            add(LocalAiSection("Points forts ${participants.firstLabel}", participantLines(participants.first, allEvidence + positiveLines, profile.strengthKeywords)))
            add(LocalAiSection("Points faibles ${participants.firstLabel}", participantLines(participants.first, allEvidence + negativeLines, profile.weaknessKeywords)))
            add(LocalAiSection("Points forts ${participants.secondLabel}", participantLines(participants.second, allEvidence + positiveLines, profile.strengthKeywords)))
            add(LocalAiSection("Points faibles ${participants.secondLabel}", participantLines(participants.second, allEvidence + negativeLines, profile.weaknessKeywords)))
            if (playerLines.isNotEmpty()) add(LocalAiSection(profile.actorSectionTitle, playerLines.takeUseful(6)))
            if (scenarioLines.isNotEmpty()) add(LocalAiSection("Scénarios calculés par le moteur", scenarioLines.takeUseful(5)))
        }

        return LocalAiReading(
            status = status,
            summary = summary,
            sportVocabulary = "Lecture ${profile.label} : ${profile.focus.joinToString(", ")}.",
            sections = sections,
            importantSignals = importantSignals.ifEmpty { listOf("Aucun fait relevé") },
            contradictions = contradictions.ifEmpty { listOf("Aucune contradiction nette relevée dans les données disponibles.") },
            missingData = missingData.ifEmpty { listOf("Aucun manque majeur détecté dans les champs déjà calculés.") },
            reliability = reliabilityLines(prediction, sourceCount, allEvidence.size, status),
            conclusion = conclusionFromLines(conclusionLines, prediction.selection, status),
        )
    }

    fun explainLive(event: LiveEventEntity): LocalAiReading {
        val sportKey = event.sportKey.substringBefore('/').normalizedKey()
        val profile = sportProfile(sportKey)
        val statLines = event.statSummary.cleanAssistantLines()
        val scenarioLines = event.scenarios.cleanAssistantLines()
        val sourceLines = event.sourceDetails.cleanAssistantLines()
        val hasScoreOrRanking = event.homeScore != null && event.awayScore != null ||
            statLines.any { it.contains("top 3", ignoreCase = true) || it.contains("classement", ignoreCase = true) }
        val missingData = buildList {
            if (!hasScoreOrRanking) add("Score, classement ou métrique principale live non confirmé.")
            if (event.displayClock.isBlank() && event.period == null && statLines.none { it.hasAny("tour", "minute", "km", "session", "temps") }) {
                add("Temps de jeu, tours, session ou distance restante non confirmé.")
            }
            if (scenarioLines.isEmpty()) add("Scénarios live absents ou non recalculés.")
            if (sourceLines.size < 2) add("Recoupement live multi-source limité.")
            addAll(profile.requiredLiveStats.filterNot { required -> statLines.any { it.matchesConcept(required) } }.map { "Donnée live ${it} manquante." })
        }.distinctBy { it.lineKey() }.take(6)
        val contradictions = contradictionLines(sourceAgreement = if (sourceLines.size >= 2) 62 else 42, lines = statLines + sourceLines)
        val status = readingStatus(
            confidence = if (event.isLive) 62 else 48,
            agreement = if (sourceLines.size >= 2) 62 else 42,
            evidenceCount = statLines.size + scenarioLines.size + sourceLines.size,
            missingCount = missingData.size,
            contradictionCount = contradictions.size,
            watchOnly = scenarioLines.isEmpty(),
        )
        val summary = if (status == LocalAiStatus.Weak || status == LocalAiStatus.WatchOnly || status == LocalAiStatus.Impossible) {
            WEAK_DATA_CONCLUSION
        } else {
            "Lecture live locale : ${event.eventName.ifBlank { "${event.homeName} — ${event.awayName}" }.cleanAssistantText()} est analysé avec les données de score/classement, d’avancement et les scénarios déjà produits par l’app."
        }
        val analysisLines = liveDeepAnalysisLines(
            event = event,
            profile = profile,
            status = status,
            statLines = statLines,
            scenarioLines = scenarioLines,
            missingData = missingData,
            contradictions = contradictions,
            hasScoreOrRanking = hasScoreOrRanking,
        )
        val bestLiveScenario = scenarioLines.firstOrNull()?.cleanAssistantText()
        val liveConclusionLines = if (status == LocalAiStatus.Weak || status == LocalAiStatus.WatchOnly || status == LocalAiStatus.Impossible) {
            listOf(
                WEAK_DATA_CONCLUSION,
                "Donnée prioritaire à récupérer : ${missingData.firstOrNull()?.cleanAssistantText() ?: "score/classement, temps et scénarios live recoupés"}.",
            )
        } else {
            listOf(
                "Scénario live le plus probable : ${bestLiveScenario ?: "continuer la lecture avec l’état réel, aucun scénario chiffré n’est assez propre."}",
                "Scénario alternatif crédible : changement brutal après score, carton, break, safety car, météo, rotation ou écart de rythme.",
                "Ce qui peut changer l’analyse : ${missingData.firstOrNull()?.cleanAssistantText() ?: "prochaine action forte ou nouvelle métrique officielle."}",
                "Niveau de confiance : ${status.label}, recoupement live ${sourceLines.size} source(s).",
            )
        }
        val liveTransparencyLines = buildList {
            val sources = (listOf(event.sourceName) + sourceLines).map { it.cleanAssistantText() }.filter { it.isNotBlank() }.distinct()
            add("Sources utilisées : ${sources.take(6).joinToString(", ").ifBlank { "non listées dans cette fiche live" }}.")
            add("Sources manquantes / limites : ${missingData.take(4).joinToString(" ; ").ifBlank { "aucun manque majeur détecté dans les champs live chargés" }}.")
            add("Dernière mise à jour : ${formatUpdateTime(event.lastUpdate)}.")
            add("Solidité des données : ${status.label}.")
            add("Architecture : analyse live locale sans clé API dans l’APK ; un backend sécurisé pourra enrichir le recoupement plus tard.")
        }
        return LocalAiReading(
            status = status,
            summary = summary,
            sportVocabulary = "Lecture live ${profile.label} : ${profile.liveFocus.joinToString(", ")}.",
            sections = listOf(
                LocalAiSection("Analyse IA live", analysisLines),
                LocalAiSection("Résumé rapide", listOf(summary)),
                LocalAiSection("Stats live utiles", statLines.takeUseful(6)),
                LocalAiSection("Scénarios live", scenarioLines.takeUseful(5)),
                LocalAiSection("Conclusion live argumentée", liveConclusionLines),
                LocalAiSection("Sources & transparence live", liveTransparencyLines),
            ),
            importantSignals = importantSignalLines(statLines, profile).ifEmpty { listOf("Aucun fait relevé") },
            contradictions = contradictions.ifEmpty { listOf("Aucune contradiction nette relevée dans les données disponibles.") },
            missingData = missingData.ifEmpty { listOf("Aucun manque majeur détecté dans les champs live déjà calculés.") },
            reliability = listOf(
                "Sources live recoupées : ${sourceLines.size}.",
                "Métrique principale : ${if (hasScoreOrRanking) "présente" else "manquante"}.",
                "Statut : ${status.label}.",
            ),
            conclusion = liveConclusionLines.firstOrNull() ?: WEAK_DATA_CONCLUSION,
        )
    }

    private fun predictionSummary(
        prediction: PredictionEntity,
        profile: SportAiProfile,
        status: LocalAiStatus,
        sourceCount: Int,
        scenarioLines: List<String>,
        playerLines: List<String>,
    ): String {
        if (status == LocalAiStatus.Weak || status == LocalAiStatus.WatchOnly || status == LocalAiStatus.Impossible) {
            return WEAK_DATA_CONCLUSION
        }
        val probability = (prediction.consensusProbability * 100).roundToInt().coerceIn(0, 100)
        val scoreText = prediction.expectedScore.takeIf { it.isNotBlank() }?.let { " Score/état lu : ${it.cleanAssistantText()}." }.orEmpty()
        val extra = when {
            scenarioLines.isNotEmpty() && playerLines.isNotEmpty() -> " Les scénarios match et joueurs sont présents."
            scenarioLines.isNotEmpty() -> " Les scénarios match sont présents."
            playerLines.isNotEmpty() -> " Les scénarios joueurs sont présents."
            else -> ""
        }
        return "Analyse IA approfondie ${profile.label} : la conclusion provisoire est « ${prediction.selection.cleanAssistantText()} » ($probability %), mais elle est relue avec contexte, forme, dernières infos et facteurs spécifiques au sport. Fiabilité ${prediction.confidenceScore}/100, $sourceCount source(s) exploitable(s).$scoreText$extra"
    }

    private fun deepAnalysisLines(
        prediction: PredictionEntity,
        profile: SportAiProfile,
        status: LocalAiStatus,
        participants: Participants,
        allEvidence: List<String>,
        scenarioLines: List<String>,
        playerLines: List<String>,
        importantSignals: List<String>,
        missingData: List<String>,
        contradictions: List<String>,
    ): List<String> {
        if (status == LocalAiStatus.Weak || status == LocalAiStatus.WatchOnly || status == LocalAiStatus.Impossible) {
            return listOf(
                "Analyse IA stoppée : les données disponibles ne suffisent pas à transformer le calendrier/statut en lecture exploitable.",
                "Ce qu’il manque le plus : ${missingData.firstOrNull()?.cleanAssistantText() ?: "statistiques, actualités ou scénarios recoupés"}.",
            )
        }

        val probability = (prediction.consensusProbability * 100).roundToInt().coerceIn(0, 100)
        val allLines = allEvidence + scenarioLines + playerLines
        val supports = analysisSupportFactors(profile, allLines)
        val selection = prediction.selection.cleanAssistantText().ifBlank { "scénario principal" }
        val mainSignalSide = signalSide(selection, participants)
        val impactSignal = importantSignals.firstOrNull { it != "Aucun fait relevé" }?.cleanAssistantText()
        val blocker = (contradictions.filterNot { it.startsWith("Aucune contradiction", ignoreCase = true) } + missingData)
            .firstOrNull()
            ?.cleanAssistantText()

        return buildList {
            add("Ce que ça change : « $selection » n’est pas juste repris du tableau ; il est retenu parce que ${supports.joinToString(" + ")} se recoupent avec une chance lue à $probability %.")
            add(sportInterpretation(profile, mainSignalSide, allLines))
            if (impactSignal != null) {
                add("Info qui pèse vraiment : $impactSignal. Cette info modifie la lecture du risque, surtout si elle touche un titulaire, un favori, un buteur, un serveur ou un pilote clé.")
            }
            add(
                if (blocker == null) {
                    "Frein principal : aucun blocage majeur ressort des données chargées ; le signal peut rester prioritaire dans cette fiche."
                } else {
                    "Frein principal : $blocker. C’est ce point qui empêche de monter la lecture d’un cran."
                }
            )
            add("Lecture opérationnelle : ${operationalReading(probability, status, missingData, contradictions)}")
        }.distinctBy { it.lineKey() }.take(5)
    }

    private fun liveDeepAnalysisLines(
        event: LiveEventEntity,
        profile: SportAiProfile,
        status: LocalAiStatus,
        statLines: List<String>,
        scenarioLines: List<String>,
        missingData: List<String>,
        contradictions: List<String>,
        hasScoreOrRanking: Boolean,
    ): List<String> {
        if (status == LocalAiStatus.Weak || status == LocalAiStatus.WatchOnly || status == LocalAiStatus.Impossible) {
            return listOf(
                "Analyse IA live limitée : score, classement, temps ou scénarios ne sont pas encore assez recoupés.",
                "Priorité live : ${missingData.firstOrNull()?.cleanAssistantText() ?: "attendre une métrique officielle et des scénarios recalculés"}.",
            )
        }
        val bestScenario = scenarioLines.firstOrNull()?.cleanAssistantText()
        val blocker = (contradictions.filterNot { it.startsWith("Aucune contradiction", ignoreCase = true) } + missingData)
            .firstOrNull()
            ?.cleanAssistantText()
        return buildList {
            add(
                if (hasScoreOrRanking) {
                    "Ce que ça change en live : la lecture part de l’état réel du match/course, puis ajuste les scénarios au rythme actuel au lieu de reprendre le pré-match."
                } else {
                    "Ce que ça change en live : l’app attend encore la métrique principale avant de hiérarchiser les scénarios."
                }
            )
            add(liveSportInterpretation(event, profile, statLines))
            if (bestScenario != null) add("Scénario le plus utile maintenant : $bestScenario.")
            add(
                if (blocker == null) {
                    "Frein principal : aucun blocage majeur dans les données live chargées ; surveiller seulement les prochains changements de score/classement."
                } else {
                    "Frein principal : $blocker. C’est la donnée qui peut retourner la lecture live."
                }
            )
        }.distinctBy { it.lineKey() }.take(5)
    }

    private fun globalReadingLines(
        prediction: PredictionEntity,
        profile: SportAiProfile,
        status: LocalAiStatus,
        participants: Participants,
        allEvidence: List<String>,
        importantSignals: List<String>,
        missingData: List<String>,
    ): List<String> {
        val eventLabel = "${prediction.sportTitle} · ${prediction.competitionName}".cleanAssistantText()
        val issueLine = allEvidence.bestLine("enjeu", "qualification", "classement", "finale", "maintien")
        val dynamicLine = allEvidence.bestLine("forme", "dynamique", "hausse", "baisse", "victoire", "défaite", "defaite")
        val stateLine = importantSignals.firstOrNull { it != "Aucun fait relevé" }
            ?: allEvidence.bestLine("blessure", "absence", "suspension", "retour", "composition", "fatigue")
        val fieldLine = terrainOrContextFactor(profile, allEvidence, prediction)
        return buildList {
            add("Contexte : $eventLabel, ${participants.firstLabel} face à ${participants.secondLabel}, analyse recalculée le ${formatUpdateTime(prediction.sourceLastUpdate)}.")
            add("Enjeu : ${issueLine ?: "non confirmé par les sources disponibles ; l’IA ne l’invente pas."}")
            add("Dynamique récente : ${dynamicLine ?: "tendance à confirmer, car les lignes actuelles ne suffisent pas à qualifier une hausse ou une baisse nette."}")
            add("État équipes/joueurs : ${stateLine ?: "aucune absence, suspension, retour ou fatigue clairement exploitable dans les données chargées."}")
            add(fieldLine)
            if (status == LocalAiStatus.Average || status == LocalAiStatus.Weak || status == LocalAiStatus.WatchOnly) {
                add("Lecture prudente : ${missingData.firstOrNull()?.cleanAssistantText() ?: "les données contextuelles restent incomplètes."}")
            }
        }.distinctBy { it.lineKey() }.take(6)
    }

    private fun tacticalAnalysisLines(profile: SportAiProfile, evidence: List<String>): List<String> {
        val joined = evidence.joinToString(" ").lineKey()
        fun hasAny(vararg words: String) = words.any { joined.contains(it.lineKey()) }
        fun availableOrMissing(label: String, vararg concepts: String): String =
            if (hasAny(*concepts)) "$label : signal présent dans les données." else "$label : non confirmé par les sources actuelles."
        return when (profile.label) {
            "football" -> listOf(
                availableOrMissing("Pressing / possession", "pressing", "possession", "corners", "tirs"),
                availableOrMissing("Transitions et attaque", "transition", "contre", "buts", "xg", "tirs cadrés"),
                availableOrMissing("Défense et discipline", "encaiss", "clean sheet", "carton", "suspension"),
                availableOrMissing("Compositions / absences", "composition", "titulaire", "blessure", "absence", "retour"),
            )
            "rugby" -> listOf(
                availableOrMissing("Puissance / conquête", "mêlée", "melee", "touche", "conquête", "occupation"),
                availableOrMissing("Discipline", "carton", "pénalité", "exclusion", "discipline"),
                availableOrMissing("Buteurs et points", "buteur", "pénalités", "transformations", "points", "essais"),
                availableOrMissing("Météo / occupation", "météo", "vent", "pluie", "occupation"),
            )
            "tennis" -> listOf(
                availableOrMissing("Surface", "surface", "gazon", "terre", "dur"),
                availableOrMissing("Service", "service", "aces", "première balle", "1res balles"),
                availableOrMissing("Retour / breaks", "retour", "break", "balles de break"),
                availableOrMissing("Fatigue / H2H", "fatigue", "minutes", "dernier match", "h2h", "face-à-face"),
            )
            "cyclisme" -> listOf(
                availableOrMissing("Profil parcours", "parcours", "montagne", "sprint", "chrono", "dénivelé"),
                availableOrMissing("Startlist / favoris", "startlist", "favori", "favoris", "coureurs"),
                availableOrMissing("Rôle des équipes", "leader", "équipier", "rôle", "equipe"),
                availableOrMissing("Météo / incidents", "météo", "vent", "chute", "abandon"),
            )
            "F1", "NASCAR" -> listOf(
                availableOrMissing("Circuit / grille", "circuit", "grille", "qualification", "qualifications"),
                availableOrMissing("Rythme long relais", "rythme", "long run", "tour", "écart"),
                availableOrMissing("Pneus / stratégie", "pneu", "pneus", "stratégie", "arrêt"),
                availableOrMissing("Fiabilité / météo", "fiabilité", "moteur", "pénalité", "météo"),
            )
            "handball" -> listOf(
                availableOrMissing("Rythme / montée de balle", "rythme", "montée de balle", "buts", "points"),
                availableOrMissing("Gardiens", "gardien", "arrêts", "arrets"),
                availableOrMissing("Exclusions / discipline", "exclusion", "carton", "2 minutes", "discipline"),
                availableOrMissing("Attaque placée", "attaque", "passes", "efficacité"),
            )
            "volley" -> listOf(
                availableOrMissing("Service", "service", "aces"),
                availableOrMissing("Réception", "réception", "reception"),
                availableOrMissing("Blocs", "bloc", "blocks", "contres"),
                availableOrMissing("Efficacité offensive / sets", "attaque", "efficacité", "sets", "set"),
            )
            "basket" -> listOf(
                availableOrMissing("Rythme / adresse", "rythme", "pace", "adresse", "points"),
                availableOrMissing("Rebonds", "rebonds", "rebound"),
                availableOrMissing("Rotations / minutes", "rotation", "minutes", "temps de jeu"),
                availableOrMissing("Fatigue / back-to-back", "fatigue", "back-to-back", "calendrier"),
            )
            "baseball" -> listOf(
                availableOrMissing("Lanceurs", "lanceur", "pitcher", "starter", "strikeout"),
                availableOrMissing("Bullpen", "bullpen", "relève", "releve"),
                availableOrMissing("Lineups", "lineup", "composition", "batteur"),
                availableOrMissing("Runs récents", "runs", "home run", "hits", "coups sûrs"),
            )
            "golf" -> listOf(
                availableOrMissing("Parcours", "parcours", "course", "greens"),
                availableOrMissing("Météo", "météo", "vent", "pluie"),
                availableOrMissing("Putting / précision", "putting", "précision", "strokes gained"),
                availableOrMissing("Historique parcours similaire", "historique", "parcours similaire", "course history"),
            )
            "sports de combat" -> listOf(
                availableOrMissing("Opposition de styles", "style", "striking", "grappling", "lutte"),
                availableOrMissing("Allonge / distance", "allonge", "reach", "distance"),
                availableOrMissing("Cardio", "cardio", "fatigue", "rounds"),
                availableOrMissing("Forme / camp / pesée", "camp", "pesée", "weigh-in", "blessure"),
            )
            else -> listOf(
                "Lecture adaptée : ${profile.focus.joinToString(", ")}.",
                "Axe confirmé : ${evidence.firstOrNull()?.cleanAssistantText() ?: "aucun axe tactique exploitable pour l’instant."}",
                "Axe manquant : l’IA garde les données inconnues comme inconnues, sans inventer de style de jeu.",
            )
        }.distinctBy { it.lineKey() }
    }

    private fun latestNewsLines(
        contextLines: List<String>,
        lineupLines: List<String>,
        sourceLines: List<String>,
    ): List<String> {
        val news = (contextLines + lineupLines)
            .map { it.cleanAssistantText() }
            .filterNot { it.equals("Aucun fait relevé", ignoreCase = true) }
            .filterNot { it.contains("stats clés surveillées", ignoreCase = true) }
            .distinctBy { it.lineKey() }
            .take(6)
        if (news.isNotEmpty()) return news
        return listOf(
            "Aucun fait relevé",
            "Sources consultées : ${sourceLines.take(4).joinToString(", ").ifBlank { "non listées" }}.",
        )
    }

    private fun favoriteFragilityLines(
        prediction: PredictionEntity,
        participants: Participants,
        evidence: List<String>,
        contradictions: List<String>,
        missingData: List<String>,
    ): List<String> {
        val favorite = selectedParticipant(prediction.selection, participants) ?: "le scénario principal"
        val directWeakness = participantEvidence(favorite, evidence, baseWeaknessKeywords + listOf("carton", "doute", "questionable"))
        val blocker = contradictions.firstOrNull { !it.startsWith("Aucune contradiction", ignoreCase = true) }
            ?: missingData.firstOrNull()
        return buildList {
            add("$favorite peut perdre de la valeur si les infos contextuelles contredisent le signal statistique.")
            add(directWeakness ?: "Point faible confirmé : aucun fait précis trouvé ; l’IA ne transforme donc pas une fragilité supposée en certitude.")
            add("Risque à surveiller : ${blocker?.cleanAssistantText() ?: "composition, absence, fatigue, météo ou changement tactique non encore confirmé."}")
        }.distinctBy { it.lineKey() }
    }

    private fun outsiderCredibilityLines(
        prediction: PredictionEntity,
        participants: Participants,
        evidence: List<String>,
        scenarioLines: List<String>,
    ): List<String> {
        val favorite = selectedParticipant(prediction.selection, participants)
        val outsider = when (favorite) {
            participants.firstLabel -> participants.secondLabel
            participants.secondLabel -> participants.firstLabel
            else -> participants.secondLabel.ifBlank { participants.firstLabel.ifBlank { "l’outsider" } }
        }
        val positiveOutsider = participantEvidence(outsider, evidence, baseStrengthKeywords + listOf("retour", "avantage", "surface", "rythme"))
        val altScenario = scenarioLines
            .map { it.cleanAssistantText() }
            .firstOrNull { line -> outsider.isNotBlank() && line.contains(outsider, ignoreCase = true) }
        return buildList {
            add("$outsider peut devenir crédible seulement si ses signaux spécifiques au sport se confirment.")
            add(positiveOutsider ?: "Signal positif confirmé : aucun fait fort isolé pour $outsider dans les données disponibles.")
            add("Scénario alternatif : ${altScenario ?: "non établi par le moteur ; garder l’outsider en hypothèse, pas en conclusion inventée."}")
        }.distinctBy { it.lineKey() }
    }

    private fun conclusionArgumentLines(
        prediction: PredictionEntity,
        status: LocalAiStatus,
        participants: Participants,
        scenarioLines: List<String>,
        missingData: List<String>,
        contradictions: List<String>,
    ): List<String> {
        val selection = prediction.selection.cleanAssistantText().ifBlank { "scénario principal" }
        val probability = (prediction.consensusProbability * 100).roundToInt().coerceIn(0, 100)
        val favorite = selectedParticipant(selection, participants)
        val alternative = scenarioLines.map { it.cleanAssistantText() }
            .firstOrNull { line -> !line.contains(selection, ignoreCase = true) }
        val changer = (contradictions.filterNot { it.startsWith("Aucune contradiction", ignoreCase = true) } + missingData)
            .firstOrNull()
        return buildList {
            add("Scénario le plus probable : $selection, mais il doit être lu comme une conclusion argumentée, pas comme un simple pourcentage ($probability %).")
            add("Scénario alternatif crédible : ${alternative ?: if (favorite != null) "surveiller la réponse de l’adversaire si les dernières infos inversent la lecture." else "aucun scénario alternatif solide disponible."}")
            add("Niveau de confiance : ${status.label}, fiabilité moteur ${prediction.confidenceScore}/100, accord sources ${prediction.sourceAgreement}/100.")
            add("Ce qui peut changer l’analyse : ${changer?.cleanAssistantText() ?: "aucun déclencheur majeur identifié, mais compositions/news de dernière minute restent prioritaires."}")
            add("Recommandation : ${recommendationForStatus(status, prediction.category)}")
        }
    }

    private fun sourceTransparencyLines(
        prediction: PredictionEntity,
        sourceLines: List<String>,
        contextLines: List<String>,
        missingData: List<String>,
        status: LocalAiStatus,
    ): List<String> = buildList {
        val sources = (listOf(prediction.sourceName) + sourceLines).map { it.cleanAssistantText() }.filter { it.isNotBlank() }.distinct()
        add("Sources utilisées : ${sources.take(6).joinToString(", ").ifBlank { "non listées dans cette fiche" }}.")
        add("Sources manquantes / limites : ${missingData.take(4).joinToString(" ; ").ifBlank { "aucun manque majeur détecté dans les champs déjà chargés" }}.")
        add("Dernière mise à jour : ${formatUpdateTime(prediction.sourceLastUpdate)}.")
        add("Solidité des données : ${status.label}.")
        add(
            if (contextLines.isEmpty() || contextLines.all { it.contains("Aucun fait relevé", ignoreCase = true) }) {
                "Actualités : aucun fait exploitable confirmé ; pas d’invention de blessure, composition, météo ou déclaration."
            } else {
                "Actualités : ${contextLines.size} ligne(s) exploitée(s), recoupement évalué via les sources listées."
            }
        )
        add("Architecture : analyse locale sans clé API dans l’APK ; backend IA sécurisé possible plus tard via cloud/Firebase/GitHub Actions.")
    }

    private fun terrainOrContextFactor(
        profile: SportAiProfile,
        evidence: List<String>,
        prediction: PredictionEntity,
    ): String {
        val line = when (profile.label) {
            "tennis" -> evidence.bestLine("surface", "gazon", "terre", "dur")
            "cyclisme" -> evidence.bestLine("parcours", "météo", "montagne", "sprint", "chrono")
            "F1", "NASCAR" -> evidence.bestLine("circuit", "grille", "qualification", "pneus", "météo")
            "golf" -> evidence.bestLine("parcours", "météo", "putting", "strokes gained")
            else -> evidence.bestLine("domicile", "extérieur", "terrain", "surface", "déplacement", "meteo", "météo")
        }
        val label = when (profile.label) {
            "tennis" -> "Facteur surface"
            "cyclisme" -> "Facteur parcours"
            "F1", "NASCAR" -> "Facteur circuit"
            "golf" -> "Facteur parcours"
            else -> "Facteur terrain / déplacement"
        }
        val neutralWarning = if (prediction.competitionName.hasAny("coupe du monde", "world cup", "finale", "neutral", "neutre")) {
            " Attention : compétition ou finale potentiellement sur terrain neutre, donc avantage domicile non supposé sans preuve."
        } else {
            ""
        }
        return "$label : ${line ?: "non confirmé par les sources disponibles."}$neutralWarning"
    }

    private fun recommendationForStatus(status: LocalAiStatus, category: String): String = when {
        status == LocalAiStatus.Solid && category.lineKey().contains("safe") -> "lecture safe possible, en restant dépendante des dernières compositions/news."
        status == LocalAiStatus.Solid || status == LocalAiStatus.Correct -> "lecture intéressante, mais validation finale après dernières infos."
        status == LocalAiStatus.Average -> "prudence : données utiles mais pas assez convergentes pour signal fort."
        status == LocalAiStatus.Weak || status == LocalAiStatus.WatchOnly -> "abstention ou surveillance, car l’analyse manque de confirmations."
        else -> "ne pas conclure : projection insuffisante."
    }

    private fun selectedParticipant(selection: String, participants: Participants): String? = when {
        participants.firstLabel.isNotBlank() && selection.contains(participants.firstLabel, ignoreCase = true) -> participants.firstLabel
        participants.secondLabel.isNotBlank() && selection.contains(participants.secondLabel, ignoreCase = true) -> participants.secondLabel
        else -> null
    }

    private fun participantEvidence(name: String, lines: List<String>, keywords: List<String>): String? {
        val nameKey = name.lineKey()
        return lines.map { it.cleanAssistantText() }
            .firstOrNull { line ->
                val key = line.lineKey()
                key.contains(nameKey) && keywords.any { keyword -> key.contains(keyword.lineKey()) }
            }
    }

    private fun List<String>.bestLine(vararg concepts: String): String? =
        map { it.cleanAssistantText() }
            .firstOrNull { line -> concepts.any { line.matchesConcept(it) || line.contains(it, ignoreCase = true) } }

    private fun formatUpdateTime(timestamp: Long): String =
        runCatching {
            Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.of("Europe/Paris"))
                .format(DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.FRANCE))
        }.getOrDefault("inconnue")

    private fun conclusionFromLines(lines: List<String>, selection: String, status: LocalAiStatus): String {
        if (status == LocalAiStatus.Weak || status == LocalAiStatus.WatchOnly || status == LocalAiStatus.Impossible) {
            return WEAK_DATA_CONCLUSION
        }
        val core = lines.firstOrNull()?.substringAfter(":")?.trim()
        return core?.takeIf { it.isNotBlank() }
            ?: "Lecture finale : le signal « ${selection.cleanAssistantText()} » est lisible avec les données disponibles."
    }

    private fun analysisSupportFactors(profile: SportAiProfile, lines: List<String>): List<String> {
        val joined = lines.joinToString(" ").lineKey()
        fun hasAny(vararg words: String) = words.any { joined.contains(it.lineKey()) }
        val sportFactors = when (profile.label) {
            "football" -> listOfNotNull(
                "production offensive".takeIf { hasAny("buts", "tirs", "xg") },
                "maîtrise du rythme".takeIf { hasAny("possession", "corners", "clean sheet") },
                "état des joueurs".takeIf { hasAny("composition", "absence", "blessure", "suspension", "carton") },
                "forme récente".takeIf { hasAny("forme", "hausse", "baisse", "6 matchs") },
            )
            "rugby" -> listOfNotNull(
                "volume de points/essais".takeIf { hasAny("points", "essais", "transformation") },
                "discipline".takeIf { hasAny("carton", "pénalité", "discipline", "exclusion") },
                "composition/buteurs".takeIf { hasAny("composition", "buteur", "titulaire") },
                "conditions de jeu".takeIf { hasAny("météo", "pluie", "vent") },
            )
            "tennis" -> listOfNotNull(
                "surface".takeIf { hasAny("surface", "gazon", "terre", "dur") },
                "service".takeIf { hasAny("service", "aces", "premières balles") },
                "retour/break".takeIf { hasAny("retour", "break") },
                "fatigue".takeIf { hasAny("fatigue", "dernier match", "minutes") },
                "face-à-face".takeIf { hasAny("h2h", "face à face", "face-a-face") },
            )
            "cyclisme" -> listOfNotNull(
                "parcours".takeIf { hasAny("parcours", "montagne", "sprint", "étape") },
                "startlist/favoris".takeIf { hasAny("startlist", "favori", "favoris") },
                "rôle d’équipe".takeIf { hasAny("rôle", "equipe", "leader", "équipier") },
                "météo/incidents".takeIf { hasAny("météo", "vent", "chute", "abandon") },
            )
            "F1", "NASCAR" -> listOfNotNull(
                "classement/grille".takeIf { hasAny("classement", "grille", "top 3", "position") },
                "rythme".takeIf { hasAny("rythme", "tour", "tours", "écart") },
                "stratégie".takeIf { hasAny("stratégie", "pneu", "arrêt") },
                "fiabilité".takeIf { hasAny("fiabilité", "moteur", "panne", "pénalité") },
            )
            "volley" -> listOfNotNull(
                "sets".takeIf { hasAny("sets", "set") },
                "service/réception".takeIf { hasAny("service", "aces", "réception") },
                "contres".takeIf { hasAny("contres", "blocks") },
                "rotation".takeIf { hasAny("rotation", "titulaire") },
            )
            "basket" -> listOfNotNull(
                "rythme et points".takeIf { hasAny("points", "rythme", "pace") },
                "rotations".takeIf { hasAny("rotation", "temps de jeu", "minutes") },
                "rebonds/passes".takeIf { hasAny("rebonds", "passes") },
                "fatigue".takeIf { hasAny("fatigue", "back-to-back", "calendrier") },
            )
            "baseball" -> listOfNotNull(
                "lanceurs".takeIf { hasAny("lanceur", "pitcher", "strikeout") },
                "bullpen".takeIf { hasAny("bullpen", "relève") },
                "production offensive".takeIf { hasAny("runs", "hits", "coups sûrs", "home run") },
            )
            else -> emptyList()
        }
        return (sportFactors + listOfNotNull(
            "accord multi-source".takeIf { hasAny("officiel", "source", "confirmé", "confirmée") },
            "scénarios calculés".takeIf { lines.isNotEmpty() },
        )).distinct().take(4).ifEmpty { listOf("forme", "statistiques disponibles", "scénarios calculés") }
    }

    private fun sportInterpretation(profile: SportAiProfile, signalSide: String, lines: List<String>): String {
        val joined = lines.joinToString(" ").lineKey()
        fun hasAny(vararg words: String) = words.any { joined.contains(it.lineKey()) }
        return when (profile.label) {
            "football" -> "Lecture terrain : l’avantage de $signalSide devient plus crédible si le volume tirs/buts et la composition vont dans le même sens ; cartons, absences ou faible création font surtout baisser le score exact."
            "rugby" -> "Lecture terrain : l’avantage de $signalSide dépend moins d’un seul score que du combo discipline + buteurs + efficacité en zone de marque ; cartons et météo peuvent déplacer la valeur vers total points plutôt que vainqueur."
            "tennis" -> "Lecture match : $signalSide prend de la valeur seulement si surface, service et retour racontent la même histoire ; fatigue ou long match récent pèse plus fort qu’une simple place au classement."
            "cyclisme" -> "Lecture course : le signal se joue sur parcours + startlist + rôle d’équipe ; sans leader/favori clairement confirmé, le podium est plus lisible que le vainqueur sec."
            "F1", "NASCAR" -> "Lecture course : le signal dépend du classement actuel/grille, du rythme par tour et de la stratégie pneus/arrêts ; un incident ou une pénalité vaut plus qu’une statistique moyenne."
            "volley" -> "Lecture match : le signal doit venir des sets, du service/réception et des contres ; une mauvaise rotation ou réception fragile change vite la probabilité set par set."
            "basket" -> "Lecture match : le rythme, les rotations et la fatigue pèsent autant que le score brut ; si l’écart reste court, les marchés joueurs/points deviennent plus volatils."
            "baseball" -> "Lecture match : lanceur partant + bullpen donnent la base ; si la relève est fragile, les runs tardifs pèsent plus que le début de match."
            else -> {
                val mainAngle = when {
                    hasAny("score", "classement") -> "score/classement"
                    hasAny("fatigue", "calendrier") -> "fatigue/calendrier"
                    hasAny("blessure", "absence", "suspension") -> "disponibilités"
                    else -> "statistiques propres au sport"
                }
                "Lecture sport : l’angle dominant est $mainAngle ; la projection doit rester liée aux données spécifiques du sport, pas à un modèle football recyclé."
            }
        }
    }

    private fun liveSportInterpretation(event: LiveEventEntity, profile: SportAiProfile, statLines: List<String>): String {
        val joined = statLines.joinToString(" ").lineKey()
        fun hasAny(vararg words: String) = words.any { joined.contains(it.lineKey()) }
        val scoreGap = if (event.homeScore != null && event.awayScore != null) kotlin.math.abs(event.homeScore - event.awayScore) else null
        return when (profile.label) {
            "football" -> "Lecture live foot : score + minute + cartons doivent guider le scénario ; écart court = priorité au prochain but/under-over, écart large = gestion du rythme."
            "rugby" -> "Lecture live rugby : score + temps + discipline pilotent la suite ; carton, pénalités répétées ou domination territoriale changent vite total points et vainqueur."
            "tennis" -> "Lecture live tennis : jeux de service, breaks et durée du match passent avant le classement ; un joueur qui subit au service perd vite la valeur du pré-match."
            "cyclisme" -> "Lecture live cyclisme : top 3, écarts et kilomètres restants priment ; échappée, météo ou chute peuvent rendre le favori pré-course secondaire."
            "F1", "NASCAR" -> "Lecture live course : top 3, tours restants, écarts et stratégie donnent la valeur ; si les écarts sont faibles, un arrêt ou safety car peut renverser le podium."
            else -> {
                val state = when {
                    scoreGap == null -> "classement/état principal"
                    scoreGap <= 2 -> "écart serré"
                    else -> "écart installé"
                }
                val angle = when {
                    hasAny("carton", "pénalité", "exclusion") -> "discipline"
                    hasAny("fatigue", "rotation") -> "fatigue/rotation"
                    else -> "rythme live"
                }
                "Lecture live ${profile.label} : $state + $angle expliquent mieux la suite qu’un simple rappel du score."
            }
        }
    }

    private fun signalSide(selection: String, participants: Participants): String = when {
        participants.first.isNotBlank() && selection.lineKey().contains(participants.first.lineKey()) -> participants.firstLabel
        participants.second.isNotBlank() && selection.lineKey().contains(participants.second.lineKey()) -> participants.secondLabel
        else -> "la sélection"
    }

    private fun operationalReading(
        probability: Int,
        status: LocalAiStatus,
        missingData: List<String>,
        contradictions: List<String>,
    ): String = when {
        status == LocalAiStatus.Solid && probability >= 75 && contradictions.isEmpty() -> "signal prioritaire : les facteurs importants convergent, donc la fiche peut être lue en priorité."
        status == LocalAiStatus.Correct && probability >= 65 -> "signal intéressant : il peut rester devant les autres, mais il dépend encore des dernières infos manquantes."
        missingData.size >= 3 || contradictions.isNotEmpty() -> "signal mitigé : garder l’idée, mais attendre les infos qui manquent avant de le classer en safe."
        probability < 60 -> "signal plutôt exotique : la lecture existe, mais elle ne doit pas passer devant les scénarios plus solides."
        else -> "signal exploitable : assez d’éléments concordent, sans être assez forts pour l’étiqueter comme ultra safe."
    }

    private fun reliabilityLines(
        prediction: PredictionEntity,
        sourceCount: Int,
        evidenceCount: Int,
        status: LocalAiStatus,
    ): List<String> = buildList {
        add("Statut de lecture : ${status.label}.")
        add("Fiabilité moteur : ${prediction.confidenceScore}/100.")
        if (prediction.sourceAgreement > 0) add("Accord des sources : ${prediction.sourceAgreement}/100.")
        add("Sources exploitables listées : $sourceCount.")
        add("Lignes de données relues localement : $evidenceCount.")
        if (prediction.category.isWatchOnlyCategory()) add("Catégorie de l’événement : surveillance, données à compléter ou calendrier.")
    }

    private fun conclusion(
        selection: String,
        status: LocalAiStatus,
        missingData: List<String>,
        contradictions: List<String>,
    ): String {
        if (status == LocalAiStatus.Weak || status == LocalAiStatus.WatchOnly || status == LocalAiStatus.Impossible) {
            return WEAK_DATA_CONCLUSION
        }
        val blockers = (contradictions.filterNot { it.startsWith("Aucune contradiction") } + missingData)
            .take(2)
            .joinToString(" ; ")
        return if (blockers.isBlank()) {
            "Lecture finale : le signal « ${selection.cleanAssistantText()} » est lisible avec les données déjà calculées par l’application."
        } else {
            "Lecture finale : le signal « ${selection.cleanAssistantText()} » reste lisible, avec points à suivre : $blockers."
        }
    }

    private fun readingStatus(
        confidence: Int,
        agreement: Int,
        evidenceCount: Int,
        missingCount: Int,
        contradictionCount: Int,
        watchOnly: Boolean,
    ): LocalAiStatus = when {
        evidenceCount == 0 || confidence <= 20 -> LocalAiStatus.Impossible
        watchOnly || confidence < 38 || evidenceCount < 3 -> LocalAiStatus.WatchOnly
        confidence < 50 || missingCount >= 5 -> LocalAiStatus.Weak
        confidence < 63 || agreement in 1..52 || missingCount >= 3 || contradictionCount >= 2 -> LocalAiStatus.Average
        confidence < 78 || contradictionCount == 1 -> LocalAiStatus.Correct
        else -> LocalAiStatus.Solid
    }

    private fun missingDataLines(
        profile: SportAiProfile,
        prediction: PredictionEntity,
        statLines: List<String>,
        contextLines: List<String>,
        sourceLines: List<String>,
        lineupLines: List<String>,
    ): List<String> {
        val all = statLines + contextLines + sourceLines + lineupLines
        return buildList {
            if (statLines.isEmpty()) add("Statistiques sportives détaillées absentes.")
            if (prediction.expectedScore.isBlank()) add("${profile.scoreLabel} non consolidé.")
            if (prediction.scenarios.isBlank()) add("Scénarios complémentaires absents.")
            if (sourceLines.size < 2) add("Recoupement multi-source limité.")
            if (contextLines.isEmpty()) add("Actualités récentes non exploitables.")
            if (profile.needsLineup && lineupLines.isEmpty()) add("Composition probable/officielle absente.")
            profile.requiredStats
                .filterNot { required -> all.any { it.matchesConcept(required) } }
                .forEach { add("Donnée ${it} manquante.") }
            all.filter { line -> line.hasAny("à compléter", "a completer", "attendre", "à confirmer", "a confirmer", "insuffisant", "manque", "non confirmé", "non confirme") }
                .take(3)
                .forEach { add("Point à compléter : ${it.cleanAssistantText()}") }
        }.distinctBy { it.lineKey() }.take(8)
    }

    private fun importantSignalLines(lines: List<String>, profile: SportAiProfile): List<String> {
        val keywords = profile.newsKeywords + baseNewsKeywords
        return lines
            .map { it.cleanAssistantText() }
            .filter { line -> keywords.any { line.contains(it, ignoreCase = true) } }
            .distinctBy { it.lineKey() }
            .take(8)
    }

    private fun contradictionLines(prediction: PredictionEntity, lines: List<String>): List<String> =
        contradictionLines(prediction.sourceAgreement, lines)

    private fun contradictionLines(sourceAgreement: Int, lines: List<String>): List<String> = buildList {
        lines
            .map { it.cleanAssistantText() }
            .filter { it.hasAny("contradiction", "contradictoire", "diverg", "opposé", "oppose", "pas confirmé", "pas confirme", "incertain", "doubtful", "questionable") }
            .distinctBy { it.lineKey() }
            .take(4)
            .forEach { add(it) }
        if (sourceAgreement in 1..49) {
            add("Accord des sources faible : les données ne convergent pas assez pour une lecture solide.")
        }
    }.distinctBy { it.lineKey() }.take(5)

    private fun participantLines(name: String, lines: List<String>, keywords: List<String>): List<String> {
        if (name.isBlank()) return listOf("Aucun fait relevé")
        val normalizedName = name.lineKey()
        return lines
            .asSequence()
            .map { it.cleanAssistantText() }
            .filter { line ->
                val key = line.lineKey()
                key.contains(normalizedName) || keywords.any { keyword -> key.contains(keyword.normalizedKey()) }
            }
            .filter { line -> keywords.any { keyword -> line.contains(keyword, ignoreCase = true) } || line.lineKey().contains(normalizedName) }
            .distinctBy { it.lineKey() }
            .take(4)
            .toList()
            .ifEmpty { listOf("Aucun fait relevé") }
    }

    private fun sportProfile(sportKey: String): SportAiProfile {
        val normalized = sportKey.normalizedKey()
        val alias = when {
            normalized == "football" -> "soccer"
            normalized.contains("soccer") -> "soccer"
            normalized.contains("rugby") -> "rugby"
            normalized.contains("tennis") || normalized == "atp" || normalized == "wta" -> "tennis"
            normalized.contains("cycling") || normalized.contains("cyclisme") -> "cycling"
            normalized.contains("formula") || normalized == "f1" || normalized.contains("racing") -> "racing"
            normalized.contains("nascar") -> "nascar"
            normalized.contains("golf") -> "golf"
            normalized.contains("mma") -> "mma"
            normalized.contains("boxing") || normalized.contains("boxe") -> "boxing"
            normalized.contains("volley") -> "volleyball"
            normalized.contains("basket") -> "basketball"
            normalized.contains("baseball") -> "baseball"
            normalized.contains("handball") -> "handball"
            else -> normalized
        }
        return sportProfiles[alias] ?: SportAiProfile(
        label = "sport adapté",
        focus = listOf("forme", "statistiques propres au sport", "actualité", "sources"),
        liveFocus = listOf("score ou état", "rythme", "statistiques live", "sources"),
        scoreLabel = "Projection finale",
        requiredStats = listOf("forme", "score", "actualité"),
        requiredLiveStats = listOf("score", "temps"),
        actorSectionTitle = "Acteurs importants",
        needsLineup = false,
        strengthKeywords = baseStrengthKeywords,
        weaknessKeywords = baseWeaknessKeywords,
        newsKeywords = baseNewsKeywords,
    )
    }
}

private data class SportAiProfile(
    val label: String,
    val focus: List<String>,
    val liveFocus: List<String>,
    val scoreLabel: String,
    val requiredStats: List<String>,
    val requiredLiveStats: List<String>,
    val actorSectionTitle: String,
    val needsLineup: Boolean,
    val strengthKeywords: List<String>,
    val weaknessKeywords: List<String>,
    val newsKeywords: List<String>,
)

private data class Participants(
    val first: String,
    val second: String,
) {
    val firstLabel: String = first.ifBlank { "participant A" }
    val secondLabel: String = second.ifBlank { "participant B" }
}

private val baseStrengthKeywords = listOf("hausse", "solide", "productif", "élevé", "avantage", "forme", "marqué", "gagné", "victoire", "domin")
private val baseWeaknessKeywords = listOf("baisse", "limité", "fragile", "encaiss", "moyen", "risque", "fatigue", "bless", "absence", "suspend")
private val baseNewsKeywords = listOf(
    "blessure",
    "blessé",
    "forfait",
    "absence",
    "suspension",
    "suspendu",
    "carton",
    "retour",
    "coach",
    "entraîneur",
    "composition",
    "officiel",
    "probable",
    "fatigue",
    "déplacement",
    "calendrier",
    "enjeu",
    "météo",
    "pluie",
    "vent",
)

private val sportProfiles = mapOf(
    "soccer" to SportAiProfile(
        label = "football",
        focus = listOf("score probable", "buts", "absences", "forme", "compositions"),
        liveFocus = listOf("score", "temps de jeu", "cartons", "tirs", "rythme"),
        scoreLabel = "Score probable",
        requiredStats = listOf("buts", "tirs", "corners", "cartons", "composition"),
        requiredLiveStats = listOf("score", "temps", "cartons", "tirs"),
        actorSectionTitle = "Joueurs impact",
        needsLineup = true,
        strengthKeywords = baseStrengthKeywords + listOf("buts", "tirs", "xg", "corners", "clean sheet"),
        weaknessKeywords = baseWeaknessKeywords + listOf("encaiss", "carton", "blessure", "suspension"),
        newsKeywords = baseNewsKeywords + listOf("composition", "titulaire", "remplaçant", "carton"),
    ),
    "rugby" to SportAiProfile(
        label = "rugby",
        focus = listOf("points", "essais", "discipline", "buteurs", "météo"),
        liveFocus = listOf("score", "temps de jeu", "cartons", "pénalités", "essais"),
        scoreLabel = "Total de points probable",
        requiredStats = listOf("points", "essais", "discipline", "météo"),
        requiredLiveStats = listOf("score", "temps", "cartons", "essais"),
        actorSectionTitle = "Joueurs / buteurs",
        needsLineup = true,
        strengthKeywords = baseStrengthKeywords + listOf("points", "essais", "pénalité", "buteur"),
        weaknessKeywords = baseWeaknessKeywords + listOf("discipline", "carton", "pénalité", "blessure"),
        newsKeywords = baseNewsKeywords + listOf("composition", "essai", "buteur", "météo"),
    ),
    "tennis" to SportAiProfile(
        label = "tennis",
        focus = listOf("surface", "service", "retour", "fatigue", "sets"),
        liveFocus = listOf("sets", "jeux", "service", "breaks", "fatigue"),
        scoreLabel = "Score en sets",
        requiredStats = listOf("surface", "service", "retour", "fatigue", "sets"),
        requiredLiveStats = listOf("sets", "service", "break"),
        actorSectionTitle = "Joueurs et marchés utiles",
        needsLineup = false,
        strengthKeywords = baseStrengthKeywords + listOf("service", "aces", "retour", "break", "surface"),
        weaknessKeywords = baseWeaknessKeywords + listOf("double faute", "fatigue", "break concédé", "blessure"),
        newsKeywords = baseNewsKeywords + listOf("surface", "fatigue", "dernier match", "forfait"),
    ),
    "cycling" to SportAiProfile(
        label = "cyclisme",
        focus = listOf("parcours", "météo", "startlist", "favoris", "rôle d’équipe"),
        liveFocus = listOf("classement", "écart", "kilomètres restants", "échappée", "météo"),
        scoreLabel = "Classement/podium projeté",
        requiredStats = listOf("parcours", "météo", "startlist", "favoris"),
        requiredLiveStats = listOf("classement", "km", "écart"),
        actorSectionTitle = "Coureurs / rôles",
        needsLineup = false,
        strengthKeywords = baseStrengthKeywords + listOf("favori", "forme", "parcours", "grimpeur", "sprinteur"),
        weaknessKeywords = baseWeaknessKeywords + listOf("chute", "abandon", "météo", "rôle", "fatigue"),
        newsKeywords = baseNewsKeywords + listOf("startlist", "chute", "abandon", "météo", "rôle"),
    ),
    "racing" to motorSportProfile("F1"),
    "nascar" to motorSportProfile("NASCAR"),
    "handball" to teamIndoorProfile("handball", "buts", listOf("gardiens", "exclusions", "jets de 7 mètres")),
    "volleyball" to teamIndoorProfile("volley", "sets", listOf("service", "réception", "contres")),
    "basketball" to teamIndoorProfile("basket", "points", listOf("rythme", "rotations", "rebonds", "passes")),
    "baseball" to SportAiProfile(
        label = "baseball",
        focus = listOf("runs", "lanceurs", "bullpen", "coups sûrs"),
        liveFocus = listOf("score", "manche", "lanceur", "bullpen", "coups sûrs"),
        scoreLabel = "Runs probables",
        requiredStats = listOf("runs", "lanceur", "bullpen", "coups sûrs"),
        requiredLiveStats = listOf("score", "manche", "lanceur"),
        actorSectionTitle = "Lanceurs / joueurs clés",
        needsLineup = true,
        strengthKeywords = baseStrengthKeywords + listOf("runs", "hit", "coups sûrs", "lanceur", "bullpen"),
        weaknessKeywords = baseWeaknessKeywords + listOf("runs encaissés", "bullpen", "fatigue lanceur"),
        newsKeywords = baseNewsKeywords + listOf("lanceur", "bullpen", "lineup"),
    ),
    "golf" to SportAiProfile(
        label = "golf",
        focus = listOf("parcours", "météo", "forme récente", "putting", "précision"),
        liveFocus = listOf("classement", "trous restants", "score", "météo", "putting"),
        scoreLabel = "Classement / top projeté",
        requiredStats = listOf("parcours", "météo", "putting", "forme"),
        requiredLiveStats = listOf("classement", "score", "trous"),
        actorSectionTitle = "Golfeurs suivis",
        needsLineup = false,
        strengthKeywords = baseStrengthKeywords + listOf("putting", "précision", "strokes gained", "top 10"),
        weaknessKeywords = baseWeaknessKeywords + listOf("cut", "bogey", "vent", "irrégulier"),
        newsKeywords = baseNewsKeywords + listOf("parcours", "vent", "forfait", "cut"),
    ),
    "mma" to combatSportProfile("sports de combat"),
    "boxing" to combatSportProfile("sports de combat"),
)

private fun motorSportProfile(label: String) = SportAiProfile(
    label = label,
    focus = listOf("grille", "circuit", "rythme", "fiabilité", "stratégie"),
    liveFocus = listOf("classement", "tours", "écarts", "stratégie", "fiabilité"),
    scoreLabel = "Top 3 / classement projeté",
    requiredStats = listOf("grille", "circuit", "rythme", "fiabilité"),
    requiredLiveStats = listOf("classement", "tours", "écart"),
    actorSectionTitle = "Pilotes / équipes",
    needsLineup = false,
    strengthKeywords = baseStrengthKeywords + listOf("grille", "rythme", "pneu", "fiabilité", "circuit"),
    weaknessKeywords = baseWeaknessKeywords + listOf("fiabilité", "panne", "dégradation", "stratégie"),
    newsKeywords = baseNewsKeywords + listOf("grille", "pénalité", "moteur", "stratégie"),
)

private fun teamIndoorProfile(label: String, scoreWord: String, extraStats: List<String>) = SportAiProfile(
    label = label,
    focus = listOf(scoreWord, "rythme") + extraStats,
    liveFocus = listOf("score", "temps", scoreWord, "rythme") + extraStats.take(2),
    scoreLabel = "Projection $scoreWord",
    requiredStats = listOf(scoreWord, "forme") + extraStats,
    requiredLiveStats = listOf("score", "temps", scoreWord),
    actorSectionTitle = "Joueurs impact",
    needsLineup = label != "volley",
    strengthKeywords = baseStrengthKeywords + listOf(scoreWord, "rythme") + extraStats,
    weaknessKeywords = baseWeaknessKeywords + listOf("fatigue", "rotation", "discipline"),
    newsKeywords = baseNewsKeywords + listOf("rotation", "composition"),
)

private fun combatSportProfile(label: String) = SportAiProfile(
    label = label,
    focus = listOf("style", "allonge", "cardio", "grappling/striking", "forme récente"),
    liveFocus = listOf("round", "volume", "fatigue", "domination", "cartes juges"),
    scoreLabel = "Méthode / durée probable",
    requiredStats = listOf("style", "allonge", "cardio", "forme"),
    requiredLiveStats = listOf("round", "volume", "fatigue"),
    actorSectionTitle = "Combattants",
    needsLineup = false,
    strengthKeywords = baseStrengthKeywords + listOf("ko", "soumission", "grappling", "striking", "cardio", "allonge"),
    weaknessKeywords = baseWeaknessKeywords + listOf("cut", "poids", "fatigue", "menton", "blessure"),
    newsKeywords = baseNewsKeywords + listOf("pesée", "weigh-in", "cut", "camp"),
)

private fun PredictionEntity.participants(): Participants =
    Participants(
        first = homeTeam.cleanAssistantText().ifBlank { selection.cleanAssistantText() },
        second = awayTeam.cleanAssistantText(),
    )

private fun String.cleanAssistantLines(): List<String> =
    lineSequence()
        .flatMap { it.split('•', ';').asSequence() }
        .map { it.cleanAssistantText() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lineKey() }
        .toList()

private fun String.cleanAssistantText(): String {
    var result = this.trim()
    hardAssistantReplacements.forEach { (bad, good) -> result = result.replace(bad, good) }
    return result.replace(Regex("\\s+"), " ").trim()
}

private val hardAssistantReplacements = listOf(
    "Ã©" to "é",
    "Ã¨" to "è",
    "Ãª" to "ê",
    "Ã " to "à",
    "Ã¹" to "ù",
    "Ã´" to "ô",
    "Ã®" to "î",
    "Ã§" to "ç",
    "â€™" to "’",
    "â€”" to "—",
    "Â·" to "·",
    "Â" to "",
)

private fun String.lineKey(): String = Normalizer.normalize(cleanAssistantText().lowercase(Locale.FRANCE), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun String.normalizedKey(): String = lineKey().replace(" ", "_")

private fun String.hasAny(vararg needles: String): Boolean = needles.any { contains(it, ignoreCase = true) }

private fun String.matchesConcept(concept: String): Boolean {
    val conceptKey = concept.lineKey()
    val self = lineKey()
    return self.contains(conceptKey) || conceptSynonyms[conceptKey].orEmpty().any { self.contains(it.lineKey()) }
}

private val conceptSynonyms = mapOf(
    "buts" to listOf("but", "goals", "marqués"),
    "points" to listOf("point", "pts"),
    "essais" to listOf("essai", "try"),
    "discipline" to listOf("carton", "pénalité", "exclusion"),
    "meteo" to listOf("météo", "pluie", "vent", "chaleur"),
    "composition" to listOf("lineup", "titulaire", "remplaçant"),
    "service" to listOf("aces", "première balle", "jeux de service"),
    "retour" to listOf("break", "retour"),
    "surface" to listOf("terre", "gazon", "dur", "surface"),
    "sets" to listOf("set", "sets"),
    "classement" to listOf("top 3", "position", "ranking"),
    "km" to listOf("kilomètres", "distance"),
    "ecart" to listOf("écart", "gap"),
    "tours" to listOf("tour", "lap"),
    "lanceur" to listOf("pitcher", "starter"),
    "coups surs" to listOf("hit", "hits"),
)

private fun List<String>.takeUseful(max: Int): List<String> =
    map { it.cleanAssistantText() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lineKey() }
        .take(max)
        .ifEmpty { listOf("Aucun fait relevé") }

private fun String.isWatchOnlySelection(): Boolean =
    hasAny("surveiller", "attendre", "données à compléter", "donnees a completer", "projection impossible")

private fun String.isWatchOnlyCategory(): Boolean =
    hasAny("calendar", "calendrier", "surveillance", "données à compléter", "donnees a completer")
