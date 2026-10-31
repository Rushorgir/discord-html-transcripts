package net.dungeonhub.wrapper.kord

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Guild
import net.dungeonhub.wrapper.DiscordServer
import net.dungeonhub.wrapper.NameCache

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
        memberNames.getOrLookup(id) { guild.getMemberOrNull(Snowflake(it))?.effectiveName }
    override suspend fun getRoleName(id: Long): String? =
        roleNames.getOrLookup(id) { guild.getRoleOrNull(Snowflake(it))?.name }
    override suspend fun getChannelName(id: Long): String? =
        channelNames.getOrLookup(id) { guild.getChannelOrNull(Snowflake(it))?.name }
}