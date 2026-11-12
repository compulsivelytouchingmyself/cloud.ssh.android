package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class CloudSshApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupSecurityProvider()
        createNotificationChannels()
    }

    private fun setupSecurityProvider() {
        try {
            // Remove any legacy BC provider on Android and insert modern BouncyCastle
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_TUNNEL,
                "Cloud SSH Tunnel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the SSH connection and browser port forwarding alive"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_TUNNEL = "cloud_ssh_tunnel_channel"
        const val NOTIFICATION_ID_TUNNEL = 1001
    }
}
