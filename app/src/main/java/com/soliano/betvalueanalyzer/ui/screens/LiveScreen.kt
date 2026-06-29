package com.soliano.betvalueanalyzer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soliano.betvalueanalyzer.data.local.LiveEventEntity
import com.soliano.betvalueanalyzer.ui.AppUiState
import com.soliano.betvalueanalyzer.ui.SyncStatus
import com.soliano.betvalueanalyzer.ui.t
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
fun LiveScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    language: String = "fr",
    onRefresh: () -> Unit,
    onOpen: (LiveEventEntity) -> Unit,
) {
    val syncing = state.syncStatus == SyncStatus.Syncing
    val liveNow = state.liveEvents.count { it.isLive }
    LazyColumn(
        modifier = Modifier.padding(contentPadding).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(30.dp), color = Color.Transparent, border = BorderStroke(1.dp, Mint.copy(alpha = 0.32f))) {
                Box(
                    Modifier.background(
                        Brush.linearGradient(listOf(Mint.copy(alpha = 0.20f), Blue.copy(alpha = 0.14f), MaterialTheme.colorScheme.surface)),
                        RoundedCornerShape(30.dp),
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                                Text("LIVE RADAR", style = MaterialTheme.typography.labelLarge, color = Mint)
                                Text(t(language, "Matchs & événements en direct", "Live matches & events", "Partidos y eventos en vivo", "Live-Spiele & Ereignisse"), style = MaterialTheme.typography.headlineMedium)
                                Text(t(language, "$liveNow live · ${state.liveEvents.size} suivis aujourd'hui", "$liveNow live · ${state.liveEvents.size} tracked today", "$liveNow en vivo · ${state.liveEvents.size} seguidos hoy", "$liveNow live · ${state.liveEvents.size} heute verfolgt"), color = TextSecondary)
                            }
                            Surface(shape = CircleShape, color = Mint.copy(alpha = 0.12f), border = BorderStroke(1.dp, Mint.copy(alpha = 0.38f))) {
                                IconButton(onClick = onRefresh, enabled = !syncing) {
                                    if (syncing) CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp, color = Mint)
                                    else Icon(Icons.Outlined.Refresh, t(language, "Actualiser live", "Refresh live", "Actualizar live", "Live aktualisieren"), tint = Mint)
                                }
                            }
                        }
                        Text(t(language, "Score ou classement + temps/tours/session affichés selon le sport.", "Score or ranking + time/laps/session depending on the sport.", "Marcador o clasificación + tiempo/vueltas/sesión según el deporte.", "Score oder Rangliste + Zeit/Runden/Session je nach Sport."), color = TextSecondary)
                    }
                }
            }
        }

        if (state.liveEvents.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, Divider)) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (syncing) t(language, "Recherche des matchs en cours...", "Searching live matches...", "Buscando partidos en vivo...", "Suche nach Live-Spielen...")
                            else t(language, "Aucun live détecté maintenant", "No live event detected now", "No se detecta ningún directo ahora", "Aktuell kein Live-Ereignis erkannt"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (syncing) {
                                t(language, "Connexion aux scoreboards publics : scores, temps de jeu, classements et statuts.", "Connecting to public scoreboards: scores, clocks, rankings and status.", "Conexión a marcadores públicos: resultados, tiempo, clasificaciones y estado.", "Verbindung zu öffentlichen Scoreboards: Scores, Uhr, Ranglisten und Status.")
                            } else {
                                t(language, "Appuie sur Actualiser pour relancer la détection des matchs en cours et résultats récents.", "Tap refresh to search live matches and recent results again.", "Pulsa actualizar para buscar partidos en vivo y resultados recientes.", "Tippe Aktualisieren, um Live-Spiele und aktuelle Ergebnisse neu zu suchen.")
                            },
                            color = TextSecondary,
                        )
                    }
                }
            }
        } else {
            items(state.liveEvents, key = { it.id }) { event ->
                LiveEventCard(event, language, onClick = { onOpen(event) })
            }
        }
    }
}

