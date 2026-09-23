package com.afudm.afuremote.tv

import android.content.Context
import android.content.Intent
import android.util.Log
import com.afudm.afuremote.AppVisibility
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

object PairBroker {
    private val pending = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<PairDecision>>()

    suspend fun request(context: Context, deviceId: String, deviceName: String): PairDecision {
        val answer = CompletableDeferred<PairDecision>()
        if (pending.putIfAbsent(deviceId, answer) != null) return PairDecision.CANNOT_PROMPT
        return try {
            val intent = Intent(context, PairPromptActivity::class.java)
                .putExtra(PairPromptActivity.EXTRA_DEVICE_ID, deviceId)
                .putExtra(PairPromptActivity.EXTRA_DEVICE_NAME, deviceName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val starter = RemoteAccessibilityService.instance ?: if (AppVisibility.isForeground) context else null
            if (starter == null) return PairDecision.CANNOT_PROMPT
            try { starter.startActivity(intent) } catch (e: Exception) {
                Log.e(TAG, "Could not show pair approval", e)
                return PairDecision.CANNOT_PROMPT
            }
            withTimeoutOrNull(TIMEOUT_MS) { answer.await() } ?: PairDecision.DENIED
        } finally { pending.remove(deviceId, answer) }
    }

    fun decide(deviceId: String, approved: Boolean) {
        pending[deviceId]?.complete(if (approved) PairDecision.APPROVED else PairDecision.DENIED)
    }
    private const val TIMEOUT_MS = 60_000L
    private const val TAG = "AfuRemotePair"
}
