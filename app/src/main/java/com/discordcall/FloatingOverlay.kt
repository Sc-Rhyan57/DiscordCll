package com.discordcall

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

@Composable
fun FloatingOverlay(
    speakerAvatarUrl: String?,
    channelName: String,
    isMuted: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onMuteToggle: () -> Unit,
    onDeafenToggle: () -> Unit,
    onHangup: () -> Unit,
    onOpenApp: () -> Unit
) {
    Box(
        Modifier
            .clip(if (expanded) RoundedCornerShape(16.dp) else CircleShape)
            .background(if (expanded) AppColors.Surface else AppColors.Primary.copy(0.92f))
            .border(1.dp, AppColors.Divider, if (expanded) RoundedCornerShape(16.dp) else CircleShape)
            .clickable { if (!expanded) onToggleExpanded() }
    ) {
        if (!expanded) {
            Box(
                Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                if (speakerAvatarUrl != null) {
                    AnimatedImage(
                        url = speakerAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                    val pulse = rememberInfiniteTransition(label = "pulse")
                    val alpha by pulse.animateFloat(0.3f, 0.8f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pa")
                    Box(Modifier.fillMaxSize().clip(CircleShape).border(2.dp, AppColors.Success.copy(alpha), CircleShape))
                } else {
                    Icon(Icons.Outlined.VolumeUp, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        } else {
            Column(Modifier.padding(12.dp).width(220.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenApp() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(AppColors.Success.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (speakerAvatarUrl != null) {
                            AnimatedImage(speakerAvatarUrl, null, Modifier.fillMaxSize().clip(CircleShape))
                        } else {
                            Icon(Icons.Outlined.VolumeUp, null, tint = AppColors.Success, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Em chamada", fontSize = 10.sp, color = AppColors.Success, fontWeight = FontWeight.Bold)
                        Text(channelName, fontSize = 13.sp, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Close, null, tint = AppColors.TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = AppColors.Divider)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OverlayActionButton(
                        icon    = if (isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                        label   = if (isMuted) "Ativo" else "Mudo",
                        onClick = onMuteToggle,
                        color   = if (isMuted) AppColors.Error else AppColors.TextMuted
                    )
                    OverlayActionButton(
                        icon    = Icons.Outlined.Headset,
                        label   = "Ouvindo",
                        onClick = onDeafenToggle,
                        color   = AppColors.TextMuted
                    )
                    OverlayActionButton(
                        icon    = Icons.Outlined.CallEnd,
                        label   = "Encerrar",
                        onClick = onHangup,
                        color   = AppColors.Error
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 9.sp, color = color)
    }
}