@Composable
private fun LiveEventCard(event: LiveEventEntity, language: String, onClick: () -> Unit) {
    val accent = if (event.isLive || event.hasLiveMainMetric()) Mint else sportAccent(event.sportKey)
    val mainScenario = event.scenarios.parseScenarioLines().firstOrNull()
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.18f), MaterialTheme.colorScheme.surface, SurfaceHigh.copy(alpha = 0.72f))),
                RoundedCornerShape(28.dp),
            ),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    LiveTag(event.liveMainMetricTag(), accent)
                    Text(formatDate(event.commenceTime), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(cleanDisplayText("${event.sportTitle} · ${event.competitionName}"), style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        cleanDisplayText(event.eventName.ifBlank { "${event.homeName} — ${event.awayName}" }),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(cleanDisplayText(event.statusDescription), style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (event.isResultBoardEvent()) {
                    LiveResultBoard(event, accent)
                } else {
                    LiveScoreBoard(event, accent)
                }
                mainScenario?.let { scenario ->
                    Surface(shape = RoundedCornerShape(18.dp), color = SurfaceHigh.copy(alpha = 0.72f), border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(t(language, "Meilleur signal live", "Best live signal", "Mejor señal en vivo", "Bestes Live-Signal"), style = MaterialTheme.typography.labelLarge, color = accent)
                            ScenarioRow(scenario, accent)
                        }
                    }
                }
                Text(t(language, "Toucher pour ouvrir stats + pronostics", "Tap to open stats + predictions", "Toca para abrir stats + pronósticos", "Tippen für Stats + Prognosen"), style = MaterialTheme.typography.labelMedium, color = accent)
            }
        }
    }
}

@Composable
private fun LiveScoreBoard(event: LiveEventEntity, accent: Color) {
    Surface(shape = RoundedCornerShape(22.dp), color = SurfaceHigh.copy(alpha = 0.82f), border = BorderStroke(1.dp, accent.copy(alpha = 0.26f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(event.liveMainMetricLabel(), style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Black)
            if (event.hasConfirmedScore()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamScore(displayTeamName(event.homeName), event.homeScore, accent, Modifier.weight(1f))
                    Text("—", color = TextSecondary, fontWeight = FontWeight.Black)
                    TeamScore(displayTeamName(event.awayName), event.awayScore, Violet, Modifier.weight(1f))
                }
            } else {
                Text(event.liveParticipantsText(), color = accent, fontWeight = FontWeight.Bold)
                Text(cleanDisplayText(event.liveMainMetricText()), color = TextSecondary)
            }
            LiveProgressPill(event.liveProgressLabel(), event.liveProgressText(), accent)
        }
    }
}

@Composable
private fun LiveResultBoard(event: LiveEventEntity, accent: Color) {
    Surface(shape = RoundedCornerShape(22.dp), color = SurfaceHigh.copy(alpha = 0.82f), border = BorderStroke(1.dp, accent.copy(alpha = 0.26f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(event.liveMainMetricLabel(), style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Black)
            Text(cleanDisplayText(event.liveMainMetricText()), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
            Text(cleanDisplayText(event.liveParticipantsText()), style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LiveProgressPill(event.liveProgressLabel(), event.liveProgressText(), accent)
        }
    }
}

@Composable
private fun LiveProgressPill(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.10f), border = BorderStroke(1.dp, color.copy(alpha = 0.20f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.weight(0.42f))
            Text(cleanDisplayText(value), style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.58f))
        }
    }
}

@Composable
private fun TeamScore(name: String, score: Int?, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, color = color, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(score?.toString() ?: "—", style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ScenarioRow(scenario: LiveScenarioLine, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cleanDisplayText(scenario.label), color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(cleanDisplayText(scenario.type), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            Text(formatPercent(scenario.probability), color = accent, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { scenario.probability.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
            color = accent,
            trackColor = Divider.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun LiveTag(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f), border = BorderStroke(1.dp, color.copy(alpha = 0.36f))) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
    }
}

private data class LiveScenarioLine(
    val type: String,
    val label: String,
    val probability: Double,
)

private fun String.parseScenarioLines(): List<LiveScenarioLine> = lines().mapNotNull { line ->
    val parts = line.split('|')
    if (parts.size < 3) return@mapNotNull null
    LiveScenarioLine(parts[0], parts[1], parts[2].toDoubleOrNull() ?: return@mapNotNull null)
}

private fun sportAccent(sportKey: String): Color = when (sportKey.substringBefore('/')) {
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
