package com.discordcall

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActiveCallScreen(
    vm: AppViewModel,
    onHangup: () -> Unit
) {
    val channel   = vm.selectedChannel ?: return
    val channelUsers = vm.voiceStates.filter { it.channelId == channel.id }
    val channelPerms = vm.computeChannelPermissions(channel)
    val canSpeak     = DiscordPermissions.has(channelPerms, DiscordPermissions.SPEAK)
    val canVideo     = DiscordPermissions.has(channelPerms, DiscordPermissions.VIDEO)
    val canStream    = DiscordPermissions.has(channelPerms, DiscordPermissions.STREAM)

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showControls      by remember { mutableStateOf(true) }
    var showInvite        by remember { mutableStateOf(false) }

    LaunchedEffect(vm.isInCall) {
        if (vm.isInCall) {
            CallService.start(
                getApplication(),
                channel.name
            )
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.CallBg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(channel = channel, vm = vm, onShowSettings = { showSettingsSheet = true })

            Row(Modifier.weight(1f)) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (channelUsers.isEmpty()) {
                        EmptyCallView()
                    } else {
                        ParticipantGrid(
                            users      = channelUsers,
                            myUserId   = vm.currentUser?.id ?: "",
                            onTap      = { showControls = !showControls }
                        )
                    }
                }
                if (vm.showChat) {
                    ChatPanel(vm = vm)
                }
            }

            AnimatedVisibility(
                visible = showControls,
                enter   = slideInVertically(initialOffsetY = { it }),
                exit    = slideOutVertically(targetOffsetY = { it })
            ) {
                BottomControls(
                    vm           = vm,
                    canSpeak     = canSpeak,
                    canVideo     = canVideo,
                    canStream    = canStream,
                    onHangup     = onHangup,
                    onShowInvite = { showInvite = true },
                    onToggleChat = { vm.showChat = !vm.showChat }
                )
            }
        }

        if (showSettingsSheet) {
            CallSettingsSheet(vm = vm, onDismiss = { showSettingsSheet = false })
        }

        if (showInvite) {
            InviteSheet(vm = vm, onDismiss = { showInvite = false })
        }
    }
}

