package com.soliano.betvalueanalyzer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.ui.t
import com.soliano.betvalueanalyzer.ui.components.StructuredActorBlock
import com.soliano.betvalueanalyzer.ui.components.StructuredActorStatRequirementBlock
import com.soliano.betvalueanalyzer.ui.components.StructuredAnalysisCache
import com.soliano.betvalueanalyzer.ui.components.StructuredAnalysisDossier
import com.soliano.betvalueanalyzer.ui.components.StructuredCompetitionStakeBlock
import com.soliano.betvalueanalyzer.ui.components.StructuredExplanationBlock
import com.soliano.betvalueanalyzer.ui.components.StructuredFormTrendBlock
import com.soliano.betvalueanalyzer.ui.components.StructuredGlobalMarketRequirement
import com.soliano.betvalueanalyzer.ui.components.StructuredParticipantBlock
import com.soliano.betvalueanalyzer.ui.components.StructuredParticipantScenarioHintBlock
import com.soliano.betvalueanalyzer.ui.components.StructuredParticipantStatRequirementBlock
import com.soliano.betvalueanalyzer.ui.components.StructuredProbabilityLine
import com.soliano.betvalueanalyzer.ui.components.StructuredReliabilityBlock
import com.soliano.betvalueanalyzer.ui.components.StructuredReliabilityLine
import com.soliano.betvalueanalyzer.ui.components.StructuredSituationBlock
import com.soliano.betvalueanalyzer.ui.components.StructuredSportScenarioHint
import com.soliano.betvalueanalyzer.ui.components.StructuredSourceCoverageLine
import com.soliano.betvalueanalyzer.ui.components.Tag
import com.soliano.betvalueanalyzer.ui.components.categoryColor
import com.soliano.betvalueanalyzer.ui.components.cleanDisplayText
import com.soliano.betvalueanalyzer.ui.components.displayPredictionTeams
import com.soliano.betvalueanalyzer.ui.components.displayTeamName
import com.soliano.betvalueanalyzer.ui.components.formatDate
import com.soliano.betvalueanalyzer.ui.components.formatPercent
import com.soliano.betvalueanalyzer.ui.components.predictionCategoryKey
import com.soliano.betvalueanalyzer.ui.components.predictionCategoryLabel
import com.soliano.betvalueanalyzer.ui.components.structuredAnalysisDossier
import com.soliano.betvalueanalyzer.ui.theme.Amber
import com.soliano.betvalueanalyzer.ui.theme.Blue
import com.soliano.betvalueanalyzer.ui.theme.Danger
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary
import com.soliano.betvalueanalyzer.ui.theme.Violet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

@Composable
fun PredictionDetailScreen(
    prediction: PredictionEntity,
    contentPadding: PaddingValues,
    language: String = "fr",
    onBack: () -> Unit,
) {
    val accent = categoryColor(prediction.category)
    val cloudAiReading = remember(prediction.aiAnalysis, prediction.aiDiagnostic) { cloudAiReadingOrNull(prediction) }
    val cloudRequestStatus = remember(prediction.aiDiagnostic) { cloudAiRequestStatus(prediction) }
    var dossier by remember(prediction.id) { mutableStateOf(StructuredAnalysisCache.get(prediction)) }
    LaunchedEffect(
        prediction.id,
        prediction.sourceLastUpdate,
        prediction.statSummary,
        prediction.contextInsights,
        prediction.scenarios,
        prediction.playerScenarios,
    ) {
        if (dossier == null) {
            dossier = withContext(Dispatchers.Default) { StructuredAnalysisCache.getOrBuild(prediction) }
        }
    }
    val tennisMode = remember(prediction.sportKey, prediction.sportTitle) { isTennisPrediction(prediction) }
    val participantDigests = remember(prediction, dossier, tennisMode) {
        if (tennisMode) tennisParticipantBilanDigests(prediction)
        else dossier?.let { participantBilanDigests(it).take(2) }.orEmpty()
    }
    val actorDigests = remember(prediction, dossier, tennisMode) {
        if (tennisMode) tennisActorBilanDigests(prediction, dossier)
        else dossier?.let { actorBilanDigests(it).take(6) }.orEmpty()
    }
    val pressDigests = remember(prediction, dossier, tennisMode) {
        if (tennisMode) tennisPressInfoDigests(prediction)
        else dossier?.let { pressInfoDigests(it).take(5) }.orEmpty()
    }
    val lineupTeams = remember(
        prediction.homeLineupStatus,
        prediction.homeLineup,
        prediction.awayLineupStatus,
        prediction.awayLineup,
    ) { lineupDisplayTeams(prediction) }
    val globalNotes = remember(dossier, tennisMode) {
        if (tennisMode) emptyList()
        else dossier?.let { matchImpactNotes(it, max = 3) }.orEmpty()
    }
    val otherScenarios = remember(prediction.selection, dossier) { dossier?.let { otherProbabilityLines(prediction, it) }.orEmpty() }
    val scenarioNotes = remember(prediction.selection, dossier) { dossier?.let { probabilityCommentLines(prediction, it) }.orEmpty() }
    val participantSectionTitle = if (tennisMode) {
        t(language, "Bilan des deux joueurs", "Two-player summary", "Resumen de los dos jugadores", "Bilanz beider Spieler")
    } else {
        t(language, "Bilan équipes / participants", "Teams / participants summary", "Resumen equipos / participantes", "Teams / Teilnehmer-Bilanz")
    }
    val actorSectionTitle = if (tennisMode) {
        t(language, "Pronostics joueurs", "Player predictions", "Pronósticos de jugadores", "Spielerprognosen")
    } else {
        t(language, "Joueurs clés", "Key players", "Jugadores clave", "Schlüsselspieler")
    }
    val pressSectionTitle = if (tennisMode) {
        t(language, "Infos joueurs récentes", "Recent player info", "Noticias recientes de jugadores", "Aktuelle Spielerinfos")
    } else {
        t(language, "Presse / infos récentes", "Press / recent news", "Prensa / noticias recientes", "Presse / aktuelle Infos")
    }

    LazyColumn(
        modifier = Modifier.padding(contentPadding).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, t(language, "Retour", "Back", "Atrás", "Zurück")) }
                Text(t(language, "ANALYSE IA", "AI ANALYSIS", "ANÁLISIS IA", "KI-ANALYSE"), style = MaterialTheme.typography.labelLarge, color = Mint)
                Spacer(Modifier.size(48.dp))
            }
        }

        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tag(predictionCategoryLabel(prediction.category), accent)
                            if (prediction.sourceName.contains("Cloud collaboratif", ignoreCase = true)) Tag("Cloud", Violet)
                        }
                        Text(formatDate(prediction.commenceTime), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(cleanDisplayText("${prediction.sportTitle} · ${prediction.competitionName}"), color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(displayPredictionTeams(prediction), style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    PronosticExpressCard(
                        prediction = prediction,
                        accent = accent,
                    )
                }
            }
        }

        item {
            val cloudSectionTitle = when {
                cloudAiReading != null -> "Analyse IA disponible"
                cloudRequestStatus?.first == "requested" -> "Analyse IA demandee"
                else -> "Diagnostic IA cloud"
            }
            PredictionSection(cloudSectionTitle, Icons.Outlined.Info, accent) {
                if (cloudAiReading != null) {
                    CloudAiReadingCard(cloudAiReading, accent)
                } else {
                    CloudAiPendingCard(prediction, accent, cloudAiUnavailableReason(prediction))
                }
            }
        }

        item {
            PredictionSection(participantSectionTitle, Icons.Outlined.Groups, Blue) {
                if (!tennisMode && dossier == null) {
                    Text(t(language, "Préparation du bilan…", "Preparing summary…", "Preparando resumen…", "Bilanz wird vorbereitet…"), color = TextSecondary)
                } else if (participantDigests.isEmpty()) {
                    Text(t(language, "Bilan indisponible sur les données actuelles.", "Summary unavailable with current data.", "Resumen no disponible con los datos actuales.", "Bilanz mit aktuellen Daten nicht verfügbar."), color = TextSecondary)
                } else {
                    participantDigests.forEach { digest -> ParticipantDigestCard(digest, Blue) }
                    if (globalNotes.isNotEmpty()) {
                        Text(t(language, "À retenir", "Key takeaways", "A retener", "Wichtig"), style = MaterialTheme.typography.titleMedium, color = Amber)
                        globalNotes.forEach { Text("• $it", color = TextSecondary) }
                    }
                }
            }
        }

        item {
            PredictionSection(pressSectionTitle, Icons.Outlined.Newspaper, Amber) {
                if (!tennisMode && dossier == null) {
                    Text(t(language, "Recherche blessures, suspensions, retours, coach, météo et fatigue récente…", "Searching injuries, suspensions, returns, coach, weather and recent fatigue…", "Buscando lesiones, sanciones, regresos, entrenador, clima y fatiga reciente…", "Suche Verletzungen, Sperren, Rückkehrer, Trainer, Wetter und Müdigkeit…"), color = TextSecondary)
                } else if (pressDigests.isEmpty()) {
                    Text(t(language, NO_RECENT_FACT_LINE, "No fact found", "Ningún hecho detectado", "Keine Tatsache gefunden"), color = TextSecondary)
                } else {
                    pressDigests.forEach { digest -> PressInfoCard(digest, Amber) }
                }
            }
        }

        if (lineupTeams.isNotEmpty()) {
            item {
                PredictionSection(t(language, "Compositions terrain", "Lineups on field", "Alineaciones en campo", "Aufstellungen auf dem Feld"), Icons.Outlined.Groups, Mint) {
                    Text(t(language, "Titulaires détectés, formation et lecture tactique.", "Detected starters, formation and tactical reading.", "Titulares detectados, formación y lectura táctica.", "Erkannte Starter, Formation und taktische Lesart."), color = TextSecondary)
                    lineupTeams.forEach { team -> LineupPitchCard(team, prediction.sportKey, Mint, language) }
                }
            }
        }

        if (!tennisMode || actorDigests.isNotEmpty()) {
        item {
            PredictionSection(actorSectionTitle, Icons.Outlined.Groups, Violet) {
                if (!tennisMode && dossier == null) {
                    Text(t(language, "Recherche approfondie++ des stats joueurs, absences, retours, suspensions et fatigue récente…", "Deep search for player stats, absences, returns, suspensions and recent fatigue…", "Búsqueda profunda de stats de jugadores, ausencias, regresos, sanciones y fatiga reciente…", "Tiefsuche nach Spieler-Stats, Ausfällen, Rückkehrern, Sperren und Müdigkeit…"), color = TextSecondary)
                } else if (actorDigests.isEmpty()) {
                    Text(t(language, "Pas d’info joueur fiable trouvée pour ce match.", "No reliable player info found for this match.", "No hay info fiable de jugadores para este partido.", "Keine zuverlässigen Spielerinfos für dieses Spiel gefunden."), color = TextSecondary)
                } else {
                    actorDigests.forEach { digest -> ActorDigestCard(digest, Violet) }
                }
            }
        }
        }

        item {
            PredictionSection(t(language, "Autres pronostics", "Other predictions", "Otros pronósticos", "Weitere Prognosen"), Icons.AutoMirrored.Outlined.TrendingUp, Mint) {
                if (dossier == null) {
                    Text(t(language, "Préparation des scénarios…", "Preparing scenarios…", "Preparando escenarios…", "Szenarien werden vorbereitet…"), color = TextSecondary)
                } else if (otherScenarios.isEmpty()) {
                    Text(t(language, "Aucun autre pronostic affichable pour ce match.", "No other prediction to show for this match.", "No hay otro pronóstico para este partido.", "Keine weitere Prognose für dieses Spiel verfügbar."), color = TextSecondary)
                } else {
                    otherScenarios.forEach { scenario -> ScenarioProbabilityRow(scenario, Mint) }
                    if (scenarioNotes.isNotEmpty()) {
                        ProbabilityNotesCard(scenarioNotes)
                    }
                }
            }
        }

        item {
            PredictionSection(t(language, "Données utilisées", "Data used", "Datos utilizados", "Genutzte Daten"), Icons.Outlined.Newspaper, Amber) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PredictionMetric("Fiabilité", "${prediction.confidenceScore}/100", accent)
                    PredictionMetric("Niveau", predictionCategoryLabel(prediction.category), accent)
                }
                dossier?.let { SourceCoverageSummary(it.sourceCoverage, Blue) }
                Text("MAJ source : ${formatDate(prediction.sourceLastUpdate)}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        }
    }
}

