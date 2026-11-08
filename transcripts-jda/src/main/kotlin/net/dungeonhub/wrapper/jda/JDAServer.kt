package net.dungeonhub.wrapper.jda

import kotlinx.coroutines.future.await
import net.dungeonhub.wrapper.DiscordServer
import net.dungeonhub.wrapper.NameCache
import net.dv8tion.jda.api.entities.Guild
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

    /**
     * Uses the cached channel if possible. Threads that aren't cached (e.g. archived or not yet seen)
     * are looked up through the API; if that fails, null is returned.
     */
    override suspend fun getChannelName(id: Long): String? =
        guild.getGuildChannelById(id)?.name
            ?: try {
                guild.retrieveActiveThreads().submit().await().firstOrNull { it.idLong == id }?.name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
}
