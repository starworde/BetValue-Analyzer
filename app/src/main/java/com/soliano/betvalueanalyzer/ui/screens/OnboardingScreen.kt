package com.soliano.betvalueanalyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soliano.betvalueanalyzer.ui.theme.Amber
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.Night
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary
import com.soliano.betvalueanalyzer.ui.theme.Violet

@Composable
fun OnboardingScreen(onConfirm: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Surface(
            modifier = Modifier.size(66.dp),
            color = Mint.copy(alpha = 0.12f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Analytics, null, tint = Mint, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("BETVALUE", style = MaterialTheme.typography.labelLarge, color = Mint)
        Text("Analyse mieux.\nDécide toi-même.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(14.dp))
        Text(
            "Un tableau de bord local pour détecter les prochains événements, comparer les formes et comprendre les scénarios probables.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(Modifier.height(30.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingPoint(Icons.Outlined.Psychology, Violet, "Scoring transparent", "Des règles lisibles, adaptées au profil du sport.")
            OnboardingPoint(Icons.Outlined.GppGood, Mint, "Aucune configuration", "Les analyses arrivent automatiquement, sans compte ni clé API.")
            OnboardingPoint(Icons.Outlined.WarningAmber, Amber, "Incertitude visible", "La confiance et les limites des données sont toujours affichées.")
        }
        Spacer(Modifier.height(30.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("confirm_age"),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Night),
        ) {
            Text("Commencer", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OnboardingPoint(icon: ImageVector, color: androidx.compose.ui.graphics.Color, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(SurfaceHigh, RoundedCornerShape(18.dp)).padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).background(color.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
