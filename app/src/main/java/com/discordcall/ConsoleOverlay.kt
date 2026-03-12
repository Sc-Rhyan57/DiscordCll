package com.discordcall

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
import androidx.compose.ui.unit.*

object Logger {
    data class LogEntry(val level: String, val tag: String, val msg: String, val ts: Long = System.currentTimeMillis())
    private val _logs = mutableStateListOf<LogEntry>()
    val logs: List<LogEntry> get() = _logs

    fun d(tag: String, msg: String) { add("DEBUG", tag, msg) }
    fun i(tag: String, msg: String) { add("INFO",  tag, msg) }
    fun w(tag: String, msg: String) { add("WARN",  tag, msg) }
    fun e(tag: String, msg: String) { add("ERROR", tag, msg) }
    fun s(tag: String, msg: String) { add("OK",    tag, msg) }

    private fun add(level: String, tag: String, msg: String) {
        android.util.Log.d("DiscordCall/$tag", "[$level] $msg")
        _logs.add(LogEntry(level, tag, msg))
        if (_logs.size > 500) _logs.removeAt(0)
    }

    fun clear() { _logs.clear() }
}

@Composable
fun ConsoleOverlay(vm: AppViewModel, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    val logs      = Logger.logs

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.6f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF0D1117))
                .clickable {}
        ) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Terminal, null, tint = Color(0xFF00FF41), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Console", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FF41), fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .background(Color(0xFF1C2128), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${logs.size} linhas", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.weight(1f))
                // VM state
                Text(
                    "v=${vm.currentUser?.displayName ?: "?"} inCall=${vm.isInCall} vsid=${vm.voiceSessionId.take(8)}",
                    fontSize = 9.sp,
                    color    = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 8.dp)
                )
                IconButton(onClick = { Logger.clear() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.DeleteSweep, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            // Logs
            LazyColumn(
                state          = listState,
                modifier       = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(logs) { entry ->
                    val color = when (entry.level) {
                        "ERROR" -> Color(0xFFFF5555)
                        "WARN"  -> Color(0xFFFFB86C)
                        "OK"    -> Color(0xFF50FA7B)
                        "INFO"  -> Color(0xFF8BE9FD)
                        else    -> Color(0xFF6272A4)
                    }
                    val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                        .format(java.util.Date(entry.ts))
                    Text(
                        "[$ts] [${entry.level.padEnd(5)}] [${entry.tag}] ${entry.msg}",
                        fontSize   = 10.sp,
                        color      = color,
                        fontFamily = FontFamily.Monospace,
                        lineHeight  = 14.sp
                    )
                }
            }
        }
    }
}
