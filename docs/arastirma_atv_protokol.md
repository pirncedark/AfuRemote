# Android TV Remote Service v2 araştırması

**Tarih:** 25 Eylül 2026  
**Kapsam:** Android TV Remote Service protokolü v2, mDNS, TLS/eşleşme, protobuf mesajları, açık kaynak uygulamalar ve AfuRemote'un mevcut keşif akışı  
**Not:** Bu belge yalnızca araştırma raporudur; uygulama kodu değiştirilmemiştir.

## Kısa sonuç

Google TV/Android TV kumanda uygulamaları, telefonda kurulan bir APK'nın TV'ye yüklenmesine ihtiyaç duymaz. Çünkü kontrol düzlemi TV'de hâlihazırda bulunan, üreticiyle birlikte gelen **Android TV Remote Service** sistem uygulamasında çalışır:

1. TV, yerel ağda `_androidtvremote2._tcp.local.` mDNS servisi olarak kendini duyurur.
2. Telefon bu servisi bulur ve SRV kaydındaki adres ile **6466** kontrol portuna bağlanır.
3. İlk kullanımda telefon, **6467** eşleşme portunda TLS bağlantısı açar ve TV ekranında görünen altı haneli kodu doğrular.
4. Eşleşme sırasında üretilen istemci sertifikası saklanır.
5. Sonraki oturumlarda telefon aynı istemci sertifikasını kullanarak doğrudan 6466'dan protobuf komutları gönderir.

Dolayısıyla “kumanda uygulaması TV'yi neden buluyor?” sorusunun cevabı özel bir keşme algoritması değil, **TV sistem uygulamasının mDNS reklamı + iki TLS portu + istemci sertifikası tabanlı eşleşme** modelidir.

AfuRemote şu anda yalnızca kendi `_afuremote._tcp` servisini arıyor ve karşılığında TV'de çalışan kendi HTTP sunucusuna bağlanıyor. Bu yüzden üretici tarafından hazır gelen Android TV Remote Service'i hiç kullanmıyor. Mevcut akış kendi TV modunda çalışıyorsa sonuç üretebilir; fakat TV'ye AfuRemote kurulmamışsa o cihazı tanımaması beklenen davranıştır.

## 1. Kaynak güvenilirliği ve sürümler

