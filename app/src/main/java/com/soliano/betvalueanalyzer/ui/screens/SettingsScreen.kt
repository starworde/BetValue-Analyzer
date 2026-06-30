package com.soliano.betvalueanalyzer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soliano.betvalueanalyzer.BuildConfig
import com.soliano.betvalueanalyzer.data.UserSettings
import com.soliano.betvalueanalyzer.domain.SportIntelligenceCatalog
import com.soliano.betvalueanalyzer.domain.SportsCatalog
import com.soliano.betvalueanalyzer.ui.AppUiState
import com.soliano.betvalueanalyzer.ui.SyncStatus
import com.soliano.betvalueanalyzer.ui.t
import com.soliano.betvalueanalyzer.ui.components.cleanDisplayText
import com.soliano.betvalueanalyzer.ui.components.formatDate
import com.soliano.betvalueanalyzer.ui.theme.Blue
import com.soliano.betvalueanalyzer.ui.theme.Danger
import com.soliano.betvalueanalyzer.ui.theme.Divider
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary
import com.soliano.betvalueanalyzer.ui.theme.Violet
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    language: String = "fr",
    onToggleAutoRefresh: (Boolean) -> Unit,
    onToggleCloudCollaborative: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onForceCloudSync: () -> Unit,
    onExportCloudDiagnostic: () -> Unit,
) {
    val syncing = state.syncStatus == SyncStatus.Syncing

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t(language, "SOURCE AUTOMATIQUE", "AUTOMATIC SOURCE", "FUENTE AUTOMÁTICA", "AUTOMATISCHE QUELLE"), style = MaterialTheme.typography.labelLarge, color = Mint)
            Text(t(language, "Internet public, sans clé", "Public Internet, no key", "Internet público, sin clave", "Öffentliches Internet, ohne Schlüssel"), style = MaterialTheme.typography.headlineMedium)
            Text(t(language, "L'application fonctionne dès l'ouverture, sans compte à configurer.", "The app works immediately, with no account to configure.", "La app funciona al abrirse, sin cuenta que configurar.", "Die App funktioniert sofort, ohne Konto-Einrichtung."), color = TextSecondary)
        }

        Surface(shape = RoundedCornerShape(24.dp), color = Mint.copy(alpha = 0.10f)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Language, null, tint = Mint)
                    Column {
                        Text(t(language, "Flux public actif", "Public feed active", "Flujo público activo", "Öffentlicher Feed aktiv"), style = MaterialTheme.typography.titleLarge)
                        Text(t(language, "Aucune clé API à saisir.", "No API key to enter.", "Sin clave API que introducir.", "Kein API-Schlüssel nötig."), color = TextSecondary)
                    }
                }
                Text(
                    t(language, "Les calendriers, scoreboards et statistiques publiques sont lus pour détecter les prochains événements. La source exacte est affichée sur chaque analyse.", "Public calendars, scoreboards and statistics are read to detect upcoming events. The exact source is shown on each analysis.", "Se leen calendarios, marcadores y estadísticas públicas para detectar próximos eventos. La fuente exacta aparece en cada análisis.", "Öffentliche Kalender, Scoreboards und Statistiken werden gelesen. Die genaue Quelle steht bei jeder Analyse."),
                    color = TextSecondary,
                )
            }
        }

        Surface(shape = RoundedCornerShape(22.dp), color = SurfaceHigh) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Language, null, tint = Blue)
                    Column {
                        Text(t(language, "Langue de l’interface", "Interface language", "Idioma de la interfaz", "Sprache der Oberfläche"), style = MaterialTheme.typography.titleLarge)
                        Text(t(language, "Choix enregistré : français, anglais, espagnol ou allemand.", "Saved choice: French, English, Spanish or German.", "Elección guardada: francés, inglés, español o alemán.", "Gespeicherte Wahl: Französisch, Englisch, Spanisch oder Deutsch."), color = TextSecondary)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    languageOptions.forEach { option ->
                        LanguageChip(
                            code = option.first,
                            label = option.second,
                            selected = state.settings.appLanguage == option.first,
                            onClick = { onLanguageChange(option.first) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        SourceHealthSection(state = state, language = language)

        Surface(shape = RoundedCornerShape(22.dp), color = SurfaceHigh) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudSync, null, tint = Mint)
                        Column {
                            Text(t(language, "Actualisation automatique", "Automatic refresh", "Actualización automática", "Automatische Aktualisierung"), style = MaterialTheme.typography.titleMedium)
                            Text(t(language, "5 min app ouverte · 2 min avant match · 15 min arrière-plan", "5 min app open · 2 min before match · 15 min background", "5 min app abierta · 2 min antes del partido · 15 min en segundo plano", "5 Min App offen · 2 Min vor Spiel · 15 Min Hintergrund"), color = TextSecondary)
                        }
                    }
                    Switch(
                        checked = state.settings.autoRefresh,
                        onCheckedChange = onToggleAutoRefresh,
                        colors = SwitchDefaults.colors(checkedTrackColor = Mint),
                    )
                }
                HorizontalDivider(color = Divider)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            if (state.settings.lastSyncEpoch > 0) t(language, "Dernière synchro : ${formatDate(state.settings.lastSyncEpoch)}", "Last sync: ${formatDate(state.settings.lastSyncEpoch)}", "Última sincro: ${formatDate(state.settings.lastSyncEpoch)}", "Letzte Synchro: ${formatDate(state.settings.lastSyncEpoch)}")
                            else t(language, "Pas encore synchronisé", "Not synced yet", "Aún no sincronizado", "Noch nicht synchronisiert"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(t(language, "Football, rugby, cyclisme, basket, tennis, F1, MMA, baseball, hockey et NFL", "Football, rugby, cycling, basketball, tennis, F1, MMA, baseball, hockey and NFL", "Fútbol, rugby, ciclismo, basket, tenis, F1, MMA, béisbol, hockey y NFL", "Fußball, Rugby, Radsport, Basketball, Tennis, F1, MMA, Baseball, Hockey und NFL"), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                    IconButton(onClick = onRefresh, enabled = !syncing) {
                        Icon(Icons.Outlined.Refresh, t(language, "Actualiser", "Refresh", "Actualizar", "Aktualisieren"), tint = Mint)
                    }
                }
                when (val status = state.syncStatus) {
                    is SyncStatus.Error -> Text(cleanDisplayText(status.message), color = MaterialTheme.colorScheme.error)
                    is SyncStatus.Ready -> Text(cleanDisplayText(status.message), color = Mint)
                    SyncStatus.Syncing -> Text(t(language, "Recherche en cours…", "Searching…", "Buscando…", "Suche läuft…"), color = TextSecondary)
                    else -> Unit
                }
            }
        }

        Surface(shape = RoundedCornerShape(22.dp), color = SurfaceHigh) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.CloudSync, null, tint = Violet)
                        Column {
                            Text(t(language, "Cloud collaboratif", "Collaborative cloud", "Cloud colaborativa", "Kollaborative Cloud"), style = MaterialTheme.typography.titleMedium)
                            Text(t(language, "Partage léger des résultats déjà calculés, sans favoris ni historique privé.", "Light sharing of already computed results, without favorites or private history.", "Comparte resultados ya calculados, sin favoritos ni historial privado.", "Leichtes Teilen bereits berechneter Ergebnisse, ohne Favoriten oder privaten Verlauf."), color = TextSecondary)
                        }
                    }
                    Switch(
                        checked = state.settings.cloudCollaborativeEnabled,
                        onCheckedChange = onToggleCloudCollaborative,
                        colors = SwitchDefaults.colors(checkedTrackColor = Violet),
                    )
                }
                HorizontalDivider(color = Divider)
                CloudInfoRow(t(language, "Dernière synchro", "Last cloud sync", "Última sync cloud", "Letzte Cloud-Sync"), cloudTimeLabel(state.settings.lastCloudSyncEpoch, language))
                CloudInfoRow(t(language, "Dernier envoi", "Last upload", "Último envío", "Letzter Upload"), cloudTimeLabel(state.settings.lastCloudUploadEpoch, language))
                CloudInfoRow(t(language, "Dernière lecture", "Last read", "Última lectura", "Letzter Abruf"), cloudTimeLabel(state.settings.lastCloudReadEpoch, language))
                CloudInfoRow(t(language, "Résultats récupérés", "Fetched results", "Resultados recuperados", "Geladene Ergebnisse"), state.settings.lastCloudFetchedCount.toString())
                CloudInfoRow(
                    t(language, "Dernière erreur", "Last error", "Último error", "Letzter Fehler"),
                    cleanDisplayText(state.settings.lastCloudError).ifBlank { t(language, "Aucune", "None", "Ninguno", "Keine") },
                )
                CloudInfoRow(t(language, "Dernier run GitHub", "Last GitHub run", "Último run GitHub", "Letzter GitHub-Lauf"), cloudJobLabel(state.settings, language))
                CloudInfoRow(t(language, "Cloud résultats écrits", "Cloud results written", "Resultados cloud escritos", "Cloud-Ergebnisse geschrieben"), "${state.settings.cloudJobResultsWritten}/${state.settings.cloudJobResultsPrepared}")
                CloudInfoRow(t(language, "Cloud événements trouvés", "Cloud events found", "Eventos cloud encontrados", "Cloud-Ereignisse gefunden"), state.settings.cloudJobEventsFound.toString())
                CloudInfoRow(t(language, "Nettoyage sports retirés", "Removed sports cleanup", "Limpieza deportes retirados", "Entfernte Sportarten bereinigt"), state.settings.cloudJobRemovedSportsDeleted.toString())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onForceCloudSync,
                        enabled = state.settings.cloudCollaborativeEnabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(t(language, "Forcer synchro", "Force sync", "Forzar sync", "Sync erzwingen"))
                    }
                    OutlinedButton(
                        onClick = onExportCloudDiagnostic,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(t(language, "Diagnostic", "Diagnostic", "Diagnóstico", "Diagnose"))
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text(t(language, "Méthode de prédiction", "Prediction method", "Método de predicción", "Prognosemethode"), style = MaterialTheme.typography.titleLarge)
                SourceRow(
                    Icons.Outlined.Language,
                    Mint,
                    t(language, "Consensus multi-source", "Multi-source consensus", "Consenso multi-fuente", "Multi-Quellen-Konsens"),
                    t(language, "ESPN, FotMob, FIFA officiel, OpenLigaDB, TheSportsDB, Jolpica, OpenF1, Google/Bing et les médias concordants sont recoupés sans clé utilisateur.", "ESPN, FotMob, official FIFA, OpenLigaDB, TheSportsDB, Jolpica, OpenF1, Google/Bing and matching media are cross-checked without a user key.", "ESPN, FotMob, FIFA oficial, OpenLigaDB, TheSportsDB, Jolpica, OpenF1, Google/Bing y medios concordantes se cruzan sin clave.", "ESPN, FotMob, FIFA offiziell, OpenLigaDB, TheSportsDB, Jolpica, OpenF1, Google/Bing und passende Medien werden ohne Schlüssel abgeglichen."),
                )
                SourceRow(Icons.Outlined.AutoGraph, Blue, t(language, "Tendance saisonnière", "Season trend", "Tendencia de temporada", "Saisontrend"), t(language, "La forme récente d'une équipe, d'un joueur ou d'un pilote est comparée à son niveau sur un large historique afin de limiter les fausses hausses.", "Recent form is compared with long-term level to avoid false spikes.", "La forma reciente se compara con un histórico amplio para limitar falsas subidas.", "Aktuelle Form wird mit langfristigem Niveau verglichen, um Fehlspitzen zu vermeiden."))
                SourceRow(Icons.Outlined.CloudSync, Violet, t(language, "Priorité aux favoris", "Favorites first", "Favoritos primero", "Favoriten zuerst"), t(language, "Les sports et compétitions favoris passent en premier pour les statistiques, compositions et actualités.", "Favorite sports and competitions are prioritized for stats, lineups and news.", "Los deportes y competiciones favoritos tienen prioridad para stats, alineaciones y noticias.", "Favorisierte Sportarten und Wettbewerbe haben Priorität bei Stats, Aufstellungen und News."))
                SourceRow(Icons.Outlined.Language, Blue, t(language, "Analyse approfondie au toucher", "Deep analysis on tap", "Análisis profundo al tocar", "Tiefanalyse per Tipp"), t(language, "Jusqu'à 365 jours de statistiques, 90 jours d'actualité, classement, coachs, presse et résultats sociaux publics sont revérifiés.", "Up to 365 days of stats and 90 days of news, standings, coaches, press and public social results are checked again.", "Hasta 365 días de stats y 90 días de actualidad, clasificación, técnicos, prensa y redes públicas se revisan.", "Bis zu 365 Tage Stats und 90 Tage News, Tabellen, Trainer, Presse und öffentliche soziale Signale werden geprüft."))
                SourceRow(Icons.Outlined.AutoGraph, Violet, t(language, "Modèle F1 dédié", "Dedicated F1 model", "Modelo F1 dedicado", "Eigenes F1-Modell"), t(language, "Pilotes, constructeurs, qualifications, fiabilité, circuit, virages, lignes droites, freinage, traction et pit-stops.", "Drivers, constructors, qualifying, reliability, track, corners, straights, braking, traction and pit stops.", "Pilotos, constructores, clasificación, fiabilidad, circuito, curvas, rectas, frenada, tracción y paradas.", "Fahrer, Teams, Qualifying, Zuverlässigkeit, Strecke, Kurven, Geraden, Bremsen, Traktion und Boxenstopps."))
                SourceRow(Icons.Outlined.Security, Mint, t(language, "Aucune fausse précision", "No fake precision", "Sin falsa precisión", "Keine falsche Präzision"), t(language, "Quand les données manquent, l'événement est marqué comme peu fiable.", "When data is missing, the event is marked less reliable.", "Cuando faltan datos, el evento se marca como menos fiable.", "Wenn Daten fehlen, wird das Ereignis als weniger zuverlässig markiert."))
            }
        }

        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BetValue Analyzer ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text(t(language, "Flux sans clé : événements à venir et analyses pré-match.", "No-key feed: upcoming events and pre-match analysis.", "Flujo sin clave: próximos eventos y análisis prepartido.", "Feed ohne Schlüssel: kommende Ereignisse und Vorab-Analyse."), color = TextSecondary)
                Text(t(language, "Interface recentrée sur l’analyse sportive : calendrier, live, stats et diagnostics sources.", "Interface focused on sports analysis: calendar, live, stats and source diagnostics.", "Interfaz centrada en análisis deportivo: calendario, directo, stats y diagnóstico de fuentes.", "Fokus auf Sportanalyse: Kalender, Live, Stats und Quellen-Diagnose."), color = TextSecondary)
            }
        }
    }
}

