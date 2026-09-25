package com.afudm.afuremote.atvremote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate

/** The private key never leaves AndroidKeyStore; only the public certificate is exposed. */
class AtvCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("atv_remote_v2", Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized fun ensureCertificate(): X509Certificate {
        if (!keyStore.containsAlias(ALIAS)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=AfuRemote"))
                .setCertificateSerialNumber(java.math.BigInteger.valueOf(System.currentTimeMillis()))
                .setCertificateNotBefore(java.util.Date())
                .setCertificateNotAfter(java.util.Date(System.currentTimeMillis() + 315_360_000_000L))
                .build())
            generator.generateKeyPair()
        }
        return keyStore.getCertificate(ALIAS) as X509Certificate
    }

    fun keyStore(): KeyStore = keyStore
    fun saveFingerprint(host: String, fingerprint: String) { prefs.edit().putString("cert_${host.lowercase()}", fingerprint).apply() }
    fun fingerprint(host: String): String? = prefs.getString("cert_${host.lowercase()}", null)

    private companion object { const val ALIAS = "afuremote_atv_remote_v2_client" }
}
