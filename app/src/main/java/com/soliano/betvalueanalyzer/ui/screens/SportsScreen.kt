package com.soliano.betvalueanalyzer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.data.local.UpcomingEventEntity
import com.soliano.betvalueanalyzer.domain.CatalogCompetition
import com.soliano.betvalueanalyzer.domain.CatalogSport
import com.soliano.betvalueanalyzer.domain.SportsCatalog
import com.soliano.betvalueanalyzer.ui.AppUiState
import com.soliano.betvalueanalyzer.ui.SyncStatus
import com.soliano.betvalueanalyzer.ui.t
import com.soliano.betvalueanalyzer.ui.components.Tag
import com.soliano.betvalueanalyzer.ui.components.cleanDisplayText
import com.soliano.betvalueanalyzer.ui.components.displayEventTitle
import com.soliano.betvalueanalyzer.ui.components.displayTeamName
import com.soliano.betvalueanalyzer.ui.components.formatDate
import com.soliano.betvalueanalyzer.ui.components.matchesSearch
import com.soliano.betvalueanalyzer.ui.components.searchableText
import com.soliano.betvalueanalyzer.ui.theme.Amber
import com.soliano.betvalueanalyzer.ui.theme.Blue
import com.soliano.betvalueanalyzer.ui.theme.Danger
import com.soliano.betvalueanalyzer.ui.theme.Divider
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.SurfaceHigh
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary
import com.soliano.betvalueanalyzer.ui.theme.Violet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SportsScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    language: String = "fr",
    onOpen: (PredictionEntity) -> Unit,
    onOpenEvent: (UpcomingEventEntity) -> Unit,
    onRefresh: () -> Unit,
    onPreloadSport: (String) -> Unit,
    onToggleSportFavorite: (String, Boolean) -> Unit,
    onToggleCompetitionFavorite: (String, Boolean) -> Unit,
) {
    val now = remember(state.upcomingEvents) { System.currentTimeMillis() }
    val futureEvents = remember(state.upcomingEvents, now) { state.upcomingEvents.filter { it.commenceTime > now } }
    val eventCountBySport = remember(futureEvents) { futureEvents.groupingBy { it.sportKey }.eachCount() }
    val nextEventBySport = remember(futureEvents) { futureEvents.groupBy { it.sportKey }.mapValues { (_, events) -> events.minOf { it.commenceTime } } }
    val dynamicSports = remember(state.upcomingEvents) {
        state.upcomingEvents.groupBy { it.sportKey }.map { (key, events) ->
            CatalogSport(key, cleanDisplayText(events.first().sportTitle), emptyList())
        }
    }
    val sports = remember(dynamicSports, state.settings.favoriteSports, eventCountBySport) {
        (SportsCatalog.sports + dynamicSports).distinctBy { it.key }
            .sortedWith(
                compareByDescending<CatalogSport> { it.key in state.settings.favoriteSports }
                    .thenByDescending { (eventCountBySport[it.key] ?: 0) > 0 }
                    .thenByDescending { eventCountBySport[it.key] ?: 0 }
                    .thenBy { cleanDisplayText(it.name) }
            )
    }
    val allCompetitions = remember(state.upcomingEvents) {
        (
            SportsCatalog.sports.flatMap { it.competitions } +
                state.upcomingEvents.map { CatalogCompetition(it.competitionKey, it.sportKey, cleanDisplayText(it.competitionName)) }
            ).distinctBy { it.key }
    }
    var selectedSport by remember { mutableStateOf<String?>(null) }
    var selectedCompetition by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val searchActive = searchQuery.trim().isNotBlank()

    LaunchedEffect(sports, state.settings.favoriteSports, eventCountBySport) {
        val selectedStillExists = selectedSport in sports.map { it.key }
        val selectedHasEvents = selectedSport?.let { (eventCountBySport[it] ?: 0) > 0 } == true
        val selectedIsFavorite = selectedSport in state.settings.favoriteSports
        if (!selectedStillExists || (!selectedHasEvents && !selectedIsFavorite)) {
            selectedSport = sports.firstOrNull { it.key in state.settings.favoriteSports && (eventCountBySport[it.key] ?: 0) > 0 }?.key
                ?: sports.firstOrNull { (eventCountBySport[it.key] ?: 0) > 0 }?.key
                ?: sports.firstOrNull { it.key in state.settings.favoriteSports }?.key
                ?: sports.firstOrNull()?.key
        }
    }
    LaunchedEffect(selectedSport) {
        selectedCompetition = null
        selectedSport?.let(onPreloadSport)
    }

    val sport = sports.firstOrNull { it.key == selectedSport }
    val staticCompetitions = sport?.competitions.orEmpty()
    val dynamicCompetitions = remember(state.upcomingEvents, selectedSport) {
        state.upcomingEvents.filter { it.sportKey == selectedSport }
            .map { CatalogCompetition(it.competitionKey, it.sportKey, cleanDisplayText(it.competitionName)) }
    }
    val eventCountByCompetition = remember(futureEvents, selectedSport) {
        futureEvents
            .filter { it.sportKey == selectedSport }
            .groupingBy { it.competitionKey }
            .eachCount()
    }
    val competitions = remember(staticCompetitions, dynamicCompetitions, state.settings.favoriteCompetitions, eventCountByCompetition) {
        (staticCompetitions + dynamicCompetitions).distinctBy { it.key }
            .sortedWith(
                compareByDescending<CatalogCompetition> { it.key in state.settings.favoriteCompetitions }
                    .thenByDescending { (eventCountByCompetition[it.key] ?: 0) > 0 }
                    .thenByDescending { eventCountByCompetition[it.key] ?: 0 }
                    .thenBy { cleanDisplayText(it.name) }
            )
    }
    val matchingCompetitions = remember(searchActive, allCompetitions, sports, searchQuery, competitions) {
        if (searchActive) {
            allCompetitions.filter { competition ->
                val sportName = sports.firstOrNull { it.key == competition.sportKey }?.name.orEmpty()
                matchesSearch(searchableText(listOf(sportName, competition.name)), searchQuery)
            }.sortedBy { it.name }.take(18)
        } else {
            competitions
        }
    }
    val visibleEvents = remember(state.upcomingEvents, searchActive, searchQuery, selectedSport, selectedCompetition) {
        state.upcomingEvents.filter { event ->
            if (searchActive) {
                matchesSearch(searchableText(event), searchQuery)
            } else {
                event.sportKey == selectedSport && (selectedCompetition == null || event.competitionKey == selectedCompetition)
            }
        }.sortedBy { it.commenceTime }
    }
    val analysesById = remember(state.predictions) { state.predictions.associateBy { it.id } }
    val syncing = state.syncStatus == SyncStatus.Syncing
    val groupedEvents = remember(visibleEvents) { visibleEvents.groupBy { dayHeader(it.commenceTime) } }
    val activeSports = remember(eventCountBySport) { eventCountBySport.count { it.value > 0 } }
    val nextStart = remember(futureEvents) { futureEvents.minByOrNull { it.commenceTime }?.commenceTime }

    LazyColumn(
        modifier = Modifier.padding(contentPadding).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Blue.copy(alpha = 0.34f)),
            ) {
                Box(
                    modifier = Modifier.background(
                        Brush.linearGradient(
                            listOf(Blue.copy(alpha = 0.24f), Violet.copy(alpha = 0.16f), MaterialTheme.colorScheme.surface)
                        ),
                        RoundedCornerShape(30.dp),
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                                Text("SPORT RADAR", style = MaterialTheme.typography.labelLarge, color = Amber)
                                Text(t(language, "Calendrier & analyses", "Schedule & analysis", "Calendario y análisis", "Kalender & Analysen"), style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    if (searchActive) t(language, "${visibleEvents.size} résultat(s) pour « ${searchQuery.trim()} »", "${visibleEvents.size} result(s) for “${searchQuery.trim()}”", "${visibleEvents.size} resultado(s) para « ${searchQuery.trim()} »", "${visibleEvents.size} Ergebnis(se) für „${searchQuery.trim()}“")
                                    else t(language, "${futureEvents.size} événements à venir • $activeSports sports actifs", "${futureEvents.size} upcoming events • $activeSports active sports", "${futureEvents.size} próximos eventos • $activeSports deportes activos", "${futureEvents.size} kommende Ereignisse • $activeSports aktive Sportarten"),
                                    color = TextSecondary,
                                )
                            }
                            Surface(shape = CircleShape, color = Mint.copy(alpha = 0.12f), border = BorderStroke(1.dp, Mint.copy(alpha = 0.38f))) {
                                IconButton(onClick = onRefresh, enabled = !syncing) {
                                    if (syncing) CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp, color = Mint)
                                    else Icon(Icons.Outlined.Refresh, t(language, "Actualiser", "Refresh", "Actualizar", "Aktualisieren"), tint = Mint)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            HeaderMetric("${futureEvents.size}", t(language, "événements", "events", "eventos", "Ereignisse"), Blue, Modifier.weight(1f))
                            HeaderMetric("$activeSports", t(language, "sports actifs", "active sports", "deportes activos", "aktive Sportarten"), Mint, Modifier.weight(1f))
                            HeaderMetric(nextStart?.let(::formatDate) ?: "—", t(language, "prochain", "next", "próximo", "nächstes"), Amber, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            SearchBox(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClear = { searchQuery = "" },
                language = language,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(t(language, "Sports", "Sports", "Deportes", "Sport"), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(sports, key = { it.key }) { item ->
                        SportSelector(
                            label = cleanDisplayText(item.name),
                            count = eventCountBySport[item.key] ?: 0,
                            nextTime = nextEventBySport[item.key],
                            accent = sportAccent(item.key),
                            selected = !searchActive && selectedSport == item.key,
                            favorite = item.key in state.settings.favoriteSports,
                            language = language,
                            onSelect = {
                                searchQuery = ""
                                selectedSport = item.key
                            },
                            onFavorite = { onToggleSportFavorite(item.key, it) },
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    if (searchActive) t(language, "Compétitions trouvées", "Competitions found", "Competiciones encontradas", "Gefundene Wettbewerbe")
                    else t(language, "Ligues, tournois, GP", "Leagues, tournaments, GPs", "Ligas, torneos, GP", "Ligen, Turniere, GPs"),
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!searchActive) {
                        item {
                            SimpleSelector(t(language, "Toutes", "All", "Todas", "Alle"), selectedCompetition == null) { selectedCompetition = null }
                        }
                    }
                    items(matchingCompetitions, key = { it.key }) { competition ->
                        CompetitionSelector(
                            label = cleanDisplayText(competition.name),
                            count = eventCountByCompetition[competition.key] ?: 0,
                            selected = !searchActive && selectedCompetition == competition.key,
                            favorite = competition.key in state.settings.favoriteCompetitions,
                            language = language,
                            onSelect = {
                                selectedSport = competition.sportKey
                                selectedCompetition = competition.key
                                searchQuery = ""
                            },
                            onFavorite = { onToggleCompetitionFavorite(competition.key, it) },
                        )
                    }
                }
            }
        }

        if (visibleEvents.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, Divider.copy(alpha = 0.8f))) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(
                            if (searchActive) t(language, "Aucun résultat", "No result", "Sin resultados", "Kein Ergebnis")
                            else t(language, "Calendrier non publié ici", "Schedule not published here", "Calendario no publicado aquí", "Kalender hier nicht veröffentlicht"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (searchActive) t(language, "Essaie le nom d'une équipe, d'un pays, d'une compétition ou d'une course.", "Try a team, country, competition or race name.", "Prueba con un equipo, país, competición o carrera.", "Versuche Team, Land, Wettbewerb oder Rennen.")
                            else if (selectedCompetition != null) t(language, "Cette compétition est bien suivie, mais aucune date fiable n'est publiée par les sources ouvertes pour le moment. Reviens sur « Toutes » pour voir les événements déjà disponibles.", "This competition is tracked, but no reliable date is currently published by open sources. Go back to All to see available events.", "Esta competición se sigue, pero las fuentes abiertas aún no publican fechas fiables. Vuelve a Todas para ver eventos disponibles.", "Dieser Wettbewerb wird verfolgt, aber offene Quellen zeigen noch kein zuverlässiges Datum. Zurück zu Alle zeigt verfügbare Events.")
                            else t(language, "Ce sport est gardé dans la liste, mais les sources ouvertes n'ont pas encore publié d'événement exploitable.", "This sport stays listed, but open sources have not published a usable event yet.", "Este deporte queda en la lista, pero las fuentes abiertas aún no publican un evento útil.", "Diese Sportart bleibt gelistet, aber offene Quellen haben noch kein nutzbares Ereignis veröffentlicht."),
                            color = TextSecondary,
                        )
                    }
                }
            }
        } else {
            groupedEvents.forEach { (day, events) ->
                item(key = "day_$day") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(day, style = MaterialTheme.typography.titleMedium)
                        Text(t(language, "${events.size} événement(s)", "${events.size} event(s)", "${events.size} evento(s)", "${events.size} Ereignis(se)"), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                }
                items(events, key = { it.id }) { event ->
                    val analysis = event.analysisId?.let(analysesById::get)
                    CalendarEventCard(
                        event = event,
                        analysis = analysis,
                        language = language,
                        onClick = { if (analysis != null) onOpen(analysis) else onOpenEvent(event) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderMetric(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit, language: String = "fr") {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextSecondary) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, t(language, "Effacer", "Clear", "Borrar", "Löschen"), tint = TextSecondary) }
            }
        },
        placeholder = { Text(t(language, "Rechercher un match, pays, équipe, tournoi…", "Search match, country, team, tournament…", "Buscar partido, país, equipo, torneo…", "Spiel, Land, Team, Turnier suchen…")) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceHigh,
            unfocusedContainerColor = SurfaceHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Mint,
        ),
    )
}

