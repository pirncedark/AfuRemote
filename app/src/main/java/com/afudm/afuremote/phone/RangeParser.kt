package com.afudm.afuremote.phone

data class ByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

object RangeParser {
    private val SINGLE = Regex("""^bytes=(\d*)-(\d*)$""")

    /** Tek aralıklı "bytes=a-b" başlığı; karşılanamaz ya da bozuksa null. */
    fun parse(header: String?, size: Long): ByteRange? {
        if (header == null || size <= 0) return null
        val (a, b) = SINGLE.matchEntire(header.trim())?.destructured ?: return null
        return when {
            a.isEmpty() && b.isEmpty() -> null
            a.isEmpty() -> {
                val suffix = b.toLong()
                if (suffix <= 0) null else ByteRange(maxOf(0L, size - suffix), size - 1)
            }
            else -> {
                val start = a.toLong()
                val end = if (b.isEmpty()) size - 1 else minOf(b.toLong(), size - 1)
                if (start >= size || start > end) null else ByteRange(start, end)
            }
        }
    }
}
