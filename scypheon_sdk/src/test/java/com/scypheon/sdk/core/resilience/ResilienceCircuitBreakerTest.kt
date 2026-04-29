package com.scypheon.sdk.core.resilience

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Enterprise-Grade ResilienceCircuitBreaker Unit Tests
 * 
 * Coverage:
 * - State transitions (CLOSED → OPEN → HALF_OPEN → CLOSED)
 * - Failure threshold triggering
 * - Cooldown timing
 * - Recovery validation
 * - Statistics tracking
 */
class ResilienceCircuitBreakerTest {

    private lateinit var circuitBreaker: ResilienceCircuitBreaker

    @Before
    fun setUp() {
        circuitBreaker = ResilienceCircuitBreaker(
            ResilienceCircuitBreaker.Config(
                failureThreshold = 3,
                cooldownMs = 1000L, // 1 second for faster tests
                successThreshold = 2
            )
        )
    }

    // ==================== INITIAL STATE TESTS ====================

    @Test
    fun `test initial state is CLOSED`() {
        assertEquals(ResilienceCircuitBreaker.State.CLOSED, circuitBreaker.getState())
    }

    @Test
    fun `test allowRequest returns true in CLOSED state`() {
        assertTrue(circuitBreaker.allowRequest())
    }

    // ==================== FAILURE THRESHOLD TESTS ====================

    @Test
    fun `test state remains CLOSED after 1 failure`() {
        circuitBreaker.recordFailure()
        assertEquals(ResilienceCircuitBreaker.State.CLOSED, circuitBreaker.getState())
    }

    @Test
    fun `test state remains CLOSED after 2 failures`() {
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()
        assertEquals(ResilienceCircuitBreaker.State.CLOSED, circuitBreaker.getState())
    }

    @Test
    fun `test state transitions to OPEN after 3 failures`() {
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()
        
        assertEquals(ResilienceCircuitBreaker.State.OPEN, circuitBreaker.getState())
    }

    @Test
    fun `test request blocked in OPEN state`() {
        // Trip the circuit breaker
        repeat(3) { circuitBreaker.recordFailure() }
        
        assertFalse(circuitBreaker.allowRequest())
    }

    // ==================== COOLDOWN AND HALF_OPEN TESTS ====================

    @Test
    fun `test transition to HALF_OPEN after cooldown`() {
        // Trip the circuit breaker
        repeat(3) { circuitBreaker.recordFailure() }
        assertEquals(ResilienceCircuitBreaker.State.OPEN, circuitBreaker.getState())
        
        // Wait for cooldown (1 second)
        Thread.sleep(1100)
        
        // Should transition to HALF_OPEN on next request
        assertTrue(circuitBreaker.allowRequest())
        assertEquals(ResilienceCircuitBreaker.State.HALF_OPEN, circuitBreaker.getState())
    }

    @Test
    fun `test request still blocked before cooldown expires`() {
        // Trip the circuit breaker
        repeat(3) { circuitBreaker.recordFailure() }
        
        // Wait only half the cooldown
        Thread.sleep(500)
        
        assertFalse(circuitBreaker.allowRequest())
        assertEquals(ResilienceCircuitBreaker.State.OPEN, circuitBreaker.getState())
    }

    // ==================== RECOVERY TESTS ====================

    @Test
    fun `test recovery to CLOSED after 2 successes in HALF_OPEN`() {
        // Trip the circuit breaker
        repeat(3) { circuitBreaker.recordFailure() }
        
        // Wait for cooldown
        Thread.sleep(1100)
        
        // Transition to HALF_OPEN
        circuitBreaker.allowRequest()
        assertEquals(ResilienceCircuitBreaker.State.HALF_OPEN, circuitBreaker.getState())
        
        // First success
        circuitBreaker.recordSuccess()
        assertEquals(ResilienceCircuitBreaker.State.HALF_OPEN, circuitBreaker.getState())
        
        // Second success - should recover
        circuitBreaker.recordSuccess()
        assertEquals(ResilienceCircuitBreaker.State.CLOSED, circuitBreaker.getState())
    }

    @Test
    fun `test back to OPEN on failure in HALF_OPEN`() {
        // Trip the circuit breaker
        repeat(3) { circuitBreaker.recordFailure() }
        
        // Wait for cooldown
        Thread.sleep(1100)
        
        // Transition to HALF_OPEN
        circuitBreaker.allowRequest()
        assertEquals(ResilienceCircuitBreaker.State.HALF_OPEN, circuitBreaker.getState())
        
        // Failure in HALF_OPEN - back to OPEN
        circuitBreaker.recordFailure()
        assertEquals(ResilienceCircuitBreaker.State.OPEN, circuitBreaker.getState())
    }

