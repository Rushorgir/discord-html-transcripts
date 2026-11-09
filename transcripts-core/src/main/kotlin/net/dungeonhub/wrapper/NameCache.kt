package net.dungeonhub.wrapper

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers successfully looked up names for a while, so that every id is only looked up once
 * per [ttlNanos], no matter how often it's mentioned.
 *
 * Failed lookups (returning null) are not cached, so later mentions can retry.
 * Entries expire so that a long-lived cache (e.g. a reused channel) doesn't return stale names forever.
 *
 * @param ttlNanos how long a looked up name stays valid, in nanoseconds
 */
class NameCache(private val ttlNanos: Long = DEFAULT_TTL_NANOS) {
    private val names = ConcurrentHashMap<Long, Name>()

    private data class Name(val value: String, val expiresAt: Long)

    /**
     * Returns the cached name for [id] if it's still valid, otherwise calls [lookup].
     * Only non-null lookup results are cached.
     */
    suspend fun getOrLookup(id: Long, lookup: suspend (Long) -> String?): String? {
        names[id]?.let { if (System.nanoTime() - it.expiresAt < 0) return it.value }

        val name = lookup(id)
        if (name != null) {
            names[id] = Name(name, System.nanoTime() + ttlNanos)
        } else {
            names.remove(id)
        }
        return name
    }

    /** Forgets all remembered names. */
    fun clear() {
        names.clear()
    }

    private companion object {
        const val DEFAULT_TTL_NANOS = 5L * 60 * 1_000_000_000
    }
}
