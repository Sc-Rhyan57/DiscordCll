package com.discordcall

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
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
fun DmListScreen(
    vm: AppViewModel,
    onDmSelected: (DmChannel) -> Unit,
    onDmCall: (DmChannel) -> Unit,
    onFriendCall: (DmRecipient) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AppColors.Primary.copy(0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.ChatBubble, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mensagens Diretas", fontSize = 17.sp, fontWeight = FontWeight.Black, color = AppColors.TextPrimary)
                    Text("Amigos e grupos", fontSize = 12.sp, color = AppColors.TextMuted)
                }
                IconButton(onClick = { vm.loadDmChannels(); vm.loadFriends() }) {
                    Icon(Icons.Outlined.Refresh, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
                }
            }

            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
                placeholder   = { Text("Buscar conversa ou amigo...", color = AppColors.TextMuted) },
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

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = AppColors.Surface,
                contentColor     = AppColors.Primary
            ) {
                Tab(
                    selected      = selectedTab == 0,
                    onClick       = { selectedTab = 0 },
                    text          = { Text("Conversas", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected      = selectedTab == 1,
                    onClick       = { selectedTab = 1 },
                    text          = { Text("Amigos", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        when (selectedTab) {
            0 -> DmConversationsTab(vm = vm, searchQuery = searchQuery, onDmSelected = onDmSelected, onCallClick = onDmCall)
            1 -> FriendsTab(vm = vm, searchQuery = searchQuery, onCall = onFriendCall)
        }
    }
}

@Composable
private fun DmConversationsTab(
    vm: AppViewModel,
    searchQuery: String,
    onDmSelected: (DmChannel) -> Unit,
    onCallClick: (DmChannel) -> Unit
) {
    val filtered = remember(vm.dmChannels, searchQuery) {
        if (searchQuery.isEmpty()) vm.dmChannels
        else vm.dmChannels.filter { dm ->
            dm.displayName.contains(searchQuery, ignoreCase = true) ||
            dm.recipients.any { it.displayName.contains(searchQuery, ignoreCase = true) }
        }
    }

    if (vm.loadingDms) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AppColors.Primary)
                Spacer(Modifier.height(12.dp))
                Text("Carregando conversas...", color = AppColors.TextMuted)
            }
        }
        return
    }

    if (filtered.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = AppColors.TextMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Nenhuma conversa encontrada", color = AppColors.TextMuted)
                Spacer(Modifier.height(8.dp))
                Text("Busque amigos na aba Amigos", fontSize = 12.sp, color = AppColors.TextMuted.copy(0.7f))
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(filtered) { dm ->
            DmChannelRow(
                dm          = dm,
                myUserId    = vm.currentUser?.id ?: "",
                onClick     = { onDmSelected(dm) },
                onCallClick = { onCallClick(dm) }
            )
        }
    }
}

@Composable
private fun DmChannelRow(
    dm: DmChannel,
    myUserId: String,
    onClick: () -> Unit,
    onCallClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp)) {
            val iconUrl = dm.iconUrl(96)
            if (iconUrl != null) {
                AnimatedImage(
                    url = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(if (dm.isGroupDm) RoundedCornerShape(16.dp) else CircleShape)
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(AppColors.Primary.copy(0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dm.displayName.take(2).uppercase(),
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = AppColors.Primary
                    )
                }
            }
            if (dm.callActive) {
                Box(
                    Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(AppColors.Background)
                        .padding(2.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(AppColors.Success)
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dm.displayName,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = AppColors.TextPrimary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
                if (dm.isGroupDm) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .background(AppColors.Primary.copy(0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("Grupo", fontSize = 9.sp, color = AppColors.Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (dm.callActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(AppColors.Success))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${dm.callParticipants.size} em chamada",
                        fontSize = 11.sp,
                        color    = AppColors.Success
                    )
                }
            } else if (dm.isGroupDm) {
                Text(
                    "${dm.recipients.size} membros",
                    fontSize = 11.sp,
                    color    = AppColors.TextMuted
                )
            }
        }

        IconButton(
            onClick  = onCallClick,
            modifier = Modifier.size(38.dp).clip(CircleShape).background(AppColors.Primary.copy(0.12f))
        ) {
            Icon(Icons.Outlined.Call, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FriendsTab(
    vm: AppViewModel,
    searchQuery: String,
    onCall: (DmRecipient) -> Unit
) {
    val filtered = remember(vm.friends, searchQuery) {
        if (searchQuery.isEmpty()) vm.friends
        else vm.friends.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.username.contains(searchQuery, ignoreCase = true)
        }
    }

    if (vm.loadingFriends) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AppColors.Primary)
                Spacer(Modifier.height(12.dp))
                Text("Carregando amigos...", color = AppColors.TextMuted)
            }
        }
        return
    }

    if (filtered.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.PeopleOutline, null, tint = AppColors.TextMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Nenhum amigo encontrado", color = AppColors.TextMuted)
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        item {
            Text(
                "AMIGOS — ${filtered.size}",
                fontSize      = 11.sp,
                fontWeight    = FontWeight.Bold,
                color         = AppColors.TextMuted,
                letterSpacing = 0.8.sp,
                modifier      = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        items(filtered) { friend ->
            FriendRow(friend = friend, onCall = { onCall(friend) }, onMessage = { vm.openDmChannel(friend) })
        }
    }
}

@Composable
private fun FriendRow(
    friend: DmRecipient,
    onCall: () -> Unit,
    onMessage: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onMessage() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedImage(
            url = friend.avatarUrl(64),
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                friend.displayName,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = AppColors.TextPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                "@${friend.username}",
                fontSize = 12.sp,
                color    = AppColors.TextMuted
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(
                onClick  = onMessage,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.SurfaceVar)
            ) {
                Icon(Icons.Outlined.ChatBubble, null, tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick  = onCall,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.Primary.copy(0.15f))
            ) {
                Icon(Icons.Outlined.Call, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
