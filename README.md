# Discord HTML Transcripts

Discord HTML Transcripts is a Kotlin module to generate nice looking HTML transcripts for JDA and Kord (Kotlin-based Discord Framework). Processes discord markdown like **bold**, *italics*, ~~strikethroughs~~, and more. Resolves user, role and channel mentions, renders custom emojis and Discord timestamps, and nicely formats attachments and embeds. Built in XSS protection, preventing users from inserting html tags.

**This module is designed to work with [Kord](https://github.com/kordlib/kord) and [JDA](https://github.com/discord-jda/JDA).**

HTML Template stolen from [DiscordChatExporter](https://github.com/Tyrrrz/DiscordChatExporter).

## Installation

```xml
<dependency>
    <groupId>net.dungeon-hub</groupId>
    <artifactId>transcripts-{framework}</artifactId>
    <version>0.1</version>
</dependency>
```

## Example Output
![output](https://img.derock.dev/5f5q0a.png)

## Usage
The transcript is returned as an HTML string. Timestamps are shown in the time zone of the system generating the transcript.

### Kord
The transcript functions are `suspend` functions, so call them from a coroutine (e.g. inside an event handler).
```kotlin
val html = channel.createTranscript() // channel: GuildMessageChannel
```

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

### Or if you prefer, you can pass in your own messages
```java
String html = JDATranscripts.generateTranscriptFromMessages(textChannel, messages); // messages: List<Message>
```
For Kord, or if you're using coroutines, use `DiscordHtmlTranscripts.generateFromMessages(channel, messages)` with the wrapped channel and messages instead.
