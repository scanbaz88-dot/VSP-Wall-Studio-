# VSP Wall Studio V3.1 — Google Play Release Readiness

**State:** Engineering/test build. NOT an approved production release. `app/build/outputs/bundle/release/app-release.aab` is UNSIGNED and must not be uploaded as a Play production artifact.

## Already implemented
- Android 36 target, Play Billing Library 9.1.0 read-only connection and product query
- Lifetime Premium product ID: `vsp_premium_lifetime`, product type: `inapp` (non-consumable, NOT subscription)
- Query existing Play purchases and pending orders, **without unlocking paid content**
- Fail-closed `PremiumPolicy`: only a trusted, server-verified, paid, correct, not-revoked purchase can confer lifetime access
- Checkout disabled until Play Console product and secure verification server are both configured
- All editing functions available in the preview, with no fake paid status
- JPEG/PNG with 1080 / 2160 / 3840 px longest-edge upper limits, no upscaling
- Signed **development-only** APK; unsigned production AAB compiles for CI checks

## REQUIRED before any real payment
1. Establish an authorized Google Play Console developer account with verified contact, store listing and a privacy policy consistent with actual data collection.
2. Reserve Android application ID `com.vintagesoulprint.wallmuse`. Do NOT publish the `.preview` ID. Confirm no prior Play package with this ID.
3. Create an active, non-consumable in-app product with exact ID `vsp_premium_lifetime`, title and regional prices. Launch price idea was $4.99 USD, **not** hardcoded; display localized Play price.
4. Provision a trusted backend. Authenticate/authorize operations and implement:
   - call Google Play Developer API `purchases.productsv2.getproductpurchasev2` on the exact token;
   - verify package, product, purchase state `PURCHASED`, token idempotency, region/account authorization where applicable, cancellation and revocation state;
   - DO NOT grant access on `PENDING`, `CANCELED`, `REFUNDED`, bad token, wrong product or network error;
   - grant and persist lifetime entitlement; handle RTDN / refunds / voided purchase reconciliation;
   - acknowledge legitimate completed purchases from backend within the Google Play acknowledgment deadline;
   - provide authenticated status/restore endpoint; never trust boolean SharedPreferences or client-provided purchase state.
5. Once backend + product are production-ready, wire BillingClient purchase UI to the server-verified receipt flow, including pending purchases, restore, and retry on network failure; set both readiness flags only after real validation and test.
6. Set up a **new PRIVATE production app signing/upload key**, configure Play App Signing, secure backups. The current public test key `preview-test-only.jks.base64` must NEVER sign a release.
7. Produce a properly SIGNED release Android App Bundle (AAB) using private CI secrets or local secure Android Studio signing. Do not publish unsigned AAB.
8. Complete Play Console privacy/data safety declarations, store graphics, screenshots, app access, testing policy, content rating, target audience, supported devices and testing tracks. Camera permission is optional and justified for live preview.
9. Test on real devices, low memory devices, several Android versions, screen sizes; run internal/closed testing and confirm all Play store/financial requirements applicable to the developer account. Test no double billing, purchases, pending, canceled, refund, restore, reinstall, device transfer.
10. Remove development-only hints before publishing. Publish only after sign-off.

**Privacy:** Photo processing and saved project data are local. Google Play Billing may communicate through Play services if the user taps readiness or store controls; no payment token is stored as entitlement in this version.

**References:**
- https://developer.android.com/google/play/billing/integrate
- https://developer.android.com/google/play/billing/security
- https://developer.android.com/google/play/billing/release-notes
