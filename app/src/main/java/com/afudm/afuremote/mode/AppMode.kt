package com.afudm.afuremote.mode

enum class AppMode {
    TV, PHONE;

    companion object {
        /** override: "tv" | "phone" | null (otomatik). Bilinmeyen değer otomatik sayılır. */
        fun detect(isTelevisionUi: Boolean, hasLeanback: Boolean, override: String?): AppMode = when (override) {
            "tv" -> TV
            "phone" -> PHONE
            else -> if (isTelevisionUi || hasLeanback) TV else PHONE
        }
    }
}
