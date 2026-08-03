package com.scypheon.sdk.core.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Enterprise-grade ACID compliant SQLite Database for Scypheon SDK.
 * Ensures data persistency and fast transactional throughput across threads.
 */
class ScypheonDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 4
        private const val DATABASE_NAME = "ScypheonCore.db"

        // Tables
        const val TABLE_SESSIONS = "sessions"
        const val TABLE_MESSAGES = "messages"
        const val TABLE_PROFILE = "user_profile"

        // Message Status Constants (Phoenix & Solaris Protocols)
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
                timestamp INTEGER NOT NULL
            )
        """)

        // Create Messages Table with Phoenix Protocol support
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

        // Enterprise Feature: BM25 Keyword Search Index (FTS4)
        db.execSQL("""
            CREATE VIRTUAL TABLE ${TABLE_MESSAGES}_fts USING fts4(
                content='$TABLE_MESSAGES',
                text
            )
        """)

        // Triggers to keep FTS index synced automatically with the main messages table
        db.execSQL("""
            CREATE TRIGGER messages_ai AFTER INSERT ON $TABLE_MESSAGES
            BEGIN
                INSERT INTO ${TABLE_MESSAGES}_fts(rowid, text) VALUES (new.id, new.text);
            END;
        """)

        db.execSQL("""
            CREATE TRIGGER messages_ad AFTER DELETE ON $TABLE_MESSAGES
            BEGIN
                INSERT INTO ${TABLE_MESSAGES}_fts(${TABLE_MESSAGES}_fts, rowid, text) VALUES ('delete', old.id, old.text);
            END;
        """)

        db.execSQL("""
            CREATE TRIGGER messages_au AFTER UPDATE OF text ON $TABLE_MESSAGES
            BEGIN
                UPDATE ${TABLE_MESSAGES}_fts SET text = new.text WHERE rowid = old.id;
            END;
        """)

        // Create User Profile Table (for Medical Allergies, preferences)
        db.execSQL("""
            CREATE TABLE $TABLE_PROFILE (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """)

        // Insert a default empty allergy profile so it can be updated later
        db.execSQL("INSERT INTO $TABLE_PROFILE (key, value) VALUES ('allergies', 'None recorded')")
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            // Enable WAL for concurrency and PRAGMA foreign_keys for integrity
            db.execSQL("PRAGMA foreign_keys=ON;")
            db.enableWriteAheadLogging()
            db.rawQuery("PRAGMA synchronous=NORMAL;", null).close()
            
            // FTS Recovery Protocol: Detect and rebuild corrupted FTS index without data loss
            db.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='${TABLE_MESSAGES}_fts'", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) == 0) {
                    db.beginTransaction()
                    try {
                        // Re-create the FTS table and triggers
                        db.execSQL("""
                            CREATE VIRTUAL TABLE IF NOT EXISTS ${TABLE_MESSAGES}_fts USING fts4(
                                content='$TABLE_MESSAGES',
                                text
                            )
                        """)
                        // Sync existing data to FTS
                        db.execSQL("INSERT INTO ${TABLE_MESSAGES}_fts(rowid, text) SELECT id, text FROM $TABLE_MESSAGES")
                        db.setTransactionSuccessful()
                    } finally { db.endTransaction() }
                }
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Log the upgrade for diagnostics
        android.util.Log.w("ScypheonDb", "Upgrading database from version $oldVersion to $newVersion")
        
        if (oldVersion < 4) {
            // Fix: Trigger update to prevent SQL logic error on BLOB update
            db.execSQL("DROP TRIGGER IF EXISTS messages_au")
            db.execSQL("""
                CREATE TRIGGER messages_au AFTER UPDATE OF text ON $TABLE_MESSAGES
                BEGIN
                    UPDATE ${TABLE_MESSAGES}_fts SET text = new.text WHERE rowid = old.id;
                END;
            """)
        }
    }
}
