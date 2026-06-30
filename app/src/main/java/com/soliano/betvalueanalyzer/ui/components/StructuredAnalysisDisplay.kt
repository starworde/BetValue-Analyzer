package com.soliano.betvalueanalyzer.ui.components

import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.domain.isNeutralVenueCompetition
import com.soliano.betvalueanalyzer.domain.isVenueEdgeLine
import com.soliano.betvalueanalyzer.domain.neutralVenueSummary
import com.soliano.betvalueanalyzer.domain.SportIntelligenceCatalog
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class StructuredAnalysisDossier(
    val participantSituation: List<String>,
    val participantSituationBlocks: List<StructuredSituationBlock>,
    val competitionSituation: List<String>,
    val competitionStakeBlocks: List<StructuredCompetitionStakeBlock>,
    val competitionImpact: List<String>,
    val rawStats: List<String>,
    val participantBlocks: List<StructuredParticipantBlock>,
    val participantStatRequirementBlocks: List<StructuredParticipantStatRequirementBlock>,
    val formTrends: List<String>,
    val formTrendBlocks: List<StructuredFormTrendBlock>,
    val importantInfo: List<StructuredReliabilityLine>,
    val importantInfoBlocks: List<StructuredReliabilityBlock>,
    val sourceReliability: List<StructuredReliabilityLine>,
    val sourceCoverage: List<StructuredSourceCoverageLine>,
    val globalProbabilities: List<StructuredProbabilityLine>,
    val globalProbabilityGroups: List<StructuredProbabilityGroup>,
    val globalMarketRequirements: List<StructuredGlobalMarketRequirement>,
    val sportProbabilityHints: List<StructuredSportScenarioHint>,
    val participantProbabilities: List<StructuredProbabilityLine>,
    val participantProbabilityHintBlocks: List<StructuredParticipantScenarioHintBlock>,
    val playerProbabilities: List<StructuredProbabilityLine>,
    val sportActorProbabilityHints: List<StructuredSportScenarioHint>,
    val actorBlocks: List<StructuredActorBlock>,
    val actorStatRequirementBlocks: List<StructuredActorStatRequirementBlock>,
    val actorStatRequirements: List<String>,
    val finalExplanation: List<String>,
    val finalExplanationBlocks: List<StructuredExplanationBlock>,
    val sportSpecificChecklist: List<String>,
)

data class StructuredSituationBlock(
    val name: String,
    val lines: List<String>,
)

data class StructuredCompetitionStakeBlock(
    val name: String,
    val statuses: List<String>,
    val reason: String,
    val forecastImpact: List<String>,
    val reading: String,
)

data class StructuredParticipantBlock(
    val name: String,
    val stats: List<String>,
    val probabilities: List<StructuredProbabilityLine>,
)

data class StructuredParticipantStatRequirementBlock(
    val name: String,
    val missingStats: List<String>,
)

data class StructuredActorBlock(
    val name: String,
    val teamOrRole: String,
    val situation: List<String>,
    val stats: List<String>,
    val importantInfo: List<StructuredReliabilityLine>,
    val probabilities: List<StructuredProbabilityLine>,
)

data class StructuredActorStatRequirementBlock(
    val name: String,
    val teamOrRole: String,
    val missingStats: List<String>,
)

data class StructuredFormTrendBlock(
    val name: String,
    val trends: List<String>,
)

data class StructuredReliabilityBlock(
    val name: String,
    val items: List<StructuredReliabilityLine>,
)

data class StructuredReliabilityLine(
    val text: String,
    val level: String,
    val category: String = "Info à qualifier",
)

data class StructuredSourceCoverageLine(
    val family: String,
    val status: String,
    val detail: String,
)

data class StructuredProbabilityLine(
    val type: String,
    val label: String,
    val probability: Double,
)

data class StructuredProbabilityGroup(
    val title: String,
    val probabilities: List<StructuredProbabilityLine>,
)

data class StructuredGlobalMarketRequirement(
    val family: String,
    val markets: String,
    val status: String,
    val detail: String,
)

data class StructuredSportScenarioHint(
    val type: String,
    val label: String,
    val priority: Double,
)

data class StructuredParticipantScenarioHintBlock(
    val name: String,
    val hints: List<StructuredSportScenarioHint>,
)

data class StructuredExplanationBlock(
    val title: String,
    val lines: List<String>,
)

object StructuredAnalysisCache {
    private const val MAX_ENTRIES = 220
    private val dossiers = ConcurrentHashMap<String, StructuredAnalysisDossier>()

    fun get(prediction: PredictionEntity): StructuredAnalysisDossier? = dossiers[prediction.structuredCacheKey()]

    fun getOrBuild(prediction: PredictionEntity): StructuredAnalysisDossier =
        dossiers[prediction.structuredCacheKey()] ?: prediction.structuredAnalysisDossier().also { dossier ->
            if (dossiers.size > MAX_ENTRIES) dossiers.clear()
            dossiers[prediction.structuredCacheKey()] = dossier
        }

    fun preload(predictions: List<PredictionEntity>, limit: Int = 80) {
        predictions
            .asSequence()
            .take(limit)
            .forEach { prediction ->
                val key = prediction.structuredCacheKey()
                if (!dossiers.containsKey(key)) {
                    if (dossiers.size > MAX_ENTRIES) dossiers.clear()
                    dossiers[key] = prediction.structuredAnalysisDossier()
                }
            }
    }
}

private fun PredictionEntity.structuredCacheKey(): String =
    listOf(
        id,
        eventId,
        sourceLastUpdate,
        confidenceScore,
        statSummary.hashCode(),
        contextInsights.hashCode(),
        scenarios.hashCode(),
        playerScenarios.hashCode(),
        sourceDetails.hashCode(),
    ).joinToString(":")

