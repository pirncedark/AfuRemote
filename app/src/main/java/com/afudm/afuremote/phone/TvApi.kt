package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.RemoteKey

interface TvApi {
    /** Onaylanırsa token, reddedilir/ulaşılamazsa null. En çok ~70 sn sürer. */
    fun pair(tv: TvDevice, req: PairRequest): String?
    fun open(tv: TvDevice, token: String, req: OpenRequest): SendResult
    fun key(tv: TvDevice, token: String, key: RemoteKey): SendResult
}
