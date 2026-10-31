package net.dungeonhub.transcripts

import net.dungeonhub.wrapper.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.jsoup.Jsoup
import java.time.Instant
import java.awt.Color
import kotlinx.coroutines.runBlocking

class TranscriptTest {

    private val mockServer = object : DiscordServer {
        override val name: String = "Test Guild"
        override val icon: String? = "http://example.com/icon.png"

        override suspend fun getMemberName(id: Long): String? = if (id == 123L) "TestUser" else null
        override suspend fun getRoleName(id: Long): String? = if (id == 456L) "TestRole" else null
        override suspend fun getChannelName(id: Long): String? = if (id == 789L) "test-channel" else null
    }

    @Test
    fun testMentionFormatting() = runBlocking {
        val rawText = "Hello <@123>, check <@&456> in <#789> and unknown <@999>"
        val formatted = Formatter.format(rawText, mockServer)
        
        assertTrue(formatted.contains("<span class=\"mention\">@TestUser</span>"))
        assertTrue(formatted.contains("<span class=\"mention\">@TestRole</span>"))
        assertTrue(formatted.contains("<span class=\"mention\">#test-channel</span>"))
        assertTrue(formatted.contains("<span class=\"mention\">@999</span>"))
    }

    @Test
    fun testBackslashEscaping() = runBlocking {
        // Escaped asterisks, underscores, and mentions should not be formatted, and backslashes should be removed.
        val text1 = "This is \\*\\*not bold\\*\\*"
        val formatted1 = Formatter.format(text1, mockServer)
        assertEquals("This is **not bold**", formatted1)

        val text2 = "This is \\_not italic\\_"
        val formatted2 = Formatter.format(text2, mockServer)
        assertEquals("This is _not italic_", formatted2)

        val text3 = "Do not resolve \\<@123\\>"
        val formatted3 = Formatter.format(text3, mockServer)
        assertEquals("Do not resolve &lt;@123&gt;", formatted3)

        val text4 = "Escaped backslash: \\\\\\*"
        val formatted4 = Formatter.format(text4, mockServer)
        assertEquals("Escaped backslash: \\*", formatted4)
    }

    @Test
    fun testHtmlSafetyAndInjection() = runBlocking {
        // Raw HTML should be escaped to prevent injection/XSS.
        val maliciousText = "<script>alert('xss')</script> and <b>bold HTML</b>"
        val formatted = Formatter.format(maliciousText, mockServer)
        
        assertFalse(formatted.contains("<script>"))
        assertFalse(formatted.contains("<b>"))
        assertTrue(formatted.contains("&lt;script&gt;"))
        assertTrue(formatted.contains("&lt;b&gt;"))
    }

    @Test
    fun testCodeBlockIsolation() = runBlocking {
        // Backslashes and markdown characters inside code blocks should not be formatted.
        val codeText = "```\n\\*\\*not formatted\\*\\*\n```"
        val formatted = Formatter.format(codeText, mockServer)
        
        assertTrue(formatted.contains("\\*\\*not formatted\\*\\*"))
        assertFalse(formatted.contains("<strong>"))
    }

    @Test
    fun testDateDividersAndTimestamps() = runBlocking {
        val times = listOf(
            Instant.parse("2026-07-29T10:00:00Z"),
            Instant.parse("2026-07-30T10:00:00Z"),
            Instant.parse("2026-07-30T11:00:00Z")
        )
        val channel = MockChannel(times)

        val html = DiscordHtmlTranscripts.createTranscript(channel)
        val doc = Jsoup.parse(html)

        // the timestamps are 24h apart, so there's a day boundary between the first two in every time zone
        val dateDividers = doc.select(".chatlog__date-divider")
        assertEquals(2, dateDividers.size)
        assertEquals(
            TimeFormatter.formatDate(TimeFormatter.toLocalDateTime(times[0]).date),
            dateDividers[0].text()
        )
        assertTrue(dateDividers[0].text().matches(Regex("\\d{1,2} [A-Z][a-z]+ \\d{4}")))

        val timestamps = doc.select(".chatlog__timestamp")
        assertEquals(3, timestamps.size)
        timestamps.zip(times).forEach { (timestamp, time) ->
            assertEquals(TimeFormatter.formatTime(time), timestamp.text())
            assertEquals(TimeFormatter.formatFullDateTime(time), timestamp.attr("title"))
            assertTrue(timestamp.attr("title").matches(Regex("[A-Z][a-z]+day, \\d{1,2} [A-Z][a-z]+ \\d{4} at \\d{2}:\\d{2}")))
        }
    }

