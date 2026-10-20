# VSP Wall Studio 2.0

Android wall art preview app for VintageSoulPrint.

Source code and CI build configuration are being uploaded. GitHub Actions will compile a debug APK for device testing. The debug APK is not suitable for publishing directly to Google Play; release AAB and a secure signing setup are required.

Project package: VSP Wall Studio 2.0.

## Test APK signature and installation

The GitHub Actions debug build is intentionally a separate installable app: `com.vintagesoulprint.wallmuse.preview` (shown as **VSP Wall Studio Test**). It can coexist with earlier `com.vintagesoulprint.wallmuse` installs without erasing their saved data. Projects are not automatically shared between the two Android package identities.

Preview debug builds use the **same development-only** key from `app/preview-test-only.jks.base64`. This certificate is public, and is **not trustworthy for production/Google Play signing**. It must NEVER be used for a release AAB. Keep the future production app on the unmodified `com.vintagesoulprint.wallmuse` application ID and use a securely held Play App Signing key.

To build: open **Actions → VSP Wall Studio - Stable Test APK**, download `VSP-Wall-Studio-Preview-Stable-APK`, extract it, and install the contained `app-debug.apk`.

## V2.4: Native frame engine and future lifetime Premium

All frames are drawn by `FrameEngine.kt` directly around the artwork aperture. Image-overlay cutouts and the old `assets/frames` directory have been removed. Frames retain their stable integer IDs for previously saved drafts; the frame library and editor preview use the SAME renderer.

`PremiumPolicy.kt` describes the intended one-time `vsp_premium_lifetime` (INAPP, non-consumable) product. **No live purchase, account verification or paywall is active in the current test APK.** Do not claim payments work before the real Play Console product, verification, pending / refund handling and restoration have been implemented.

Free: basic wall/camera/crop editing, selected frames, 1920 px output and limited future project storage. Lifetime Premium: all frames, high-resolution output, unlimited future project storage, and no recurring fees. Multi-project storage and Play Billing production integration remain to be built.

The public preview debug keystore is ONLY for `com.vintagesoulprint.wallmuse.preview`. NEVER upload it to Play or use it as the production signing key.
