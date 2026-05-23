package com.aistock.analysis.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aistock.analysis.di.ServiceLocator
import com.aistock.analysis.net.ApiException
import com.aistock.analysis.net.StreamEvent
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeScreen(ticker: String, onBack: () -> Unit) {
    val repo = ServiceLocator.repo
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(AnalyzeState.Loading) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var trialRemaining by remember { mutableStateOf<Int?>(null) }
    val scroll = rememberScrollState()

    LaunchedEffect(ticker) {
        text = ""
        status = AnalyzeState.Loading
        errorMsg = null
        repo.analyze(ticker)
            .catch { e ->
                status = AnalyzeState.Error
                errorMsg = when (e) {
                    is ApiException -> when (e.errorCode) {
                        "TRIAL_EXHAUSTED" -> "Trial used up — subscribe to PRO"
                        "TICKER_NOT_SUPPORTED" -> "Ticker not supported"
                        "DEVICE_BLOCKED" -> "This device is blocked"
                        else -> e.message ?: "Request failed"
                    }
                    else -> e.message ?: "Network error"
                }
            }
            .collect { evt ->
                when (evt) {
                    is StreamEvent.Meta -> {
                        trialRemaining = evt.event.gate.remaining
                        status = AnalyzeState.Streaming
                    }
                    is StreamEvent.Delta -> {
                        text += evt.event.text
                        if (status == AnalyzeState.Loading) status = AnalyzeState.Streaming
                    }
                    is StreamEvent.Error -> {
                        status = AnalyzeState.Error
                        errorMsg = evt.event.message ?: evt.event.code
                    }
                    is StreamEvent.Done -> {
                        status = AnalyzeState.Done
                        repo.refreshStatus()
                    }
                }
            }
    }

    LaunchedEffect(text) {
        if (text.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ticker) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    trialRemaining?.let { Text("$it left  ", style = MaterialTheme.typography.labelMedium) }
                },
            )
        },
    ) { inner ->
        Box(modifier = Modifier.padding(inner).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(16.dp),
            ) {
                if (status == AnalyzeState.Loading && text.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Generating analysis for $ticker…")
                    }
                }
                MarkdownText(source = text)

                when (status) {
                    AnalyzeState.Streaming -> {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Streaming…", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    AnalyzeState.Error -> {
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                text = errorMsg ?: "Something went wrong",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    AnalyzeState.Done -> {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Not financial advice. Verify with primary sources before investing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnalyzeState.Loading -> { /* spinner above */ }
                }
            }
        }
    }
}

private enum class AnalyzeState { Loading, Streaming, Done, Error }
