package com.aistock.analysis.billing

import android.app.Activity
import android.content.Context
import com.aistock.analysis.BuildConfig
import com.aistock.analysis.data.AppRepository
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class BillingManager(
    private val appContext: Context,
    private val repo: AppRepository,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableStateFlow<BillingEvent>(BillingEvent.Idle)
    val events: StateFlow<BillingEvent> = _events

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    private var connected = false

    suspend fun ensureConnected() {
        if (connected && client.isReady) return
        suspendCoroutine<Unit> { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        connected = true
                        cont.resume(Unit)
                    } else {
                        cont.resumeWithException(
                            RuntimeException("Billing connect failed: ${result.debugMessage}")
                        )
                    }
                }
                override fun onBillingServiceDisconnected() {
                    connected = false
                }
            })
        }
    }

    suspend fun loadProSubscription(): ProductDetails {
        ensureConnected()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BuildConfig.PRO_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        return suspendCoroutine { cont ->
            client.queryProductDetailsAsync(params) { result, list ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    cont.resumeWithException(
                        RuntimeException("queryProductDetails failed: ${result.debugMessage}")
                    )
                    return@queryProductDetailsAsync
                }
                val pd = list.firstOrNull()
                if (pd == null) {
                    cont.resumeWithException(RuntimeException("Product not found: ${BuildConfig.PRO_PRODUCT_ID}"))
                } else {
                    cont.resume(pd)
                }
            }
        }
    }

    suspend fun launchPurchase(activity: Activity, productDetails: ProductDetails) {
        ensureConnected()
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: throw RuntimeException("No subscription offer available")

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            throw RuntimeException("launchBillingFlow failed: ${result.debugMessage}")
        }
    }

    suspend fun restorePurchases() {
        ensureConnected()
        val res = suspendCoroutine<Pair<BillingResult, List<Purchase>>> { cont ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { result, purchases -> cont.resume(result to purchases) }
        }
        if (res.first.responseCode != BillingClient.BillingResponseCode.OK) return
        for (p in res.second) {
            handlePurchase(p)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _events.value = BillingEvent.Error(result.debugMessage ?: "Purchase failed")
            return
        }
        purchases?.forEach { handlePurchase(it) }
    }

    private fun handlePurchase(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val productId = p.products.firstOrNull() ?: BuildConfig.PRO_PRODUCT_ID
        scope.launch {
            runCatching {
                repo.verifyPurchase(productId, p.purchaseToken)
            }.onSuccess {
                _events.value = BillingEvent.Verified
                repo.refreshStatus()
            }.onFailure {
                _events.value = BillingEvent.Error(it.message ?: "Verify failed")
            }
        }
    }

    sealed interface BillingEvent {
        data object Idle : BillingEvent
        data object Verified : BillingEvent
        data class Error(val message: String) : BillingEvent
    }
}
