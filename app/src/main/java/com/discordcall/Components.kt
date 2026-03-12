package com.discordcall

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Send
import androidx.compose.foundation.verticalScroll

@Composable
fun AnimatedImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val ctx = LocalContext.current
    val loader = remember(ctx) {
        ImageLoader.Builder(ctx).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
        }.build()
    }
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(url).crossfade(false).build(),
        contentDescription = contentDescription,
        imageLoader = loader,
        modifier = modifier,
        contentScale = contentScale
    )
}

@Composable
fun UserAvatar(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier,
    speaking: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "speaking")
    val speakingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "spAlpha"
    )
    Box(
        modifier = modifier.size(size + if (speaking) 6.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        if (speaking) {
            Box(
                Modifier
                    .size(size + 6.dp)
                    .clip(CircleShape)
                    .background(AppColors.Success.copy(alpha = speakingAlpha * 0.6f))
            )
        }
        AnimatedImage(
            url = url,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun ParticleBackground(speedMultiplier: Float = 1f, modifier: Modifier = Modifier) {
    data class Particle(val x: Float, val y: Float, val speed: Float, val size: Float, val alpha: Float, val phase: Float, val wobble: Float)
    data class Glob(val x: Float, val y: Float, val r: Float, val color: Color, val phase: Float)

    val particles = remember {
        List(90) {
            Particle(
                x       = Random.nextFloat(),
                y       = Random.nextFloat(),
                speed   = Random.nextFloat() * 0.5f + 0.1f,
                size    = Random.nextFloat() * 2.5f + 0.5f,
                alpha   = Random.nextFloat() * 0.4f + 0.06f,
                phase   = Random.nextFloat() * 6.28f,
                wobble  = Random.nextFloat() * 0.08f + 0.02f
            )
        }
    }
    val globs = remember {
        listOf(
            Glob(0.15f, 0.25f, 420f, Color(0xFF5865F2), 0.47f),
            Glob(0.80f, 0.60f, 350f, Color(0xFF7B5EA7), 2.51f),
            Glob(0.50f, 0.85f, 280f, Color(0xFF3B4EC8), 1.57f),
            Glob(0.70f, 0.10f, 220f, Color(0xFF5865F2), 3.14f)
        )
    }
    val transition = rememberInfiniteTransition(label = "bg")
    val baseDuration = (80000f / speedMultiplier).toInt().coerceAtLeast(3000)
    val time by transition.animateFloat(
        0f, 10000f,
        infiniteRepeatable(tween(baseDuration, easing = LinearEasing)),
        label = "t"
    )
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        globs.forEach { g ->
            val gx = g.x * w + sin(time * 0.00025f * speedMultiplier + g.phase) * 80f
            val gy = g.y * h + kotlin.math.cos(time * 0.0003f * speedMultiplier + g.phase) * 70f
            drawCircle(color = g.color.copy(alpha = 0.09f), radius = g.r, center = Offset(gx, gy))
        }
        particles.forEach { p ->
            val px = ((p.x + sin(time * 0.0015f * p.speed * speedMultiplier + p.phase) * p.wobble).mod(1f) + 1f).mod(1f) * w
            val py = ((p.y + time * p.speed * speedMultiplier * 0.00032f).mod(1f)) * h
            drawCircle(color = Color.White.copy(alpha = p.alpha), radius = p.size, center = Offset(px, py))
        }
    }
}

@Composable
fun CallControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean    = true,
    activeColor: Color = AppColors.Surface,
    inactiveColor: Color = AppColors.Error,
    modifier: Modifier = Modifier,
    enabled: Boolean   = true
) {
    val color = if (active) activeColor else inactiveColor
    Column(
        modifier         = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color)
                .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) AppColors.TextPrimary else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = AppColors.TextMuted)
    }
}

@Composable
fun Footer(clicks: Int, onClick: () -> Unit) {
    val t   = rememberInfiniteTransition(label = "rgb")
    val hue by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "h"
    )
    val c = Color.hsv(hue, 0.75f, 1f)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.clickable { onClick() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✦", fontSize = 11.sp, color = c)
            Spacer(Modifier.width(6.dp))
            Text("By Rhyan57", fontSize = 12.sp, color = c, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("✦", fontSize = 11.sp, color = c)
        }
        if (clicks in 1..4) {
            Text("${5 - clicks}x to open console", fontSize = 10.sp, color = AppColors.TextMuted.copy(0.5f))
        }
    }
}

@Composable
fun StatusDot(status: String, size: Dp = 10.dp) {
    val color = when (status) {
        "online"    -> AppColors.Success
        "idle"      -> AppColors.Warning
        "dnd"       -> AppColors.Error
        else        -> AppColors.TextMuted
    }
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun ChatPanel(
    messages: List<Message>,
    currentUserId: String,
    loadingMessages: Boolean,
    onSend: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val listState   = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        if (loadingMessages) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary, modifier = Modifier.size(24.dp))
            }
        } else if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = AppColors.TextMuted, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Sem mensagens ainda", fontSize = 12.sp, color = AppColors.TextMuted)
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                state          = listState,
                modifier       = Modifier.weight(1f).padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages.size) { idx ->
                    val msg    = messages[idx]
                    val isSelf = msg.authorId == currentUserId
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
                    ) {
                        if (!isSelf) {
                            AsyncImage(
                                model = msg.authorAvatarUrl(32),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Column(horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start) {
                            if (!isSelf) {
                                Text(msg.authorName, fontSize = 10.sp, color = AppColors.TextMuted)
                                Spacer(Modifier.height(2.dp))
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(
                                        topStart = if (isSelf) 14.dp else 4.dp,
                                        topEnd   = if (isSelf) 4.dp  else 14.dp,
                                        bottomStart = 14.dp, bottomEnd = 14.dp
                                    ))
                                    .background(if (isSelf) AppColors.Primary.copy(0.85f) else AppColors.SurfaceVar)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    msg.content,
                                    fontSize = 13.sp,
                                    color    = if (isSelf) Color.White else AppColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = messageText,
                onValueChange = { messageText = it },
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Mensagem…", color = AppColors.TextMuted, fontSize = 13.sp) },
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AppColors.Primary,
                    unfocusedBorderColor = AppColors.Divider,
                    focusedTextColor     = AppColors.TextPrimary,
                    unfocusedTextColor   = AppColors.TextPrimary,
                    cursorColor          = AppColors.Primary
                ),
                shape    = RoundedCornerShape(20.dp),
                maxLines = 3
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = {
                    val text = messageText.trim()
                    if (text.isNotEmpty()) { onSend(text); messageText = "" }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (messageText.trim().isNotEmpty()) AppColors.Primary else AppColors.SurfaceVar)
            ) {
                Icon(
                    Icons.Outlined.Send,
                    null,
                    tint = if (messageText.trim().isNotEmpty()) Color.White else AppColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
