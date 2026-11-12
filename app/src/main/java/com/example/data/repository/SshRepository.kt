package com.example.data.repository

import com.example.data.db.ConnectionDao
import com.example.data.model.ConnectionProfile
import com.example.data.model.SshKeyPair
import kotlinx.coroutines.flow.Flow

class SshRepository(private val dao: ConnectionDao) {

    val allProfiles: Flow<List<ConnectionProfile>> = dao.getAllProfiles()
    val allKeyPairs: Flow<List<SshKeyPair>> = dao.getAllKeyPairs()

    suspend fun getProfile(id: Long): ConnectionProfile? = dao.getProfileById(id)

    suspend fun saveProfile(profile: ConnectionProfile): Long {
        return if (profile.id == 0L) {
            dao.insertProfile(profile)
        } else {
            dao.updateProfile(profile)
            profile.id
        }
    }

    suspend fun deleteProfile(profile: ConnectionProfile) = dao.deleteProfile(profile)

    suspend fun recordConnection(profileId: Long) {
        if (profileId > 0) {
            dao.updateLastConnected(profileId, System.currentTimeMillis())
        }
    }

    suspend fun saveKeyPair(keyPair: SshKeyPair): Long = dao.insertKeyPair(keyPair)

    suspend fun deleteKeyPair(keyPair: SshKeyPair) = dao.deleteKeyPair(keyPair)

    suspend fun getKeyPair(id: Long): SshKeyPair? = dao.getKeyPairById(id)
}
