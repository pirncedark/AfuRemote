package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.ApiResult
import com.afudm.afuremote.protocol.HATA_ERISILEBILIRLIK
import com.afudm.afuremote.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString

/** Telefonda gösterilen metinler. "İzin ver" ifadesi burada KULLANILMAZ (TV düğmesinin etiketi). */
object Messages {
    const val UNREACHABLE = "TV'ye ulaşılamadı — TV açık ve aynı Wi-Fi'de mi?"
    const val NO_TV = "TV bulunamadı — TV'de AfuRemote açık mı, telefon ve TV aynı Wi-Fi'de mi?"
    const val DENIED = "TV onay vermedi ya da 60 sn içinde yanıt gelmedi"
    const val WAITING_TV = "TV ekranındaki onayı bekliyor… (TV kumandasıyla onaylayın)"
    const val ACCESSIBILITY = "TV'de AfuRemote için Erişilebilirlik iznini açın (TV: Ayarlar → Erişilebilirlik → AfuRemote)"
    const val OLD_TV = "TV'deki AfuRemote eski — TV'de de güncelleyin"
    const val CLOCK = "TV ile telefonun saati eşitlenemedi — TV saatini kontrol edin"

    fun pairingCode(code: String) = "TV'deki kod: $code — aynıysa TV kumandasıyla onaylayın"

    fun forError(code: Int, body: String?): String {
        val hata = body?.let { runCatching { ProtocolJson.decodeFromString<ApiResult>(it).hata }.getOrNull() }.orEmpty()
        return when (hata) {
            HATA_ERISILEBILIRLIK -> ACCESSIBILITY
            "gecersiz_link" -> "Bu link anlaşılamadı"
            "acacak_uygulama_yok", "uygulama_bulunamadi" -> "TV'de bunu açabilecek uygulama yok"
            "bozuk_istek", "bilinmeyen_tus", "yok", "desteklenmiyor" -> OLD_TV
            "yazi_alani_yok" -> "TV'de önce bir yazı alanı seçin"
            else -> "TV hata verdi ($code)"
        }
    }
}
