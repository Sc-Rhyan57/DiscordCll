package com.discordcall

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*

@Composable
fun LogsScreen(
    onClose: () -> Unit
) {
    var logEnabled by remember { mutableStateOf(Logger.enabled.value) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(AppColors.Background)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(AppColors.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Terminal, null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Console", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.background(AppColors.Primary.copy(0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("${Logger.logs.size}", fontSize = 10.sp, color = AppColors.Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Log", fontSize = 11.sp, color = AppColors.TextMuted)
                        Spacer(Modifier.width(6.dp))
                        Switch(
                            checked         = logEnabled,
                            onCheckedChange = { logEnabled = it; Logger.enabled.value = it },
                            modifier        = Modifier.height(24.dp),
                            colors          = SwitchDefaults.colors(checkedThumbColor = AppColors.Primary, checkedTrackColor = AppColors.Primary.copy(0.3f))
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { Logger.logs.clear() }) {
                            Text("Limpar", color = AppColors.Error, fontSize = 11.sp)
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, null, tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (Logger.logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Terminal, null, tint = AppColors.TextMuted, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Sem logs ainda", color = AppColors.TextMuted, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                        items(Logger.logs) { log ->
                            LogRow(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: AppLog) {
    var expanded by remember { mutableStateOf(false) }
    val levelColor = when (log.level) {
        "SUCCESS" -> AppColors.Success
        "ERROR"   -> AppColors.Error
        "WARN"    -> AppColors.Warning
        else      -> AppColors.Primary
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .background(AppColors.Surface.copy(0.5f), RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(log.timestamp, fontSize = 10.sp, color = AppColors.TextMuted, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(5.dp))
            Box(Modifier.background(levelColor.copy(0.15f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text(log.level, fontSize = 9.sp, color = levelColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(5.dp))
            Text("[${log.tag}]", fontSize = 10.sp, color = AppColors.Primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(
                log.message,
                fontSize  = 11.sp,
                color     = AppColors.TextSecondary,
                fontFamily = FontFamily.Monospace,
                modifier  = Modifier.weight(1f),
                maxLines  = if (expanded) Int.MAX_VALUE else 1,
                overflow  = TextOverflow.Ellipsis
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint     = AppColors.TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
        if (expanded && log.detail != null) {
            Spacer(Modifier.height(4.dp))
            Text(log.detail, fontSize = 10.sp, color = AppColors.TextMuted, fontFamily = FontFamily.Monospace, lineHeight = 14.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
fun CrashScreen(trace: String, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, null, tint = AppColors.Error, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("App Crashou", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = AppColors.TextPrimary)
            }
            TextButton(onClick = { onClose() }) {
                Text("Fechar", color = AppColors.Error, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(
                "Ocorreu um erro inesperado. Copie o log abaixo e reporte.",
                style    = MaterialTheme.typography.bodyMedium,
                color    = AppColors.TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Button(
                onClick  = { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Crash", trace)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copiar Log", fontWeight = FontWeight.Bold)
            }
            Card(
                modifier = Modifier.fillMaxSize(),
                colors   = CardDefaults.cardColors(containerColor = AppColors.ErrorContainer),
                shape    = RoundedCornerShape(16.dp)
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            trace,
                            modifier = Modifier.padding(14.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 12.sp,
                            color      = AppColors.OnError,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}
