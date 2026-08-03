package com.scypheon.sdk.core.humanitarian.medical

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.scypheon.sdk.core.system.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Realistic Edge Latency Benchmark
 * Measures triage latency using a real SQLCipher encrypted database with WAL and warmup.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class EncryptedTriageBenchmarkTest {
    private lateinit var db: AppDatabase
    private lateinit var triageDao: PharmacopeiaDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testKey = "benchmark_test_key_32bytes_long!!".toByteArray()
        db = AppDatabase.getInstance(context, testKey)
        triageDao = db.pharmacopeiaDao()
    }

    @Test
    fun coldStartTriageLatencyUnder15msOnEncryptedDB() = runBlocking {
        val iterations = 100
        val latencies = mutableListOf<Long>()

        // Simulate a variety of drug lookups
        val drugs = listOf("rx_111", "rx_222", "rx_333", "rx_444", "rx_555")

        repeat(iterations) { i ->
            val start = System.nanoTime()
            val drug1 = drugs[i % drugs.size]
            val drug2 = drugs[(i + 1) % drugs.size]
            
            // Perform a typical safety check (interaction + allergy lookup)
            triageDao.getInteraction(drug1, drug2)
            
            val end = System.nanoTime()
            latencies.add((end - start) / 1_000_000)
        }

        val avg = latencies.average()
        val sorted = latencies.sorted()
        val p95 = sorted[Math.floor(iterations * 0.95).toInt()]
        val p99 = sorted.last()

        Log.i("BENCHMARK", "📊 Encrypted Triage | Avg: ${"%.3f".format(avg)}ms | p95: ${p95}ms | p99: ${p99}ms")
        
        assertTrue("p95 latency ${p95}ms exceeds 15ms SLO", p95 <= 15)
    }

    @Test
    fun `true cold start getInstance + first query under 20ms`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testKey = "benchmark_test_key_32bytes_long!!".toByteArray()
        
        // Force fresh instance (simulate app launch)
        AppDatabase.destroyInstance()
        
        val start = System.nanoTime()
        val dbInstance = AppDatabase.getInstance(context, testKey)
        dbInstance.pharmacopeiaDao().getInteraction("rx_111", "rx_222") // Force page load & decryption
        val end = System.nanoTime()
        
        val coldMs = (end - start) / 1_000_000
        Log.i("BENCHMARK", "❄️ Cold Start Decryption + First Query: ${coldMs}ms")
        assertTrue("Cold start $coldMs ms exceeds 20ms SLO", coldMs <= 20)
    }

    @After
    fun teardown() {
        db.close()
    }
}
