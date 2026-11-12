package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ConnectionProfile
import com.example.data.model.SshKeyPair
import com.example.data.storage.DiskBackupManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultPersistenceTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var backupManager: DiskBackupManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.filesDir.resolve("ssh_credentials_vault").deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backupManager = DiskBackupManager(context)
    }

    @After
    fun teardown() {
        db.close()
        context.filesDir.resolve("ssh_credentials_vault").deleteRecursively()
    }

    @Test
    fun diskVaultRestoresKeysAndProfilesWhenDatabaseIsEmpty() = runBlocking {
        val dao = db.connectionDao()
        val key = SshKeyPair(
            title = "Vast Cloud RSA",
            publicKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAB test@cloudssh",
            privateKey = "-----BEGIN RSA PRIVATE KEY-----\nTESTKEYDATA\n-----END RSA PRIVATE KEY-----\n",
            keyType = "RSA",
            fingerprint = "SHA256:testfp"
        )
        val profile = ConnectionProfile(
            name = "Vast GPU",
            rawCommand = "ssh -p 40946 root@95.169.14.15 -L 8080:localhost:8080",
            host = "95.169.14.15",
            port = 40946,
            username = "root",
            remotePort = 7860,
            localPort = 7860,
            postConnectCommand = "cd /workspace && python launch.py"
        )

        val keyId = dao.insertKeyPair(key)
        val profId = dao.insertProfile(profile)
        backupManager.backupKey(key.copy(id = keyId))
        backupManager.backupProfile(profile.copy(id = profId))

        // Simulate fresh Room DB after an update while filesDir survives
        val freshDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val freshDao = freshDb.connectionDao()
            assertEquals(0, freshDao.getAllKeyPairsSync().size)
            assertEquals(0, freshDao.getAllProfilesSync().size)

            backupManager.syncWithDatabase(freshDao)

            val restoredKeys = freshDao.getAllKeyPairsSync()
            val restoredProfiles = freshDao.getAllProfilesSync()
            assertEquals(1, restoredKeys.size)
            assertEquals("Vast Cloud RSA", restoredKeys[0].title)
            assertEquals(1, restoredProfiles.size)
            assertEquals("95.169.14.15", restoredProfiles[0].host)
            assertEquals(40946, restoredProfiles[0].port)
            assertEquals("cd /workspace && python launch.py", restoredProfiles[0].postConnectCommand)
        } finally {
            freshDb.close()
        }
    }

    @Test
    fun exportAndImportJsonRoundTripSkipsDuplicates() = runBlocking {
        val dao = db.connectionDao()
        val key = SshKeyPair(
            title = "RunPod Key",
            publicKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAC roundtrip@cloudssh",
            privateKey = "-----BEGIN RSA PRIVATE KEY-----\nROUNDTRIPDATA\n-----END RSA PRIVATE KEY-----\n",
            keyType = "RSA",
            fingerprint = "SHA256:roundtrip"
        )
        dao.insertKeyPair(key)

        val json = backupManager.exportVaultToJson(dao)
        assertTrue(json.contains("RunPod Key"))

        // Import into empty DB
        val targetDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val targetDao = targetDb.connectionDao()
            val firstImport = backupManager.importVaultFromJson(json, targetDao)
            assertEquals(1, firstImport.restoredKeys)
            assertEquals(0, firstImport.restoredProfiles)

            // Re-import same JSON should skip duplicates
            val secondImport = backupManager.importVaultFromJson(json, targetDao)
            assertEquals(0, secondImport.restoredKeys)
            assertEquals(1, targetDao.getAllKeyPairsSync().size)
        } finally {
            targetDb.close()
        }
    }
}
