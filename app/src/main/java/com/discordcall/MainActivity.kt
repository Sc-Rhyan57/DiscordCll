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

sealed class Screen {
    object Login         : Screen()
    object Home          : Screen()
    object GuildChannels : Screen()
    object ChannelLobby  : Screen()
    object ActiveCall    : Screen()
    object DmCallScreen  : Screen()
}

@Composable
fun AppRoot(vm: AppViewModel) {
    var screen       by remember { mutableStateOf<Screen>(Screen.Login) }
    var footerClicks by remember { mutableStateOf(0) }
    var showConsole  by remember { mutableStateOf(false) }

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
                targetState  = screen,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label        = "nav"
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
                        vm              = vm,
                        footerClicks    = footerClicks,
                        onFooterClick   = {
                            footerClicks++
                            if (footerClicks >= 5) { showConsole = true; footerClicks = 0 }
                        },
                        onGuildSelected = { guild: DiscordGuild ->
                            vm.selectGuild(guild)
                            screen = Screen.GuildChannels
                        },
                        onDmCallStart   = { screen = Screen.DmCallScreen },
                        onLogout        = {
                            vm.logout()
                            screen = Screen.Login
                        }
                    )

                    Screen.GuildChannels -> {
                        val guild = vm.selectedGuild
                        if (guild == null) {
                            LaunchedEffect(Unit) { screen = Screen.Home }
                            Box(Modifier.fillMaxSize().background(AppColors.Background))
                        } else {
                            GuildChannelScreen(
                                vm        = vm,
                                guild     = guild,
                                onBack    = { screen = Screen.Home },
                                onChannel = { ch: VoiceChannel ->
                                    vm.selectChannel(ch)
                                    screen = Screen.ChannelLobby
                                }
                            )
                        }
                    }

                    Screen.ChannelLobby -> {
                        val channel = vm.selectedChannel
                        if (channel == null) {
                            LaunchedEffect(Unit) { screen = Screen.GuildChannels }
                            Box(Modifier.fillMaxSize().background(AppColors.Background))
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
                            LaunchedEffect(Unit) { screen = Screen.GuildChannels }
                            Box(Modifier.fillMaxSize().background(AppColors.Background))
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

                    Screen.DmCallScreen -> {
                        val dm = vm.activeDmCall
                        if (dm == null) {
                            LaunchedEffect(Unit) { screen = Screen.Home }
                            Box(Modifier.fillMaxSize().background(AppColors.Background))
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

            // Incoming call overlay
            val incoming = vm.incomingCall
            if (incoming != null) {
                IncomingCallOverlay(
                    call      = incoming,
                    onAnswer  = { vm.answerIncomingCall(); screen = Screen.DmCallScreen },
                    onDecline = { vm.declineIncomingCall() }
                )
            }

            // Global floating console button
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 12.dp)
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.4f))
                        .clickable { showConsole = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.BugReport, null, tint = AppColors.TextMuted.copy(0.7f), modifier = Modifier.size(17.dp))
                }
            }

            if (showConsole) {
                ConsoleOverlay(vm = vm, onDismiss = { showConsole = false })
            }
        }
    }
}
