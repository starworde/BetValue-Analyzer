package com.soliano.betvalueanalyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.soliano.betvalueanalyzer.data.DeepAnalysisTarget
import com.soliano.betvalueanalyzer.ui.DeepAnalysisStatus
import com.soliano.betvalueanalyzer.ui.components.cleanDisplayText
import com.soliano.betvalueanalyzer.ui.theme.Danger
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary
import com.soliano.betvalueanalyzer.ui.theme.Violet

@Composable
fun DeepAnalysisScreen(
    status: DeepAnalysisStatus,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRetry: (DeepAnalysisTarget) -> Unit,
) {
    val target = when (status) {
        is DeepAnalysisStatus.Running -> status.target
        is DeepAnalysisStatus.Error -> status.target
        else -> return
    }
    val progress = (status as? DeepAnalysisStatus.Running)?.progress ?: 0.0
    val stage = when (status) {
        is DeepAnalysisStatus.Running -> status.stage
        is DeepAnalysisStatus.Error -> status.message
        else -> ""
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") }
            Column {
                Text("RECHERCHE APPROFONDIE++", style = MaterialTheme.typography.labelLarge, color = Mint)
                Text("Stats, joueurs et infos récentes", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = SurfaceHigh) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(cleanDisplayText("${target.homeTeam} — ${target.awayTeam}"), style = MaterialTheme.typography.headlineMedium)
                Text(cleanDisplayText(target.competitionName), color = TextSecondary)
                if (status is DeepAnalysisStatus.Running) {
                    LinearProgressIndicator(
                        progress = { progress.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = Mint,
                        trackColor = Mint.copy(alpha = 0.12f),
                    )
                    Text(cleanDisplayText("${(progress * 100).toInt()} % · $stage"), color = Mint)
                } else {
                    Text(cleanDisplayText(stage), color = Danger)
                }
            }
        }

        Text("Ce qui est recalculé", style = MaterialTheme.typography.titleLarge)
        if (target.sportKey.startsWith("racing")) {
            AnalysisStep(Icons.Outlined.TravelExplore, "Profil du circuit", "Urbain ou permanent, virages rapides/lents, lignes droites, freinage, traction et dénivelé", progress >= 0.14)
            AnalysisStep(Icons.Outlined.AutoGraph, "Pilotes et voitures", "Championnat, forme récente, fiabilité, constructeur et performances sur circuits similaires", progress >= 0.48)
            AnalysisStep(Icons.Outlined.Groups, "Qualifications et stratégie", "Position de grille disponible, rythme, arrêts, pneus et exécution des pit-stops", progress >= 0.62)
            AnalysisStep(Icons.Outlined.RecordVoiceOver, "Équipes et pilotes", "Interviews, directeurs d'équipe, évolutions techniques et déclarations officielles", progress >= 0.78)
            AnalysisStep(Icons.Outlined.Newspaper, "Presse et réseaux F1", "Presse spécialisée et résultats publics Instagram, Facebook et X", progress >= 0.88)
            AnalysisStep(Icons.Outlined.CheckCircle, "Modèle F1 maison", "Probabilité de victoire et de podium recalculée pour toute la grille", progress >= 1.0)
        } else {
            AnalysisStep(Icons.Outlined.TravelExplore, "Historique large", "Jusqu'à 365 jours pour la tendance de fond et 90 jours d'actualité", progress >= 0.20)
            AnalysisStep(Icons.Outlined.AutoGraph, "Joueurs à impact", "Buts, passes décisives, temps de jeu, forme récente et charge comparée à la saison", progress >= 0.45)
            AnalysisStep(Icons.Outlined.Groups, "Cas particuliers", "Blessures, retours, suspensions, absences, titulaires/remplaçants et fatigue récente", progress >= 0.60)
            AnalysisStep(Icons.Outlined.RecordVoiceOver, "Coach et conférences", "Interviews, conférences de presse, entraînement et choix tactiques", progress >= 0.70)
            AnalysisStep(Icons.Outlined.Newspaper, "Presse et réseaux publics", "Journaux et résultats publics Instagram, Facebook et X des équipes, coachs et joueurs", progress >= 0.82)
            AnalysisStep(Icons.Outlined.CheckCircle, "Modèle maison", "Recoupement, pondération et nouveau calcul de probabilités", progress >= 1.0)
        }
        if (status is DeepAnalysisStatus.Error) {
            Button(onClick = { onRetry(target) }, modifier = Modifier.fillMaxWidth()) { Text("Réessayer l'analyse") }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(if (status is DeepAnalysisStatus.Running) "Annuler" else "Retour")
        }
    }
}

@Composable
private fun AnalysisStep(icon: ImageVector, title: String, body: String, complete: Boolean) {
    val color = if (complete) Mint else Violet
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.13f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (complete) Icons.Outlined.CheckCircle else icon, null, tint = color, modifier = Modifier.size(21.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(cleanDisplayText(title), style = MaterialTheme.typography.titleMedium)
            Text(cleanDisplayText(body), color = TextSecondary)
        }
        if (!complete) Icon(Icons.Outlined.Pending, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
    }
}