@Composable
private fun SportSelector(
    label: String,
    count: Int,
    nextTime: Long?,
    accent: Color,
    selected: Boolean,
    favorite: Boolean,
    language: String,
    onSelect: () -> Unit,
    onFavorite: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.width(172.dp),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else SurfaceHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.68f) else Divider.copy(alpha = 0.82f)),
    ) {
        Column(Modifier.clickable(onClick = onSelect).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(cleanDisplayText(label), color = if (selected) accent else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onFavorite(!favorite) }, modifier = Modifier.size(34.dp)) {
                    Icon(if (favorite) Icons.Rounded.Star else Icons.Outlined.StarBorder, t(language, "Favori", "Favorite", "Favorito", "Favorit"), tint = if (favorite) Amber else TextSecondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniPill(if (count > 0) t(language, "$count év.", "$count ev.", "$count ev.", "$count Ev.") else t(language, "calendrier", "schedule", "calendario", "Kalender"), if (count > 0) accent else TextSecondary)
                nextTime?.let { MiniPill(formatDate(it), Amber) }
            }
        }
    }
}

@Composable
private fun CompetitionSelector(
    label: String,
    count: Int,
    selected: Boolean,
    favorite: Boolean,
    language: String,
    onSelect: () -> Unit,
    onFavorite: (Boolean) -> Unit,
) {
    val accent = if (count > 0) Blue else TextSecondary
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Blue.copy(alpha = 0.16f) else SurfaceHigh,
        border = BorderStroke(1.dp, if (selected) Blue.copy(alpha = 0.55f) else Divider),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.clickable(onClick = onSelect).padding(start = 13.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(cleanDisplayText(label), color = if (selected) Blue else MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (count > 0) t(language, "$count événement(s)", "$count event(s)", "$count evento(s)", "$count Ereignis(se)")
                    else t(language, "pas encore publié", "not published yet", "aún no publicado", "noch nicht veröffentlicht"),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { onFavorite(!favorite) }) {
                Icon(if (favorite) Icons.Rounded.Star else Icons.Outlined.StarBorder, t(language, "Favori", "Favorite", "Favorito", "Favorit"), tint = if (favorite) Amber else TextSecondary)
            }
        }
    }
}