fun PredictionEntity.structuredAnalysisDossier(): StructuredAnalysisDossier {
    val neutralVenueNote = neutralVenueSummary(sportKey, sportTitle, competitionName, homeTeam = homeTeam, awayTeam = awayTeam, commenceTime = commenceTime)
    val neutralVenue = neutralVenueNote != null
    val statLines = statSummary.cleanLines()
        .filterNot { neutralVenue && isVenueEdgeLine(it) }
        .let { lines -> if (neutralVenueNote != null) lines + neutralVenueNote else lines }
        .distinct()
    val contextLines = contextInsights.cleanLines()
        .filterNot { neutralVenue && isVenueEdgeLine(it) }
    val sourceLines = sourceDetails.cleanLines()
    val positiveLines = positiveArguments.cleanLines()
        .filterNot { neutralVenue && isVenueEdgeLine(it) }
    val negativeLines = negativeArguments.cleanLines()
        .filterNot { neutralVenue && isVenueEdgeLine(it) }
    val allContext = (statLines + contextLines + positiveLines + negativeLines).distinct()

    val sportProfile = SportIntelligenceCatalog.profile(sportKey)
    val sourceSportPackLines = statLines.filter(::isSportChecklistLine)
    val catalogSportPackLines = sportProfile.summaryLines.map(::cleanDisplayText)
    val sportPackLines = (sourceSportPackLines + catalogSportPackLines).distinctBy(::normalizeForStructure)
    val usableStatLines = statLines.filterNot(::isSportChecklistLine)
    val competitionLines = allContext.filter(::isCompetitionSituationLine)
    val formLines = allContext.filter(::isFormTrendLine)
    val importantLines = (contextLines + allContext.filter(::isImportantInfoLine)).distinct()
    val sourceReliability = buildList {
        add("Source principale : ${cleanDisplayText(sourceName)}".withReliability(sourceAgreement, confidenceScore))
        if (sourceAgreement > 0) add("Accord des sources/statistiques : $sourceAgreement/100".withReliability(sourceAgreement, confidenceScore))
        add("Fiabilité des données : $confidenceScore/100 · risque $riskLevel".withReliability(sourceAgreement, confidenceScore))
        sourceLines.forEach { add(it.withReliability(sourceAgreement, confidenceScore)) }
    }.distinct()
    val sourceCoverage = sourceCoverageLines(
        sourceName = sourceName,
        sourceLines = sourceLines,
        signalLines = allContext + listOf(
            "composition ${homeLineupStatus} ${awayLineupStatus}",
            homeLineup,
            awayLineup,
            competitionName,
            sportTitle,
        ),
    )

    val rawLines = usableStatLines
        .filterNot { line -> line in competitionLines || line in formLines || line in importantLines }
        .ifEmpty {
            usableStatLines.takeIf { it.isNotEmpty() } ?: listOf("Statistiques brutes encore à consolider avec les flux publics disponibles.")
        }

    val scenarioLines = scenarios.toStructuredProbabilities()
    val playerLines = playerScenarios.toStructuredProbabilities()
    val participantNames = listOf(homeTeam, awayTeam, selection)
        .map(::cleanDisplayText)
        .filter { it.isNotBlank() }
    val participantScenarios = scenarioLines.filter { scenario ->
        scenario.mentionsAny(participantNames) || scenario.type.isParticipantProbabilityType()
    }
    val globalScenarios = scenarioLines.filterNot { scenario -> scenario in participantScenarios }
    val participantAliasList = participantAliases()
    val participantSituationBlocks = participantSituationBlocks(
        participants = participantAliasList,
        rawLines = rawLines,
        competitionLines = competitionLines,
        formLines = formLines,
        importantLines = importantLines,
    )
    val competitionStakeBlocks = competitionStakeBlocks(participantAliasList, allContext)
    val actorAliasList = actorAliases(playerLines)
    val participantProbabilityHintBlocks = participantProbabilityHintBlocks(participantAliasList)
    val participantBlocks = participantAliasList.mapNotNull { participant ->
        val stats = rawLines.filter { line -> participant.matches(line) }
        val probabilities = participantScenarios.filter { scenario ->
            participant.matches(scenario.label) || participant.matches(scenario.type)
        }
        if (stats.isEmpty() && probabilities.isEmpty()) return@mapNotNull null
        StructuredParticipantBlock(
            name = participant.displayName,
            stats = stats.distinct(),
            probabilities = probabilities.distinctBy { it.type + it.label },
        )
    }
    val participantStatRequirementBlocks = participantStatRequirementBlocks(
        participants = participantAliasList,
        rawLines = rawLines,
        expectedStats = sportProfile.watchedStats,
    )
    val formTrendBlocks = participantFormTrendBlocks(
        participants = participantAliasList,
        formLines = formLines,
        rawLines = rawLines,
        importantLines = importantLines,
        competitionLines = competitionLines,
    )
    val importantInfoBlocks = buildList {
        participantAliasList.forEach { participant ->
            val items = importantLines
                .filter { line -> participant.matches(line) }
                .distinct()
                .map { it.withReliability(sourceAgreement, confidenceScore) }
            if (items.isNotEmpty()) {
                add(StructuredReliabilityBlock(participant.displayName, items))
            }
        }
        actorAliasList.forEach { actor ->
            val items = importantLines
                .filter { line -> actor.matches(line) }
                .distinct()
                .map { it.withReliability(sourceAgreement, confidenceScore) }
            if (items.isNotEmpty()) {
                add(StructuredReliabilityBlock(actor.displayName, items))
            }
        }
    }.distinctBy { normalizeForStructure(it.name) }
    val participantAssignedStats = participantBlocks.flatMap { it.stats }.toSet()
    val participantAssignedFormTrends = formTrendBlocks.flatMap { it.trends }.toSet()
    val assignedImportantInfoTexts = importantInfoBlocks.flatMap { block -> block.items.map { it.text } }.toSet()
    val globalRawStats = rawLines
        .filterNot { it in participantAssignedStats }
        .ifEmpty {
            if (participantBlocks.isNotEmpty()) emptyList()
            else listOf("Statistiques brutes encore à consolider avec les flux publics disponibles.")
        }
    val actorBlocks = actorAliasList.mapNotNull { actor ->
        val stats = usableStatLines.filter { line -> actor.matches(line) }
        val info = importantLines.filter { line -> actor.matches(line) }
        val form = formLines.filter { line -> actor.matches(line) }
        val probabilities = playerLines.filter { scenario ->
            actor.matches(scenario.label) || actor.matches(scenario.type)
        }
        if (stats.isEmpty() && info.isEmpty() && form.isEmpty() && probabilities.isEmpty()) return@mapNotNull null
        StructuredActorBlock(
            name = actor.displayName,
            teamOrRole = actor.teamOrRole,
            situation = actor.situation + form,
            stats = stats.distinct(),
            importantInfo = info.distinct().map { it.withReliability(sourceAgreement, confidenceScore) },
            probabilities = probabilities.distinctBy { it.type + it.label },
        )
    }.distinctBy { normalizeForStructure(it.name) + normalizeForStructure(it.teamOrRole) }
    val actorStatRequirementBlocks = actorStatRequirementBlocks(
        actors = actorAliasList,
        statLines = usableStatLines,
        expectedStats = sportProfile.playerStats,
    )
    val globalProbabilityList = buildList {
        add(StructuredProbabilityLine("Signal principal", cleanDisplayText(selection), consensusProbability))
        if (expectedScore.isNotBlank()) {
            add(StructuredProbabilityLine("Score / état probable", cleanDisplayText(expectedScore), scoreProbability(globalScenarios)))
        }
        addAll(globalScenarios)
    }.distinctBy { it.type + it.label }
    val competitionImpactList = competitionImpactLines(competitionLines)
    val finalExplanationList = buildList {
        add(cleanDisplayText(explanation))
        positiveLines.takeIf { it.isNotEmpty() }?.let { add("Points favorables : ${it.joinToString(" · ")}") }
        negativeLines.takeIf { it.isNotEmpty() }?.let { add("Points de vigilance : ${it.joinToString(" · ")}") }
    }.filter { it.isNotBlank() }

    return StructuredAnalysisDossier(
        participantSituation = participantSituationLines(),
        participantSituationBlocks = participantSituationBlocks,
        competitionSituation = competitionLines.ifEmpty {
            listOf("Aucune situation de compétition utile confirmée par les sources.")
        },
        competitionStakeBlocks = competitionStakeBlocks,
        competitionImpact = competitionImpactList,
        rawStats = globalRawStats,
        participantBlocks = participantBlocks,
        participantStatRequirementBlocks = participantStatRequirementBlocks,
        formTrends = formLines.filterNot { it in participantAssignedFormTrends },
        formTrendBlocks = formTrendBlocks,
        importantInfo = importantLines.filterNot { cleanDisplayText(it) in assignedImportantInfoTexts }.ifEmpty {
            if (importantInfoBlocks.isNotEmpty()) emptyList()
            else listOf("Aucun fait relevé")
        }.map { it.withReliability(sourceAgreement, confidenceScore) },
        importantInfoBlocks = importantInfoBlocks,
        sourceReliability = sourceReliability,
        sourceCoverage = sourceCoverage,
        globalProbabilities = globalProbabilityList,
        globalProbabilityGroups = globalProbabilityList.toProbabilityGroups(),
        globalMarketRequirements = globalMarketRequirementLines(sportKey, globalProbabilityList),
        sportProbabilityHints = sportProfile.probabilityScenarios.map {
            StructuredSportScenarioHint(
                type = cleanDisplayText(it.type),
                label = cleanDisplayText(it.label),
                priority = it.probability,
            )
        },
        participantProbabilities = participantScenarios.distinctBy { it.type + it.label },
        participantProbabilityHintBlocks = participantProbabilityHintBlocks,
        playerProbabilities = playerLines.distinctBy { it.type + it.label },
        sportActorProbabilityHints = sportProfile.playerProbabilityScenarios.map {
            StructuredSportScenarioHint(
                type = cleanDisplayText(it.type),
                label = cleanDisplayText(it.label),
                priority = it.probability,
            )
        },
        actorBlocks = actorBlocks,
        actorStatRequirementBlocks = actorStatRequirementBlocks,
        actorStatRequirements = if (actorStatRequirementBlocks.isEmpty()) sportProfile.playerStats.map(::cleanDisplayText) else emptyList(),
        finalExplanation = finalExplanationList,
        finalExplanationBlocks = finalExplanationBlocks(
            baseExplanation = finalExplanationList,
            positiveLines = positiveLines,
            negativeLines = negativeLines,
            competitionImpact = competitionImpactList,
            globalProbabilities = globalProbabilityList,
            participantProbabilities = participantScenarios,
            playerProbabilities = playerLines,
        ),
        sportSpecificChecklist = sportPackLines,
    )
}

private fun PredictionEntity.participantSituationLines(): List<String> = buildList {
    add("${cleanDisplayText(sportTitle)} · ${cleanDisplayText(competitionName)}")
    add("Début prévu : ${formatDate(commenceTime)}")
    val home = cleanDisplayText(homeTeam)
    val away = cleanDisplayText(awayTeam)
    val neutralVenueNote = neutralVenueSummary(sportKey, sportTitle, competitionName, homeTeam = homeTeam, awayTeam = awayTeam, commenceTime = commenceTime)
    if (sportKey.substringBefore('/') in resultBoardSports) {
        add("Événement/participant principal : ${cleanDisplayText(home.ifBlank { selection })}")
        if (away.isNotBlank()) add("Catégorie/session suivie : $away")
    } else {
        add("Participant A : ${displayTeamName(home)}")
        add("Participant B : ${displayTeamName(away)}")
        add(neutralVenueNote ?: "Domicile/extérieur : ${displayTeamName(home)} reçoit, ${displayTeamName(away)} se déplace si le lieu publié le confirme.")
    }
}.distinct()

