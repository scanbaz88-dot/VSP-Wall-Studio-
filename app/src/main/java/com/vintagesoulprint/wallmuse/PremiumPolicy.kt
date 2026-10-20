package com.vintagesoulprint.wallmuse

/**
 * VSP Wall Studio's commercial offer has ONE non-consumable product.
 * The actual entitlement is NOT stored as a mutable boolean in preferences.
 * A Play purchase token must be verified and periodically reconciled before
 * production paywall enforcement; the test APK stays fully usable.
 */
object PremiumPolicy {
    const val PRODUCT_ID="vsp_premium_lifetime"
    const val SKU_KIND="inapp"
    const val FREE_MAX_SAVED_PROJECTS=3
    const val FREE_MAX_EXPORT_LONG_EDGE=1920
    const val PREMIUM_EXPORT_LONG_EDGE=3840

    // Free access should demonstrate the actual value, with no time limit.
    val freeFrameIds=setOf(0,1,2,3,11,17,19,22)
    data class Access(
        val premium:Boolean,
        val maxSavedProjects:Int,
        val maxExportLongEdge:Int,
        val availableFrames:Set<Int>,
        val watermark:Boolean=false
    )
    fun access(premium:Boolean):Access=if(premium)Access(
        premium=true,
        maxSavedProjects=Int.MAX_VALUE,
        maxExportLongEdge=PREMIUM_EXPORT_LONG_EDGE,
        availableFrames=FrameCatalog.frames.indices.toSet()
    )else Access(
        premium=false,
        maxSavedProjects=FREE_MAX_SAVED_PROJECTS,
        maxExportLongEdge=FREE_MAX_EXPORT_LONG_EDGE,
        availableFrames=freeFrameIds
    )
    /** Never paywall a completed draft while entitlement restoration is pending. */
    fun mayOpenExistingProject(saved:Boolean)=saved
}
