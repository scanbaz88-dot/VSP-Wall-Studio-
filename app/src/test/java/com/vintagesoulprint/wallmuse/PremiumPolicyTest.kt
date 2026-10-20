package com.vintagesoulprint.wallmuse

import org.junit.Assert.*
import org.junit.Test

class PremiumPolicyTest {
    @Test fun productIsNonConsumable() {
        assertEquals("vsp_premium_lifetime",PremiumPolicy.PRODUCT_ID)
        assertEquals("inapp",PremiumPolicy.SKU_KIND)
    }
    @Test fun premiumNeverLimitsSavesOrExportsByUsageCount(){
        val premium=PremiumPolicy.access(true)
        assertEquals(Int.MAX_VALUE,premium.maxSavedProjects)
        assertEquals(PremiumPolicy.PREMIUM_EXPORT_LONG_EDGE,premium.maxExportLongEdge)
        assertFalse(premium.watermark)
    }
    @Test fun freePreviewRemainsUseful(){
        val free=PremiumPolicy.access(false)
        assertTrue(free.availableFrames.size>=6)
        assertTrue(free.maxExportLongEdge>=1080)
        assertFalse(free.watermark)
    }
    @Test fun noUnverifiedPendingOrRevokedPurchasesCanUnlock(){
        val states=PremiumPolicy.PaymentState.values()
        for(state in states){
            for(verified in listOf(false,true)){
                val receipt=PremiumPolicy.ReceiptValidation(state,verified,true,false)
                assertEquals(state==PremiumPolicy.PaymentState.PURCHASED&&verified,
                    PremiumPolicy.hasVerifiedLifetimeAccess(receipt))
            }
        }
        assertFalse(PremiumPolicy.hasVerifiedLifetimeAccess(PremiumPolicy.ReceiptValidation(
            PremiumPolicy.PaymentState.PURCHASED,true,true,true)))
        assertFalse(PremiumPolicy.hasVerifiedLifetimeAccess(PremiumPolicy.ReceiptValidation(
            PremiumPolicy.PaymentState.PURCHASED,true,false,false)))
    }
    @Test fun doNotChargeInPreviewOrBeforeBackendExists(){
        assertFalse(PremiumPolicy.checkoutEnabled(true))
        assertFalse(PremiumPolicy.checkoutEnabled(false))
        assertNull(PremiumPolicy.validPlayPrice(null))
        assertEquals("$4.99",PremiumPolicy.validPlayPrice("$4.99"))
    }
}