private fun PredictionEntity.participantSituationBlocks(
    participants: List<ParticipantAlias>,
    rawLines: List<String>,
    competitionLines: List<String>,
    formLines: List<String>,
    importantLines: List<String>,
): List<StructuredSituationBlock> {
    val sport = sportKey.substringBefore('/')
    val neutralVenue = isNeutralVenueCompetition(sportKey, sportTitle, competitionName, homeTeam = homeTeam, awayTeam = awayTeam, commenceTime = commenceTime)
    return participants.mapIndexed { index, participant ->
        val participantCompetition = competitionLines.filter { participant.matches(it) }.distinct()
        val participantForm = formLines.filter { participant.matches(it) }.distinct()
        val participantImportant = importantLines.filter { participant.matches(it) }.distinct()
        val participantRaw = rawLines.filter { participant.matches(it) }.distinct()
        val role = when {
            sport in resultBoardSports -> "Rôle : événement, course, session ou participant principal suivi."
            neutralVenue -> "Rôle : participant sur terrain neutre, aucun avantage domicile/extérieur appliqué."
            index == 0 -> "Rôle : domicile / participant A, si le lieu publié confirme l’avantage terrain."
            else -> "Rôle : extérieur / participant B."
        }
        StructuredSituationBlock(
            name = participant.displayName,
            lines = buildList {
                add(role)
                if (participantCompetition.isEmpty()) {
                    add("Situation compétition : aucune donnée de classement exploitable pour ce participant.")
                } else {
                    participantCompetition.take(4).forEach { add(labeledSituationLine("Situation compétition", it)) }
                }
                participantRaw.firstOrNull { line ->
                    val text = normalizeForStructure(line)
                    listOf("classement", "points", "matchs", "victoires", "defaites", "buts", "essais", "runs").any { it in text }
                }?.let { add(labeledSituationLine("Repère brut", it)) }
                if (participantForm.isEmpty()) {
                    add("Forme/confiance : tendance récente encore insuffisante pour trancher hausse, stabilité ou baisse.")
                } else {
                    participantForm.take(3).forEach { add(labeledSituationLine("Forme/confiance", it)) }
                }
                participantImportant.take(3).forEach { add(labeledSituationLine("Info clé", it)) }
                participantActionableReading(participant.displayName, participantCompetition + participantForm + participantImportant + participantRaw)
                    .takeIf { it.isNotBlank() }
                    ?.let { add(it) }
            }.distinct(),
        )
    }
}

private fun labeledSituationLine(label: String, line: String): String {
    val clean = cleanDisplayText(line)
    val normalized = normalizeForStructure(clean)
    val normalizedLabel = normalizeForStructure(label)
    return if (normalized.startsWith(normalizedLabel)) clean else "$label : $clean"
}

private fun participantActionableReading(name: String, lines: List<String>): String {
    val text = normalizeForStructure(lines.joinToString(" "))
    val cleanName = cleanDisplayText(name)
    return when {
        "doit absolument gagner" in text || ("doit" in text && "gagner" in text) || "besoin de victoire" in text ->
            "Impact concret : $cleanName doit produire plus de volume offensif : tirs, occasions et buts/points deviennent prioritaires."
        "ne pas perdre" in text || "faire nul" in text || "nul suffit" in text ->
            "Impact concret : $cleanName peut privilégier la solidité ; regarder buts/points encaissés, tirs subis et clean sheet plutôt que simple victoire."
        "deja qualifie" in text || "deja qualifiee" in text || "rotation" in text || "faire tourner" in text ->
            "Impact concret : composition prioritaire pour $cleanName ; les marchés joueurs/buteurs/score exact baissent si titulaires protégés."
        "deja elimine" in text || "deja eliminee" in text ->
            "Impact concret : intensité moins fiable pour $cleanName ; ne renforcer le signal que si les stats récentes montrent encore du volume."
        "fatigue" in text || "minutes" in text || "calendrier charge" in text ->
            "Impact concret : charge/fatigue pour $cleanName ; baisse possible du pressing, du volume et du temps de jeu des cadres."
        "bless" in text || "absent" in text || "suspend" in text || "indisponible" in text ->
            "Impact concret : disponibilité de $cleanName ; absence ou retour d’un cadre change attaque, défense et projections joueurs."
        "domicile" in text || "recoit" in text ->
            "Impact concret : avantage terrain seulement avec lieu confirmé ; utiliser production maison/extérieure plutôt qu’un domicile automatique."
        else -> ""
    }
}

private fun participantSituationReading(name: String, lines: List<String>): String =
    participantActionableReading(name, lines)

private fun competitionStakeBlocks(
    participants: List<ParticipantAlias>,
    contextLines: List<String>,
): List<StructuredCompetitionStakeBlock> =
    participants.map { participant ->
        val lines = contextLines.filter { line -> participant.matches(line) }
        val statuses = competitionStatuses(lines).ifEmpty { listOf("Enjeu à confirmer") }
        StructuredCompetitionStakeBlock(
            name = participant.displayName,
            statuses = statuses,
            reason = competitionStakeReason(participant.displayName, statuses, lines),
            forecastImpact = competitionStakeForecastImpact(participant.displayName, statuses),
            reading = competitionStakeReading(participant.displayName, statuses, lines),
        )
    }

private fun competitionStatuses(lines: List<String>): List<String> {
    val text = normalizeForStructure(lines.joinToString(" "))
    return buildList {
        if ("doit absolument gagner" in text || ("doit" in text && "gagner" in text) || "besoin de victoire" in text) {
            add("Doit absolument gagner")
        }
        if ("doit au minimum faire nul" in text || "doit faire nul" in text || "nul suffit" in text) {
            add("Doit au minimum faire nul")
        }
        if ("ne pas perdre" in text || "non defaite" in text) {
            add("Doit ne pas perdre")
        }
        if ("defendre" in text && ("premiere place" in text || "deuxieme place" in text || "classement" in text || "place qualificative" in text)) {
            add("Doit défendre sa place")
        }
        if ("resultat tres important" in text || "tres important" in text || "qualification" in text && ("pts" in text || "point" in text)) {
            add("Résultat très important")
        }
        if ("deja qualifie" in text || "deja qualifiee" in text) {
            add("Déjà qualifié")
        }
        if ("deja elimine" in text || "deja eliminee" in text) {
            add("Déjà éliminé")
        }
        if ("match sans enjeu" in text || "sans enjeu majeur" in text || "resultat indifferent" in text) {
            add("Match sans enjeu majeur")
        }
        if ("rotation" in text || "faire tourner" in text) {
            add("Rotation possible")
        }
        if ("difference de buts" in text || "soigner la difference" in text) {
            add("Doit soigner la différence")
        }
        if ("marquer beaucoup" in text) {
            add("Doit marquer beaucoup")
        }
        if ("carton" in text || "blessure" in text || "suspension" in text) {
            add("Doit éviter cartons/blessures")
        }
        if ("prochain match" in text || "prepare un autre match" in text) {
            add("Prépare un match plus important")
        }
    }.distinct()
}

private fun competitionStakeReading(name: String, statuses: List<String>, lines: List<String>): String {
    val cleanName = cleanDisplayText(name)
    val normalizedStatuses = statuses.joinToString(" ").let(::normalizeForStructure)
    return when {
        "doit absolument gagner" in normalizedStatuses ->
            "$cleanName doit transformer l’enjeu en faits mesurables : tirs/attaques, buts ou points marqués, et espaces laissés derrière."
        "resultat tres important" in normalizedStatuses ->
            "$cleanName a un résultat important, mais il n’est utile que si les stats récentes confirment du volume ou une défense solide."
        "doit au minimum faire nul" in normalizedStatuses || "doit ne pas perdre" in normalizedStatuses ->
            "$cleanName peut privilégier la solidité : buts/points encaissés, tirs subis et rythme réel comptent plus qu’une simple victoire."
        "deja qualifie" in normalizedStatuses || "rotation possible" in normalizedStatuses ->
            "$cleanName peut changer ses titulaires : joueurs, buteurs/essais, podiums et score exact deviennent plus fragiles."
        "deja elimine" in normalizedStatuses || "match sans enjeu majeur" in normalizedStatuses ->
            "$cleanName ne doit être retenu que si les derniers matchs montrent encore du volume, pas seulement parce que l’affiche existe."
        "enjeu a confirmer" in normalizedStatuses ->
            ""
        else ->
            cleanDisplayText(lines.firstOrNull().orEmpty())
    }
}

private fun competitionStakeReason(name: String, statuses: List<String>, lines: List<String>): String {
    val cleanName = cleanDisplayText(name)
    val normalizedStatuses = statuses.joinToString(" ").let(::normalizeForStructure)
    val evidence = competitionStakeEvidence(lines)
    val evidenceSuffix = evidence.takeIf { it.isNotBlank() }?.let { " Source détectée : $it" }.orEmpty()
    return when {
        "doit absolument gagner" in normalizedStatuses ->
            "$cleanName a un objectif de résultat qui doit se traduire dans les stats : volume offensif, tirs/attaques, buts ou points.$evidenceSuffix"
        "resultat tres important" in normalizedStatuses ->
            "$cleanName joue un résultat important ; le signal reste valable seulement si forme récente, absences et production suivent.$evidenceSuffix"
        "doit au minimum faire nul" in normalizedStatuses || "doit ne pas perdre" in normalizedStatuses ->
            "$cleanName peut atteindre son objectif sans gagner : privilégier données défensives, rythme bas et score serré.$evidenceSuffix"
        "doit defendre sa place" in normalizedStatuses ->
            "$cleanName protège une position : défense, discipline et gestion des temps faibles pèsent davantage.$evidenceSuffix"
        "deja qualifie" in normalizedStatuses ->
            "$cleanName a déjà validé une partie de son objectif : composition/startlist prioritaire avant toute projection joueur ou score exact.$evidenceSuffix"
        "deja elimine" in normalizedStatuses ->
            "$cleanName n’a plus d’objectif clair : ignorer le contexte si les stats récentes ne montrent pas de volume réel.$evidenceSuffix"
        "match sans enjeu majeur" in normalizedStatuses ->
            "$cleanName est dans un contexte moins lisible : ne garder que les signaux confirmés par production, absences et composition.$evidenceSuffix"
        "rotation possible" in normalizedStatuses ->
            "$cleanName peut préserver certains joueurs : minutes, titulaire/remplaçant et marché joueur deviennent fragiles.$evidenceSuffix"
        "enjeu a confirmer" in normalizedStatuses ->
            ""
        else ->
            evidenceSuffix.trim()
    }
}