private val languageOptions = listOf(
    "fr" to "FR",
    "en" to "EN",
    "es" to "ES",
    "de" to "DE",
)

@Composable
private fun CloudInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.1f))
    }
}

private fun cloudTimeLabel(value: Long, language: String): String =
    if (value > 0L) formatDate(value)
    else t(language, "Jamais", "Never", "Nunca", "Nie")

private fun cloudJobLabel(settings: UserSettings, language: String): String {
    val status = cleanDisplayText(settings.cloudJobStatus).ifBlank {
        return t(language, "Jamais", "Never", "Nunca", "Nie")
    }
    val time = listOf(settings.cloudJobFinishedEpoch, settings.cloudJobUpdatedEpoch)
        .firstOrNull { it > 0L }
        ?.let(::formatDate)
        .orEmpty()
    val counters = buildList {
        if (settings.cloudJobResultsPrepared > 0 || settings.cloudJobResultsWritten > 0) {
            add("${settings.cloudJobResultsWritten}/${settings.cloudJobResultsPrepared}")
        }
        if (settings.cloudJobEventsFound > 0) add("${settings.cloudJobEventsFound} events")
        if (settings.cloudJobSourceErrors > 0) add("${settings.cloudJobSourceErrors} source errors")
    }.joinToString(" · ")
    return listOf(status, time, counters)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

@Composable
private fun SourceHealthSection(state: AppUiState, language: String) {
    val health = sourceHealthRows(state, language)
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, null, tint = Mint)
                Column {
                    Text(t(language, "Santé des sources", "Source health", "Salud de fuentes", "Quellenstatus"), style = MaterialTheme.typography.titleLarge)
                    Text(t(language, "Pourquoi un match manque : source vide, erreur, cloud ou donnée pas publiée.", "Why an event is missing: empty source, error, cloud or unpublished data.", "Por qué falta un evento: fuente vacía, error, cloud o dato no publicado.", "Warum etwas fehlt: leere Quelle, Fehler, Cloud oder nicht veröffentlichte Daten."), color = TextSecondary)
                }
            }
            health.forEach { row ->
                DiagnosticRow(row.label, row.value, row.statusColor)
            }
            val sportRows = sourceHealthSportRows(state)
            if (sportRows.isNotEmpty()) {
                HorizontalDivider(color = Divider)
                Text(t(language, "Événements trouvés par sport", "Events found by sport", "Eventos encontrados por deporte", "Gefundene Ereignisse je Sport"), style = MaterialTheme.typography.titleMedium, color = Blue)
                sportRows.forEach { row ->
                    DiagnosticRow(row.label, row.value, row.statusColor)
                }
            }
            HorizontalDivider(color = Divider)
            Text(t(language, "Diagnostic détaillé par sport", "Detailed sport diagnostics", "Diagnóstico detallado por deporte", "Detaillierte Sportdiagnose"), style = MaterialTheme.typography.titleMedium, color = Violet)
            sourceHealthSportDiagnosticRows(state, language).forEach { row ->
                DiagnosticRow(row.label, row.value, row.statusColor)
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(cleanDisplayText(label), style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(cleanDisplayText(value), style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1.1f))
    }
}