    @Test
    fun testMentionsResolveThroughMessageChannel() = runBlocking {
        val message = MockChannel(listOf(Instant.parse("2026-07-30T10:00:00Z")), "Hi <@123>").messages.single()

        val html = message.transcriptify().outerHtml()

        assertTrue(html.contains("@TestUser"))
    }

    @Test
    fun testOversizedMentionId() = runBlocking {
        val formatted = Formatter.format("<@99999999999999999999999>", mockServer)

        assertEquals("<span class=\"mention\">@99999999999999999999999</span>", formatted)
    }

    @Test
    fun testMixedCodeBlocks() = runBlocking {
        val formatted = Formatter.format("```\n**a**\n``` and `**b**` and **c**", mockServer)

        assertTrue(formatted.contains("<div class=\"pre pre--multiline nohighlight\">**a**</div>"))
        assertTrue(formatted.contains("<span class=\"pre pre--inline\">**b**</span>"))
        assertTrue(formatted.contains("<strong>c</strong>"))
    }

    @Test
    fun testQuote() = runBlocking {
        assertEquals("<span class=\"quote\">quoted</span>", Formatter.format("> quoted", mockServer))
        assertEquals(
            "first<br /><span class=\"quote\">quoted</span>",
            Formatter.format("first\n> quoted", mockServer)
        )
    }

    @Test
    fun testPlaceholderDelimiterInInput() = runBlocking {
        assertEquals("a0b", Formatter.format("a\u00000\u0000b", mockServer))
    }

    @Test
    fun testEscapedBacktick() = runBlocking {
        assertEquals("`not code`", Formatter.format("\\`not code\\`", mockServer))
        // a backslash inside code stays as it is
        assertEquals("<span class=\"pre pre--inline\">a\\</span>", Formatter.format("`a\\`", mockServer))
    }

    @Test
    fun testTruncate() {
        assertEquals("short", Formatter.truncate("short", 42))
        assertEquals("abc...", Formatter.truncate("abcdef", 3))
        // the cut would land inside the mention / code / markdown, so the preview is extended to its end
        assertEquals("hi <@123456789>...", Formatter.truncate("hi <@123456789> and more", 6))
        assertEquals("a `some code`...", Formatter.truncate("a `some code` b", 5))
        assertEquals("**bold text**...", Formatter.truncate("**bold text** rest", 4))
    }

    @Test
    fun testEmbedMentionsResolve() = runBlocking {
        val embed = object : DiscordMessageEmbed {
            override val color: Color? = null
            override val title: String? = null
            override val description: String = "Hi <@123>"
            override val fields: List<DiscordMessageEmbedField> = listOf(object : DiscordMessageEmbedField {
                override val name: String = "Role"
                override val value: String = "<@&456>"
                override val isInline: Boolean = false
            })
            override val url: String? = null
            override val thumbnail: String? = null
            override val image: String? = null
            override val footer: DiscordMessageEmbedFooter? = null
            override val author: DiscordMessageEmbedAuthor? = null
            override val timestamp: Instant? = null
        }

        val html = embed.transcriptify(mockServer).outerHtml()

        assertTrue(html.contains("@TestUser"))
        assertTrue(html.contains("@TestRole"))
    }

    @Test
    fun testUserControlledNamesAreEscaped() = runBlocking {
        val payload = "<img src=x onerror=alert(1)>"
        val maliciousAuthor = object : DiscordMessageAuthor {
            override val id: Long = 1L
            override val name: String = payload
            override val displayName: String = payload
            override val avatar: String = "\" onerror=\"alert(1)"
            override val isBot: Boolean = false
            override val roleColor: Color = Color.WHITE
        }
        val time = Instant.parse("2026-07-30T10:00:00Z")
        val channel = MockChannel(listOf(time))
        val referenced = createMockMessage(1L, "original", time, channel, msgAuthor = maliciousAuthor)
        val reply = createMockMessage(2L, "reply", time, channel, msgReference = referenced)
        val field = object : DiscordMessageEmbedField {
            override val name: String = payload
            override val value: String = "value"
            override val isInline: Boolean = false
        }

        val html = reply.transcriptify().outerHtml() + field.transcriptify(mockServer).outerHtml()
        val doc = Jsoup.parse(html)

        assertTrue(doc.select("img[onerror]").isEmpty())
        assertTrue(doc.select("[onerror]").isEmpty())
        assertEquals(payload, doc.selectFirst(".chatlog__reference-name")!!.text())
    }

