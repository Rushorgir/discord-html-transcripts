@file:JvmName("JDATranscripts")

package net.dungeonhub.wrapper.jda

import kotlinx.coroutines.runBlocking
import net.dungeonhub.transcripts.DiscordHtmlTranscripts
import net.dungeonhub.wrapper.DiscordChannel
import net.dungeonhub.wrapper.DiscordFramework
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import java.io.IOException

class JDAFramework(val jda: JDA): DiscordFramework

fun GuildMessageChannel.toTranscriptableChannel(): DiscordChannel<JDAFramework> {
    return JDAChannel(this)
}

/*
 * Blocking variants of the transcript functions, so they can be used from Java and without coroutines,
 * e.g. JDATranscripts.createTranscript(channel). This blocks the calling thread until the transcript is done,
 * so don't call these from JDA's callback threads (e.g. inside queue(...)).
 */

@Throws(IOException::class)
fun GuildMessageChannel.createTranscript(): String = runBlocking {
    DiscordHtmlTranscripts.createTranscript(toTranscriptableChannel())
}

@Throws(IOException::class)
fun GuildMessageChannel.generateTranscriptFromMessages(messages: List<Message>): String = runBlocking {
    val channel = JDAChannel(this@generateTranscriptFromMessages)
    // direct messages must not resolve mentions with this guild's nicknames
    val detachedChannel = JDAChannel(this@generateTranscriptFromMessages, DetachedJDAServer(guild))

    DiscordHtmlTranscripts.generateFromMessages(
        channel,
        messages.map { message ->
            when {
                !message.isFromGuild -> JDAMessage(message, detachedChannel)
                // messages from another guild use their own channel, so mentions are resolved against the right guild
                message.guild != guild -> JDAMessage(message)
                else -> JDAMessage(message, channel)
            }
        }
    )
}
