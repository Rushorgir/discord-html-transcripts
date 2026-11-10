package net.dungeonhub.wrapper.kord

import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.dungeonhub.wrapper.DiscordChannel
import net.dungeonhub.wrapper.DiscordMessage
import net.dungeonhub.wrapper.DiscordServer

class KordChannel(val channel: GuildMessageChannel) : DiscordChannel<KordFramework> {
    override val framework: KordFramework
        get() = KordFramework(channel.kord)
    override val name: String
        get() = channel.name
    // cached, since every message accesses the server while being transcribed
    private val kordServer by lazy { runBlocking { KordServer(channel.getGuild()) } }
    override val server: DiscordServer
        get() = kordServer
    // a new transcript generation must look names up fresh instead of reusing stale ones
    override fun startGeneration() = kordServer.clearCache()

    override val messages: List<DiscordMessage>
        get() = runBlocking {
            channel.withStrategy(EntitySupplyStrategy.rest).messages.map { KordMessage(it, this@KordChannel) }.toList()
        }
}