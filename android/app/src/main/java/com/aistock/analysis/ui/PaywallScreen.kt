package com.aistock.analysis.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aistock.analysis.R
import com.aistock.analysis.billing.BillingManager
import com.aistock.analysis.di.ServiceLocator
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val repo = ServiceLocator.repo
    val billing = ServiceLocator.billing
    val status by repo.status.collectAsState()
    val session by repo.session.collectAsState(
        initial = com.aistock.analysis.data.Session(null, null, null, null)
    )

    var productDetails by remember { mutableStateOf<ProductDetails?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val billingEvent by billing.events.collectAsState()

    LaunchedEffect(Unit) {
        if (session.isSignedIn) {
            runCatching { billing.loadProSubscription() }
                .onSuccess { productDetails = it }
                .onFailure { loadError = it.message }
        }
    }

    LaunchedEffect(billingEvent) {
        when (val e = billingEvent) {
            is BillingManager.BillingEvent.Verified -> {
                repo.refreshStatus()
            }
            is BillingManager.BillingEvent.Error -> {
                loadError = e.message
                busy = false
            }
            else -> { /* idle */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRO Subscription") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Unlimited Stock Analyses",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "10-section Wall Street-grade analysis on demand",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    PriceRow(productDetails)
                    Spacer(Modifier.height(12.dp))
                    Bullet("Unlimited analyses (no daily caps)")
                    Bullet("S&P 500 + NASDAQ 100 coverage")
                    Bullet("Streaming responses for instant insight")
                    Bullet("Cancel anytime in Google Play")
                }
            }

            Spacer(Modifier.height(20.dp))

            when {
                status?.subscriptionActive == true -> {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "You're on PRO. Thanks for supporting the app!",
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                !session.isSignedIn -> {
                    Text(
                        text = "Sign in with Google to subscribe.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Go back to sign in")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            val a = activity ?: return@Button
                            val pd = productDetails ?: return@Button
                            busy = true
                            scope.launch {
                                runCatching { billing.launchPurchase(a, pd) }
                                    .onFailure {
                                        loadError = it.message
                                        busy = false
                                    }
                            }
                        },
                        enabled = !busy && productDetails != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                        ) else Text(stringResource(R.string.subscribe))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching { billing.restorePurchases() }
                                    .onFailure { loadError = it.message }
                                repo.refreshStatus()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.restore_purchases))
                    }
                }
            }

            loadError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PriceRow(pd: ProductDetails?) {
    val price = pd?.subscriptionOfferDetails?.firstOrNull()
        ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
        ?: "$9.99 / month"
    Text(price, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
