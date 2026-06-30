package com.soliano.betvalueanalyzer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Scoreboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soliano.betvalueanalyzer.data.local.LiveEventEntity
import com.soliano.betvalueanalyzer.domain.LocalAiReading
import com.soliano.betvalueanalyzer.domain.LocalAnalysisAssistant
import com.soliano.betvalueanalyzer.ui.components.cleanDisplayText
import com.soliano.betvalueanalyzer.ui.components.displayTeamName
import com.soliano.betvalueanalyzer.ui.components.formatDate
import com.soliano.betvalueanalyzer.ui.components.formatPercent
import com.soliano.betvalueanalyzer.ui.components.hasConfirmedScore
import com.soliano.betvalueanalyzer.ui.components.hasLiveMainMetric
import com.soliano.betvalueanalyzer.ui.components.isResultBoardEvent
import com.soliano.betvalueanalyzer.ui.components.liveMainMetricLabel
import com.soliano.betvalueanalyzer.ui.components.liveMainMetricTag
import com.soliano.betvalueanalyzer.ui.components.liveMainMetricText
import com.soliano.betvalueanalyzer.ui.components.liveParticipantsText
import com.soliano.betvalueanalyzer.ui.components.liveProgressLabel
import com.soliano.betvalueanalyzer.ui.components.liveProgressText
import com.soliano.betvalueanalyzer.ui.theme.Amber
import com.soliano.betvalueanalyzer.ui.theme.Blue
import com.soliano.betvalueanalyzer.ui.theme.Divider
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary
import com.soliano.betvalueanalyzer.ui.theme.Violet

