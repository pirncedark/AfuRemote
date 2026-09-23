package com.afudm.afuremote.tv

import androidx.media3.exoplayer.ExoPlayer

object PlayerRegistry {
    @Volatile var current: ExoPlayer? = null
}
