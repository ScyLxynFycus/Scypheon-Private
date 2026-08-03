package com.scypheon.sdk.core.humanitarian.medical

import androidx.room.*

@Dao
interface PharmacopeiaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PharmacopeiaEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PharmacopeiaEntry>)

    @Query("SELECT * FROM pharmacopeia WHERE drug_name = :name LIMIT 1")
    suspend fun getByDrugName(name: String): PharmacopeiaEntry?

    @Query("SELECT * FROM pharmacopeia WHERE id = :id LIMIT 1")
    suspend fun getDrugById(id: String): PharmacopeiaEntry?

    @Query("SELECT * FROM pharmacopeia WHERE generic_name = :genericName LIMIT 1")
    suspend fun getEntryByGenericName(genericName: String): PharmacopeiaEntry?

    // Ambil semua obat sekaligus hanya dengan 1 kali query!
    @Query("SELECT * FROM pharmacopeia WHERE LOWER(drug_name) IN (:tokens) OR LOWER(generic_name) IN (:tokens)")
    suspend fun getDrugsByTokens(tokens: List<String>): List<PharmacopeiaEntry>

    // 🚀 THE OFFLINE RAG WEAPON
    // Mencari indikasi medis dengan kecepatan cahaya (< 2ms)
    @Query("""
        SELECT main.* FROM pharmacopeia main
        JOIN pharmacopeia_fts fts ON main.rowid = fts.rowid
        WHERE pharmacopeia_fts MATCH :searchQuery
    """)
    suspend fun searchMedicalContext(searchQuery: String): List<PharmacopeiaEntry>

    @Query("""
        SELECT descriptionEn FROM drug_interactions 
        WHERE (drugAId = :a AND drugBId = :b) OR (drugAId = :b AND drugBId = :a) 
        LIMIT 1
    """)
    suspend fun getInteraction(a: String, b: String): String?

    @Query("SELECT * FROM first_aid_protocols WHERE conditionName MATCH :symptom LIMIT 1")
    suspend fun getFirstAidProtocol(symptom: String): FirstAidEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFirstAid(entry: FirstAidEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFirstAidAll(entries: List<FirstAidEntity>)

    @Query("SELECT * FROM first_aid_protocols")
    suspend fun getAllProtocols(): List<FirstAidEntity>

    @Query("""
        SELECT main.id FROM pharmacopeia main
        JOIN pharmacopeia_fts fts ON main.rowid = fts.rowid
        WHERE pharmacopeia_fts MATCH :query 
        LIMIT 10
    """)
    suspend fun resolveIds(query: String): List<String>

    @Query("SELECT * FROM pharmacopeia_metadata LIMIT 1")
    suspend fun getMetadata(): PharmacopeiaMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: PharmacopeiaMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteractions(interactions: List<InteractionEntity>)

    @Transaction
    suspend fun insertFullDataset(
        drugs: List<PharmacopeiaEntry>,
        interactions: List<InteractionEntity>,
        firstAid: List<FirstAidEntity>,
        metadata: PharmacopeiaMetadata
    ) {
        insertAll(drugs)
        insertInteractions(interactions)
        insertFirstAidAll(firstAid)
        insertMetadata(metadata)
    }

    @Query("SELECT * FROM medical_vectors WHERE sourceType = :sourceType")
    suspend fun getAllVectors(sourceType: String): List<MedicalVectorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVectors(vectors: List<MedicalVectorEntity>)
}
