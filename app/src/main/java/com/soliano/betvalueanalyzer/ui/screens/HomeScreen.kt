package com.soliano.betvalueanalyzer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.soliano.betvalueanalyzer.data.local.LiveEventEntity
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.data.local.UpcomingEventEntity
import com.soliano.betvalueanalyzer.ui.AppUiState
import com.soliano.betvalueanalyzer.ui.SyncStatus
import com.soliano.betvalueanalyzer.ui.t
import com.soliano.betvalueanalyzer.ui.components.PredictionCard
import com.soliano.betvalueanalyzer.ui.components.SectionTitle
import com.soliano.betvalueanalyzer.ui.components.cleanDisplayText
import com.soliano.betvalueanalyzer.ui.components.displayEventTitle
import com.soliano.betvalueanalyzer.ui.components.formatDate
import com.soliano.betvalueanalyzer.ui.components.predictionCategoryKey
import com.soliano.betvalueanalyzer.ui.theme.Amber
import com.soliano.betvalueanalyzer.ui.theme.Blue
import com.soliano.betvalueanalyzer.ui.theme.Danger
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary
import com.soliano.betvalueanalyzer.ui.theme.Violet
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class AutomaticFilter(val label: String) {
    All("Tous"), Safe("Safe"), Mixed("Mitigé"), Exotic("Exotique")
}

private fun AutomaticFilter.label(language: String): String = when (this) {
    AutomaticFilter.All -> t(language, "Tous", "All", "Todos", "Alle")
    AutomaticFilter.Safe -> t(language, "Safe", "Safe", "Seguro", "Sicher")
    AutomaticFilter.Mixed -> t(language, "Mitigé", "Mixed", "Mitigado", "Gemischt")
    AutomaticFilter.Exotic -> t(language, "Exotique", "Exotic", "Exótico", "Exotisch")
}

