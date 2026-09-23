package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairStartRequest
import com.afudm.afuremote.protocol.RemoteKey

interface TvApi {
    /** Onaylanırsa paylaşılan anahtarın hex biçimi, başarısızsa null döner. */
    fun pair(tv: TvDevice, req: PairStartRequest, onCode: (String) -> Unit): String?
    fun open(tv: TvDevice, authKey: String, req: OpenRequest): SendResult
    fun key(tv: TvDevice, authKey: String, key: RemoteKey): SendResult
    /** İmzalı genel komut (uygulama açma, yazı gönderme). */
    fun command(tv: TvDevice, authKey: String, path: String, json: String): SendResult = SendResult.Failed(Messages.OLD_TV)
}