    // ==================== SUCCESS RESETS FAILURE COUNT ====================

    @Test
    fun `test success resets failure count in CLOSED state`() {
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()
        
        val statusBefore = circuitBreaker.getStatus()
        assertEquals(2, statusBefore.failureCount)
        
        circuitBreaker.recordSuccess()
        
        val statusAfter = circuitBreaker.getStatus()
        assertEquals(0, statusAfter.failureCount)
    }

    // ==================== RESET FUNCTIONALITY ====================

    @Test
    fun `test reset returns to CLOSED state`() {
        // Trip the circuit breaker
        repeat(3) { circuitBreaker.recordFailure() }
        assertEquals(ResilienceCircuitBreaker.State.OPEN, circuitBreaker.getState())
        
        // Reset
        circuitBreaker.reset()
        
        assertEquals(ResilienceCircuitBreaker.State.CLOSED, circuitBreaker.getState())
        assertEquals(0, circuitBreaker.getStatus().failureCount)
    }

    // ==================== FORCE OPEN ====================

    @Test
    fun `test forceOpen immediately opens circuit`() {
        assertEquals(ResilienceCircuitBreaker.State.CLOSED, circuitBreaker.getState())
        
        circuitBreaker.forceOpen()
        
        assertEquals(ResilienceCircuitBreaker.State.OPEN, circuitBreaker.getState())
    }

    // ==================== STATUS REPORTING ====================

    @Test
    fun `test status reports correct information`() {
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()
        
        val status = circuitBreaker.getStatus()
        
        assertEquals(ResilienceCircuitBreaker.State.CLOSED, status.state)
        assertEquals(2, status.failureCount)
        assertEquals(0, status.successCount)
        assertTrue(status.totalRequests >= 0)
        assertEquals(2, status.totalFailures)
    }

    @Test
    fun `test cooldownRemaining is zero in CLOSED state`() {
        val status = circuitBreaker.getStatus()
        assertEquals(0, status.cooldownRemaining)
    }

    @Test
    fun `test cooldownRemaining positive in OPEN state`() {
        // Trip the circuit breaker
        repeat(3) { circuitBreaker.recordFailure() }
        
        val status = circuitBreaker.getStatus()
        assertTrue(status.cooldownRemaining > 0)
        assertTrue(status.cooldownRemaining <= 1000)
    }

    // ==================== STATISTICS TRACKING ====================

    @Test
    fun `test statistics accumulate correctly`() {
        // Some failures
        repeat(2) { circuitBreaker.recordFailure() }
        
        // Some successes
        repeat(3) { circuitBreaker.recordSuccess() }
        
        val status = circuitBreaker.getStatus()
        
        assertEquals(2, status.totalFailures)
        assertEquals(3, status.totalSuccesses)
        assertTrue(status.totalRequests >= 0)
    }

    // ==================== CIRCUIT BREAKER FACTORY ====================

    @Test
    fun `test factory creates named circuit breakers`() {
        val factory = ResilienceCircuitBreaker.CircuitBreakerFactory()
        
        val breaker1 = factory.getOrCreate("api", ResilienceCircuitBreaker.Config(failureThreshold = 5))
        val breaker2 = factory.getOrCreate("database", ResilienceCircuitBreaker.Config(failureThreshold = 10))
        
        assertNotSame(breaker1, breaker2)
        assertEquals(2, factory.getAll().size)
    }

    @Test
    fun `test factory returns same instance for same name`() {
        val factory = ResilienceCircuitBreaker.CircuitBreakerFactory()
        
        val breaker1 = factory.getOrCreate("test")
        val breaker2 = factory.getOrCreate("test")
        
        assertSame(breaker1, breaker2)
    }

    @Test
    fun `test factory resetAll`() {
        val factory = ResilienceCircuitBreaker.CircuitBreakerFactory()
        
        val breaker1 = factory.getOrCreate("a")
        val breaker2 = factory.getOrCreate("b")
        
        // Trip both
        repeat(3) { breaker1.recordFailure() }
        repeat(3) { breaker2.recordFailure() }
        
        assertEquals(ResilienceCircuitBreaker.State.OPEN, breaker1.getState())
        assertEquals(ResilienceCircuitBreaker.State.OPEN, breaker2.getState())
        
        // Reset all
        factory.resetAll()
        
        assertEquals(ResilienceCircuitBreaker.State.CLOSED, breaker1.getState())
        assertEquals(ResilienceCircuitBreaker.State.CLOSED, breaker2.getState())
    }
}
