package com.example.data.repository

import android.content.Context
import com.example.data.db.ConnectionDao
import com.example.data.model.ConnectionProfile
import com.example.data.model.SshKeyPair
import com.example.data.storage.DiskBackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SshRepository(
    private val dao: ConnectionDao,
    context: Context
) {
    private val diskBackupManager = DiskBackupManager(context)

    val allProfiles: Flow<List<ConnectionProfile>> = dao.getAllProfiles()
    val allKeyPairs: Flow<List<SshKeyPair>> = dao.getAllKeyPairs()

    init {
        // Auto-reconcile on startup to restore any keys or profiles from persistent disk vault
        CoroutineScope(Dispatchers.IO).launch {
            reconcileBackups()
        }
    }

    suspend fun reconcileBackups() {
        diskBackupManager.syncWithDatabase(dao)
    }

    suspend fun getProfile(id: Long): ConnectionProfile? = dao.getProfileById(id)

    suspend fun saveProfile(profile: ConnectionProfile): Long {
        val id = if (profile.id == 0L) {
            dao.insertProfile(profile)
        } else {
            dao.updateProfile(profile)
            profile.id
        }
        val saved = profile.copy(id = id)
        diskBackupManager.backupProfile(saved)
        return id
    }

    suspend fun deleteProfile(profile: ConnectionProfile) {
        dao.deleteProfile(profile)
        diskBackupManager.removeProfileBackup(profile)
    }

    suspend fun recordConnection(profileId: Long) {
        if (profileId > 0) {
            dao.updateLastConnected(profileId, System.currentTimeMillis())
        }
    }

    suspend fun saveKeyPair(keyPair: SshKeyPair): Long {
        val id = dao.insertKeyPair(keyPair)
        val saved = keyPair.copy(id = id)
        diskBackupManager.backupKey(saved)
        return id
    }

    suspend fun deleteKeyPair(keyPair: SshKeyPair) {
        dao.deleteKeyPair(keyPair)
        diskBackupManager.removeKeyBackup(keyPair)
    }

    suspend fun getKeyPair(id: Long): SshKeyPair? = dao.getKeyPairById(id)
}
