package com.discordcall

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

@Composable
fun GuildChannelScreen(
    vm:        AppViewModel,
    guild:     DiscordGuild,
    onBack:    () -> Unit,
    onChannel: (VoiceChannel) -> Unit
) {
    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(AppColors.Surface)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.TextPrimary)
            }
            val iconUrl = guild.iconUrl(64)
            if (iconUrl != null) {
                AnimatedImage(
                    url = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                guild.name,
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold,
                color      = AppColors.TextPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.loadGuilds() }) {
                Icon(Icons.Outlined.Refresh, null, tint = AppColors.TextMuted)
            }
        }

        if (vm.loadingChannels) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppColors.Primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Carregando canais...", color = AppColors.TextMuted)
                }
            }
            return
        }

        if (vm.categories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.VolumeOff, null, tint = AppColors.TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nenhum canal de voz encontrado", color = AppColors.TextMuted)
                }
            }
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            vm.categories.forEach { category ->
                val voiceChannels = category.channels.filter { it.type == 2 || it.type == 13 }
                if (voiceChannels.isEmpty()) return@forEach

                item(key = "cat_${category.id}") {
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            null,
                            tint     = AppColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            category.name.uppercase(),
                            fontSize      = 11.sp,
                            fontWeight    = FontWeight.Bold,
                            color         = AppColors.TextMuted,
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                items(voiceChannels, key = { it.id }) { channel ->
                    VoiceChannelRow(
                        channel      = channel,
                        voiceStates  = vm.voiceStates.filter { it.channelId == channel.id },
                        isSelected   = vm.selectedChannel?.id == channel.id,
                        onClick      = { onChannel(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceChannelRow(
    channel:     VoiceChannel,
    voiceStates: List<VoiceState>,
    isSelected:  Boolean,
    onClick:     () -> Unit
) {
    val hasUsers = voiceStates.isNotEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AppColors.Primary.copy(0.15f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (channel.isStageChannel) Icons.Outlined.RecordVoiceOver else Icons.Outlined.VolumeUp,
                null,
                tint     = if (isSelected) AppColors.Primary else AppColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                channel.name,
                fontSize   = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (isSelected) AppColors.TextPrimary else AppColors.TextSecondary,
                modifier   = Modifier.weight(1f),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            if (channel.userLimit > 0) {
                Text(
                    "${voiceStates.size}/${channel.userLimit}",
                    fontSize = 11.sp,
                    color    = if (voiceStates.size >= channel.userLimit) AppColors.Error else AppColors.TextMuted
                )
            } else if (hasUsers) {
                Text(
                    "${voiceStates.size}",
                    fontSize = 11.sp,
                    color    = AppColors.Success
                )
            }
        }

        // Show users in channel
        if (hasUsers) {
            Spacer(Modifier.height(4.dp))
            voiceStates.forEach { vs ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedImage(
                        url = vs.avatarUrl(32),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        vs.username,
                        fontSize = 12.sp,
                        color    = if (vs.speaking) AppColors.Success else AppColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (vs.selfMute || vs.serverMute)
                            Icon(Icons.Outlined.MicOff, null, tint = AppColors.Error, modifier = Modifier.size(10.dp))
                        if (vs.selfDeaf || vs.serverDeaf)
                            Icon(Icons.Outlined.HeadsetOff, null, tint = AppColors.Error, modifier = Modifier.size(10.dp))
                        if (vs.selfVideo)
                            Icon(Icons.Outlined.Videocam, null, tint = AppColors.Primary, modifier = Modifier.size(10.dp))
                    }
                }
            }
        }
    }
}
