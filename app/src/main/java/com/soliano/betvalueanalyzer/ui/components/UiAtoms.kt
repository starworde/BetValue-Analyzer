package com.soliano.betvalueanalyzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soliano.betvalueanalyzer.ui.theme.Amber
import com.soliano.betvalueanalyzer.ui.theme.Blue
import com.soliano.betvalueanalyzer.ui.theme.Danger
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary

fun predictionCategoryLabel(category: String): String {
    val cleaned = cleanDisplayText(category)
    val normalized = cleaned.lowercase()
    if (normalized in setOf("données à compléter", "donnees a completer", "données insuffisantes", "donnees insuffisantes")) {
        return "Données faibles"
    }
    return when (predictionCategoryKey(cleaned)) {
        "safe" -> "Safe"
        "mitige" -> "Mitigé"
        "exotique" -> "Exotique"
        else -> cleaned
    }
}

fun predictionCategoryKey(category: String): String = when (cleanDisplayText(category).lowercase()) {
    "fort potentiel", "safe", "analyse fiable" -> "safe"
    "potentiel", "mitige", "mitigé", "signal moyen" -> "mitige"
    "prudent", "prudents", "exotique", "données faibles", "donnees faibles", "données à compléter", "donnees a completer", "données insuffisantes", "donnees insuffisantes", "surveillance seulement" -> "exotique"
    else -> ""
}

fun categoryColor(category: String): Color = when (predictionCategoryKey(category)) {
    "safe" -> Mint
    "mitige" -> Amber
    "exotique" -> Danger
    else -> Blue
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(cleanDisplayText(title), style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(cleanDisplayText(subtitle), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Text(
            text = cleanDisplayText(text).uppercase(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
fun MetricPill(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .background(SurfaceHigh, RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text("${cleanDisplayText(label)} ${cleanDisplayText(value)}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}
