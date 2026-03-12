package com.discordcall

import androidx.compose.animation.*
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
fun GuildPickerScreen(
    vm: AppViewModel,
    onGuildSelected: (DiscordGuild) -> Unit,
    footerClicks: Int = 0,
    onFooterClick: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(vm.guilds, searchQuery) {
        if (searchQuery.isEmpty()) vm.guilds
        else vm.guilds.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppColors.Primary.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Call, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Discord Call", fontSize = 18.sp, fontWeight = FontWeight.Black, color = AppColors.TextPrimary)
                        Text("Selecione um servidor", fontSize = 12.sp, color = AppColors.TextMuted)
                    }
                    val user = vm.currentUser
                    if (user != null) {
                        AnimatedImage(
                            url = user.avatarUrl(64),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("Buscar servidor...", color = AppColors.TextMuted) },
                    leadingIcon   = { Icon(Icons.Outlined.Search, null, tint = AppColors.TextMuted) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AppColors.Primary,
                        unfocusedBorderColor = AppColors.Divider,
                        focusedTextColor     = AppColors.TextPrimary,
                        unfocusedTextColor   = AppColors.TextPrimary,
                        cursorColor          = AppColors.Primary
                    ),
                    shape  = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }

        if (vm.loadingGuilds) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppColors.Primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Carregando servidores...", color = AppColors.TextMuted)
                }
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Groups, null, tint = AppColors.TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nenhum servidor encontrado", color = AppColors.TextMuted)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding    = PaddingValues(vertical = 8.dp)
            ) {
                items(filtered) { guild ->
                    GuildRow(guild = guild, onClick = { onGuildSelected(guild) })
                }
            }
        }
    }
}

@Composable
private fun GuildRow(guild: DiscordGuild, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconUrl = guild.iconUrl(128)
        if (iconUrl != null) {
            AnimatedImage(
                url = iconUrl,
                contentDescription = null,
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp))
            )
        } else {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.Primary.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    guild.name.take(2).uppercase(),
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Black,
                    color      = AppColors.Primary
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            guild.name,
            fontSize   = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color      = AppColors.TextPrimary,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.weight(1f)
        )
        Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ChannelPickerScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onChannelSelected: (VoiceChannel) -> Unit
) {
    val guild = vm.selectedGuild ?: return
    var searchQuery by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.TextPrimary)
                }
                val iconUrl = guild.iconUrl(64)
                if (iconUrl != null) {
                    AnimatedImage(iconUrl, null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                } else {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.Primary.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(guild.name.take(1), fontSize = 14.sp, fontWeight = FontWeight.Black, color = AppColors.Primary)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(guild.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Canais de voz", fontSize = 11.sp, color = AppColors.TextMuted)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Buscar canal...", color = AppColors.TextMuted) },
                leadingIcon   = { Icon(Icons.Outlined.Search, null, tint = AppColors.TextMuted) },
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AppColors.Primary,
                    unfocusedBorderColor = AppColors.Divider,
                    focusedTextColor     = AppColors.TextPrimary,
                    unfocusedTextColor   = AppColors.TextPrimary,
                    cursorColor          = AppColors.Primary
                ),
                shape      = RoundedCornerShape(12.dp),
                singleLine = true
            )
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

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            vm.categories.forEach { cat ->
                val filteredChannels = if (searchQuery.isEmpty()) cat.channels
                else cat.channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
                if (filteredChannels.isEmpty()) return@forEach

                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.ExpandMore, null, tint = AppColors.TextMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            cat.name.uppercase(),
                            fontSize     = 11.sp,
                            fontWeight   = FontWeight.Bold,
                            color        = AppColors.TextMuted,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                items(filteredChannels) { channel ->
                    val channelVoiceStates = vm.voiceStates.filter { it.channelId == channel.id }
                    val channelPerms       = vm.computeChannelPermissions(channel)
                    val canView            = DiscordPermissions.has(channelPerms, DiscordPermissions.VIEW_CHANNEL)
                    val canConnect         = DiscordPermissions.has(channelPerms, DiscordPermissions.CONNECT)

                    ChannelRow(
                        channel    = channel,
                        voiceStates= channelVoiceStates,
                        canView    = canView,
                        canConnect = canConnect,
                        onClick    = { onChannelSelected(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: VoiceChannel,
    voiceStates: List<VoiceState>,
    canView: Boolean,
    canConnect: Boolean,
    onClick: () -> Unit
) {
    val hasUsers = voiceStates.isNotEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .then(if (canView) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (hasUsers) AppColors.Surface.copy(0.7f) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (channel.isStageChannel) Icons.Outlined.RecordVoiceOver else Icons.Outlined.VolumeUp,
                null,
                tint     = if (!canView) AppColors.TextMuted.copy(0.4f) else if (hasUsers) AppColors.Primary else AppColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    fontSize   = 14.sp,
                    fontWeight = if (hasUsers) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (!canView) AppColors.TextMuted.copy(0.4f) else AppColors.TextPrimary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                if (channel.userLimit > 0) {
                    Text(
                        "${voiceStates.size}/${channel.userLimit}",
                        fontSize = 11.sp,
                        color    = AppColors.TextMuted
                    )
                }
            }

            if (!canConnect && canView) {
                Icon(Icons.Outlined.Lock, null, tint = AppColors.TextMuted, modifier = Modifier.size(14.dp))
            }

            if (hasUsers) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    voiceStates.take(3).forEach { vs ->
                        AnimatedImage(
                            url = vs.avatarUrl(32),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).clip(CircleShape).border(1.5.dp, AppColors.Background, CircleShape)
                        )
                    }
                    if (voiceStates.size > 3) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(AppColors.SurfaceVar).border(1.5.dp, AppColors.Background, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+${voiceStates.size - 3}", fontSize = 8.sp, color = AppColors.TextMuted)
                        }
                    }
                }
            }
        }
    }
}
