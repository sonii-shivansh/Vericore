package com.vericore.server

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RateLimiterTest {

    private lateinit var rateLimiter: RateLimiter

    @BeforeEach
    fun setup() {
        rateLimiter = RateLimiter(maxRequestsPerMinute = 5, maxRequestsPerHour = 20)
    }

    @Test
    fun `should allow requests within minute limit`() {
        val clientId = "test-client-1"

        // First 5 requests should be allowed
        repeat(5) {
            assertTrue(rateLimiter.checkLimit(clientId), "Request ${it + 1} should be allowed")
        }
    }

    @Test
    fun `should block requests exceeding minute limit`() {
        val clientId = "test-client-2"

        // First 5 requests allowed
        repeat(5) { rateLimiter.checkLimit(clientId) }

        // 6th request should be blocked
        assertFalse(rateLimiter.checkLimit(clientId), "6th request should be blocked")
    }

    @Test
    fun `should block requests exceeding hour limit`() {
        val clientId = "test-client-3"

        // Create a rate limiter with low hour limit for testing
        val limiter = RateLimiter(maxRequestsPerMinute = 100, maxRequestsPerHour = 10)

        // First 10 requests allowed
        repeat(10) { assertTrue(limiter.checkLimit(clientId)) }

        // 11th request should be blocked
        assertFalse(limiter.checkLimit(clientId))
    }

    @Test
    fun `should track different clients separately`() {
        val client1 = "client-1"
        val client2 = "client-2"

        // Client 1 uses all their requests
        repeat(5) { rateLimiter.checkLimit(client1) }
        assertFalse(rateLimiter.checkLimit(client1))

        // Client 2 should still have requests available
        assertTrue(rateLimiter.checkLimit(client2))
    }

    @Test
    fun `should return correct remaining requests`() {
        val clientId = "test-client-4"

        assertEquals(5, rateLimiter.getRemainingMinute(clientId))

        rateLimiter.checkLimit(clientId)
        assertEquals(4, rateLimiter.getRemainingMinute(clientId))

        rateLimiter.checkLimit(clientId)
        assertEquals(3, rateLimiter.getRemainingMinute(clientId))
    }

    @Test
    fun `should return seconds until reset when limit exceeded`() {
        val clientId = "test-client-5"

        // Use up all requests
        repeat(5) { rateLimiter.checkLimit(clientId) }

        // Try one more to trigger limit
        rateLimiter.checkLimit(clientId)

        val secondsUntilReset = rateLimiter.getSecondsUntilReset(clientId)
        assertTrue(
                secondsUntilReset > 0 && secondsUntilReset <= 60,
                "Seconds until reset should be between 1 and 60, got: $secondsUntilReset"
        )
    }

    @Test
    fun `should return hourly reset when hourly limit is exhausted before minute limit`() {
        val clientId = "hourly-reset-client"
        val limiter = RateLimiter(maxRequestsPerMinute = 100, maxRequestsPerHour = 2)

        repeat(2) { assertTrue(limiter.checkLimit(clientId)) }
        assertFalse(limiter.checkLimit(clientId))

        val secondsUntilReset = limiter.getSecondsUntilReset(clientId)
        val expectedHourlyReset = 3_600L - ((System.currentTimeMillis() / 1000L) % 3_600L)
        assertTrue(
            kotlin.math.abs(secondsUntilReset - expectedHourlyReset) <= 1L,
            "Hourly exhaustion should return the hourly reset (within one second of observation), expected $expectedHourlyReset, got: $secondsUntilReset"
        )
    }

    @Test
    fun `should return the longer reset when both windows are exhausted`() {
        val clientId = "both-windows-reset-client"
        val limiter = RateLimiter(maxRequestsPerMinute = 2, maxRequestsPerHour = 2)

        repeat(2) { assertTrue(limiter.checkLimit(clientId)) }
        assertFalse(limiter.checkLimit(clientId))

        val secondsUntilReset = limiter.getSecondsUntilReset(clientId)
        val expectedHourlyReset = 3_600L - ((System.currentTimeMillis() / 1000L) % 3_600L)
        assertTrue(
            kotlin.math.abs(secondsUntilReset - expectedHourlyReset) <= 1L,
            "When both windows are exhausted, Retry-After should reflect the longer hourly wait; expected $expectedHourlyReset, got: $secondsUntilReset"
        )
    }

    @Test
    fun `should be thread-safe with concurrent requests`() {
        val clientId = "concurrent-client"
        val threadCount = 10
        val requestsPerThread = 2
        val latch = CountDownLatch(threadCount)
        val results = mutableListOf<Boolean>()

        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) {
            executor.submit {
                try {
                    repeat(requestsPerThread) {
                        synchronized(results) { results.add(rateLimiter.checkLimit(clientId)) }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        // Total requests = 20, limit = 5, so exactly 5 should succeed
        val successCount = results.count { it }
        assertEquals(5, successCount, "Exactly 5 requests should succeed in concurrent scenario")
    }

    @Test
    fun `should clear all rate limit data`() {
        val clientId = "test-client-6"

        // Use up all requests
        repeat(5) { rateLimiter.checkLimit(clientId) }

        // Should be blocked
        assertFalse(rateLimiter.checkLimit(clientId))

        // Clear and try again
        rateLimiter.clear()
        assertTrue(rateLimiter.checkLimit(clientId), "Should allow requests after clear")
    }

    @Test
    fun `should handle cleanup without errors`() {
        // Create many clients to trigger cleanup
        repeat(100) { i -> rateLimiter.checkLimit("client-$i") }

        // This should trigger cleanup internally (1% chance per request)
        // Just verify no exceptions are thrown
        repeat(200) { i -> rateLimiter.checkLimit("cleanup-test-$i") }

        // If we get here without exceptions, cleanup works
        assertTrue(true)
    }
}
