package com.scypheon.sdk.core.humanitarian.medical

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Entitas Utama: Menyimpan data medis absolut.
 */
@Entity(tableName = "pharmacopeia")
data class PharmacopeiaEntry(
    @PrimaryKey 
    @ColumnInfo(name = "id") val id: String,
    
    @ColumnInfo(name = "drug_name") val drugName: String,
    @ColumnInfo(name = "generic_name") val genericName: String? = null,
    @ColumnInfo(name = "dosage") val dosage: String,
    @ColumnInfo(name = "indications") val indications: String,
    @ColumnInfo(name = "contraindications") val contraindications: String,
    
    // Safety Envelopes (Digunakan oleh ClinicalDosageTool)
    @ColumnInfo(name = "max_mg_per_kg") val maxMgPerKg: Float? = null,
    @ColumnInfo(name = "max_daily_mg") val maxDailyMg: Double? = null,
    @ColumnInfo(name = "max_single_dose_mg") val maxSingleDoseMg: Double? = null,
    
    // Audit & Metadata
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long,
    @ColumnInfo(name = "is_high_risk") val isHighRisk: Boolean = false,
    @ColumnInfo(name = "risk_category") val riskCategory: String? = null,
    @ColumnInfo(name = "atc_code") val atcCode: String? = null,
    @ColumnInfo(name = "route") val route: String? = null,
    @ColumnInfo(name = "storage_conditions") val storageConditions: String? = null,
    @ColumnInfo(name = "pregnancy_category") val pregnancyCategory: String? = null
)

/**
 * Entitas FTS4 (Virtual Table): Mesin pencari Offline RAG Anda.
 * HANYA cantumkan kolom teks yang benar-benar akan dicari oleh AI untuk menghemat RAM.
 */
@Fts4(contentEntity = PharmacopeiaEntry::class)
@Entity(tableName = "pharmacopeia_fts")
data class PharmacopeiaFts(
    // Sinkronisasi implisit: Kita tidak mendeklarasikan PrimaryKey di sini.
    // Room akan menggunakan 'rowid' SQLite secara otomatis.
    
    @ColumnInfo(name = "drug_name") val drugName: String,
    @ColumnInfo(name = "generic_name") val genericName: String?,
    @ColumnInfo(name = "indications") val indications: String,
    @ColumnInfo(name = "contraindications") val contraindications: String
    
    // Dosage tidak dimasukkan ke FTS karena kita tidak mencari obat berdasarkan dosisnya.
    // Kita mencari obat berdasarkan Gejala (indications) atau Nama, lalu MENGAMBIL dosisnya dari tabel utama.
)
