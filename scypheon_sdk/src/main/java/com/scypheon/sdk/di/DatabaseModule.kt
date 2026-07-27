package com.scypheon.sdk.di

import android.content.Context
import java.io.File
import androidx.room.Room
import androidx.room.RoomDatabase
import com.scypheon.sdk.core.agent.AgentCheckpointDao
import com.scypheon.sdk.core.humanitarian.maps.MapTileDao
import com.scypheon.sdk.core.humanitarian.medical.PharmacopeiaDao
import com.scypheon.sdk.core.system.AppDatabase
import com.scypheon.sdk.core.telemetry.TelemetryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.scypheon.sdk.db.MedicalDatabase
import com.scypheon.sdk.db.MedicalQueries
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyManager: com.scypheon.sdk.core.security.DatabaseKeyManager
    ): AppDatabase {
        val isIsolated = try {
            android.os.Process.isIsolated()
        } catch (e: Exception) {
            false
        }

        val key = keyManager.getDatabaseKey()
        val factory = net.zetetic.database.sqlcipher.SupportOpenHelperFactory(key)

        if (isIsolated) {
            android.util.Log.i("ScypheonDB", "Isolated process detected. Providing encrypted in-memory fallback database.")
            return Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            )
            .openHelperFactory(factory)
            .build()
        }

        val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
        return try {
            val dbName = "scypheon_secure.db"
            val dbFile = context.getDatabasePath(dbName)
            val key = keyManager.getDatabaseKey()
            
            val factory = net.zetetic.database.sqlcipher.SupportOpenHelperFactory(key)
            
            val builder = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                dbName
            )
            .openHelperFactory(factory)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration()

            try {
                builder.build()
            } catch (e: Exception) {
                if (e.message?.contains("file is not a database") == true || e.message?.contains("code 26") == true) {
                    android.util.Log.e("ScypheonDB", "Database corruption detected during build. Resetting...", e)
                    if (dbFile.exists()) {
                        dbFile.delete()
                        File(dbFile.path + "-shm").delete()
                        File(dbFile.path + "-wal").delete()
                    }
                    builder.build()
                } else {
                    throw e
                }
            }
        } finally {
            android.os.StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    @Provides
    fun provideAgentCheckpointDao(db: AppDatabase): AgentCheckpointDao = db.agentCheckpointDao()

    @Provides
    fun provideMapTileDao(db: AppDatabase): MapTileDao = db.mapTileDao()

    @Provides
    fun provideTelemetryDao(db: AppDatabase): TelemetryDao = db.telemetryDao()

    @Provides
    fun providePharmacopeiaDao(db: AppDatabase): PharmacopeiaDao = db.pharmacopeiaDao()

    @Provides
    fun provideKnowledgeDao(db: AppDatabase): com.scypheon.sdk.core.grounding.KnowledgeDao = db.knowledgeDao()

    @Provides
    fun provideAuditChainDao(db: AppDatabase): com.scypheon.sdk.core.security.AuditChainDao = db.auditChainDao()

    @Provides
    fun provideMeshDao(db: AppDatabase): com.scypheon.sdk.core.humanitarian.mesh.MeshDao = db.meshDao()

    @Provides
    fun provideGraphDao(db: AppDatabase): com.scypheon.sdk.core.intelligence.graph.GraphDao = db.graphDao()

    @Provides
    fun provideMemoryDao(db: AppDatabase): com.scypheon.sdk.core.memory.MemoryDao = db.memoryDao()

    @Provides
    @Singleton
    fun provideMedicalQueries(driver: SqlDriver): MedicalQueries {
        return MedicalDatabase(driver).medicalQueries
    }

    @Provides
    @Singleton
    fun provideSqlDriver(@ApplicationContext context: Context): SqlDriver {
        val isIsolated = try {
            android.os.Process.isIsolated()
        } catch (e: Exception) {
            false
        }

        if (isIsolated) {
            return app.cash.sqldelight.driver.android.AndroidSqliteDriver(
                MedicalDatabase.Schema,
                context,
                null // Passing null name creates an in-memory database
            )
        }

        return AndroidSqliteDriver(
            MedicalDatabase.Schema,
            context,
            "medical.db"
        )
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
