package net.dungeonhub.wrapper.jda

import net.dungeonhub.wrapper.DiscordServer
import net.dungeonhub.wrapper.NameCache
import net.dv8tion.jda.api.entities.Guild

class JDAServer(val guild: Guild): DiscordServer {
    override val name: String
        get() = guild.name
    override val icon: String?
        get() = guild.iconUrl

    // members that aren't cached by JDA need a request each, so the result is remembered
    private val memberNames = NameCache()

    override suspend fun getMemberName(id: Long): String? = memberNames.getOrLookup(id) {
        guild.getMemberById(id)?.effectiveName
            ?: runCatching { guild.retrieveMemberById(id).complete().effectiveName }.getOrNull()
    }
    override suspend fun getRoleName(id: Long): String? = guild.getRoleById(id)?.name
    override suspend fun getChannelName(id: Long): String? = guild.getGuildChannelById(id)?.name
}