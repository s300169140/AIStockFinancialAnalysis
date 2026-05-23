package com.aistock.analysis.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aistock.analysis.di.ServiceLocator

@Composable
fun AppNav() {
    val nav = rememberNavController()

    LaunchedEffect(Unit) {
        ServiceLocator.repo.refreshStatus()
        ServiceLocator.repo.loadTickers()
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(nav) }
        composable("analyze/{ticker}") { entry ->
            val ticker = entry.arguments?.getString("ticker") ?: "AAPL"
            AnalyzeScreen(ticker, onBack = { nav.popBackStack() })
        }
        composable("paywall") { PaywallScreen(onBack = { nav.popBackStack() }) }
        composable("signin") { SignInScreen(onBack = { nav.popBackStack() }) }
    }
}
