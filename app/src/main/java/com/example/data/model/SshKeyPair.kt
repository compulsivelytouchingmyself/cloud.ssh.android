package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_key_pairs")
data class SshKeyPair(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val publicKey: String,
    val privateKey: String,
    val keyType: String = "ED25519", // ED25519, RSA
    val fingerprint: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
