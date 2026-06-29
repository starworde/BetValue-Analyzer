package com.soliano.betvalueanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.soliano.betvalueanalyzer.ui.BetValueApp
import com.soliano.betvalueanalyzer.ui.MainViewModel
import com.soliano.betvalueanalyzer.ui.theme.BetValueTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application as BetValueApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BetValueTheme {
                BetValueApp(viewModel)
            }
        }
    }
}

