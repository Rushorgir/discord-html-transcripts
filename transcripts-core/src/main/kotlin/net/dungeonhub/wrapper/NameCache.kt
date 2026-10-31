package net.dungeonhub.wrapper

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers looked up names, including the ones that couldn't be found,
 * so that every id is only looked up once, no matter how often it's mentioned.
 */
class NameCache {
    private val names = ConcurrentHashMap<Long, Name>()

    // ConcurrentHashMap can't store null, so a missing name is stored as Name(null)
    private data class Name(val value: String?)

    suspend fun getOrLookup(id: Long, lookup: suspend (Long) -> String?): String? {
        names[id]?.let { return it.value }

        val name = lookup(id)
        names[id] = Name(name)
        return name
    }
}
