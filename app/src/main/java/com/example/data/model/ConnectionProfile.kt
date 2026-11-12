package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_profiles")
data class ConnectionProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val rawCommand: String = "",
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val authType: String = "KEY", // KEY, PASSWORD
    val privateKey: String = "",
    val passphrase: String = "",
    val password: String = "",
    val remotePort: Int = 7860,
    val localPort: Int = 7860,
    val autoOpenBrowser: Boolean = true,
    val postConnectCommand: String = "",
    val lastConnectedAt: Long = 0L,
    val isFavorite: Boolean = false
)

enum class SshAuthType {
    KEY,
    PASSWORD
}