private fun competitionStakeEvidence(lines: List<String>): String =
    lines.firstOrNull { line ->
        val text = normalizeForStructure(line)
        listOf(
            "classement",
            "groupe",
            "poule",
            "qualifie",
            "elimine",
            "gagner",
            "nul",
            "ne pas perdre",
            "rotation",
            "difference",
            "points",
            "prochain match",
        ).any { marker -> marker in text }
    }.orEmpty().let(::cleanDisplayText)

private fun competitionStakeForecastImpact(name: String, statuses: List<String>): List<String> {
    val cleanName = cleanDisplayText(name)
    val normalizedStatuses = statuses.joinToString(" ").let(::normalizeForStructure)
    val impacts = buildList {
        if ("doit absolument gagner" in normalizedStatuses) {
            add("$cleanName devrait augmenter le volume : tirs/attaques plus hauts et espaces possibles derrière.")
            add("Risque inverse : plus d’espaces laissés derrière, donc scénario adverse ou buts/points des deux côtés à regarder.")
        }
        if ("resultat tres important" in normalizedStatuses) {
            add("$cleanName a un résultat important : comparer forme récente, production offensive/défensive et absences avant de renforcer le signal.")
        }
        if ("doit au minimum faire nul" in normalizedStatuses || "doit ne pas perdre" in normalizedStatuses) {
            add("$cleanName peut fermer le rythme : score serré, moins d’espaces et handicap agressif moins fiable.")
        }
        if ("doit defendre sa place" in normalizedStatuses) {
            add("$cleanName peut gérer le score ou protéger son classement : avantage aux marchés de solidité si les stats défensives suivent.")
        }
        if ("deja qualifie" in normalizedStatuses || "rotation possible" in normalizedStatuses) {
            add("$cleanName peut changer ses titulaires : marchés joueurs, buteurs/essais et score exact deviennent plus fragiles.")
        }
        if ("deja elimine" in normalizedStatuses || "match sans enjeu majeur" in normalizedStatuses) {
            add("$cleanName rend le signal plus fragile : confiance à baisser sauf preuve récente de volume, titulaires solides et implication statistique.")
        }
        if ("difference" in normalizedStatuses || "marquer beaucoup" in normalizedStatuses) {
            add("$cleanName peut chercher un écart ou du volume : over, tirs/attaques, essais/points ou domination à comparer aux stats récentes.")
        }
        if ("cartons" in normalizedStatuses || "blessures" in normalizedStatuses) {
            add("$cleanName peut protéger des titulaires : temps de jeu et intensité baissent si carton/blessure pèse.")
        }
        if ("prepare un match plus important" in normalizedStatuses) {
            add("$cleanName peut économiser des forces : rotation probable, tempo plus bas et gros écart moins fiable.")
        }
    }
    return impacts.distinct()
}

private fun PredictionEntity.participantAliases(): List<ParticipantAlias> {
    val sport = sportKey.substringBefore('/')
    return if (sport in resultBoardSports) {
        listOf(
            ParticipantAlias(
                displayName = cleanDisplayText(homeTeam.ifBlank { selection }),
                aliases = aliasesFor(homeTeam, selection, competitionName),
            )
        )
    } else {
        listOf(
            ParticipantAlias(displayTeamName(homeTeam), aliasesFor(homeTeam, displayTeamName(homeTeam))),
            ParticipantAlias(displayTeamName(awayTeam), aliasesFor(awayTeam, displayTeamName(awayTeam))),
        ).filter { it.displayName.isNotBlank() }
    }
}

private fun participantStatRequirementBlocks(
    participants: List<ParticipantAlias>,
    rawLines: List<String>,
    expectedStats: List<String>,
): List<StructuredParticipantStatRequirementBlock> =
    participants.mapNotNull { participant ->
        val participantStats = rawLines.filter { line -> participant.matches(line) }
        val missingStats = expectedStats
            .filterNot { expected -> participantStats.any { line -> line.coversExpectedStat(expected) } }
            .map(::cleanDisplayText)
            .take(9)
        if (missingStats.isEmpty()) return@mapNotNull null
        StructuredParticipantStatRequirementBlock(
            name = participant.displayName,
            missingStats = missingStats,
        )
    }

private fun participantFormTrendBlocks(
    participants: List<ParticipantAlias>,
    formLines: List<String>,
    rawLines: List<String>,
    importantLines: List<String>,
    competitionLines: List<String>,
): List<StructuredFormTrendBlock> =
    participants.map { participant ->
        val explicitTrends = formLines.filter { line -> participant.matches(line) }.distinct()
        val participantStats = rawLines.filter { line -> participant.matches(line) }.distinct()
        val participantImportant = importantLines.filter { line -> participant.matches(line) }.distinct()
        val participantCompetition = competitionLines.filter { line -> participant.matches(line) }.distinct()
        StructuredFormTrendBlock(
            name = participant.displayName,
            trends = (
                explicitTrends.take(4) +
                    estimatedFormTrendLines(
                        name = participant.displayName,
                        explicitTrends = explicitTrends,
                        stats = participantStats,
                        important = participantImportant,
                        competition = participantCompetition,
                    )
                ).distinctBy(::normalizeForStructure),
        )
    }

private fun estimatedFormTrendLines(
    name: String,
    explicitTrends: List<String>,
    stats: List<String>,
    important: List<String>,
    competition: List<String>,
): List<String> {
    val cleanName = cleanDisplayText(name)
    val evidenceLines = (explicitTrends + stats + important + competition).map(::cleanDisplayText).filter { it.isNotBlank() }
    val text = normalizeForStructure(evidenceLines.joinToString(" "))
    val direction = when {
        formPositiveMarkers.any { it in text } && formNegativeMarkers.any { it in text } -> "irrégulière"
        formPositiveMarkers.any { it in text } -> "en hausse"
        formNegativeMarkers.any { it in text } -> "en baisse / sous vigilance"
        evidenceLines.isNotEmpty() -> "stable à confirmer"
        else -> "non déterminée"
    }
    val reason = when {
        evidenceLines.isNotEmpty() -> evidenceLines.first()
        else -> "Aucune série 3/5/10 derniers matchs assez claire n’est encore isolée pour $cleanName."
    }
    return listOf(
        "Tendance estimée : $direction.",
        "Pourquoi : $reason",
    )
}

private fun String.coversExpectedStat(expectedStat: String): Boolean {
    val line = normalizeForStructure(this)
    val expected = normalizeForStructure(expectedStat)
    if (expected.isBlank()) return false
    val expectedWords = expected
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 && it !in weakStatWords }
    return expected in line || expectedWords.any { word -> word in line }
}

private fun PredictionEntity.participantProbabilityHintBlocks(
    participants: List<ParticipantAlias>,
): List<StructuredParticipantScenarioHintBlock> =
    participants.mapNotNull { participant ->
        val hints = participantProbabilityHintTemplates(sportKey, participant.displayName)
        if (hints.isEmpty()) return@mapNotNull null
        StructuredParticipantScenarioHintBlock(
            name = participant.displayName,
            hints = hints.distinctBy { it.type + it.label },
        )
    }