internal data class SourceHealthRow(
    val label: String,
    val value: String,
    val statusColor: androidx.compose.ui.graphics.Color,
)

internal fun sourceHealthRows(state: AppUiState, language: String): List<SourceHealthRow> {
    val sourceNames = (
        state.upcomingEvents.map { it.sourceName } +
            state.predictions.map { it.sourceName } +
            state.liveEvents.map { it.sourceName }
        )
        .map(::cleanDisplayText)
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.FRANCE) }
    val eventsBySport = state.upcomingEvents.groupingBy { cleanDisplayText(it.sportTitle) }.eachCount()
    val emptyMajorSports = listOf("Football", "Rugby", "Tennis", "Volley-ball", "Basketball", "Cyclisme", "Formule 1", "Baseball", "Hockey")
        .filter { sport -> eventsBySport.keys.none { it.equals(sport, ignoreCase = true) } }
    val syncError = (state.syncStatus as? SyncStatus.Error)?.message.orEmpty()
    val cloudError = cleanDisplayText(state.settings.lastCloudError)
    val firestoreRead = firestoreStatus(
        enabled = state.settings.cloudCollaborativeEnabled,
        successEpoch = state.settings.lastCloudReadEpoch,
        error = cloudError,
        keyword = "lecture",
        language = language,
    )
    val firestoreWrite = firestoreStatus(
        enabled = state.settings.cloudCollaborativeEnabled,
        successEpoch = state.settings.lastCloudUploadEpoch,
        error = cloudError,
        keyword = "ecriture",
        language = language,
    )
    val githubActions = githubActionsStatus(state.settings, language)
    val firestoreJobError = listOf(
        state.settings.cloudJobFirestoreError,
        state.settings.cloudJobFirestoreCleanupError,
        state.settings.cloudJobError,
    )
        .map(::cleanDisplayText)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    return listOf(
        SourceHealthRow(
            "GitHub Actions",
            githubActions.first,
            githubActions.second,
        ),
        SourceHealthRow(
            "Firestore quota/nettoyage",
            firestoreJobError.ifBlank {
                if (state.settings.cloudJobStatus.isNotBlank()) "OK"
                else t(language, "Pas encore confirmé", "Not confirmed yet", "Aún no confirmado", "Noch nicht bestätigt")
            },
            if (firestoreJobError.isBlank() && state.settings.cloudJobStatus.isNotBlank()) Mint else if (firestoreJobError.isBlank()) TextSecondary else Danger,
        ),
        SourceHealthRow(
            t(language, "Sources actives", "Active sources", "Fuentes activas", "Aktive Quellen"),
            if (sourceNames.isEmpty()) t(language, "Aucune source chargée", "No source loaded", "Ninguna fuente cargada", "Keine Quelle geladen")
            else "${sourceNames.size} · ${sourceNames.take(4).joinToString(", ")}",
            if (sourceNames.isEmpty()) TextSecondary else Mint,
        ),
        SourceHealthRow(
            t(language, "Sources vides visibles", "Visible empty sources", "Fuentes vacías visibles", "Sichtbar leere Quellen"),
            if (emptyMajorSports.isEmpty()) t(language, "Aucun sport majeur vide", "No major sport empty", "Ningún deporte mayor vacío", "Keine große Sportart leer")
            else emptyMajorSports.take(5).joinToString(", "),
            if (emptyMajorSports.isEmpty()) Mint else Blue,
        ),
        SourceHealthRow(
            t(language, "Dernière actualisation", "Last refresh", "Última actualización", "Letzte Aktualisierung"),
            cloudTimeLabel(state.settings.lastSyncEpoch, language),
            if (state.settings.lastSyncEpoch > 0L) Mint else TextSecondary,
        ),
        SourceHealthRow(
            t(language, "Erreurs app/source", "App/source errors", "Errores app/fuente", "App-/Quellenfehler"),
            cleanDisplayText(syncError).ifBlank { t(language, "Aucune erreur actuelle", "No current error", "Sin error actual", "Kein aktueller Fehler") },
            if (syncError.isBlank()) Mint else Danger,
        ),
        SourceHealthRow(
            "Firestore lecture",
            firestoreRead.first,
            firestoreRead.second,
        ),
        SourceHealthRow(
            "Firestore écriture",
            firestoreWrite.first,
            firestoreWrite.second,
        ),
        SourceHealthRow(
            "GitHub Actions",
            t(language, "Non exposé à l’app · vérifier côté GitHub si besoin", "Not exposed to the app · check GitHub if needed", "No expuesto a la app · revisar GitHub si hace falta", "Nicht in der App verfügbar · bei Bedarf GitHub prüfen"),
            TextSecondary,
        ),
        SourceHealthRow(
            t(language, "Dernière erreur cloud", "Last cloud error", "Último error cloud", "Letzter Cloud-Fehler"),
            cloudError.ifBlank { t(language, "Aucune", "None", "Ninguna", "Keine") },
            if (cloudError.isBlank()) Mint else Danger,
        ),
    ).distinctBy { it.label }
}

