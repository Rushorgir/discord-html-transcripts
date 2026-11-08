package net.dungeonhub.wrapper.kord

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Guild
import net.dungeonhub.wrapper.DiscordServer
import net.dungeonhub.wrapper.NameCache
import kotlin.coroutines.cancellation.CancellationException

/**
 * [DiscordServer] backed by a Kord [Guild].
 */
class KordServer(val guild: Guild): DiscordServer {
    override val name: String
        get() = guild.name
    override val icon: String?
        get() = guild.icon?.cdnUrl?.toUrl()

    // lookups can require a request each, so the results are remembered
    private val memberNames = NameCache()
    private val roleNames = NameCache()
    private val channelNames = NameCache()

    override suspend fun getMemberName(id: Long): String? =
        lookup { memberNames.getOrLookup(id) { guild.getMemberOrNull(Snowflake(it))?.effectiveName } }

    override suspend fun getRoleName(id: Long): String? =
        lookup { roleNames.getOrLookup(id) { guild.getRoleOrNull(Snowflake(it))?.name } }

    override suspend fun getChannelName(id: Long): String? =
        lookup { channelNames.getOrLookup(id) { guild.getChannelOrNull(Snowflake(it))?.name } }

    // a failed lookup falls back to the raw id (null) and isn't cached; cancellation still propagates
    private inline fun lookup(block: () -> String?): String? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
