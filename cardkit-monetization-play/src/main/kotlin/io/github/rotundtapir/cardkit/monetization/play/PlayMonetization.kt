// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.monetization.play

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import io.github.rotundtapir.cardkit.monetization.Monetization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ad-supported monetization for Google Play builds: a banner slot, occasional interstitials, and a
 * one-time "remove ads" in-app purchase (Google Play Billing).
 *
 * Ad unit ids and the product id are supplied via [Config] so the library carries no hard-coded
 * account details. Use Google's official test ad unit ids during development.
 *
 * Ads are gated behind Google's User Messaging Platform: on construction the current consent state
 * is refreshed (showing the GDPR consent form to EEA/UK users when required) and the Mobile Ads SDK
 * is only initialised — and ads only requested — once [ConsentInformation.canRequestAds] holds.
 * Consent failures (offline, misconfiguration) never block the app; ads simply stay off.
 */
class PlayMonetization(
    activity: Activity,
    private val config: Config,
) : Monetization, PurchasesUpdatedListener {

    data class Config(
        val bannerAdUnitId: String,
        val interstitialAdUnitId: String,
        val removeAdsProductId: String,
        /** Debug-only: force EEA geography so the consent form always shows. */
        val consentDebugGeographyEea: Boolean = false,
        /** Debug-only: UMP test device hashed ids (logged by UMP on first run). */
        val consentTestDeviceHashedIds: List<String> = emptyList(),
    )

    private val appContext = activity.applicationContext

    private val _adsRemoved = MutableStateFlow(false)
    override val adsRemoved: StateFlow<Boolean> = _adsRemoved.asStateFlow()

    override val offersRemoveAds: Boolean = true

    private val consentInformation = UserMessagingPlatform.getConsentInformation(appContext)

    /** Ads may be requested (consent obtained or not required) — gates the banner and interstitials. */
    private val _adsEnabled = MutableStateFlow(false)

    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private var adsInitialized = false

    private var interstitial: InterstitialAd? = null
    private var removeAdsProduct: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    init {
        connectBilling() // billing is independent of ad consent
        requestConsent(activity)
    }

    // --- Consent (UMP) -----------------------------------------------------------------------------

    private fun requestConsent(activity: Activity) {
        val params = ConsentRequestParameters.Builder().apply {
            if (config.consentDebugGeographyEea || config.consentTestDeviceHashedIds.isNotEmpty()) {
                setConsentDebugSettings(
                    ConsentDebugSettings.Builder(activity).apply {
                        config.consentTestDeviceHashedIds.forEach(::addTestDeviceHashedId)
                        if (config.consentDebugGeographyEea) {
                            setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        }
                    }.build()
                )
            }
        }.build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Shows the form only when consent is required; the callback fires either way.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    // Form errors (offline, misconfiguration) must never block the game: proceed,
                    // and ads stay off until canRequestAds() holds on a later launch.
                    updateFromConsentState()
                }
            },
            { _ ->
                updateFromConsentState()
            },
        )
        // Consent persisted from a previous session lets ads start immediately, in parallel with
        // the update above (Google's recommended pattern).
        updateFromConsentState()
    }

    private fun updateFromConsentState() {
        _privacyOptionsRequired.value = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (consentInformation.canRequestAds()) initializeAds()
    }

    private fun initializeAds() {
        if (adsInitialized) return // all UMP callbacks arrive on the main thread
        adsInitialized = true
        MobileAds.initialize(appContext) { }
        _adsEnabled.value = true
        loadInterstitial()
    }

    override fun showPrivacyOptionsForm(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
            updateFromConsentState()
        }
    }

    // --- Billing ---------------------------------------------------------------------------------

    private fun connectBilling() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryRemoveAdsProduct()
                    refreshPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // A production app would back off and retry; kept minimal here.
            }
        })
    }

    private fun queryRemoveAdsProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(config.removeAdsProductId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { _, products ->
            removeAdsProduct = products.firstOrNull()
        }
    }

    /** Reconcile [adsRemoved] with purchases already owned (e.g. after reinstall / on another device). */
    private fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { _, purchases ->
            if (purchases.any { it.isRemoveAds() && it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                _adsRemoved.value = true
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return
        for (purchase in purchases) {
            if (purchase.isRemoveAds() && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                _adsRemoved.value = true
                if (!purchase.isAcknowledged) {
                    val ack = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(ack) { }
                }
            }
        }
    }

    private fun Purchase.isRemoveAds(): Boolean = products.contains(config.removeAdsProductId)

    override fun launchRemoveAdsOrDonate(activity: Activity) {
        val product = removeAdsProduct ?: return
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build()
                )
            )
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    // --- Ads -------------------------------------------------------------------------------------

    @Composable
    override fun BannerSlot(modifier: Modifier) {
        val removed by adsRemoved.collectAsState()
        val enabled by _adsEnabled.collectAsState()
        if (removed || !enabled) return
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = config.bannerAdUnitId
                    loadAd(AdRequest.Builder().build())
                }
            },
        )
    }

    private fun loadInterstitial() {
        if (_adsRemoved.value || !_adsEnabled.value) return
        InterstitialAd.load(
            appContext,
            config.interstitialAdUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                }
            },
        )
    }

    override fun maybeShowInterstitial(activity: Activity) {
        if (_adsRemoved.value || !_adsEnabled.value) return
        val ad = interstitial ?: return
        ad.show(activity)
        interstitial = null
        loadInterstitial() // preload the next one
    }

    override fun dispose() {
        interstitial = null
        if (billingClient.isReady) billingClient.endConnection()
    }
}