@Composable
fun HomeScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    language: String = "fr",
    onOpen: (PredictionEntity) -> Unit,
    onOpenEvent: (UpcomingEventEntity) -> Unit,
    onRefresh: () -> Unit,
) {
    var filter by remember { mutableStateOf(AutomaticFilter.All) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ordered = remember(state.predictions, state.settings.favoriteSports, state.settings.favoriteCompetitions) {
        state.topPredictions
    }
    val automaticBets = remember(state.predictions) { state.automaticValueBets }
    val visible = remember(filter, ordered) {
        when (filter) {
            AutomaticFilter.All -> ordered
            AutomaticFilter.Safe -> ordered.filter { predictionCategoryKey(it.category) == "safe" }
            AutomaticFilter.Mixed -> ordered.filter { predictionCategoryKey(it.category) == "mitige" }
            AutomaticFilter.Exotic -> ordered.filter { predictionCategoryKey(it.category) == "exotique" }
        }
    }
    val syncing = state.syncStatus == SyncStatus.Syncing
    val staleData = state.settings.lastSyncEpoch > 0L &&
        System.currentTimeMillis() - state.settings.lastSyncEpoch > 2 * 60 * 60 * 1000L
    val now = remember(state.upcomingEvents, state.liveEvents) { System.currentTimeMillis() }
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val liveNow = remember(state.liveEvents) { state.liveEvents.filter { it.isLive }.take(8) }
    val todayEvents = remember(state.upcomingEvents, now, today) {
        state.upcomingEvents
            .filter { it.commenceTime >= now && eventLocalDate(it.commenceTime) == today }
            .take(10)
    }
    val tomorrowEvents = remember(state.upcomingEvents, tomorrow) {
        state.upcomingEvents
            .filter { eventLocalDate(it.commenceTime) == tomorrow }
            .take(10)
    }
    val watchEvents = remember(state.upcomingEvents, now) {
        state.upcomingEvents
            .filter { it.commenceTime >= now && it.analysisId == null }
            .take(10)
    }
    val favoriteCompetitionEvents = remember(state.upcomingEvents, state.settings.favoriteCompetitions) {
        state.favoriteCompetitionUpcomingEvents
    }
    val favoriteSportEvents = remember(state.upcomingEvents, state.settings.favoriteSports, state.settings.favoriteCompetitions) {
        state.favoriteSportUpcomingEvents
    }
    val nextStart = remember(state.upcomingEvents, now) {
        state.upcomingEvents.firstOrNull { it.commenceTime > now }?.commenceTime
    }
    val showFavoriteHub = state.hasConfiguredFavorites || favoriteCompetitionEvents.isNotEmpty() || favoriteSportEvents.isNotEmpty()
    val analysisSectionIndex = 3 +
        (if (showFavoriteHub) 1 else 0) +
        (if (staleData && state.predictions.isNotEmpty()) 1 else 0)
    val showStrongSignals: () -> Unit = {
        filter = AutomaticFilter.Safe
        scope.launch { listState.animateScrollToItem(analysisSectionIndex) }
    }

    LazyColumn(
        modifier = Modifier.padding(contentPadding).statusBarsPadding().testTag("home_screen"),
        state = listState,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("BETVALUE LIVE", style = MaterialTheme.typography.labelLarge, color = Mint)
                    Text(t(language, "Prochains événements sportifs", "Upcoming sports events", "Próximos eventos deportivos", "Nächste Sportereignisse"), style = MaterialTheme.typography.headlineMedium)
                    Text(t(language, "Détection et prédictions Internet sans clé", "Internet detection and predictions without API key", "Detección y predicciones por Internet sin clave", "Internet-Erkennung und Prognosen ohne Schlüssel"), color = TextSecondary)
                }
                IconButton(onClick = onRefresh, enabled = !syncing) {
                    if (syncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, "Actualiser", tint = Mint)
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.Transparent) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(listOf(Mint.copy(alpha = 0.17f), Violet.copy(alpha = 0.10f), SurfaceHigh)),
                            RoundedCornerShape(24.dp),
                        )
                        .padding(19.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        LiveKpi(Icons.Outlined.Bolt, state.upcomingEvents.size.toString(), t(language, "événements", "events", "eventos", "Ereignisse"), Mint)
                        LiveKpi(
                            Icons.Outlined.AutoGraph,
                            automaticBets.size.toString(),
                            t(language, "signaux forts", "strong signals", "señales fuertes", "starke Signale"),
                            Violet,
                            onClick = showStrongSignals,
                        )
                        LiveKpi(Icons.Outlined.Schedule, nextStart?.let { formatDate(it) } ?: "—", t(language, "prochain départ", "next start", "próximo inicio", "nächster Start"), Amber)
                    }
                    val syncText = when (val status = state.syncStatus) {
                        SyncStatus.Idle -> t(language, "Le flux public est prêt", "Public feed ready", "Flujo público listo", "Öffentlicher Feed bereit")
                        SyncStatus.Syncing -> t(language, "Recherche des prochains événements…", "Searching upcoming events…", "Buscando próximos eventos…", "Suche nach nächsten Ereignissen…")
                        is SyncStatus.Ready -> status.message
                        is SyncStatus.Error -> status.message
                    }
                    Text(
                        cleanDisplayText(syncText),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.syncStatus is SyncStatus.Error) Danger else TextSecondary,
                    )
                }
            }
        }

        if (liveNow.isNotEmpty()) {
            item {
                HomeLiveRail(
                    title = t(language, "En direct", "Live now", "En vivo", "Live"),
                    subtitle = t(language, "Scores ou classements déjà confirmés par les flux publics", "Scores or standings already confirmed by public feeds", "Marcadores o clasificaciones confirmados por fuentes públicas", "Scores oder Stände aus öffentlichen Feeds"),
                    events = liveNow,
                )
            }
        }

        if (todayEvents.isNotEmpty()) {
            item {
                HomeEventRail(
                    title = t(language, "Aujourd’hui", "Today", "Hoy", "Heute"),
                    subtitle = t(language, "Les prochains matchs, courses et tournois du jour", "Upcoming matches, races and tournaments today", "Partidos, carreras y torneos de hoy", "Heutige Spiele, Rennen und Turniere"),
                    events = todayEvents,
                    accent = Mint,
                    onOpenEvent = onOpenEvent,
                )
            }
        }

        if (tomorrowEvents.isNotEmpty()) {
            item {
                HomeEventRail(
                    title = t(language, "Demain", "Tomorrow", "Mañana", "Morgen"),
                    subtitle = t(language, "À préparer sans attendre le jour J", "Prepare before match day", "Preparar antes del día del evento", "Vor dem Ereignistag vorbereiten"),
                    events = tomorrowEvents,
                    accent = Blue,
                    onOpenEvent = onOpenEvent,
                )
            }
        }

        if (showFavoriteHub) {
            item {
                FavoriteHomeHub(
                    competitionEvents = favoriteCompetitionEvents,
                    sportEvents = favoriteSportEvents,
                    hasConfiguredFavorites = state.hasConfiguredFavorites,
                    syncing = syncing,
                    language = language,
                    onOpenEvent = onOpenEvent,
                    onRefresh = onRefresh,
                )
            }
        }

        if (watchEvents.isNotEmpty()) {
            item {
                HomeEventRail(
                    title = t(language, "À surveiller", "To monitor", "A vigilar", "Beobachten"),
                    subtitle = t(language, "Événements détectés mais pas encore assez solides pour une analyse complète", "Events detected but not solid enough for a full analysis yet", "Eventos detectados aún sin datos suficientes", "Erkannt, aber noch nicht robust genug"),
                    events = watchEvents,
                    accent = Amber,
                    onOpenEvent = onOpenEvent,
                )
            }
        }


        if (staleData && state.predictions.isNotEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = Amber.copy(alpha = 0.11f)) {
                    Text(
                        "Données anciennes : dernière actualisation le ${formatDate(state.settings.lastSyncEpoch)}.",
                        modifier = Modifier.padding(15.dp),
                        color = Amber,
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(
                    t(language, "Analyses pré-match", "Pre-match analysis", "Análisis prepartido", "Vorab-Analyse"),
                    t(language, "Scénario probable, confiance et points de vigilance", "Likely scenario, confidence and watch points", "Escenario probable, confianza y puntos clave", "Wahrscheinliches Szenario, Vertrauen und Warnpunkte"),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AutomaticFilter.entries) { item ->
                        val itemColor = item.filterColor()
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item.label(language)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = itemColor.copy(alpha = 0.14f),
                                selectedLabelColor = itemColor,
                            ),
                        )
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (syncing) t(language, "Analyse en cours…", "Analysis running…", "Análisis en curso…", "Analyse läuft…")
                            else t(language, "Aucun signal disponible", "No signal available", "No hay señal disponible", "Kein Signal verfügbar"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            t(language, "Aucun événement n'a encore été chargé. Vérifiez Internet puis relancez l'actualisation.", "No event has been loaded yet. Check Internet, then refresh again.", "Aún no se ha cargado ningún evento. Comprueba Internet y actualiza.", "Noch kein Ereignis geladen. Prüfe Internet und aktualisiere erneut."),
                            color = TextSecondary,
                        )
                        Button(onClick = onRefresh, enabled = !syncing) { Text(t(language, "Actualiser maintenant", "Refresh now", "Actualizar ahora", "Jetzt aktualisieren")) }
                    }
                }
            }
        } else {
            items(visible, key = { it.id }) { prediction ->
                PredictionCard(prediction = prediction, onClick = { onOpen(prediction) })
            }
        }
    }
}