private data class CloudAiReading(
    val statusLabel: String,
    val providerLabel: String,
    val headline: String,
    val confidenceLabel: String,
    val sections: List<CloudAiSection>,
    val diagnosticLines: List<String>,
)

private data class CloudAiSection(
    val title: String,
    val lines: List<String>,
)

private data class LineupPlayerView(val number: String?, val name: String, val position: String?)

private data class LineupTeamView(
    val teamName: String,
    val status: String,
    val formation: String?,
    val players: List<LineupPlayerView>,
)

private data class ParticipantDigest(
    val name: String,
    val lines: List<String>,
    val missingStats: List<String>,
)

private data class ActorDigest(
    val name: String,
    val role: String,
    val lines: List<String>,
    val probabilities: List<StructuredProbabilityLine>,
)

private data class PressInfoDigest(
    val title: String,
    val level: String,
    val lines: List<String>,
)

private const val NO_RECENT_FACT_LINE = "Aucun fait relevé"

private fun cloudAiReadingOrNull(prediction: PredictionEntity): CloudAiReading? {
    val analysis = prediction.aiAnalysis.parseCloudJsonObjectOrNull() ?: return null
    val diagnostic = prediction.aiDiagnostic.parseCloudJsonObjectOrNull()

    val providerCount = analysis.intField("providerCount")
    val source = analysis.textField("source")
    if (providerCount <= 0 || source.contains("fallback", ignoreCase = true) || source.contains("local-preanalysis", ignoreCase = true)) {
        return null
    }
    val headline = listOf(
        analysis.textField("titreAnalyse"),
        "Analyse IA — ${displayPredictionTeams(prediction)}",
    ).firstOrNull { it.isNotBlank() }.orEmpty()
    val confidence = analysis.intField("confianceIA")
    val statusLabel = when {
        providerCount >= 2 -> "Fusion IA"
        providerCount == 1 -> "IA cloud"
        else -> "Analyse préparée"
    }
    val providerLabel = analysis.textField("modeleUtilise")
        .ifBlank {
            diagnostic?.textListField("iaRepondues")?.joinToString(" + ").orEmpty()
        }
        .ifBlank { "backend sécurisé · aucune clé dans l’APK" }
    if (looksLikeLegacyLocalCloudAnalysis(analysis, providerLabel)) return null

    val sections = buildList {
        addCloudSection(
            "📌 Lecture rapide",
            analysis.textField("lectureRapide"),
        )
        addCloudSection(
            "✅ Avantage ${cloudParticipantLabel(prediction.selection, prediction)}",
            analysis.textField("avantageFavori"),
            analysis.textField("favoriLogique"),
        )
        addCloudSection(
            "⚠️ Danger ${cloudOutsiderLabel(prediction)}",
            analysis.textField("dangerOutsider"),
            analysis.textField("dangerAdversaire"),
        )
        addCloudSection(
            "🧩 Match-up clé",
            analysis.textField("matchUpCle"),
            analysis.textField("reponseStrategique"),
        )
        addCloudSection(
            "🔥 Points qui comptent vraiment",
            analysis.textField("pointsQuiComptent"),
            analysis.textField("avantagesExploitables"),
            analysis.textField("avantagesNeutralises"),
        )
        addCloudSection(
            "🎯 Scénario principal",
            analysis.textField("scenarioPrincipal"),
            analysis.textField("scoreProbable").takeIf { it.isNotBlank() }?.let { "Score / état probable : $it" }.orEmpty(),
        )
        addCloudSection(
            "🔁 Scénario alternatif",
            analysis.textField("scenarioAlternatif"),
        )
        addCloudSection(
            "📊 Confiance IA",
            analysis.textField("confianceTexte"),
            confidence.takeIf { it > 0 }?.let { "$it/100" }.orEmpty(),
            analysis.textField("donneesManquantes").takeIf { it.isNotBlank() }?.let { "À vérifier : $it" }.orEmpty(),
        )
    }.filter { it.lines.isNotEmpty() }

    if (headline.isBlank() && sections.isEmpty()) return null
    return CloudAiReading(
        statusLabel = statusLabel,
        providerLabel = providerLabel,
        headline = headline.ifBlank { "Analyse enrichie disponible." },
        confidenceLabel = confidence.takeIf { it > 0 }?.let { "$it/100" }.orEmpty(),
        sections = sections.take(8),
        diagnosticLines = cloudAiDiagnosticLines(analysis, diagnostic),
    )
}

private fun looksLikeLegacyLocalCloudAnalysis(analysis: JsonObject, providerLabel: String): Boolean {
    val sourceText = listOf(
        analysis.textField("source"),
        analysis.textField("modeleUtilise"),
        providerLabel,
    ).joinToString(" ").canonicalNameKey()
    if ("local" in sourceText || "fallback" in sourceText || "pre analyse" in sourceText || "sans cle" in sourceText) {
        return true
    }
    val body = listOf(
        analysis.textField("titreAnalyse"),
        analysis.textField("lectureRapide"),
        analysis.textField("avantageFavori"),
        analysis.textField("dangerOutsider"),
        analysis.textField("matchUpCle"),
        analysis.textField("pointsQuiComptent"),
        analysis.textField("scenarioPrincipal"),
        analysis.textField("scenarioAlternatif"),
        analysis.textField("confianceTexte"),
    ).joinToString(" ").canonicalNameKey()
    val legacySignals = listOf(
        "conclusion provisoire",
        "ce que ca change",
        "pas juste repris du tableau",
        "signal present dans les donnees",
        "doit etre lu comme une conclusion",
        "lignes de donnees relues localement",
        "analyse correcte local",
    )
    return legacySignals.any { it in body }
}

private fun cloudAiUnavailableReason(prediction: PredictionEntity): String {
    if (prediction.aiAnalysis.isBlank()) return "pas d’analyse IA cloud rattachée à cette fiche"
    val analysis = prediction.aiAnalysis.parseCloudJsonObjectOrNull()
        ?: return "JSON IA invalide ou incomplet"
    val source = analysis.textField("source")
    if (source.contains("fallback", ignoreCase = true) || source.contains("local-preanalysis", ignoreCase = true)) {
        return "fallback local rejeté"
    }
    val providerCount = analysis.intField("providerCount")
    if (providerCount <= 0) return "providerCount = 0, donc pas une vraie IA cloud"
    val providerLabel = analysis.textField("modeleUtilise")
        .ifBlank { prediction.aiDiagnostic.parseCloudJsonObjectOrNull()?.textListField("iaRepondues")?.joinToString(" + ").orEmpty() }
    if (looksLikeLegacyLocalCloudAnalysis(analysis, providerLabel)) return "ancienne analyse générique rejetée"
    return "analyse cloud non affichable avec les champs actuels"
}

private fun cloudAiRequestStatus(prediction: PredictionEntity): Pair<String, String>? {
    val diagnostic = prediction.aiDiagnostic.parseCloudJsonObjectOrNull() ?: return null
    val status = diagnostic.textField("aiRequestStatus")
    val message = diagnostic.textField("aiRequestMessage")
    if (status.isBlank() && message.isBlank()) return null
    return status to message
}

private fun MutableList<CloudAiSection>.addCloudSection(title: String, vararg rawLines: String) {
    val lines = rawLines
        .flatMap(::splitCloudAiLine)
        .map(::cleanCloudAiDisplayText)
        .filter(::isUsefulCloudAiLine)
        .distinctBy { it.canonicalNameKey() }
        .take(4)
    if (lines.isNotEmpty()) add(CloudAiSection(title, lines))
}

private fun splitCloudAiLine(value: String): List<String> {
    value.toHumanProbabilityLineOrNull()?.let { return listOf(it) }
    val cleaned = cleanDisplayText(value)
        .replace(" · ", "\n")
        .replace(" ; ", "\n")
        .replace(" - ", " — ")
    if (cleaned.isBlank()) return emptyList()
    if (cleaned.length <= 230) return listOf(cleaned)
    return cleaned
        .split(Regex("(?<=[.!?])\\s+|\\n+"))
        .map { it.trim(' ', '•', '-', '·') }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf(cleaned.take(260)) }
}

private fun cloudAiDiagnosticLines(analysis: JsonObject, diagnostic: JsonObject?): List<String> {
    if (diagnostic == null) return emptyList()
    val called = diagnostic.textListField("iaAppelees")
    val responded = diagnostic.textListField("iaRepondues")
    val errors = diagnostic.textListField("iaEnErreur")
    return buildList {
        val model = responded.joinToString(" + ").ifBlank { analysis.textField("modeleUtilise") }
        if (model.isNotBlank()) add("IA utilisée : $model")
        val sources = analysis.textField("sourcesUtilisees")
        if (sources.isNotBlank()) add("Sources sport : $sources")
        if (called.isNotEmpty() || responded.isNotEmpty()) add("Réponses cloud : ${responded.size}/${called.size.coerceAtLeast(responded.size)}")
        if (errors.isNotEmpty()) add("Limite actuelle : ${errors.take(1).joinToString(" | ")}")
        val date = diagnostic.textField("dateGeneration")
        if (date.isNotBlank()) add("Dernière génération : $date")
    }.map(::cleanCloudAiDisplayText).filter(::isUsefulCloudAiLine).take(4)
}

private fun cloudParticipantLabel(selection: String, prediction: PredictionEntity): String =
    listOf(prediction.homeTeam, prediction.awayTeam)
        .map(::displayTeamName)
        .firstOrNull { selection.contains(it, ignoreCase = true) }
        ?: cleanDisplayText(selection).substringBefore("(").trim().takeIf { it.isNotBlank() }
        ?: "favori"

private fun cloudOutsiderLabel(prediction: PredictionEntity): String {
    val favorite = cloudParticipantLabel(prediction.selection, prediction).canonicalNameKey()
    return listOf(prediction.homeTeam, prediction.awayTeam)
        .map(::displayTeamName)
        .firstOrNull { it.isNotBlank() && !it.canonicalNameKey().let(favorite::contains) }
        ?: "outsider"
}

private fun cleanCloudAiDisplayText(value: String): String =
    cleanDisplayText(value)
        .replaceRawCloudProbabilities()
        .replace("0 €", "zéro euro")
        .trim()

private fun isUsefulCloudAiLine(value: String): Boolean {
    val text = cleanDisplayText(value).lowercase(Locale.FRANCE)
    return text.isNotBlank() &&
        text != "undefined" &&
        text != "null" &&
        !text.matches(Regex("""0[,.]\d{3,}""")) &&
        !text.contains("architecture") &&
        !text.contains("analyse locale sans clé") &&
        !text.contains("fallback local") &&
        !text.contains("secours local") &&
        !text.contains("simple pourcentage") &&
        !text.contains("doit être lu comme une conclusion")
}

