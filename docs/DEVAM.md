# AfuRemote - Devam Notu

Son guncelleme: 2026-09-29 (koordinator oturumu)

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

## ACIK HATALAR (29 Eyl, AfuRemote terminali buldu, koordinator kodda dogruladi) - SONRAKI OTURUMUN ILK ISI
1. `AtvSession.readFrame`: soTimeout=500 ms iken cercevenin ortasinda SocketTimeoutException gelirse okunmus baytlar atilir,
   sonraki okuma cerceve ortasindan baslar -> akis kayar, oturum kopup yeniden baglanir. Ayrica tus gonderimi <=500 ms gecikebilir.
   Cozum: ayri okuyucu thread (bloklayan okuma, timeout yok) + yazici `queue.take()`.
2. READY iken kuyruga giren tuslar kopma/yeniden baglanma sirasinda bekleyip sonra topluca gidebilir (bayat POWER vb.).
   Yeniden baglaninca kuyrugu temizle ya da komuta sure siniri koy.
- Not: gorev metnindeki "nonce 4 bayt" yanlisti; dogrusu 2 bayt (kod dogru).

## Sonraki adim
1. PR #10 MERGED (main 8c61053). Once yukaridaki 2 acik hatayi duzelt (codex yazar, opencode gradle test).
2. Gercek TV'de dene: eslestir -> tuslar gecikmesiz mi, uygulama arka plana gidip gelince oturum geri geliyor mu.
