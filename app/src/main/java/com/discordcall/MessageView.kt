package com.discordcall

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiscordMessageView(msg: Message, isCompact: Boolean = false) {
    when (msg.type) {
        7  -> SystemMessage("${msg.authorName} entrou no servidor.", Icons.Outlined.PersonAdd)
        8  -> SystemMessage("${msg.authorName} saiu do servidor.", Icons.Outlined.PersonRemove)
        else -> RegularMessage(msg, isCompact)
    }
}

@Composable
private fun SystemMessage(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AppColors.TextMuted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = AppColors.TextMuted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }
}

@Composable
private fun RegularMessage(msg: Message, isCompact: Boolean) {
    val timeStr = remember(msg.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = if (isCompact) 2.dp else 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (!isCompact) {
            AnimatedImage(
                url = msg.authorAvatarUrl(64),
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape)
            )
        } else {
            Spacer(Modifier.width(36.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (!isCompact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        msg.authorName,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = AppColors.Primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(timeStr, fontSize = 10.sp, color = AppColors.TextMuted)
                }
                Spacer(Modifier.height(2.dp))
            }

            // Text content
            if (msg.content.isNotBlank()) {
                MessageContent(msg.content)
            }

            // Stickers
            msg.stickers.forEach { sticker ->
                StickerView(sticker)
            }

            // Attachments
            msg.attachments.forEach { att ->
                AttachmentView(att)
            }

            // Embeds
            msg.embeds.forEach { embed ->
                EmbedView(embed)
            }

            // Reactions
            if (msg.reactions.isNotEmpty()) {
                ReactionRow(msg.reactions)
            }
        }
    }
}

@Composable
private fun MessageContent(content: String) {
    // Simple markdown-like rendering
    val lines = content.split("\n")
    Column {
        lines.forEachIndexed { idx, line ->
            when {
                line.startsWith("```") -> {
                    // Code block
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppColors.Background)
                            .padding(8.dp)
                    ) {
                        Text(
                            line.removePrefix("```").removeSuffix("```"),
                            fontSize   = 12.sp,
                            color      = AppColors.TextPrimary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
                line.startsWith("> ") -> {
                    Row {
                        Box(Modifier.width(3.dp).heightIn(min = 18.dp).background(AppColors.Primary.copy(0.7f), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(line.removePrefix("> "), fontSize = 14.sp, color = AppColors.TextSecondary, fontStyle = FontStyle.Italic)
                    }
                }
                else -> {
                    Text(
                        buildAnnotatedString { appendStyledText(line) },
                        fontSize = 14.sp,
                        color    = AppColors.TextPrimary,
                        lineHeight = 20.sp
                    )
                }
            }
            if (idx < lines.size - 1) Spacer(Modifier.height(1.dp))
        }
    }
}

private fun AnnotatedString.Builder.appendStyledText(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text.startsWith("*", i) && !text.startsWith("**", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1 && !text.startsWith("**", end)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(
                        fontFamily  = androidx.compose.ui.text.font.FontFamily.Monospace,
                        background  = AppColors.Background,
                        color       = AppColors.TextPrimary,
                        fontSize    = 12.sp
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text.startsWith("__", i) -> {
                val end = text.indexOf("__", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

@Composable
private fun EmbedView(embed: MessageEmbed) {
    val borderColor = if (embed.color != null) {
        val r = (embed.color shr 16) and 0xFF
        val g = (embed.color shr 8)  and 0xFF
        val b = embed.color and 0xFF
        Color(r, g, b)
    } else AppColors.Primary.copy(0.5f)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.SurfaceVar)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(borderColor))
        Column(
            Modifier
                .weight(1f)
                .padding(10.dp)
        ) {
            // Author
            if (embed.authorName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (embed.authorIcon != null) {
                        AsyncImage(
                            model = embed.authorIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(embed.authorName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary)
                }
                Spacer(Modifier.height(4.dp))
            }
            // Title
            if (embed.title != null) {
                Text(embed.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary)
                Spacer(Modifier.height(2.dp))
            }
            // Description
            if (embed.description != null) {
                Text(embed.description, fontSize = 13.sp, color = AppColors.TextSecondary, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
            }
            // Fields
            if (embed.fields.isNotEmpty()) {
                // Inline fields in rows
                var i = 0
                while (i < embed.fields.size) {
                    val f = embed.fields[i]
                    if (f.inline && i + 1 < embed.fields.size && embed.fields[i + 1].inline) {
                        Row(Modifier.fillMaxWidth()) {
                            EmbedFieldView(f, Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            EmbedFieldView(embed.fields[i + 1], Modifier.weight(1f))
                        }
                        i += 2
                    } else {
                        EmbedFieldView(f, Modifier.fillMaxWidth())
                        i++
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            // Image
            if (embed.imageUrl != null) {
                Spacer(Modifier.height(6.dp))
                AsyncImage(
                    model = embed.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).heightIn(max = 300.dp),
                    contentScale = ContentScale.FillWidth
                )
            }
            // Thumbnail (right side — simplified to bottom)
            if (embed.thumbnailUrl != null && embed.imageUrl == null) {
                Spacer(Modifier.height(4.dp))
                AsyncImage(
                    model = embed.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(4.dp)).align(Alignment.End),
                    contentScale = ContentScale.Crop
                )
            }
            // Footer
            if (embed.footerText != null) {
                Spacer(Modifier.height(6.dp))
                Text(embed.footerText, fontSize = 11.sp, color = AppColors.TextMuted)
            }
        }
    }
}

@Composable
private fun EmbedFieldView(field: EmbedField, modifier: Modifier) {
    Column(modifier) {
        Text(field.name,  fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Text(field.value, fontSize = 12.sp, color = AppColors.TextSecondary)
    }
}

@Composable
private fun AttachmentView(att: MessageAttachment) {
    when {
        att.isImage -> {
            AsyncImage(
                model = att.proxyUrl.ifEmpty { att.url },
                contentDescription = att.filename,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.FillWidth
            )
        }
        att.isVideo -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceVar)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.VideoFile, null, tint = AppColors.Primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(att.filename, fontSize = 13.sp, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatFileSize(att.size), fontSize = 11.sp, color = AppColors.TextMuted)
                }
            }
        }
        att.isAudio -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceVar)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.AudioFile, null, tint = AppColors.Success, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(att.filename, fontSize = 13.sp, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatFileSize(att.size), fontSize = 11.sp, color = AppColors.TextMuted)
                }
            }
        }
        else -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceVar)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.AttachFile, null, tint = AppColors.TextMuted, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(att.filename, fontSize = 13.sp, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatFileSize(att.size), fontSize = 11.sp, color = AppColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun StickerView(sticker: StickerItem) {
    AsyncImage(
        model = sticker.url(),
        contentDescription = sticker.name,
        modifier = Modifier
            .size(160.dp)
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun ReactionRow(reactions: List<MessageReaction>) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { reaction ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (reaction.me) AppColors.Primary.copy(0.2f)
                        else AppColors.SurfaceVar
                    )
                    .border(
                        1.dp,
                        if (reaction.me) AppColors.Primary.copy(0.5f) else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (reaction.emojiUrl != null) {
                    AsyncImage(
                        model = reaction.emojiUrl,
                        contentDescription = reaction.emoji,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(reaction.emoji, fontSize = 14.sp)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    reaction.count.toString(),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (reaction.me) AppColors.Primary else AppColors.TextSecondary
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024       -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else               -> "${bytes / (1024 * 1024)} MB"
    }
}