private fun sourceHealthSportRows(state: AppUiState): List<SourceHealthRow> =
    state.upcomingEvents
        .groupingBy { cleanDisplayText(it.sportTitle).ifBlank { it.sportKey } }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(10)
        .map { SourceHealthRow(it.key, "${it.value} événement(s)", Mint) }

internal fun sourceHealthSportDiagnosticRows(state: AppUiState, language: String): List<SourceHealthRow> {
    val catalog = SportsCatalog.sports.associateBy { it.key }
    val dynamicKeys = (
        state.upcomingEvents.map { it.sportKey.substringBefore('/') } +
            state.predictions.map { it.sportKey.substringBefore('/') } +
            state.liveEvents.map { it.sportKey.substringBefore('/') }
        ).filter { it.isNotBlank() }
    val keys = (SportsCatalog.sports.map { it.key } + dynamicKeys).distinct()
    return keys.map { sportKey ->
        val events = state.upcomingEvents.filter { it.sportKey.substringBefore('/') == sportKey }
        val predictions = state.predictions.filter { it.sportKey.substringBefore('/') == sportKey }
        val lives = state.liveEvents.filter { it.sportKey.substringBefore('/') == sportKey }
        val sources = (
            events.map { it.sourceName } +
                predictions.map { it.sourceName } +
                lives.flatMap { live -> listOf(live.sourceName) + live.sourceDetails.split("·", ",") }
            )
            .map(::cleanDisplayText)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.FRANCE) }
        val profile = SportIntelligenceCatalog.profile(sportKey)
        val allSummary = predictions.joinToString(" ") { "${it.statSummary} ${it.scenarios} ${it.playerScenarios}" }
            .lowercase(Locale.FRANCE)
        val matchedStats = profile.watchedStats.filter { stat -> statMatchesSummary(stat, allSummary) }
        val availableStats = buildList {
            if (events.isNotEmpty() || lives.isNotEmpty()) add("calendrier")
            if (events.any { it.competitionName.isNotBlank() } || predictions.any { it.competitionName.isNotBlank() }) add("compétition")
            if (events.isNotEmpty() || predictions.isNotEmpty()) add("équipes")
            if (sources.isNotEmpty()) add("source")
            if (predictions.isNotEmpty()) add("analyse")
            if (lives.isNotEmpty()) add("live")
            addAll(matchedStats)
        }.distinct().take(12)
        val missingStats = profile.watchedStats
            .filterNot { stat -> availableStats.any { it.equals(stat, ignoreCase = true) } || statMatchesSummary(stat, allSummary) }
            .take(10)
        val lastUpdate = listOf(
            state.settings.lastSyncEpoch,
            predictions.maxOfOrNull { it.sourceLastUpdate } ?: 0L,
        ).maxOrNull() ?: 0L
        val confidenceLabel = when {
            predictions.isNotEmpty() -> "${predictions.map { it.confidenceScore }.average().roundToInt()}/100"
            events.isNotEmpty() || lives.isNotEmpty() -> t(language, "surveillance seulement", "watch only", "solo vigilancia", "nur Beobachtung")
            else -> t(language, "aucune donnée publiée", "no published data", "sin dato publicado", "keine Daten veröffentlicht")
        }
        val countLabel = "${events.size + lives.size} match(s)/événement(s)"
        val sourceLabel = sources.take(3).joinToString(", ").ifBlank {
            t(language, "aucune source active", "no active source", "sin fuente activa", "keine aktive Quelle")
        }
        val updateLabel = if (lastUpdate > 0L) formatDate(lastUpdate) else t(language, "jamais", "never", "nunca", "nie")
        val availableLabel = availableStats.joinToString(", ").ifBlank {
            t(language, "aucune stat exploitable", "no usable stat", "sin stat utilizable", "keine nutzbare Statistik")
        }
        val missingLabel = missingStats.joinToString(", ").ifBlank {
            t(language, "aucune stat clé manquante", "no key stat missing", "sin stat clave faltante", "keine Kernstatistik fehlt")
        }
        SourceHealthRow(
            catalog[sportKey]?.name ?: sportKey,
            t(
                language,
                "$countLabel · source : $sourceLabel · MAJ : $updateLabel · stats dispo : $availableLabel · stats manquantes : $missingLabel · confiance : $confidenceLabel",
                "$countLabel · source: $sourceLabel · updated: $updateLabel · available stats: $availableLabel · missing stats: $missingLabel · confidence: $confidenceLabel",
                "$countLabel · fuente: $sourceLabel · act.: $updateLabel · stats disp.: $availableLabel · stats faltantes: $missingLabel · confianza: $confidenceLabel",
                "$countLabel · Quelle: $sourceLabel · Update: $updateLabel · verfügbare Stats: $availableLabel · fehlende Stats: $missingLabel · Vertrauen: $confidenceLabel",
            ),
            when {
                predictions.isNotEmpty() -> Mint
                events.isNotEmpty() || lives.isNotEmpty() -> Blue
                else -> TextSecondary
            },
        )
    }
}

