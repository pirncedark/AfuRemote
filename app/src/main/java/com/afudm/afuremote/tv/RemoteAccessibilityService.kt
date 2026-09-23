package com.afudm.afuremote.tv

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.afudm.afuremote.protocol.RemoteKey

class RemoteAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    /** Yön tuşları: Android 13+ sistem eylemi; öncesinde odaklı öğeden komşuya odak taşınır. */
    fun dpad(key: RemoteKey): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val action = when (key) {
                RemoteKey.DPAD_UP -> GLOBAL_ACTION_DPAD_UP
                RemoteKey.DPAD_DOWN -> GLOBAL_ACTION_DPAD_DOWN
                RemoteKey.DPAD_LEFT -> GLOBAL_ACTION_DPAD_LEFT
                RemoteKey.DPAD_RIGHT -> GLOBAL_ACTION_DPAD_RIGHT
                RemoteKey.DPAD_CENTER -> GLOBAL_ACTION_DPAD_CENTER
                else -> return false
            }
            return performGlobalAction(action)
        }
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (key == RemoteKey.DPAD_CENTER) return focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val direction = when (key) {
            RemoteKey.DPAD_UP -> View.FOCUS_UP
            RemoteKey.DPAD_DOWN -> View.FOCUS_DOWN
            RemoteKey.DPAD_LEFT -> View.FOCUS_LEFT
            RemoteKey.DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> return false
        }
        val next = focused.focusSearch(direction) ?: return false
        return next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    /** Odaklı yazı alanına metni yazar (TV klavyesini açmadan). */
    fun typeText(text: String): Boolean {
        val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    companion object { @Volatile var instance: RemoteAccessibilityService? = null; private set }
}
