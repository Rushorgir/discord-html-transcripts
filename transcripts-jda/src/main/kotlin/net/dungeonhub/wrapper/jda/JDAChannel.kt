package net.dungeonhub.wrapper.jda

import net.dungeonhub.wrapper.DiscordChannel
import net.dungeonhub.wrapper.DiscordMessage
import net.dungeonhub.wrapper.DiscordServer
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel

/**
 * @param serverOverride resolves mentions instead of the channel's own guild, e.g. for messages of another context
 */
class JDAChannel(
    val channel: GuildMessageChannel,
    private val serverOverride: DiscordServer? = null
): DiscordChannel<JDAFramework> {
    override val framework: JDAFramework
        get() = JDAFramework(channel.jda)
    override val name: String
        get() = channel.name
    // shared by all messages of this channel, so looked up names are only fetched once
    private val jdaServer by lazy { JDAServer(channel.guild) }
    override val server: DiscordServer
        get() = serverOverride ?: jdaServer
    // reading the messages starts a new transcript generation, so names are looked up fresh instead of stale
    override val messages: List<DiscordMessage>
        get() {
            jdaServer.clearCache()

            return channel.iterableHistory.stream()
                .map { JDAMessage(it, this) }
                .toList()
        }
}