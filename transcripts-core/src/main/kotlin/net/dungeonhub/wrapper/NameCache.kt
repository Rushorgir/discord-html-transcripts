package net.dungeonhub.wrapper

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Remembers successfully looked up names for a while, so that every id is only looked up once
 * per [ttlNanos], no matter how often it's mentioned.
 *
 * - Failed lookups (returning null) are not cached, so later mentions can retry.
 * - Entries expire, and expired entries are evicted when the cache grows, so a long-lived cache
 *   (e.g. a reused channel) neither returns stale names forever nor grows without limit.
 * - Concurrent lookups of the same id share one in-flight lookup instead of all starting their own.
 * - [clear] starts a new generation: lookups that were started before it can't put their results
 *   into the cache afterwards.
 *
 * @param ttlNanos how long a looked up name stays valid, in nanoseconds
 * @param maxEntries how many names are kept at most; expired names are evicted first, then everything
 * @param clock the time source in nanoseconds, replaceable for tests
 */
class NameCache(
    private val ttlNanos: Long = DEFAULT_TTL_NANOS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::nanoTime
) {
    private class Name(val value: String, val expiresAt: Long)

    private class InFlight(val result: CompletableDeferred<String?> = CompletableDeferred())

    private val names = ConcurrentHashMap<Long, Name>()
    private val inFlight = ConcurrentHashMap<Long, InFlight>()
    private val lock = Any()
    private var generation = 0L

    private fun isValid(name: Name) = clock() - name.expiresAt < 0

    /**
     * Returns the cached name for [id] if it's still valid, otherwise calls [lookup]
     * (or waits for the lookup of the same id that is already running).
     * Only non-null lookup results are cached.
     */
    suspend fun getOrLookup(id: Long, lookup: suspend (Long) -> String?): String? {
        while (true) {
            names[id]?.let { if (isValid(it)) return it.value }

            val own = InFlight()
            // registering the lookup and noting the generation happen together, so clear() can't run in between
            val (running, startedIn) = synchronized(lock) {
                inFlight.putIfAbsent(id, own) to generation
            }

            if (running == null) {
                return lookupAndStore(id, own, startedIn, lookup)
            }

            try {
                return running.result.await()
            } catch (_: CancellationException) {
                // either this coroutine was cancelled, or the one doing the lookup was: then try again ourselves
                currentCoroutineContext().ensureActive()
            } catch (_: Exception) {
                // the lookup we waited for failed, so try again ourselves
            }
        }
    }

    private suspend fun lookupAndStore(
        id: Long,
        own: InFlight,
        startedIn: Long,
        lookup: suspend (Long) -> String?
    ): String? {
        try {
            val name = lookup(id)

            synchronized(lock) {
                if (startedIn == generation) {
                    if (name != null) store(id, name) else names.remove(id)
                }
            }

            own.result.complete(name)
            return name
        } catch (e: Throwable) {
            own.result.completeExceptionally(e)
            throw e
        } finally {
            inFlight.remove(id, own)
        }
    }

    // must be called while holding the lock
    private fun store(id: Long, name: String) {
        if (names.size >= maxEntries) {
            names.values.removeIf { !isValid(it) }

            if (names.size >= maxEntries) {
                names.clear()
            }
        }

        names[id] = Name(name, clock() + ttlNanos)
    }

    /** Forgets all remembered names and ignores the results of lookups that are still running. */
    fun clear() {
        synchronized(lock) {
            generation++
            names.clear()
            inFlight.clear()
        }
    }

    private companion object {
        const val DEFAULT_TTL_NANOS = 5L * 60 * 1_000_000_000
        const val DEFAULT_MAX_ENTRIES = 10_000
    }
}