private fun String.toHumanProbabilityLineOrNull(): String? {
    val parts = split('|').map(::cleanDisplayText)
    if (parts.size < 3) return null
    val probability = parts[2].replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: return null
    val percent = String.format(Locale.FRANCE, "%.0f %%", probability * 100)
    return listOf(parts[1], parts[0].takeIf { it.isNotBlank() }?.let { "($it)" }, percent)
        .filterNotNull()
        .joinToString(" — ")
}

private fun String.replaceRawCloudProbabilities(): String =
    Regex("""(?<!\d)0[,.](\d{3,})(?!\d)""").replace(this) { match ->
        val number = "0.${match.groupValues[1]}".toDoubleOrNull()
        if (number != null) String.format(Locale.FRANCE, "%.0f %%", number * 100) else match.value
    }

private fun String.parseCloudJsonObjectOrNull(): JsonObject? =
    runCatching { JsonParser.parseString(this).asJsonObjectOrNull() }.getOrNull()

private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
    takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.textField(key: String): String =
    get(key)?.toCloudText().orEmpty()

private fun JsonObject.textListField(key: String): List<String> {
    val element = get(key) ?: return emptyList()
    return when {
        element.isJsonArray -> element.asJsonArray.mapNotNull { it.toCloudText().takeIf(String::isNotBlank) }
        else -> element.toCloudText().split(',', '|').map(::cleanDisplayText).filter { it.isNotBlank() }
    }.distinct()
}

private fun JsonObject.intField(key: String): Int =
    runCatching {
        val element = get(key) ?: return 0
        if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) element.asInt
        else element.toCloudText().filter(Char::isDigit).toIntOrNull() ?: 0
    }.getOrDefault(0).coerceIn(0, 100)

private fun JsonObject.booleanField(key: String): Boolean =
    runCatching {
        val element = get(key) ?: return false
        if (element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) element.asBoolean
        else element.toCloudText().equals("true", ignoreCase = true)
    }.getOrDefault(false)

private fun JsonElement.toCloudText(): String = when {
    isJsonNull -> ""
    isJsonPrimitive -> asString
    isJsonArray -> asJsonArray.joinToString(" · ") { it.toCloudText() }
    isJsonObject -> asJsonObject.entrySet().joinToString(" · ") { "${it.key}: ${it.value.toCloudText()}" }
    else -> toString()
}.let(::cleanDisplayText)

private fun parseLineup(value: String): List<LineupPlayerView> = value.lines().mapNotNull { line ->
    if (line.isBlank()) return@mapNotNull null
    val parts = line.split('|')
    when (parts.size) {
        3 -> LineupPlayerView(parts[0].ifBlank { null }, parts[1], parts[2].ifBlank { null })
        2 -> if (parts[0].startsWith("#")) {
            LineupPlayerView(parts[0], parts[1], null)
        } else {
            LineupPlayerView(null, parts[0], parts[1].ifBlank { null })
        }
        else -> LineupPlayerView(null, parts[0], null)
    }
}.map { player ->
    player.copy(
        number = player.number?.let(::cleanDisplayText),
        name = cleanDisplayText(player.name),
        position = player.position?.let(::cleanDisplayText),
    )
}.filter { it.name.isNotBlank() }

private fun lineupDisplayTeams(prediction: PredictionEntity): List<LineupTeamView> = buildList {
    val home = parseLineup(prediction.homeLineup)
    val away = parseLineup(prediction.awayLineup)
    if (home.isNotEmpty()) {
        add(
            LineupTeamView(
                teamName = displayTeamName(prediction.homeTeam),
                status = cleanDisplayText(prediction.homeLineupStatus),
                formation = lineupFormation(prediction.homeLineupStatus),
                players = home,
            )
        )
    }
    if (away.isNotEmpty()) {
        add(
            LineupTeamView(
                teamName = displayTeamName(prediction.awayTeam),
                status = cleanDisplayText(prediction.awayLineupStatus),
                formation = lineupFormation(prediction.awayLineupStatus),
                players = away,
            )
        )
    }
}

private fun lineupFormation(status: String): String? =
    Regex("""\b\d(?:-\d){1,4}\b""").find(cleanDisplayText(status))?.value

private fun isTennisPrediction(prediction: PredictionEntity): Boolean =
    prediction.sportKey.substringBefore('/').equals("tennis", ignoreCase = true) ||
        prediction.sportTitle.contains("tennis", ignoreCase = true)

private fun tennisPlayerNames(prediction: PredictionEntity): List<String> {
    val directNames = listOf(prediction.homeTeam, prediction.awayTeam)
    val titleNames = splitTennisPlayerPair(displayPredictionTeams(prediction))
    val statNames = (prediction.statSummary.lines() + prediction.contextInsights.lines())
        .mapNotNull { line ->
            cleanDisplayText(line)
                .substringBefore(':', missingDelimiterValue = "")
                .takeIf { it.isNotBlank() && " vs " !in it.lowercase(Locale.FRANCE) }
        }
    return (directNames + titleNames + statNames)
        .flatMap(::splitTennisPlayerPair)
        .map(::cleanTennisPlayerCandidate)
        .filter(::isUsableTennisPlayerName)
        .distinctBy { it.canonicalNameKey() }
        .take(2)
}

private fun splitTennisPlayerPair(value: String): List<String> {
    val cleaned = cleanDisplayText(value).trim()
    if (cleaned.isBlank()) return emptyList()
    val normalized = cleaned
        .replace(Regex("\\s+(?:vs\\.?|v)\\s+", RegexOption.IGNORE_CASE), " — ")
        .replace(" - ", " — ")
    return normalized.split("—", "–")
        .map(::cleanTennisPlayerCandidate)
        .filter { it.isNotBlank() }
}

private fun cleanTennisPlayerCandidate(value: String): String =
    cleanDisplayText(value)
        .substringBefore(" plus de ", missingDelimiterValue = value)
        .substringBefore(" moins de ", missingDelimiterValue = value)
        .substringBefore(" over ", missingDelimiterValue = value)
        .substringBefore(" under ", missingDelimiterValue = value)
        .substringBefore(" avantage ", missingDelimiterValue = value)
        .substringBefore(" conserve ", missingDelimiterValue = value)
        .replace(Regex("\\s+"), " ")
        .trim(' ', '·', '-', ':')

private fun isUsableTennisPlayerName(value: String): Boolean {
    val cleaned = cleanDisplayText(value)
    val key = cleaned.canonicalNameKey().replace("/", " ")
    if (cleaned.length !in 3..46) return false
    if (Regex("""\d""").containsMatchIn(cleaned)) return false
    return listOf(
        "tennis",
        "classement",
        "bilan",
        "service",
        "retour",
        "surface",
        "forme",
        "h2h",
        "aucun fait",
        "score",
        "vainqueur",
        "wimbledon",
        "atp",
        "wta",
    ).none { it in key }
}

private fun tennisParticipantBilanDigests(prediction: PredictionEntity): List<ParticipantDigest> =
    tennisPlayerNames(prediction).map { player ->
        val lines = tennisStatLinesForPlayer(prediction, player, includeProbabilityNotes = false)
            .ifEmpty { listOf("Aucune statistique joueur trouvée.") }
        ParticipantDigest(
            name = player,
            lines = compactUiLines(lines, max = 6),
            missingStats = emptyList(),
        )
    }

private fun tennisActorBilanDigests(
    prediction: PredictionEntity,
    dossier: StructuredAnalysisDossier?,
): List<ActorDigest> =
    tennisPlayerNames(prediction).mapNotNull { player ->
        val directProbabilities = prediction.playerScenarios.lines()
            .mapNotNull { it.toProbabilityLineOrNull() }
        val probabilities = (directProbabilities + dossier?.playerProbabilities.orEmpty())
            .filter { probability -> probability.mentionsPlayer(player) }
            .filter(::isActionableProbabilityLine)
            .distinctBy { cleanDisplayText(it.type).canonicalNameKey() + "|" + cleanDisplayText(it.label).canonicalNameKey() }
            .take(3)
        if (probabilities.isEmpty()) return@mapNotNull null
        ActorDigest(
            name = player,
            role = "Marchés joueur tennis",
            lines = emptyList(),
            probabilities = probabilities,
        )
    }

private fun tennisPressInfoDigests(prediction: PredictionEntity): List<PressInfoDigest> =
    tennisPlayerNames(prediction).map { player ->
        val facts = prediction.contextInsights.lines()
            .map(::cleanDisplayText)
            .mapNotNull { line -> tennisScopedLine(player, line) }
            .filterNot { isNoRecentFactLine(it) }
            .filter(::isRealTennisPressFact)
            .let { compactUiLines(it, max = 4) }
        PressInfoDigest(
            title = player,
            level = facts.firstOrNull()?.let { "Info joueur" }.orEmpty(),
            lines = facts.ifEmpty { listOf(NO_RECENT_FACT_LINE) },
        )
    }

private fun tennisStatLinesForPlayer(
    prediction: PredictionEntity,
    player: String,
    includeProbabilityNotes: Boolean,
): List<String> {
    val statLines = prediction.statSummary.lines()
        .map(::cleanDisplayText)
        .mapNotNull { line -> tennisScopedLine(player, line) }
        .filterNot(::isTennisMatchLevelLine)
    val probabilityLines = if (!includeProbabilityNotes) {
        emptyList()
    } else {
        prediction.playerScenarios.lines()
            .mapNotNull { it.toProbabilityLineOrNull() }
            .filter { it.mentionsPlayer(player) }
            .map { "${cleanDisplayText(it.type)} : ${stripDigestPrefix(player, it.label)} (${formatPercent(it.probability)})" }
    }
    return (statLines + probabilityLines)
        .map(::simplifyTennisStatLine)
        .filterNot(::isNoRecentFactLine)
        .filter(::isUsefulTennisStatLine)
        .distinctBy { it.canonicalNameKey() }
}

private fun tennisScopedLine(player: String, line: String): String? {
    val cleaned = cleanDisplayText(line).trim()
    val prefix = cleaned.substringBefore(':', missingDelimiterValue = "")
    if (prefix.isBlank()) return null
    val prefixKey = cleanTennisPlayerCandidate(prefix).canonicalNameKey()
    val playerKey = player.canonicalNameKey()
    if (prefixKey != playerKey && !prefixKey.startsWith("$playerKey ")) return null
    return cleaned.substringAfter(':').trim(' ', '·', '-', ':').takeIf { it.isNotBlank() }
}

private fun simplifyTennisStatLine(value: String): String =
    cleanDisplayText(value)
        .replace("retour/pression 365j", "retour 365j")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '·', '-', ':')

private fun isUsefulTennisStatLine(value: String): Boolean {
    val key = value.canonicalNameKey().replace("/", " ")
    if (key.isBlank()) return false
    if (isNoRecentFactLine(key)) return false
    if (listOf("projection tennis", "source", "aucune stat").any { it in key }) return false
    return listOf(
        "classement",
        "bilan 365j",
        "bilan carriere",
        "service 365j",
        "retour 365j",
        "forme recente",
        "dynamique",
        "charge",
        "dernier match",
        "sur dur",
        "sur gazon",
        "sur terre",
        "sur surface",
    ).any { it in key }
}

private fun isTennisMatchLevelLine(value: String): Boolean {
    val key = value.canonicalNameKey()
    return " vs " in value.lowercase(Locale.FRANCE) ||
        "face a face" in key ||
        key.startsWith("h2h ") ||
        "aucun face a face" in key ||
        "dernier h2h" in key
}

