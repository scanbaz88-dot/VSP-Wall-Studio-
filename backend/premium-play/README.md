# Production Premium verifier (deployment template, NOT deployed)

This source implements server-side Google Play ProductPurchaseV2 validation for the non-consumable item `vsp_premium_lifetime`. It is **not yet deployed**, nor is it a substitute for a Play Console product or service account.

## Launch checklist
- Create a **new** Google Cloud service account, authorize it in Google Play Console API access, and grant purchase/financial order access only as necessary.
- Configure service account credentials on a secured Node 20+ runtime as `GOOGLE_APPLICATION_CREDENTIALS` or workload identity. Do not commit credentials.
- Set TLS HTTPS with rate limiting, abuse mitigation, secure logs and 99.9%+ uptime.
- Provision the Play Console one-time product with exact identifier `vsp_premium_lifetime` and its purchase option, price and localization.
- Deploy and test backend in a **closed Play testing track** using license tester accounts and Google test payment instruments.
- Configure Android production build with public endpoint via `VSP_PREMIUM_VERIFY_URL=https://your.domain/verify`. Debug preview remains checkout disabled.
- Validate completed, pending, canceled, refunded, rejected, wrong-app and wrong-product transactions.
- Subscribe to RTDN and reconcile voided purchases. The example currently queries Google each time a Play purchase is restored; it does not yet contain a persistent entitlements database, RTDN subscriber or hardened authentication/rate-limiting layer.
- Audit data processing, refunds, token replay, service-account permission, fraud and Play policy. Add server metrics and monitoring. Make sure paid users can restore purchases on reinstall.

**Critical:** Do not publish the paid checkout until backend has passed security review and end-to-end license testing; the example is a starting point.

Official: https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.productsv2/getproductpurchasev2
Official: https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.products/acknowledge
