package net.dungeonhub.transcripts

import net.dungeonhub.wrapper.DiscordServer
import java.awt.Color
import java.time.Instant
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors
import kotlin.math.ln
import kotlin.math.pow

/**
 * Created by Ryzeon
 * Modified by Taubsie, for Kotlin and greater Framework support
 * Project: discord-html-transcripts
 * Date: 2/12/21 @ 00:32
 * Twitter: @Ryzeon_ 😎
 * Github: github.ryzeon.me
 */
object Formatter {
    private val STRONG: Pattern = Pattern.compile("\\*\\*(.+?)\\*\\*")
    private val EM: Pattern = Pattern.compile("\\*(.+?)\\*")
    private val S: Pattern = Pattern.compile("~~(.+?)~~")
    private val U: Pattern = Pattern.compile("__(.+?)__")
    private val CODE: Pattern = Pattern.compile("```(.+?)```", Pattern.DOTALL)
    private val CODE_1: Pattern = Pattern.compile("`([^`]+?)`")
    // matched after html escaping, so the quote marker is "&gt;" instead of ">"
    private val QUOTE: Pattern = Pattern.compile("^(?:&gt;){1,3} (.*)$", Pattern.MULTILINE)
    private val LINK: Pattern = Pattern.compile("\\[([^\\[]+)\\](\\((www|http:|https:)+[^\\s]+[\\w]\\))")
    private val NEW_LINE: Pattern = Pattern.compile("\\n")
    // Discord mention patterns: user (<@id> or <@!id>), role (<@&id>), channel (<#id>)
    private val MENTION_USER: Pattern = Pattern.compile("<@!?(\\d+)>")
    private val MENTION_ROLE: Pattern = Pattern.compile("<@&(\\d+)>")
    private val MENTION_CHANNEL: Pattern = Pattern.compile("<#(\\d+)>")
    // slash command mention: </name:id> or </name subcommand:id>
    private val MENTION_COMMAND: Pattern = Pattern.compile("</([^:<>]+):(\\d+)>")
    // custom emoji: <:name:id>, animated emoji: <a:name:id>
    private val CUSTOM_EMOJI: Pattern = Pattern.compile("<(a?):(\\w+):(\\d+)>")
    // Discord timestamp: <t:seconds> or <t:seconds:style>
    private val DISCORD_TIMESTAMP: Pattern = Pattern.compile("<t:(-?\\d+)(?::([tTdDfFR]))?>")
    // a backslash in front of one of these characters removes its markdown / mention meaning
    private val ESCAPED_CHARACTER: Pattern = Pattern.compile("\\\\([\\\\*_~`<>\\[\\]()#@])")
    // escaped characters and code are matched in a single pass, so whichever comes first wins:
    // "\\`" is a literal backtick, while a backslash inside a code block stays a backslash
    private val ESCAPED_CHARACTER_OR_CODE: Pattern = Pattern.compile(
        "${ESCAPED_CHARACTER.pattern()}|${CODE.pattern()}|${CODE_1.pattern()}",
        Pattern.DOTALL
    )
    // everything that would break if a message preview was cut in the middle of it
    private val PREVIEW_TOKENS = listOf(
        ESCAPED_CHARACTER_OR_CODE, MENTION_USER, MENTION_ROLE, MENTION_CHANNEL, MENTION_COMMAND, CUSTOM_EMOJI,
        DISCORD_TIMESTAMP, STRONG, EM, S, U, LINK
    )

    /*
     * Code blocks, mentions and escaped characters are rendered first and swapped out for a placeholder,
     * so that the markdown patterns can't modify them. The placeholders are swapped back at the very end.
     * Discord messages can't contain the NUL character, which makes it safe to use as the delimiter.
     */
    private const val PLACEHOLDER_DELIMITER = "\u0000"
    private val PLACEHOLDER: Pattern = Pattern.compile("$PLACEHOLDER_DELIMITER(\\d+)$PLACEHOLDER_DELIMITER")