private val homeDateZone: ZoneId = ZoneId.systemDefault()

private fun eventLocalDate(value: Long): LocalDate =
    Instant.ofEpochMilli(value).atZone(homeDateZone).toLocalDate()

@Composable
private fun HomeEventRail(
    title: String,
    subtitle: String,
    events: List<UpcomingEventEntity>,
    accent: Color,
    onOpenEvent: (UpcomingEventEntity) -> Unit,
) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            SectionTitle(title, subtitle)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(events, key = { it.id }) { event ->
                    HomeAgendaCard(event = event, accent = accent, onClick = { onOpenEvent(event) })
                }
            }
        }
    }
}

@Composable
private fun HomeAgendaCard(event: UpcomingEventEntity, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(250.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceHigh.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(cleanDisplayText(event.sportTitle), style = MaterialTheme.typography.labelLarge, color = accent)
            Text(cleanDisplayText(event.competitionName), style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1)
            Text(cleanDisplayText(displayEventTitle(event)), style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(formatDate(event.commenceTime), style = MaterialTheme.typography.labelMedium, color = Amber)
            Text("Source : ${cleanDisplayText(event.sourceName)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun HomeLiveRail(title: String, subtitle: String, events: List<LiveEventEntity>) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            SectionTitle(title, subtitle)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(events, key = { it.id }) { event ->
                    HomeLiveCard(event)
                }
            }
        }
    }
}

