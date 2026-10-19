# VSP Wall Studio — Görsel Çerçeve Kütüphanesi v1 (V2.3)

Bu paket, üretken görsel kaynaklardan özel hazırlanmış **12 ayrı, alfa kanallı WebP çerçeve görseli** içerir. Doku ve oyma ayrıntıları kaynak görsel tabanlıdır; eski kod çizimi tabanlı basit çerçeveler yeni seçim ekranından kaldırılmıştır (eski çalışma kayıtlarıyla uyumluluk için çizim kodu korunur). Bu ilk yüksek görünürlüklü prototip, gerçek ürün fotoğrafı lisanslı koleksiyon değildir.

| ID | Ad | Grup | Yapı |
|---:|---|---|---|
| 17 | İnce siyah metal fotoğraf | Modern | Köşeli / 9 dilimli |
| 18 | Klasik beyaz fotoğraf | Modern | Köşeli / 9 dilimli |
| 19 | Doğal meşe fotoğraf | Ahşap | Köşeli / 9 dilimli |
| 20 | Koyu ceviz fotoğraf | Ahşap | Köşeli / 9 dilimli |
| 21 | Eskitilmiş ahşap fotoğraf | Ahşap | Köşeli / 9 dilimli |
| 22 | Siyah galeri fotoğraf | Modern | Köşeli / 9 dilimli |
| 23 | Antika altın oyma fotoğraf | Antika | Köşeli / 9 dilimli |
| 24 | Viktoryen oyma fotoğraf | Antika | Köşeli / 9 dilimli |
| 25 | Eski bronz fotoğraf | Antika | Köşeli / 9 dilimli |
| 26 | Antika oval fotoğraf | Oval & Yuvarlak | Oval görüntü |
| 27 | Yuvarlak altın fotoğraf | Oval & Yuvarlak | Dairesel görüntü |
| 28 | Oval siyah fotoğraf | Oval & Yuvarlak | Oval görüntü |

Görüntü ölçeklenirken dikdörtgen çerçevelerin köşe dokuları 9-parçalı renderer üzerinden korunur. Oval/yuvarlak olanlarda şeffaf iç delik görselin şekline uyumludur. Gölgeler görüntü sahnesi üzerinde çizilir. Tuval / floating canvas seçenekleri eski numaralarında bulunmaya devam eder.

**Not:** Fotoğrafik detayları daha ileriye taşımak için sonraki aşamada her çerçevenin tek başına 1500–3000 px kaynak görselinden ve serbest lisanslı üretici görsellerinden alınan doku setleriyle değiştirilmesi önerilir. Bu sürümde kullanılan 512px dokular uygulama prototipine uygundur ancak profesyonel baskı kalitesinde çerçeve görseli değildir.

## Tam ekran
- Ana ekran tam ekran düğmesindeki ters çevrilmiş etkinlik durumu düzeltildi.
- Tam ekran sırasında başlık, kontrol panelleri ve Android sistem çubukları gizlenir.
- Sağ üstte ayrı bir `✕ Düzenle` butonuyla güvenli çıkış yapılır.
- Geri düğmesi de normal düzenlemeye döndürür.

## APK çakışması
- Test sürümü imzası sabit, `com.vintagesoulprint.wallmuse.preview` kimliğiyle hazırlanır.
- Ana `com.vintagesoulprint.wallmuse` kurulumundan ayrıdır; eski veri silinmez.
- Bu test imzası **public development-only** olup hiçbir zaman Google Play / production için kullanılmamalıdır.
