package com.soliano.betvalueanalyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.soliano.betvalueanalyzer.data.local.AnalysisRecordEntity
import com.soliano.betvalueanalyzer.ui.components.AnalysisCard
import com.soliano.betvalueanalyzer.ui.components.SectionTitle
import com.soliano.betvalueanalyzer.ui.theme.Amber
import com.soliano.betvalueanalyzer.ui.theme.Danger
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun HistoryScreen(
    analyses: List<AnalysisRecordEntity>,
    contentPadding: PaddingValues,
    onOpen: (AnalysisRecordEntity) -> Unit,
) {
    var outcomeFilter by remember { mutableStateOf("Tous") }
    val settled = analyses.filter { it.outcome != "En attente" }
    val wins = settled.count { it.outcome == "Gagné" }
    val theoreticalUnits = settled.sumOf {
        when (it.outcome) {
            "Gagné" -> it.odds - 1.0
            "Perdu" -> -1.0
            else -> 0.0
        }
    }
    val roi = if (settled.isEmpty()) 0.0 else theoreticalUnits / settled.size * 100.0
    val visible = if (outcomeFilter == "Tous") analyses else analyses.filter { it.outcome == outcomeFilter }

    LazyColumn(
        modifier = Modifier.padding(contentPadding).statusBarsPadding().testTag("history_screen"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("JOURNAL LOCAL", style = MaterialTheme.typography.labelLarge, color = Mint)
                Text("Historique & backtest", style = MaterialTheme.typography.headlineMedium)
                Text("Mesurez les résultats au lieu de retenir seulement les bons coups.", color = TextSecondary)
            }
        }

        item {
            Surface(shape = RoundedCornerShape(22.dp), color = SurfaceHigh) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    HistoryKpi("${analyses.size}", "analyses", Mint)
                    HistoryKpi(if (settled.isEmpty()) "—" else "${wins * 100 / settled.size} %", "réussite", Amber)
                    HistoryKpi(String.format(Locale.FRANCE, "%+.1f %%", roi), "ROI théorique", if (roi >= 0) Mint else Danger)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Toutes les analyses", "Résultats saisis manuellement")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Tous", "En attente", "Gagné", "Perdu").forEach { label ->
                        FilterChip(
                            selected = outcomeFilter == label,
                            onClick = { outcomeFilter = label },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Mint.copy(alpha = 0.14f),
                                selectedLabelColor = Mint,
                            ),
                        )
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Aucun résultat ici", style = MaterialTheme.typography.titleMedium)
                        Text("Les analyses enregistrées apparaîtront dans ce journal.", color = TextSecondary)
                    }
                }
            }
        } else {
            items(visible, key = { it.id }) { record ->
                AnalysisCard(record = record, onClick = { onOpen(record) })
            }
        }
    }
}

@Composable
private fun HistoryKpi(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}