private fun statMatchesSummary(stat: String, summary: String): Boolean {
    if (summary.isBlank()) return false
    val cleaned = cleanDisplayText(stat).lowercase(Locale.FRANCE)
    val candidates = buildList {
        add(cleaned)
        add(cleaned.substringBefore(" "))
        cleaned.split("/", " ", "-", "·").filter { it.length >= 4 }.forEach(::add)
    }.map { it.trim() }.filter { it.length >= 3 }.distinct()
    return candidates.any { it in summary }
}

private fun firestoreStatus(
    enabled: Boolean,
    successEpoch: Long,
    error: String,
    keyword: String,
    language: String,
): Pair<String, androidx.compose.ui.graphics.Color> {
    if (!enabled) return t(language, "Désactivé", "Disabled", "Desactivado", "Deaktiviert") to TextSecondary
    val normalized = error.lowercase(Locale.FRANCE)
    val hasFirestoreError = "firestore" in normalized || keyword in normalized || "refuse" in normalized || "permission" in normalized
    if (hasFirestoreError) return cleanDisplayText(error).ifBlank { "Erreur Firestore" } to Danger
    return if (successEpoch > 0L) {
        "OK · ${formatDate(successEpoch)}" to Mint
    } else {
        t(language, "Pas encore confirmé", "Not confirmed yet", "Aún no confirmado", "Noch nicht bestätigt") to TextSecondary
    }
}

