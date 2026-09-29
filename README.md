# AfuRemote

**English** · [Türkçe](#türkçe)

AfuRemote lets you open links and videos on an Android TV and control it from your phone over the same Wi-Fi network.

## What it does

Share a link or video from your phone to open it on a nearby Android TV, then use your phone as the TV remote. AfuRemote discovers TVs on the local network automatically; you do not need to scan a QR code or enter a PIN or IP address.

## Features

- Open direct media links in the TV player, YouTube links in the YouTube TV app when available, and other links in the TV browser.
- Play a video stored on your phone on the TV over your local network.
- Control back, home, volume, mute, play/pause, and 10-second seeking from your phone.
- Discover Android TVs automatically on the same Wi-Fi network and approve phones on the TV.
- Check for and install updates from inside the app.

## Install

Download `AfuRemote-universal.apk` from [GitHub Releases](https://github.com/pirncedark/AfuRemote/releases) and install that same APK on your Android phone and Android TV. AfuRemote requires Android 8.0 (API 26) or later. Your device may ask you to allow installation from this source.

## First-time setup on TV

On the TV, open **Settings → Accessibility → AfuRemote** and turn it **On**. This permission enables the back and home remote buttons and lets AfuRemote show link and pairing screens when it is in the background.

## Pairing

Make sure the phone and TV are on the same Wi-Fi network and open AfuRemote on the TV. When you first send a link or remote command, compare the six-digit codes shown on the TV and phone. If they match, select **Allow** on the TV to pair the devices.

## Updates

Use the in-app update checker to find and download releases. AfuRemote verifies the downloaded APK with SHA-256 before offering installation. Updates are signed for installation over the existing app and preserve its data.

## Security notes

Pairing uses ECDH (P-256); the six-digit code helps detect a device-in-the-middle attack. The remote key never travels over the network, and every request is signed with HMAC-SHA256. Timestamps and one-time nonces prevent replay attacks. Network traffic is not encrypted, so someone on the same network may see the address of a link you open. Use AfuRemote only on Wi-Fi networks you trust.

## Build from source

Install JDK 17 and Gradle 9.6.0. Generate the Gradle wrapper, then build a debug APK with the same commands used by the CI build workflow:

```sh
gradle wrapper --gradle-version 9.6.0 --distribution-type bin
./gradlew test assembleDebug --no-daemon --stacktrace
```

The APK is produced under `app/build/outputs/apk/debug/`.

License: not specified yet


## Afu family

- [AfuDM](https://github.com/pirncedark/AfuDM) — download manager (Windows)
- [AfuDesk](https://github.com/pirncedark/afudesk) — serverless remote desktop: connect with one code (Windows)
- [AfuTube](https://github.com/pirncedark/AfuDM/releases?q=afutube) — video downloader (Android)

---

## Türkçe

AfuRemote, aynı Wi-Fi ağı üzerinden bağlantıları ve videoları Android TV'de açmanızı ve TV'yi telefonunuzdan kontrol etmenizi sağlar.

## Ne işe yarar?

Telefonunuzdan bir bağlantı veya video paylaşarak yakındaki Android TV'de açabilir, ardından telefonunuzu TV kumandası olarak kullanabilirsiniz. AfuRemote yerel ağdaki TV'leri otomatik bulur; QR kod okutmanız, PIN veya IP adresi girmeniz gerekmez.

## Özellikler

- Doğrudan medya bağlantılarını TV oynatıcısında, YouTube bağlantılarını varsa YouTube TV uygulamasında, diğer bağlantıları TV tarayıcısında açar.
- Telefonunuzda kayıtlı bir videoyu yerel ağ üzerinden TV'de oynatır.
- Telefonunuzdan geri, ana ekran, ses, sessiz, oynat/duraklat ve 10 saniye ileri/geri sarma kontrollerini kullanmanızı sağlar.
- Aynı Wi-Fi ağındaki Android TV'leri otomatik bulur ve telefon eşleştirmesinin TV'den onaylanmasını sağlar.
- Uygulama içinden güncellemeleri denetler ve yükler.

## Kurulum

[GitHub Releases](https://github.com/pirncedark/AfuRemote/releases) sayfasından `AfuRemote-universal.apk` dosyasını indirin ve aynı APK'yı Android telefonunuza ve Android TV'nize kurun. AfuRemote için Android 8.0 (API 26) veya üzeri gerekir. Cihazınız, bu kaynaktan uygulama kurmanıza izin vermenizi isteyebilir.

## TV'de ilk kurulum

TV'de **Ayarlar → Erişilebilirlik → AfuRemote** bölümünü açıp hizmeti **Açık** duruma getirin. Bu izin, geri ve ana ekran kumanda tuşlarını etkinleştirir; ayrıca AfuRemote arka plandayken bağlantı ve eşleştirme ekranlarını açmasını sağlar.

## Eşleştirme

Telefonun ve TV'nin aynı Wi-Fi ağına bağlı olduğundan emin olun ve TV'de AfuRemote'u açın. İlk bağlantı veya kumanda komutunu gönderdiğinizde TV'de ve telefonda gösterilen altı haneli kodları karşılaştırın. Kodlar aynıysa cihazları eşleştirmek için TV'de **İzin ver** seçeneğini seçin.

## Güncellemeler

Yeni sürümleri bulup indirmek için uygulama içindeki güncelleme denetleyicisini kullanın. AfuRemote, yükleme seçeneğini sunmadan önce indirilen APK'nın SHA-256 özetini doğrular. Güncellemeler mevcut uygulamanın üzerine kurulur ve verilerinizi korur.

## Güvenlik notları

Eşleştirme ECDH (P-256) kullanır; altı haneli kod araya giren bir cihazı fark etmenize yardımcı olur. Kumanda anahtarı ağ üzerinden hiçbir zaman gönderilmez ve her istek HMAC-SHA256 ile imzalanır. Zaman damgaları ve tek kullanımlık nonce değerleri tekrar saldırılarını engeller. Ağ trafiği şifrelenmez; bu nedenle aynı ağdaki biri açtığınız bağlantının adresini görebilir. AfuRemote'u yalnızca güvendiğiniz Wi-Fi ağlarında kullanın.

## Kaynak koddan derleme

JDK 17 ve Gradle 9.6.0 kurun. Gradle wrapper'ını oluşturun, ardından CI derleme iş akışında kullanılan komutlarla bir hata ayıklama APK'sı derleyin:

```sh
gradle wrapper --gradle-version 9.6.0 --distribution-type bin
./gradlew test assembleDebug --no-daemon --stacktrace
```

APK, `app/build/outputs/apk/debug/` klasörüne oluşturulur.

Lisans: henüz belirtilmedi

## Afu ailesi

- [AfuDM](https://github.com/pirncedark/AfuDM) — indirme yöneticisi (Windows)
- [AfuDesk](https://github.com/pirncedark/afudesk) — sunucusuz uzak masaüstü: tek kodla bağlan (Windows)
- [AfuTube](https://github.com/pirncedark/AfuDM/releases?q=afutube) — video indirici (Android)
