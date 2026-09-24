package com.afudm.afuremote.tv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.cancel
import com.afudm.afuremote.R
import com.afudm.afuremote.pairing.PairingStore
import com.afudm.afuremote.pairing.TokenRegistry
import com.afudm.afuremote.protocol.TV_PORT
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class AfuTvService : Service() {
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var server: TvHttpServer? = null
    private var advertiser: NsdAdvertiser? = null
    private var udpDiscovery: UdpDiscoveryResponder? = null
    private var registry: TokenRegistry? = null
    private lateinit var store: PairingStore

    override fun onCreate() { super.onCreate(); store = PairingStore(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (intent?.action == ACTION_RESET_PAIRINGS) { registry?.clear(); store.saveApproved(emptyMap()) }
        if (server == null) startServer()
        return START_STICKY
    }

    private fun startServer() {
        val reg = store.tvRegistry().also { registry = it }
        val actions = AndroidTvActions(applicationContext, store)
        val router = TvRouter(actions, reg, onPairingsChanged = { store.saveApproved(it) })
        server = try { TvHttpServer(router).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) } }
        catch (e: IOException) { Log.e(TAG, "TV sunucusu acilamadi", e); null }
        if (server != null) {
            Log.i(TAG, "TV sunucusu hazir: port $TV_PORT")
            val name = "AfuRemote ${actions.deviceName()}".take(60)
            advertiser = NsdAdvertiser(this).also { it.register(name) }
            udpDiscovery = UdpDiscoveryResponder(name, store.deviceId()).also { it.start(serviceScope) }
        }
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "AfuRemote TV", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle("AfuRemote TV hazır")
            .setContentText("Telefondan gelen linkler bu TV'de açılır")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() { udpDiscovery?.stop(); advertiser?.unregister(); server?.stop(); serviceScope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AfuRemoteTv"
        private const val CHANNEL = "afuremote_tv"
        private const val NOTIFICATION_ID = 9870
        const val ACTION_RESET_PAIRINGS = "com.afudm.afuremote.RESET_PAIRINGS"
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, AfuTvService::class.java)) }
        fun resetPairings(context: Context) { ContextCompat.startForegroundService(context, Intent(context, AfuTvService::class.java).setAction(ACTION_RESET_PAIRINGS)) }
    }
}
