package com.discordcall

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme

object AppColors {
    val Background     = Color(0xFF1E1F22)
    val Surface        = Color(0xFF2B2D31)
    val SurfaceVar     = Color(0xFF313338)
    val CodeBg         = Color(0xFF1A1B1E)
    val Primary        = Color(0xFF5865F2)
    val OnPrimary      = Color(0xFFFFFFFF)
    val Success        = Color(0xFF23A55A)
    val Warning        = Color(0xFFFAA61A)
    val Error          = Color(0xFFED4245)
    val ErrorContainer = Color(0xFF3B1A1B)
    val OnError        = Color(0xFFFFDFDE)
    val TextPrimary    = Color(0xFFF2F3F5)
    val TextSecondary  = Color(0xFFB5BAC1)
    val TextMuted      = Color(0xFF80848E)
    val Divider        = Color(0xFF3F4147)
    val LoginBg1       = Color(0xFF1A1C3A)
    val LoginBg2       = Color(0xFF111228)
    val LoginBg3       = Color(0xFF0A0B19)
    val CallBg         = Color(0xFF111214)
    val Overlay        = Color(0xCC000000)
}

object Radius {
    val Small  = 8
    val Medium = 12
    val Large  = 16
    val XLarge = 20
    val Card   = 18
    val Button = 14
    val Badge  = 20
}

val DiscordDarkColorScheme = darkColorScheme(
    primary        = AppColors.Primary,
    onPrimary      = AppColors.OnPrimary,
    background     = AppColors.Background,
    surface        = AppColors.Surface,
    surfaceVariant = AppColors.SurfaceVar,
    onBackground   = AppColors.TextPrimary,
    onSurface      = AppColors.TextPrimary,
    error          = AppColors.Error,
    errorContainer = AppColors.ErrorContainer,
    onError        = AppColors.OnError
)
