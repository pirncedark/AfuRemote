package com.afudm.afuremote.tv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.afudm.afuremote.AppVisibility
import com.afudm.afuremote.BuildConfig
import com.afudm.afuremote.classify.ClassifiedLink
import com.afudm.afuremote.classify.LinkKind
import com.afudm.afuremote.pairing.PairingStore
import com.afudm.afuremote.protocol.ApiResult
import com.afudm.afuremote.protocol.InfoResponse
import com.afudm.afuremote.protocol.RemoteKey

class AndroidTvActions(private val context: Context, private val store: PairingStore) : TvActions {
    override fun info() = InfoResponse(store.deviceId(), deviceName(), android.os.Build.MODEL.orEmpty(), BuildConfig.VERSION_NAME)
    fun deviceName(): String = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "AfuRemote TV"

    override fun startPairPrompt(pairId: String, req: com.afudm.afuremote.protocol.PairStartRequest, code: String): Boolean =
        PairBroker.begin(context, pairId, req.deviceId, req.deviceName, code)

    override suspend fun awaitPairApproval(pairId: String): PairDecision = PairBroker.await(pairId)

    override fun open(link: ClassifiedLink, title: String): ApiResult {
        val intent = when (link.kind) {
            LinkKind.MEDIA -> Intent(context, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, link.url).putExtra(PlayerActivity.EXTRA_TITLE, title)
            LinkKind.WEB -> Intent(context, WebActivity::class.java).putExtra(WebActivity.EXTRA_URL, link.url)
            LinkKind.YOUTUBE -> youtubeIntent(link.url)
        }
        return when (launch(intent)) {
            LaunchResult.OK -> ApiResult(true)
            LaunchResult.NO_APP -> ApiResult(false, "acacak_uygulama_yok")
            LaunchResult.NOT_ALLOWED -> accessibilityError()
        }
    }

    private fun youtubeIntent(url: String): Intent {
        val app = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).setPackage(YOUTUBE_TV).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (app.resolveActivity(context.packageManager) != null) app
        else Intent(context, WebActivity::class.java).putExtra(WebActivity.EXTRA_URL, url)
    }

    override fun key(key: RemoteKey): ApiResult {
        val audio = context.getSystemService(AudioManager::class.java)
        when (key) {
            RemoteKey.VOL_UP -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            RemoteKey.VOL_DOWN -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            RemoteKey.MUTE -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            RemoteKey.PLAY_PAUSE, RemoteKey.SEEK_FWD, RemoteKey.SEEK_BACK -> {
                val active = PlayerRegistry.current
                if (active != null) Handler(Looper.getMainLooper()).post {
                    when (key) {
                        RemoteKey.PLAY_PAUSE -> if (active.isPlaying) active.pause() else active.play()
                        RemoteKey.SEEK_FWD -> active.seekTo(active.currentPosition + 10_000)
                        RemoteKey.SEEK_BACK -> active.seekTo((active.currentPosition - 10_000).coerceAtLeast(0))
                        else -> Unit
                    }
                } else dispatchMediaKey(audio, key)
            }
            RemoteKey.BACK -> return accessibilityAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            RemoteKey.HOME -> return accessibilityAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        }
        return ApiResult(true)
    }

    private fun dispatchMediaKey(audio: AudioManager, key: RemoteKey) {
        val code = when (key) {
            RemoteKey.PLAY_PAUSE -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            RemoteKey.SEEK_FWD -> android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            RemoteKey.SEEK_BACK -> android.view.KeyEvent.KEYCODE_MEDIA_REWIND
            else -> return
        }
        val now = android.os.SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, code, 0))
    }

    private fun accessibilityAction(action: Int): ApiResult {
        val service = RemoteAccessibilityService.instance ?: return accessibilityError()
        return if (service.performGlobalAction(action)) ApiResult(true) else ApiResult(false, "islem_basarisiz")
    }

    private fun accessibilityError() = ApiResult(false, com.afudm.afuremote.protocol.HATA_ERISILEBILIRLIK)

    private fun launch(intent: Intent): LaunchResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val starter: Context = RemoteAccessibilityService.instance
            ?: if (AppVisibility.isForeground) context else return LaunchResult.NOT_ALLOWED
        return try { starter.startActivity(intent); LaunchResult.OK }
        catch (e: ActivityNotFoundException) { LaunchResult.NO_APP }
        catch (e: SecurityException) { LaunchResult.NOT_ALLOWED }
        catch (e: Exception) { Log.e(TAG, "Could not launch activity", e); LaunchResult.NO_APP }
    }

    private enum class LaunchResult { OK, NO_APP, NOT_ALLOWED }
    private companion object { const val TAG = "AfuRemoteActions"; const val YOUTUBE_TV = "com.google.android.youtube.tv" }
}
