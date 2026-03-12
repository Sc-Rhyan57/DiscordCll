package com.discordcall

// ─── User / Guild ─────────────────────────────────────────────────────────────

data class DiscordUser(
    val id:            String,
    val username:      String,
    val discriminator: String,
    val avatar:        String?,
    val globalName:    String?
) {
    val displayName get() = globalName ?: username
    fun avatarUrl(size: Int = 128) =
        if (avatar != null) "https://cdn.discordapp.com/avatars/$id/$avatar.${if (avatar.startsWith("a_")) "gif" else "png"}?size=$size"
        else "https://cdn.discordapp.com/embed/avatars/${(id.toLongOrNull() ?: 0L).mod(5L)}.png"
}

data class DiscordGuild(
    val id:                     String,
    val name:                   String,
    val icon:                   String?,
    val ownerId:                String,
    val approximateMemberCount: Int = 0
) {
    fun iconUrl(size: Int = 128): String? =
        if (icon != null) "https://cdn.discordapp.com/icons/$id/$icon.${if (icon.startsWith("a_")) "gif" else "png"}?size=$size"
        else null
}

// ─── Channels ─────────────────────────────────────────────────────────────────

data class ChannelCategory(
    val id:       String,
    val name:     String,
    val position: Int,
    val channels: List<VoiceChannel>
)

data class PermissionOverwrite(
    val id:    String,
    val type:  Int,   // 0=role, 1=member
    val allow: Long,
    val deny:  Long
)

data class VoiceChannel(
    val id:                  String,
    val name:                String,
    val type:                Int,    // 2=voice, 13=stage
    val position:            Int,
    val userLimit:           Int,
    val bitrate:             Int,
    val permissionOverwrites: List<PermissionOverwrite> = emptyList()
) {
    val isStageChannel get() = type == 13
}

object DiscordPermissions {
    const val ADMINISTRATOR = 1L shl 3
    const val MANAGE_CHANNELS = 1L shl 4
    const val CONNECT = 1L shl 20
    const val SPEAK   = 1L shl 21
    const val VIDEO   = 1L shl 27
    const val STREAM  = 1L shl 9

    fun has(perms: Long, flag: Long) = (perms and flag) != 0L
}

// ─── Voice ────────────────────────────────────────────────────────────────────

data class VoiceState(
    val userId:     String,
    val username:   String,
    val avatar:     String?,
    val channelId:  String,
    val sessionId:  String = "",
    val selfMute:   Boolean,
    val selfDeaf:   Boolean,
    val selfVideo:  Boolean,
    val selfStream: Boolean,
    val serverMute: Boolean,
    val serverDeaf: Boolean,
    val suppress:   Boolean,
    val speaking:   Boolean = false
) {
    fun avatarUrl(size: Int = 128): String =
        if (avatar != null) "https://cdn.discordapp.com/avatars/$userId/$avatar.${if (avatar.startsWith("a_")) "gif" else "png"}?size=$size"
        else "https://cdn.discordapp.com/embed/avatars/${(userId.toLongOrNull() ?: 0L).mod(5L)}.png"
}

// ─── DM ───────────────────────────────────────────────────────────────────────

data class DmRecipient(
    val id:          String,
    val username:    String,
    val globalName:  String?,
    val avatar:      String?
) {
    val displayName get() = globalName ?: username
    fun avatarUrl(size: Int = 128): String =
        if (avatar != null) "https://cdn.discordapp.com/avatars/$id/$avatar.${if (avatar.startsWith("a_")) "gif" else "png"}?size=$size"
        else "https://cdn.discordapp.com/embed/avatars/${(id.toLongOrNull() ?: 0L).mod(5L)}.png"
}

data class DmChannel(
    val id:            String,
    val type:          Int,
    val recipients:    List<DmRecipient>,
    val name:          String?,
    val icon:          String?,
    val lastMessageId: String?,
    val callActive:    Boolean = false,
    val callParticipants: List<String> = emptyList()
) {
    val isGroupDm  get() = type == 3
    val displayName: String get() =
        name ?: recipients.joinToString(", ") { it.displayName }.ifEmpty { "DM" }

    fun iconUrl(size: Int = 128): String? =
        if (isGroupDm && icon != null)
            "https://cdn.discordapp.com/channel-icons/$id/$icon.png?size=$size"
        else recipients.firstOrNull()?.avatarUrl(size)
}

