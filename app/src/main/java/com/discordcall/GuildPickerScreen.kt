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
fun GuildPickerScreen(
    vm:              AppViewModel,
    onGuildSelected: (DiscordGuild) -> Unit,
    footerClicks:    Int,
    onFooterClick:   () -> Unit,
    onLogout:        () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val favorites  = vm.favoriteGuildIds
    val allGuilds  = vm.guilds

    val sortedGuilds = remember(allGuilds, favorites, searchQuery) {
        val q = searchQuery.trim().lowercase()
        val filtered = if (q.isEmpty()) allGuilds
                       else allGuilds.filter { it.name.lowercase().contains(q) }
        filtered.sortedWith(compareByDescending { it.id in favorites })
    }

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        // Header
        Column(
            Modifier.fillMaxWidth().background(AppColors.Surface)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val user = vm.currentUser
                    if (user != null) {
                        AnimatedImage(
                            url = user.avatarUrl(40),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(user.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                            Text("@${user.username}", fontSize = 11.sp, color = AppColors.TextMuted)
                        }
                    } else {
                        Text("Servidores", fontSize = 18.sp, fontWeight = FontWeight.Black, color = AppColors.TextPrimary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { vm.loadGuilds() }) {
                        Icon(Icons.Outlined.Refresh, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Outlined.Logout, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

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
                shape      = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        if (vm.loadingGuilds) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppColors.Primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Carregando servidores...", color = AppColors.TextMuted)
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Favorites section header
                if (favorites.isNotEmpty() && searchQuery.isEmpty()) {
                    item {
                        Text(
                            "FAVORITOS",
                            fontSize      = 11.sp,
                            fontWeight    = FontWeight.Bold,
                            color         = AppColors.Warning,
                            letterSpacing = 0.8.sp,
                            modifier      = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                items(sortedGuilds, key = { it.id }) { guild ->
                    val isFav = guild.id in favorites

                    // Show "TODOS OS SERVIDORES" header after favorites
                    if (favorites.isNotEmpty() && searchQuery.isEmpty() && !isFav
                        && sortedGuilds.indexOf(guild) == favorites.size) {
                        Text(
                            "TODOS OS SERVIDORES",
                            fontSize      = 11.sp,
                            fontWeight    = FontWeight.Bold,
                            color         = AppColors.TextMuted,
                            letterSpacing = 0.8.sp,
                            modifier      = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    GuildRow(
                        guild       = guild,
                        isFavorite  = isFav,
                        onClick     = { onGuildSelected(guild) },
                        onToggleFav = { vm.toggleFavoriteGuild(guild.id) }
                    )
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    Footer(clicks = footerClicks, onClick = onFooterClick)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor   = AppColors.Surface,
            title            = { Text("Sair?", color = AppColors.TextPrimary) },
            text             = { Text("Tem certeza que deseja deslogar?", color = AppColors.TextSecondary) },
            confirmButton    = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Sair", color = AppColors.Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar", color = AppColors.TextMuted)
                }
            }
        )
    }
}

@Composable
private fun GuildRow(
    guild: DiscordGuild,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFav: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isFavorite) AppColors.Warning.copy(0.05f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(50.dp)) {
            val iconUrl = guild.iconUrl(100)
            if (iconUrl != null) {
                AnimatedImage(
                    url = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                )
            } else {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
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
            if (isFavorite) {
                Box(
                    Modifier
                        .size(14.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(AppColors.Warning),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Star, null, tint = Color.White, modifier = Modifier.size(9.dp))
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                guild.name,
                fontSize   = 15.sp,
                fontWeight = if (isFavorite) FontWeight.Bold else FontWeight.SemiBold,
                color      = if (isFavorite) AppColors.TextPrimary else AppColors.TextSecondary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            val voiceCount = guild.approximateMemberCount
            if (voiceCount > 0) {
                Text("$voiceCount membros", fontSize = 11.sp, color = AppColors.TextMuted)
            }
        }

        IconButton(
            onClick  = onToggleFav,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                null,
                tint     = if (isFavorite) AppColors.Warning else AppColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
    }
}
