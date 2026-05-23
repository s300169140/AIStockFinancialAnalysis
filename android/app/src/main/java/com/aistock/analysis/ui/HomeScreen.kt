package com.aistock.analysis.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aistock.analysis.R
import com.aistock.analysis.di.ServiceLocator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val repo = ServiceLocator.repo
    val status by repo.status.collectAsState()
    val tickers by repo.tickers.collectAsState()
    val session by repo.session.collectAsState(
        initial = com.aistock.analysis.data.Session(null, null, null, null)
    )

    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val filtered = remember(query, tickers) {
        if (query.isBlank()) tickers
        else tickers.filter { it.startsWith(query.uppercase()) }.take(80)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (session.isSignedIn) {
                        TextButton(onClick = { nav.navigate("paywall") }) {
                            Icon(Icons.Filled.WorkspacePremium, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (status?.subscriptionActive == true) "PRO" else "Upgrade")
                        }
                    } else {
                        TextButton(onClick = { nav.navigate("signin") }) {
                            Text("Sign in")
                        }
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            TrialBanner(
                remaining = status?.trial?.remaining ?: 0,
                limit = status?.trial?.limit ?: 3,
                isPro = status?.subscriptionActive == true,
                onUpgrade = { nav.navigate("paywall") },
            )

            OutlinedTextField(
                value = query,
                onValueChange = { v ->
                    query = v.uppercase().filter { it.isLetterOrDigit() || it == '.' || it == '-' }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                label = { Text(stringResource(R.string.search_tickers)) },
                placeholder = { Text("AAPL, NVDA, MSFT…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = { it }) { sym ->
                    ListItem(
                        headlineContent = {
                            Text(sym, style = MaterialTheme.typography.titleMedium)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                focusManager.clearFocus()
                                nav.navigate("analyze/$sym")
                            },
                    )
                    HorizontalDivider()
                }
                if (filtered.isEmpty() && tickers.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No matches for \"$query\".\nWe only support S&P 500 + NASDAQ 100.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun TrialBanner(remaining: Int, limit: Int, isPro: Boolean, onUpgrade: () -> Unit) {
    val container =
        if (isPro) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                when {
                    isPro -> {
                        Text("PRO subscription active", style = MaterialTheme.typography.titleMedium)
                        Text("Unlimited analyses", style = MaterialTheme.typography.bodySmall)
                    }
                    remaining > 0 -> {
                        Text("$remaining of $limit free analyses left", style = MaterialTheme.typography.titleMedium)
                        Text("Upgrade for unlimited", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        Text("Free trial used up", style = MaterialTheme.typography.titleMedium)
                        Text("Subscribe to keep analyzing", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!isPro) {
                Button(onClick = onUpgrade) { Text("Upgrade") }
            }
        }
    }
}
