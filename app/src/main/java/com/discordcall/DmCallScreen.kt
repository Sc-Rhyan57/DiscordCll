package com.discordcall

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*

@Composable
fun IncomingCallOverlay(
    call: IncomingCall,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.12f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.Surface)
                .padding(32.dp)
        ) {
            Text(
                if (call.isGroup) "Chamada em grupo" else "Chamada recebida",
                fontSize = 13.sp,
                color    = AppColors.TextMuted,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(20.dp))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(104.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(AppColors.Primary.copy(0.15f))
                )
                AnimatedImage(
                    url = call.callerAvatarUrl(128),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp).clip(CircleShape)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                if (call.isGroup) (call.groupName ?: "Grupo") else call.callerName,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Black,
                color      = AppColors.TextPrimary,
                textAlign  = TextAlign.Center
            )
            if (call.isGroup) {
                Spacer(Modifier.height(4.dp))
                Text(call.callerName, fontSize = 13.sp, color = AppColors.TextMuted)
            }

            Spacer(Modifier.height(36.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick  = onDecline,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AppColors.Error)
                    ) {
                        Icon(Icons.Outlined.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Recusar", fontSize = 12.sp, color = AppColors.TextMuted)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick  = onAnswer,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AppColors.Success)
                    ) {
                        Icon(Icons.Outlined.Call, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Atender", fontSize = 12.sp, color = AppColors.TextMuted)
                }
            }
        }
    }
}

@Composable
fun DmCallScreen(
    vm: AppViewModel,
    dm: DmChannel,
    onHangUp: () -> Unit
) {
    val callState = vm.dmCallState
    val participants = vm.dmVoiceStates

    var showChat by remember { mutableStateOf(false) }

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
            IconButton(
                onClick  = onHangUp,
                modifier = Modifier.align(Alignment.CenterStart).size(36.dp)
            ) {
                Icon(Icons.Outlined.ArrowBackIos, null, tint = AppColors.TextMuted)
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dm.displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(
                        when (callState) {
                            DmCallState.ACTIVE      -> AppColors.Success
                            DmCallState.RINGING_OUT -> AppColors.Warning
                            DmCallState.RINGING_IN  -> AppColors.Warning
                            DmCallState.IDLE        -> AppColors.TextMuted
                        }
                    ))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        when (callState) {
                            DmCallState.ACTIVE      -> "Em chamada"
                            DmCallState.RINGING_OUT -> "Chamando…"
                            DmCallState.RINGING_IN  -> "Recebendo…"
                            DmCallState.IDLE        -> "Aguardando"
                        },
                        fontSize = 12.sp,
                        color    = AppColors.TextMuted
                    )
                }
            }
            IconButton(
                onClick  = { showChat = !showChat },
                modifier = Modifier.align(Alignment.CenterEnd).size(36.dp)
                    .clip(CircleShape)
                    .background(if (showChat) AppColors.Primary.copy(0.2f) else Color.Transparent)
            ) {
                Icon(Icons.Outlined.Chat, null, tint = if (showChat) AppColors.Primary else AppColors.TextMuted)
            }
        }

        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f)) {
                when {
                    callState == DmCallState.RINGING_OUT -> RingingOutView(dm)
                    participants.isEmpty() -> WaitingView(dm)
                    else -> ParticipantsGrid(
                        participants    = participants,
                        currentUserId   = vm.currentUser?.id ?: "",
                        currentSpeaker  = vm.currentSpeakerId
                    )
                }
            }

            AnimatedVisibility(
                visible = showChat,
                enter   = slideInHorizontally { it },
                exit    = slideOutHorizontally { it }
            ) {
                Box(
                    Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .background(AppColors.Surface)
                ) {
                    ChatPanel(
                        messages        = vm.messages,
                        currentUserId   = vm.currentUser?.id ?: "",
                        loadingMessages = vm.loadingMessages,
                        onSend          = vm::sendMessage
                    )
                }
            }
        }

        DmCallControls(
            vm       = vm,
            onHangUp = onHangUp
        )
    }
}

