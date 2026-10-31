# Discord HTML Transcripts

Discord HTML Transcripts is a Kotlin library to generate nice looking HTML transcripts of Discord channels, with support for [JDA](https://github.com/discord-jda/JDA) and [Kord](https://github.com/kordlib/kord).

HTML Template stolen from [DiscordChatExporter](https://github.com/Tyrrrz/DiscordChatExporter).

## Features
- Discord markdown: **bold**, *italics*, ~~strikethroughs~~, underlines, quotes, links, inline code and code blocks
- Backslash escapes, e.g. `\*not italic\*`
- User, role and channel mentions resolved to their names, as well as slash command mentions
- Custom (and animated) emojis and Discord timestamps (`<t:...>`)
- Replies, slash command interactions, attachments (images, videos, audio and files) and embeds
- Date dividers between days, and the full date when hovering over a message's time
- Built in XSS protection, preventing users from inserting HTML tags

## Example Output
![output](https://img.derock.dev/5f5q0a.png)

## Installation
Use `transcripts-jda` or `transcripts-kord`, depending on your Discord library.

### Gradle (Kotlin DSL)
```kotlin
dependencies {
    implementation("net.dungeon-hub:transcripts-jda:0.3.0")
}
```

### Maven
```xml
<dependency>
    <groupId>net.dungeon-hub</groupId>
    <artifactId>transcripts-jda</artifactId>
    <version>0.3.0</version>
</dependency>
```

`transcripts-jda` includes JDA. `transcripts-kord` doesn't include Kord, so add Kord to your project yourself.

## Usage
The transcript is returned as an HTML string. Timestamps are shown in the time zone of the system generating the transcript.

### JDA (Java)
`JDATranscripts` blocks until the transcript is generated, so don't call it from JDA's callback threads (e.g. inside `queue(...)`).
```java
String html = JDATranscripts.createTranscript(textChannel);

textChannel.sendFiles(FileUpload.fromData(html.getBytes(StandardCharsets.UTF_8), "transcript.html")).queue();
```

### JDA (Kotlin)
```kotlin
val html = textChannel.createTranscript()
```

### Kord
The transcript functions are `suspend` functions, so call them from a coroutine (e.g. inside an event handler).
```kotlin
val html = channel.createTranscript() // channel: GuildMessageChannel
```

### Passing in your own messages
Instead of the whole channel history, you can also generate a transcript of specific messages:
```java
String html = JDATranscripts.generateTranscriptFromMessages(textChannel, messages); // messages: List<Message>
```
For Kord, or if you're using coroutines, use `DiscordHtmlTranscripts.generateFromMessages(channel, messages)` with the wrapped channel and messages instead.

### Other Discord libraries
The core module (`transcripts-core`) works with any Discord library: implement the interfaces in `net.dungeonhub.wrapper` (`DiscordChannel`, `DiscordMessage`, `DiscordServer`, ...) and pass your channel to `DiscordHtmlTranscripts.createTranscript`. The `transcripts-jda` and `transcripts-kord` modules are examples of this.

## Upgrading from 0.2
- `DiscordHtmlTranscripts.createTranscript` and `generateFromMessages` are now `suspend` functions. From Java, or without coroutines, use the blocking `JDATranscripts` functions instead.
- Custom wrappers: `DiscordMessage` has a new `channel` property, used to resolve mentions. `DiscordServer` has new `getMemberName`, `getRoleName` and `getChannelName` functions; they return `null` by default, which shows mentions by their id.
