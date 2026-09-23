package com.afudm.afuremote.net

/** Telefonun alt ağındaki taranacak adresler. Büyük ağlarda telefonun /24'ü ile sınırlı kalır. */
object Subnet {
    fun hosts(ip: String, prefix: Int): List<String> {
        val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return emptyList()
        val self = parts.fold(0L) { acc, p -> (acc shl 8) or p.toLong() }
        val bits = prefix.coerceIn(24, 30)
        val mask = (0xFFFFFFFFL shl (32 - bits)) and 0xFFFFFFFFL
        val network = self and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
        // Telefona yakın adresler önce: evlerde TV çoğunlukla aynı DHCP aralığındadır.
        return ((network + 1) until broadcast)
            .sortedBy { kotlin.math.abs(it - self) }
            .map { n -> "${(n shr 24) and 255}.${(n shr 16) and 255}.${(n shr 8) and 255}.${n and 255}" }
    }
}