private fun participantProbabilityHintTemplates(sportKey: String, participantName: String): List<StructuredSportScenarioHint> {
    val sport = sportKey.substringBefore('/')
    val name = cleanDisplayText(participantName)
    fun hint(type: String, label: String, priority: Double) = StructuredSportScenarioHint(
        type = cleanDisplayText(type),
        label = cleanDisplayText(label),
        priority = priority,
    )
    return when (sport) {
        "soccer" -> listOf(
            hint("Équipe · Buts", "$name marque au moins 1 but à recalculer avec xG, tirs cadrés et composition.", 0.66),
            hint("Équipe · Buts", "$name marque 2+ buts seulement si volume offensif et contexte concordent.", 0.54),
            hint("Équipe · Stats de match", "$name tirs cadrés/corners à vérifier avec domination territoriale et style.", 0.58),
            hint("Équipe · Défense", "$name clean sheet ou encaisse au moins 1 but selon forme défensive et absences.", 0.55),
            hint("Équipe · Discipline", "$name carton à surveiller selon arbitre, historique fautes et score du match.", 0.49),
        )
        "rugby" -> listOf(
            hint("Équipe · Essais", "$name 1+ essai à recalculer avec occupation, mètres gagnés et composition.", 0.64),
            hint("Équipe · Essais", "$name 2+ essais si conquête, météo et forme offensive concordent.", 0.55),
            hint("Équipe · Points", "$name total points équipe à recalculer avec buteur, pénalités et transformations.", 0.63),
            hint("Équipe · Conquête", "$name touches/mêlées à surveiller pour estimer la domination territoriale.", 0.57),
            hint("Équipe · Discipline", "$name carton ou pénalités concédées à surveiller selon arbitre et historique.", 0.50),
        )
        "basketball" -> listOf(
            hint("Équipe · Points", "$name total points équipe à recalculer avec pace, adresse et rotations.", 0.66),
            hint("Équipe · Quart-temps", "$name gagne un quart-temps à surveiller selon rythme et banc.", 0.56),
            hint("Équipe · Rebonds", "$name rebonds à recalculer avec taille, matchup et rythme.", 0.57),
            hint("Équipe · Adresse", "$name 3 points/pertes de balle à surveiller avec défense adverse et rythme.", 0.54),
            hint("Équipe · Handicap", "$name handicap points à valider après absences et minutes limitées.", 0.60),
        )
        "tennis" -> listOf(
            hint("Participant · Set", "$name gagne le premier set à recalculer avec surface, service et entrée de match.", 0.58),
            hint("Participant · Service", "$name aces/doubles fautes à surveiller selon surface, vent et état physique.", 0.55),
            hint("Participant · Retour", "$name breaks obtenus/concédés à recalculer avec seconde balle et qualité retour.", 0.56),
            hint("Participant · Jeux", "$name handicap jeux à valider après fatigue et historique de surface.", 0.53),
            hint("Participant · Format", "$name score en sets à recalculer avec forme récente et temps passé sur le court.", 0.54),
        )
        "cycling" -> listOf(
            hint("Participant · Classement", "$name top 10/podium à éviter sans startlist, favoris et rôle confirmé.", 0.56),
            hint("Participant · Duel", "$name duel coureur/équipe à recalculer après favoris recoupés.", 0.55),
            hint("Participant · Profil", "$name avantage sprint/montagne/chrono selon parcours et météo.", 0.58),
            hint("Participant · Course", "$name échappée ou contrôle peloton à surveiller selon rôle d’équipe.", 0.54),
            hint("Participant · Risque", "$name chute/abandon à intégrer avec météo, fatigue et parcours technique.", 0.52),
        )
        "racing", "nascar" -> listOf(
            hint("Pilote · Classement", "$name podium/top 10 à recalculer après qualifications et rythme long run.", 0.60),
            hint("Pilote · Duel", "$name devant coéquipier/rival à surveiller avec rythme et grille.", 0.56),
            hint("Pilote · Course", "$name gain/perte de places à recalculer avec stratégie pneus et trafic.", 0.55),
            hint("Pilote · Risque", "$name abandon/incident à intégrer avec fiabilité, météo et safety car.", 0.52),
            hint("Pilote · Stratégie", "$name arrêt au stand/undercut à surveiller après grille et pneus.", 0.54),
        )
        "golf" -> listOf(
            hint("Participant · Classement", "$name top 5/top 10/top 20 à recalculer avec field et forme parcours.", 0.58),
            hint("Participant · Cut", "$name passe le cut à surveiller avec régularité tee-to-green.", 0.62),
            hint("Participant · Score", "$name score sous le par à recalculer avec météo et putting.", 0.55),
            hint("Participant · Duel", "$name duel joueur à valider avec strokes gained et historique parcours.", 0.54),
            hint("Participant · Risque", "$name bogeys/vent à intégrer avant top classement.", 0.50),
        )
        "baseball" -> listOf(
            hint("Équipe · Runs", "$name runs équipe à recalculer avec lanceur partant et bullpen.", 0.63),
            hint("Équipe · Handicap", "$name run line à vérifier avec starter, bullpen et stade.", 0.58),
            hint("Équipe · Batting", "$name hits/home runs à surveiller avec météo et lineup.", 0.55),
            hint("Équipe · Pitching", "$name strikeouts concédés/obtenus selon matchup lanceur-lineup.", 0.56),
            hint("Équipe · Late game", "$name risque fin de match si bullpen fatigué.", 0.53),
        )
        "hockey" -> listOf(
            hint("Équipe · Buts", "$name marque/encaisse à recalculer après gardien titulaire.", 0.62),
            hint("Équipe · Tirs", "$name tirs à surveiller avec volume offensif récent.", 0.57),
            hint("Équipe · Special teams", "$name power play/penalty kill à intégrer avec discipline adverse.", 0.56),
            hint("Équipe · Écart", "$name match à 1 but d’écart si gardiens et niveaux proches.", 0.55),
            hint("Équipe · Fatigue", "$name back-to-back/repos à intégrer avant total buts.", 0.53),
        )
        else -> listOf(
            hint("Participant · Résultat", "$name résultat/issue à recalculer avec forme, classement et contexte.", 0.56),
            hint("Participant · Performance", "$name performance propre au sport à valider avec statistiques récentes.", 0.55),
            hint("Participant · Discipline", "$name discipline/risque à surveiller selon historique et enjeu.", 0.50),
            hint("Participant · Fatigue", "$name fatigue/charge récente à comparer à la moyenne saison.", 0.54),
            hint("Participant · Fiabilité", "$name signal fort seulement si plusieurs sources publiques concordent.", 0.70),
        )
    }
}

private data class ParticipantAlias(
    val displayName: String,
    val aliases: List<String>,
) {
    fun matches(value: String): Boolean {
        val normalized = normalizeForStructure(value)
        return aliases.any { alias ->
            val cleanAlias = normalizeForStructure(alias)
            cleanAlias.length >= 3 && cleanAlias in normalized
        }
    }
}

private fun aliasesFor(vararg values: String): List<String> = values
    .flatMap { value ->
        listOf(
            cleanDisplayText(value),
            runCatching { displayTeamName(value) }.getOrDefault(cleanDisplayText(value)),
        )
    }
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinctBy(::normalizeForStructure)

private fun PredictionEntity.actorAliases(playerLines: List<StructuredProbabilityLine>): List<ActorAlias> {
    val fromLineups = parseActorLineup(homeTeam, homeLineupStatus, homeLineup) +
        parseActorLineup(awayTeam, awayLineupStatus, awayLineup)
    val knownNames = fromLineups.map { normalizeForStructure(it.displayName) }.toSet()
    val guessed = playerLines.mapNotNull { scenario ->
        val name = guessActorName(scenario.label)
        if (name.isBlank() || normalizeForStructure(name) in knownNames) return@mapNotNull null
        ActorAlias(
            displayName = name,
            aliases = aliasesFor(name),
            teamOrRole = "Joueur/participant à confirmer",
            situation = listOf("Statut : à confirmer dans les compositions officielles"),
        )
    }
    return (fromLineups + guessed).distinctBy { normalizeForStructure(it.displayName) }
}

private fun parseActorLineup(team: String, status: String, rawLineup: String): List<ActorAlias> =
    rawLineup.lines().mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val parts = line.split('|')
        val name = when {
            parts.size >= 3 -> parts[1]
            parts.size == 2 && parts[0].startsWith("#") -> parts[1]
            parts.size == 2 -> parts[0]
            else -> parts.firstOrNull().orEmpty()
        }.trim()
        if (name.isBlank()) return@mapNotNull null
        val situation = buildList {
            add("Composition : ${status.ifBlank { "à confirmer" }}")
        }
        ActorAlias(
            displayName = cleanDisplayText(name),
            aliases = aliasesFor(name),
            teamOrRole = displayTeamName(team),
            situation = situation,
        )
    }

private fun actorStatRequirementBlocks(
    actors: List<ActorAlias>,
    statLines: List<String>,
    expectedStats: List<String>,
): List<StructuredActorStatRequirementBlock> =
    actors.mapNotNull { actor ->
        val actorStats = statLines.filter { line -> actor.matches(line) }
        val missingStats = expectedStats
            .filterNot { expected -> actorStats.any { line -> line.coversExpectedStat(expected) } }
            .map(::cleanDisplayText)
            .take(8)
        if (missingStats.isEmpty()) return@mapNotNull null
        StructuredActorStatRequirementBlock(
            name = actor.displayName,
            teamOrRole = actor.teamOrRole,
            missingStats = missingStats,
        )
    }

private data class ActorAlias(
    val displayName: String,
    val aliases: List<String>,
    val teamOrRole: String,
    val situation: List<String>,
) {
    fun matches(value: String): Boolean {
        val normalized = normalizeForStructure(value)
        return aliases.any { alias ->
            val cleanAlias = normalizeForStructure(alias)
            cleanAlias.length >= 3 && cleanAlias in normalized
        }
    }
}

private fun guessActorName(label: String): String {
    val clean = cleanDisplayText(label).substringBefore(':').trim()
    val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return ""
    val stopIndex = words.indexOfFirst { word -> normalizeForStructure(word) in actorStopWords }
    return words.take(if (stopIndex > 0) stopIndex else words.take(2).size)
        .joinToString(" ")
        .trim()
}

private fun String.toStructuredProbabilities(): List<StructuredProbabilityLine> = lines().mapNotNull { line ->
    val parts = line.split('|', limit = 3)
    if (parts.size != 3) return@mapNotNull null
    val probability = parts[2].toDoubleOrNull() ?: return@mapNotNull null
    StructuredProbabilityLine(
        type = cleanDisplayText(parts[0]),
        label = cleanDisplayText(parts[1]),
        probability = probability,
    )
}

private fun String.cleanLines(): List<String> = lines()
    .map { cleanDisplayText(it).trim() }
    .filter { it.isNotBlank() }

private fun String.withReliability(sourceAgreement: Int, confidenceScore: Int): StructuredReliabilityLine =
    StructuredReliabilityLine(
        text = cleanDisplayText(this),
        level = reliabilityLevel(this, sourceAgreement, confidenceScore),
        category = importantInfoCategory(this),
    )

