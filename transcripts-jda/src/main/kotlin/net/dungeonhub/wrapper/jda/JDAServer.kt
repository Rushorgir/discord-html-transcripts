package net.dungeonhub.wrapper.jda

import kotlinx.coroutines.future.await
import net.dungeonhub.wrapper.DiscordServer
import net.dungeonhub.wrapper.NameCache
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction
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
            } catch (e: Exception) {
                null
            }
    }

    override suspend fun getRoleName(id: Long): String? = guild.getRoleById(id)?.name

    // threads that need API requests to be found are remembered too
    private val channelNames = NameCache()

    /**
     * Forgets all remembered names, so that the next lookups check JDA again.
     * Called when a new transcript generation starts on a reused channel.
     */
    fun clearCache() {
        memberNames.clear()
        channelNames.clear()
    }

    /**
     * Uses the cached channel if possible. Threads that aren't cached are looked up through the API:
     * first the active threads of the guild, then the archived threads of every thread container
     * (public, joined private and - if permitted - all private ones). Returns null if nothing was found
     * or the requests failed, e.g. because of missing permissions.
     */
    override suspend fun getChannelName(id: Long): String? =
        guild.getGuildChannelById(id)?.name ?: channelNames.getOrLookup(id) { retrieveThreadName(it) }

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

        action.forEachAsync { thread ->
            if (thread.idLong == id) {
                found = thread.name
                false
            } else {
                true
            }
        }.await()

        return found
    }

    // failed requests (e.g. missing permissions) count as "not found", but cancellation still propagates
    private inline fun <T> safely(block: () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
