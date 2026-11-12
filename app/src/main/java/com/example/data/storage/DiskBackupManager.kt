package com.example.data.storage

import android.content.Context
import android.util.Log
import com.example.data.db.ConnectionDao
import com.example.data.model.ConnectionProfile
import com.example.data.model.SshKeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Dual-layer persistent storage manager.
 * Saves SSH Keys and Profiles to private app internal storage on disk in addition
 * to Room database, ensuring that updates, migrations, or database cache clears
 * NEVER wipe out user SSH keys or saved connection profiles.
 */
class DiskBackupManager(private val context: Context) {

    private val vaultDir: File by lazy {
        context.filesDir.resolve("ssh_credentials_vault").apply {
            if (!exists()) mkdirs()
        }
    }

    private val keysDir: File by lazy {
        vaultDir.resolve("keys").apply {
            if (!exists()) mkdirs()
        }
    }

    private val profilesDir: File by lazy {
        vaultDir.resolve("profiles").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun backupKey(key: SshKeyPair) = withContext(Dispatchers.IO) {
        try {
            val keyFile = getKeyFile(key)
            val json = JSONObject().apply {
                put("title", key.title)
                put("publicKey", key.publicKey)
                put("privateKey", key.privateKey)
                put("keyType", key.keyType)
                put("fingerprint", key.fingerprint)
                put("createdAt", key.createdAt)
            }
            keyFile.writeText(json.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("DiskBackupManager", "Failed to backup key to disk: ${e.message}")
        }
    }

    suspend fun removeKeyBackup(key: SshKeyPair) = withContext(Dispatchers.IO) {
        try {
            val file = getKeyFile(key)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("DiskBackupManager", "Failed to delete key backup: ${e.message}")
        }
    }

    suspend fun backupProfile(profile: ConnectionProfile) = withContext(Dispatchers.IO) {
        try {
            val file = getProfileFile(profile)
            val json = JSONObject().apply {
                put("name", profile.name)
                put("rawCommand", profile.rawCommand)
                put("host", profile.host)
                put("port", profile.port)
                put("username", profile.username)
                put("authType", profile.authType)
                put("privateKey", profile.privateKey)
                put("passphrase", profile.passphrase)
                put("password", profile.password)
                put("remotePort", profile.remotePort)
                put("localPort", profile.localPort)
                put("autoOpenBrowser", profile.autoOpenBrowser)
                put("postConnectCommand", profile.postConnectCommand)
                put("lastConnectedAt", profile.lastConnectedAt)
                put("isFavorite", profile.isFavorite)
            }
            file.writeText(json.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("DiskBackupManager", "Failed to backup profile to disk: ${e.message}")
        }
    }

    suspend fun removeProfileBackup(profile: ConnectionProfile) = withContext(Dispatchers.IO) {
        try {
            val file = getProfileFile(profile)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("DiskBackupManager", "Failed to delete profile backup: ${e.message}")
        }
    }

    /**
     * Reconciles Room database with the persistent disk vault.
     * Restores any missing keys or profiles from the disk vault into Room,
     * and backs up any keys or profiles in Room to disk.
     */
    suspend fun syncWithDatabase(dao: ConnectionDao) = withContext(Dispatchers.IO) {
        try {
            val dbKeys = dao.getAllKeyPairsSync()
            val dbProfiles = dao.getAllProfilesSync()

            // 1. Back up all DB keys to disk
            for (key in dbKeys) {
                backupKey(key)
            }

            // 2. Back up all DB profiles to disk
            for (profile in dbProfiles) {
                backupProfile(profile)
            }

            // 3. Restore any keys present in disk vault that are missing in DB
            val diskKeyFiles = keysDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
            for (file in diskKeyFiles) {
                try {
                    val content = file.readText(Charsets.UTF_8)
                    val json = JSONObject(content)
                    val privKey = json.optString("privateKey")
                    val pubKey = json.optString("publicKey")
                    val title = json.optString("title")

                    val alreadyInDb = dbKeys.any {
                        it.privateKey.trim() == privKey.trim() ||
                                (it.publicKey.isNotBlank() && it.publicKey.trim() == pubKey.trim())
                    }

                    if (!alreadyInDb && privKey.isNotBlank()) {
                        val restoredKey = SshKeyPair(
                            title = title.ifBlank { "Restored Key" },
                            publicKey = pubKey,
                            privateKey = privKey,
                            keyType = json.optString("keyType", "RSA"),
                            fingerprint = json.optString("fingerprint", ""),
                            createdAt = json.optLong("createdAt", System.currentTimeMillis())
                        )
                        dao.insertKeyPair(restoredKey)
                        Log.i("DiskBackupManager", "Auto-restored SSH Key '${restoredKey.title}' from disk vault.")
                    }
                } catch (e: Exception) {
                    Log.w("DiskBackupManager", "Could not parse disk key file: ${file.name}")
                }
            }

            // 4. Restore any profiles present in disk vault that are missing in DB
            val diskProfileFiles = profilesDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
            for (file in diskProfileFiles) {
                try {
                    val content = file.readText(Charsets.UTF_8)
                    val json = JSONObject(content)
                    val name = json.optString("name")
                    val host = json.optString("host")

                    val alreadyInDb = dbProfiles.any { it.name == name && it.host == host }
                    if (!alreadyInDb && host.isNotBlank()) {
                        val restoredProfile = ConnectionProfile(
                            name = name,
                            rawCommand = json.optString("rawCommand"),
                            host = host,
                            port = json.optInt("port", 22),
                            username = json.optString("username", "root"),
                            authType = json.optString("authType", "KEY"),
                            privateKey = json.optString("privateKey"),
                            passphrase = json.optString("passphrase"),
                            password = json.optString("password"),
                            remotePort = json.optInt("remotePort", 7860),
                            localPort = json.optInt("localPort", 7860),
                            autoOpenBrowser = json.optBoolean("autoOpenBrowser", true),
                            postConnectCommand = json.optString("postConnectCommand"),
                            lastConnectedAt = json.optLong("lastConnectedAt", 0L),
                            isFavorite = json.optBoolean("isFavorite", false)
                        )
                        dao.insertProfile(restoredProfile)
                        Log.i("DiskBackupManager", "Auto-restored Profile '${restoredProfile.name}' from disk vault.")
                    }
                } catch (e: Exception) {
                    Log.w("DiskBackupManager", "Could not parse disk profile file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("DiskBackupManager", "Error during disk vault sync: ${e.message}")
        }
    }

    private fun getKeyFile(key: SshKeyPair): File {
        val safeName = key.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
        val hash = (key.publicKey.hashCode() + key.privateKey.take(50).hashCode()).toString().replace("-", "n")
        return keysDir.resolve("key_${safeName}_$hash.json")
    }

    private fun getProfileFile(profile: ConnectionProfile): File {
        val safeName = profile.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
        val hash = (profile.host.hashCode() + profile.port.hashCode()).toString().replace("-", "n")
        return profilesDir.resolve("prof_${safeName}_$hash.json")
    }
}
