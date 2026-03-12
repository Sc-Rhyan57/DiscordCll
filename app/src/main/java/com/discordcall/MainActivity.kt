package com.discordcall

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import java.io.PrintWriter
import java.io.StringWriter

private const val PREF_CRASH      = "crash_prefs"
private const val KEY_CRASH_TRACE = "crash_trace"

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupCrashHandler()

        val crashPrefs = getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE)
        val crashTrace = crashPrefs.getString(KEY_CRASH_TRACE, null)
        if (crashTrace != null) crashPrefs.edit().remove(KEY_CRASH_TRACE).apply()

        setContent {
            MaterialTheme(colorScheme = DiscordDarkColorScheme) {
                Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
                    if (crashTrace != null) {
                        CrashScreen(trace = crashTrace) {
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    } else {
                        AppRoot(vm)
                    }
                }
            }
        }
    }

    private fun setupCrashHandler() {
        val def = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val log = buildString {
                    appendLine("Discord Call — Crash Report")
                    appendLine("Manufacturer: ${Build.MANUFACTURER}")
                    appendLine("Device: ${Build.MODEL}")
                    appendLine("Android: ${Build.VERSION.RELEASE}")
                    appendLine("Version: ${BuildConfig.VERSION_NAME}")
                    appendLine("Stacktrace:")
                    append(sw.toString())
                }
                getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE)
                    .edit().putString(KEY_CRASH_TRACE, log).commit()
                startActivity(
                    android.content.Intent(this, MainActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
            } catch (_: Exception) {
                def?.uncaughtException(t, e)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(2)
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    var footerClicks by remember { mutableStateOf(0) }
    var showLogs     by remember { mutableStateOf(false) }

    fun onFooterClick() {
        footerClicks++
        if (footerClicks >= 5) { showLogs = true; footerClicks = 0 }
    }

    if (showLogs) {
        LogsScreen(onClose = { showLogs = false })
        return
    }

    val screen = when {
        !vm.isLoggedIn                              -> "login"
        vm.isInCall && vm.activeDmCall != null      -> "dm_call"
        vm.isInCall                                 -> "call"
        vm.activeDmCall != null                     -> "dm_call"
        vm.selectedDmChannel != null                -> "dm_chat"
        vm.selectedChannel != null                  -> "lobby"
        vm.selectedGuild != null                    -> "channels"
        vm.homeTab == HomeTab.DMS                   -> "dms"
        else                                        -> "guilds"
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState  = screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
            label        = "nav"
        ) { s ->
            when (s) {
                "login" -> LoginScreen(
                    vm            = vm,
                    footerClicks  = footerClicks,
                    onFooterClick = ::onFooterClick
                )
                "guilds" -> HomeScreen(
                    vm            = vm,
                    footerClicks  = footerClicks,
                    onFooterClick = ::onFooterClick
                )
                "dms" -> HomeScreen(
                    vm            = vm,
                    footerClicks  = footerClicks,
                    onFooterClick = ::onFooterClick
                )
                "channels" -> ChannelPickerScreen(
                    vm     = vm,
                    onBack = { vm.selectedGuild = null },
                    onChannelSelected = { channel -> vm.selectChannel(channel) }
                )
                "lobby" -> ChannelLobbyScreen(
                    vm     = vm,
                    onBack = { vm.selectedChannel = null },
                    onJoin = { vm.joinVoiceChannel(vm.selectedChannel!!) }
                )
                "call" -> ActiveCallScreen(
                    vm       = vm,
                    onHangup = { vm.leaveVoiceChannel() }
                )
                "dm_call" -> DmCallScreen(
                    vm       = vm,
                    dm       = vm.activeDmCall!!,
                    onHangUp = {
                        vm.leaveDmCall()
                        vm.selectedDmChannel = null
                    }
                )
                "dm_chat" -> DmChatScreen(
                    vm     = vm,
                    dm     = vm.selectedDmChannel!!,
                    onBack = { vm.selectedDmChannel = null },
                    onCall = { vm.startDmCall(vm.selectedDmChannel!!) }
                )
            }
        }

        val incoming = vm.incomingCall
        if (incoming != null) {
            IncomingCallOverlay(
                call      = incoming,
                onAnswer  = { vm.answerIncomingCall() },
                onDecline = { vm.declineIncomingCall() }
            )
        }
    }
}