@Composable
private fun HomeLiveCard(event: LiveEventEntity) {
    Surface(
        modifier = Modifier.width(250.dp),
        shape = RoundedCornerShape(18.dp),
        color = Mint.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, Mint.copy(alpha = 0.30f)),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(cleanDisplayText(event.sportTitle), style = MaterialTheme.typography.labelLarge, color = Mint)
            Text(cleanDisplayText(event.competitionName), style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1)
            Text(cleanDisplayText(event.eventName), style = MaterialTheme.typography.titleMedium, maxLines = 2)
            val score = if (event.homeScore != null && event.awayScore != null) "${event.homeScore} - ${event.awayScore}" else event.statSummary.lines().firstOrNull { "Top 3" in it }.orEmpty()
            Text(cleanDisplayText(score.ifBlank { event.statusDescription }), style = MaterialTheme.typography.bodyMedium, color = Mint, maxLines = 2)
            Text(cleanDisplayText(event.displayClock.ifBlank { event.statusDescription }), style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
        }
    }
}

private fun AutomaticFilter.filterColor(): Color = when (this) {
    AutomaticFilter.All -> Mint
    AutomaticFilter.Safe -> Mint
    AutomaticFilter.Mixed -> Amber
    AutomaticFilter.Exotic -> Danger
}

