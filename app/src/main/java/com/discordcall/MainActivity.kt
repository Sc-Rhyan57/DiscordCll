package com.discordcall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot(vm) }
    }
}

/** Navigation state — all in one sealed class to prevent null pointer during animated transitions */
sealed class Screen {
    object Login          : Screen()
    object Home           : Screen()
    object GuildChannels  : Screen()
    object ChannelLobby   : Screen()
    object ActiveCall     : Screen()
    object DmList         : Screen()
    object DmCallScreen   : Screen()
}

@Composable
fun AppRoot(vm: AppViewModel) {
    var screen        by remember { mutableStateOf<Screen>(Screen.Login) }
    var footerClicks  by remember { mutableStateOf(0) }
    var showConsole   by remember { mutableStateOf(false) }

    // Derive screen from VM state changes safely
    LaunchedEffect(vm.isLoggedIn) {
        screen = if (vm.isLoggedIn) Screen.Home else Screen.Login
    }
    LaunchedEffect(vm.isInCall, vm.selectedChannel) {
        if (vm.isInCall && vm.selectedChannel != null && screen != Screen.ActiveCall) {
            screen = Screen.ActiveCall
        }
    }
    LaunchedEffect(vm.activeDmCall, vm.dmCallState) {
        if (vm.activeDmCall != null && screen != Screen.DmCallScreen) {
            screen = Screen.DmCallScreen
        } else if (vm.activeDmCall == null && screen == Screen.DmCallScreen) {
            screen = Screen.Home
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                label = "nav"
            ) { target ->
                when (target) {
                    Screen.Login -> LoginScreen(
                        vm            = vm,
                        footerClicks  = footerClicks,
                        onFooterClick = {
                            footerClicks++
                            if (footerClicks >= 5) { showConsole = true; footerClicks = 0 }
                        }
                    )
                    Screen.Home -> HomeScreen(
                        vm            = vm,
                        footerClicks  = footerClicks,
                        onFooterClick = {
                            footerClicks++
                            if (footerClicks >= 5) { showConsole = true; footerClicks = 0 }
                        },
                        onGuildSelected = { guild ->
                            vm.selectGuild(guild)
                            screen = Screen.GuildChannels
                        },
                        onDmCallStart  = { screen = Screen.DmCallScreen },
                        onLogout       = {
                            vm.logout()
                            screen = Screen.Login
                        }
                    )
                    Screen.GuildChannels -> {
                        // Guard against null guild — go back home instead of crashing
                        val guild = vm.selectedGuild
                        if (guild == null) {
                            screen = Screen.Home
                        } else {
                            GuildChannelScreen(
                                vm        = vm,
                                guild     = guild,
                                onBack    = { screen = Screen.Home },
                                onChannel = { ch ->
                                    vm.selectChannel(ch)
                                    screen = Screen.ChannelLobby
                                }
                            )
                        }
                    }
                    Screen.ChannelLobby -> {
                        val channel = vm.selectedChannel
                        if (channel == null) {
                            screen = Screen.GuildChannels
                        } else {
                            ChannelLobbyScreen(
                                vm     = vm,
                                onBack = { screen = Screen.GuildChannels },
                                onJoin = {
                                    vm.joinVoiceChannel(channel)
                                    screen = Screen.ActiveCall
                                }
                            )
                        }
                    }
                    Screen.ActiveCall -> {
                        val channel = vm.selectedChannel
                        if (channel == null) {
                            screen = Screen.GuildChannels
                        } else {
                            ActiveCallScreen(
                                vm       = vm,
                                onHangup = {
                                    vm.leaveVoiceChannel()
                                    screen = Screen.GuildChannels
                                }
                            )
                        }
                    }
                    Screen.DmList -> DmListScreen(
                        vm           = vm,
                        onDmSelected = { dm -> vm.selectDmChannel(dm) },
                        onDmCall     = { dm -> vm.startDmCall(dm); screen = Screen.DmCallScreen },
                        onFriendCall = { r  ->
                            val existing = vm.dmChannels.find { dm -> dm.recipients.any { it.id == r.id } }
                            if (existing != null) {
                                vm.startDmCall(existing)
                                screen = Screen.DmCallScreen
                            } else {
                                vm.openDmChannel(r)
                            }
                        }
                    )
                    Screen.DmCallScreen -> {
                        val dm = vm.activeDmCall
                        if (dm == null) {
                            screen = Screen.Home
                        } else {
                            DmCallScreen(
                                vm       = vm,
                                dm       = dm,
                                onHangUp = {
                                    vm.leaveDmCall()
                                    screen = Screen.Home
                                }
                            )
                        }
                    }
                }
            }

            // Incoming call overlay — shown on top of everything
            val incoming = vm.incomingCall
            if (incoming != null) {
                IncomingCallOverlay(
                    call      = incoming,
                    onAnswer  = { vm.answerIncomingCall(); screen = Screen.DmCallScreen },
                    onDecline = { vm.declineIncomingCall() }
                )
            }

            // Global floating console button — always accessible
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 12.dp)
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AppColors.SurfaceVar.copy(0.85f))
                        .clickable { showConsole = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.BugReport, null, tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            // Console overlay
            if (showConsole) {
                ConsoleOverlay(vm = vm, onDismiss = { showConsole = false })
            }
        }
    }
}
