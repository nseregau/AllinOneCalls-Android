package com.huntingcalls

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StorePlan(
    val id: String,
    val title: String,
    val period: String,
    val badge: String? = null,
    val productType: String,
)

data class PurchaseHelper(val activity: Activity) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private lateinit var billingClient: BillingClient
    private lateinit var purchase: Purchase

    val plans = listOf(
        StorePlan("com.huntingcalls.monthly", "Monthly Plan", "per month", productType = BillingClient.ProductType.SUBS),
        StorePlan("com.huntingcalls.yearly", "Yearly Plan", "per year", "BEST VALUE", BillingClient.ProductType.SUBS),
        StorePlan("com.huntingcalls.lifetime", "Lifetime Plan", "one-time", "PAY ONCE", BillingClient.ProductType.INAPP),
    )

    private val queriedPlans = plans

    private val productDetailsById = mutableMapOf<String, ProductDetails>()
    private val localUnlockKey = "unlock_state_full_access"
    private var inAppEntitlement = false
    private var subscriptionEntitlement = false

    private fun isLocallyUnlocked(): Boolean {
        return activity.getSharedPreferences("purchase_state", Context.MODE_PRIVATE)
            .getBoolean(localUnlockKey, false)
    }

    private fun persistLocalUnlockState(isUnlocked: Boolean) {
        activity.getSharedPreferences("purchase_state", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(localUnlockKey, isUnlocked)
            .apply()
    }

    private val _productName = MutableStateFlow("Searching...")
    val productName = _productName.asStateFlow()

    private val _productPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val productPrices = _productPrices.asStateFlow()

    private val _buyEnabled = MutableStateFlow(false)
    val buyEnabled = _buyEnabled.asStateFlow()

    private val _consumeEnabled = MutableStateFlow(isLocallyUnlocked())
    val consumeEnabled = _consumeEnabled.asStateFlow()

    private val _statusText = MutableStateFlow("Initializing...")
    val statusText = _statusText.asStateFlow()

    fun billingSetup() {
        if (::billingClient.isInitialized && billingClient.isReady) {
            queryProducts()
            reloadPurchase()
            return
        }

        billingClient = BillingClient.newBuilder(activity)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _statusText.value = "Billing Client Connected"
                    queryProducts()
                    reloadPurchase()
                } else {
                    _statusText.value = "Billing Client Connection Failure"
                }
            }

            override fun onBillingServiceDisconnected() {
                _statusText.value = "Billing Client Connection Lost"
            }
        })
    }

    fun queryProducts() {
        if (!::billingClient.isInitialized || !billingClient.isReady) return

        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                queriedPlans.map { plan ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(plan.id)
                        .setProductType(plan.productType)
                        .build()
                }
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                productDetailsById.clear()
                productDetailsList.forEach { productDetailsById[it.productId] = it }
                val prices = productDetailsList.associate { it.productId to it.formattedPrice() }
                _productPrices.value = prices
                _productName.value = prices["com.huntingcalls.yearly"] ?: prices.values.firstOrNull().orEmpty()
                if (!_consumeEnabled.value) {
                    _buyEnabled.value = true
                }
            } else {
                _statusText.value = "No Matching Products Found"
                _buyEnabled.value = false
            }
        }
    }

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    completePurchase(purchase)
                }
            } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                _statusText.value = "Purchase Canceled"
            } else {
                _statusText.value = "Purchase Error"
            }
        }

    private fun completePurchase(item: Purchase) {
        purchase = item
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            _buyEnabled.value = false
            _consumeEnabled.value = true
            persistLocalUnlockState(true)
            _statusText.value = "Purchase Completed"

            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                coroutineScope.launch {
                    billingClient.acknowledgePurchase(acknowledgePurchaseParams.build())
                }
            }
        }
    }

    fun refreshProduct() {
        queryProducts()
    }

    fun makePurchase(productId: String): Boolean {
        val productDetails = productDetailsById[productId]
        if (productDetails == null) {
            _statusText.value = "Product Not Loaded"
            refreshProduct()
            return false
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        productDetails.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken
            ?.let(productDetailsParamsBuilder::setOfferToken)

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun reloadPurchase() {
        reloadPurchaseType(BillingClient.ProductType.INAPP)
        reloadPurchaseType(BillingClient.ProductType.SUBS)
    }

    private fun reloadPurchaseType(productType: String) {
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()

        billingClient.queryPurchasesAsync(queryPurchasesParams) { billingResult, purchases ->
            val hasActivePurchase = billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases.isNotEmpty()
            if (hasActivePurchase) {
                purchase = purchases.first()
                purchases.forEach(::completePurchase)
            }
            if (productType == BillingClient.ProductType.INAPP) {
                inAppEntitlement = hasActivePurchase
            } else {
                subscriptionEntitlement = hasActivePurchase
            }
            updateUnlockState()
        }
    }

    private fun updateUnlockState() {
        val unlocked = inAppEntitlement || subscriptionEntitlement
        persistLocalUnlockState(unlocked)
        _consumeEnabled.value = unlocked
        _buyEnabled.value = !unlocked && productDetailsById.isNotEmpty()
        if (unlocked) {
            _statusText.value = "Previous Purchase Found"
        }
    }

    private fun ProductDetails.formattedPrice(): String {
        oneTimePurchaseOfferDetails?.formattedPrice?.let { return it }
        return subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.lastOrNull()
            ?.formattedPrice
            ?: ""
    }
}
