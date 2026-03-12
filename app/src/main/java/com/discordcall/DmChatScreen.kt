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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Composable
fun DmChatScreen(
    vm: AppViewModel,
    dm: DmChannel,
    onBack: () -> Unit,
    onCall: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBackIos, null, tint = AppColors.TextMuted)
            }

            val iconUrl = dm.iconUrl(64)
            if (iconUrl != null) {
                AnimatedImage(
                    url = iconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(if (dm.isGroupDm) RoundedCornerShape(12.dp) else CircleShape)
                )
            } else {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AppColors.Primary.copy(0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dm.displayName.take(2).uppercase(),
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color      = AppColors.Primary
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    dm.displayName,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = AppColors.TextPrimary,
                    maxLines   = 1
                )
                if (dm.isGroupDm) {
                    Text("${dm.recipients.size} membros", fontSize = 11.sp, color = AppColors.TextMuted)
                }
            }

            IconButton(
                onClick  = onCall,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppColors.Primary.copy(0.12f))
            ) {
                Icon(Icons.Outlined.Call, null, tint = AppColors.Primary)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            ChatPanel(
                messages        = vm.messages,
                currentUserId   = vm.currentUser?.id ?: "",
                loadingMessages = vm.loadingMessages,
                onSend          = vm::sendMessage
            )
        }
    }
}