@Composable
private fun RingingOutView(dm: DmChannel) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.6f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "r1"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.6f,
        animationSpec = infiniteRepeatable(tween(1200, delayMillis = 400), RepeatMode.Restart),
        label = "r2"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(100.dp).scale(ring1).clip(CircleShape).background(AppColors.Primary.copy(0.08f)))
                Box(Modifier.size(100.dp).scale(ring2).clip(CircleShape).background(AppColors.Primary.copy(0.05f)))
                val iconUrl = dm.iconUrl(128)
                if (iconUrl != null) {
                    AnimatedImage(url = iconUrl, contentDescription = null, modifier = Modifier.size(88.dp).clip(CircleShape))
                } else {
                    Box(
                        Modifier.size(88.dp).clip(CircleShape).background(AppColors.Primary.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(dm.displayName.take(2).uppercase(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = AppColors.Primary)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(dm.displayName, fontSize = 22.sp, fontWeight = FontWeight.Black, color = AppColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text("Chamando…", fontSize = 14.sp, color = AppColors.TextMuted)
        }
    }
}

@Composable
private fun WaitingView(dm: DmChannel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val iconUrl = dm.iconUrl(128)
            if (iconUrl != null) {
                AnimatedImage(url = iconUrl, contentDescription = null, modifier = Modifier.size(80.dp).clip(CircleShape))
            } else {
                Box(
                    Modifier.size(80.dp).clip(CircleShape).background(AppColors.SurfaceVar),
                    contentAlignment = Alignment.Center
                ) {
                    Text(dm.displayName.take(2).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Black, color = AppColors.Primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(dm.displayName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text("Aguardando entrar…", fontSize = 13.sp, color = AppColors.TextMuted)
        }
    }
}

@Composable
private fun ParticipantsGrid(
    participants: List<VoiceState>,
    currentUserId: String,
    currentSpeaker: String?
) {
    val cols = when {
        participants.size <= 2 -> 1
        participants.size <= 4 -> 2
        else                   -> 3
    }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns   = androidx.compose.foundation.lazy.grid.GridCells.Fixed(cols),
        modifier  = Modifier.fillMaxSize().padding(8.dp)
    ) {
        items(participants.size) { idx ->
            val vs = participants[idx]
            CallParticipantTile(
                vs            = vs,
                isSelf        = vs.userId == currentUserId,
                isSpeaking    = vs.speaking || vs.userId == currentSpeaker
            )
        }
    }
}

@Composable
private fun CallParticipantTile(
    vs: VoiceState,
    isSelf: Boolean,
    isSpeaking: Boolean
) {
    val borderColor by animateColorAsState(
        if (isSpeaking) AppColors.Primary else Color.Transparent,
        tween(150),
        label = "border"
    )
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            UserAvatar(
                url       = vs.avatarUrl(128),
                size      = 56.dp,
                speaking  = isSpeaking
            )
            Spacer(Modifier.height(8.dp))
            Text(
                vs.username + if (isSelf) " (você)" else "",
                fontSize  = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color     = AppColors.TextPrimary,
                maxLines  = 1
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (vs.selfMute || vs.serverMute) {
                    Icon(Icons.Outlined.MicOff, null, tint = AppColors.Error, modifier = Modifier.size(14.dp))
                }
                if (vs.selfDeaf || vs.serverDeaf) {
                    Icon(Icons.Outlined.HeadsetOff, null, tint = AppColors.Error, modifier = Modifier.size(14.dp))
                }
                if (vs.selfVideo) {
                    Icon(Icons.Outlined.Videocam, null, tint = AppColors.Primary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun DmCallControls(
    vm: AppViewModel,
    onHangUp: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        CallControlButton(
            icon       = if (vm.isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
            label      = if (vm.isMuted) "Mudo" else "Mic",
            active     = vm.isMuted,
            activeColor= AppColors.Error,
            onClick    = vm::toggleMute
        )
        CallControlButton(
            icon       = if (vm.isDeafened) Icons.Outlined.HeadsetOff else Icons.Outlined.Headset,
            label      = if (vm.isDeafened) "Surdo" else "Áudio",
            active     = vm.isDeafened,
            activeColor= AppColors.Error,
            onClick    = vm::toggleDeafen
        )
        CallControlButton(
            icon       = if (vm.isCameraOn) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
            label      = "Câmera",
            active     = vm.isCameraOn,
            activeColor= AppColors.Primary,
            onClick    = { vm.isCameraOn = !vm.isCameraOn }
        )
        CallControlButton(
            icon       = Icons.Outlined.CallEnd,
            label      = "Encerrar",
            active     = true,
            activeColor= AppColors.Error,
            onClick    = onHangUp
        )
    }
}
