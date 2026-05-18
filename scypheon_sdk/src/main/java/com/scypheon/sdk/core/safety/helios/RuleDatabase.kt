package com.scypheon.sdk.core.safety.helios

import androidx.room.*
import androidx.room.Database
import android.content.Context

@Entity(tableName = "safety_rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val pattern: String,
    val weight: Double,
    val layer: Int, // 0: Sanitizer, 1: RuleEngine
    val useRegex: Boolean,
    val isIndonesian: Boolean = false,
    val description: String? = null
)

@Dao
interface RuleDao {
    @Query("SELECT * FROM safety_rules WHERE layer = :layer")
    suspend fun getRulesByLayer(layer: Int): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: RuleEntity)

    @Delete
    suspend fun deleteRule(rule: RuleEntity)

    @Query("SELECT COUNT(*) FROM safety_rules")
    suspend fun getCount(): Int
}

@Database(entities = [RuleEntity::class], version = 1, exportSchema = false)
abstract class RuleDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao

    companion object {
        @Volatile
        private var INSTANCE: RuleDatabase? = null

        fun getInstance(context: Context): RuleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RuleDatabase::class.java,
                    "helios_sentinel_rules.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
