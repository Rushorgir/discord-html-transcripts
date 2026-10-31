package net.dungeonhub.transcripts

import net.dungeonhub.wrapper.*
import java.awt.Color
import java.time.Instant

object TestFramework : DiscordFramework

data class TestServer(override val name: String, override val icon: String? = null) : DiscordServer

// messages are created before the channel they're transcribed with, so they reference this one by default
val defaultTestChannel = TestChannel("default", TestServer("Default Server"), emptyList())

data class TestChannel(
    override val name: String,
    override val server: DiscordServer,
    override val messages: List<DiscordMessage>
) : DiscordChannel<TestFramework> {
    override val framework = TestFramework
}

data class TestAuthor(
    override val id: Long,
    override val name: String,
    override val displayName: String = name,
    override val roleColor: Color = Color.WHITE,
    override val avatar: String = "https://example.com/avatar.png",
    override val isBot: Boolean = false
) : DiscordMessageAuthor

data class TestApplication(
    override val id: Long,
    override val name: String,
    override val displayName: String = name,
    override val roleColor: Color = Color.WHITE,
    override val avatar: String = "https://example.com/application.png"
) : DiscordApplication

data class TestInteraction(
    override val id: Long,
    override val name: String,
    override val user: DiscordMessageAuthor,
    override val application: DiscordApplication? = null
) : DiscordInteraction

data class TestMessage(
    override val id: Long,
    override val content: String,
    override val creationTime: Instant,
    override val author: DiscordMessageAuthor? = null,
    override val embeds: List<DiscordMessageEmbed?> = emptyList(),
    override val attachments: List<DiscordMessageAttachment> = emptyList(),
    override val reference: DiscordMessage? = null,
    override val interaction: DiscordInteraction? = null,
    override val channel: DiscordChannel<*> = defaultTestChannel
) : DiscordMessage

data class TestAttachment(
    override val fileName: String,
    override val isImage: Boolean,
    override val url: String,
    override val size: Long
) : DiscordMessageAttachment

data class TestEmbedField(
    override val name: String,
    override val value: String,
    override val isInline: Boolean
) : DiscordMessageEmbedField

data class TestEmbedAuthor(
    override val name: String?,
    override val iconUrl: String?,
    override val url: String?
) : DiscordMessageEmbedAuthor

data class TestEmbedFooter(
    override val text: String?,
    override val iconUrl: String?
) : DiscordMessageEmbedFooter

data class TestEmbed(
    override val color: Color? = null,
    override val title: String? = null,
    override val description: String? = null,
    override val fields: List<DiscordMessageEmbedField> = emptyList(),
    override val url: String? = null,
    override val thumbnail: String? = null,
    override val image: String? = null,
    override val footer: DiscordMessageEmbedFooter? = null,
    override val author: DiscordMessageEmbedAuthor? = null,
    override val timestamp: Instant? = null
) : DiscordMessageEmbed
