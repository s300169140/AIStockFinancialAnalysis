package com.aistock.analysis.data

import com.aistock.analysis.net.ApiClient
import com.aistock.analysis.net.StatusResp
import com.aistock.analysis.net.StreamEvent
import com.aistock.analysis.net.VerifyResp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppRepository(
    private val api: ApiClient,
    private val prefs: Prefs,
) {
    private val _status = MutableStateFlow<StatusResp?>(null)
    val status: StateFlow<StatusResp?> = _status

    private val _tickers = MutableStateFlow<List<String>>(emptyList())
    val tickers: StateFlow<List<String>> = _tickers

    val session = prefs.session

    suspend fun refreshStatus() {
        runCatching { api.status() }.onSuccess { _status.value = it }
    }

    suspend fun loadTickers() {
        if (_tickers.value.isNotEmpty()) return
        runCatching { api.tickers() }.onSuccess { _tickers.value = it }
    }

    suspend fun signInWithGoogle(idToken: String) {
        val resp = api.googleSignIn(idToken)
        prefs.setSession(
            token = resp.token,
            email = resp.user.email,
            name = resp.user.name,
            picture = resp.user.picture,
        )
        refreshStatus()
    }

    suspend fun signOut() {
        prefs.clearSession()
        refreshStatus()
    }

    fun analyze(ticker: String): Flow<StreamEvent> = api.analyzeStream(ticker)

    suspend fun verifyPurchase(productId: String, purchaseToken: String): VerifyResp =
        api.verifyPurchase(productId, purchaseToken)
}
