package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ConnectionProfile
import com.example.data.model.SshKeyPair

@Database(
    entities = [ConnectionProfile::class, SshKeyPair::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration from Version 1 to Version 2:
         * Adds postConnectCommand column to connection_profiles without dropping any tables or keys.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure ssh_key_pairs table exists
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ssh_key_pairs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        publicKey TEXT NOT NULL,
                        privateKey TEXT NOT NULL,
                        keyType TEXT NOT NULL,
                        fingerprint TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // Safely add postConnectCommand column to connection_profiles if it doesn't already exist
                try {
                    db.execSQL("ALTER TABLE connection_profiles ADD COLUMN postConnectCommand TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    // Column already exists, safe to ignore
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cloud_ssh_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // NEVER call fallbackToDestructiveMigration() so updates never wipe user data!
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
