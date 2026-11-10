package net.dungeonhub.wrapper.jda

import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.requests.restaction.pagination.MessagePaginationAction
import java.util.stream.Stream
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture

/**
 * Tests the thread lookup of [JDAServer] against hand-written stand-ins for the JDA interfaces.
 */
class JDAServerTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> stub(type: Class<T>, handler: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            handler(method.name, args ?: emptyArray())
        } as T

    private fun thread(id: Long, name: String) = stub(ThreadChannel::class.java) { method, _ ->
        when (method) {
            "getIdLong" -> id
            "getName" -> name
            else -> null
        }
    }

    private fun pagination(threads: List<ThreadChannel>, failWith: Exception? = null) =
        stub(ThreadChannelPaginationAction::class.java) { method, args ->
            when (method) {
                "forEachAsync" -> {
                    if (failWith != null) {
                        CompletableFuture.failedFuture<Any?>(failWith)
                    } else {
                        // like JDA: keep going until the callback returns false
                        val callback = args[0] as net.dv8tion.jda.api.utils.Procedure<ThreadChannel>
                        threads.takeWhile { callback.execute(it) }
                        CompletableFuture.completedFuture<Any?>(null)
                    }
                }
                else -> null
            }
        }

    private fun container(archived: ThreadChannelPaginationAction) = stub(TextChannel::class.java) { method, _ ->
        when (method) {
            "retrieveArchivedPublicThreadChannels" -> archived
            "retrieveArchivedPrivateJoinedThreadChannels", "retrieveArchivedPrivateThreadChannels" ->
                pagination(emptyList(), failWith = IllegalStateException("missing permission"))
            else -> null
        }
    }

    private fun guild(
        active: List<ThreadChannel>,
        containers: List<TextChannel>,
        onActiveRequest: () -> Unit = {},
        activeRequestFailure: Throwable? = null
    ) =
        stub(Guild::class.java) { method, _ ->
            when (method) {
                "getGuildChannelById" -> null
                "retrieveActiveThreads" -> stub(RestAction::class.java) { m, _ ->
                    if (m == "submit") {
                        onActiveRequest()
                        if (activeRequestFailure != null) {
                            CompletableFuture.failedFuture<Any?>(activeRequestFailure)
                        } else {
                            CompletableFuture.completedFuture(active)
                        }
                    } else {
                        null
                    }
                }
                "getTextChannels" -> containers
                "getNewsChannels", "getForumChannels", "getMediaChannels" -> emptyList<Any>()
                else -> null
            }
        }

    @Test
    fun testArchivedThreadNameIsRetrieved() = runBlocking {
        val archived = pagination(listOf(thread(1L, "other"), thread(2L, "archived-thread")))
        val server = JDAServer(guild(emptyList(), listOf(container(archived))))

        assertEquals("archived-thread", server.getChannelName(2L))
    }

    @Test
    fun testActiveThreadNameIsRetrieved() = runBlocking {
        val server = JDAServer(guild(listOf(thread(3L, "active-thread")), emptyList()))

        assertEquals("active-thread", server.getChannelName(3L))
    }

    @Test
    fun testUnknownChannelAndFailingRequestsReturnNull() = runBlocking {
        val failing = pagination(emptyList(), failWith = IllegalStateException("missing permission"))
        val server = JDAServer(guild(emptyList(), listOf(container(failing))))

        assertNull(server.getChannelName(4L))
    }

    @Test
    fun testFoundThreadsAreCachedUntilTheCacheIsCleared() = runBlocking {
        var requests = 0
        val server = JDAServer(guild(listOf(thread(3L, "active-thread")), emptyList(), onActiveRequest = { requests++ }))

        assertEquals("active-thread", server.getChannelName(3L))
        assertEquals("active-thread", server.getChannelName(3L))
        assertEquals(1, requests)

        server.clearCache()
        assertEquals("active-thread", server.getChannelName(3L))
        assertEquals(2, requests)
    }

    @Test
    fun testCancellationIsNotSwallowed() {
        val server = JDAServer(
            guild(emptyList(), emptyList(), activeRequestFailure = java.util.concurrent.CancellationException())
        )

        assertThrows(java.util.concurrent.CancellationException::class.java) {
            runBlocking { server.getChannelName(5L) }
        }
    }

    @Test
    fun testStartingAGenerationOnAReusedChannelRefreshesNames() = runBlocking {
        var requests = 0
        val guild = guild(listOf(thread(3L, "active-thread")), emptyList(), onActiveRequest = { requests++ })
        val messageChannel = stub(GuildMessageChannel::class.java) { method, _ ->
            when (method) {
                "getGuild" -> guild
                "getIterableHistory" -> stub(MessagePaginationAction::class.java) { m, _ ->
                    if (m == "stream") Stream.empty<Any>() else null
                }
                else -> null
            }
        }
        val channel = JDAChannel(messageChannel)

        assertEquals("active-thread", channel.server.getChannelName(3L))
        assertEquals("active-thread", channel.server.getChannelName(3L))
        assertEquals(1, requests)

        // starting a new transcript generation must not reuse the names of the previous one
        channel.startGeneration()
        assertEquals("active-thread", channel.server.getChannelName(3L))
        assertEquals(2, requests)
    }

    @Test
    fun testMissingChannelsAreNotSearchedAgainUntilTheCacheIsCleared() = runBlocking {
        var requests = 0
        val server = JDAServer(guild(emptyList(), emptyList(), onActiveRequest = { requests++ }))

        assertNull(server.getChannelName(9L))
        assertNull(server.getChannelName(9L))
        assertEquals(1, requests)

        server.clearCache()
        assertNull(server.getChannelName(9L))
        assertEquals(2, requests)
    }

    @Test
    fun testArchivedThreadSearchIsBounded() = runBlocking {
        val threads = (1L..300L).map { thread(it, "thread-$it") }
        val server = JDAServer(guild(emptyList(), listOf(container(pagination(threads)))))

        assertEquals("thread-150", server.getChannelName(150L))
        // beyond the limit of 200 threads per source, so the id is not resolved
        assertNull(server.getChannelName(250L))
    }

    @Test
    fun testDetachedServerResolvesNoMentions() = runBlocking {
        val guild = guild(listOf(thread(3L, "active-thread")), emptyList())
        val server = DetachedJDAServer(guild)

        assertNull(server.getMemberName(1L))
        assertNull(server.getRoleName(2L))
        assertNull(server.getChannelName(3L))
    }

    @Test
    fun testChannelUsesTheServerOverride() {
        val messageChannel = stub(GuildMessageChannel::class.java) { _, _ -> null }
        val override = DetachedJDAServer(guild(emptyList(), emptyList()))

        assertEquals(override, JDAChannel(messageChannel, override).server)
    }
}