@Composable
fun LiveDetailScreen(
    event: LiveEventEntity,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val accent = if (event.isLive || event.hasLiveMainMetric()) Mint else liveDetailSportAccent(event.sportKey)
    val aiReading = remember(event) { LocalAnalysisAssistant.explainLive(event) }
    val scenarioLines = event.scenarios.toLiveDetailScenarios()
    val statLines = event.statSummary.lines().map { it.trim() }.filter { it.isNotBlank() }
    val sportPackLines = statLines.filter(::isLiveDetailSportIntelligenceLine)
    val liveStatLines = statLines.filterNot(::isLiveDetailSportIntelligenceLine)
    val sources = event.sourceDetails.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct()
    val isResultBoard = event.isResultBoardEvent()
    val compactLiveStats = liveStatLines
        .filterNot(::isLiveDetailWatchLine)
        .filterNot(::isLiveMetricDuplicateLine)
        .toCompactLiveDetailLines(max = 4)
    val watchLines = (sportPackLines + liveStatLines.filter(::isLiveDetailWatchLine))
        .toCompactLiveDetailLines(max = 3)
        .ifEmpty { liveDetailSportWatchFallback(event.sportKey).take(3) }
    val compactSources = sources.take(4)

    LazyColumn(
        modifier = Modifier.padding(contentPadding).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") }
                Text("DOSSIER LIVE", style = MaterialTheme.typography.labelLarge, color = accent)
                Spacer(Modifier.size(48.dp))
            }
        }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent, border = BorderStroke(1.dp, accent.copy(alpha = 0.36f))) {
                Box(
                    Modifier.background(
                        Brush.linearGradient(listOf(accent.copy(alpha = 0.20f), MaterialTheme.colorScheme.surface, SurfaceHigh.copy(alpha = 0.72f))),
                        RoundedCornerShape(28.dp),
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            LiveDetailTag(event.liveMainMetricTag(), accent)
                            Text(formatDate(event.commenceTime), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                        Text(cleanDisplayText("${event.sportTitle} · ${event.competitionName}"), color = accent, style = MaterialTheme.typography.labelLarge)
                        Text(cleanDisplayText(event.eventName.ifBlank { "${event.homeName} — ${event.awayName}" }), style = MaterialTheme.typography.headlineMedium)
                        Text(cleanDisplayText(event.statusDescription), color = TextSecondary)
                        if (isResultBoard) {
                            LiveDetailResultBoard(event, accent)
                        } else {
                            LiveDetailScoreBoard(event, accent)
                        }
                    }
                }
            }
        }
        item {
            LiveDetailSection(if (isResultBoard) "Classement et avancement" else "Score et temps de jeu", Icons.Outlined.Scoreboard, accent) {
                Text("${event.liveMainMetricLabel()} : ${cleanDisplayText(event.liveMainMetricText())}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text("${event.liveProgressLabel()} : ${cleanDisplayText(event.liveProgressText())}", color = Amber, fontWeight = FontWeight.Bold)
            }
        }
        item {
            LiveDetailSection("Analyse IA live approfondie", Icons.Outlined.Info, accent) {
                LiveLocalAiReadingCard(aiReading, accent)
            }
        }
        if (scenarioLines.isNotEmpty()) {
            item {
                LiveDetailSection("Pronostics live", Icons.AutoMirrored.Outlined.TrendingUp, Mint) {
                    scenarioLines.take(5).forEach { scenario ->
                        LiveDetailScenarioRow(scenario, accent)
                    }
                }
            }
        }
        if (compactLiveStats.isNotEmpty()) {
            item {
                LiveDetailSection("Stats utiles", Icons.Outlined.QueryStats, Blue) {
                    compactLiveStats.forEach { line ->
                        Text("• ${cleanDisplayText(line)}", color = TextSecondary)
                    }
                }
            }
        }
        item {
            LiveDetailSection("A surveiller", Icons.Outlined.Info, Amber) {
                watchLines.forEach { line ->
                    Text("• ${cleanDisplayText(line)}", color = TextSecondary)
                }
            }
        }
        if (sources.isNotEmpty()) {
            item {
                LiveDetailSection("Sources", Icons.Outlined.Language, Blue) {
                    Text("${sources.size} source(s) · MAJ ${formatDate(event.lastUpdate)}", style = MaterialTheme.typography.titleMedium)
                    compactSources.forEach { source ->
                        Text("• ${cleanDisplayText(source)}", color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveLocalAiReadingCard(reading: LocalAiReading, accent: Color) {
    val analyticalSections = reading.sections
        .filterNot { it.title == "Résumé rapide" }
        .take(6)
    Surface(shape = RoundedCornerShape(22.dp), color = accent.copy(alpha = 0.10f), border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                LiveDetailTag(reading.status.label, accent)
                Text("local · recalcul live", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            Text(cleanDisplayText(reading.summary), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(cleanDisplayText(reading.sportVocabulary), style = MaterialTheme.typography.labelMedium, color = TextSecondary)

            analyticalSections.forEach { section ->
                val color = when {
                    section.title.contains("stats", ignoreCase = true) -> Blue
                    section.title.contains("scénario", ignoreCase = true) -> Mint
                    section.title.contains("source", ignoreCase = true) -> Blue
                    section.title.contains("favori", ignoreCase = true) -> Amber
                    section.title.contains("outsider", ignoreCase = true) -> Mint
                    else -> accent
                }
                val max = when {
                    section.title.contains("Analyse IA", ignoreCase = true) -> 5
                    section.title.contains("source", ignoreCase = true) -> 5
                    else -> 4
                }
                LiveLocalAiBlock(section.title, section.lines, color, maxLines = max)
            }

            Text(cleanDisplayText(reading.conclusion), style = MaterialTheme.typography.bodyMedium, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiveLocalAiBlock(title: String, lines: List<String>, color: Color, maxLines: Int = 3) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceHigh.copy(alpha = 0.78f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Black)
            lines.take(maxLines).forEach { line ->
                Text("• ${cleanDisplayText(line)}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LiveDetailScoreBoard(event: LiveEventEntity, accent: Color) {
    Surface(shape = RoundedCornerShape(22.dp), color = SurfaceHigh.copy(alpha = 0.82f), border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(event.liveMainMetricLabel(), style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Black)
            if (event.hasConfirmedScore()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiveDetailTeamScore(displayTeamName(event.homeName), event.homeScore, accent, Modifier.weight(1f))
                    Text("—", color = TextSecondary, fontWeight = FontWeight.Black)
                    LiveDetailTeamScore(displayTeamName(event.awayName), event.awayScore, Violet, Modifier.weight(1f))
                }
            } else {
                Text(event.liveParticipantsText(), color = accent, fontWeight = FontWeight.Bold)
                Text(event.liveMainMetricText(), color = TextSecondary)
            }
            LiveDetailProgressPill(event.liveProgressLabel(), event.liveProgressText(), accent)
        }
    }
}

@Composable
private fun LiveDetailResultBoard(event: LiveEventEntity, accent: Color) {
    Surface(shape = RoundedCornerShape(22.dp), color = SurfaceHigh.copy(alpha = 0.82f), border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(event.liveMainMetricLabel(), style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Black)
            Text(cleanDisplayText(event.liveMainMetricText()), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
            Text(cleanDisplayText(event.liveParticipantsText()), style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LiveDetailProgressPill(event.liveProgressLabel(), event.liveProgressText(), accent)
        }
    }
}

@Composable
private fun LiveDetailProgressPill(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.10f), border = BorderStroke(1.dp, color.copy(alpha = 0.20f))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(cleanDisplayText(value), style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiveDetailTeamScore(name: String, score: Int?, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, color = color, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(score?.toString() ?: "—", style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LiveDetailSection(title: String, icon: ImageVector, color: Color, content: @Composable () -> Unit) {
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
private fun LiveDetailScenarioRow(scenario: LiveDetailScenario, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cleanDisplayText(scenario.label), color = color, style = MaterialTheme.typography.bodyLarge)
                Text(cleanDisplayText(scenario.type), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            Text(formatPercent(scenario.probability), color = color, style = MaterialTheme.typography.titleMedium)
        }
        LinearProgressIndicator(
            progress = { scenario.probability.coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = color,
            trackColor = Divider.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun LiveDetailTag(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f), border = BorderStroke(1.dp, color.copy(alpha = 0.36f))) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
    }
}

private data class LiveDetailScenario(val type: String, val label: String, val probability: Double)

private fun String.toLiveDetailScenarios(): List<LiveDetailScenario> = lines().mapNotNull { line ->
    val parts = line.split('|', limit = 3)
    if (parts.size < 3) return@mapNotNull null
    LiveDetailScenario(parts[0], parts[1], parts[2].toDoubleOrNull() ?: return@mapNotNull null)
}.distinctBy { it.type + it.label }.sortedByDescending { it.probability }

private fun isLiveDetailSportIntelligenceLine(value: String): Boolean =
    value.startsWith("Stats cles surveillees", ignoreCase = true) ||
        value.startsWith("Stats joueurs suivies", ignoreCase = true) ||
        value.startsWith("Contexte a recouper", ignoreCase = true) ||
        value.startsWith("Stats clés surveillées", ignoreCase = true) ||
        value.startsWith("Contexte à recouper", ignoreCase = true)

private fun isLiveDetailWatchLine(value: String): Boolean {
    val text = value.liveDetailLineKey()
    return text.startsWith("a surveiller") ||
        text.startsWith("stats cles") ||
        text.startsWith("contexte a recouper") ||
        text.startsWith("stats joueurs suivies")
}

private fun isLiveMetricDuplicateLine(value: String): Boolean {
    val text = value.liveDetailLineKey()
    return text.startsWith("score live") ||
        text.startsWith("classement/top 3 live") ||
        text.startsWith("top 3 / classement") ||
        text.startsWith("avancement course") ||
        text.startsWith("synthese live") ||
        text.startsWith("projection live")
}

private fun List<String>.toCompactLiveDetailLines(max: Int): List<String> =
    asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { line -> if (line.length > 150) line.take(147).trimEnd() + "..." else line }
        .distinctBy { it.liveDetailLineKey() }
        .take(max)
        .toList()

private fun String.liveDetailLineKey(): String =
    lowercase()
        .replace('\u00e9', 'e')
        .replace('\u00e8', 'e')
        .replace('\u00ea', 'e')
        .replace('\u00eb', 'e')
        .replace('\u00e0', 'a')
        .replace('\u00e2', 'a')
        .replace('\u00f9', 'u')
        .replace('\u00fb', 'u')
        .replace('\u00ee', 'i')
        .replace('\u00ef', 'i')
        .replace('\u00f4', 'o')
        .replace('\u00e7', 'c')
        .substringBefore(" :")
        .substringBefore(" ·")
        .take(56)

private fun liveDetailSportWatchFallback(sportKey: String): List<String> = when (sportKey.substringBefore('/')) {
    "soccer" -> listOf("Score, cartons, changements et tirs cadres.", "Fatigue des titulaires et dynamique match.", "Compositions confirmees des publication.")
    "rugby" -> listOf("Essais, penalites, cartons et conquete.", "Impact du banc et fatigue en fin de match.", "Meteo et occupation du terrain.")
    "tennis" -> listOf("Breaks, service, tie-break et duree du match.", "Fatigue du joueur par rapport a sa saison.", "Blessure, soin medical ou baisse de vitesse.")
    "cycling" -> listOf("Top 3, ecarts, echappee et meteo.", "Chutes, abandons et role d'equipe.", "Profil de l'etape avant projection vainqueur/podium.")
    "racing", "nascar" -> listOf("Top 3, pneus, arrets et incidents.", "Safety car/pluie si confirmee.", "Rythme course avant projection podium.")
    "golf" -> listOf("Leaderboard, round, trous joues et score sous le par.", "Vent, putting et dynamique du dernier tour.", "Top 5/top 10 si ecarts serres.")
    else -> listOf("Score ou classement officiel.", "Stats cles adaptees au sport.", "Fatigue, blessure ou incident confirme.")
}

private fun liveDetailSportAccent(sportKey: String): Color = when (sportKey.substringBefore('/')) {
    "soccer" -> Mint
    "basketball" -> Amber
    "tennis" -> Blue
    "rugby" -> Color(0xFF7CF08A)
    "cycling" -> Color(0xFFFFD447)
    "racing", "nascar" -> Color(0xFFFF7A59)
    "golf" -> Color(0xFF74E0A3)
    "mma", "boxing" -> Color(0xFFFF5E91)
    "hockey", "field_hockey" -> Color(0xFF8FD3FF)
    else -> Violet
}
