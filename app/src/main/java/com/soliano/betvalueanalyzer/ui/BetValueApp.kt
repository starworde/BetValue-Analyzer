package com.soliano.betvalueanalyzer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.soliano.betvalueanalyzer.data.local.LiveEventEntity
import com.soliano.betvalueanalyzer.data.local.PredictionEntity
import com.soliano.betvalueanalyzer.ui.screens.HomeScreen
import com.soliano.betvalueanalyzer.ui.screens.LiveDetailScreen
import com.soliano.betvalueanalyzer.ui.screens.LiveScreen
import com.soliano.betvalueanalyzer.ui.screens.SportsScreen
import com.soliano.betvalueanalyzer.ui.screens.OnboardingScreen
import com.soliano.betvalueanalyzer.ui.screens.PredictionDetailScreen
import com.soliano.betvalueanalyzer.ui.screens.SettingsScreen
import com.soliano.betvalueanalyzer.ui.screens.DeepAnalysisScreen
import com.soliano.betvalueanalyzer.ui.theme.Mint
import com.soliano.betvalueanalyzer.ui.theme.Night
import com.soliano.betvalueanalyzer.ui.theme.Surface
import com.soliano.betvalueanalyzer.ui.theme.TextSecondary

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Accueil", Icons.Outlined.Home),
    Live("Live", Icons.Outlined.Bolt),
    Sports("Sports", Icons.Outlined.SportsSoccer),
    Settings("Réglages", Icons.Outlined.Settings),
}

private fun destinationLabel(destination: Destination, language: String): String = when (destination) {
    Destination.Home -> t(language, "Accueil", "Home", "Inicio", "Start")
    Destination.Live -> t(language, "Live", "Live", "En vivo", "Live")
    Destination.Sports -> t(language, "Sports", "Sports", "Deportes", "Sport")
    Destination.Settings -> t(language, "Réglages", "Settings", "Ajustes", "Einstellungen")
}

@Composable
fun BetValueApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val deepAnalysis by viewModel.deepAnalysis.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var destination by remember { mutableStateOf(Destination.Home) }
    var selectedPrediction by remember { mutableStateOf<PredictionEntity?>(null) }
    var selectedLiveEvent by remember { mutableStateOf<LiveEventEntity?>(null) }
    val language = state.settings.appLanguage

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(deepAnalysis) {
        val ready = deepAnalysis as? DeepAnalysisStatus.Ready ?: return@LaunchedEffect
        selectedLiveEvent = null
        selectedPrediction = ready.prediction
        viewModel.consumeDeepAnalysis()
    }
    LaunchedEffect(destination) {
        if (destination == Destination.Live) viewModel.preloadLive()
    }

    if (!state.isReady) {
        Box(Modifier.fillMaxSize())
        return
    }
    if (!state.settings.ageConfirmed) {
        OnboardingScreen(onConfirm = viewModel::confirmAge)
        return
    }

    val analysisOpen = deepAnalysis is DeepAnalysisStatus.Running || deepAnalysis is DeepAnalysisStatus.Error
    val detailOpen = selectedPrediction != null || selectedLiveEvent != null || analysisOpen
    BackHandler(enabled = true) {
        when {
            analysisOpen -> viewModel.dismissDeepAnalysis()
            selectedPrediction != null -> selectedPrediction = null
            selectedLiveEvent != null -> selectedLiveEvent = null
            destination != Destination.Home -> destination = Destination.Home
            else -> Unit
        }
    }
    Scaffold(
        containerColor = Night,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!detailOpen) {
                NavigationBar(modifier = Modifier.navigationBarsPadding(), containerColor = Surface) {
                    Destination.entries.forEach { item ->
                        val label = destinationLabel(item, language)
                        NavigationBarItem(
                            modifier = Modifier.testTag("nav_${item.name}"),
                            selected = destination == item,
                            onClick = {
                                if (item == Destination.Live) viewModel.preloadLive()
                                destination = item
                            },
                            icon = { Icon(item.icon, label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Mint,
                                selectedTextColor = Mint,
                                indicatorColor = Mint.copy(alpha = 0.12f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (analysisOpen) {
            DeepAnalysisScreen(
                status = deepAnalysis,
                contentPadding = padding,
                onBack = viewModel::dismissDeepAnalysis,
                onRetry = viewModel::retryDeepAnalysis,
            )
            return@Scaffold
        }
        selectedPrediction?.let { prediction ->
            PredictionDetailScreen(
                prediction = prediction,
                contentPadding = padding,
                language = language,
                onBack = { selectedPrediction = null },
            )
            return@Scaffold
        }
        selectedLiveEvent?.let { event ->
            LiveDetailScreen(
                event = event,
                contentPadding = padding,
                onBack = { selectedLiveEvent = null },
            )
            return@Scaffold
        }

        when (destination) {
            Destination.Home -> HomeScreen(
                state = state,
                contentPadding = padding,
                language = language,
                onOpen = viewModel::analyzePrediction,
                onOpenEvent = viewModel::analyzeEvent,
                onRefresh = viewModel::refreshSportsData,
            )
            Destination.Sports -> SportsScreen(
                state = state,
                contentPadding = padding,
                language = language,
                onOpen = viewModel::analyzePrediction,
                onOpenEvent = viewModel::analyzeEvent,
                onRefresh = viewModel::refreshSportsData,
                onPreloadSport = viewModel::preloadSportAnalyses,
                onToggleSportFavorite = viewModel::setFavoriteSport,
                onToggleCompetitionFavorite = viewModel::setFavoriteCompetition,
            )
            Destination.Live -> LiveScreen(
                state = state,
                contentPadding = padding,
                language = language,
                onRefresh = viewModel::refreshLive,
                onOpen = { selectedLiveEvent = it },
            )
            Destination.Settings -> SettingsScreen(
                state = state,
                contentPadding = padding,
                language = language,
                onToggleAutoRefresh = viewModel::setAutoRefresh,
                onToggleCloudCollaborative = viewModel::setCloudCollaborativeEnabled,
                onLanguageChange = viewModel::setAppLanguage,
                onRefresh = viewModel::refreshSportsData,
                onForceCloudSync = viewModel::forceCloudSync,
                onExportCloudDiagnostic = viewModel::exportCloudDiagnostic,
            )
        }
    }
}