private fun githubActionsStatus(settings: UserSettings, language: String): Pair<String, androidx.compose.ui.graphics.Color> {
    val status = cleanDisplayText(settings.cloudJobStatus)
    if (status.isBlank()) {
        return t(language, "Pas encore confirmé", "Not confirmed yet", "Aún no confirmado", "Noch nicht bestätigt") to TextSecondary
    }
    val normalized = status.lowercase(Locale.FRANCE)
    val color = when {
        "success" in normalized -> Mint
        "partial" in normalized || "quota" in normalized -> Blue
        "error" in normalized || "fail" in normalized -> Danger
        else -> TextSecondary
    }
    val finishedAt = listOf(settings.cloudJobFinishedEpoch, settings.cloudJobUpdatedEpoch)
        .firstOrNull { it > 0L }
        ?.let(::formatDate)
        .orEmpty()
    val counters = buildList {
        add("${settings.cloudJobResultsWritten}/${settings.cloudJobResultsPrepared} écrits")
        if (settings.cloudJobEventsFound > 0) add("${settings.cloudJobEventsFound} événements")
        if (settings.cloudJobSourcesChecked > 0) add("${settings.cloudJobSourcesChecked} sources")
        if (settings.cloudJobSourceErrors > 0) add("${settings.cloudJobSourceErrors} erreurs source")
    }.joinToString(" · ")
    return listOf(status, finishedAt, counters)
        .filter { it.isNotBlank() }
        .joinToString(" · ") to color
}

@Composable
private fun LanguageChip(code: String, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (selected) Mint else TextSecondary
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Mint.copy(alpha = 0.16f) else Divider.copy(alpha = 0.24f),
    ) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = color, style = MaterialTheme.typography.titleMedium)
            Text(code.uppercase(), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SourceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = color)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
