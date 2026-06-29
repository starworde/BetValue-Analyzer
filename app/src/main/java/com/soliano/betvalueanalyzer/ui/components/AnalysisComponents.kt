package com.soliano.betvalueanalyzer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soliano.betvalueanalyzer.data.local.AnalysisRecordEntity
import com.soliano.betvalueanalyzer.ui.theme.Amber
import com.soliano.betvalueanalyzer.ui.theme.Blue
import com.soliano.betvalueanalyzer.ui.theme.Danger
import com.soliano.betvalueanalyzer.ui.theme.Divider
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary
import com.soliano.betvalueanalyzer.ui.theme.Violet

fun predictionCategoryLabel(category: String): String = when (predictionCategoryKey(category)) {
    "safe" -> "Safe"
    "mitige" -> "Mitigé"
    "exotique" -> "Exotique"
    else -> cleanDisplayText(category)
}

fun predictionCategoryKey(category: String): String = when (cleanDisplayText(category).lowercase()) {
    "fort potentiel", "safe" -> "safe"
    "potentiel", "mitige", "mitigé" -> "mitige"
    "prudent", "prudents", "exotique" -> "exotique"
    else -> ""
}

fun categoryColor(category: String): Color = when (predictionCategoryKey(category)) {
    "safe" -> Mint
    "mitige" -> Amber
    "exotique" -> Danger
    else -> when (category) {
    "Value bet" -> Violet
    "Pari prudent" -> Mint
    "À éviter" -> Danger
    "Données insuffisantes" -> TextSecondary
    "Risqué mais intéressant" -> Amber
    else -> Blue
    }
}

fun riskColor(risk: String): Color = when (risk) {
    "Élevé" -> Danger
    "Modéré" -> Amber
    else -> Mint
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
fun AnalysisCard(record: AnalysisRecordEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = categoryColor(record.category)
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Divider.copy(alpha = 0.8f))
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag(record.category, accent)
                Text(record.eventDate, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${record.sport} · ${record.competition}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${record.participantA} — ${record.participantB}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                ConfidenceRing(score = record.confidenceScore, color = accent)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(record.selection, style = MaterialTheme.typography.titleMedium)
                    Text(record.market, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatOdds(record.odds), style = MaterialTheme.typography.titleLarge, color = accent)
                    Text("cote", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricPill("Edge", formatSignedPercent(record.valueEdge), if (record.valueEdge >= 0) Mint else Danger)
                    MetricPill("Risque", record.riskLevel, riskColor(record.riskLevel))
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = TextSecondary)
            }
        }
    }
}

@Composable
fun ConfidenceRing(score: Int, color: Color, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(700),
        label = "confidence",
    )
    Box(modifier = modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(58.dp)) {
            drawArc(
                color = Divider,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Text("$score", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
        Text("$label $value", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
fun ProbabilityComparison(implied: Double, estimated: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProbabilityBar("Implicite", implied, Blue)
        ProbabilityBar("Estimée", estimated, Mint)
    }
}

@Composable
private fun ProbabilityBar(label: String, value: Double, color: Color) {
    val progress by animateFloatAsState(
        targetValue = value.coerceIn(0.0, 1.0).toFloat(),
        animationSpec = tween(700),
        label = label,
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(formatPercent(value), style = MaterialTheme.typography.labelLarge, color = color)
        }
        Canvas(Modifier.fillMaxWidth().height(9.dp)) {
            drawRoundRect(color = SurfaceHigh, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
            drawRoundRect(
                color = color,
                size = Size(size.width * progress, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height),
            )
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun ResponsibleBanner(compact: Boolean = false) {
    // Message volontairement masqué : l’interface reste centrée sur les données utiles.
}
