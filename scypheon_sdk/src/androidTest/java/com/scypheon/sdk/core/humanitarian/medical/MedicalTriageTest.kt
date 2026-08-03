package com.scypheon.sdk.core.humanitarian.medical

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.scypheon.sdk.core.system.AppDatabase
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class MedicalTriageTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PharmacopeiaDao
    private lateinit var triage: MedicalTriageGateway

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.pharmacopeiaDao()
        // Using MockK (already in project dependencies)
        val mockMemory = io.mockk.mockk<com.scypheon.sdk.core.memory.DualMemoryManager>(relaxed = true)
        triage = MedicalTriageGateway(dao, mockMemory)
        
        runBlocking {
            val (drugs, interactions, protocols) = MedicalSeeder.getPilotDataset()
            dao.insertFullDataset(
                drugs = drugs,
                interactions = interactions,
                firstAid = protocols,
                metadata = PharmacopeiaMetadata(0, "v4.0-Architect", "WHO", "hash", System.currentTimeMillis() + 86400000L, drugs.size)
            )
        }
    }

    @Test fun `bypasses LLM on critical interaction`() = runBlocking {
        val start = System.nanoTime()
        val result = triage.triage("headache advil bayer")
        val durationMs = (System.nanoTime() - start) / 1_000_000.0
        println("Triage Latency: ${durationMs}ms")
        assertTrue("Expected latency < 15ms, but got ${durationMs}ms", durationMs < 15.0) 
        assertTrue("Result is not CriticalInteraction: $result", result is TriageResult.CriticalInteraction)
        if (result is TriageResult.CriticalInteraction) {
            assertTrue(result.severity == Severity.MAJOR)
            assertTrue(result.effect.contains("pendarahan"))
        }
    }

    @Test fun `routes general query to LLM path`() = runBlocking {
        val result = triage.triage("mild headache paracetamol")
        assertTrue(result is TriageResult.General)
    }

    @Test fun `override engine generates deterministic alert`() {
        val triageResult = TriageResult.CriticalInteraction("WHO-101", "WHO-102", Severity.MAJOR, "Synergy", "Risiko pendarahan")
        val alert = SafetyOverrideEngine.generateCriticalAlert(triageResult)
        assertTrue(alert.contains("⛔ KRITICAL MEDICAL ALERT"))
        assertTrue(alert.contains("TINDAKAN SEGERA"))
    }

    @After fun teardown() { db.close() }
}