    fun formatBytes(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1].toString()
        return String.format("%.1f %sB", bytes / unit.toDouble().pow(exp.toDouble()), pre)
    }

    fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    private class Placeholders {
        private val replacements = mutableListOf<String>()

        /**
         * Replaces every match of [pattern] in [text] with a placeholder for the html returned by [toHtml].
         */
        suspend fun replace(text: String, pattern: Pattern, toHtml: suspend (Matcher) -> String): String {
            val matcher = pattern.matcher(text)
            val result = StringBuilder()
            while (matcher.find()) {
                replacements.add(toHtml(matcher))
                val placeholder = "$PLACEHOLDER_DELIMITER${replacements.size - 1}$PLACEHOLDER_DELIMITER"
                matcher.appendReplacement(result, Matcher.quoteReplacement(placeholder))
            }
            matcher.appendTail(result)
            return result.toString()
        }

        fun restore(text: String): String {
            return PLACEHOLDER.matcher(text).replaceAll {
                Matcher.quoteReplacement(replacements[it.group(1).toInt()])
            }
        }
    }

    suspend fun format(originalText: String, server: DiscordServer? = null): String {
        val placeholders = Placeholders()

        // the delimiter must not appear in the input, otherwise it could be mistaken for a placeholder
        var newText = originalText.replace(PLACEHOLDER_DELIMITER, "")

        newText = placeholders.replace(newText, ESCAPED_CHARACTER_OR_CODE) {
            val escapedCharacter = it.group(1)
            val codeBlock = it.group(2)
            val inlineCode = it.group(3)

            when {
                escapedCharacter != null -> escapeHtml(escapedCharacter)
                codeBlock != null ->
                    "<div class=\"pre pre--multiline nohighlight\">${formatCodeBlock(escapeHtml(codeBlock))}</div>"
                else -> "<span class=\"pre pre--inline\">${escapeHtml(inlineCode)}</span>"
            }
        }

        suspend fun resolveMentions(pattern: Pattern, prefix: Char, resolver: suspend (Long) -> String?) {
            newText = placeholders.replace(newText, pattern) {
                val id = it.group(1)
                val name = id.toLongOrNull()?.let { parsedId -> resolver(parsedId) } ?: id
                "<span class=\"mention\">${escapeHtml("$prefix$name")}</span>"
            }
        }

        resolveMentions(MENTION_USER, '@') { server?.getMemberName(it) }
        resolveMentions(MENTION_ROLE, '@') { server?.getRoleName(it) }
        resolveMentions(MENTION_CHANNEL, '#') { server?.getChannelName(it) }

        newText = placeholders.replace(newText, MENTION_COMMAND) {
            "<span class=\"mention\">${escapeHtml("/${it.group(1)}")}</span>"
        }

        newText = placeholders.replace(newText, CUSTOM_EMOJI) {
            val extension = if (it.group(1) == "a") "gif" else "png"
            val name = ":${it.group(2)}:"
            "<img class=\"emoji\" alt=\"$name\" title=\"$name\" " +
                    "src=\"https://cdn.discordapp.com/emojis/${it.group(3)}.$extension\" loading=\"lazy\">"
        }

        newText = placeholders.replace(newText, DISCORD_TIMESTAMP) {
            // out of range timestamps are kept as they were written
            runCatching {
                val instant = Instant.ofEpochSecond(it.group(1).toLong())
                val text = TimeFormatter.formatDiscordTimestamp(instant, it.group(2)?.single())
                "<span class=\"timestamp\" title=\"${TimeFormatter.formatFullDateTime(instant)}\">$text</span>"
            }.getOrElse { _ -> escapeHtml(it.group()) }
        }

        newText = escapeHtml(newText)

        var matcher = STRONG.matcher(newText)
        while (matcher.find()) {
            val group = matcher.group()
            newText = newText.replace(
                group,
                "<strong>" + group.replace("**", "") + "</strong>"
            )
        }
        matcher = EM.matcher(newText)
        while (matcher.find()) {
            val group = matcher.group()
            newText = newText.replace(
                group,
                "<em>" + group.replace("*", "") + "</em>"
            )
        }
        matcher = S.matcher(newText)
        while (matcher.find()) {
            val group = matcher.group()
            newText = newText.replace(
                group,
                "<s>" + group.replace("~~", "") + "</s>"
            )
        }
        matcher = U.matcher(newText)
        while (matcher.find()) {
            val group = matcher.group()
            newText = newText.replace(
                group,
                "<u>" + group.replace("__", "") + "</u>"
            )
        }
        matcher = QUOTE.matcher(newText)
        while (matcher.find()) {
            val group = matcher.group()
            newText = newText.replace(
                group,
                "<span class=\"quote\">" + matcher.group(1) + "</span>"
            )
        }
        matcher = LINK.matcher(newText)
        while (matcher.find()) {
            val group = matcher.group(1)
            val link = matcher.group(2)
            val raw = "[" + group + "]" + link

            newText =
                newText.replace(raw, "<a href=\"" + link.replace("(", "").replace(")", "") + "\">" + group + "</a>")
        }

        matcher = NEW_LINE.matcher(newText)
        while (matcher.find()) {
            newText = newText.replace(matcher.group(), "<br />")
        }

        return placeholders.restore(newText)
    }

    /**
     * Cuts [text] down to roughly [maxLength] characters. If the cut would land inside a mention, code block or
     * markdown element, the text is kept until the end of that element instead, so it still renders correctly.
     */
    fun truncate(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text

        var end = maxLength
        var extended = true
        while (extended) {
            extended = false
            for (pattern in PREVIEW_TOKENS) {
                val matcher = pattern.matcher(text)
                while (matcher.find()) {
                    if (matcher.start() < end && matcher.end() > end) {
                        end = matcher.end()
                        extended = true
                    }
                }
            }
        }

        return if (end >= text.length) text else text.substring(0, end) + "..."
    }

    fun formatCodeBlock(group: String): String {
        var result = group.replace("```", "").trim()

        val empty = AtomicBoolean(true)
        result = Arrays.stream(result.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            .sequential().filter { s: String ->
                if (empty.get()) {
                    if (s.isBlank()) {
                        return@filter false
                    } else {
                        empty.set(false)
                        return@filter true
                    }
                } else {
                    return@filter true
                }
            }.collect(Collectors.joining("\n"))

        return result
    }

    fun toHex(color: Color): String {
        var hex = Integer.toHexString(color.rgb and 0xffffff)
        while (hex.length < 6) {
            hex = "0${hex}"
        }
        return hex
    }
}
