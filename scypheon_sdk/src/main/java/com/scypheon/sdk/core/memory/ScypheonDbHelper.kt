package com.scypheon.sdk.core.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import timber.log.Timber

/**
 * Enterprise-grade ACID compliant SQLite Database for Scypheon SDK.
 * Version 5: Introduces Unified Memory Tiers and SHA-256 Deduplication support.
 */
class ScypheonDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 7
        private const val DATABASE_NAME = "ScypheonCore.db"

        // Legacy Tables
        const val TABLE_SESSIONS = "sessions"
        const val TABLE_MESSAGES = "messages"
        const val TABLE_PROFILE = "user_profile"

        // [v5.0] Unified Enterprise Memory
        const val TABLE_MEMORY_ENTRIES = "memory_entries"
        
        // [v6.0] Searchable Encryption Tokens
        const val TABLE_SEARCHABLE_TOKENS = "searchable_tokens"

        // Memory Status Constants
        const val STATUS_SUCCESS = 0
        const val STATUS_FAILED = 1
        const val STATUS_SYSTEM = 2
        const val STATUS_QUEUED = 3
        const val STATUS_PROCESSING = 4
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create Sessions Table
        db.execSQL("""
            CREATE TABLE $TABLE_SESSIONS (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                is_archived INTEGER DEFAULT 0
            )
        """)

        // Create Messages Table
        db.execSQL("""
            CREATE TABLE $TABLE_MESSAGES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                text TEXT NOT NULL,
                is_user INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                status INTEGER DEFAULT 0,
                is_context_eligible INTEGER DEFAULT 1,
                embedding BLOB,
                FOREIGN KEY(session_id) REFERENCES $TABLE_SESSIONS(id) ON DELETE CASCADE
            )
        """)

        // [v5.0] Unified Memory Entry Table with Deduplication Support
        db.execSQL("""
            CREATE TABLE $TABLE_MEMORY_ENTRIES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content_hash TEXT UNIQUE NOT NULL, -- SHA-256 hash for deduplication
                content TEXT NOT NULL,
                tier TEXT NOT NULL, -- WORKING, EPISODIC, SEMANTIC
                source_id TEXT, -- message_id or trace_id
                timestamp INTEGER NOT NULL,
                importance REAL DEFAULT 0.5,
                embedding BLOB,
                metadata TEXT -- JSON encoded extra data
            )
        """)

        // [v6.0] Searchable Encryption Tokens Table
        db.execSQL("""
            CREATE TABLE $TABLE_SEARCHABLE_TOKENS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                message_id INTEGER NOT NULL,
                token_hash TEXT NOT NULL,
                encrypted_token TEXT NOT NULL,
                position INTEGER NOT NULL,
                token_type TEXT NOT NULL,
                relevance_weight REAL DEFAULT 1.0,
                FOREIGN KEY (message_id) REFERENCES $TABLE_MESSAGES(id) ON DELETE CASCADE
            )
        """)
        
        db.execSQL("CREATE INDEX idx_searchable_tokens_hash ON $TABLE_SEARCHABLE_TOKENS(token_hash)")
        db.execSQL("CREATE INDEX idx_searchable_tokens_message ON $TABLE_SEARCHABLE_TOKENS(message_id)")

        // User Profile Table
        db.execSQL("""
            CREATE TABLE $TABLE_PROFILE (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """)

        db.execSQL("INSERT INTO $TABLE_PROFILE (key, value) VALUES ('allergies', 'None recorded')")
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            db.execSQL("PRAGMA foreign_keys=ON;")
            db.enableWriteAheadLogging()
            db.rawQuery("PRAGMA synchronous=NORMAL;", null).close()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Timber.w("Upgrading database from $oldVersion to $newVersion")
        
        if (oldVersion < 5) {
            // Upgrade to v5.0: Add Unified Memory Entries
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_MEMORY_ENTRIES (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    content_hash TEXT UNIQUE NOT NULL,
                    content TEXT NOT NULL,
                    tier TEXT NOT NULL,
                    source_id TEXT,
                    timestamp INTEGER NOT NULL,
                    importance REAL DEFAULT 0.5,
                    embedding BLOB,
                    metadata TEXT
                )
            """)
        }
        
        if (oldVersion < 6) {
            // Drop broken FTS infrastructure
            db.execSQL("DROP TRIGGER IF EXISTS messages_ai")
            db.execSQL("DROP TRIGGER IF EXISTS messages_ad")
            db.execSQL("DROP TRIGGER IF EXISTS messages_au")
            db.execSQL("DROP TABLE IF EXISTS ${TABLE_MESSAGES}_fts")
            
            // Create new searchable tokens table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_SEARCHABLE_TOKENS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    message_id INTEGER NOT NULL,
                    token_hash TEXT NOT NULL,
                    encrypted_token TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    token_type TEXT NOT NULL,
                    relevance_weight REAL DEFAULT 1.0,
                    FOREIGN KEY (message_id) REFERENCES $TABLE_MESSAGES(id) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_searchable_tokens_hash ON $TABLE_SEARCHABLE_TOKENS(token_hash)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_searchable_tokens_message ON $TABLE_SEARCHABLE_TOKENS(message_id)")
        }
        
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN is_archived INTEGER DEFAULT 0")
            } catch (e: Exception) {
                Timber.e(e, "Failed to alter sessions table for is_archived column")
            }
        }
    }
}
