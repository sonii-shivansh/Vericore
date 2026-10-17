package com.vericore.server

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe rate limiter with per-minute and per-hour counters.
 * Uses a per-window key strategy, which is stable and easy to test.
 */
class RateLimiter(
    private val maxRequestsPerMinute: Int = 60,
    private val maxRequestsPerHour: Int = 1000
) {
    private val minuteCounters = ConcurrentHashMap<String, WindowCounter>()
    private val hourCounters = ConcurrentHashMap<String, WindowCounter>()

    data class WindowCounter(
        val counter: AtomicInteger = AtomicInteger(0),
        val startTime: Long = System.currentTimeMillis()
    )

    fun checkLimit(clientId: String): Boolean {
        val now = System.currentTimeMillis()
        val minuteKey = "$clientId:${now / 60_000}"
        val hourKey = "$clientId:${now / 3_600_000}"

        val minuteCounter = minuteCounters.computeIfAbsent(minuteKey) { WindowCounter() }
        val hourCounter = hourCounters.computeIfAbsent(hourKey) { WindowCounter() }

        if (minuteCounter.counter.incrementAndGet() > maxRequestsPerMinute) {
            minuteCounter.counter.decrementAndGet()
            return false
        }

        if (hourCounter.counter.incrementAndGet() > maxRequestsPerHour) {
            hourCounter.counter.decrementAndGet()
            minuteCounter.counter.decrementAndGet()
            return false
        }

        if (Math.random() < 0.01) {
            cleanup(now)
        }

        return true
    }

    fun getRemainingMinute(clientId: String): Int {
        val now = System.currentTimeMillis()
        val minuteKey = "$clientId:${now / 60_000}"
        val counter = minuteCounters[minuteKey]
        return if (counter != null) maxOf(0, maxRequestsPerMinute - counter.counter.get()) else maxRequestsPerMinute
    }

    fun getRemainingHour(clientId: String): Int {
        val now = System.currentTimeMillis()
        val hourKey = "$clientId:${now / 3_600_000}"
        val counter = hourCounters[hourKey]
        return if (counter != null) maxOf(0, maxRequestsPerHour - counter.counter.get()) else maxRequestsPerHour
    }

    /**
     * Returns the time until every exhausted rate-limit window has reset.
     * When both limits are exhausted, the longer wait is authoritative.
     */
    fun getSecondsUntilReset(clientId: String): Long {
        val now = System.currentTimeMillis()
        val minuteKey = "$clientId:${now / 60_000}"
        val hourKey = "$clientId:${now / 3_600_000}"
        val epochSeconds = now / 1000L

        val minuteCounter = minuteCounters[minuteKey]
        val minuteReset = if (minuteCounter != null && minuteCounter.counter.get() >= maxRequestsPerMinute) {
            60L - (epochSeconds % 60L)
        } else {
            0L
        }

        val hourCounter = hourCounters[hourKey]
        val hourReset = if (hourCounter != null && hourCounter.counter.get() >= maxRequestsPerHour) {
            3_600L - (epochSeconds % 3_600L)
        } else {
            0L
        }

        return maxOf(minuteReset, hourReset)
    }

    private fun cleanup(now: Long) {
        minuteCounters.entries.removeIf { (_, counter) -> now - counter.startTime > 60_000L }
        hourCounters.entries.removeIf { (_, counter) -> now - counter.startTime > 3_600_000L }
    }

    fun clear() {
        minuteCounters.clear()
        hourCounters.clear()
    }
}
