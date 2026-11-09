package net.dungeonhub.wrapper

import kotlinx.coroutines.runBlocking
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
}
