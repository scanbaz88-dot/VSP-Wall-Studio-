package com.vintagesoulprint.wallmuse

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.*
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.Executors

/** Verified Play lifetime entitlement, deliberately ephemeral and fail-closed.
 * Never grant Premium from a local Boolean or a raw Play client purchase alone.
 */
object PremiumSession {
    @Volatile var verified:Boolean=false
        private set
    fun updateFromTrustedServer(entitled:Boolean){verified=entitled}
}

/**
 * End-to-end Google Play Billing 9.1 flow. Buy button only becomes enabled
 * for the *release* package and a provisioned HTTPS server. Every purchase,
 * restore and pending state is handled. The server itself verifies and
 * acknowledges the purchase via the official Android Publisher API.
 */
class PlayPremiumBillingController(
    private val activity:Activity,
    private val report:(String)->Unit,
    private val refresh:()->Unit
){
    private val client=BillingClient.newBuilder(activity)
        .setListener(PurchasesUpdatedListener { result, purchases ->
            when(result.responseCode){
                BillingClient.BillingResponseCode.OK ->
                    purchases.orEmpty().forEach{inspectPurchase(it)}
                BillingClient.BillingResponseCode.USER_CANCELED ->
                    message("Purchase canceled.")
                else->message("Google Play checkout unavailable: ${result.debugMessage}")
            }
        })
        .enablePendingPurchases(PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()
    private val executor=Executors.newSingleThreadExecutor()
    private val ui=Handler(Looper.getMainLooper())
    private var product:ProductDetails?=null
    private var offerToken:String?=null
    private var ready=false
    private var disconnected=false
    val canCheckout get()=
        !BuildConfig.DEBUG &&
        activity.packageName=="com.vintagesoulprint.wallmuse" &&
        BuildConfig.PREMIUM_VERIFY_URL.startsWith("https://") &&
        ready && product!=null && offerToken!=null
    fun connect(){
        if(disconnected)return
        client.startConnection(object:BillingClientStateListener {
            override fun onBillingSetupFinished(result:BillingResult){
                if(result.responseCode==BillingClient.BillingResponseCode.OK){
                    ready=true;loadProduct();restore()
                }else message("Google Play Billing unavailable: ${result.debugMessage}")
            }
            override fun onBillingServiceDisconnected(){ready=false;refresh()}
        })
    }
    private fun loadProduct(){
        val p=QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PremiumPolicy.PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP).build()))
            .build()
        client.queryProductDetailsAsync(p){res,details->
            val item=details.productDetailsList.firstOrNull()
            if(res.responseCode==BillingClient.BillingResponseCode.OK && item!=null){
                product=item
                offerToken=item.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
                val price=item.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
                message(price?.let{"Google Play Lifetime Premium: $it"} ?: "Product found")
            }else message("Premium item not yet configured on Google Play.")
            refresh()
        }
    }
    fun purchase(){
        if(!canCheckout){message("Payments not ready. Setup and secure verification required.");return}
        val p=product?:return
        val offer=offerToken?:return
        val details=BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(p).setOfferToken(offer).build()
        val args=BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(details)).build()
        val result=client.launchBillingFlow(activity,args)
        if(result.responseCode!=BillingClient.BillingResponseCode.OK)
            message("Unable to open Google Play: ${result.debugMessage}")
    }
    fun restore(){
        if(!ready)return
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()){result,purchases->
            if(result.responseCode!=BillingClient.BillingResponseCode.OK){
                message("Cannot restore purchases yet.");return@queryPurchasesAsync
            }
            val matching=purchases.filter{PremiumPolicy.PRODUCT_ID in it.products}
            if(matching.isEmpty()){
                PremiumSession.updateFromTrustedServer(false)
                refresh()
                message("No active lifetime purchase found on Google Play.")
            }else matching.forEach{inspectPurchase(it)}
        }
    }
    private fun inspectPurchase(purchase:Purchase){
        if(PremiumPolicy.PRODUCT_ID !in purchase.products)return
        if(purchase.purchaseState==Purchase.PurchaseState.PENDING){
            message("Payment pending; Premium is not active yet.")
            return
        }
        if(purchase.purchaseState!=Purchase.PurchaseState.PURCHASED){
            PremiumSession.updateFromTrustedServer(false);refresh();return
        }
        // Never unlock or acknowledge from client Play purchase state.
        val endpoint=BuildConfig.PREMIUM_VERIFY_URL
        if(!endpoint.startsWith("https://")||BuildConfig.DEBUG){
            message("Premium requires production server validation.");return
        }
        val token=purchase.purchaseToken
        executor.execute{
            var allowed=false
            var note="Purchase verification unavailable. Please retry."
            try{
                val connection=URL(endpoint).openConnection() as HttpsURLConnection
                connection.requestMethod="POST"
                connection.connectTimeout=12_000
                connection.readTimeout=12_000
                connection.doOutput=true
                connection.setRequestProperty("content-type","application/json; charset=utf-8")
                connection.setRequestProperty("accept","application/json")
                val request=JSONObject().apply{
                    put("purchaseToken",token)
                    put("productId",PremiumPolicy.PRODUCT_ID)
                    put("packageName","com.vintagesoulprint.wallmuse")
                }.toString()
                connection.outputStream.use{it.write(request.toByteArray(Charsets.UTF_8))}
                if(connection.responseCode==200){
                    val body=connection.inputStream.bufferedReader().use{it.readText()}
                    val response=JSONObject(body)
                    allowed=response.optBoolean("entitled",false) &&
                        response.optString("status")=="PURCHASED" &&
                        response.optString("productId")==PremiumPolicy.PRODUCT_ID
                    note=if(allowed)"Lifetime Premium verified and unlocked."
                        else "Google Play purchase not verified."
                }
                connection.disconnect()
            }catch(_:Exception){}
            ui.post{
                if(disconnected)return@post
                PremiumSession.updateFromTrustedServer(allowed)
                message(note)
                refresh()
            }
        }
    }
    private fun message(value:String){ui.post{if(!disconnected)report(value)}}
    fun close(){disconnected=true;client.endConnection();executor.shutdownNow()}
}
