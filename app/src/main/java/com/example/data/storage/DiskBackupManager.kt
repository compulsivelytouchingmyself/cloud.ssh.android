package com.example.data.storage

import android.content.Context
import android.util.Log
import com.example.data.db.ConnectionDao
import com.example.data.model.ConnectionProfile
import com.example.data.model.SshKeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VaultImportResult(
    val restoredKeys: Int,
    val restoredProfiles: Int
)

/**
 * Dual-layer persistent storage manager.
 * Saves SSH Keys and Profiles to private app internal storage on disk in addition
 * to Room database, ensuring that updates, migrations, or database cache clears
 * NEVER wipe out user SSH keys or saved connection profiles.
 * Also provides local JSON export/import for user-controlled backups without cloud dependencies.
 */
class DiskBackupManager(private val context: Context) {

    private val syncMutex = Mutex()

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
        syncMutex.withLock {
            writeKeyFile(key)
        }
    }

    private fun writeKeyFile(key: SshKeyPair) {
        try {
            val keyFile = getKeyFile(key)
            val json = keyToJson(key)
            keyFile.writeText(json.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("DiskBackupManager", "Failed to backup key to disk: ${e.message}")
        }
    }

    suspend fun removeKeyBackup(key: SshKeyPair) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                val file = getKeyFile(key)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("DiskBackupManager", "Failed to delete key backup: ${e.message}")
            }
        }
    }

    suspend fun backupProfile(profile: ConnectionProfile) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            writeProfileFile(profile)
        }
    }

    private fun writeProfileFile(profile: ConnectionProfile) {
        try {
            val file = getProfileFile(profile)
            val json = profileToJson(profile)
            file.writeText(json.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("DiskBackupManager", "Failed to backup profile to disk: ${e.message}")
        }
    }

    suspend fun removeProfileBackup(profile: ConnectionProfile) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                val file = getProfileFile(profile)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("DiskBackupManager", "Failed to delete profile backup: ${e.message}")
            }
        }
    }

    /**
     * Reconciles Room database with the persistent disk vault.
     * Restores any missing keys or profiles from the disk vault into Room,
     * and backs up any keys or profiles in Room to disk.
     */
    suspend fun syncWithDatabase(dao: ConnectionDao) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                val dbKeys = dao.getAllKeyPairsSync()
                val dbProfiles = dao.getAllProfilesSync()

                // 1. Back up all DB keys to disk
                for (key in dbKeys) {
                    writeKeyFile(key)
                }

                // 2. Back up all DB profiles to disk
                for (profile in dbProfiles) {
                    writeProfileFile(profile)
                }

                // 3. Restore any keys present in disk vault that are missing in DB
                val diskKeyFiles = keysDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
                for (file in diskKeyFiles) {
                    try {
                        val content = file.readText(Charsets.UTF_8)
                        val json = JSONObject(content)
                        val restoredKey = jsonToKeyPair(json) ?: continue

                        val alreadyInDb = dbKeys.any {
                            it.privateKey.trim() == restoredKey.privateKey.trim() ||
                                (it.publicKey.isNotBlank() && it.publicKey.trim() == restoredKey.publicKey.trim())
                        }

                        if (!alreadyInDb) {
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
                        val restoredProfile = jsonToProfile(json) ?: continue

                        val alreadyInDb = dbProfiles.any {
                            it.name == restoredProfile.name && it.host == restoredProfile.host && it.port == restoredProfile.port
                        }
                        if (!alreadyInDb) {
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
    }

    /**
     * Exports all keys and profiles into a portable JSON backup string.
     */
    suspend fun exportVaultToJson(dao: ConnectionDao): String = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val dbKeys = dao.getAllKeyPairsSync()
            val dbProfiles = dao.getAllProfilesSync()

            val keysArray = JSONArray()
            dbKeys.forEach { keysArray.put(keyToJson(it)) }

            val profilesArray = JSONArray()
            dbProfiles.forEach { profilesArray.put(profileToJson(it)) }

            JSONObject().apply {
                put("schemaVersion", 1)
                put("exportedAt", System.currentTimeMillis())
                put("keys", keysArray)
                put("profiles", profilesArray)
            }.toString(2)
        }
    }

    /**
     * Imports keys and profiles from a JSON backup string into both Room and the disk vault,
     * skipping duplicates already present.
     */
    suspend fun importVaultFromJson(jsonContent: String, dao: ConnectionDao): VaultImportResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val root = JSONObject(jsonContent)
            val existingKeys = dao.getAllKeyPairsSync().toMutableList()
            val existingProfiles = dao.getAllProfilesSync().toMutableList()

            var restoredKeysCount = 0
            var restoredProfilesCount = 0

            val keysArray = root.optJSONArray("keys") ?: JSONArray()
            for (i in 0 until keysArray.length()) {
                val obj = keysArray.optJSONObject(i) ?: continue
                val key = jsonToKeyPair(obj) ?: continue
                val duplicate = existingKeys.any {
                    it.privateKey.trim() == key.privateKey.trim() ||
                        (it.publicKey.isNotBlank() && it.publicKey.trim() == key.publicKey.trim())
                }
                if (!duplicate) {
                    val insertedId = dao.insertKeyPair(key)
                    val saved = key.copy(id = insertedId)
                    existingKeys.add(saved)
                    writeKeyFile(saved)
                    restoredKeysCount++
                }
            }

            val profilesArray = root.optJSONArray("profiles") ?: JSONArray()
            for (i in 0 until profilesArray.length()) {
                val obj = profilesArray.optJSONObject(i) ?: continue
                val profile = jsonToProfile(obj) ?: continue
                val duplicate = existingProfiles.any {
                    it.name == profile.name && it.host == profile.host && it.port == profile.port
                }
                if (!duplicate) {
                    val insertedId = dao.insertProfile(profile)
                    val saved = profile.copy(id = insertedId)
                    existingProfiles.add(saved)
                    writeProfileFile(saved)
                    restoredProfilesCount++
                }
            }

            VaultImportResult(restoredKeysCount, restoredProfilesCount)
        }
    }

    private fun keyToJson(key: SshKeyPair): JSONObject = JSONObject().apply {
        put("title", key.title)
        put("publicKey", key.publicKey)
        put("privateKey", key.privateKey)
        put("keyType", key.keyType)
        put("fingerprint", key.fingerprint)
        put("createdAt", key.createdAt)
    }

    private fun jsonToKeyPair(json: JSONObject): SshKeyPair? {
        val privKey = json.optString("privateKey")
        if (privKey.isBlank()) return null
        return SshKeyPair(
            title = json.optString("title").ifBlank { "Restored Key" },
            publicKey = json.optString("publicKey"),
            privateKey = privKey,
            keyType = json.optString("keyType", "RSA"),
            fingerprint = json.optString("fingerprint", ""),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private fun profileToJson(profile: ConnectionProfile): JSONObject = JSONObject().apply {
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

    private fun jsonToProfile(json: JSONObject): ConnectionProfile? {
        val host = json.optString("host")
        if (host.isBlank()) return null
        return ConnectionProfile(
            name = json.optString("name").ifBlank { "Cloud-$host" },
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
