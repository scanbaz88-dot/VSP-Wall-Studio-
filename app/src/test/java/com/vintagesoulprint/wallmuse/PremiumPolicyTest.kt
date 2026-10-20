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
}
