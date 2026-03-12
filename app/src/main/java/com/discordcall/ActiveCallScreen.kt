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
    val channel      = vm.selectedChannel ?: return
    val channelUsers = vm.voiceStates.filter { it.channelId == channel.id }
    val channelPerms = vm.computeChannelPermissions(channel)
    val canSpeak     = DiscordPermissions.has(channelPerms, DiscordPermissions.SPEAK)
    val canVideo     = DiscordPermissions.has(channelPerms, DiscordPermissions.VIDEO)

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showControls      by remember { mutableStateOf(true) }

    LaunchedEffect(vm.isInCall) {
        if (vm.isInCall) CallService.start(vm.getApp(), channel.name)
    }

    Box(Modifier.fillMaxSize().background(AppColors.CallBg)) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().background(AppColors.Surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                    IconButton(onClick = { vm.showChat = !vm.showChat }) {
                        Icon(Icons.Outlined.Chat, null, tint = if (vm.showChat) AppColors.Primary else AppColors.TextMuted)
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Outlined.Settings, null, tint = AppColors.TextMuted)
                    }
                }
            }

            Row(Modifier.weight(1f)) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (channelUsers.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AppColors.Primary, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Conectando...", color = AppColors.TextSecondary)
                            }
                        }
                    } else {
                        val cols = if (channelUsers.size <= 1) 1 else if (channelUsers.size <= 4) 2 else 3
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            modifier = Modifier.fillMaxSize().clickable { showControls = !showControls },
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(channelUsers) { vs ->
                                Box(
                                    Modifier
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AppColors.Surface)
                                        .then(if (vs.speaking) Modifier.border(2.dp, AppColors.Success, RoundedCornerShape(10.dp)) else Modifier),
                                    contentAlignment = Alignment.Center
                                ) {
                                    UserAvatar(url = vs.avatarUrl(128), size = 64.dp, speaking = vs.speaking)
                                    Row(
                                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f))))
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (vs.userId == vm.currentUser?.id) "${vs.username} (você)" else vs.username,
                                            fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        if (vs.selfMute || vs.serverMute)
                                            Icon(Icons.Outlined.MicOff, null, tint = AppColors.Error, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(visible = vm.showChat, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
                    ChatPanelInCall(vm = vm)
                }
            }

            AnimatedVisibility(
                visible = showControls,
                enter   = slideInVertically { it },
                exit    = slideOutVertically { it }
            ) {
                Row(
                    Modifier.fillMaxWidth().background(AppColors.Surface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    if (canSpeak) {
                        CallControlButton(
                            icon        = if (vm.isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                            label       = if (vm.isMuted) "Mudo" else "Mic",
                            active      = !vm.isMuted,
                            inactiveColor = AppColors.Error,
                            onClick     = { vm.toggleMute() }
                        )
                    }
                    CallControlButton(
                        icon       = if (vm.isDeafened) Icons.Outlined.HeadsetOff else Icons.Outlined.Headset,
                        label      = if (vm.isDeafened) "Surdo" else "Ouvindo",
                        active     = !vm.isDeafened,
                        inactiveColor = AppColors.Error,
                        onClick    = { vm.toggleDeafen() }
                    )
                    if (canVideo) {
                        CallControlButton(
                            icon    = if (vm.isCameraOn) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
                            label   = "Câmera",
                            active  = vm.isCameraOn,
                            activeColor = AppColors.Primary,
                            onClick = { vm.isCameraOn = !vm.isCameraOn }
                        )
                    }
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(AppColors.Error).clickable { onHangup() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.CallEnd, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    CallControlButton(
                        icon    = Icons.Outlined.Settings,
                        label   = "Config",
                        onClick = { showSettingsSheet = true }
                    )
                }
            }
        }

        if (showSettingsSheet) {
            VoiceSettingsSheet(vm = vm, onDismiss = { showSettingsSheet = false })
        }
    }
}