| Kaynak | Doğrulanan sürüm/tarih | Kullanım alanı | Lisans |
|---|---:|---|---|
| [AOSP Google TV pairing protocol](https://android.googlesource.com/platform/external/google-tv-pairing-protocol/) | `master`, 25.09.2026'da okundu | Polo protobuf ve eşleşme algoritması | Apache-2.0 |
| [`tronikos/androidtvremote2`](https://github.com/tronikos/androidtvremote2) | `v0.3.2`, 01.09.2026 | Protokol v2 uçtan uca Python implementasyonu | Apache-2.0 |
| [Home Assistant `androidtv_remote`](https://github.com/home-assistant/core/tree/dev/homeassistant/components/androidtv_remote) | Core `2026.9.3`, 18.09.2026 | Keşif, eşleşme, remote/media player entegrasyonu | Apache-2.0 |
| [Android `NsdManager` referansı](https://developer.android.com/reference/android/net/nsd/NsdManager) | Güncel Android SDK belgeleri, 25.09.2026'da okundu | Android mDNS/NSD API'si | Android SDK lisans koşulları |
| [Android NSD rehberi](https://developer.android.com/develop/connectivity/wifi/use-nsd) | Güncel Android geliştirici rehberi | NSD kullanımı | Android SDK lisans koşulları |

### Doğrulanamayan noktalar

- Android TV Remote Service'in AOSP'da dağıtılan her üretici buildindeki **tam sürüm numarası** doğrulanamadı.
- Remote v2 ağ sunucusunun bütün Google ürün cihazlarındaki kapalı kaynak uygulama kodu doğrulanamadı ve AOSP'de bulunamadı.
- Bazı üreticilerin Remote Service sürümü, mDNS servisi veya 6466/6467 portlarını kaldırması/sınırlaması mümkündür. Hangi marka/modelde mutlaka çalıştığı bu kaynaklarla doğrulanamadı.
- Bazı TV'lerde mDNS görünür olsa bile 6466 veya 6467 portu yerel ağ politikası nedeniyle engellenebilir. Bu cihaz listesi doğrulanamadı.

## 2. Android TV Remote Service v2 mimarisi

```text
Telefon / Google TV mobil uygulaması
        |
        | 1. mDNS: _androidtvremote2._tcp.local.
        |    SRV -> host + 6466, TXT -> en az "bt"
        v
Android TV Remote Service (TV'de önceden kurulmuş sistem uygulaması)
        |
        | 2. İlk oturum: TLS + protobuf, port 6467
        |    PairingRequest -> Options -> Configuration -> Secret
        |    TV ekranında 6 haneli kod
        v
        |
        | 3. Eşleşmiş istemci sertifikasıyla TLS, port 6466
        |    RemoteConfigure -> RemoteSetActive -> komutlar
        v
Android TV çalışma zamanı / launcher / uygulamalar / IME / ses
```

### 2.1 mDNS servisi

Doğrulanan tam DNS-SD biçimi:

```text
_service: _androidtvremote2._tcp.local.
instance: <TV adı>._androidtvremote2._tcp.local.
port:     6466
TXT:      bt=<MAC veya kimlik bilgisi>
```

Kanıtlar:

- Home Assistant `manifest.json`, Zeroconf türünü `_androidtvremote2._tcp.local.` olarak bildiriyor.
- Home Assistant config flow, servis adından TV adını ayırıyor ve TXT alanında `bt` değerini zorunlu olarak kullanıyor.
- Home Assistant, cihazın benzersiz kimliği için `bt` değerini MAC olarak biçimlendiriyor.
- `androidtvremote2` demosu Zeroconf ile tam DNS-SD türünü tarıyor ve SRV adresini/portunu çözümlüyor.
- `androidtvremote2` istemcisi kontrol için varsayılan `6466`, eşleşme için `6467` kullanıyor.

**Önemli ayrım:** Zeroconf tam türü son `.local.` ile biter. Android `NsdManager` doküman örnekleri ise genellikle `_androidtvremote2._tcp` biçimini kullanır. AOSP'nin `NsdServiceInfo` sınıfı servis tipini doğrudan saklar; son nokta/normalizasyon davranışını uygulama güvenle varsaymamalı, seçilen biçim hedef cihazlarda test edilmelidir.

### 2.2 Portlar

| Port | İşlev | Kaynak |
|---:|---|---|
| `6466` | TLS kumanda oturumu ve protobuf komutları | `androidtvremote2` `AndroidTVRemote` varsayılanı; Home Assistant dolaylı olarak aynı istemciyi kullanıyor |
| `6467` | TLS eşleşme oturumu | `androidtvremote2` `pair_port=6467`; Node.js ve Go uygulamaları da aynı değeri kullanıyor |

mDNS SRV kaydı kontrol portunu 6466 olarak verir. 6467 çoğunlukla sabit port olarak kullanılır ve ayrı bir mDNS servisiyle ilan edildiği doğrulanamadı.

### 2.3 TLS ve istemci sertifikası

`androidtvremote2 v0.3.2` doğrulanan davranışı:

- RSA 2048 bitlik kendi kendine imzalı istemci anahtar çifti üretir.
- Sertifika CN/SAN alanına kullanıcıya görünen istemci adı yazılır.
- Sertifika `BasicConstraints: CA=true, pathLength=0` kullanır.
- Sertifika 10 yıl geçerli olur.
- İstemci normal CA doğrulaması yapmaz ve hostname kontrolünü kapatır; çünkü TV kendi kendine imzalı, dinamik bir sunucu sertifikası sunar.
- Bununla birlikte istemci, eşleşme sonrasında sunucunun kabul ettiği kendi sertifikasını TLS bağlantısında sunar.
- Eşleşme sırasında sunucu sertifikasının RSA modulus/exponent değerleri alınır.
- Eşleşme anahtarı/özel anahtarı güvenli depolamada telefonda saklanır ve sonraki bağlantılarda yeniden kullanılır.

Bu model “kullanıcı adı/parola” değildir. TV, daha önce eşleşmiş bir istemci sertifikasını tanıyarak 6466 bağlantısına izin verir.

Örnek Python kullanımı:

```python
from androidtvremote2 import AndroidTVRemote

remote = AndroidTVRemote(
    client_name="My Phone",
    certfile="cert.pem",
    keyfile="key.pem",
    host="192.168.1.50",
)

await remote.async_generate_cert_if_missing()
await remote.async_start_pairing()
await remote.async_finish_pairing(input("TV'deki 6 haneli kod: "))
await remote.async_connect()

remote.send_key_command("HOME")
remote.send_key_command("VOLUME_UP")
remote.send_text("Merhaba")
remote.send_launch_app_command("https://www.youtube.com")
```

## 3. Eşleşme protokolü

### 3.1 Taşınan veri

Hem eşleşme hem kumanda akışında mesajlar şu biçimde çerçevelenir:

```text
protobuf-length (unsigned varint) + protobuf bytes
```

`androidtvremote2` `ProtobufProtocol`, parça parça gelen TCP verisini tamponlayarak tam protobuf mesajlarını ayırır. Aynı anda birden fazla veya yarım mesaj gelebilir.

### 3.2 Eşleşme adımları

`androidtvremote2 v0.3.2` tarafından doğrulanan akış:

1. Telefon kendi istemci sertifikasını üretir.
2. Telefon `6467` portuna TLS bağlantısı açar.
3. `OuterMessage(protocol_version=2, status=200)` içinde `PairingRequest` gönderir:
   - `service_name = "atvremote"`
   - `client_name = kullanıcıya görünen telefon adı`
4. TV `PairingRequestAck` döndürür.
5. İstemci `Options` gönderir:
   - `preferred_role = ROLE_TYPE_INPUT`
   - `input_encodings = HEXADECIMAL`
   - `symbol_length = 6`
6. TV yanıtlar; istemci `Configuration` gönderir:
   - `client_role = ROLE_TYPE_INPUT`
   - encoding: 6 haneli hexadecimal
7. TV `ConfigurationAck` döndürür ve ekranda altı haneli kod gösterir.
8. Telefon, istemci ve sunucu RSA anahtarlarından kodu türetir:

```text
secret = SHA-256(
    client_certificate_RSA_modulus ||
    client_certificate_RSA_exponent ||
    server_certificate_RSA_modulus ||
    server_certificate_RSA_exponent ||
    four_byte_nonce
)

nonce = hex_decode(pairing_code[2:])
secret[0] == hex_decode(pairing_code[0:2])
```

9. Telefon `Secret(secret)` mesajını gönderir.
10. TV `SecretAck` döndürür ve istemci sertifikası eşleşmiş olur.
11. Telefon artık `6466` portuna aynı sertifikayla bağlanabilir.

### 3.3 Secret hesabının kaynakları

- Resmî AOSP `google-tv-pairing-protocol` Java kodu, RSA modulus, public exponent ve rastgele nonce'un SHA-256 ile birleştirilmesini doğruluyor.
- AOSP Java kodunda BigInteger başındaki gereksiz null byte'ları temizleniyor.
- `androidtvremote2` Python kodu aynı işlemi uygular ve tipik RSA public exponent `65537` için uyumlu hexadecimal giriş kullanır.
- TV'nin ürettiği kodun ilk baytı ile hesaplanan secret'ın ilk baytı aynı olmalıdır; bu kontrol yanlış kod yerine TV'den yeni kod istenirken kullanılabilir.

### 3.4 Protobuf eşleşme tanımı

Kaynak konumları:

- Resmî eski Polo şeması: [`proto/polo.proto`](https://android.googlesource.com/platform/external/google-tv-pairing-protocol/+/refs/heads/master/proto/polo.proto)
- v2 kullanan güncel şema: [`src/androidtvremote2/polo.proto`](https://github.com/tronikos/androidtvremote2/blob/v0.3.2/src/androidtvremote2/polo.proto)
- TLS framing: [`src/androidtvremote2/base.py`](https://github.com/tronikos/androidtvremote2/blob/v0.3.2/src/androidtvremote2/base.py)
- Eşleşme uygulaması: [`src/androidtvremote2/pairing.py`](https://github.com/tronikos/androidtvremote2/blob/v0.3.2/src/androidtvremote2/pairing.py)

Resmî AOSP `polo.proto`, bazı alanlarda `type + payload` yapısıyla daha eski/genel Polo yaklaşımını gösterir. `androidtvremote2 v0.3.2`, Node.js v2 uygulamasına dayalı olarak `OuterMessage` içine eşleşme mesajlarını doğrudan alan olarak koyar. Bu nedenle üretim için referans alınması gereken v2 alan düzeni `androidtvremote2 v0.3.2` şemasıdır; AOSP dosyası tek başına kopyalanmamalıdır.

## 4. Kumanda oturumu ve komutlar

### 4.1 Oturum başlangıcı

`6466` bağlantısında:

1. TV `RemoteConfigure` gönderir; cihaz bilgisi ve desteklenen özellik bit maskesi gelir.
2. İstemci kendi etkin özelliklerini ve cihaz bilgisini `RemoteConfigure` ile döndürür.
3. TV `RemoteSetActive` gönderir.
4. TV hazır olduğunda `RemoteStart` gönderir.
5. İstemci komutları gönderir; TV boşta kaldığında ping isteği gönderir ve istemci yanıtlar.

Özellik bitlerinden `androidtvremote2 v0.3.2` şu değerleri kullanıyor:

```text
PING      = 1 << 0
KEY       = 1 << 1
IME       = 1 << 2
VOICE     = 1 << 3
UNKNOWN_1 = 1 << 4
POWER     = 1 << 5
VOLUME    = 1 << 6
APP_LINK  = 1 << 9
```

TV desteklemiyorsa özellik maskesi kesişir. Uygulama, cihaz özelliklerini varsayıp başarısız komutlar göndermemeli.

### 4.2 Komut yetenekleri

| İşlem | Protokol yolu | Durum |
|---|---|---|
| Yukarı/aşağı/sol/sağ/OK | `RemoteKeyInject` + karşılık gelen `KeyEvent` | Mümkün |
| Geri | `KEYCODE_BACK` | Mümkün |
| Ana ekran | `KEYCODE_HOME` | Mümkün; Android framework anahtarı yakalar |
| Ses aç/kapat | `KEYCODE_VOLUME_UP`, `KEYCODE_VOLUME_DOWN` | Mümkün |
| Sessiz | `KEYCODE_VOLUME_MUTE` | Mümkün |
| Oynat/durdur | `KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_PAUSE`, `KEYCODE_MEDIA_PLAY_PAUSE` | Mümkün |
| İleri/geri/rewind/fast-forward | İlgili medya keycode'ları | Mümkün |
| Uzun basış | `START_LONG` ve `END_LONG` yönleri | Mümkün |
| Metin girişi | `RemoteImeBatchEdit` | Mümkün; bazı cihazlar TV ekranında mobil klavye uyarısı gösterebilir |
| Web linki | `RemoteAppLinkLaunchRequest.app_link` | Mümkün |
| Uygulama scheme | `RemoteAppLinkLaunchRequest.app_link` | Mümkün |
| Paket adı | `market://launch?id=<paket>` şeklinde örtülü açma | Teknik olarak denenebilir; kütüphane uygulama adıyla açmanın güvenilir olmadığını açıkça belirtir |
| Ses seviyesi durumu | `RemoteSetVolumeLevel` yanıtı | Okunabilir |
| Cihaz açık/kapalı durumu | `RemoteStart.started` | Okunabilir |
| Ön plandaki uygulama | `RemoteImeKeyInject.app_info.app_package` | Okunabilir |
| Sesli arama | `RemoteVoiceBegin/Payload/End`, PCM 16-bit mono 8 kHz | Mümkün; cihaz/mikrofon davranışına bağlı |
| Ekran yansıtma | Yok | Bu protokolle mümkün değil |
| Dosya aktarımı | Yok | Bu protokolle mümkün değil |
| TV uygulamasının özel komutları | Yok | AfuRemote'ın özel sunucusu gerekir |

Kısa protobuf örneği:

```text
RemoteMessage
  remote_key_inject
    key_code: KEYCODE_DPAD_CENTER
    direction: SHORT
```

Metin örneği:

```text
RemoteMessage
  remote_ime_batch_edit
    ime_counter: <TV tarafından verilen sayaç>
    field_counter: <TV tarafından verilen sayaç>
    edit_info[0]
      insert: 1
      text_field_status
        start: len(text)-1
        end: len(text)-1
        value: text
```

Bağlantı/mesaj protobuf tanımı:

- [`remotemessage.proto`](https://github.com/tronikos/androidtvremote2/blob/v0.3.2/src/androidtvremote2/remotemessage.proto)
- [`remote.py`](https://github.com/tronikos/androidtvremote2/blob/v0.3.2/src/androidtvremote2/remote.py)

## 5. Açık kaynak uygulamalar

### 5.1 Python: `tronikos/androidtvremote2`

- **Sürüm:** `0.3.2`
- **Yayın:** 01.09.2026
- **Lisans:** Apache-2.0
- **Olgunluk:** 132 yıldız; güncel; Home Assistant tarafından doğrudan kullanılıyor.
- **Kapsam:** mDNS demosu, RSA sertifikası, 6467 eşleşme, 6466 kumanda, tuş, metin, uygama/link, ses ve cihaz durumu.

En güçil referans budur. Protobuf şemaları ve Apache-2.0 lisansı bulunduğu için doğrudan kopyalanabilir; ancak Python kodu Android'e taşınırken uygun lisans bildirimi korunmalıdır.

Kısa örnekler:

```python
await remote.async_generate_cert_if_missing()
await remote.async_start_pairing()
await remote.async_finish_pairing("ABCDEF")
await remote.async_connect()

remote.send_key_command("DPAD_RIGHT")
remote.send_key_command("DPAD_CENTER")
remote.send_text("https://example.com")
remote.send_launch_app_command("https://www.youtube.com")
```

### 5.2 Home Assistant: `androidtv_remote`

İncelenen sürüm:

- Home Assistant Core `2026.9.3`
- Bağımlılık: `androidtvremote2==0.3.2`
- Lisans: Apache-2.0
- Keşif türü: `_androidtvremote2._tcp.local.`

Kaynak konumları:

- [`manifest.json`](https://github.com/home-assistant/core/blob/dev/homeassistant/components/androidtv_remote/manifest.json)
- [`__init__.py`](https://github.com/home-assistant/core/blob/dev/homeassistant/components/androidtv_remote/__init__.py)
- [`config_flow.py`](https://github.com/home-assistant/core/blob/dev/homeassistant/components/androidtv_remote/config_flow.py)
- [`helpers.py`](https://github.com/home-assistant/core/blob/dev/homeassistant/components/androidtv_remote/helpers.py)
- [`remote.py`](https://github.com/home-assistant/core/blob/dev/homeassistant/components/androidtv_remote/remote.py)
- [`media_player.py`](https://github.com/home-assistant/core/blob/dev/homeassistant/components/androidtv_remote/media_player.py)

Doğrulanan davranışlar:

- Zeroconf kaydından doğrudan config flow başlatır.
- TXT `bt` değerinden benzersiz cihaz kimliği oluşturur.
- IP değiştiğinde mevcut config entry'yi günceller.
- Aynı istemci adıyla `6467` eşleşmesi başlatır ve TV'deki kodu ister.
- Sertifika ve özel anahtarı Home Assistant `.storage` altında tutar.
- `6466` bağlantısını `androidtvremote2` üzerinden açar.
- Tuş, kısa/uzun basış, açma/kapatma, uygulama, ses, medya oynatma ve kanal numarası gönderir.
- ADB veya TV geliştirici seçenekleri gerektirmez.

Home Assistant, üretim kalitesindeki davranış ve ürün entegrasyonu bakımından referanstır.

### 5.3 Resmî AOSP Java/C++ eşleşme kütüphanesi

Kaynak: [`platform/external/google-tv-pairing-protocol`](https://android.googlesource.com/platform/external/google-tv-pairing-protocol/)

Doğrulanan içerik:

- `proto/polo.proto`
- `java/src/com/google/polo/pairing/ClientPairingSession.java`
- `java/src/com/google/polo/pairing/PoloChallengeResponse.java`
- `java/src/com/google/polo/encoding/HexadecimalEncoder.java`
- C++ karşılıkları
- Apache-2.0 lisansı ve NOTICE

Bu depo eşleşme algoritmasının resmî kökenini gösterir. Ancak tam Google TV Remote v2 kontrol servisinin ve `_androidtvremote2._tcp` ağ sunucusunun bütünü değildir.

### 5.4 Kotlin/Java Android uygulama örnekleri

Aşağıdaki depolar incelendi; ancak lisans dosyaları bulunmadığı için kodları AfuRemote'a kopyalanmamalıdır.

| Depo | Son push | Lisans | Protobuf | Değerlendirme |
|---|---:|---|---|---|
| [`Junaid546/Android-TV-Remote-`](https://github.com/Junaid546/Android-TV-Remote-) | 13.03.2026 | Yok | `android/app/src/main/proto/pairing.proto`, `remote.proto` | NSD, TLS, 6466/6467 ve eşleşme akışını Kotlin/Java ile gösteriyor; ancak şema AfuRemote için tek doğru kaynak değil |
| [`nrv-96/TvRemote`](https://github.com/nrv-96/TvRemote) | 07.06.2026 | Yok | `app/src/main/proto/remotemessage.proto` | Android UI ve protokol katmanı örneği; keşfi eski `_androidtvremote._tcp` türüne yöneliyor ve şema alanları kısmen farklı |
| [`Mohammadhesham1/tv-remote-v2`](https://github.com/Mohammadhesham1/tv-remote-v2) | 15.05.2026 | Yok |Depoda uygulama protobuf tanımı doğrulanamadı | Java `NsdManager` örneği; referans olarak yetersiz |
| [`pritpatelbeetonz/remote_controller`](https://github.com/pritpatelbeetonz/remote_controller) | 10.08.2026 | Yok | `android/app/src/main/proto/remotemessage.proto` | NSD kuyruğu, TLS ve Kotlin iskeleti var; eşleşme mesajları kütüphanedeki v2 akışından tam ve güvenilir biçimde doğrulanamadı |

**Lisans sonucu:** Kotlin/Java örnekleri inceleme amaçlıdır. Kod kopyalanacaksa yalnızca açık lisansı olan AOSP ve `androidtvremote2` materyali kullanılmalıdır.

## 6. Android `NsdManager` ile `_androidtvremote2._tcp` keşfi

### 6.1 Önerilen temel akış

```kotlin
val request = NsdManager.DiscoveryRequest(
    NsdManager.ServiceType("_androidtvremote2._tcp"),
    NsdManager.ProtocolType.DNS_SD
)

nsdManager.discoverServices(
    request,
    mainExecutor,
    object : NsdManager.DiscoveryListener {
        override fun onServiceFound(info: NsdServiceInfo) {
            // API 34+ ServiceInfoCallback veya eski yolda resolveService
        }
        // diğer callback'ler
    }
)
```

### 6.2 Android 12–14 tuzakları

#### MulticastLock

Android SDK dokümanına göre:

- Android 12 ve öncesi ile T Extensions 7 almayan bazı Android 13 cihazlarda mDNS almak için `WifiManager.MulticastLock` gerekir.
- T Extensions 7 ve sonrasında foreground uygulamalar için sistem multicast alımını otomatik yönetir.
- Arka plan uygulamalar lock almaktan kaçınmalıdır.

Uygulama lock'u `discoverServices` çağrısından **önce** almalı, bırakmalıdır. Kilit `resolveService` tamamlanana kadar açık tutulmalıdır. Foreground olmayan tarama için kalıcı lock pil tüketimini artırır.

#### Tek çözümleme kuyruğu

Eski Android yollarında aynı anda birden fazla `resolveService` çağrısı `FAILURE_ALREADY_ACTIVE` verebilir. Bulunan servisleri kuyruğa alıp tek bir resolve worker ile sırayla çözmek gerekir. Başarısız çözümlemeler geçici hata olarak sınırlı sayıda tekrar edilebilir.

#### API 34 deprecation

Android 14/API 34:

- `resolveService()` deprecated olmuştur.
- Dönen `NsdServiceInfo` bayat olabilir.
- Callback anında tüm host adresleri bulunmayabilir.
- Android 14 T Extensions 22 ve üzerinde `registerServiceInfoCallback(DiscoveryRequest, Executor, ServiceInfoCallback)` keşif + güncel çözümlemeyi birleştirir.

Düşük API'lerde legacy listener/resolve, API 34+'ta callback tabanlı akış kullanılmalıdır.

#### Host adresi seçimi

- API 34 öncesinde çoğunlukla `info.host` kullanılır.
- API 34+'ta `hostAddresses` kullanılabilir.
- Birden fazla adres varsa IPv4 tercih edilebilir, fakat yalnızca ilk adres seçilmemeli; TV gerçekten 6466'yı kabul edene kadar adaylar denenmelidir.
- IPv6-only veya VPN/iki ağ arayüzlü telefonlarda `LocalIp`/interface seçimi sonuçları değiştirebilir.

#### Servis tipi son noktası

- Zeroconf tam türü: `_androidtvremote2._tcp.local.`
- `NsdManager` doküman örnekleri: `_androidtvremote2._tcp`
- Bazı örnek uygulamalar `_androidtvremote2._tcp.` kullanır.
- AOSP `NsdServiceInfo.setServiceType()` değeri doğrudan saklar.

Bu nedenle tek bir biçim evrensel kabul edilmemeli. NsdManager akışında doküman örneğine uygun kısa biçim kullanılmalı; cihaz matrisi testinde alternatif biçimler denemelidir.

#### Ağ değişimi

Wi-Fi ağı, adresi veya DHCP lease'i değiştiğinde:

- Eski discovery kayıtları geçersizleşebilir.
- SRV/TXT kaynakları yeniden alınmalıdır.
- TLS oturumu kapatılıp yeni adrese taşınmalıdır.
- Cihaz listesi eski IP ile kalıcı görünmemelidir.

#### Ağ engelleri

Aynı SSID tek başına yeterli değildir. Aşağıdaki durumlar mDNS'yi veya doğrudan TCP'yi engelleyebilir:

- Guest network
- AP client isolation
- Farklı VLAN/subnet
- Konuk/hotspot ağı
- VPN
- TV/phone güvenlik duvarı
- mDNS reflector/proxy'nin kapalı olması
- DHCP sonrası farklı subnet
- Cihazın derin uykuda olması

Bunların tamamı Android `NsdManager` hatası değildir; ağ topolojisinden kaynaklanır.

### 6.3 Önerilen dayanıklılık davranışı

1. Hem `_afuremote._tcp` hem `_androidtvremote2._tcp` paralel taranmalı.
2. Eski çözümleme API'sinde tek worker kuyruğu kullanılmalı.
3. API 34+'ta `ServiceInfoCallback` kullanılmalı.
4. SRV adres/portu doğrulanmadan cihaz “bulundu” sayılmamalı.
5. Ağ değişiminde tüm keşif yeniden başlatılmalı.
6. Eski kayıtlar IP + servis kimliği + TXT `bt` ile birleştirilmeli.
7. Kısa süreli çözümleme hataları tekrar edilmeli; `onStartDiscoveryFailed` sonrası discovery yeniden başlatılmalı.
8. Kullanıcıya “Wi-Fi aynı ama multicast engelli” gibi kesin bir hata nedeni ancak tanılanabilirse gösterilmeli.

## 7. Mevcut AfuRemote kodunun değerlendirmesi

### 7.1 Telefon tarafı

`app/src/main/java/com/afudm/afuremote/phone/TvDiscovery.kt`:

- `CHANGE_WIFI_MULTICAST_STATE` izni manifestte mevcut.
- `createMulticastLock("afuremote-bulma")`, discovery başlamadan önce alınıyor.
- Eski `discoverServices + resolveService` yolu kullanılıyor.
- `resolveLock`, aynı anda tek resolve çağrısı yapılmasını önlüyor; bu, Android 12–13 `FAILURE_ALREADY_ACTIVE` tuzağına karşı doğru bir önlem.
- API 34'te `hostAddresses` okunuyor; IPv4 önceliği veriliyor.
- Yalnızca `_afuremote._tcp.` aranıyor.
- 2,5 saniye sonra özel UDP broadcast ve `/24` port taraması yapılıyor.
- İlk açılışta kayıtlı cihazlar hemen doğrulanıyor.
- NSD sonucu, ardından `GET /v1/info` ile doğrulanıyor.

**Doğru taraflar:**

- Bilinen cihazları yeniden denemesi.
- mDNS, özel UDP ve subnet tarama katmanlarını birlikte kullanması.
- Resolve işlemlerini kilitlemesi.
- NSD bulgusunu gerçek HTTP cevabıyla doğrulaması.
- API 34 host adreslerini dikkate alması.

**Eksikler ve riskler:**

1. **Stock Android TV Remote v2 aranmıyor.** Yalnızca `_afuremote._tcp` taranıyor.
2. **API 34 legacy resolve kullanılıyor.** `registerServiceInfoCallback` tercih edilmiyor; çözülen adres bayat kalabilir.
3. **Ağ değişimini dinlemiyor.** Wi-Fi/iface/IP değişince NSD yeniden kurulmuyor.
4. **MulticastLock koşulsuz alınıyor.** Android 13 T Extensions 7 ve sonrasında gereksiz pil maliyeti oluşturabilir.
5. **Servis tipi biçimi karışık kullanılıyor.** Zeroconf/NSD için `.local.` ve son nokta conventions'ı ayrımı yapılmamış.
6. **Tek lifecycle.** `stop()` çağrısı, hâlâ çözümlemekte olan callback'ler için iptal/nesne kimliği kontrolü içermiyor; eski callback yeni listede yanlış güncelleme yapabilir.
7. **2,5 saniyelik NSD grace kısa olabilir.** Ağ gecikmesi veya Android NSD cache davranışında yetersiz kalabilir.
8. **Doğrulama yalnızca Afu HTTP protokolüne bağlı.** Stock TV için 6466 TLS/protobuf probe gerekir.
9. **Aynı TV iki kez görünebilir.** İleride iki servis paralel aranırsa IP/TXT `bt` ile birleştirme gerekir.

### 7.2 TV tarafı

`app/src/main/java/com/afudm/afuremote/tv/NsdAdvertiser.kt`:

- `_afuremote._tcp.` servisini 9870 portunda kaydeder.
- `AfuTvService` foreground service başlatıldığında HTTP sunucusu hazır olduktan sonra reklamı başlatır.
- Boot sonrasında yalnızca kalıcı mod `TV` ise servis başlatılır.
- Telefonda NSD sonrası aynı HTTP bilgi uç noktası doğrulanır.

**Doğru taraflar:**

- Sunucu portu ile mDNS portu aynı.
- Reklam, HTTP sunucusu başarıyla açıldıktan sonra başlatılıyor.
- Servis foreground'da tutuluyor.

**Eksikler ve riskler:**

1. **AfuRemote olmadan stock TV görünmez.** Bu, mimari seçiminin doğrudan sonucu.
2. **Network callback yok.** Wi-Fi değişince kayıt eski ağda kalabilir.
3. **Registration callback tamamlanmadan yeniden register deneyebilir.** `registered` yalnızca callback'te true oluyor.
4. **Pending registration temizliği eksik olabilir.** Servis callback gelmeden yok edilirse listener yaşam döngüsü ve unregister yönetimi zayıf.
5. **Servis adı çakışmaları yönetilmiyor.** NsdManager adı değiştirebilir; kayıt edilen gerçek ad saklanmalı.
6. **Android 12–14 NSD edge-case logları sınırlı.** Hata kodları loglanıyor, fakat ağ/topoloji teşhisi yok.
7. **Aynı ağdaki diğer cihazlara açık HTTP/UDP tasarımı var.** Mevcut imza ve eşleşme mekanizması var; yeni protokol eklenirken mevcut güvenlik sınırı korunmalı.

### 7.3 “Telefon TV'yi bulamıyor” olası nedenleri

Öncelik sırasıyla:

1. **TV'de AfuRemote TV modu/uygulaması yok veya çalışmıyor.** Beklenen sonuç.
2. **AfuRemote stock `_androidtvremote2._tcp` servisini aramıyor.** Mevcut kod yalnızca kendi özel servisini arıyor.
3. **TV, Wi-Fi değiştikten sonra NSD kaydını yeniden yayınlamıyor.** `AfuTvService` ağ callback'i dinlemiyor.
4. **NSD kaydı oluşmuş, fakat 9870 portu erişilemiyor.** Telefon `verify()` sonucu cihazı listeden düşürüyor.
5. **AP client isolation/Guest/VLAN.** Aynı SSID olmasına rağmen multicast ve TCP engellidir.
6. **Android 14 legacy resolve sonucu eksik/bayat.** Mevcut `hostAddresses` okuması olsa da callback yaklaşımı daha kararlı olur.
7. **TV uygulaması foreground service olmasına rağmen üretici kısıtlamasıyla servisini durduruyor.** `START_STICKY` yardımcı olur, fakat üretici power management uygulamasını öldürebilir.
8. **Servis portu 9870 başka bir uygulama/process tarafından işgal edilmiş.** `AfuTvService` HTTP sunucusunu başlatamaz ve mDNS kaydı yapmaz.
9. **DHCP sonrası telefon/TV farklı alt ağlara düşmüş.** Özel UDP broadcast ve subnet taraması da başarısız olur.

## 8. ÖNERİ

### Birincil öneri

AfuRemote'u **tek protokolden zorunlu olarak bağımlı** kalmak yerine iki TV taşıma katmanını desteklemesi:

1. **Stock Android TV Remote v2 taşıma katmanı**
   - TV'de uygulama kurulumu gerektirmez.
   - mDNS, TLS/sertifika, protobuf.
   - Yön tuşları, OK, geri, ana ekran, ses, medya, metin, link/uygulama.
2. **AfuRemote taşıma katmanı**
   - Mevcut özel HTTP/eşleşme protokolü korunur.
   - Ekran yansıtma, özel medya sunucusu, cihaz üzerinde özel işlemler ve stock protokolün desteklemediği özellikler için kullanılır.

Kullanıcı açısından tek bir cihaz listesi ve tek kumanda ekranı korunmalı; cihaz modelinde `backend = ATV_REMOTE_V2 | AFUREMOTE` taşınmalıdır. Stock TV görüldüğünde ek uygulama istememek en büyük ürün kazancıdır. AfuRemote TV modu kuruluysa ikinci cihaz kaydı aynı IP/TXT ile birleştirilmeli ve kullanıcıya iki özellik grubu gösterilmeli.

### Teknik öneri

- Protokol doğrulaması ve protobuf şemaları için Apache-2.0 `androidtvremote2 v0.3.2` esas alınmalı.
- Kotlin'de protobuf üretimi Android Gradle protobuf plugin ile yapılmalı.
- RSA sertifika üretimi Android Keystore içinde 2048 bit RSA ile yapılmalı. Python'daki PEM dosya modeli birebir kopyalanmamalı; amaç aynı protokol, uygulama içi güvenli depolama.
- TLS sunucu sertifikası doğrulaması, eşleşmede sunucu modulus/exponent'i okunmalı; sonraki bağlantılarda mümkünse sunucu parmak izi sabitlenmeli. İlk eşleşmede kullanıcı onayı zaten TV ekranındaki kodla sağlanır.
- Uzun görevler foreground service/WorkManager ile güvenli yönetilmeli.
- Android 14+ ServiceInfoCallback, eski sürümlerde kuyruklanmış `resolveService` kullanılmalı.
- Her komut, cihazın `remote_configure.code1` özellik maskesine göre etkinleştirilmeli.

## 9. UYGULAMA PLANI

Bu plan araştırma gereksinimi nedeniyle uygulanmadı; kod değişikliği yapılmadı.

### Adım 1 — Protobuf sözleşmelerini ekle

Yeni dosyalar:

- `app/src/main/proto/atvremote/polo.proto`
- `app/src/main/proto/atvremote/remotemessage.proto`

Kaynak:

- Apache-2.0 `androidtvremote2 v0.3.2`
- Apache-2.0 AOSP `google-tv-pairing-protocol` eşleşme yardımcıları

Kontrol:

- Alan numaraları v0.3.2 ile birebir aynı.
- Lisans/attribution notu ekleniyor.
- Mesaj framing varint uzunluk + protobuf olarak test ediliyor.

### Adım 2 — Android protobuf build katmanını ekle

Değiştirilecek dosya:

- `app/build.gradle.kts`
- `build.gradle.kts`
- `settings.gradle.kts`

Gerekenler:

- Protobuf Gradle plugin
- Java/Kotlin protobuf codegen
- lite/runtime uyumlu protobuf runtime

### Adım 3 — Stock TV mDNS keşfini ekle

Yeni dosya:

- `app/src/main/java/com/afudm/afuremote/atvremote/AtvDiscovery.kt`

Değiştirilecek dosyalar:

- `app/src/main/java/com/afudm/afuremote/phone/TvDiscovery.kt`
- `app/src/main/java/com/afudm/afuremote/phone/TvDevice.kt`
- `app/src/main/java/com/afudm/afuremote/phone/PhoneGraph.kt`

Davranış:

- `_androidtvremote2._tcp` ve `_afuremote._tcp` paralel taranır.
- API 34+'ta `ServiceInfoCallback`.
- Eski Android'da tek çözümleme worker'ı.
- `bt`, host, port ve cihaz adı normalize edilir.
- Stock adayın 6466 TLS erişimi doğrulanır.
- Aynı cihaz iki servisle bulunursa birleştirilir.

### Adım 4 — Güvenli istemci kimliği ve TLS eşleşmesini ekle

Yeni dosyalar:

- `app/src/main/java/com/afudm/afuremote/atvremote/AtvCredentialStore.kt`
- `app/src/main/java/com/afudm/afuremote/atvremote/AtvPairingManager.kt`
- `app/src/main/java/com/afudm/afuremote/atvremote/AtvTlsClientFactory.kt`
- `app/src/main/java/com/afudm/afuremote/atvremote/AtvProtocolFramer.kt`

Davranış:

- Android Keystore RSA-2048.
- Özel anahtar APK dışına çıkmaz.
- 6467 üzerinde Polo eşleşme adımları.
- TV sertifikası ve parmak izi saklama.
- 6466'da sunucu parmak izi doğrulama; eşleşme öncesi kullanıcı/TV kodu akışı.
- Sertifika silinince yeniden eşleşme durumu.

### Adım 5 — Stock kumanda istemcisini ekle

Yeni dosyalar:

- `app/src/main/java/com/afudm/afuremote/atvremote/AtvRemoteClient.kt`
- `app/src/main/java/com/afudm/afuremote/atvremote/AtvCapabilities.kt`
- `app/src/main/java/com/afudm/afuremote/atvremote/AtvCommandMapper.kt`

Davranış:

- `RemoteConfigure` özellik maskesi.
- `RemoteSetActive` ve `RemoteStart` hazır olma akışı.
- KeyEvent eşlemesi.
- IME metin gönderimi.
- App/deep link gönderimi.
- Ping/timeout/reconnect.
- Desteklenmeyen komutları UI'da pasifleştirme.

### Adım 6 — Ortak cihaz/denetleyici arayüzüne geç

Değiştirilecek dosyalar:

- `app/src/main/java/com/afudm/afuremote/phone/TvApi.kt`
- `app/src/main/java/com/afudm/afuremote/phone/PhoneController.kt`
- `app/src/main/java/com/afudm/afuremote/phone/PhoneHomeScreen.kt`
- `app/src/main/java/com/afudm/afuremote/phone/RemoteScreen.kt`
- `app/src/main/java/com/afudm/afuremote/phone/DiscoveryScreen.kt`
- `app/src/main/java/com/afudm/afuremote/phone/KnownTvStore.kt`

Davranış:

- `TvDevice.backend`.
- Stock ve AfuRemote istemcileri ortak komut arayüzüne uyarlanır.
- Stock cihazda eşleşme PIN akışı; Afu cihazda mevcut kullanıcı onaylı eşleşme korunur.
- Ekran yansıtma gibi yalnızca AfuRemote'da olan özellikler cihaz yeteneğine göre gizlenir.

### Adım 7 — Mevcut AfuRemote mDNS yaşam döngüsünü sağlamlaştır

Değiştirilecek dosyalar:

- `app/src/main/java/com/afudm/afuremote/tv/NsdAdvertiser.kt`
- `app/src/main/java/com/afudm/afuremote/tv/AfuTvService.kt`
- `app/src/main/java/com/afudm/afuremote/phone/TvDiscovery.kt`

Davranış:

- Network callback'iyle unregister/re-register.
- Registration listener referansı ve gerçek kayıtlı ad.
- Servis adı çakışması için state.
- Ağ değişiminde yeniden tarama.
- Android extension seviyesine göre MulticastLock.
- API 34+ ServiceInfoCallback.
- Stop sonrası geç gelen callback'leri geçersiz kılma.

### Adım 8 — Test matrisi

Yeni testler:

- `app/src/test/java/com/afudm/afuremote/atvremote/ProtobufFramingTest.kt`
- `app/src/test/java/com/afudm/afuremote/atvremote/PairingSecretTest.kt`
- `app/src/test/java/com/afudm/afuremote/atvremote/AtvCommandMapperTest.kt`
- `app/src/test/java/com/afudm/afuremote/phone/TvDiscoveryMergeTest.kt`

Cihaz matrisi:

- Android 12, 13 ve 14+ NSD
- API 33 ve 34 resolve/callback farkı
- IPv4-only, IPv6, VPN
- Aynı `/24` ve farklı subnet
- Guest network/client isolation
- Android TV Remote Service v2 olan en az iki marka/model
- Remote Service'in çıkarıldığı veya portların kapatıldığı bir cihaz
- TV uygulamasının force-stop/killer sonrası yeniden açılması
- Aynı TV'de hem `_androidtvremote2` hem `_afuremote` reklamının bulunması

## 10. Sonuç

Google TV ve Android TV kumandalarının TV'ye uygulama kurmama avantajı, protokolün TV'de standart bir sistem servisi tarafından sunulmasından gelir. mDNS cihazı bulur, TLS + istemci sertifikası eşleşmeyi kanıtlar ve protobuf komutları doğrudan Android TV çalışma zamanına gider.

AfuRemote'un telefon tarafındaki özel mDNS ve HTTP protokolü çalışabilir, ancak yalnızca AfuRemote'un TV tarafında etkin olduğu cihazları kapsar. Stock Android TV cihazlarını kapsamak ve “TV'ye uygulama kurmadan kumanda” özelliğini gerçekleştirmek için `_androidtvremote2._tcp` keşfi ile Remote v2 istemcisi eklenmelidir. Mevcut AfuRemote taşıma katmanı ise ekran yansıtma ve protokole özel işlevler için korunmalıdır.