private fun importantInfoCategory(value: String): String {
    val text = normalizeForStructure(value)
    return when {
        listOf("source principale", "accord des sources", "fiabilite des donnees", "espn", "thesportsdb", "source").any { it in text } ->
            "Source / fiabilité"
        listOf("coach", "entraineur", "vestiaire", "crise interne", "pression mediatique").any { it in text } ->
            "Coach / vestiaire"
        listOf("blesse", "blessure", "incertain", "retour de blessure", "malade", "pas dans le groupe", "absent", "absence").any { it in text } ->
            "Infirmerie / disponibilité"
        listOf("suspendu", "suspension", "carton precedent", "risque suspension", "carton").any { it in text } ->
            "Discipline / suspension"
        listOf("rotation", "faire tourner", "composition", "lineup", "titulaire", "remplacant", "feuille de match", "startlist", "groupe").any { it in text } ->
            "Rotation / composition"
        listOf("calendrier", "deplacement", "match important a venir", "prochain match", "temps de jeu trop eleve", "charge").any { it in text } ->
            "Calendrier / charge"
        listOf("meteo", "pluie", "vent", "terrain", "pelouse", "huis clos", "conditions").any { it in text } ->
            "Météo / terrain"
        listOf("domicile", "exterieur", "recoit", "avantage domicile", "voyage").any { it in text } ->
            "Domicile / déplacement"
        listOf("interview", "conference de presse", "communique", "officiel", "club", "federation").any { it in text } ->
            "Communiqué / presse officielle"
        listOf("fatigue", "forme mentale", "probleme personnel", "mental").any { it in text } ->
            "État mental / fatigue"
        else -> "Info complémentaire"
    }
}

private fun reliabilityLevel(value: String, sourceAgreement: Int, confidenceScore: Int): String {
    val text = normalizeForStructure(value)
    return when {
        listOf("rumeur", "rumour", "bruit", "non verifie").any { it in text } -> "Rumeur faible"
        listOf("non confirme", "a confirmer", "incertain", "douteux").any { it in text } -> "Incertain"
        listOf("officiel", "communique", "club", "federation", "conference de presse", "composition officielle", "confirme").any { it in text } -> "Confirmé officiel"
        sourceAgreement >= 78 || confidenceScore >= 82 || listOf("recoupe", "plusieurs sources", "espn", "thesportsdb", "stats publiques").any { it in text } -> "Très probable"
        confidenceScore < 50 || sourceAgreement in 1..49 -> "Incertain"
        else -> "Très probable"
    }
}

private fun sourceCoverageLines(
    sourceName: String,
    sourceLines: List<String>,
    signalLines: List<String>,
): List<StructuredSourceCoverageLine> {
    val text = normalizeForStructure((listOf(sourceName) + sourceLines + signalLines).joinToString(" "))
    fun coveredBy(vararg keywords: String): Boolean = keywords.any { keyword -> normalizeForStructure(keyword) in text }
    fun line(family: String, covered: Boolean, coveredDetail: String, missingDetail: String): StructuredSourceCoverageLine =
        StructuredSourceCoverageLine(
            family = family,
            status = if (covered) "Couvert" else "À recouper",
            detail = if (covered) coveredDetail else missingDetail,
        )
    return listOf(
        line(
            family = "Sites officiels / fédérations / clubs",
            covered = coveredBy("officiel", "club", "fédération", "federation", "communiqué", "communique", "ligue", "FIFA", "UEFA", "UCI"),
            coveredDetail = "Au moins une source institutionnelle ou officielle est repérée.",
            missingDetail = "À vérifier via site officiel, fédération, club, communiqué ou organisateur.",
        ),
        line(
            family = "Calendrier / compétition",
            covered = coveredBy("calendrier", "date détectée", "date detectee", "commence", "tournoi", "championnat", "competition", "compétition", "ESPN", "TheSportsDB"),
            coveredDetail = "Le calendrier ou la compétition apparaît dans les sources/données.",
            missingDetail = "À confirmer via calendrier officiel, organisateur ou base événementielle fiable.",
        ),
        line(
            family = "Compositions / feuilles de match / startlist",
            covered = coveredBy("composition", "lineup", "feuille de match", "titulaire", "groupe", "startlist", "grille", "qualifications"),
            coveredDetail = "Des éléments de composition, groupe, grille ou startlist sont disponibles ou surveillés.",
            missingDetail = "À recouper dès publication des compositions, feuilles de match, grilles ou startlists.",
        ),
        line(
            family = "Statistiques publiques",
            covered = coveredBy("stats", "statistiques", "ESPN", "TheSportsDB", "classement", "moyenne", "boxscore", "xG", "strokes gained"),
            coveredDetail = "Des statistiques publiques ou bases de données sportives sont utilisées.",
            missingDetail = "À compléter avec statistiques publiques récentes, historiques et données propres au sport.",
        ),
        line(
            family = "Presse / conférence / interview",
            covered = coveredBy("presse", "interview", "conférence", "conference", "article", "média", "media"),
            coveredDetail = "Une source presse, conférence ou interview est mentionnée.",
            missingDetail = "À recouper via conférence de presse, interviews, articles fiables ou déclarations d’avant-match.",
        ),
        line(
            family = "Réseaux sociaux officiels",
            covered = coveredBy("instagram", "facebook", "x.com", "twitter", "réseaux", "reseaux", "social"),
            coveredDetail = "Un canal social officiel ou réseau social est mentionné.",
            missingDetail = "À vérifier via comptes officiels Instagram, Facebook ou X/Twitter.",
        ),
        line(
            family = "Météo / conditions",
            covered = coveredBy("météo", "meteo", "vent", "pluie", "terrain", "surface", "conditions", "parcours", "circuit", "pneus"),
            coveredDetail = "Les conditions météo, terrain, surface, parcours ou matériel sont prises en compte.",
            missingDetail = "À compléter avec météo, terrain/surface, parcours/circuit ou conditions matérielles.",
        ),
    )
}

private fun competitionImpactLines(lines: List<String>): List<String> {
    val impacts = lines.flatMap { line ->
        val text = normalizeForStructure(line)
        val subject = line.substringBefore(':').trim().takeIf { ':' in line && it.length in 2..40 }
        val prefix = subject?.let { "${cleanDisplayText(it)} : " }.orEmpty()
        buildList {
            if ("doit absolument gagner" in text || ("doit" in text && "gagner" in text) || "besoin de victoire" in text) {
                add("${prefix}obligation de gagner → prise de risque, volume offensif/tirs plus élevé et espaces possibles derrière.")
            }
            if ("doit au minimum faire nul" in text || "doit faire nul" in text || "nul suffit" in text || "ne pas perdre" in text) {
                add("${prefix}nul ou non-défaite utile → gestion plus prudente, double chance plus lisible que victoire sèche.")
            }
            if ("deja qualifie" in text || "deja qualifiee" in text) {
                add("${prefix}déjà qualifié → risque de gestion/rotation, intensité et fiabilité de la victoire à réduire.")
            }
            if ("deja elimine" in text || "deja eliminee" in text) {
                add("${prefix}déjà éliminé → signal réduit sauf si les derniers matchs confirment volume, titulaires et intensité réelle.")
            }
            if ("defendre" in text && ("premiere place" in text || "deuxieme place" in text || "classement" in text)) {
                add("${prefix}place à défendre → approche plus contrôlée, enjeu défensif et gestion des temps faibles importants.")
            }
            if ("difference de buts" in text || "marquer beaucoup" in text || "soigner la difference" in text) {
                add("${prefix}besoin de différence/score → tendance favorable aux tirs, corners et scénarios avec buts.")
            }
            if ("rotation" in text || "faire tourner" in text || "prochain match" in text) {
                add("${prefix}rotation possible → titulaires incertains, baisse de fiabilité sur joueur titulaire et score exact.")
            }
            if ("carton" in text || "blessure" in text || "suspension" in text) {
                add("${prefix}discipline/santé à gérer → risque d’intensité réduite ou de joueurs protégés.")
            }
        }
    }.distinct()
    return impacts
}

private fun StructuredProbabilityLine.mentionsAny(values: List<String>): Boolean {
    val text = normalizeForStructure("$label $type")
    return values
        .map(::normalizeForStructure)
        .filter { it.length >= 3 }
        .any { it in text }
}

private fun StructuredProbabilityLine.isParticipantProbabilityType(): Boolean = type.isParticipantProbabilityType()

private fun String.isParticipantProbabilityType(): Boolean {
    val value = normalizeForStructure(this)
    return listOf("equipe", "participant", "joueur", "pilote", "clean sheet", "domicile", "exterieur").any { it in value }
}

private fun isSportChecklistLine(value: String): Boolean {
    val text = normalizeForStructure(value)
    return text.startsWith("stats cles surveillees") ||
        text.startsWith("stats joueurs suivies") ||
        text.startsWith("contexte a recouper")
}

private fun isCompetitionSituationLine(value: String): Boolean {
    val text = normalizeForStructure(value)
    return competitionKeywords.any { it in text }
}

private fun isFormTrendLine(value: String): Boolean {
    val text = normalizeForStructure(value)
    return formKeywords.any { it in text }
}

private fun isImportantInfoLine(value: String): Boolean {
    val text = normalizeForStructure(value)
    return importantKeywords.any { it in text }
}

private fun scoreProbability(scenarios: List<StructuredProbabilityLine>): Double =
    scenarios.firstOrNull { normalizeForStructure(it.type).contains("score") }?.probability ?: 0.50

