package com.vintagesoulprint.wallmuse

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing 9.1 *read-only readiness and restore discovery*.
 *
 * NO transaction is launched and NO token is locally trusted as a Premium
 * entitlement. When the release product, authenticated server verification,
 * refund checks, and server acknowledgment are available, implement the
 * verified checkout flow here. This is deliberately production fail-closed.
 */
class PlayBillingReadiness(private val activity:Activity) {
    data class Status(
        val connected:Boolean,
        val productFound:Boolean,
        val localizedPrice:String?,
        val ownedOnPlay:Boolean,
        val pendingOnPlay:Boolean,
        val explanation:String
    )
    private var client:BillingClient?=null

    fun inspect(callback:(Status)->Unit){
        close()
        val c=BillingClient.newBuilder(activity)
            .setListener(PurchasesUpdatedListener { result,purchases->
                // A purchase event is not proof of payment. Never grant or
                // acknowledge from this callback without server verification.
            })
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        client=c
        c.startConnection(object:BillingClientStateListener{
            override fun onBillingSetupFinished(result:BillingResult){
                if(result.responseCode!=BillingClient.BillingResponseCode.OK){
                    finish(Status(false,false,null,false,false,
                        "Google Play is unavailable: ${result.debugMessage}"),callback)
                    return
                }
                inspectProduct(c,callback)
            }
            override fun onBillingServiceDisconnected() {
                // Auto-service reconnection is active; UI can retry at any time.
            }
        })
    }
    private fun inspectProduct(c:BillingClient,callback:(Status)->Unit){
        val product=QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PremiumPolicy.PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params=QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product)).build()
        c.queryProductDetailsAsync(params){result,details->
            val item:ProductDetails?=if(result.responseCode==BillingClient.BillingResponseCode.OK)
                details.productDetailsList.firstOrNull()else null
            val price=item?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
            val purchaseParams=QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()
            c.queryPurchasesAsync(purchaseParams){purchaseResult,purchases->
                val matching=if(purchaseResult.responseCode==BillingClient.BillingResponseCode.OK)
                    purchases.filter{PremiumPolicy.PRODUCT_ID in it.products}
                    else emptyList()
                val owned=matching.any{it.purchaseState==Purchase.PurchaseState.PURCHASED}
                val pending=matching.any{it.purchaseState==Purchase.PurchaseState.PENDING}
                val status=Status(true,item!=null,
                    PremiumPolicy.validPlayPrice(price),owned,pending,
                    if(item==null)"Product has not been published for this package on Google Play."
                    else if(owned)"A Play purchase was found; server verification is required."
                    else if(pending)"A payment is pending; Premium cannot be activated."
                    else "Play product is configured, no matching purchase was found.")
                finish(status,callback)
            }
        }
    }
    private fun finish(status:Status,callback:(Status)->Unit){
        activity.runOnUiThread { callback(status) }
    }
    fun close(){client?.endConnection();client=null}
}
