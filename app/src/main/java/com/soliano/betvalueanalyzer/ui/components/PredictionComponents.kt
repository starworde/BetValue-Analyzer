package com.soliano.betvalueanalyzer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.ui.theme.Divider
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary
import com.soliano.betvalueanalyzer.ui.theme.Violet

@Composable
fun PredictionCard(
    prediction: PredictionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = categoryColor(prediction.category)
    val sportCopy = sportPredictionCopy(prediction.sportKey, prediction.sportTitle)
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.36f)),
    ) {
        Column(
            Modifier
                .background(
                    Brush.linearGradient(listOf(accent.copy(alpha = 0.12f), MaterialTheme.colorScheme.surface, SurfaceHigh.copy(alpha = 0.72f))),
                    RoundedCornerShape(22.dp),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(predictionCategoryLabel(prediction.category), accent)
                    if (prediction.isCloudPrediction()) Tag("Cloud", Violet)
                }
                Text(formatDate(prediction.commenceTime), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    cleanDisplayText("${prediction.sportTitle} · ${prediction.competitionName}"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Mint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PredictionTeams(prediction, accent)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(sportCopy.cardLabel, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(cleanDisplayText(prediction.selection), style = MaterialTheme.typography.titleMedium)
                    if (prediction.expectedScore.isNotBlank()) {
                        Text("${sportCopy.expectedShortLabel} : ${prediction.expectedScore}", style = MaterialTheme.typography.bodyMedium, color = Mint)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatPercent(prediction.consensusProbability), style = MaterialTheme.typography.titleLarge, color = accent)
                    Text(sportCopy.probabilityMetricLabel.lowercase(), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricPill("Confiance", "${prediction.confidenceScore}/100", accent)
                    MetricPill("Niveau", predictionCategoryLabel(prediction.category), accent)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = TextSecondary)
            }
        }
    }
}

private fun PredictionEntity.isCloudPrediction(): Boolean =
    sourceName.contains("Cloud collaboratif", ignoreCase = true)

@Composable
private fun PredictionTeams(prediction: PredictionEntity, accent: Color) {
    if (prediction.sportKey.startsWith("racing") || prediction.sportKey.startsWith("cycling")) {
        Surface(shape = RoundedCornerShape(18.dp), color = SurfaceHigh.copy(alpha = 0.82f), border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))) {
            Text(
                displayPredictionTeams(prediction),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PredictionTeamBox(displayTeamName(prediction.homeTeam), accent, Modifier.weight(1f))
        Text("VS", style = MaterialTheme.typography.labelMedium, color = TextSecondary, fontWeight = FontWeight.Black)
        PredictionTeamBox(displayTeamName(prediction.awayTeam), Violet, Modifier.weight(1f))
    }
}

@Composable
private fun PredictionTeamBox(name: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceHigh.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).background(color.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(teamInitials(name), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Black)
            }
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun teamInitials(name: String): String = name.split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.first().uppercaseChar().toString() }
    .ifBlank { "?" }