private fun PredictionEntity.finalExplanationBlocks(
    baseExplanation: List<String>,
    positiveLines: List<String>,
    negativeLines: List<String>,
    competitionImpact: List<String>,
    globalProbabilities: List<StructuredProbabilityLine>,
    participantProbabilities: List<StructuredProbabilityLine>,
    playerProbabilities: List<StructuredProbabilityLine>,
): List<StructuredExplanationBlock> = buildList {
    val mainLines = buildList {
        add("Pronostic principal : ${cleanDisplayText(selection)} à ${probabilityPercentLabel(consensusProbability)}.")
        if (expectedScore.isNotBlank()) {
            add("Score / état le plus lisible : ${cleanDisplayText(expectedScore)}.")
        }
        addAll(baseExplanation.take(1))
    }.distinct()
    add(StructuredExplanationBlock("Lecture principale", mainLines))

    val topGlobal = globalProbabilities
        .filterNot { normalizeForStructure(it.type) == "signal principal" }
        .sortedByDescending { it.probability }
        .take(4)
        .map { "${cleanDisplayText(it.label)} (${cleanDisplayText(it.type)}) : ${probabilityPercentLabel(it.probability)}." }
    if (topGlobal.isNotEmpty()) {
        add(StructuredExplanationBlock("Pourquoi ce scénario ressort", topGlobal))
    }

    if (positiveLines.isNotEmpty()) {
        add(StructuredExplanationBlock("Ce qui augmente la probabilité", positiveLines.take(5).map(::cleanDisplayText)))
    }
    if (negativeLines.isNotEmpty()) {
        add(StructuredExplanationBlock("Ce qui baisse la probabilité / vigilance", negativeLines.take(5).map(::cleanDisplayText)))
    }
    if (competitionImpact.isNotEmpty()) {
        add(StructuredExplanationBlock("Pourquoi le contexte change le pronostic", competitionImpact.take(5).map(::cleanDisplayText)))
    }

    val participantHighlights = participantProbabilities
        .sortedByDescending { it.probability }
        .take(3)
        .map { "${cleanDisplayText(it.label)} : ${probabilityPercentLabel(it.probability)}." }
    if (participantHighlights.isNotEmpty()) {
        add(StructuredExplanationBlock("Participants à surveiller", participantHighlights))
    }

    val playerHighlights = playerProbabilities
        .sortedByDescending { it.probability }
        .take(3)
        .map { "${cleanDisplayText(it.label)} : ${probabilityPercentLabel(it.probability)}." }
    if (playerHighlights.isNotEmpty()) {
        add(StructuredExplanationBlock("Joueurs / pilotes / acteurs clés", playerHighlights))
    }

    add(
        StructuredExplanationBlock(
            title = "Confiance de l’analyse",
            lines = buildList {
                add("Fiabilité des données : $confidenceScore/100 ; ce score mesure la solidité des infos, pas une probabilité de victoire.")
                if (sourceAgreement > 0) add("Accord des sources/statistiques : $sourceAgreement/100.")
                add("Incertitude affichée : ${cleanDisplayText(riskLevel)}.")
                add(confidenceReading(confidenceScore, sourceAgreement, riskLevel))
            }.distinct(),
        )
    )
}.filter { it.lines.isNotEmpty() }

private fun confidenceReading(confidenceScore: Int, sourceAgreement: Int, riskLevel: String): String {
    val risk = normalizeForStructure(riskLevel)
    return when {
        confidenceScore >= 82 && sourceAgreement >= 78 && "eleve" !in risk ->
            "Lecture confiance : signal solide, car les données et les sources vont globalement dans le même sens."
        confidenceScore < 55 || sourceAgreement in 1..54 || "eleve" in risk ->
            "Lecture confiance : prudence, car une partie des données reste fragile, contradictoire ou trop incertaine."
        else ->
            "Lecture confiance : niveau moyen, exploitable mais à recalculer si composition, météo, blessure ou info récente change."
    }
}

private fun probabilityPercentLabel(value: Double): String =
    "${(value.coerceIn(0.0, 1.0) * 100).toInt()} %"

private fun List<StructuredProbabilityLine>.toProbabilityGroups(): List<StructuredProbabilityGroup> {
    val order = listOf(
        "Résultat & score",
        "Totaux",
        "Stats de match",
        "Discipline & contexte",
        "Course / podium",
        "Autres scénarios",
    )
    return groupBy { it.probabilityGroupTitle() }
        .map { (title, probabilities) ->
            StructuredProbabilityGroup(
                title = title,
                probabilities = probabilities.distinctBy { probability -> probability.type + probability.label },
            )
        }
        .sortedBy { group ->
            order.indexOf(group.title).takeIf { it >= 0 } ?: order.size
        }
}

private fun globalMarketRequirementLines(
    sportKey: String,
    probabilities: List<StructuredProbabilityLine>,
): List<StructuredGlobalMarketRequirement> {
    val sport = sportKey.substringBefore('/')
    val text = normalizeForStructure(probabilities.joinToString(" ") { "${it.type} ${it.label}" })
    fun coveredBy(vararg markers: String): Boolean = markers.any { marker -> normalizeForStructure(marker) in text }
    fun req(
        family: String,
        markets: String,
        covered: Boolean,
        coveredDetail: String,
        missingDetail: String,
    ) = StructuredGlobalMarketRequirement(
        family = cleanDisplayText(family),
        markets = cleanDisplayText(markets),
        status = if (covered) "Couvert" else "À recouper",
        detail = cleanDisplayText(if (covered) coveredDetail else missingDetail),
    )
    fun resultScoreTotal(
        resultMarkets: String = "vainqueur / résultat principal",
        scoreMarkets: String = "score final ou état final probable",
        totalMarkets: String = "total points / buts / jeux / sets selon le sport",
    ) = listOf(
        req(
            "Résultat global",
            resultMarkets,
            coveredBy("signal principal", "resultat", "victoire", "vainqueur", "gagne"),
            "Une probabilité de résultat global existe déjà.",
            "Le PDF attend au minimum une lecture du résultat global avant les probabilités par participant.",
        ),
        req(
            "Score / état final",
            "$scoreMarkets + probabilité associée",
            coveredBy("score", "score / etat", "etat probable", "classement probable"),
            "Un score ou état final probable est affiché avec une probabilité.",
            "À compléter avec score final, score en sets, classement ou état final probable selon le sport.",
        ),
        req(
            "Totaux de l’événement",
            totalMarkets,
            coveredBy("total", "plus de", "moins de", "over", "under", "buts", "points", "jeux", "sets", "runs", "essais"),
            "Au moins un total global est disponible.",
            "À recouper avec les totaux attendus du sport avant de renforcer le signal.",
        ),
    )

    return when (sport) {
        "soccer" -> resultScoreTotal(
            resultMarkets = "victoire équipe A, nul, victoire équipe B",
            scoreMarkets = "score final probable",
            totalMarkets = "+0,5 / +1,5 / +2,5 / +3,5 buts et under",
        ) + listOf(
            req(
                "Double chance",
                "équipe A ou nul, équipe B ou nul",
                coveredBy("double chance", "ou nul"),
                "Une double chance est déjà présente.",
                "Le PDF attend les doubles chances en plus du 1N2.",
            ),
            req(
                "Les deux équipes marquent",
                "BTTS / les deux équipes marquent",
                coveredBy("deux equipes marquent", "btts"),
                "Le marché “les deux équipes marquent” est présent.",
                "À recouper avec buts marqués/encaissés et contexte compétition.",
            ),
            req(
                "Stats de match",
                "total tirs, tirs cadrés, corners",
                coveredBy("tir", "tirs", "cadre", "cadres", "corner", "corners"),
                "Des stats de match globales sont déjà probabilisées.",
                "Le PDF attend total tirs, tirs cadrés et corners du match total.",
            ),
            req(
                "Discipline / événements",
                "total cartons, penalty, carton rouge",
                coveredBy("carton", "rouge", "penalty"),
                "Une proba discipline/contexte est déjà présente.",
                "À compléter avec cartons, rouge ou penalty si les données existent.",
            ),
        )

        "tennis" -> resultScoreTotal(
            resultMarkets = "joueur A gagne, joueur B gagne",
            scoreMarkets = "score 2-0 / 2-1 ou 3-0 / 3-1 / 3-2",
            totalMarkets = "total jeux et nombre de sets",
        ) + listOf(
            req(
                "Tie-break",
                "tie-break oui/non",
                coveredBy("tie-break", "tiebreak"),
                "Le tie-break est déjà surveillé.",
                "À recouper avec service, surface et historique tie-break.",
            ),
        )

        "basketball" -> resultScoreTotal(
            resultMarkets = "victoire équipe A, victoire équipe B",
            scoreMarkets = "score final probable",
            totalMarkets = "total points",
        ) + listOf(
            req("Handicap / écart", "handicap et match serré", coveredBy("handicap", "ecart", "serre"), "Le handicap/écart est déjà surveillé.", "À recouper avec rythme, adresse et blessures."),
            req("Prolongation", "prolongation si possible", coveredBy("prolongation", "overtime"), "La prolongation est déjà surveillée.", "À recouper si match serré ou équipes proches."),
        )

        "rugby" -> resultScoreTotal(
            resultMarkets = "victoire, nul, double chance",
            scoreMarkets = "score final probable",
            totalMarkets = "total points",
        ) + listOf(
            req("Essais / points rugby", "nombre d’essais, pénalités, transformations", coveredBy("essai", "essais", "penalite", "penalites", "transformation"), "Les essais/points rugby sont déjà surveillés.", "Le PDF attend essais, pénalités et transformations."),
            req("Discipline rugby", "cartons", coveredBy("carton", "discipline"), "La discipline rugby est déjà surveillée.", "À recouper avec arbitre, météo et enjeu."),
        )

        "handball" -> resultScoreTotal("victoire, nul", "score final probable", "total buts") +
            listOf(req("Handicap / gardiens", "handicap, buts par équipe, arrêts gardien", coveredBy("handicap", "gardien", "arrets"), "Handicap/gardien déjà surveillé.", "À recouper avec efficacité au tir et arrêts gardien."))

        "hockey" -> resultScoreTotal("victoire temps réglementaire / prolongation comprise", "score final probable", "total buts") +
            listOf(req("Tirs / gardien", "tirs, but joueur, assistance, arrêts gardien", coveredBy("tir", "tirs", "gardien", "arrets", "assistance"), "Tirs/gardien déjà surveillés.", "À recouper avec gardien confirmé, power play et fatigue."))

        "baseball" -> resultScoreTotal("victoire", "score final probable", "total runs") +
            listOf(req("Actions baseball", "home run, hit, RBI, strikeouts, extra innings", coveredBy("home run", "hit", "rbi", "strikeout", "extra innings"), "Actions baseball déjà surveillées.", "À recouper avec lanceur partant, bullpen, météo et stade."))

        "football" -> resultScoreTotal("victoire", "score final probable", "total points") +
            listOf(req("Actions football", "touchdown, yards, interception, sack", coveredBy("touchdown", "yards", "interception", "sack"), "Actions spécifiques déjà surveillées.", "À recouper avec QB, météo, blessures et turnovers."))

        "volleyball" -> resultScoreTotal("victoire", "score en sets", "total points") +
            listOf(req("Handicap volley", "handicap sets / points, aces, contres", coveredBy("handicap", "ace", "aces", "contre", "contres", "bloc", "blocks"), "Handicap/aces/contres déjà surveillés.", "À recouper avec réception, erreurs et fatigue."))

        "mma", "boxing" -> listOf(
            req("Issue du combat", "victoire, KO/TKO, soumission, décision", coveredBy("victoire", "ko", "tko", "soumission", "decision"), "L’issue du combat est déjà surveillée.", "Le PDF attend méthode de victoire et décision/finish."),
            req("Durée du combat", "nombre de rounds, +1,5 / +2,5 rounds", coveredBy("round", "rounds", "+1,5", "+2,5"), "La durée du combat est déjà surveillée.", "À recouper avec cardio, style et historique des finishes."),
        )

        "racing" -> listOf(
            req("Course F1 globale", "vainqueur course, pole, meilleur tour", coveredBy("vainqueur", "course", "pole", "meilleur tour"), "Le scénario course/pole est déjà surveillé.", "Le PDF attend vainqueur, pole et meilleur tour."),
            req("Contexte F1", "safety car, pluie, abandon", coveredBy("safety car", "pluie", "abandon"), "Le contexte F1 est déjà surveillé.", "À recouper avec météo, pneus, fiabilité et circuit."),
        )

        "nascar" -> listOf(
            req("Course NASCAR", "victoire, podium, top 5, top 10, pole", coveredBy("victoire", "podium", "top 5", "top 10", "pole"), "Les marchés course NASCAR sont déjà surveillés.", "À recouper avec piste, qualif, stratégie et incidents."),
            req("Incidents NASCAR", "crash, abandon, safety car", coveredBy("crash", "abandon", "safety car", "caution"), "Les incidents NASCAR sont déjà surveillés.", "À recouper avec piste, accidents récents et météo."),
        )

        "cycling" -> listOf(
            req("Classement course", "victoire, podium, top 5, top 10", coveredBy("victoire", "podium", "top 5", "top 10", "classement"), "Les marchés classement course sont déjà surveillés.", "Le PDF attend victoire/podium/top 5/top 10."),
            req("Scénario de course", "échappée, sprint massif, attaque réussie, maillot", coveredBy("echappee", "sprint", "attaque", "maillot"), "Un scénario de course est déjà surveillé.", "À recouper avec parcours, météo, rôles d’équipe et startlist."),
            req("Risque course", "abandon, devant rival", coveredBy("abandon", "rival", "devant"), "Le risque/duel course est déjà surveillé.", "À recouper avec fatigue, chute récente, météo et parcours technique."),
        )

        "golf" -> listOf(
            req("Classement golf", "victoire, top 5, top 10, top 20", coveredBy("victoire", "top 5", "top 10", "top 20"), "Les marchés classement golf sont déjà surveillés.", "À recouper avec field, parcours et forme récente."),
            req("Cut / score", "passe le cut, birdies, bogeys, score sous le par", coveredBy("cut", "birdie", "bogey", "par"), "Cut/score déjà surveillé.", "À recouper avec strokes gained, putting et météo."),
        )

        else -> resultScoreTotal() + listOf(
            req(
                "Marchés spécifiques au sport",
                "scénarios propres au sport : handicap, classement, discipline, incident ou performance",
                coveredBy("handicap", "classement", "discipline", "abandon", "podium", "top", "performance"),
                "Au moins un scénario spécifique est déjà surveillé.",
                "À compléter avec les marchés propres au sport quand les sources les renvoient.",
            ),
        )
    }.distinctBy { normalizeForStructure(it.family) }
}

