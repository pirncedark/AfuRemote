package com.afudm.afuremote.tv

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.afudm.afuremote.AppVisibility
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

object PairBroker {
    private data class Pending(val pairId: String, val deviceId: String, val answer: CompletableDeferred<PairDecision>)
    private val pending = java.util.concurrent.atomic.AtomicReference<Pending?>(null)

    /** Shows the approval screen with the pairing code; false when another pairing is waiting or no screen can be opened. */
    fun begin(context: Context, pairId: String, deviceId: String, deviceName: String, code: String): Boolean {
        val request = Pending(pairId, deviceId, CompletableDeferred())
        if (!pending.compareAndSet(null, request)) return false
        val starter = RemoteAccessibilityService.instance ?: if (AppVisibility.isForeground) context else null
        if (starter == null) {
            pending.compareAndSet(request, null)
            return false
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (pending.compareAndSet(request, null)) request.answer.complete(PairDecision.DENIED)
        }, TIMEOUT_MS)
        val intent = Intent(context, PairPromptActivity::class.java)
            .putExtra(PairPromptActivity.EXTRA_PAIR_ID, pairId)
            .putExtra(PairPromptActivity.EXTRA_DEVICE_ID, deviceId)
            .putExtra(PairPromptActivity.EXTRA_DEVICE_NAME, deviceName)
            .putExtra(PairPromptActivity.EXTRA_CODE, code)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            starter.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not show pair approval", e)
            pending.compareAndSet(request, null)
            false
        }
    }

    suspend fun await(pairId: String): PairDecision {
        val request = pending.get()?.takeIf { it.pairId == pairId } ?: return PairDecision.DENIED
        return try {
            withTimeoutOrNull(TIMEOUT_MS) { request.answer.await() } ?: PairDecision.DENIED
        } finally {
            pending.compareAndSet(request, null)
        }
    }

    fun decide(pairId: String, deviceId: String, approved: Boolean) {
        pending.get()?.takeIf { it.pairId == pairId && it.deviceId == deviceId }?.answer
            ?.complete(if (approved) PairDecision.APPROVED else PairDecision.DENIED)
    }

    private const val TIMEOUT_MS = 60_000L
    private const val TAG = "AfuRemotePair"
}