enum class DmCallState { IDLE, RINGING_OUT, RINGING_IN, ACTIVE }

data class IncomingCall(
    val channelId:    String,
    val callerId:     String,
    val callerName:   String,
    val callerAvatar: String?,
    val isGroup:      Boolean,
    val groupName:    String?
) {
    fun callerAvatarUrl(size: Int = 128): String =
        if (callerAvatar != null) "https://cdn.discordapp.com/avatars/$callerId/$callerAvatar.png?size=$size"
        else "https://cdn.discordapp.com/embed/avatars/${(callerId.toLongOrNull() ?: 0L).mod(5L)}.png"
}

// ─── Messages (rich) ─────────────────────────────────────────────────────────

data class EmbedField(
    val name:   String,
    val value:  String,
    val inline: Boolean
)

data class MessageEmbed(
    val title:        String?,
    val description:  String?,
    val url:          String?,
    val color:        Int?,
    val authorName:   String?,
    val authorIcon:   String?,
    val footerText:   String?,
    val imageUrl:     String?,
    val thumbnailUrl: String?,
    val fields:       List<EmbedField>
)

data class MessageAttachment(
    val id:          String,
    val filename:    String,
    val url:         String,
    val proxyUrl:    String,
    val size:        Long,
    val width:       Int?,
    val height:      Int?,
    val contentType: String?
) {
    val isImage get() = contentType?.startsWith("image/") == true || filename.matches(Regex(".*\\.(png|jpg|jpeg|gif|webp)", RegexOption.IGNORE_CASE))
    val isVideo get() = contentType?.startsWith("video/") == true || filename.matches(Regex(".*\\.(mp4|mov|webm)", RegexOption.IGNORE_CASE))
    val isAudio get() = contentType?.startsWith("audio/") == true || filename.matches(Regex(".*\\.(mp3|ogg|wav|flac)", RegexOption.IGNORE_CASE))
}

data class MessageReaction(
    val emoji:   String,
    val emojiId: String?,
    val count:   Int,
    val me:      Boolean
) {
    val emojiUrl: String? get() =
        if (emojiId != null) "https://cdn.discordapp.com/emojis/$emojiId.png?size=32" else null
    val displayEmoji: String get() =
        if (emojiId != null) "<:$emoji:$emojiId>" else emoji
}

data class StickerItem(val id: String, val name: String) {
    fun url() = "https://media.discordapp.net/stickers/$id.png?size=160"
}

data class Message(
    val id:          String,
    val channelId:   String,
    val content:     String,
    val authorId:    String,
    val authorName:  String,
    val avatar:      String?,
    val timestamp:   Long,
    val embeds:      List<MessageEmbed>      = emptyList(),
    val attachments: List<MessageAttachment> = emptyList(),
    val reactions:   List<MessageReaction>  = emptyList(),
    val type:        Int                    = 0,
    val stickers:    List<StickerItem>      = emptyList()
) {
    fun authorAvatarUrl(size: Int = 64): String =
        if (avatar != null) "https://cdn.discordapp.com/avatars/$authorId/$avatar.${if (avatar.startsWith("a_")) "gif" else "png"}?size=$size"
        else "https://cdn.discordapp.com/embed/avatars/${(authorId.toLongOrNull() ?: 0L).mod(5L)}.png"
}

// ─── Settings ────────────────────────────────────────────────────────────────

data class CallSettings(
    val noiseSuppression:   Boolean = true,
    val echoCancellation:   Boolean = true,
    val autoGainControl:    Boolean = true,
    val stereoAudio:        Boolean = false,
    val overlayEnabled:     Boolean = true
)

enum class VideoQuality(val label: String, val width: Int, val height: Int, val fps: Int) {
    AUTO("Auto", 1280, 720, 30),
    LOW("360p",  640,  360, 15),
    MED("720p",  1280, 720, 30),
    HIGH("1080p",1920, 1080,30)
}

// ─── Guild Member / Roles ────────────────────────────────────────────────────

data class GuildMember(
    val userId: String,
    val roles:  List<String>,
    val nick:   String?
)