@Composable
private fun MiniPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f), border = BorderStroke(1.dp, color.copy(alpha = 0.25f))) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun SimpleSelector(label: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onSelect),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Blue.copy(alpha = 0.14f) else SurfaceHigh,
        border = BorderStroke(1.dp, if (selected) Blue.copy(alpha = 0.55f) else Divider),
    ) {
        Text(cleanDisplayText(label), modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), color = if (selected) Blue else TextSecondary)
    }
}

@Composable
private fun CalendarEventCard(
    event: UpcomingEventEntity,
    analysis: PredictionEntity?,
    language: String,
    onClick: () -> Unit,
) {
    val accent = sportAccent(event.sportKey)
    val hasTeams = event.participantA.isNotBlank() && event.participantB.isNotBlank()
    val deepAvailable = event.deepAnalysisAvailable()
    val statusLabel = when {
        analysis != null -> t(language, "Analyse prête", "Analysis ready", "Análisis listo", "Analyse bereit")
        deepAvailable -> t(language, "Lancer l'analyse", "Run analysis", "Lanzar análisis", "Analyse starten")
        else -> t(language, "Événement suivi", "Tracked event", "Evento seguido", "Verfolgtes Ereignis")
    }
    val statusColor = when {
        analysis != null -> Mint
        deepAvailable -> accent
        else -> TextSecondary
    }
    val statusBackground = when {
        analysis != null -> Mint.copy(alpha = 0.16f)
        deepAvailable -> accent.copy(alpha = 0.14f)
        else -> SurfaceHigh.copy(alpha = 0.92f)
    }
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.20f), MaterialTheme.colorScheme.surface, SurfaceHigh.copy(alpha = 0.72f))),
                RoundedCornerShape(28.dp),
            )
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tag(event.sportTitle, accent)
                        Tag(event.eventTypeLabel(language), Violet)
                    }
                    Text(formatDate(event.commenceTime), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(cleanDisplayText(event.competitionName), style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (hasTeams) {
                        MatchTeams(event, accent)
                    } else {
                        Surface(shape = RoundedCornerShape(20.dp), color = SurfaceHigh.copy(alpha = 0.86f), border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))) {
                            Text(
                                displayEventTitle(event),
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(cleanDisplayText(event.sourceName.removeSuffix(" public")), style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1)
                    Surface(shape = RoundedCornerShape(50), color = statusBackground, border = BorderStroke(1.dp, statusColor.copy(alpha = 0.22f))) {
                        Text(
                            cleanDisplayText(statusLabel),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchTeams(event: UpcomingEventEntity, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TeamNameBox(displayTeamName(event.participantA), accent, Modifier.weight(1f))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.background.copy(alpha = 0.62f), border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))) {
            Text(
                "VS",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Black,
            )
        }
        TeamNameBox(displayTeamName(event.participantB), Violet, Modifier.weight(1f))
    }
}

@Composable
private fun TeamNameBox(name: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.50f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(teamInitials(name), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Black)
            }
            Text(
                name,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
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

private fun UpcomingEventEntity.eventTypeLabel(language: String): String = when (eventType) {
    "TOURNAMENT" -> t(language, "Tournoi", "Tournament", "Torneo", "Turnier")
    "GP" -> "Grand Prix"
    "RACE" -> t(language, "Course", "Race", "Carrera", "Rennen")
    "EVENT" -> t(language, "Événement", "Event", "Evento", "Ereignis")
    else -> t(language, "Match", "Match", "Partido", "Spiel")
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
    "field_hockey",
    "cricket",
    "australian_football",
    "tennis",
    "golf",
    "mma",
    "boxing",
    "nascar",
    "darts",
    "snooker",
    "athletics",
    "racing",
    "cycling",
) || eventType == "GP"

private fun sportAccent(sportKey: String): Color = when (sportKey.substringBefore('/')) {
    "soccer", "rugby", "golf", "snooker", "darts" -> Mint
    "basketball", "baseball", "football", "hockey", "handball", "volleyball", "field_hockey", "australian_football", "cricket" -> Blue
    "cycling", "athletics" -> Amber
    "racing", "nascar", "tennis" -> Violet
    "mma", "boxing" -> Danger
    else -> Violet
}

private fun dayHeader(timestamp: Long): String {
    val raw = SimpleDateFormat("EEEE dd MMM", Locale.FRANCE).format(Date(timestamp))
    return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRANCE) else it.toString() }
}
