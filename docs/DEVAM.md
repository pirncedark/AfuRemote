# AfuRemote - Devam Notu

Son guncelleme: 2026-10-01 (IS3 kaynak ve ortam kontrolu)

## Bu tur: Android TV kumanda (v2) duzeltmesi - dal `fix/atv-kumanda-calismiyor`, PR #10
- 2324442: androidtvremote2 v2 tasimasindaki 7 hata (nonce, perform ana thread disi, secret[0] tasmasi, v2 icin HTTP heartbeat yok, v2 cihaz kaydi, IME sayac, POWER).
- Ikinci commit (refaktor):
  - `atvremote/AtvSession.kt`: kalici TLS oturumu; her tusta yeni baglanti yok. Koptugunda en fazla 30 sn geri cekilerek yeniden baglanir.
    Hazir degilken `send()` false doner, sessizce basarili saymaz.
  - `atvremote/protocol/AtvWireProtocol.kt`: saf bayt/protobuf katmani (cerceve, varint, pairing secret, tus/IME/launch).
  - `phone/RemoteTransport.kt`: HTTP (AfuRemote TV uygulamasi) ve ATV Remote v2 tek arayuzde; `isAlive` suspend.
  - `TvDiscovery.restore` suspend yapildi (ana thread'de ag cagrisi yok).
- Nonce 2 bayt KALDI: 6 haneli kod = 1 bayt kontrol oneki + 2 bayt nonce (androidtvremote2 ile ayni). 4 bayt YANLIS olur.

## Dogrulama (yerelde gercekten calistirildi)
- `gradle --console=plain test assembleDebug` -> BUILD SUCCESSFUL, birim test 75/75 (70 eski + 5 yeni AtvWireProtocolTest).
- Depoda gradlew yok: sistem gradle 9.6.0 + `local.properties` (sdk.dir=C:\android-sdk). Codex sandbox'i gradle calistiramiyor, derlemeyi Claude/koordinator kosar.

## IS3 guncel durum: kaynak korumalari mevcut, dogrulama bekliyor
- Incelenen HEAD: `3202944a79f76a26d10e0670e7da021c219483e3`. Izlenen dosyalar baslangicta temizdi; mevcut takip disi raporlar ve `.gradle-local/` korunuyor.
- Eski 500 ms timeout notu bu kaynak icin gecersiz: `AtvSession` timeout 0 ile ayri bloklayan okuyucu kullanir; yazici 20 ms poll yapar. `AtvWireProtocol.readFrame` parcali payload okumalarini tamamlar.
- READY sonrasi ve finally icinde kuyruk temizlenir; komutlarin yas siniri iki saniyedir. Bu kaynak incelemesidir, send/state/queue yarislari veya gercek TV kabul kaniti degildir.
- Mevcut `AtvWireProtocolTest` kisa parcali okuma ve iki bayt nonce regresyonlarini icerir. Sessiz TV, kopmada POWER aktarimi, kontrollu saatle komut suresi ve close/reader serbest kalmasi icin oturum regresyonlari henuz eklenmedi/calismadi.
- Ortam: Temurin OpenJDK 17.0.17+10; kurulu sistem Gradle dagitimi 9.6.0 (`--version` bile baslayamadi); SDK `C:\android-sdk`, platformlar android-36/android-37.0, build-tools 36.0.0/36.1.0/37.0.0. Proje compileSdk/targetSdk 37.
- Bu tur `gradle --offline --console=plain test assembleDebug` exit 1: native services baslatilamadi, `native-platform.dll` yuklenemedi. Kotlin/test asamasina ulasilmadi; bu bir Kotlin kusuru veya offline dependency eksikligi kaniti degildir. Erisim kisiti asilmadi.
- Diskteki XML ve APK 30 Eylul tarihli eski ciktilardir; bu tur icin guncel test toplamı veya yeni APK hash kaniti yoktur. Yukaridaki BUILD SUCCESSFUL ve 75/75 yalniz tarihsel kayittir.
- Eski XML toplami: 76 test, 0 failure, 0 error (30 Eylul 00:43:22). Eski APK SHA256: `EF6C1D3D8C16A34A7093F4CC0A316A07AE4FC8A0A8323BCEAE1914B38E22FD93` (30 Eylul 00:40:38). Bunlar guncel kosu sonucu olarak kullanilamaz.
- Uretim kodu degistirilmedi; yeniden uretilmis kusur yok. Push/release yapilmadi. IS3 tamamlanmadi; normal build ortami ve kullanici TV kabulu bekleniyor.

## Sonraki adim
1. Normal build ortaminda guncel kaynak icin test/assemble calistir; oturum transport/saat sinirina gerekirse minimal enjeksiyonla sahte socket regresyonlarini ekle. Yalniz testte yeniden uretilen kusuru duzelt; XML toplamlarini ve yeni APK SHA256 degerini kaydet.
2. KULLANICI: APK'yi telefona kur; TV marka/model/Remote Service surumunu kaydet. Eslesme ve 20 ardisik yon/OK/ses basisi, arka plan/geri donus, ag kes/geri ac ve uygulama kapanisini dene. Kopukken basarili gorunmemesi, eski POWER/yon komutlarinin tekrar gonderilmemesi ve oturum temizligi dogrulanmali. Gercek gecikme ve cihaz kabulu bekliyor.