private fun StructuredProbabilityLine.probabilityGroupTitle(): String {
    val text = normalizeForStructure("$type $label")
    return when {
        listOf(
            "tir",
            "tirs",
            "cadre",
            "cadres",
            "corners",
            "possession",
            "rebonds",
            "passes",
            "aces",
            "breaks",
            "wickets",
            "yards",
            "run rate",
        ).any { it in text } -> "Stats de match"
        listOf(
            "carton",
            "rouge",
            "penalty",
            "discipline",
            "blessure",
            "meteo",
            "rotation",
            "risque",
            "incertitude",
            "suspension",
        ).any { it in text } -> "Discipline & contexte"
        listOf(
            "podium",
            "top",
            "classement",
            "pole",
            "safety car",
            "abandon",
            "course",
            "qualification",
            "qualifications",
            "echappee",
            "maillot",
        ).any { it in text } -> "Course / podium"
        listOf(
            "signal principal",
            "resultat",
            "vainqueur",
            "victoire",
            "nul",
            "double chance",
            "score exact",
            "score final",
            "score probable",
            "score / etat",
            "gagne",
        ).any { it in text } -> "Résultat & score"
        listOf(
            "total",
            "plus de",
            "moins de",
            "over",
            "under",
            "buts",
            "points",
            "runs",
            "jeux",
            "sets",
            "frames",
            "legs",
            "essais",
        ).any { it in text } -> "Totaux"
        else -> "Autres scénarios"
    }
}

private fun normalizeForStructure(value: String): String = Normalizer.normalize(
    cleanDisplayText(value).lowercase(Locale.FRANCE),
    Normalizer.Form.NFD,
).replace(Regex("\\p{M}+"), "")

private val resultBoardSports = setOf("cycling", "racing", "nascar", "golf", "athletics")

private val weakStatWords = setOf(
    "avec",
    "dans",
    "pour",
    "selon",
    "forme",
    "recent",
    "recente",
    "saison",
    "match",
    "matchs",
    "info",
    "live",
    "officielle",
    "officiel",
    "stats",
    "cles",
    "surveillees",
)

private val competitionKeywords = listOf(
    "classement",
    "qualification",
    "qualifie",
    "elimine",
    "groupe",
    "poule",
    "championnat",
    "tournoi",
    "enjeu",
    "doit",
    "besoin",
    "points",
    "playoff",
    "pression",
    "motivation",
    "rotation",
    "premiere place",
    "deuxieme place",
)

private val formKeywords = listOf(
    "forme",
    "tendance",
    "hausse",
    "baisse",
    "stable",
    "derniers",
    "dernieres",
    "moyenne",
    "dynamique",
    "fatigue",
    "charge",
    "minutes",
    "confiance",
)

private val formPositiveMarkers = listOf(
    "tendance positive",
    "forme positive",
    "en hausse",
    "hausse",
    "progression",
    "progresse",
    "victoire",
    "victoires",
    "invaincu",
    "marque de plus en plus",
    "plus decisif",
    "confiance",
    "boost",
    "retour",
)

private val formNegativeMarkers = listOf(
    "tendance negative",
    "tendance irreguliere",
    "irreguliere",
    "en baisse",
    "baisse",
    "defaite",
    "defaites",
    "perd souvent",
    "encaisse plus",
    "subit beaucoup",
    "moins utilise",
    "moins de minutes",
    "marque moins",
    "fatigue",
    "charge",
    "blessure",
    "suspension",
    "rotation",
    "coup de mou",
)

private val importantKeywords = listOf(
    "blesse",
    "blessure",
    "suspend",
    "absence",
    "absent",
    "incertain",
    "retour",
    "coach",
    "entraineur",
    "meteo",
    "terrain",
    "domicile",
    "exterieur",
    "composition",
    "officielle",
    "probable",
    "calendrier",
    "deplacement",
    "carton",
    "interview",
    "communique",
    "conference",
    "crise",
    "vestiaire",
    "huis clos",
    "pression mediatique",
    "malade",
    "mental",
    "probleme personnel",
    "charge",
)

private val actorStopWords = setOf(
    "but",
    "buts",
    "passe",
    "passes",
    "decisive",
    "decisives",
    "point",
    "points",
    "essai",
    "essais",
    "tir",
    "tirs",
    "cadre",
    "cadres",
    "carton",
    "rebond",
    "rebonds",
    "ace",
    "aces",
    "strikeout",
    "strikeouts",
    "home",
    "run",
    "touchdown",
    "podium",
    "top",
    "victoire",
    "abandon",
    "pole",
)
