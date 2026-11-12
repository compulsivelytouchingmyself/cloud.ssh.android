package com.example.ssh

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.CloudSshApp
import com.example.MainActivity
import com.example.R
import com.example.data.model.ConnectionProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SshTunnelService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val binder = LocalBinder()
    lateinit var tunnelManager: SshTunnelManager
        private set

    inner class LocalBinder : Binder() {
        fun getService(): SshTunnelService = this@SshTunnelService
    }

    override fun onCreate() {
        super.onCreate()
        tunnelManager = SshTunnelManager(serviceScope)

        serviceScope.launch {
            tunnelManager.tunnelState.collect { state ->
                when (state) {
                    is TunnelState.Connected -> {
                        updateNotification(state)
                    }
                    is TunnelState.Disconnected, is TunnelState.Error -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                tunnelManager.disconnect()
            }
            ACTION_OPEN_BROWSER -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (!url.isNullOrBlank()) {
                    openBrowser(this, url)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun connectTunnel(profile: ConnectionProfile, onResult: (Result<Int>) -> Unit) {
        // Start foreground notification immediately in connecting state
        startForeground(
            CloudSshApp.NOTIFICATION_ID_TUNNEL,
            buildConnectingNotification(profile)
        )

        serviceScope.launch {
            val result = tunnelManager.connect(profile)
            if (result.isSuccess) {
                val port = result.getOrThrow()
                if (profile.autoOpenBrowser) {
                    openBrowser(this@SshTunnelService, "http://127.0.0.1:$port")
                }
            }
            onResult(result)
        }
    }

    fun disconnectTunnel() {
        tunnelManager.disconnect()
    }

    private fun buildConnectingNotification(profile: ConnectionProfile): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CloudSshApp.CHANNEL_ID_TUNNEL)
            .setContentTitle("CloudSSH Connecting...")
            .setContentText("${profile.host}:${profile.port} -> Local proxy :${profile.localPort}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(state: TunnelState.Connected) {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val browserPendingIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(Intent.ACTION_VIEW, Uri.parse(state.localUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, SshTunnelService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            2,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CloudSshApp.CHANNEL_ID_TUNNEL)
            .setContentTitle("CloudSSH Proxy Active: :${state.localPort}")
            .setContentText("Connected to ${state.host} | Forwarding remote :${state.remotePort}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, "Open Browser", browserPendingIntent)
            .addAction(0, "End Connection", disconnectPendingIntent)
            .build()

        startForeground(CloudSshApp.NOTIFICATION_ID_TUNNEL, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        tunnelManager.disconnect()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_DISCONNECT = "com.example.action.DISCONNECT"
        const val ACTION_OPEN_BROWSER = "com.example.action.OPEN_BROWSER"
        const val EXTRA_URL = "com.example.extra.URL"

        fun openBrowser(context: Context, url: String) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
