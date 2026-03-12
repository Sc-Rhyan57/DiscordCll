package com.discordcall

data class DiscordUser(
    val id: String,
    val username: String,
    val discriminator: String,
    val globalName: String?,
    val avatar: String?,
    val email: String?,
    val verified: Boolean,
    val mfaEnabled: Boolean,
    val premiumType: Int,
    val publicFlags: Long,
    val flags: Long
) {
    val displayName get() = globalName ?: username
    fun avatarUrl(size: Int = 128): String =
        if (avatar != null) {
            val ext = if (avatar.startsWith("a_")) "gif" else "png"
            "https://cdn.discordapp.com/avatars/$id/$avatar.$ext?size=$size"
        } else {
            val index = (id.toLongOrNull() ?: 0L) % 6
            "https://cdn.discordapp.com/embed/avatars/$index.png"
        }
}

data class DiscordGuild(
    val id: String,
    val name: String,
    val icon: String?,
    val ownerId: String,
    val permissions: Long
) {
    fun iconUrl(size: Int = 128): String? =
        icon?.let {
            val ext = if (it.startsWith("a_")) "gif" else "png"
            "https://cdn.discordapp.com/icons/$id/$it.$ext?size=$size"
        }
}

data class VoiceChannel(
    val id: String,
    val guildId: String,
    val name: String,
    val type: Int,
    val position: Int,
    val userLimit: Int,
    val bitrate: Int,
    val parentId: String?,
    val permissionOverwrites: List<PermissionOverwrite>,
    val voiceStates: List<VoiceState> = emptyList()
) {
    val isStageChannel get() = type == 13
    val isVoiceChannel get() = type == 2
}

data class PermissionOverwrite(
    val id: String,
    val type: Int,
    val allow: Long,
    val deny: Long
)

data class VoiceState(
    val userId: String,
    val username: String,
    val avatar: String?,
    val channelId: String,
    val selfMute: Boolean,
    val selfDeaf: Boolean,
    val selfVideo: Boolean,
    val selfStream: Boolean,
    val serverMute: Boolean,
    val serverDeaf: Boolean,
    val suppress: Boolean,
    val speaking: Boolean = false
) {
    fun avatarUrl(size: Int = 64): String =
        if (avatar != null) {
            val ext = if (avatar.startsWith("a_")) "gif" else "png"
            "https://cdn.discordapp.com/avatars/$userId/$avatar.$ext?size=$size"
        } else {
            val index = (userId.toLongOrNull() ?: 0L) % 6
            "https://cdn.discordapp.com/embed/avatars/$index.png"
        }
}

data class ChannelCategory(
    val id: String,
    val name: String,
    val position: Int,
    val channels: MutableList<VoiceChannel> = mutableListOf()
)

data class Message(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String?,
    val content: String,
    val timestamp: Long,
    val attachments: List<MessageAttachment> = emptyList()
) {
    fun authorAvatarUrl(size: Int = 32): String =
        if (authorAvatar != null) {
            val ext = if (authorAvatar.startsWith("a_")) "gif" else "png"
            "https://cdn.discordapp.com/avatars/$authorId/$authorAvatar.$ext?size=$size"
        } else {
            val index = (authorId.toLongOrNull() ?: 0L) % 6
            "https://cdn.discordapp.com/embed/avatars/$index.png"
        }
}

data class MessageAttachment(
    val id: String,
    val filename: String,
    val url: String,
    val proxyUrl: String,
    val size: Int,
    val width: Int?,
    val height: Int?,
    val contentType: String?
)

data class GuildMember(
    val userId: String,
    val nick: String?,
    val roles: List<String>,
    val permissions: Long
)

object DiscordPermissions {
    const val VIEW_CHANNEL      = 1L shl 10
    const val CONNECT           = 1L shl 20
    const val SPEAK             = 1L shl 21
    const val MUTE_MEMBERS      = 1L shl 22
    const val DEAFEN_MEMBERS    = 1L shl 23
    const val MOVE_MEMBERS      = 1L shl 24
    const val USE_VAD           = 1L shl 25
    const val VIDEO             = 1L shl 9
    const val SEND_MESSAGES     = 1L shl 11
    const val STREAM            = 1L shl 9
    const val ADMINISTRATOR     = 1L shl 3
    const val MANAGE_CHANNELS   = 1L shl 4
    const val PRIORITY_SPEAKER  = 1L shl 8
    const val REQUEST_TO_SPEAK  = 1L shl 32

    fun has(permissions: Long, flag: Long) = (permissions and flag) != 0L
}

enum class DmCallState { IDLE, RINGING_OUT, RINGING_IN, ACTIVE }

data class DmChannel(
    val id: String,
    val type: Int,
    val recipients: List<DmRecipient>,
    val name: String?,
    val icon: String?,
    val lastMessageId: String?,
    val callActive: Boolean = false,
    val callParticipants: List<String> = emptyList()
) {
    val isDm      get() = type == 1
    val isGroupDm get() = type == 3

    val displayName: String get() = when {
        isGroupDm && !name.isNullOrBlank() -> name
        isGroupDm -> recipients.joinToString(", ") { it.displayName }.take(32)
        else      -> recipients.firstOrNull()?.displayName ?: "DM"
    }

    fun iconUrl(size: Int = 64): String? = when {
        isGroupDm && icon != null ->
            "https://cdn.discordapp.com/channel-icons/$id/$icon.png?size=$size"
        isDm -> recipients.firstOrNull()?.avatarUrl(size)
        else -> null
    }
}

data class DmRecipient(
    val id: String,
    val username: String,
    val globalName: String?,
    val avatar: String?,
    val discriminator: String = "0"
) {
    val displayName get() = globalName ?: username

    fun avatarUrl(size: Int = 64): String =
        if (avatar != null) {
            val ext = if (avatar.startsWith("a_")) "gif" else "png"
            "https://cdn.discordapp.com/avatars/$id/$avatar.$ext?size=$size"
        } else {
            val index = (id.toLongOrNull() ?: 0L) % 6
            "https://cdn.discordapp.com/embed/avatars/$index.png"
        }
}

data class IncomingCall(
    val channelId: String,
    val callerId: String,
    val callerName: String,
    val callerAvatar: String?,
    val isGroup: Boolean,
    val groupName: String?
) {
    fun callerAvatarUrl(size: Int = 128): String =
        if (callerAvatar != null) {
            val ext = if (callerAvatar.startsWith("a_")) "gif" else "png"
            "https://cdn.discordapp.com/avatars/$callerId/$callerAvatar.$ext?size=$size"
        } else {
            val index = (callerId.toLongOrNull() ?: 0L) % 6
            "https://cdn.discordapp.com/embed/avatars/$index.png"
        }
}

data class AppLog(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val detail: String? = null
)

data class CallSettings(
    val noiseSuppression: Boolean = true,
    val echoCancellation: Boolean = true,
    val autoGainControl: Boolean  = true,
    val stereoAudio: Boolean      = false,
    val inputSensitivity: Float   = 0.5f,
    val pushToTalk: Boolean       = false,
    val videoQuality: VideoQuality = VideoQuality.AUTO,
    val overlayEnabled: Boolean   = false
)

enum class VideoQuality(val label: String, val width: Int, val height: Int, val fps: Int, val bitrate: Int) {
    LOW("360p", 640, 360, 15, 500_000),
    MEDIUM("480p", 854, 480, 30, 1_500_000),
    HIGH("720p", 1280, 720, 30, 3_000_000),
    FHD("1080p", 1920, 1080, 30, 6_000_000),
    AUTO("Auto", 1280, 720, 30, 2_500_000)
}
