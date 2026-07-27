package com.scypheon.sdk.core.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long
)

@Dao
interface ConversationDao {
    @Insert suspend fun insert(entity: ConversationEntity)
    @Query("SELECT content FROM conversations WHERE sessionId = :sessionId AND role != 'SYSTEM' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTurns(sessionId: String, limit: Int): List<String>
}

@Database(entities = [ConversationEntity::class], version = 1, exportSchema = false)
abstract class ConversationDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
}

@Singleton
class RoomConversationRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context
) : com.scypheon.sdk.core.agent.ooda.ConversationRepository {
    private val dao = Room.databaseBuilder(context, ConversationDatabase::class.java, "conversations.db")
        .fallbackToDestructiveMigration()
        .build()
        .conversationDao()

    override suspend fun getRecentTurns(sessionId: String, windowSize: Int): List<String> = withContext(Dispatchers.IO) {
        dao.getRecentTurns(sessionId, windowSize).reversed()
    }

    suspend fun saveTurn(sessionId: String, role: String, content: String) = withContext(Dispatchers.IO) {
        dao.insert(ConversationEntity(sessionId = sessionId, role = role, content = content, timestamp = System.currentTimeMillis()))
    }
}
