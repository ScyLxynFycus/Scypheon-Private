package com.scypheon.sdk.core.humanitarian.medical

import com.scypheon.sdk.core.memory.DualMemoryManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MedicalTriageGatewayTest {

    private val dao = mockk<PharmacopeiaDao>()
    private val memoryManager = mockk<DualMemoryManager>()
    private val telemetry = mockk<com.scypheon.sdk.core.telemetry.TelemetryDao>(relaxed = true)
    private lateinit var gateway: MedicalTriageGateway

    @Before
    fun setup() {
        gateway = MedicalTriageGateway(dao, memoryManager, telemetry)
        // Default stubs for safety
        coEvery { dao.searchFirstAidProtocols(any()) } returns emptyList()
        coEvery { dao.resolveIds(any()) } returns emptyList()
        coEvery { dao.getDrugById(any()) } returns null
        coEvery { dao.getInteraction(any(), any()) } returns null
    }

    @Test
    fun `triage should detect emergency symptoms via FTS5 fallback`() = runBlocking {
        val input = "nyeri dada"
        val protocol = FirstAidEntity(1, "Cardiac Arrest", "Henti Jantung", 3, "CPR", "RJP", "[]", "W", "W", "nyeri dada", "WHO", "2026-05-05")
        
        coEvery { dao.searchFirstAidProtocols(any()) } returns listOf(protocol)
        
        val result = gateway.triage(input)
        
        assertTrue("Result should be Emergency", result is TriageResult.Emergency)
        assertEquals("Cardiac Arrest", (result as TriageResult.Emergency).firstAid?.conditionName)
    }

    @Test
    fun `triage should detect fatal interactions in multi-drug input`() = runBlocking {
        val drug1 = DrugEntity("WHO-104", "Morphine", "Morfin", "Opioid", "", "", "", 0f, 0f, 0f, 0f, 0f, "", "", "", "", 3, 0, "morfin", "", "")
        val drug2 = DrugEntity("WHO-1201", "Omeprazole", "Omeprazol", "PPI", "", "", "", 0f, 0f, 0f, 0f, 0f, "", "", "", "", 3, 0, "omeprazol", "", "")
        
        coEvery { dao.resolveIds("morfin") } returns listOf("WHO-104")
        coEvery { dao.resolveIds("omeprazol") } returns listOf("WHO-1201")
        coEvery { dao.getDrugById("WHO-104") } returns drug1
        coEvery { dao.getDrugById("WHO-1201") } returns drug2
        coEvery { dao.getInteraction("WHO-104", "WHO-1201") } returns InteractionEntity("WHO-104", "WHO-1201", "Fatal", Severity.FATAL, "R", "R")
        
        val result = gateway.triage("morfin dan omeprazol")
        
        assertTrue("Result should be CriticalInteraction", result is TriageResult.CriticalInteraction)
    }

    @Test
    fun `triage should validate dosages for multiple drugs`() = runBlocking {
        val drug = DrugEntity("WHO-101", "Paracetamol", "Parasetamol", "Analgesic", "", "", "", 500f, 1000f, 4f, 4000f, 50f, "", "", "", "", 3, 1, "paracetamol", "", "")
        
        coEvery { dao.resolveIds("paracetamol") } returns listOf("WHO-101")
        coEvery { dao.getDrugById("WHO-101") } returns drug
        
        val result = gateway.triage("paracetamol 5000mg")
        
        assertTrue("Result should be Warning due to daily max", result is TriageResult.Warning)
        assertTrue((result as TriageResult.Warning).reason.contains("MAX DAILY DOSE EXCEEDED"))
    }

    @Test
    fun `triage should return NoResults when no entities match`() = runBlocking {
        val result = gateway.triage("I have a weird feeling in my toe")
        assertTrue("Result should be NoResults", result is TriageResult.NoResults)
    }

    @Test
    fun `triage should support fuzzy search for minor typos`() = runBlocking {
        val drug = DrugEntity("WHO-101", "Paracetamol", "Parasetamol", "Analgesic", "", "", "", 500f, 1000f, 4f, 4000f, 50f, "", "", "", "", 3, 1, "paracetamol", "", "")
        
        coEvery { dao.resolveIds("parasetamool") } returns emptyList()
        coEvery { dao.resolveIds("parasetamol") } returns listOf("WHO-101")
        coEvery { dao.getDrugById("WHO-101") } returns drug
        
        val result = gateway.triage("parasetamool 500mg")
        
        assertTrue("Result should find the drug via fuzzy variant", result is TriageResult.General)
        assertTrue((result as TriageResult.General).detectedDrugIds.contains("WHO-101"))
    }

    @Test
    fun `validateDosage should reject dose exceeding maxDailyMg`() = runBlocking {
        val drug = DrugEntity("WHO-101", "Paracetamol", "Parasetamol", "Analgesic", "", "", "", 500f, 1000f, 4f, 4000f, 50f, "", "", "", "", 3, 1, "paracetamol", "", "")
        coEvery { dao.getDrugById("WHO-101") } returns drug
        
        val result = gateway.validateDosage("WHO-101", 5000f)
        
        assertTrue("Result should be Warning due to daily max", result is TriageResult.Warning)
        assertTrue((result as TriageResult.Warning).reason.contains("MAX DAILY DOSE EXCEEDED"))
    }

    @Test
    fun `auditFinalResponse should detect fatal interactions in LLM output`() = runBlocking {
        val drug1 = DrugEntity("WHO-111", "Warfarin", "Warfarin", "Anticoagulant", "", "", "", 0f, 0f, 0f, 0f, 0f, "", "", "", "", 3, 0, "warfarin", "", "")
        val drug2 = DrugEntity("WHO-103", "Aspirin", "Aspirin", "NSAID", "", "", "", 0f, 0f, 0f, 0f, 0f, "", "", "", "", 3, 0, "aspirin", "", "")
        
        coEvery { dao.resolveIds("warfarin") } returns listOf("WHO-111")
        coEvery { dao.resolveIds("aspirin") } returns listOf("WHO-103")
        coEvery { dao.getDrugById("WHO-111") } returns drug1
        coEvery { dao.getDrugById("WHO-103") } returns drug2
        coEvery { dao.getInteraction("WHO-111", "WHO-103") } returns InteractionEntity("WHO-111", "WHO-103", "Fatal", Severity.FATAL, "Severe bleeding", "Pendarahan hebat")
        
        val result = gateway.auditFinalResponse("trace-123", "Anda harus mengonsumsi Warfarin dan Aspirin bersamaan.")
        
        assertTrue("Result should be CriticalInteraction", result is TriageResult.CriticalInteraction)
        assertEquals("Warfarin", (result as TriageResult.CriticalInteraction).drugA)
        assertEquals("Aspirin", result.drugB)
    }
}
