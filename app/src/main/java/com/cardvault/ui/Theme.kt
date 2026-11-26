package com.cardvault.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val Navy = Color(0xFF0F1A2B)
val NavyCard = Color(0xFF182740)
val NavyRaised = Color(0xFF22355A)
val Gold = Color(0xFFD4A23A)
val Up = Color(0xFF4CC38A)
val Down = Color(0xFFE5675C)
val Muted = Color(0xFF9AA8BF)

private val scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Navy,
    secondary = Color(0xFF8FB3E8),
    background = Navy,
    onBackground = Color(0xFFF1F4F9),
    surface = NavyCard,
    onSurface = Color(0xFFF1F4F9),
    surfaceVariant = NavyRaised,
    onSurfaceVariant = Muted,
    surfaceContainer = NavyCard,
    surfaceContainerHigh = NavyRaised,
    surfaceContainerLow = NavyCard,
    outline = Color(0xFF3A4F6E),
)

@Composable
fun CardVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

private val money = NumberFormat.getCurrencyInstance(Locale.US)
private val moneyWhole = NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }

fun formatMoney(v: Double?): String = when {
    v == null -> "—"
    v >= 1000 -> moneyWhole.format(v)
    else -> money.format(v)
}

fun formatDate(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy").format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun formatAgo(epochMillis: Long?): String {
    if (epochMillis == null) return "never"
    val mins = (System.currentTimeMillis() - epochMillis) / 60_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 60 * 24 -> "${mins / 60}h ago"
        mins < 60 * 24 * 30 -> "${mins / (60 * 24)}d ago"
        else -> formatDate(epochMillis)
    }
}
