# AfuRemote — Tasarım (v1)

Tarih: 2026-09-23 · Durum: onaylandı (2026-09-23)

## 1. Amaç

Telefondaki bir linki veya videoyu **aynı Wi-Fi'deki Android TV'de tek dokunuşla açmak** ve telefonu **TV kumandası** olarak kullanmak.
QR / PIN / IP girme yok; TV telefonda kendiliğinden görünür.

**Kullanıcının söyledikleri:** tek APK; 2 özellik (TV'de aç + kumanda); uygulama içinden güncelleme (AfuTube gibi);
çalıştığı kanıtlanmadan "hazır" denmez; ücretsiz / açık kaynak.
**Varsayımlar (düzeltilebilir):** hedef TV Android TV / Google TV; telefon Android 8+ (minSdk 26).

**v1 başarı ölçütü:** İki cihaza aynı APK kurulu → telefonda TV listede kendiliğinden görünür → Chrome'da mp4 linki
Paylaş → AfuRemote → TV'de oynatıcıda açılır → telefondan ses / oynat-duraklat çalışır.

## 2. Kapsam

| v1 (bu spec) | Sonra |
|---|---|
| TV'de aç: link paylaş (mp4/mkv/webm/m3u8/mpd → oynatıcı, YouTube → YouTube TV, diğer → TV tarayıcı) | Ekran yansıtma |
| Telefondaki yerel videoyu TV'de oynat (telefon akıtır) | Chromecast / DLNA |
| Kumanda: geri, ev, ses +/−, sessiz, oynat/duraklat, ±10 sn sar | Sesli komut, D-pad / klavye |
| TV kendiliğinden bulunur (NSD / mDNS) | Birden fazla TV'de aynı anda oynatma |
| İlk bağlantıda TV'de "izin ver" + kalıcı token | |
| Uygulama içi güncelleme (GitHub Releases, veriler silinmeden) | |

**Karar:** sitelerden gizli medya adresi sökülmez (DRM/oturum) — normal site TV tarayıcısında açılır.

## 3. Mimari

Tek APK `com.afudm.afuremote`, tek Gradle modülü `app` (küçük dosyalar, paket paket ayrım). İki çalışma modu:

- **TV modu** — `UiModeManager` = TELEVISION veya `FEATURE_LEANBACK` ise otomatik. Ön plan servisi (`AfuTvService`,
  tür `connectedDevice`) HTTP sunucusu açar (NanoHTTPD, port **9870**) ve NSD ile `_afuremote._tcp.` duyurur.
- **Telefon modu** — diğer tüm cihazlar. NSD ile TV'leri bulur, OkHttp ile komut gönderir; yerel video için geçici
  dosya sunucusu (NanoHTTPD, port **9871**) açar.
- Ayarlar'da mod elle değiştirilebilir (test ve istisna cihazlar için).

### Paketler

| Paket | Sorumluluk | Android'e bağımlı mı |
|---|---|---|
| `protocol` | JSON mesajları (`OpenRequest`, `KeyRequest`, `PairRequest/Response`), sürüm sabiti `v1` | Hayır (birim testli) |
| `classify` | `LinkClassifier`: URL → `MEDIA` / `YOUTUBE` / `WEB` (+ YouTube video id çıkarma) | Hayır (birim testli) |
| `pairing` | `PairingStore` (TV: onaylı cihaz token'ları; telefon: TV başına token), token üretimi | SharedPreferences |
| `tv` | `AfuTvService`, `TvHttpServer`, `NsdAdvertiser`, `PlayerActivity` (Media3), `WebActivity` (WebView), `PairPromptActivity`, `RemoteAccessibilityService`, `TvMediaControl` | Evet |
| `phone` | `TvDiscovery` (NSD), `TvClient` (OkHttp), `ShareActivity` (paylaşım hedefi), `LocalMediaServer`, `RemoteScreen` (Compose kumanda) | Evet |
| `update` | AfuTube'dan uyarlanmış `AppUpdateParser` + `ApkDownloadWorker` (ön plan, dataSync, % bildirim, "kurmak için dokun") | Kısmen (parser birim testli) |
| `ui` | Compose tema, ana ekranlar (TV: durum + onaylı cihazlar; telefon: TV listesi + kumanda + ayarlar) | Evet |

## 4. Protokol (HTTP + JSON, TV:9870)

Her istekte `X-Afu-Token` başlığı (eşleştirme hariç). Token yok/yanlış → `401`.

| Uç | Gövde | Etki |
|---|---|---|
| `GET /v1/info` | — | `{name, model, version, protocol:"v1"}` (token gerekmez; keşif doğrulaması) |
| `POST /v1/pair` | `{deviceName, deviceId}` | TV'de `PairPromptActivity` açılır; kullanıcı TV kumandasıyla "İzin ver" → `{token}`; red/60 sn → `403` |
| `POST /v1/open` | `{url, title?}` | `LinkClassifier` → Player / YouTube intent / WebActivity |
| `POST /v1/key` | `{key}` — `back, home, vol_up, vol_down, mute, play_pause, seek_fwd, seek_back` | Aşağıdaki "Kumanda" |

**Kumanda eşlemesi (TV):** ses → `AudioManager.adjustStreamVolume`; oynat/duraklat/sar → bizim oynatıcı açıksa doğrudan
Media3, değilse `AudioManager.dispatchMediaKeyEvent` (diğer uygulamalar); geri/ev → `RemoteAccessibilityService.performGlobalAction`
(kullanıcı bir kez Erişilebilirlik'ten açar; kapalıysa `409 {"hata":"erisilebilirlik_kapali"}` → telefonda "TV'de izni aç" uyarısı).

**Yerel video:** telefon `LocalMediaServer`'da rastgele 32 karakterlik yol üretir: `http://<telefon-ip>:9871/m/<rastgele>`;
Range (206) destekli; yalnızca paylaşılan tek dosya; uygulama/paylaşım bitince kapanır. Bu URL `/v1/open` ile TV'ye gider.

## 5. Akışlar

1. **Keşif:** TV servisi açılışta başlar (TV'de `BOOT_COMPLETED` ile de). Telefon ana ekranı ve paylaşım ekranı NSD taraması yapar;
   bulunan her servis `GET /v1/info` ile doğrulanır. Liste isim + model gösterir.
2. **Eşleştirme:** Token'ı olmayan TV'ye ilk komutta otomatik `/v1/pair`; telefonda "TV ekranında İzin ver'e bas" yazar.
3. **Paylaş:** `ShareActivity` (text/plain, video/*): tek eşleşmiş TV varsa doğrudan gönderir ve "TV'de açıldı" deyip kapanır;
   birden fazla varsa seçim sayfası; hiç yoksa "TV bulunamadı — TV'de AfuRemote açık mı, aynı Wi-Fi mı?".
4. **Kumanda:** telefon ana ekranında seçili TV için büyük tuşlar; her tuş `/v1/key`.

## 6. Hata yönetimi

- Ağ hatası/zaman aşımı (bağlan 3 sn, oku 5 sn) → Türkçe tek satır mesaj + "Tekrar dene".
- TV IP'si değişirse: `401/bağlanamadı` → NSD'den yeniden çöz, bir kez tekrar dene.
- Oynatıcı hatası (codec/404) → TV'de hata ekranı + telefona `/v1/open` yanıtında `{ok:false, hata}`.
- Uygulama açılışı ağa **bağlı değildir** (AfuTube dersi): sunucu/keşif arka planda; UI hemen gelir.
- Ön plan servisleri **tür belirtilerek** başlatılır (AfuTube dersi: Android 14+ çökmesi).

## 7. Güncelleme

AfuTube modeli: `afuremote-v*` etiketli GitHub Release; `AfuRemote-universal.apk` + `.sha256`; aynı anahtarla imza
(yeni anahtar `afuremote-release.jks`, `afuproject\_gizli` altında + GitHub secret). Açılışta günde bir + Ayarlar'da
"Güncellemeleri denetle"; indirme ön planda % bildirimle; SHA-256 doğrulaması; "kurmak için dokun".
Repo: **`pirncedark/AfuRemote` — PUBLIC** (kullanıcı kararı 2026-09-23): kaynak + release'ler tek depoda; uygulama
GitHub API'sini ve asset'leri girişsiz okur. İmza anahtarı ve şifreleri depoya ASLA girmez (yalnız GitHub secrets).

## 8. Test ve kalite kapısı

- **Birim:** `LinkClassifier` (mp4/m3u8/mpd/YouTube kısa+uzun+shorts/web/boş), protokol JSON gidiş-dönüş, `PairingStore` token doğrulama,
  güncelleme parser'ı, Range başlık ayrıştırma.
- **CI emülatör e2e (tek emülatör, iki rol):**
  1. APK kur; `--es mode tv` ile TV modunu başlat → `adb forward tcp:9870` → `GET /v1/info` 200.
  2. `POST /v1/pair` → TV'de "İzin ver" ekranı uiautomator ile tıklanır → token alınır.
  3. `POST /v1/open` küçük mp4 → `PlayerActivity` ön planda + oynatıcı durumu "oynuyor" (logcat) — 60 sn içinde.
  4. `POST /v1/key vol_up` → `dumpsys audio` ses seviyesi arttı; `play_pause` → oynatıcı duraklatıldı.
  5. Telefon modu ekranı açılır → NSD ile aynı cihazdaki TV servisi listede görünür (isim eşleşir).
  6. Çökme yok (`FATAL EXCEPTION` taranır); kanıt: ekran görüntüleri + logcat artifact.
- Kapı: e2e kırmızıysa release yok. "Hazır" yalnız e2e yeşil + kullanıcının gerçek TV'sinde ilk deneme sonrası.

## 9. Teknoloji (hepsi ücretsiz/açık kaynak)

Kotlin 2.2.10, AGP 9.4.1, Gradle 9.6.0, compileSdk/targetSdk 37, minSdk 26, Compose BOM 2026.09.00 + Material3 (TV'de de Compose),
Media3 ExoPlayer 1.11.1 (Apache-2.0), NanoHTTPD 2.3.1 (BSD-3), OkHttp (Apache-2.0), kotlinx-serialization (Apache-2.0),
WorkManager (güncelleme). Derleme yalnız GitHub Actions'ta (bu PC'de SDK yok).

## 10. Riskler

- Bazı TV'lerde (Samsung Tizen, LG webOS) Android yok → uygulama kurulamaz; v1 yalnız Android TV / Google TV.
- Erişilebilirlik izni (TV'de bir kez) iki şey için gerekir: geri/ev tuşları ve AfuRemote ekranda değilken oynatıcı/onay ekranı açmak
  (Android 10+ arka plandan ekran açmayı engeller; bağlı erişilebilirlik servisi resmi istisnadır). İzin kapalıyken yalnız AfuRemote TV ekranı açıksa link açılır; telefon net uyarı gösterir.
- Ev ağında mDNS'i engelleyen router/"AP isolation" → keşif olmaz; v1'de elle IP girme **yok**, hata mesajı bunu söyler.
