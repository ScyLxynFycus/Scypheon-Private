package com.scypheon.sdk.core.agent.tool

import com.scypheon.sdk.core.medical.MedicalRepository
import com.scypheon.sdk.core.medical.MedicineRecord
import com.scypheon.sdk.core.telemetry.TelemetryDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClinicalValidatorTest {

    private lateinit var repository: MedicalRepository
    private lateinit var telemetry: TelemetryDao
    private lateinit var auditChain: com.scypheon.sdk.core.security.AuditChain
    private lateinit var validator: ClinicalValidator

    @Before
    fun setup() {
        repository = mockk()
        telemetry = mockk()
        auditChain = mockk(relaxed = true)
        coEvery { telemetry.insert(any()) } returns Unit
        validator = ClinicalValidator(repository, telemetry, auditChain)
    }

    @Test
    fun `harden corrects wrong dosage for a drug`() = runTest {
        val response = "Take Aspirin 1000mg for your headache."
        val traceId = "test-trace"
        
        val med = MedicineRecord(
            id = "asp", name = "Aspirin", category = "NSAID", 
            brands = listOf("Bayer"), laymanExplanation = "Pain", 
            dosageAdult = "500mg", dosageChild = null, symptoms = emptyList(),
            contraindications = "peptic ulcer, bleeding"
        )
        
        coEvery { repository.search("") } returns listOf(med)
        
        val hardened = validator.harden(response, "I have a headache", traceId)
        
        assertTrue(hardened.contains("[VERIFIED] 500mg"))
        assertTrue(hardened.contains("(was 1000mg)"))
    }

    @Test
    fun `harden flags sentence on alignment failure`() = runTest {
        // Two drugs but only one dosage
        val response = "Take Aspirin and Ibuprofen 500mg."
        val traceId = "test-trace"
        
        val med1 = MedicineRecord("asp", "Aspirin", "", emptyList(), "", "500mg", null, emptyList(), "")
        val med2 = MedicineRecord("ibu", "Ibuprofen", "", emptyList(), "", "400mg", null, emptyList(), "")
        
        coEvery { repository.search("") } returns listOf(med1, med2)
        
        val hardened = validator.harden(response, "Some input", traceId)
        
        assertTrue(hardened.contains("⚠️ [UNSAFE CLINICAL DATA - ALIGNMENT FAILURE]"))
    }

    @Test
    fun `harden ignores non-clinical sentences`() = runTest {
        val response = "The weather is nice today."
        val traceId = "test-trace"
        
        coEvery { repository.search("") } returns emptyList()
        
        val hardened = validator.harden(response, "Some input", traceId)
        
        assertEquals(response, hardened)
    }

    @Test
    fun `harden blocks response on contraindication match`() = runTest {
        val userQuery = "Saya ada sakit lambung atau maag"
        val response = "Anda bisa minum Ibuprofen 400mg."
        
        val med = MedicineRecord("ibu", "Ibuprofen", "", emptyList(), "", "400mg", null, emptyList(), "lambung, maag, peptic ulcer")
        coEvery { repository.search("") } returns listOf(med)
        
        val hardened = validator.harden(response, userQuery, "trace-id")
        
        assertTrue(hardened.contains("❌ OBAT TIDAK AMAN"))
        assertTrue(hardened.contains("lambung"))
    }
}
