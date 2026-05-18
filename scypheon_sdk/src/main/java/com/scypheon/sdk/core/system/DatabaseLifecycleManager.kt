package com.scypheon.sdk.core.system

import android.content.Context
import androidx.room.Room
import com.scypheon.sdk.core.security.SignatureVerificationException
import com.scypheon.sdk.core.security.SignatureVerifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseLifecycleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signatureVerifier: SignatureVerifier
) {
    companion object {
        private const val DB_NAME = "pharmacopeia.db"
        private const val MANIFEST_NAME = "manifest.sig"
        private const val ASSETS_DIR = "db"
    }

    suspend fun initializeDatabase(): AppDatabase = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(DB_NAME)
        
        // 1. First run: Copy from assets
        if (!dbFile.exists()) {
            copyAssetsToInternal()
        }

        // 2. Integrity Check
        try {
            verifyIntegrity(dbFile)
            
            // 3. Mount Production Database
            return@withContext Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        } catch (e: SignatureVerificationException) {
            quarantineDatabase(dbFile)
            // 4. Fallback to a Safe-Mode (Empty or minimal) database to prevent crash
            return@withContext Room.databaseBuilder(context, AppDatabase::class.java, "safe_mode.db")
                .fallbackToDestructiveMigration()
                .build()
        }
    }

    private fun copyAssetsToInternal() {
        val dbDir = context.getDatabasePath(DB_NAME).parentFile
        if (!dbDir!!.exists()) dbDir.mkdirs()

        context.assets.open("$ASSETS_DIR/$DB_NAME").use { input ->
            File(dbDir, DB_NAME).outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        context.assets.open("$ASSETS_DIR/$MANIFEST_NAME").use { input ->
            File(dbDir, MANIFEST_NAME).outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun verifyIntegrity(dbFile: File) {
        val sigFile = File(dbFile.parentFile, MANIFEST_NAME)
        if (!sigFile.exists()) throw SignatureVerificationException("Missing signature manifest")
        
        FileInputStream(dbFile).use { dbIn ->
            FileInputStream(sigFile).use { sigIn ->
                signatureVerifier.verify(dbIn, sigIn)
            }
        }
    }

    private fun quarantineDatabase(dbFile: File) {
        val quarantineDir = File(context.filesDir, "quarantine")
        if (!quarantineDir.exists()) quarantineDir.mkdirs()
        
        val timestamp = System.currentTimeMillis()
        dbFile.renameTo(File(quarantineDir, "corrupt_db_$timestamp.bin"))
        File(dbFile.parentFile, MANIFEST_NAME).delete()
    }
}