private fun isRealTennisPressFact(value: String): Boolean {
    val key = value.canonicalNameKey().replace("/", " ")
    if (key.isBlank()) return false
    if (isTennisMetricInfoText(key)) return false
    if (listOf("classement", "forme recente", "bilan 365j", "service 365j", "surface", "h2h").any { it in key }) return false
    val healthOrAvailability = listOf(
        "bless",
        "forfait",
        "withdraw",
        "abandon",
        "suspend",
        "suspension",
        "absence",
        "absent",
        "malade",
        "fatigue",
        "douleur",
        "poignet",
        "genou",
        "epaule",
        "dos",
        "cheville",
    ).any { it in key }
    val concreteReturn = listOf(
        "retour de blessure",
        "retour competition",
        "retour entrainement",
        "retour apres",
        "reprise entrainement",
    ).any { it in key }
    return healthOrAvailability || concreteReturn
}

private fun StructuredProbabilityLine.mentionsPlayer(player: String): Boolean =
    cleanDisplayText("$type $label").canonicalNameKey().contains(player.canonicalNameKey())

private fun String.toProbabilityLineOrNull(): StructuredProbabilityLine? {
    val parts = split('|')
    if (parts.size < 3) return null
    return StructuredProbabilityLine(
        type = cleanDisplayText(parts[0]),
        label = cleanDisplayText(parts[1]),
        probability = parts[2].replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: return null,
    )
}

private fun participantBilanDigests(dossier: StructuredAnalysisDossier): List<ParticipantDigest> {
    val names = buildList {
        addAll(dossier.participantBlocks.map { it.name })
        addAll(dossier.participantSituationBlocks.map { it.name })
        addAll(dossier.competitionStakeBlocks.map { it.name })
        addAll(dossier.formTrendBlocks.map { it.name })
        addAll(dossier.importantInfoBlocks.map { it.name })
        addAll(dossier.participantStatRequirementBlocks.map { it.name })
    }.map(::cleanDisplayText).filter { it.isNotBlank() }.distinctBy { it.canonicalNameKey() }

    return names.mapNotNull { name ->
        val participant = dossier.participantBlocks.firstNamed(name)
        val situation = dossier.participantSituationBlocks.firstNamed(name)
        val competition = dossier.competitionStakeBlocks.firstNamed(name)
        val form = dossier.formTrendBlocks.firstNamed(name)
        val important = dossier.importantInfoBlocks.firstNamed(name)
        val missing = dossier.participantStatRequirementBlocks.firstNamed(name)

        val impactLines = participantImpactLines(
            name = name,
            stats = participant?.stats.orEmpty(),
            trends = form?.trends.orEmpty(),
            important = important?.items.orEmpty().map { "${it.category} : ${it.text}" },
            competition = competition?.forecastImpact.orEmpty(),
            situation = situation?.lines.orEmpty(),
        )
        val lines = compactDigestLines(
            name = name,
            lines = buildList {
                addAll(impactLines)
                form?.let { addAll(it.trends) }
                important?.let { block ->
                    addAll(block.items.map { "${it.category} : ${it.text}" }.filter { info ->
                        isAvailabilityImpactLine(info) || isDisciplineImpactLine(info)
                    })
                }
            },
            max = 3,
        )
        val missingLines = compactUiLines(missing?.missingStats.orEmpty(), max = 3)
        if (lines.isEmpty()) null else ParticipantDigest(name, lines, missingLines)
    }
}

private fun actorBilanDigests(dossier: StructuredAnalysisDossier): List<ActorDigest> {
    val actorLines = dossier.actorBlocks.mapNotNull { block ->
        val probabilities = block.probabilities
            .filter(::isActionableProbabilityLine)
            .distinctBy { cleanDisplayText(it.label).canonicalNameKey() }
            .take(3)
        val lines = compactDigestLines(
            name = block.name,
            lines = block.stats + block.importantInfo.map { "${it.category} : ${it.text}" },
            max = 3,
        )
        if (lines.isEmpty() && probabilities.isEmpty()) null else ActorDigest(block.name, block.teamOrRole, lines, probabilities)
    }
    if (actorLines.isNotEmpty()) return actorLines
    return emptyList()
}

private fun pressInfoDigests(dossier: StructuredAnalysisDossier): List<PressInfoDigest> {
    val participantNames = buildList {
        addAll(dossier.participantBlocks.map { it.name })
        addAll(dossier.participantSituationBlocks.map { it.name })
        addAll(dossier.formTrendBlocks.map { it.name })
        addAll(dossier.importantInfoBlocks.map { it.name })
    }.map(::cleanDisplayText)
        .filter { it.isNotBlank() && it.canonicalNameKey() !in setOf("match", "sourcespressereseaux") }
        .distinctBy { it.canonicalNameKey() }
        .take(2)

    val participantInfo = participantNames.map { name ->
        val block = dossier.importantInfoBlocks.firstNamed(name)
        val usefulItems = block?.items.orEmpty()
            .filter(::isPressInfoLine)
        val lines = usefulItems
            .map { pressInfoLine(name, it) }
            .let { compactUiLines(it, max = 5) }
            .ifEmpty { listOf(NO_RECENT_FACT_LINE) }
        PressInfoDigest(
            title = name,
            level = usefulItems.firstOrNull()?.level.orEmpty().takeUnless { lines.singleOrNull() == NO_RECENT_FACT_LINE }.orEmpty(),
            lines = lines,
        )
    }
    val participantKeys = participantNames.map { it.canonicalNameKey() }.toSet()
    val actorInfo = dossier.actorBlocks.mapNotNull { block ->
        if (isNonParticipantPressScope(block.name)) return@mapNotNull null
        if (block.name.canonicalNameKey() in participantKeys) return@mapNotNull null
        val infoLines = block.importantInfo
            .filter(::isPressInfoLine)
            .map { pressInfoLine(block.name, it) }
        val probabilityLines = block.probabilities
            .filterNot(::isActionableProbabilityLine)
            .map { cleanDisplayText(it.label) }
            .filter(::isUsefulProbabilityNote)
            .map { "Impact joueur : $it" }
        val lines = compactUiLines(infoLines + probabilityLines, max = 4)
        if (lines.isEmpty()) null else PressInfoDigest(
            title = block.name,
            level = block.importantInfo.firstOrNull(::isPressInfoLine)?.level.orEmpty(),
            lines = lines,
        )
    }
    return (participantInfo + actorInfo)
        .distinctBy { digest -> digest.title.canonicalNameKey() + "|" + digest.lines.joinToString("|") { it.canonicalNameKey() } }
}

private fun isPressInfoLine(line: StructuredReliabilityLine): Boolean {
    val raw = cleanDisplayText("${line.category} ${line.text}")
    val text = raw.canonicalNameKey()
    if (text.isBlank()) return false
    if (isNoRecentFactLine(text) || isGenericPressChecklistLine(text)) return false
    if (isTennisMetricInfoText(text)) return false
    if (listOf(
            "source principale",
            "fiabilite des donnees",
            "accord des sources",
            "aucune donnee",
            "a verifier",
            "a recouper",
            "a recalculer",
            "attendre",
            "aucun fait releve",
            "stats cles surveillees",
            "stats joueurs suivies",
        ).any { it in text }
    ) return false
    return listOf(
        "infirmerie",
        "disponibilite",
        "bless",
        "absent",
        "forfait",
        "retour",
        "malade",
        "discipline",
        "suspension",
        "carton",
        "titulaire",
        "remplacant",
        "coach",
        "vestiaire",
        "communique",
        "presse",
        "conference",
        "entrainement",
    ).any { it in text }
}

private fun isTennisMetricInfoText(text: String): Boolean {
    val key = cleanDisplayText(text).canonicalNameKey().replace("/", " ")
    return listOf(
        "service 365j",
        "retour pression 365j",
        "retour 365j",
        "break points",
        "balles de break",
        "aces match",
        "aces par match",
        "doubles fautes",
        "jeux de service",
        "pts gagnes",
        "1res balles",
        "match moyen",
        "bilan 365j",
    ).any { it in key }
}