@Composable
private fun ChatPanelInCall(vm: AppViewModel) {
    var messageText by remember { mutableStateOf("") }
    val listState   = rememberLazyListState()

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    Column(
        Modifier.width(280.dp).fillMaxHeight().background(AppColors.Background)
    ) {
        Row(
            Modifier.fillMaxWidth().background(AppColors.Surface).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Chat", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.showChat = false }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Close, null, tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
            }
        }

        if (vm.loadingMessages) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary, modifier = Modifier.size(24.dp))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(vm.messages.size) { idx ->
                    val msg = vm.messages[idx]
                    val prev = vm.messages.getOrNull(idx - 1)
                    val isCompact = prev?.authorId == msg.authorId
                    DiscordMessageView(msg = msg, isCompact = isCompact)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(AppColors.Surface).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = messageText,
                onValueChange = { messageText = it },
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Mensagem...", fontSize = 12.sp, color = AppColors.TextMuted) },
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AppColors.Primary,
                    unfocusedBorderColor = AppColors.Divider,
                    focusedTextColor     = AppColors.TextPrimary,
                    unfocusedTextColor   = AppColors.TextPrimary
                ),
                shape      = RoundedCornerShape(20.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (messageText.isNotBlank()) { vm.sendMessage(messageText.trim()); messageText = "" }
                })
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick  = { if (messageText.isNotBlank()) { vm.sendMessage(messageText.trim()); messageText = "" } },
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(if (messageText.isNotBlank()) AppColors.Primary else AppColors.SurfaceVar)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, null, tint = if (messageText.isNotBlank()) Color.White else AppColors.TextMuted)
            }
        }
    }
}

@Composable
fun VoiceSettingsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(AppColors.Surface)
                .clickable {}
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Configurações de Voz", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, null, tint = AppColors.TextMuted) }
            }
            Spacer(Modifier.height(16.dp))

            Text("ÁUDIO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(8.dp))
            VoiceSettingToggle("Supressão de ruído", "Reduz ruído de fundo automaticamente", vm.callSettings.noiseSuppression) {
                vm.callSettings = vm.callSettings.copy(noiseSuppression = it)
            }
            VoiceSettingToggle("Cancelamento de eco", "Evita feedback de áudio", vm.callSettings.echoCancellation) {
                vm.callSettings = vm.callSettings.copy(echoCancellation = it)
            }
            VoiceSettingToggle("Controle de ganho", "Normaliza seu volume automaticamente", vm.callSettings.autoGainControl) {
                vm.callSettings = vm.callSettings.copy(autoGainControl = it)
            }
            VoiceSettingToggle("Áudio estéreo", "Qualidade melhorada (usa mais bateria)", vm.callSettings.stereoAudio) {
                vm.callSettings = vm.callSettings.copy(stereoAudio = it)
            }

            Spacer(Modifier.height(16.dp))
            Text("STATUS DE VOZ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusInfoChip("SSRC", "Em chamada", Modifier.weight(1f))
                StatusInfoChip("Modo", "xchacha20", Modifier.weight(1f))
                StatusInfoChip("Codec", "Opus 48kHz", Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))
            Text("VÍDEO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoQuality.values().forEach { q ->
                    FilterChip(
                        selected = vm.videoQuality == q,
                        onClick  = { vm.videoQuality = q },
                        label    = { Text(q.label) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Primary,
                            selectedLabelColor     = Color.White,
                            containerColor         = AppColors.SurfaceVar,
                            labelColor             = AppColors.TextSecondary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceSettingToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = AppColors.TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = AppColors.TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AppColors.Primary, checkedTrackColor = AppColors.Primary.copy(0.3f))
        )
    }
}

@Composable
private fun StatusInfoChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(AppColors.SurfaceVar).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = AppColors.TextMuted)
        Text(value, fontSize = 11.sp, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

private val rememberScrollState = @Composable { androidx.compose.foundation.rememberScrollState() }
