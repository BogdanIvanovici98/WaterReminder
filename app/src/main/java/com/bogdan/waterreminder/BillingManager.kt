package com.bogdan.waterreminder

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*

class BillingManager(
    private val context: Context,
    private val listener: PurchasesUpdatedListener
) {

    private lateinit var billingClient: BillingClient

    fun startBillingConnection(onReady: (() -> Unit)? = null) {
        billingClient = BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Ready to query purchases and products
                    onReady?.invoke()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Optionally implement retry logic
            }
        })
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        billingClient.queryProductDetailsAsync(params) { _, productDetailsList ->
            if (productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val offerToken = productDetails.subscriptionOfferDetails?.get(0)?.offerToken
                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken ?: "")
                        .build()
                )
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()
                billingClient.launchBillingFlow(activity, billingFlowParams)
            }
        }
    }

    fun queryActiveSubscriptions(onResult: (List<Purchase>) -> Unit) {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { _, purchasesList ->
            onResult(purchasesList)
        }
    }

    fun acknowledgePurchase(purchase: Purchase, onAck: ((Boolean) -> Unit)? = null) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { billingResult ->
                onAck?.invoke(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
            }
        } else {
            onAck?.invoke(true)
        }
    }
}