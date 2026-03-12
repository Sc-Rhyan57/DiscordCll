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
fun ChannelLobbyScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onJoin: () -> Unit
) {
    val channel = vm.selectedChannel ?: return
    val guild   = vm.selectedGuild   ?: return

    val channelPerms   = vm.computeChannelPermissions(channel)
    val canConnect     = DiscordPermissions.has(channelPerms, DiscordPermissions.CONNECT)
    val canSpeak       = DiscordPermissions.has(channelPerms, DiscordPermissions.SPEAK)
    val canVideo       = DiscordPermissions.has(channelPerms, DiscordPermissions.VIDEO)
    val channelUsers   = vm.voiceStates.filter { it.channelId == channel.id }
    val isEmpty        = channelUsers.isEmpty()

    val scale = remember { Animatable(0.95f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) }

    Column(Modifier.fillMaxSize().background(AppColors.CallBg)) {
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
                Icon(
                    if (channel.isStageChannel) Icons.Outlined.RecordVoiceOver else Icons.Outlined.VolumeUp,
                    null,
                    tint     = AppColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    channel.name,
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color      = AppColors.TextPrimary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (isEmpty) {
                EmptyChannelView(channel = channel, canConnect = canConnect, onJoin = onJoin)
            } else {
                OccupiedChannelView(
                    channel    = channel,
                    users      = channelUsers,
                    canConnect = canConnect,
                    onJoin     = onJoin
                )
            }
        }
    }
}

@Composable
private fun EmptyChannelView(
    channel: VoiceChannel,
    canConnect: Boolean,
    onJoin: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pScale by pulse.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "ps")

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(AppColors.Surface)
                .graphicsLayer { scaleX = pScale; scaleY = pScale },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.VolumeUp, null, tint = AppColors.TextMuted, modifier = Modifier.size(40.dp))
        }

        Spacer(Modifier.height(24.dp))
        Text("Não tem ninguém aqui ainda", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Canais de voz servem para passar o tempo. Quando estiver pronto(a) para falar é só entrar. Seus amigos vão poder te ver e se juntar a você.",
            fontSize  = 14.sp,
            color     = AppColors.TextSecondary,
            lineHeight = 20.sp
        )

        Spacer(Modifier.weight(1f))

        if (canConnect) {
            Button(
                onClick  = onJoin,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AppColors.Success),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.VolumeUp, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Entrar na chamada de voz", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick  = onJoin,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextSecondary),
                border   = BorderStroke(1.dp, AppColors.Divider),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.MicOff, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Entrar silenciado(a)", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
        } else {
            Button(
                onClick  = {},
                enabled  = false,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(disabledContainerColor = AppColors.Surface),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Canal bloqueado", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = {},
                enabled  = false,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(disabledContainerColor = AppColors.Surface),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.MicOff, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Canal bloqueado", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OccupiedChannelView(
    channel: VoiceChannel,
    users: List<VoiceState>,
    canConnect: Boolean,
    onJoin: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "${users.size} participante${if (users.size != 1) "s" else ""}",
                        fontSize   = 13.sp,
                        color      = AppColors.TextMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (users.any { it.selfStream }) {
                        Row(
                            Modifier
                                .background(AppColors.Error.copy(0.15f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(AppColors.Error))
                            Spacer(Modifier.width(5.dp))
                            Text("AO VIVO", fontSize = 10.sp, color = AppColors.Error, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            items(users) { vs ->
                VoiceUserRow(vs)
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(AppColors.Surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.PersonAdd, null, tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Convidar amigos", fontSize = 15.sp, color = AppColors.TextMuted)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canConnect) {
                Button(
                    onClick  = onJoin,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AppColors.Success),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.VolumeUp, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Entrar na chamada de voz", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                OutlinedButton(
                    onClick  = { },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextSecondary),
                    border   = BorderStroke(1.dp, AppColors.Divider),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.MicOff, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Entrar silenciado(a)", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
            } else {
                Button(
                    onClick  = {},
                    enabled  = false,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Canal bloqueado", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Button(
                    onClick  = {},
                    enabled  = false,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.MicOff, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Canal bloqueado", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun VoiceUserRow(vs: VoiceState) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Surface.copy(0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(url = vs.avatarUrl(64), size = 40.dp, speaking = vs.speaking)
        Spacer(Modifier.width(12.dp))
        Text(
            vs.username,
            fontSize   = 15.sp,
            fontWeight = FontWeight.Medium,
            color      = AppColors.TextPrimary,
            modifier   = Modifier.weight(1f),
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (vs.selfMute || vs.serverMute) Icon(Icons.Outlined.MicOff, null, tint = AppColors.Error, modifier = Modifier.size(16.dp))
            if (vs.selfDeaf || vs.serverDeaf) Icon(Icons.Outlined.HeadsetOff, null, tint = AppColors.Error, modifier = Modifier.size(16.dp))
            if (vs.selfVideo) Icon(Icons.Outlined.Videocam, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
            if (vs.selfStream) {
                Row(
                    Modifier.background(AppColors.Error.copy(0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(AppColors.Error))
                    Spacer(Modifier.width(3.dp))
                    Text("LIVE", fontSize = 9.sp, color = AppColors.Error, fontWeight = FontWeight.Black)
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
