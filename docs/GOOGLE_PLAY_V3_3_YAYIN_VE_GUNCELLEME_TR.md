# VSP Wall Studio — Google Play V3.3 Yayın ve Güncelleme Rehberi

## Şu anki durum
- 24 hazır oda çevrimdışı; 11 sorunlu fotoğraf yerleştirme için daha uygun görsellerle değiştirildi.
- Ücretsiz sürüm: 6 stok sahne, 3 kayıtlı proje, temel çerçeveler, 1920 px'ye kadar dışa aktarma.
- Lifetime Premium: 18 ek sahne, sınırsız proje, tüm çerçeveler, 3840 px'ye kadar dışa aktarma.
- Ömür boyu **tek seferlik** ürün kodu: `vsp_premium_lifetime`.
- **Ödeme henüz canlı değil.** Sunucu örnek kodu `backend/premium-play/` içinde; Play Console hizmet hesabı ve üretim sunucusuna dağıtım gerektiriyor.

## İlk günden Premium satışı için gerekenler
1. Google Play Console hesabından yeni uygulama oluşturun ve üretim paketini `com.vintagesoulprint.wallmuse` olarak sabitleyin.
2. **Monetize → Products → In-app products** üzerinden `vsp_premium_lifetime` ürününü **tek seferlik/non-consumable** olarak tanımlayın, satın alma seçeneği ve fiyatlarını açın. Uygulama hiçbir fiyatı kod içine sabitlemez; Play'in yerel fiyatını gösterir.
3. Play Developer API erişimli Google Cloud hizmet hesabı oluşturun. Gerekli izinleri minimum seviyede tutun; anahtarlarını **asla** APK veya GitHub kaynak dosyalarına koymayın.
4. `backend/premium-play/server.mjs` sunucu örneğini yetkili bir HTTPS sunucuya yayınlayın. Rate-limit, RTDN/iptal ve iade takibi, güvenlik ve kötüye kullanım önlemlerini tamamlayın. İlgili dosyanın README'si bu örneğin güvenlik sınırlarını açıklar.
5. Sunucu `POST /verify` ile satın alma tokenini Google'a doğrulamalı, tamamlanan işlemi acknowledge etmeli, beklemede/iptal/iade durumuna Premium vermemelidir.
6. Play Console **closed testing** üzerinden gerçek Google Play ödeme testleri yapın; satın alma, bekleyen ödeme, ödeme iptali, iade, yeniden yükleme ve satın almayı geri yükleme testlerinden geçin.
7. Yalnızca bu testler başarılı olunca `VSP_PREMIUM_VERIFY_URL` yayın ortamında tanımlanarak ücretli sürüm derlenmeli. **Test APK'sında ödeme hiçbir zaman açılmaz.**

## İmzalı AAB oluşturma
GitHub deposu → Settings → Secrets and variables → Actions bölümünde bu isimlerle secrets tanımlayın:
- `VSP_PREMIUM_VERIFY_URL`: dağıtılmış, test edilmiş HTTPS `/verify` URL'si.
- `VSP_RELEASE_KEYSTORE_BASE64`: **özel, yeni Play upload anahtarı** içeren JKS'nin base64 hâli.
- `VSP_RELEASE_STORE_PASSWORD`, `VSP_RELEASE_KEY_PASSWORD`, `VSP_RELEASE_KEY_ALIAS`.
- Önceki açık test sertifikasıyla üretim AAB imzalanmamalıdır.

Sonrasında Actions → **Sign production Google Play AAB** → Run workflow.
Üretilen `VSP-Wall-Studio-v3.3-PLAY-UPLOAD-SIGNED-AAB` artefaktı kontrol edildikten sonra Play Console'un internal/closed testing kanalına yüklenir. Bu GitHub eylemi kendi başına Google Play'de yayın yapmaz.

## Güncellemeler
Google Play'de ürün yayınlandıktan sonra her güncellemede:
- `app/build.gradle.kts` içinde `versionCode` kesinlikle **artırılır**; `versionName` güncellenir.
- **Aynı** `applicationId` (`com.vintagesoulprint.wallmuse`) ve Play'in tanıdığı **aynı upload signing key** kullanılarak AAB derlenir.
- Yeni AAB Play Console'da test kanalında doğrulanır, ardından production sürüm olarak yayımlanır.
- Otomatik güncellemesi açık olan telefonlara Google Play otomatik yükler; diğerleri Play Store'dan **Güncelle** seçer. İsteğe göre Play In-App Updates eklenebilir; şart değildir.
- Satın alınan Lifetime Premium hakkı, aynı Google Play hesabıyla yeniden yüklemede `Restore purchase` işleviyle sunucu üzerinden doğrulanmalıdır.

**Önemli:** `com.vintagesoulprint.wallmuse.preview` kimlikli test APK'ları üretim uygulamasından farklıdır; Play'deki production uygulamanın üzerine otomatik dönüşmez. Test uygulamasını ayrıca kaldırmak gerekebilir.

## Yayına çıkmadan son kontrol
Gizlilik politikası, Data Safety, görsel kaynak/izinleri, app içi dosya izinleri, mağaza görselleri, açıklamalar, içerik derecelendirmesi, hesap doğrulaması, gerçek cihaz testleri ve Play'in hesabınıza uyguladığı test koşulları tamamlanmalı.
