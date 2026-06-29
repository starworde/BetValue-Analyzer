package com.soliano.betvalueanalyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soliano.betvalueanalyzer.data.UserSettings
import com.soliano.betvalueanalyzer.data.local.AnalysisRecordEntity
import com.soliano.betvalueanalyzer.ui.components.ConfidenceRing
import com.soliano.betvalueanalyzer.ui.components.ProbabilityComparison
import com.soliano.betvalueanalyzer.ui.components.Tag
import com.soliano.betvalueanalyzer.ui.components.categoryColor
import com.soliano.betvalueanalyzer.ui.components.formatMoney
import com.soliano.betvalueanalyzer.ui.components.formatOdds
import com.soliano.betvalueanalyzer.ui.components.formatSignedPercent
import com.soliano.betvalueanalyzer.ui.theme.Amber
import com.soliano.betvalueanalyzer.ui.theme.Blue
import com.soliano.betvalueanalyzer.ui.theme.Danger
import com.soliano.betvalueanalyzer.ui.theme.Divider
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary
import com.soliano.betvalueanalyzer.ui.theme.Violet

@Composable
fun AnalysisDetailScreen(
    record: AnalysisRecordEntity,
    settings: UserSettings,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOutcome: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val accent = categoryColor(record.category)
    val suggestedAmount = settings.bankroll * record.suggestedStakePercent / 100.0

    LazyColumn(
        modifier = Modifier.padding(contentPadding).statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") }
                Text("ANALYSE COMPLÈTE", style = MaterialTheme.typography.labelLarge, color = Mint)
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Supprimer", tint = Danger) }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Tag(record.category, accent)
                        Tag(record.outcome, outcomeColor(record.outcome))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${record.sport} · ${record.competition}", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Text("${record.participantA} — ${record.participantB}", style = MaterialTheme.typography.headlineMedium)
                        Text(record.eventDate, color = TextSecondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConfidenceRing(record.confidenceScore, accent, Modifier.size(72.dp))
                        Spacer(Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(record.selection, style = MaterialTheme.typography.titleLarge)
                            Text(record.market, color = TextSecondary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatOdds(record.odds), style = MaterialTheme.typography.headlineMedium, color = accent)
                            Text("cote saisie", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    }
                }
            }
        }

        item {
            DetailSection("Probabilités", Icons.AutoMirrored.Outlined.TrendingUp, Mint) {
                ProbabilityComparison(record.impliedProbability, record.estimatedProbability)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailMetric("Edge", formatSignedPercent(record.valueEdge), if (record.valueEdge >= 0) Mint else Danger)
                    DetailMetric("Espérance", formatSignedPercent(record.expectedValue), if (record.expectedValue >= 0) Mint else Danger)
                    DetailMetric("Value", record.valueLevel, Violet)
                }
            }
        }

        item {
            DetailSection("Lecture du modèle", Icons.Outlined.Lightbulb, Amber) {
                Text(record.explanation, style = MaterialTheme.typography.bodyLarge)
                if (record.contextNote.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(15.dp), color = SurfaceHigh) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Contexte saisi", style = MaterialTheme.typography.labelLarge, color = Amber)
                            Text(record.contextNote, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArgumentCard(
                    title = "Signaux positifs",
                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    color = Mint,
                    arguments = record.positiveArguments.lines().filter { it.isNotBlank() },
                    modifier = Modifier.weight(1f),
                )
                ArgumentCard(
                    title = "Points de vigilance",
                    icon = Icons.AutoMirrored.Outlined.TrendingDown,
                    color = Danger,
                    arguments = record.negativeArguments.lines().filter { it.isNotBlank() },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            DetailSection("Qualité & risque", Icons.Outlined.HourglassTop, Blue) {
                SignalRow("Qualité des données", record.dataQuality, Mint)
                SignalRow("Incertitude", record.uncertainty, Amber)
                if (!record.competition.startsWith("Flux Internet")) {
                    SignalRow("Forme ${record.participantA}", record.formA, Blue)
                    SignalRow("Forme ${record.participantB}", record.formB, Violet)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailMetric("Confiance", "${record.confidenceScore}/100", accent)
                    DetailMetric("Niveau", record.confidenceLabel, accent)
                    DetailMetric("Risque", record.riskLevel, outcomeColor(if (record.riskLevel == "Élevé") "Perdu" else if (record.riskLevel == "Modéré") "En attente" else "Gagné"))
                }
            }
        }

        item {
            DetailSection("Bankroll responsable", Icons.Outlined.CheckCircle, Amber) {
                when {
                    settings.analysisOnly || !settings.stakeSuggestions ->
                        Text("Mode analyse uniquement : aucune suggestion de mise n'est affichée.", color = TextSecondary)
                    record.suggestedStakePercent <= 0.0 ->
                        Text("Aucune mise suggérée : la value, la confiance ou la qualité des données est insuffisante.", color = TextSecondary)
                    else -> {
                        Text(formatMoney(suggestedAmount), style = MaterialTheme.typography.headlineMedium, color = Amber)
                        Text(
                            "Repère facultatif de ${record.suggestedStakePercent} % sur une bankroll de ${formatMoney(settings.bankroll)}. Ne dépassez jamais vos limites.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }

        item {
            DetailSection("Résultat pour le backtest", Icons.Outlined.CheckCircle, Blue) {
                Text("Renseignez le résultat réel. Le ROI affiché dans l'historique est théorique, à mise fixe d'une unité.", color = TextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutcomeButton("En attente", record.outcome, Amber, Modifier.weight(1f), onOutcome)
                    OutcomeButton("Gagné", record.outcome, Mint, Modifier.weight(1f), onOutcome)
                    OutcomeButton("Perdu", record.outcome, Danger, Modifier.weight(1f), onOutcome)
                }
            }
        }

    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette analyse ?") },
            text = { Text("Cette action retire définitivement l'entrée de l'historique local.") },
            confirmButton = {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                ) { Text("Supprimer") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    icon: ImageVector,
    color: Color,
    content: @Composable () -> Unit,
) {
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
private fun DetailMetric(label: String, value: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
private fun ArgumentCard(
    title: String,
    icon: ImageVector,
    color: Color,
    arguments: List<String>,
    modifier: Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.08f)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(icon, null, tint = color)
            Text(title, style = MaterialTheme.typography.titleMedium, color = color)
            arguments.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        }
    }
}

@Composable
private fun SignalRow(label: String, value: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value/100", style = MaterialTheme.typography.labelLarge, color = color)
        }
        Box(Modifier.fillMaxWidth().size(width = 1.dp, height = 7.dp).background(SurfaceHigh, RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(value.coerceIn(0, 100) / 100f).size(width = 1.dp, height = 7.dp).background(color, RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun OutcomeButton(
    label: String,
    selected: String,
    color: Color,
    modifier: Modifier,
    onOutcome: (String) -> Unit,
) {
    if (selected == label) {
        Button(
            onClick = { onOutcome(label) },
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF061018)),
            contentPadding = PaddingValues(horizontal = 5.dp),
        ) { Text(label, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(
            onClick = { onOutcome(label) },
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 5.dp),
        ) { Text(label) }
    }
}

private fun outcomeColor(outcome: String): Color = when (outcome) {
    "Gagné" -> Mint
    "Perdu" -> Danger
    else -> Amber
}
