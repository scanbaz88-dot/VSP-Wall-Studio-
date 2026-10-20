# VSP Wall Studio V2.4 — Play Launch and Lifetime Premium

## Commercial proposal
* One non-consumable INAPP ID: `vsp_premium_lifetime`; no subscription, no repeat charge.
* Proposed US launch price: **$4.99**, considering **$6.99** after validation. This is not a configured store price.
* Account-bound permanently restored entitlement via Google Play purchases. Refund/revocation must revoke access.
* Free: no watermark, 8 basic frames, 1920 px export; Premium: all native frames, 3840 px export, unlimited separately saved projects.
* The existing app only has a single draft autosave. A multi-project repository UI is required before marketing unlimited saved projects.

## Monetization is not activated in V2.4
There is no fake checkout. Billing and entitlement backend are upcoming release work, not already complete. In particular:
1. Open a Play Console developer and merchant account.
2. Define `vsp_premium_lifetime` as non-consumable INAPP; configure regional pricing.
3. Integrate current Google Play Billing Library (9.1.0 as of Oct 2026), display live localized price, query products and owned purchases, respond to PENDING, grant on PURCHASED, acknowledge within 3 days, restore from purchase history, verify purchase tokens on a secure backend / Android Publisher API, track refunds and voided purchases.
4. Enforce the PremiumPolicy access rules *only after* validation and reliable restoring.
5. Implement separate project listing, persistent image permissions, database migration and backup.
6. Validate Android device camera orientation, realistic frame depths, fullscreen, import, save and Play closed testing.

## Technical isolation
`com.vintagesoulprint.wallmuse.preview`: stable public test signing identity, can coexist with past installs; no shared data.
`com.vintagesoulprint.wallmuse`: planned release app; Play App Signing needed and secure keys never stored in the public repository.
Test APK is NOT a signed production AAB.

## Rollout
- V2.4 native frame renderer, app-structure separation, Premium policy specification.
- V2.5 multi-project management, export tiers, restore-safe entitlement and billing.
- V2.6 Play internal testing, privacy/security checklist, device QA, production release AAB.

## Play policies
* Purchases of digital features on Play use Play Billing unless specific policy exceptions apply.
* Some newer personal developer accounts must complete 12 opted-in testers for at least 14 days before production access.
* Fees differ by market, product and program; do not assume a flat universal Play fee.

Official sources:
https://developer.android.com/google/play/billing/one-time-products
https://developer.android.com/google/play/billing/integrate
https://developer.android.com/google/play/billing/lifecycle/one-time
https://support.google.com/googleplay/android-developer/answer/14151465
https://support.google.com/googleplay/android-developer/answer/112622