@Composable
private fun TopBar(channel: VoiceChannel, vm: AppViewModel, onShowSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppColors.CallBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.VolumeUp, null, tint = AppColors.Success, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(channel.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, maxLines = 1)
                Text(vm.selectedGuild?.name ?: "", fontSize = 11.sp, color = AppColors.TextMuted, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { vm.showChat = !vm.showChat }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Chat, null, tint = if (vm.showChat) AppColors.Primary else AppColors.TextMuted, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onShowSettings, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Settings, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun EmptyCallView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AppColors.Primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text("Conectando...", color = AppColors.TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ParticipantGrid(
    users: List<VoiceState>,
    myUserId: String,
    onTap: () -> Unit
) {
    val cols = when {
        users.size <= 1 -> 1
        users.size <= 4 -> 2
        else            -> 3
    }

    LazyVerticalGrid(
        columns      = GridCells.Fixed(cols),
        modifier     = Modifier.fillMaxSize().clickable { onTap() },
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement   = Arrangement.spacedBy(4.dp)
    ) {
        items(users) { vs ->
            ParticipantTile(vs = vs, isMe = vs.userId == myUserId)
        }
    }
}

@Composable
private fun ParticipantTile(vs: VoiceState, isMe: Boolean) {
    val speakingScale by animateFloatAsState(
        targetValue  = if (vs.speaking) 1.02f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label        = "speak"
    )
    Box(
        Modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Surface)
            .then(if (vs.speaking) Modifier.border(2.dp, AppColors.Success, RoundedCornerShape(10.dp)) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        UserAvatar(url = vs.avatarUrl(128), size = 64.dp, speaking = vs.speaking)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f))),
                    RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isMe) "${vs.username} (você)" else vs.username,
                    fontSize   = 12.sp,
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
                if (vs.selfMute || vs.serverMute) Icon(Icons.Outlined.MicOff, null, tint = AppColors.Error, modifier = Modifier.size(12.dp))
                if (vs.selfDeaf || vs.serverDeaf) Spacer(Modifier.width(2.dp)).also { Icon(Icons.Outlined.HeadsetOff, null, tint = AppColors.Error, modifier = Modifier.size(12.dp)) }
                if (vs.selfStream) {
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.background(AppColors.Error.copy(0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("LIVE", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomControls(
    vm: AppViewModel,
    canSpeak: Boolean,
    canVideo: Boolean,
    canStream: Boolean,
    onHangup: () -> Unit,
    onShowInvite: () -> Unit,
    onToggleChat: () -> Unit
) {
    var showMore by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Surface)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Divider))

        AnimatedVisibility(visible = showMore) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.SurfaceVar)
                    .padding(16.dp)
            ) {
                Text("Mais opções", fontSize = 11.sp, color = AppColors.TextMuted, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CallControlButton(
                        icon    = Icons.Outlined.PersonAdd,
                        label   = "Convidar",
                        onClick = onShowInvite,
                        modifier = Modifier.weight(1f)
                    )
                    if (canStream) {
                        CallControlButton(
                            icon    = Icons.Outlined.ScreenShare,
                            label   = "Transmissão",
                            onClick = { },
                            active  = !vm.isScreenSharing,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    CallControlButton(
                        icon    = Icons.Outlined.FiberManualRecord,
                        label   = "Gravar",
                        onClick = { },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            if (canSpeak) {
                CallControlButton(
                    icon        = if (vm.isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                    label       = if (vm.isMuted) "Desmutado" else "Mudo",
                    onClick     = { vm.toggleMute() },
                    active      = !vm.isMuted,
                    inactiveColor = AppColors.SurfaceVar
                )
            }
            CallControlButton(
                icon        = if (vm.isDeafened) Icons.Outlined.HeadsetOff else Icons.Outlined.Headset,
                label       = if (vm.isDeafened) "Surdo" else "Ouvindo",
                onClick     = { vm.toggleDeafen() },
                active      = !vm.isDeafened,
                inactiveColor = AppColors.SurfaceVar
            )
            if (canVideo) {
                CallControlButton(
                    icon    = if (vm.isCameraOn) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
                    label   = "Câmera",
                    onClick = { vm.isCameraOn = !vm.isCameraOn },
                    active  = vm.isCameraOn,
                    activeColor = AppColors.Surface
                )
            }
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AppColors.Error)
                    .clickable { onHangup() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.CallEnd, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            CallControlButton(
                icon    = Icons.Outlined.ExpandLess,
                label   = "Mais",
                onClick = { showMore = !showMore },
                active  = showMore,
                activeColor = AppColors.Primary
            )
        }
    }
}

@Composable
private fun ChatPanel(vm: AppViewModel) {
    var messageText by remember { mutableStateOf("") }
    val listState   = rememberLazyListState()

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    Column(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(AppColors.Background)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Chat, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Chat", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.showChat = false }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Close, null, tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (vm.loadingMessages) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary, modifier = Modifier.size(24.dp))
            }
        } else if (vm.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Chat, null, tint = AppColors.TextMuted, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Sem mensagens ainda", fontSize = 12.sp, color = AppColors.TextMuted)
                }
            }
        } else {
            LazyColumn(
                state          = listState,
                modifier       = Modifier.weight(1f).padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(vm.messages) { msg ->
                    MessageBubble(msg)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value         = messageText,
                    onValueChange = { messageText = it },
                    modifier      = Modifier.weight(1f),
                    placeholder   = { Text("Mensagem...", fontSize = 12.sp, color = AppColors.TextMuted) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AppColors.Primary,
                        unfocusedBorderColor = AppColors.Divider,
                        focusedTextColor     = AppColors.TextPrimary,
                        unfocusedTextColor   = AppColors.TextPrimary,
                        cursorColor          = AppColors.Primary
                    ),
                    shape      = RoundedCornerShape(20.dp),
                    singleLine = false,
                    maxLines   = 4,
                    textStyle  = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (messageText.isNotBlank()) {
                            vm.sendMessage(messageText.trim())
                            messageText = ""
                        }
                    })
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick  = {
                        if (messageText.isNotBlank()) {
                            vm.sendMessage(messageText.trim())
                            messageText = ""
                        }
                    },
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(
                        if (messageText.isNotBlank()) AppColors.Primary else AppColors.SurfaceVar
                    )
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, null, tint = if (messageText.isNotBlank()) Color.White else AppColors.TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: Message) {
    val time = remember(msg.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AnimatedImage(
            url = msg.authorAvatarUrl(32),
            contentDescription = null,
            modifier = Modifier.size(24.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(msg.authorName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Primary)
                Spacer(Modifier.width(4.dp))
                Text(time, fontSize = 9.sp, color = AppColors.TextMuted)
            }
            if (msg.content.isNotBlank()) {
                Text(msg.content, fontSize = 12.sp, color = AppColors.TextSecondary, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun CallSettingsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(AppColors.Surface)
                .clickable {}
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Configurações de Chamada", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, null, tint = AppColors.TextMuted) }
            }
            Spacer(Modifier.height(16.dp))

            SettingsSectionTitle("Áudio")
            SettingsToggleRow("Supressão de ruído", "Reduz ruído de fundo", vm.callSettings.noiseSuppression) { vm.callSettings = vm.callSettings.copy(noiseSuppression = it) }
            SettingsToggleRow("Cancelamento de eco", "Evita feedback de áudio", vm.callSettings.echoCancellation) { vm.callSettings = vm.callSettings.copy(echoCancellation = it) }
            SettingsToggleRow("Controle de ganho automático", "Normaliza o volume", vm.callSettings.autoGainControl) { vm.callSettings = vm.callSettings.copy(autoGainControl = it) }
            SettingsToggleRow("Áudio estéreo", "Qualidade de áudio melhorada", vm.callSettings.stereoAudio) { vm.callSettings = vm.callSettings.copy(stereoAudio = it) }

            Spacer(Modifier.height(16.dp))
            SettingsSectionTitle("Vídeo")
            Text("Qualidade de vídeo", fontSize = 14.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoQuality.values().forEach { q ->
                    FilterChip(
                        selected = vm.videoQuality == q,
                        onClick  = { vm.videoQuality = q },
                        label    = { Text(q.label, fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Primary,
                            selectedLabelColor     = Color.White,
                            containerColor         = AppColors.SurfaceVar,
                            labelColor             = AppColors.TextSecondary
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SettingsSectionTitle("Interface")
            SettingsToggleRow("Overlay flutuante", "Mostra bolinha ao minimizar", vm.callSettings.overlayEnabled) {
                vm.callSettings = vm.callSettings.copy(overlayEnabled = it)
                vm.overlayEnabled = it
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = AppColors.TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = AppColors.TextMuted)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onChange,
            colors          = SwitchDefaults.colors(checkedThumbColor = AppColors.Primary, checkedTrackColor = AppColors.Primary.copy(0.3f))
        )
    }
}

@Composable
private fun InviteSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val channel = vm.selectedChannel ?: return
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(AppColors.Surface).clickable {}.padding(20.dp)
        ) {
            Text("Convidar para ${channel.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Copie o link abaixo e compartilhe com seus amigos.", fontSize = 13.sp, color = AppColors.TextMuted)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().background(AppColors.SurfaceVar, RoundedCornerShape(10.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("discord.gg/invite", fontSize = 13.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                Icon(Icons.Outlined.ContentCopy, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Fechar", color = AppColors.TextMuted)
            }
        }
    }
}

private fun rememberScrollState() = androidx.compose.foundation.rememberScrollState()
private fun Modifier.horizontalScroll(state: androidx.compose.foundation.ScrollState) = this.then(
    androidx.compose.foundation.Modifier.horizontalScroll(state)
)

private val FontFamily.Companion.Monospace get() = androidx.compose.ui.text.font.FontFamily.Monospace