@Composable
private fun FavoriteHomeHub(
    competitionEvents: List<UpcomingEventEntity>,
    sportEvents: List<UpcomingEventEntity>,
    hasConfiguredFavorites: Boolean,
    syncing: Boolean,
    language: String,
    onOpenEvent: (UpcomingEventEntity) -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, Mint.copy(alpha = 0.24f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            SectionTitle(
                t(language, "Accueil favoris", "Favorite home", "Inicio favoritos", "Favoriten-Start"),
                t(language, "Matchs, courses et tournois tirés de tes compétitions favorites", "Matches, races and tournaments from your favorite competitions", "Partidos, carreras y torneos de tus competiciones favoritas", "Spiele, Rennen und Turniere aus deinen Favoriten"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FavoriteHubMetric(t(language, "Compétitions", "Competitions", "Competiciones", "Wettbewerbe"), competitionEvents.size.toString(), Mint)
                FavoriteHubMetric(t(language, "Sports favoris", "Favorite sports", "Deportes favoritos", "Favorisierte Sportarten"), sportEvents.size.toString(), Blue)
                FavoriteHubMetric(t(language, "Total suivi", "Tracked total", "Total seguido", "Verfolgt gesamt"), (competitionEvents.size + sportEvents.size).toString(), Amber)
            }
            when {
                competitionEvents.isNotEmpty() -> {
                    FavoriteEventsRail(
                        title = t(language, "Compétitions favorites en priorité", "Favorite competitions first", "Competiciones favoritas primero", "Favorisierte Wettbewerbe zuerst"),
                        subtitle = t(language, "Ce sont les événements issus directement des ligues/tournois/GP que tu as étoilés dans Sports.", "These events come from the leagues, tournaments or GPs you starred in Sports.", "Estos eventos vienen de las ligas, torneos o GP marcados en Deportes.", "Diese Ereignisse kommen aus den markierten Ligen, Turnieren oder GPs."),
                        events = competitionEvents.take(14),
                        onOpenEvent = onOpenEvent,
                    )
                    if (sportEvents.isNotEmpty()) {
                        FavoriteEventsRail(
                            title = t(language, "Autres événements de tes sports favoris", "Other events from favorite sports", "Otros eventos de tus deportes favoritos", "Weitere Ereignisse deiner Sportfavoriten"),
                            subtitle = t(language, "Moins prioritaire que tes compétitions favorites, mais gardé sous surveillance.", "Lower priority than favorite competitions, but still monitored.", "Menos prioritario que tus competiciones favoritas, pero vigilado.", "Weniger wichtig als Favoriten-Wettbewerbe, aber weiter überwacht."),
                            events = sportEvents.take(10),
                            onOpenEvent = onOpenEvent,
                        )
                    }
                }
                sportEvents.isNotEmpty() -> {
                    FavoriteEventsRail(
                        title = t(language, "Sports favoris détectés", "Favorite sports detected", "Deportes favoritos detectados", "Sportfavoriten erkannt"),
                        subtitle = t(language, "Ajoute aussi des ligues/tournois en favoris pour rendre l’accueil encore plus ciblé.", "Add leagues/tournaments as favorites to make the home screen sharper.", "Añade ligas/torneos favoritos para afinar el inicio.", "Markiere auch Ligen/Turniere, um den Start genauer zu machen."),
                        events = sportEvents.take(14),
                        onOpenEvent = onOpenEvent,
                    )
                }
                hasConfiguredFavorites -> {
                    FavoriteEmptyCard(
                        title = t(language, "Aucun événement favori chargé pour l’instant", "No favorite event loaded yet", "Aún no hay eventos favoritos cargados", "Noch keine Favoriten-Ereignisse geladen"),
                        text = t(language, "Tes favoris sont bien mémorisés. Actualise pour forcer la recherche des prochains matchs, courses ou tournois liés à ces compétitions.", "Your favorites are saved. Refresh to search upcoming matches, races or tournaments linked to them.", "Tus favoritos están guardados. Actualiza para buscar partidos, carreras o torneos vinculados.", "Deine Favoriten sind gespeichert. Aktualisiere, um passende Spiele, Rennen oder Turniere zu suchen."),
                        action = t(language, "Actualiser les favoris", "Refresh favorites", "Actualizar favoritos", "Favoriten aktualisieren"),
                        syncing = syncing,
                        onRefresh = onRefresh,
                    )
                }
                else -> {
                    FavoriteEmptyCard(
                        title = t(language, "Choisis tes compétitions favorites", "Choose your favorite competitions", "Elige tus competiciones favoritas", "Wähle deine Favoriten"),
                        text = t(language, "Dans l’onglet Sports, étoile tes ligues, tournois, Grands Prix ou courses. Ensuite l’accueil affichera ces événements en premier.", "In Sports, star leagues, tournaments, GPs or races. Home will show them first.", "En Deportes, marca ligas, torneos, GP o carreras. Inicio los mostrará primero.", "Markiere in Sport Ligen, Turniere, GPs oder Rennen. Der Start zeigt sie zuerst."),
                        action = t(language, "Actualiser après ajout", "Refresh after adding", "Actualizar después", "Nach dem Hinzufügen aktualisieren"),
                        syncing = syncing,
                        onRefresh = onRefresh,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.FavoriteHubMetric(label: String, value: String, color: Color) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
private fun FavoriteEventsRail(
    title: String,
    subtitle: String,
    events: List<UpcomingEventEntity>,
    onOpenEvent: (UpcomingEventEntity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(events, key = { it.id }) { event ->
                FavoriteEventCard(event, onOpenEvent)
            }
        }
    }
}

@Composable
private fun FavoriteEmptyCard(
    title: String,
    text: String,
    action: String,
    syncing: Boolean,
    onRefresh: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(18.dp), color = SurfaceHigh, border = BorderStroke(1.dp, Amber.copy(alpha = 0.24f))) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Amber)
            Text(text, color = TextSecondary)
            Button(onClick = onRefresh, enabled = !syncing) {
                Text(action)
            }
        }
    }
}

@Composable
private fun RowScope.LiveKpi(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.weight(1f).then(clickModifier),
    ) {
        Box(Modifier.size(32.dp).background(color.copy(alpha = 0.14f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
private fun FavoriteEventCard(event: UpcomingEventEntity, onOpen: (UpcomingEventEntity) -> Unit) {
    val accent = sportAccent(event.sportKey)
    val deepAvailable = event.deepAnalysisAvailable()
    Surface(
        modifier = Modifier.width(250.dp).clickable { onOpen(event) },
        shape = RoundedCornerShape(20.dp),
        color = SurfaceHigh,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(cleanDisplayText(event.sportTitle), style = MaterialTheme.typography.labelMedium, color = accent)
            Text(cleanDisplayText(event.competitionName), style = MaterialTheme.typography.labelLarge, color = Mint, maxLines = 1)
            Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = 0.12f)) {
                Text(
                    displayEventTitle(event),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    maxLines = 2,
                )
            }
            Text(formatDate(event.commenceTime), color = TextSecondary)
            Text(if (deepAvailable) "Toucher pour recalculer en profondeur" else "Événement favori suivi automatiquement", color = TextSecondary)
        }
    }
}

private fun UpcomingEventEntity.deepAnalysisAvailable(): Boolean = sportKey.substringBefore('/') in setOf(
    "soccer",
    "basketball",
    "baseball",
    "rugby",
    "hockey",
    "football",
    "handball",
    "volleyball",
    "cricket",
    "australian_football",
    "tennis",
    "golf",
    "mma",
    "boxing",
    "nascar",
    "darts",
    "athletics",
    "racing",
    "cycling",
) || eventType == "GP"

private fun sportAccent(sportKey: String): Color = when (sportKey.substringBefore('/')) {
    "soccer", "rugby", "golf", "darts" -> Mint
    "basketball", "baseball", "football", "hockey", "handball", "volleyball", "australian_football", "cricket" -> Blue
    "cycling", "athletics" -> Amber
    "racing", "nascar", "tennis" -> Violet
    "mma", "boxing" -> Danger
    else -> Violet
}
