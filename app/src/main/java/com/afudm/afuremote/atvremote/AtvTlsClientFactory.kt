package com.afudm.afuremote.atvremote

import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class AtvTlsClientFactory(private val credentials: AtvCredentialStore) {
    fun connect(host: String, port: Int, requirePinned: Boolean): Pair<SSLSocket, String> {
        credentials.ensureCertificate()
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(credentials.keyStore(), null) }
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                require(chain.isNotEmpty()) { "TV TLS certificate is missing" }
                val fingerprint = fingerprint(chain[0])
                val saved = credentials.fingerprint(host)
                if (requirePinned && saved != fingerprint) throw java.security.cert.CertificateException("TV certificate changed")
            }
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(trust), null) }
        val socket = (ssl.socketFactory.createSocket(host, port) as SSLSocket).apply {
            soTimeout = 10_000
            startHandshake()
        }
        val cert = socket.session.peerCertificates.first() as X509Certificate
        return socket to fingerprint(cert)
    }

    companion object {
        fun fingerprint(cert: X509Certificate): String = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
    }
}
