package net.dungeonhub.wrapper

interface DiscordServer {
    val name: String
    val icon: String?

    /** Returns the member's effective (nick → global name → username) name, or null if not found. */
    suspend fun getMemberName(id: Long): String? = null
    /** Returns the role's name, or null if not found. */
    suspend fun getRoleName(id: Long): String? = null
    /** Returns the channel's name, or null if not found. */
    suspend fun getChannelName(id: Long): String? = null
}
