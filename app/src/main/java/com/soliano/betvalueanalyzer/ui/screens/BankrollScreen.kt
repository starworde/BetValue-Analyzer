package com.soliano.betvalueanalyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.soliano.betvalueanalyzer.ui.AppUiState
import com.soliano.betvalueanalyzer.ui.components.formatMoney
import com.soliano.betvalueanalyzer.ui.theme.Amber
import com.soliano.betvalueanalyzer.ui.theme.Danger
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.Night
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary

@Composable
fun BankrollScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    onSave: (Double) -> Unit,
    onToggleSuggestions: (Boolean) -> Unit,
    onToggleAnalysisOnly: (Boolean) -> Unit,
) {
    var bankrollText by remember { mutableStateOf(state.settings.bankroll.toString()) }
    LaunchedEffect(state.settings.bankroll) { bankrollText = state.settings.bankroll.toString() }
    val bankroll = bankrollText.replace(',', '.').toDoubleOrNull()
    val best = state.topPredictions.firstOrNull { it.expectedValue > 0 && it.confidenceScore >= 45 }
    val stakePercent = best?.let {
        when {
            it.riskLevel == "Élevé" -> 0.25
            it.confidenceScore >= 90 -> 1.5
            it.confidenceScore >= 75 -> 1.0
            it.confidenceScore >= 60 -> 0.5
            else -> 0.25
        }
    } ?: 0.0
    val recommended = if (best != null && bankroll != null) bankroll * stakePercent / 100.0 else 0.0
    val recentSettled = state.analyses.filter { it.outcome != "En attente" }
    val lossStreak = recentSettled.takeWhile { it.outcome == "Perdu" }.size

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("GESTION RESPONSABLE", style = MaterialTheme.typography.labelLarge, color = Mint)
            Text("Bankroll", style = MaterialTheme.typography.headlineMedium)
            Text("Des repères prudents, jamais une invitation à miser.", color = TextSecondary)
        }

        Surface(shape = RoundedCornerShape(24.dp), color = Mint.copy(alpha = 0.10f)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Mint)
                    Text("Capital de référence", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(
                    value = bankrollText,
                    onValueChange = { bankrollText = it.filter { char -> char.isDigit() || char == ',' || char == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Bankroll en euros") },
                    suffix = { Text("€") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Button(
                    onClick = { bankroll?.let(onSave) },
                    enabled = bankroll != null && bankroll >= 0,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Night),
                    shape = RoundedCornerShape(15.dp),
                ) { Text("Enregistrer") }
            }
        }

        Surface(shape = RoundedCornerShape(22.dp), color = SurfaceHigh) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Savings, null, tint = Amber)
                    Text("Repère de mise prudente", style = MaterialTheme.typography.titleMedium)
                }
                if (state.settings.analysisOnly || !state.settings.stakeSuggestions) {
                    Text("Suggestions masquées par vos réglages.", color = TextSecondary)
                } else if (best == null) {
                    Text("Aucune analyse ne justifie une suggestion actuellement.", color = TextSecondary)
                } else {
                    Text(formatMoney(recommended), style = MaterialTheme.typography.headlineMedium, color = Amber)
                    Text(
                        "$stakePercent % de bankroll pour « ${best.selection} ». Plafond logiciel : 2 %. Ce montant reste facultatif.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        }

        if (lossStreak >= 2) {
            Surface(shape = RoundedCornerShape(20.dp), color = Danger.copy(alpha = 0.12f)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Block, null, tint = Danger)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pause recommandée", style = MaterialTheme.typography.titleMedium, color = Danger)
                        Text("$lossStreak pertes consécutives sont enregistrées. N'augmentez pas les mises et ne cherchez pas à vous refaire.")
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingSwitch(
                    title = "Afficher les suggestions",
                    subtitle = "Repères limités entre 0,25 % et 2 %.",
                    checked = state.settings.stakeSuggestions && !state.settings.analysisOnly,
                    enabled = !state.settings.analysisOnly,
                    onChange = onToggleSuggestions,
                )
                SettingSwitch(
                    title = "Mode analyse uniquement",
                    subtitle = "Masque toute suggestion de mise.",
                    checked = state.settings.analysisOnly,
                    onChange = onToggleAnalysisOnly,
                )
            }
        }

        Surface(shape = RoundedCornerShape(18.dp), color = Danger.copy(alpha = 0.08f)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Règles non négociables", style = MaterialTheme.typography.titleMedium, color = Danger)
                Text("• Pas de martingale\n• Pas d'augmentation après une perte\n• Pas d'argent nécessaire au quotidien\n• Une pause vaut mieux qu'une décision impulsive", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Mint),
        )
    }
}
