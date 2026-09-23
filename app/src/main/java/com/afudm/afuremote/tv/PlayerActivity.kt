package com.afudm.afuremote.tv

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { Log.i(TAG, "state=${if (isPlaying) "PLAYING" else "PAUSED"}") }
        override fun onPlayerError(error: PlaybackException) { Log.e(TAG, "Playback failed", error); Toast.makeText(this@PlayerActivity, "Video açılamadı", Toast.LENGTH_LONG).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = PlayerView(this)
        setContentView(view)
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        PlayerRegistry.current = exo
        view.player = exo
        exo.addListener(listener)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onStop() {
        player?.let { it.removeListener(listener); it.release() }
        if (PlayerRegistry.current === player) PlayerRegistry.current = null
        player = null
        super.onStop()
        finish()
    }

    companion object { const val EXTRA_URL = "url"; const val EXTRA_TITLE = "title"; private const val TAG = "AfuRemotePlayer" }
}