    @Test
    fun testCustomEmoji() = runBlocking {
        val formatted = Formatter.format("hi <:pepe:123> <a:dance:456>", mockServer)

        assertEquals(
            "hi <img class=\"emoji\" alt=\":pepe:\" title=\":pepe:\" " +
                "src=\"https://cdn.discordapp.com/emojis/123.png\" loading=\"lazy\"> " +
                "<img class=\"emoji\" alt=\":dance:\" title=\":dance:\" " +
                "src=\"https://cdn.discordapp.com/emojis/456.gif\" loading=\"lazy\">",
            formatted
        )
    }

    @Test
    fun testDiscordTimestamps() = runBlocking {
        val instant = Instant.ofEpochSecond(1_700_000_000)
        val fullDate = TimeFormatter.formatFullDateTime(instant)

        assertEquals(
            "<span class=\"timestamp\" title=\"$fullDate\">${TimeFormatter.formatTime(instant)}</span>",
            Formatter.format("<t:1700000000:t>", mockServer)
        )
        assertEquals(
            "<span class=\"timestamp\" title=\"$fullDate\">${TimeFormatter.formatDiscordTimestamp(instant, null)}</span>",
            Formatter.format("<t:1700000000>", mockServer)
        )
        assertTrue(TimeFormatter.formatDiscordTimestamp(instant, 'd').matches(Regex("\\d{2}/\\d{2}/2023")))
        // out of range timestamps are kept as text
        assertEquals("&lt;t:99999999999999999:R&gt;", Formatter.format("<t:99999999999999999:R>", mockServer))
    }

    @Test
    fun testCommandMention() = runBlocking {
        assertEquals(
            "use <span class=\"mention\">/ticket close</span>",
            Formatter.format("use </ticket close:123>", mockServer)
        )
    }

    @Test
    fun testNameCacheLooksUpEachIdOnce() = runBlocking {
        val cache = net.dungeonhub.wrapper.NameCache()
        var lookups = 0
        val lookup: suspend (Long) -> String? = { lookups++; if (it == 1L) "one" else null }

        repeat(3) {
            assertEquals("one", cache.getOrLookup(1L, lookup))
            assertNull(cache.getOrLookup(2L, lookup))
        }

        assertEquals(2, lookups)
    }

    @Test
    fun testReplyPreviewResolvesMentions() = runBlocking {
        val time = Instant.parse("2026-07-30T10:00:00Z")
        val channel = MockChannel(listOf(time))
        val referenced = createMockMessage(1L, "Hey <@123>, see <#789> <:pepe:42>", time, channel)
        val reply = createMockMessage(2L, "reply", time, channel, msgReference = referenced)

        val preview = Jsoup.parse(reply.transcriptify().outerHtml()).selectFirst(".chatlog__reference-link em")!!

        assertEquals("Hey @TestUser, see #test-channel", preview.text().trim())
        assertNotNull(preview.selectFirst("img.emoji"))
        assertFalse(preview.html().contains("&lt;@"))
    }

    private inner class MockChannel(times: List<Instant>, content: String = "Message") : DiscordChannel<DiscordFramework> {
        override val framework: DiscordFramework = object : DiscordFramework {}
        override val name: String = "general"
        override val server: DiscordServer = mockServer
        override val messages: List<DiscordMessage> = times.mapIndexed { index, time ->
            createMockMessage(index.toLong(), content, time, this)
        }
    }

    private fun createMockMessage(
        messageId: Long,
        msgContent: String,
        msgTime: Instant,
        msgChannel: DiscordChannel<*>,
        msgReference: DiscordMessage? = null,
        msgAuthor: DiscordMessageAuthor? = null
    ): DiscordMessage {
        val mockAuthor = object : DiscordMessageAuthor {
            override val id: Long = 1000L + messageId
            override val name: String = "Author $messageId"
            override val displayName: String = "Author $messageId"
            override val avatar: String = "http://example.com/avatar.png"
            override val isBot: Boolean = false
            override val roleColor: Color = Color.WHITE
        }

        return object : DiscordMessage {
            override val id: Long = messageId
            override val content: String = msgContent
            override val embeds: List<DiscordMessageEmbed?> = emptyList()
            override val attachments: List<DiscordMessageAttachment> = emptyList()
            override val creationTime: Instant = msgTime
            override val reference: DiscordMessage? = msgReference
            override val author: DiscordMessageAuthor = msgAuthor ?: mockAuthor
            override val interaction: DiscordInteraction? = null
            override val channel: DiscordChannel<*> = msgChannel
        }
    }
}
