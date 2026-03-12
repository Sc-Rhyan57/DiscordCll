package com.discordcall

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    vm: AppViewModel,
    footerClicks: Int,
    onFooterClick: () -> Unit
) {
    Scaffold(
        containerColor = AppColors.Background,
        bottomBar = {
            NavigationBar(
                containerColor = AppColors.Surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected  = vm.homeTab == HomeTab.SERVERS,
                    onClick   = { vm.homeTab = HomeTab.SERVERS },
                    icon      = { Icon(Icons.Outlined.Hub, contentDescription = null) },
                    label     = { Text("Servidores", fontSize = 11.sp) },
                    colors    = navBarColors()
                )
                NavigationBarItem(
                    selected  = vm.homeTab == HomeTab.DMS,
                    onClick   = { vm.homeTab = HomeTab.DMS },
                    icon      = {
                        BadgedBox(badge = {
                            if (vm.incomingCall != null) Badge()
                        }) {
                            Icon(Icons.Outlined.ChatBubble, contentDescription = null)
                        }
                    },
                    label     = { Text("Mensagens", fontSize = 11.sp) },
                    colors    = navBarColors()
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (vm.homeTab) {
                HomeTab.SERVERS -> GuildPickerScreen(
                    vm              = vm,
                    onGuildSelected = { guild -> vm.selectGuild(guild) },
                    footerClicks    = footerClicks,
                    onFooterClick   = onFooterClick
                )
                HomeTab.DMS -> DmListScreen(
                    vm            = vm,
                    onDmSelected  = { dm -> vm.selectDmChannel(dm) },
                    onFriendCall  = { recipient ->
                        // Open or create DM then immediately call
                        val existing = vm.dmChannels.find { dm -> dm.recipients.any { it.id == recipient.id } }
                        if (existing != null) {
                            vm.startDmCall(existing)
                        } else {
                            vm.openDmChannel(recipient)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun navBarColors() = NavigationBarItemDefaults.colors(
    selectedIconColor   = AppColors.Primary,
    selectedTextColor   = AppColors.Primary,
    unselectedIconColor = AppColors.TextMuted,
    unselectedTextColor = AppColors.TextMuted,
    indicatorColor      = AppColors.Primary.copy(alpha = 0.15f)
)
