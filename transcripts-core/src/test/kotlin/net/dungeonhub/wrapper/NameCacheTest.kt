package net.dungeonhub.wrapper

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NameCacheTest {
    @Test
    fun testEntriesExpireAfterTheTtl() = runBlocking {
        // a ttl of 0 means every entry is already expired on the next access
        val cache = NameCache(ttlNanos = 0)
        var lookups = 0
        val lookup: suspend (Long) -> String? = { lookups++; "name$lookups" }

        assertEquals("name1", cache.getOrLookup(1L, lookup))
        assertEquals("name2", cache.getOrLookup(1L, lookup))
        assertEquals(2, lookups)
    }

    @Test
    fun testEntriesAreKeptWithinTheTtl() = runBlocking {
        val cache = NameCache()
        var lookups = 0
        val lookup: suspend (Long) -> String? = { lookups++; "name$lookups" }

        assertEquals("name1", cache.getOrLookup(1L, lookup))
        assertEquals("name1", cache.getOrLookup(1L, lookup))
        assertEquals(1, lookups)
    }

    @Test
    fun testClearForgetsAllNames() = runBlocking {
        val cache = NameCache()
        var lookups = 0
        val lookup: suspend (Long) -> String? = { lookups++; "name$lookups" }

        assertEquals("name1", cache.getOrLookup(1L, lookup))
        cache.clear()
        assertEquals("name2", cache.getOrLookup(1L, lookup))
    }

    @Test
    fun testNameThatIsFoundLaterReplacesTheMissingResult() = runBlocking {
        val cache = NameCache()
        var available = false
        val lookup: suspend (Long) -> String? = { if (available) "found" else null }

        assertNull(cache.getOrLookup(1L, lookup))
        available = true
        assertEquals("found", cache.getOrLookup(1L, lookup))
    }

    @Test
    fun testFailingLookupIsNotCached() = runBlocking {
        val cache = NameCache()
        var fail = true
        val lookup: suspend (Long) -> String? = { if (fail) throw IllegalStateException("boom") else "ok" }

        runCatching { cache.getOrLookup(1L, lookup) }
        fail = false
        assertEquals("ok", cache.getOrLookup(1L, lookup))
    }

    @Test
    fun testPositiveTtlWithControlledClock() = runBlocking {
        var now = 0L
        val cache = NameCache(ttlNanos = 100, clock = { now })
        var lookups = 0
        val lookup: suspend (Long) -> String? = { lookups++; "name$lookups" }

        assertEquals("name1", cache.getOrLookup(1L, lookup))

        now = 99
        assertEquals("name1", cache.getOrLookup(1L, lookup))
        assertEquals(1, lookups)

        now = 100
        assertEquals("name2", cache.getOrLookup(1L, lookup))
        assertEquals(2, lookups)
    }

    @Test
    fun testExpiredEntriesAreEvictedBeforeValidOnes() = runBlocking {
        var now = 0L
        val cache = NameCache(ttlNanos = 10, maxEntries = 3, clock = { now })
        var lookups = 0
        val lookup: suspend (Long) -> String? = { lookups++; "name$it" }

        cache.getOrLookup(1L, lookup)
        cache.getOrLookup(2L, lookup)
        now = 20
        cache.getOrLookup(3L, lookup)
        // the cache is full now, so the expired ids 1 and 2 are dropped to make room
        cache.getOrLookup(4L, lookup)
        assertEquals(4, lookups)

        // 3 and 4 are still cached
        cache.getOrLookup(3L, lookup)
        cache.getOrLookup(4L, lookup)
        assertEquals(4, lookups)
    }

    @Test
    fun testCacheStaysBoundedWhenAllEntriesAreValid() = runBlocking {
        val cache = NameCache(maxEntries = 2)
        var lookups = 0
        val lookup: suspend (Long) -> String? = { lookups++; "name$it" }

        cache.getOrLookup(1L, lookup)
        cache.getOrLookup(2L, lookup)
        // full of valid entries, so everything is dropped to make room
        cache.getOrLookup(3L, lookup)
        assertEquals(3, lookups)

        cache.getOrLookup(1L, lookup)
        assertEquals(4, lookups)
    }

    @Test
    fun testConcurrentLookupsOfTheSameIdShareOneLookup() = runBlocking {
        val cache = NameCache()
        val gate = CompletableDeferred<Unit>()
        var lookups = 0
        val lookup: suspend (Long) -> String? = { lookups++; gate.await(); "shared" }

        val first = async { cache.getOrLookup(1L, lookup) }
        val second = async { cache.getOrLookup(1L, lookup) }
        yield()
        gate.complete(Unit)

        assertEquals("shared", first.await())
        assertEquals("shared", second.await())
        assertEquals(1, lookups)
    }

    @Test
    fun testWaiterRetriesWhenTheRunningLookupFails() = runBlocking {
        val cache = NameCache()
        val gate = CompletableDeferred<Unit>()
        var lookups = 0
        val lookup: suspend (Long) -> String? = {
            if (lookups++ == 0) {
                gate.await()
                throw IllegalStateException("boom")
            }
            "second try"
        }

        val first = async { runCatching { cache.getOrLookup(1L, lookup) } }
        val second = async { cache.getOrLookup(1L, lookup) }
        yield()
        gate.complete(Unit)

        assertEquals(true, first.await().isFailure)
        assertEquals("second try", second.await())
    }

    @Test
    fun testLookupStartedBeforeClearDoesNotPopulateTheNewGeneration() = runBlocking {
        val cache = NameCache()
        val gate = CompletableDeferred<Unit>()
        var lookups = 0
        val lookup: suspend (Long) -> String? = { lookups++; gate.await(); "old" }

        val running = async { cache.getOrLookup(1L, lookup) }
        yield()
        cache.clear()
        gate.complete(Unit)
        assertEquals("old", running.await())

        // the old result wasn't stored, so this looks the name up again
        assertEquals("new", cache.getOrLookup(1L) { lookups++; "new" })
        assertEquals(2, lookups)

        // lookups of the new generation are cached again
        assertEquals("new", cache.getOrLookup(1L) { lookups++; "unused" })
        assertEquals(2, lookups)
    }
}
