package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ConnectionProfile
import com.example.data.model.SshKeyPair
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {

    // Profiles
    @Query("SELECT * FROM connection_profiles ORDER BY lastConnectedAt DESC, id DESC")
    fun getAllProfiles(): Flow<List<ConnectionProfile>>

    @Query("SELECT * FROM connection_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Long): ConnectionProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ConnectionProfile): Long

    @Update
    suspend fun updateProfile(profile: ConnectionProfile)

    @Delete
    suspend fun deleteProfile(profile: ConnectionProfile)

    @Query("SELECT * FROM connection_profiles")
    suspend fun getAllProfilesSync(): List<ConnectionProfile>

    @Query("UPDATE connection_profiles SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Long, timestamp: Long)

    // Key Pairs
    @Query("SELECT * FROM ssh_key_pairs ORDER BY createdAt DESC")
    fun getAllKeyPairs(): Flow<List<SshKeyPair>>

    @Query("SELECT * FROM ssh_key_pairs")
    suspend fun getAllKeyPairsSync(): List<SshKeyPair>

    @Query("SELECT * FROM ssh_key_pairs WHERE id = :id LIMIT 1")
    suspend fun getKeyPairById(id: Long): SshKeyPair?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyPair(keyPair: SshKeyPair): Long

    @Delete
    suspend fun deleteKeyPair(keyPair: SshKeyPair)
}
