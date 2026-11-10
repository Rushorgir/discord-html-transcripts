package net.dungeonhub.wrapper

interface DiscordChannel<FW: DiscordFramework> {
    val framework: FW
    val name: String
    val server: DiscordServer
    val messages: List<DiscordMessage>

    /**
     * Called at the start of every transcript generation, including [net.dungeonhub.transcripts.DiscordHtmlTranscripts.generateFromMessages],
     * so implementations can drop anything remembered from an earlier generation (e.g. looked up names).
     */
    fun startGeneration() {}
}