private fun pressInfoLine(scope: String, line: StructuredReliabilityLine): String {
    val body = stripDigestPrefix(scope, line.text)
    val category = pressDisplayCategory(line)
    val level = cleanDisplayText(line.level)
        .takeIf { it.isNotBlank() && it !in setOf("Très probable", "Info") }
    return listOfNotNull(category, level, body)
        .joinToString(" : ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '·', '-', ':')
}

private fun isNonParticipantPressScope(value: String): Boolean {
    val key = cleanDisplayText(value).canonicalNameKey().replace(" ", "")
    return key in setOf("match", "sourcepressereseaux", "sourcespressereseaux", "sources", "presse", "reseaux")
}

private fun isNoRecentFactLine(text: String): Boolean {
    val key = cleanDisplayText(text).canonicalNameKey()
    if ("aucun fait releve" in key) return true
    val saysNone = listOf("aucune", "aucun", "pas de", "non trouve", "rien trouve").any { it in key }
    if (!saysNone) return false
    return listOf(
        "absence",
        "absent",
        "bless",
        "suspension",
        "carton",
        "retour",
        "alerte",
        "indisponible",
        "forfait",
        "malade",
    ).any { it in key }
}

private fun isGenericPressChecklistLine(text: String): Boolean {
    val key = cleanDisplayText(text).canonicalNameKey()
    return listOf(
        "stats cles surveillees",
        "stats joueurs suivies",
        "source presse reseaux",
        "sources presse reseaux",
        "reseaux sociaux officiels couvert",
        "canal social officiel",
        "une source presse conference",
        "composition publiee apres",
    ).any { it in key }
}

private fun pressDisplayCategory(line: StructuredReliabilityLine): String? {
    val text = cleanDisplayText("${line.category} ${line.text}").canonicalNameKey()
    return when {
        listOf("carton", "suspend", "suspension", "discipline").any { it in text } -> "Suspension / carton"
        listOf("retour", "apte", "disponible", "reprise", "entrainement").any { it in text } -> "Retour / disponibilité"
        listOf("bless", "forfait", "absent", "indisponible", "malade", "doubtful", "incertain").any { it in text } -> "Blessure / absence"
        listOf("coach", "conference", "communique", "presse", "officiel").any { it in text } -> "Info officielle"
        else -> cleanDisplayText(line.category).takeIf { it.isNotBlank() && it != "Info complémentaire" }
    }
}

private fun lineupRows(team: LineupTeamView, sportKey: String): List<List<LineupPlayerView>> {
    val players = team.players.distinctBy { it.name.canonicalNameKey() }
    if (players.isEmpty()) return emptyList()
    if (sportKey.substringBefore('/') == "rugby") {
        rugbyLineupRows(players).takeIf { it.isNotEmpty() }?.let { return it }
    }
    val byRole = players.groupBy { lineupRole(it.position) }
    val roleRows = listOf(
        byRole["att"].orEmpty(),
        byRole["mid"].orEmpty(),
        byRole["def"].orEmpty(),
        byRole["gk"].orEmpty(),
    ).filter { it.isNotEmpty() }
    if (roleRows.flatten().size >= players.size.coerceAtMost(5)) return roleRows

    val formationNumbers = team.formation?.split('-')?.mapNotNull { it.toIntOrNull() }.orEmpty()
    if (formationNumbers.isNotEmpty() && players.size >= formationNumbers.sum().coerceAtMost(players.size - 1)) {
        val bottomToTop = mutableListOf<List<LineupPlayerView>>()
        var cursor = 0
        if (players.size >= formationNumbers.sum() + 1) {
            bottomToTop.add(players.take(1))
            cursor = 1
        }
        formationNumbers.forEach { amount ->
            val row = players.drop(cursor).take(amount)
            if (row.isNotEmpty()) bottomToTop.add(row)
            cursor += amount
        }
        val rest = players.drop(cursor)
        if (rest.isNotEmpty()) bottomToTop.add(rest)
        return bottomToTop.asReversed().filter { it.isNotEmpty() }
    }

    return fallbackLineupRows(players, sportKey)
}

private fun rugbyLineupRows(players: List<LineupPlayerView>): List<List<LineupPlayerView>> {
    fun byNumbers(vararg numbers: Int): List<LineupPlayerView> = numbers.asSequence().mapNotNull { number ->
        players.firstOrNull { player ->
            player.number?.filter(Char::isDigit)?.toIntOrNull() == number
        }
    }.toList()
    val numberedRows = listOf(
        byNumbers(11, 15, 14),
        byNumbers(12, 13),
        byNumbers(10, 9),
        byNumbers(6, 8, 7),
        byNumbers(4, 5),
        byNumbers(1, 2, 3),
    ).filter { it.isNotEmpty() }
    val placed = numberedRows.flatten().map { it.name.canonicalNameKey() }.toSet()
    val rest = players.filterNot { it.name.canonicalNameKey() in placed }
    return if (numberedRows.flatten().size >= players.size.coerceAtMost(10)) {
        (numberedRows + rest.chunked(3)).filter { it.isNotEmpty() }
    } else {
        val rowCount = when {
            players.size >= 15 -> 6
            players.size >= 10 -> 5
            players.size >= 6 -> 3
            else -> 2
        }
        players.chunked(((players.size + rowCount - 1) / rowCount).coerceAtLeast(1))
    }
}

private fun lineupRole(position: String?): String? {
    val value = cleanDisplayText(position.orEmpty()).canonicalNameKey()
    return when {
        value in setOf("g", "gb", "gk", "goalkeeper", "keeper") -> "gk"
        value in setOf("d", "df", "def", "defender", "cb", "lb", "rb", "lcb", "rcb", "ar", "pilier", "talonneur", "lock") -> "def"
        value in setOf("m", "mf", "mid", "midfielder", "dm", "cm", "am", "lm", "rm", "scrumhalf", "flyhalf", "demi", "centre") -> "mid"
        value in setOf("f", "fw", "st", "cf", "att", "forward", "lw", "rw", "wing", "ailier", "fullback") -> "att"
        value.startsWith("gk") -> "gk"
        value.startsWith("def") || value.endsWith("back") -> "def"
        value.startsWith("mid") -> "mid"
        value.startsWith("for") || value.startsWith("att") || value.contains("wing") -> "att"
        else -> null
    }
}

private fun fallbackLineupRows(players: List<LineupPlayerView>, sportKey: String): List<List<LineupPlayerView>> {
    val sport = sportKey.substringBefore('/')
    if (sport == "basketball" && players.size <= 7) {
        return listOf(players.take(2), players.drop(2).take(3), players.drop(5)).filter { it.isNotEmpty() }
    }
    val rowCount = when {
        players.size >= 15 -> 5
        players.size >= 10 -> 4
        players.size >= 6 -> 3
        else -> 2
    }
    val chunkSize = ((players.size + rowCount - 1) / rowCount).coerceAtLeast(1)
    return players.chunked(chunkSize)
}

private fun lineupTacticalReading(team: LineupTeamView, sportKey: String, language: String): String {
    val sport = sportKey.substringBefore('/')
    val numbers = team.formation?.split('-')?.mapNotNull { it.toIntOrNull() }.orEmpty()
    if (numbers.isNotEmpty()) {
        val defenders = numbers.firstOrNull() ?: 0
        val attackers = numbers.lastOrNull() ?: 0
        val midfield = numbers.drop(1).dropLast(1).sum()
        return when {
            defenders >= 5 -> t(language, "Lecture : bloc plutôt défensif, priorité à la protection de l’axe.", "Reading: rather defensive block, priority on protecting the central lane.", "Lectura: bloque más defensivo, prioridad a proteger el eje.", "Lesart: eher defensiver Block, Schutz der Mitte im Fokus.")
            attackers >= 3 -> t(language, "Lecture : structure offensive, présence forte sur la ligne d’attaque.", "Reading: attacking structure, strong presence on the forward line.", "Lectura: estructura ofensiva, fuerte presencia arriba.", "Lesart: offensive Struktur, starke Präsenz in der Angriffsreihe.")
            midfield >= 5 -> t(language, "Lecture : densité au milieu, contrôle et transitions prioritaires.", "Reading: midfield density, control and transitions first.", "Lectura: densidad en el medio, control y transiciones.", "Lesart: Dichte im Mittelfeld, Kontrolle und Umschalten.")
            else -> t(language, "Lecture : structure équilibrée, projection et sécurité partagées.", "Reading: balanced structure between attacking projection and cover.", "Lectura: estructura equilibrada entre proyección y seguridad.", "Lesart: ausgewogene Struktur zwischen Vorstoß und Absicherung.")
        }
    }
    return when (sport) {
        "rugby" -> t(language, "Lecture : vérifier charnière, buteur, banc et conquête pour estimer l’orientation offensive/défensive.", "Reading: check half-backs, kicker, bench and set-piece to judge attack/defense orientation.", "Lectura: revisar pareja de medios, pateador, banquillo y conquista para estimar orientación.", "Lesart: Halbspieler, Kicker, Bank und Standards für Angriffs-/Defensivfokus prüfen.")
        "basketball" -> t(language, "Lecture : cinq majeur et rotations à comparer avec usage, fatigue et forme des scoreurs.", "Reading: starting five and rotations must be compared with usage, fatigue and scorer form.", "Lectura: quinteto y rotaciones frente a uso, fatiga y forma de anotadores.", "Lesart: Starting Five und Rotation mit Usage, Müdigkeit und Scorerform abgleichen.")
        "baseball" -> t(language, "Lecture : lineup et lanceur probable guident surtout runs, hits et strikeouts.", "Reading: lineup and probable pitcher mainly guide runs, hits and strikeouts.", "Lectura: lineup y pitcher probable guían carreras, hits y strikeouts.", "Lesart: Lineup und Pitcher steuern vor allem Runs, Hits und Strikeouts.")
        "hockey" -> t(language, "Lecture : alignement/gardien à croiser avec volume de tirs et discipline.", "Reading: lineup/goalie should be crossed with shot volume and discipline.", "Lectura: alineación/portero cruzados con tiros y disciplina.", "Lesart: Aufstellung/Torwart mit Schussvolumen und Disziplin abgleichen.")
        else -> t(language, "Lecture : placement indicatif selon les postes détectés par les sources publiques.", "Reading: indicative placement based on public source positions.", "Lectura: colocación indicativa según posiciones de fuentes públicas.", "Lesart: indikative Platzierung anhand öffentlicher Positionsdaten.")
    }
}

private fun playerRoleProfile(player: LineupPlayerView, sportKey: String, language: String): String {
    val sport = sportKey.substringBefore('/')
    val role = lineupRole(player.position)
    return when (sport) {
        "rugby" -> when (role) {
            "att" -> t(language, "finisseur / relance", "finisher / counter", "finalizador / contraataque", "Finisher / Konter")
            "mid" -> t(language, "création / jeu au pied", "creator / kicking", "creador / patada", "Spielmacher / Kick")
            "def" -> t(language, "conquête / impact", "set-piece / impact", "conquista / impacto", "Standard / Impact")
            else -> t(language, "rôle à confirmer", "role to confirm", "rol por confirmar", "Rolle offen")
        }
        "basketball" -> when (role) {
            "att" -> t(language, "scoreur / spacing", "scorer / spacing", "anotador / spacing", "Scorer / Spacing")
            "mid" -> t(language, "création / passe", "creation / passing", "creación / pase", "Aufbau / Pass")
            "def" -> t(language, "rebond / protection", "rebound / rim cover", "rebote / protección", "Rebound / Schutz")
            else -> t(language, "rotation utile", "useful rotation", "rotación útil", "nützliche Rotation")
        }
        "baseball" -> t(language, "batte / matchup", "batting / matchup", "bateo / matchup", "Schlag / Matchup")
        "hockey" -> when (role) {
            "att" -> t(language, "finition / tirs", "finishing / shots", "definición / tiros", "Abschluss / Schüsse")
            "def" -> t(language, "défense / relance", "defense / breakout", "defensa / salida", "Defensive / Aufbau")
            else -> t(language, "transition / pressing", "transition / pressing", "transición / presión", "Umschalten / Pressing")
        }
        else -> when (role) {
            "gk" -> t(language, "gardien / dernier rempart", "keeper / last line", "portero / último muro", "Torwart / letzte Linie")
            "def" -> t(language, "défense / duels", "defense / duels", "defensa / duelos", "Defensive / Duelle")
            "mid" -> t(language, "création / passes", "creation / passing", "creación / pases", "Kreation / Pässe")
            "att" -> t(language, "finition / appels", "finishing / runs", "definición / desmarques", "Abschluss / Läufe")
            else -> t(language, "profil à confirmer", "profile to confirm", "perfil por confirmar", "Profil offen")
        }
    }
}

private fun otherProbabilityLines(
    prediction: PredictionEntity,
    dossier: StructuredAnalysisDossier,
): List<StructuredProbabilityLine> {
    val mainLabel = cleanDisplayText(prediction.selection).canonicalNameKey()
    return buildList {
        addAll(dossier.globalProbabilities)
        addAll(dossier.participantProbabilities)
        addAll(dossier.participantBlocks.flatMap { it.probabilities })
    }
        .filter(::isActionableProbabilityLine)
        .filterNot { cleanDisplayText(it.type).equals("Signal principal", ignoreCase = true) }
        .filterNot { cleanDisplayText(it.label).canonicalNameKey() == mainLabel }
        .distinctBy { "${cleanDisplayText(it.type).canonicalNameKey()}|${cleanDisplayText(it.label).canonicalNameKey()}" }
        .sortedByDescending { it.probability }
        .take(9)
}

private fun probabilityCommentLines(
    prediction: PredictionEntity,
    dossier: StructuredAnalysisDossier,
): List<String> {
    val mainLabel = cleanDisplayText(prediction.selection).canonicalNameKey()
    return buildList {
        addAll(dossier.globalProbabilities)
        addAll(dossier.participantProbabilities)
        addAll(dossier.playerProbabilities)
        addAll(dossier.participantBlocks.flatMap { it.probabilities })
        addAll(dossier.actorBlocks.flatMap { it.probabilities })
    }
        .filterNot(::isActionableProbabilityLine)
        .filterNot { cleanDisplayText(it.label).canonicalNameKey() == mainLabel }
        .map { cleanDisplayText(it.label) }
        .filter(::isUsefulProbabilityNote)
        .distinctBy { it.canonicalNameKey() }
        .take(3)
}

private fun isUsefulProbabilityNote(value: String): Boolean {
    val text = cleanDisplayText(value).canonicalNameKey()
    if (text.isBlank()) return false
    if (listOf(
            "a recouper",
            "a recalculer",
            "a verifier",
            "a surveiller",
            "a confirmer",
            "attendre",
            "probabilite renforcee",
            "source publique",
            "sources publiques",
            "multi source",
            "concordent",
            "rythme meteo",
        ).any { it in text }
    ) return false
    return listOf(
        "bless",
        "absent",
        "suspend",
        "forfait",
        "retour",
        "fatigue",
        "carton",
        "meteo",
        "pluie",
        "vent",
        "terrain",
        "surface",
    ).any { it in text }
}

private fun isActionableProbabilityLine(scenario: StructuredProbabilityLine): Boolean {
    val label = cleanDisplayText(scenario.label)
    val type = cleanDisplayText(scenario.type)
    val text = "$type $label".lowercase()
    if (text.contains("probabilité renforcée") || text.contains("probabilite renforcee")) return false
    if (text.contains("source publique") || text.contains("multi-source")) return false
    if (listOf("à recouper", "a recouper", "à recalculer", "a recalculer", "à vérifier", "a verifier", "à surveiller", "a surveiller", "à intégrer", "a integrer", "attendre", "confirmer").any { it in text }) {
        return false
    }
    val hasMarketNumber = Regex("""\b\d+([,.]\d+)?\s*\+?""").containsMatchIn(label)
    val hasScore = Regex("""\b\d+\s*[—-]\s*\d+\b""").containsMatchIn(label)
    val hasPlayerAction = listOf(
        "marque",
        "but",
        "passe décisive",
        "passe decisive",
        "but ou passe",
        "but + passe",
        "essai",
        "points",
        "rebonds",
        "passes",
        "home run",
        "coups sûrs",
        "coups surs",
        "aces",
        "break",
        "service",
    ).any { it in text }
    val hasTeamMarket = listOf(
        "plus de",
        "moins de",
        "over",
        "under",
        "btts",
        "les deux équipes marquent",
        "les deux equipes marquent",
        "corners",
        "score",
        "victoire",
        "match nul",
        "sets",
        "jeux",
        "tie-break",
        "tiebreak",
    ).any { it in text }
    return hasScore || hasMarketNumber || hasPlayerAction || hasTeamMarket
}

private fun <T> List<T>.firstNamed(name: String): T? = firstOrNull { item ->
    val itemName = when (item) {
        is StructuredParticipantBlock -> item.name
        is StructuredSituationBlock -> item.name
        is StructuredCompetitionStakeBlock -> item.name
        is StructuredFormTrendBlock -> item.name
        is StructuredReliabilityBlock -> item.name
        is StructuredParticipantStatRequirementBlock -> item.name
        else -> ""
    }
    itemName.canonicalNameKey() == name.canonicalNameKey()
}

private fun matchImpactNotes(dossier: StructuredAnalysisDossier, max: Int): List<String> {
    val names = dossier.participantBlocks.map { it.name }
        .plus(dossier.participantSituationBlocks.map { it.name })
        .distinctBy { it.canonicalNameKey() }
    val participantNotes = names.flatMap { name ->
        val participant = dossier.participantBlocks.firstNamed(name)
        val form = dossier.formTrendBlocks.firstNamed(name)
        val important = dossier.importantInfoBlocks.firstNamed(name)
        val competition = dossier.competitionStakeBlocks.firstNamed(name)
        val situation = dossier.participantSituationBlocks.firstNamed(name)
        participantImpactLines(
            name = name,
            stats = participant?.stats.orEmpty(),
            trends = form?.trends.orEmpty(),
            important = important?.items.orEmpty().map { "${it.category} : ${it.text}" },
            competition = competition?.forecastImpact.orEmpty(),
            situation = situation?.lines.orEmpty(),
        )
    }
    val actorNotes = dossier.actorBlocks.flatMap { block ->
        val usefulInfo = block.importantInfo.map { "${block.name} : ${it.category} - ${it.text}" }
        val usefulProbabilities = block.probabilities
            .filter(::isActionableProbabilityLine)
            .map { "${block.name} : ${cleanDisplayText(it.label)} (${formatPercent(it.probability)})" }
        usefulInfo + usefulProbabilities
    }
    return compactDigestLines(
        name = "",
        lines = participantNotes + actorNotes + dossier.rawStats,
        max = max,
    )
}

private fun participantImpactLines(
    name: String,
    stats: List<String>,
    trends: List<String>,
    important: List<String>,
    competition: List<String>,
    situation: List<String>,
): List<String> {
    val cleanName = cleanDisplayText(name)
    val evidence = (stats + trends + important + competition + situation)
        .map(::cleanDisplayText)
        .filter { it.isNotBlank() }
    return buildList {
        productionImpactLine(cleanName, evidence)?.let { add(it) }
        shotVolumeImpactLine(cleanName, evidence)?.let { add(it) }
        important.firstOrNull(::isAvailabilityImpactLine)?.let { add("Disponibilité : ${stripDigestPrefix(cleanName, it)}") }
        important.firstOrNull(::isDisciplineImpactLine)?.let { add("Discipline : ${stripDigestPrefix(cleanName, it)}") }
        trends.firstOrNull(::isConcreteTrendLine)?.let { add("Dynamique : ${stripDigestPrefix(cleanName, it)}") }
        competition.firstOrNull(::isConcreteCompetitionImpactLine)?.let { add(stripDigestPrefix(cleanName, it)) }
    }
        .map { cleanDisplayText(it).replace(Regex("\\s+"), " ").trim(' ', '·', '-', ':') }
        .filter(::isUsefulDigestLine)
        .distinctBy { digestLineKey(cleanName, it) }
}

private data class ProductionImpact(val scored: Double?, val conceded: Double?, val unit: String)

private fun productionImpactLine(name: String, lines: List<String>): String? {
    val stat = lines.firstNotNullOfOrNull(::extractProductionImpact) ?: return null
    val unit = stat.unit
    val scored = stat.scored
    val conceded = stat.conceded
    val parts = buildList {
        scored?.let {
            val label = when {
                unit == "points" && it >= 30.0 -> "attaque productive"
                unit == "points" && it <= 18.0 -> "attaque limitée"
                unit != "points" && it >= 2.0 -> "attaque productive"
                unit != "points" && it <= 1.1 -> "attaque limitée"
                else -> "production offensive correcte"
            }
            add("$label : ${decimalText(it)} $unit marqués/match")
        }
        conceded?.let {
            val label = when {
                unit == "points" && it >= 28.0 -> "défense exposée"
                unit == "points" && it <= 18.0 -> "défense solide"
                unit != "points" && it >= 1.5 -> "défense exposée"
                unit != "points" && it <= 0.8 -> "défense solide"
                else -> "défense moyenne"
            }
            add("$label : ${decimalText(it)} $unit encaissés/match")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ; ")?.let { "$name : $it." }
}

private fun extractProductionImpact(line: String): ProductionImpact? {
    val normalized = cleanDisplayText(line).lowercase()
    val scoredMatch = Regex("""(\d+(?:[,.]\d+)?)\s*(buts|points|runs|essais)\s+marqu""").find(normalized)
    val slashPoints = Regex("""(\d+(?:[,.]\d+)?)\s*points\s+marqu[^/]+/\s*(\d+(?:[,.]\d+)?)\s*points\s+encaiss""").find(normalized)
    val scored = scoredMatch?.groupValues?.getOrNull(1)?.parseFrenchDouble()
        ?: slashPoints?.groupValues?.getOrNull(1)?.parseFrenchDouble()
    val unit = scoredMatch?.groupValues?.getOrNull(2)
        ?: slashPoints?.let { "points" }
        ?: return null
    val conceded = Regex("""(\d+(?:[,.]\d+)?)\s*(?:buts|points|runs|essais)?\s*encaiss""")
        .find(normalized.substringAfter(scoredMatch?.value ?: ""))
        ?.groupValues
        ?.getOrNull(1)
        ?.parseFrenchDouble()
        ?: slashPoints?.groupValues?.getOrNull(2)?.parseFrenchDouble()
    if (scored == null && conceded == null) return null
    return ProductionImpact(scored = scored, conceded = conceded, unit = unit)
}

private fun shotVolumeImpactLine(name: String, lines: List<String>): String? {
    val match = lines.asSequence()
        .map(::cleanDisplayText)
        .mapNotNull { line ->
            Regex("""(\d+(?:[,.]\d+)?)\s*tirs?\s+dont\s+(\d+(?:[,.]\d+)?)\s*cadr""", RegexOption.IGNORE_CASE)
                .find(line)
        }
        .firstOrNull() ?: return null
    val shots = match.groupValues[1].parseFrenchDouble() ?: return null
    val onTarget = match.groupValues[2].parseFrenchDouble() ?: return null
    val label = when {
        shots <= 8.0 || onTarget <= 3.0 -> "volume offensif faible"
        shots >= 15.0 || onTarget >= 5.0 -> "volume offensif élevé"
        else -> "volume offensif moyen"
    }
    return "$name : $label, ${decimalText(shots)} tirs dont ${decimalText(onTarget)} cadrés."
}

private fun isAvailabilityImpactLine(value: String): Boolean {
    val text = value.canonicalNameKey()
    return listOf("bless", "absent", "absence", "indisponible", "doubtful", "suspend", "retour de blessure", "malade", "forfait").any { it in text }
}

private fun isDisciplineImpactLine(value: String): Boolean {
    val text = value.canonicalNameKey()
    return listOf("carton", "suspension", "discipline").any { it in text }
}

private fun isConcreteTrendLine(value: String): Boolean {
    val text = value.canonicalNameKey()
    return !text.contains("controle pdf") &&
        !text.contains("non determinee") &&
        !text.contains("insuffisante") &&
        !text.contains("a confirmer") &&
        listOf("hausse", "baisse", "positive", "negative", "irreguliere", "victoires", "defaites", "fatigue").any { it in text }
}

private fun isConcreteCompetitionImpactLine(value: String): Boolean {
    val text = value.canonicalNameKey()
    if (text.contains("pression") || text.contains("motivation") || text.contains("mental")) return false
    return listOf("volume offensif", "tirs", "attaques", "espaces", "rotation", "composition", "solidite", "defens", "over", "points", "buts").any { it in text }
}

private fun stripDigestPrefix(name: String, value: String): String {
    val participant = cleanDisplayText(name)
    return cleanDisplayText(value)
        .replace(Regex("^${Regex.escape(participant)}\\s*[:·-]\\s*", RegexOption.IGNORE_CASE), "")
        .replace("Infirmerie / disponibilité :", "")
        .replace("Discipline / suspension :", "")
        .replace("Rotation / composition :", "")
        .trim(' ', '·', '-', ':')
}

private fun String.parseFrenchDouble(): Double? =
    replace(',', '.').toDoubleOrNull()

private fun decimalText(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.FRANCE, "%.1f", value)

private fun compactDigestLines(name: String, lines: List<String>, max: Int): List<String> =
    lines
        .map(::cleanDisplayText)
        .mapNotNull { scopeLineToParticipant(name, it) }
        .map { simplifyDigestLine(name, it) }
        .filter(::isUsefulConcreteDigestLine)
        .distinctBy { concreteDigestLineKey(name, it) }
        .take(max)

private fun scopeLineToParticipant(name: String, value: String): String? {
    val cleaned = cleanDisplayText(value)
    if (name.isBlank()) return cleaned
    if (!cleaned.contains("indisponibil", ignoreCase = true) && !cleaned.contains("bless", ignoreCase = true)) return cleaned
    val markers = Regex("""(^|[,;]\s*)([\p{L}\p{M} .'\-]{3,45})\s*:\s*""")
        .findAll(cleaned)
        .toList()
    val ownIndex = markers.indexOfFirst { match ->
        val label = match.groupValues[2].trim()
        labelMatchesParticipant(label, name)
    }
    if (ownIndex < 0) {
        val mentionedTeams = markers.map { it.groupValues[2].trim() }
            .filterNot { it.contains("indisponibil", ignoreCase = true) || it.contains("disponibil", ignoreCase = true) || it.contains("bless", ignoreCase = true) }
        return if (mentionedTeams.any { !labelMatchesParticipant(it, name) }) null else cleaned
    }
    val ownMarker = markers[ownIndex]
    val nextMarkerStart = markers.drop(ownIndex + 1).firstOrNull()?.range?.first ?: cleaned.length
    val details = cleaned.substring(ownMarker.range.last + 1, nextMarkerStart)
        .trim(' ', ',', ';', ':', '·')
    if (details.isBlank()) return null
    return "Indisponibilités : $details"
}

private fun labelMatchesParticipant(label: String, participant: String): Boolean {
    val labelKey = label.canonicalNameKey()
    val translatedLabelKey = displayTeamName(label).canonicalNameKey()
    val participantKey = participant.canonicalNameKey()
    val translatedParticipantKey = displayTeamName(participant).canonicalNameKey()
    return labelKey == participantKey ||
        translatedLabelKey == participantKey ||
        labelKey == translatedParticipantKey ||
        translatedLabelKey == translatedParticipantKey
}

private fun simplifyDigestLine(name: String, value: String): String {
    val participant = cleanDisplayText(name)
    return cleanDisplayText(value)
        .replace("Situation compétition :", "")
        .replace("Repère brut :", "")
        .replace("Forme/confiance :", "")
        .replace("Info clé :", "")
        .replace("$participant :", "")
        .replace("Dynamique $participant :", "Dynamique :")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '·', '-', ':')
}

private fun isUsefulConcreteDigestLine(value: String): Boolean {
    val key = value.canonicalNameKey()
    if (key.isBlank()) return false
    if (listOf(
            "role",
            "lecture situation",
            "impact pronostic a confirmer",
            "faute de preuve",
            "non prouve",
            "enjeu exact",
            "pression",
            "motivation",
            "modele tient compte",
            "controle pdf",
            "stats attendues",
            "a consolider",
            "a confirmer",
            "a recouper",
            "a recalculer",
            "a verifier",
            "a surveiller",
            "attendre",
            "prudence",
            "signal fort seulement",
            "sources concordent",
            "comparer",
            "aucune donnee",
            "donnees actuelles",
        ).any { it in key }
    ) return false
    return hasConcreteDigestEvidence(key)
}

private fun hasConcreteDigestEvidence(key: String): Boolean =
    Regex("""\b\d+(?:[,.]\d+)?\b""").containsMatchIn(key) ||
        listOf(
            "attaque productive",
            "attaque limitee",
            "production offensive",
            "defense solide",
            "defense exposee",
            "volume offensif",
            "buts marques",
            "points marques",
            "encaisses",
            "tirs",
            "cadres",
            "bless",
            "absent",
            "absence",
            "indisponible",
            "doubtful",
            "suspend",
            "forfait",
            "retour de blessure",
            "carton",
            "hausse",
            "baisse",
            "irreguliere",
            "victoires",
            "defaites",
            "fatigue",
            "rotation",
        ).any { it in key }

private fun concreteDigestLineKey(name: String, value: String): String {
    val lineKey = simplifyDigestLine(name, value).canonicalNameKey()
    val nameKey = name.canonicalNameKey()
    return if (nameKey.isBlank()) lineKey else lineKey.replace(nameKey, "")
}

private fun isUsefulDigestLine(value: String): Boolean {
    val text = value.lowercase()
    return value.isNotBlank() &&
        !text.startsWith("rôle") &&
        !text.contains("impact pronostic à confirmer") &&
        !text.contains("faute de preuve") &&
        !text.contains("non prouvé") &&
        !text.contains("enjeu exact") &&
        !text.contains("lecture situation") &&
        !text.contains("le modèle tient compte")
}

private fun digestLineKey(name: String, value: String): String =
    simplifyDigestLine(name, value)
        .canonicalNameKey()
        .replace(name.canonicalNameKey(), "")
        .replace("domicile exterieur", "domicile extérieur")

private fun String.canonicalNameKey(): String =
    Normalizer.normalize(cleanDisplayText(this).lowercase(Locale.FRANCE), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace("é", "e")
        .replace("è", "e")
        .replace("ê", "e")
        .replace("à", "a")
        .replace("â", "a")
        .replace("î", "i")
        .replace("ï", "i")
        .replace("ô", "o")
        .replace("ù", "u")
        .replace("ç", "c")
        .replace(Regex("[^a-z0-9,./%]+"), " ")
        .trim()

@Composable
private fun CloudAiReadingCard(reading: CloudAiReading, accent: Color) {
    Surface(shape = RoundedCornerShape(22.dp), color = accent.copy(alpha = 0.11f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag(reading.statusLabel, accent)
                Text(
                    cleanDisplayText(reading.providerLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
            }
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        cleanDisplayText(reading.headline),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    if (reading.confidenceLabel.isNotBlank()) {
                        Text("Confiance IA : ${reading.confidenceLabel}", style = MaterialTheme.typography.labelLarge, color = accent)
                    }
                }
            }

            reading.sections.forEach { section ->
                val color = when {
                    section.title.contains("Avantage", ignoreCase = true) -> Amber
                    section.title.contains("Danger", ignoreCase = true) -> Danger
                    section.title.contains("Scénario", ignoreCase = true) -> accent
                    section.title.contains("Confiance", ignoreCase = true) -> Blue
                    else -> Mint
                }
                LocalAiMiniBlock(section.title, section.lines, color, maxLines = 4)
            }
            if (reading.diagnosticLines.isNotEmpty()) {
                LocalAiMiniBlock("Sources IA", reading.diagnosticLines, Blue, maxLines = 4)
            }
        }
    }
}

@Composable
private fun CloudAiPendingCard(prediction: PredictionEntity, accent: Color, reason: String) {
    val requestStatus = cloudAiRequestStatus(prediction)
    val requestMessage = requestStatus?.second.orEmpty()
    val requestSent = requestStatus?.first == "requested"
    val title = if (requestSent) "Analyse IA demandee" else "IA cloud indisponible"
    val subtitle = requestMessage.ifBlank { if (requestSent) "file IA prioritaire" else "diagnostic disponible" }
    Surface(shape = RoundedCornerShape(22.dp), color = accent.copy(alpha = 0.10f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag(title, accent)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
            }
            Text(
                if (requestSent) {
                    "Cette fiche est dans la file IA cloud prioritaire. Le prochain passage GitHub Actions doit produire l'analyse externe si le quota repond."
                } else {
                    "Aucune analyse IA cloud valide n'est encore rattachee a cette fiche."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Diagnostic : $reason.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            val source = cleanDisplayText(prediction.sourceName)
                .ifBlank { cleanDisplayText(prediction.sourceDetails) }
                .ifBlank { "sources sport en consolidation" }
            Text(
                "Source actuelle : $source",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LocalAiMiniBlock(title: String, lines: List<String>, color: Color, maxLines: Int = 3) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Black)
            lines.take(maxLines).forEach { line ->
                Text("• ${cleanCloudAiDisplayText(line)}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PronosticExpressCard(
    prediction: PredictionEntity,
    accent: Color,
) {
    val nonActionable = prediction.isNonActionableAnalysis()
    val title = if (nonActionable) "À surveiller" else "À jouer"
    val chanceValue = if (nonActionable) "Non chiffré" else formatPercent(prediction.consensusProbability)
    val chanceLabel = if (nonActionable) "Statut" else "Chance"
    val expectedLabel = prediction.expectedStateLabel(nonActionable)
    Surface(shape = RoundedCornerShape(22.dp), color = accent.copy(alpha = 0.13f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Black)
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        cleanDisplayText(prediction.selection),
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                        fontWeight = FontWeight.Black,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(cleanDisplayText(prediction.market), color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ExpressMetric(chanceLabel, chanceValue, accent, Modifier.weight(1f))
                ExpressMetric("Confiance", "${prediction.confidenceScore}/100", Blue, Modifier.weight(1f))
            }
            if (prediction.expectedScore.isNotBlank()) {
                Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(expectedLabel, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                        Text(
                            cleanDisplayText(prediction.expectedScore),
                            color = accent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun PredictionEntity.isNonActionableAnalysis(): Boolean {
    val categoryKey = predictionCategoryKey(category)
    val text = cleanDisplayText("$category $market $selection $expectedScore").lowercase(Locale.FRANCE)
    return categoryKey == "exotique" && listOf(
        "données faibles",
        "donnees faibles",
        "données à compléter",
        "donnees a completer",
        "surveillance",
        "projection impossible",
        "attendre",
        "pas de ",
        "non validée",
        "non validee",
    ).any { it in text }
}

private fun PredictionEntity.expectedStateLabel(nonActionable: Boolean): String {
    if (nonActionable) return "État attendu"
    return when (sportKey.substringBefore('/')) {
        "tennis" -> "Format probable"
        "cycling", "racing", "nascar", "golf", "athletics" -> "Projection / état"
        "rugby", "basketball", "football", "handball", "volleyball", "baseball", "hockey" -> "Score / total"
        else -> "Score final"
    }
}

@Composable
private fun ExpressMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.11f)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ParticipantDigestCard(digest: ParticipantDigest, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cleanDisplayText(digest.name), style = MaterialTheme.typography.titleMedium, color = color)
            digest.lines.forEach { Text("• $it", color = TextSecondary) }
        }
    }
}

@Composable
private fun ActorDigestCard(digest: ActorDigest, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cleanDisplayText(digest.name), style = MaterialTheme.typography.titleMedium, color = color)
            if (digest.role.isNotBlank()) {
                Text(cleanDisplayText(digest.role), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            digest.probabilities.forEach { scenario -> ScenarioProbabilityRow(scenario, color) }
            digest.lines.forEach { Text("• $it", color = TextSecondary) }
        }
    }
}

@Composable
private fun PressInfoCard(digest: PressInfoDigest, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.10f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(cleanDisplayText(digest.title), style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
                if (digest.level.isNotBlank()) {
                    Text(cleanDisplayText(digest.level), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }
            digest.lines.forEach { line ->
                Text("• ${cleanDisplayText(line)}", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun LineupPitchCard(team: LineupTeamView, sportKey: String, color: Color, language: String) {
    val rows = lineupRows(team, sportKey)
    val sport = sportKey.substringBefore('/')
    val fieldHeight = when (sport) {
        "rugby" -> 310.dp
        "basketball", "baseball", "hockey", "volleyball", "handball" -> 280.dp
        else -> 430.dp
    }
    Surface(shape = RoundedCornerShape(20.dp), color = SurfaceHigh.copy(alpha = 0.88f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(team.teamName, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            team.status.takeIf { it.isNotBlank() },
                            team.formation?.let { t(language, "Formation $it", "Formation $it", "Formación $it", "Formation $it") },
                        ).joinToString(" · ").ifBlank { t(language, "Composition détectée", "Lineup detected", "Alineación detectada", "Aufstellung erkannt") },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(fieldHeight)
                    .background(
                        Color(0xFF0A5F1A),
                        RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 14.dp),
            ) {
                LineupFieldBackground(sport)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            row.forEach { player ->
                                LineupPlayerChip(player, sportKey, language, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Text(lineupTacticalReading(team, sportKey, language), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun LineupFieldBackground(sport: String) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (sport == "rugby") drawRugbyField() else drawFootballField()
    }
}

private fun DrawScope.drawFootballField() {
    val stripeCount = 12
    val stripeHeight = size.height / stripeCount
    repeat(stripeCount) { index ->
        drawRect(
            color = if (index % 2 == 0) Color(0xFF129A1B) else Color(0xFF0D8616),
            topLeft = Offset(0f, index * stripeHeight),
            size = Size(size.width, stripeHeight),
        )
    }
    val line = Color.White.copy(alpha = 0.86f)
    val stroke = Stroke(width = 4f)
    val pad = 14f
    drawRect(line, topLeft = Offset(pad, pad), size = Size(size.width - pad * 2, size.height - pad * 2), style = stroke)
    drawLine(line, Offset(pad, size.height / 2f), Offset(size.width - pad, size.height / 2f), strokeWidth = 4f)
    drawCircle(line, radius = size.width * 0.13f, center = Offset(size.width / 2f, size.height / 2f), style = stroke)
    drawCircle(line, radius = 5f, center = Offset(size.width / 2f, size.height / 2f))

    val boxW = size.width * 0.54f
    val boxH = size.height * 0.12f
    drawRect(line, topLeft = Offset((size.width - boxW) / 2f, pad), size = Size(boxW, boxH), style = stroke)
    drawRect(line, topLeft = Offset((size.width - boxW) / 2f, size.height - pad - boxH), size = Size(boxW, boxH), style = stroke)
    val smallW = size.width * 0.28f
    val smallH = size.height * 0.06f
    drawRect(line, topLeft = Offset((size.width - smallW) / 2f, pad), size = Size(smallW, smallH), style = stroke)
    drawRect(line, topLeft = Offset((size.width - smallW) / 2f, size.height - pad - smallH), size = Size(smallW, smallH), style = stroke)
    drawCircle(line, radius = 5f, center = Offset(size.width / 2f, pad + boxH * 0.62f))
    drawCircle(line, radius = 5f, center = Offset(size.width / 2f, size.height - pad - boxH * 0.62f))
}

private fun DrawScope.drawRugbyField() {
    val stripeCount = 10
    val stripeHeight = size.height / stripeCount
    repeat(stripeCount) { index ->
        drawRect(
            color = if (index % 2 == 0) Color(0xFF079610) else Color(0xFF05820D),
            topLeft = Offset(0f, index * stripeHeight),
            size = Size(size.width, stripeHeight),
        )
    }
    val line = Color.White.copy(alpha = 0.88f)
    val stroke = Stroke(width = 4f)
    val pad = 12f
    drawRect(line, topLeft = Offset(pad, pad), size = Size(size.width - pad * 2, size.height - pad * 2), style = stroke)
    listOf(0.10f, 0.27f, 0.50f, 0.73f, 0.90f).forEach { ratio ->
        drawLine(line, Offset(pad, size.height * ratio), Offset(size.width - pad, size.height * ratio), strokeWidth = if (ratio == 0.50f) 4f else 3f)
    }
    listOf(0.22f, 0.50f, 0.78f).forEach { x ->
        drawLine(line.copy(alpha = 0.65f), Offset(size.width * x, pad), Offset(size.width * x, size.height - pad), strokeWidth = 3f)
    }
    listOf(0.18f, 0.82f).forEach { x ->
        drawLine(line, Offset(size.width * x, pad), Offset(size.width * x, pad + 35f), strokeWidth = 4f)
        drawLine(line, Offset(size.width * x, size.height - pad), Offset(size.width * x, size.height - pad - 35f), strokeWidth = 4f)
    }
}

@Composable
private fun LineupPlayerChip(player: LineupPlayerView, sportKey: String, language: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = Color.Black.copy(alpha = 0.34f)) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(cleanDisplayText(player.name), color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(player.number, player.position).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(cleanDisplayText(meta), color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(playerRoleProfile(player, sportKey, language), color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProbabilityNotesCard(notes: List<String>) {
    Surface(shape = RoundedCornerShape(16.dp), color = Amber.copy(alpha = 0.10f)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Infos de contexte", style = MaterialTheme.typography.labelLarge, color = Amber)
            notes.forEach { note ->
                Text("• ${cleanDisplayText(note)}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun CompetitionStakeBlockCard(block: StructuredCompetitionStakeBlock, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(cleanDisplayText(block.name), style = MaterialTheme.typography.titleMedium, color = color)
            Text(
                "Enjeu : ${block.statuses.joinToString(", ") { cleanDisplayText(it) }}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            compactUiLines(block.forecastImpact + block.reading, max = 2).forEach { line ->
                Text("• $line", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SourceCoverageLine(line: StructuredSourceCoverageLine, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(cleanDisplayText(line.family), style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.weight(1f))
            Text(
                cleanDisplayText(line.status),
                style = MaterialTheme.typography.labelMedium,
                color = if (line.status == "Couvert") Mint else Amber,
            )
        }
        Text(cleanDisplayText(line.detail), color = TextSecondary)
    }
}

@Composable
private fun SourceCoverageSummary(lines: List<StructuredSourceCoverageLine>, color: Color) {
    val covered = lines.count { it.status == "Couvert" }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Sources : $covered/${lines.size} familles utilisées", style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun ActorStatRequirementCard(block: StructuredActorStatRequirementBlock, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cleanDisplayText(block.name), style = MaterialTheme.typography.titleMedium, color = color)
            if (block.teamOrRole.isNotBlank()) {
                Text(cleanDisplayText(block.teamOrRole), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            val visible = compactUiLines(block.missingStats, max = 4)
            Text(
                if (block.missingStats.size > visible.size) "À recouper : ${visible.joinToString(", ")} +${block.missingStats.size - visible.size}"
                else "À recouper : ${visible.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun ParticipantStatRequirementCard(block: StructuredParticipantStatRequirementBlock, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cleanDisplayText(block.name), style = MaterialTheme.typography.titleMedium, color = color)
            val visible = block.missingStats.take(4).map(::cleanDisplayText)
            Text(
                if (block.missingStats.size > visible.size) "À recouper : ${visible.joinToString(", ")} +${block.missingStats.size - visible.size}"
                else "À recouper : ${visible.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun ParticipantScenarioHintBlockCard(block: StructuredParticipantScenarioHintBlock, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cleanDisplayText(block.name), style = MaterialTheme.typography.titleMedium, color = color)
            block.hints.take(5).forEach { hint -> SportScenarioHintRow(hint, color) }
        }
    }
}

@Composable
private fun SportScenarioHintRow(hint: StructuredSportScenarioHint, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cleanDisplayText(hint.label), style = MaterialTheme.typography.bodyLarge, color = color)
                Text(cleanDisplayText(hint.type), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            Text("info secondaire", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator(
            progress = { hint.priority.coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = SurfaceHigh,
        )
    }
}

@Composable
private fun GlobalMarketRequirementLine(requirement: StructuredGlobalMarketRequirement, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(cleanDisplayText(requirement.family), style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.weight(1f))
            Text(
                cleanDisplayText(requirement.status),
                style = MaterialTheme.typography.labelMedium,
                color = if (requirement.status == "Couvert") Mint else Amber,
            )
        }
        Text(cleanDisplayText(requirement.markets), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun ExplanationBlockCard(block: StructuredExplanationBlock, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cleanDisplayText(block.title), style = MaterialTheme.typography.titleMedium, color = color)
            compactUiLines(block.lines, max = 3).forEach { Text("• $it", color = TextSecondary) }
        }
    }
}

@Composable
private fun SituationBlockCard(block: StructuredSituationBlock, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cleanDisplayText(block.name), style = MaterialTheme.typography.titleMedium, color = color)
            compactUiLines(block.lines.filterNot(::isNoisySituationLine), max = 3).forEach { Text("• $it", color = TextSecondary) }
        }
    }
}

@Composable
private fun ReliabilityBlockCard(block: StructuredReliabilityBlock, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cleanDisplayText(block.name), style = MaterialTheme.typography.titleMedium, color = color)
            block.items.take(2).forEach { ReliabilityInfoLine(it, color) }
        }
    }
}

@Composable
private fun FormTrendBlockCard(block: StructuredFormTrendBlock, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cleanDisplayText(block.name), style = MaterialTheme.typography.titleMedium, color = color)
            compactUiLines(block.trends, max = 2).forEach { Text("• $it", color = TextSecondary) }
        }
    }
}

@Composable
private fun ReliabilityInfoLine(info: StructuredReliabilityLine, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("• ${cleanDisplayText(info.category)} · ${cleanDisplayText(info.level)}", style = MaterialTheme.typography.labelMedium, color = color)
        Text(cleanDisplayText(info.text), color = TextSecondary)
    }
}

@Composable
private fun ParticipantBlockCard(
    block: StructuredParticipantBlock,
    showStats: Boolean = true,
    showProbabilities: Boolean = true,
    color: Color,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cleanDisplayText(block.name), style = MaterialTheme.typography.titleMedium, color = color)
            if (showStats && block.stats.isNotEmpty()) {
                Text("Statistiques brutes", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                compactUiLines(block.stats, max = 3).forEach { Text("• $it", color = TextSecondary) }
            }
            if (showProbabilities && block.probabilities.isNotEmpty()) {
                Text("Probabilités du participant", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                block.probabilities.forEach { scenario -> ScenarioProbabilityRow(scenario, color) }
            }
        }
    }
}

@Composable
private fun ActorBlockCard(block: StructuredActorBlock, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.86f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cleanDisplayText(block.name), style = MaterialTheme.typography.titleMedium, color = color)
            if (block.teamOrRole.isNotBlank()) {
                Text(cleanDisplayText(block.teamOrRole), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            if (block.situation.isNotEmpty()) {
                Text("Situation", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                compactUiLines(block.situation, max = 2).forEach { Text("• $it", color = TextSecondary) }
            }
            if (block.stats.isNotEmpty()) {
                Text("Statistiques brutes", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                compactUiLines(block.stats, max = 3).forEach { Text("• $it", color = TextSecondary) }
            }
            if (block.importantInfo.isNotEmpty()) {
                Text("Infos importantes", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                block.importantInfo.take(2).forEach { ReliabilityInfoLine(it, color) }
            }
            if (block.probabilities.isNotEmpty()) {
                Text("Probabilités", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                block.probabilities.take(4).forEach { scenario -> ScenarioProbabilityRow(scenario, color) }
            }
        }
    }
}

@Composable
private fun LineupTeam(team: String, status: String, players: List<LineupPlayerView>) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(displayTeamName(team), style = MaterialTheme.typography.titleMedium, color = Blue)
                Text(status, style = MaterialTheme.typography.labelMedium, color = if (status.startsWith("Officielle")) Mint else Amber)
            }
            players.forEach { player ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        listOfNotNull(player.number, player.name).joinToString("  "),
                        modifier = Modifier.weight(1f),
                        color = if (player.number != null) Mint else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    player.position?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun ScenarioProbabilityRow(scenario: StructuredProbabilityLine, color: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceHigh.copy(alpha = 0.70f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(cleanDisplayText(scenario.label), style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(cleanDisplayText(scenario.type), style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatPercent(scenario.probability), color = color, style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(
                progress = { scenario.probability.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@Composable
private fun PredictionSection(title: String, icon: ImageVector, color: Color, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(color.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
                }
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}

@Composable
private fun PredictionMetric(label: String, value: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

private fun compactUiLines(lines: List<String>, max: Int): List<String> =
    lines
        .map(::cleanDisplayText)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy(::compactLineKey)
        .take(max)

private fun compactLineKey(value: String): String =
    cleanDisplayText(value)
        .lowercase()
        .replace("situation compétition :", "")
        .replace("info clé :", "")
        .replace("forme/confiance :", "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun isNoisySituationLine(value: String): Boolean {
    val text = cleanDisplayText(value).lowercase()
    return text.startsWith("rôle :") ||
        text.startsWith("lecture situation") ||
        text.contains("pronostic est volontairement") ||
        text.contains("le modèle tient compte") ||
        text.contains("domicile/extérieur") && text.contains(",") && text.length > 90
}
