package net.dungeonhub.wrapper.jda

import kotlinx.coroutines.future.await
import net.dungeonhub.wrapper.DiscordServer
import net.dungeonhub.wrapper.NameCache
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * [DiscordServer] backed by a JDA [Guild].
 */
class JDAServer(val guild: Guild): DiscordServer {
    override val name: String
        get() = guild.name
    override val icon: String?
        get() = guild.iconUrl

    // members that aren't cached by JDA need a request each, so the result is remembered (for a limited time)
    private val memberNames = NameCache()

    override suspend fun getMemberName(id: Long): String? = memberNames.getOrLookup(id) {
        guild.getMemberById(id)?.effectiveName
            ?: try {
                guild.retrieveMemberById(id).submit().await().effectiveName
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
    }

    override suspend fun getRoleName(id: Long): String? = guild.getRoleById(id)?.name

    // threads that need API requests to be found are remembered too
    private val channelNames = NameCache()

    // ids that weren't found, so repeated mentions don't scan the API again during the same transcript generation
    private val missingChannels = ConcurrentHashMap.newKeySet<Long>()

    /**
     * Forgets all remembered names, so that the next lookups check JDA again.
     * Called when a new transcript generation starts on a reused channel.
     */
    fun clearCache() {
        memberNames.clear()
        channelNames.clear()
        missingChannels.clear()
    }

    /**
     * Uses the cached channel if possible. Threads that aren't cached are looked up through the API:
     * first the active threads of the guild, then the archived threads of every thread container
     * (public, joined private and - if permitted - all private ones). Returns null if nothing was found
     * or the requests failed, e.g. because of missing permissions. At most [MAX_THREADS_PER_SOURCE] archived
     * threads are checked per source, and ids that weren't found are not searched again until [clearCache].
     */
    override suspend fun getChannelName(id: Long): String? {
        guild.getGuildChannelById(id)?.name?.let { return it }

        if (id in missingChannels) {
            return null
        }

        val name = channelNames.getOrLookup(id) { retrieveThreadName(it) }

        if (name == null) {
            missingChannels.add(id)
        }

        return name
    }

    private suspend fun retrieveThreadName(id: Long): String? {
        safely {
            guild.retrieveActiveThreads().submit().await().firstOrNull { it.idLong == id }?.name
        }?.let { return it }

        val containers: List<IThreadContainer> =
            guild.textChannels + guild.newsChannels + guild.forumChannels + guild.mediaChannels

        for (container in containers) {
            val actions = listOf(
                container::retrieveArchivedPublicThreadChannels,
                container::retrieveArchivedPrivateJoinedThreadChannels,
                container::retrieveArchivedPrivateThreadChannels
            )

            for (action in actions) {
                safely { findThreadName(action(), id) }?.let { return it }
            }
        }

        return null
    }

    // pages through the threads until the one with the given id is found
    private suspend fun findThreadName(action: ThreadChannelPaginationAction, id: Long): String? {
        var found: String? = null
        var checked = 0

        action.forEachAsync { thread ->
            if (thread.idLong == id) {
                found = thread.name
                false
            } else {
                // stop paging once the limit is reached, large guilds can have thousands of archived threads
                ++checked < MAX_THREADS_PER_SOURCE
            }
        }.await()

        return found
    }

    // failed requests (e.g. missing permissions) count as "not found", but cancellation still propagates
    private inline fun <T> safely(block: () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MAX_THREADS_PER_SOURCE = 200
    }
}

/**
 * Server context for messages that don't belong to a guild (e.g. direct messages): it shows the
 * guild's name and icon, but doesn't resolve any mentions, so no guild nicknames, roles or channels leak in.
 */
internal class DetachedJDAServer(private val guild: Guild): DiscordServer {
    override val name: String
        get() = guild.name
    override val icon: String?
        get() = guild.iconUrl

    override suspend fun getMemberName(id: Long): String? = null
    override suspend fun getRoleName(id: Long): String? = null
    override suspend fun getChannelName(id: Long): String? = null
}
