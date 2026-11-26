package com.cardvault.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cardvault.CardVaultApp
import com.cardvault.data.Card
import com.cardvault.data.PricePoint
import com.cardvault.data.gradeLabel
import com.cardvault.data.title
import com.cardvault.data.value
import com.cardvault.net.EbayClient
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(cardId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val repo = CardVaultApp.instance.repo
    val cardFlow = remember(cardId) { repo.observeCard(cardId) }
    val historyFlow = remember(cardId) { repo.observeHistory(cardId) }
    val card by cardFlow.collectAsState(initial = null)
    val history by historyFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var pricing by remember { mutableStateOf(false) }
    var showValueDialog by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    fun refresh() {
        if (!repo.settings.hasEbay) {
            scope.launch { snackbar.showSnackbar("Add your free eBay keys in Settings to look up prices.") }
            return
        }
        scope.launch {
            pricing = true
            try {
                val updated = repo.refreshPrice(cardId)
                snackbar.showSnackbar(
                    if (updated.marketSampleSize == 0) "No matching eBay listings found. Try editing the eBay search text."
                    else "Price updated from ${updated.marketSampleSize} eBay listings."
                )
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Price lookup failed.")
            } finally {
                pricing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card?.player?.ifBlank { "Card" } ?: "Card") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Navy,
    ) { padding ->
        val c = card
        if (c == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            FlipCard(c, Modifier.fillMaxWidth(0.72f).align(Alignment.CenterHorizontally))
            Text(
                "Tap the card to flip it",
                color = Muted, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
            )
            Spacer(Modifier.height(16.dp))

            ValueSection(c, history, pricing, onRefresh = ::refresh, onSetValue = { showValueDialog = true })
            Spacer(Modifier.height(16.dp))
            TrendSection(history)
            Spacer(Modifier.height(16.dp))
            DetailsSection(c)
            Spacer(Modifier.height(16.dp))
            SoldPriceLinks(c) { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showValueDialog) {
        card?.let { c ->
            ValueDialog(
                current = c.manualValue,
                onDismiss = { showValueDialog = false },
                onSave = { v ->
                    showValueDialog = false
                    scope.launch { repo.save(c.copy(manualValue = v)) }
                },
            )
        }
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("This removes the card, its photos and its price history from the app.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    card?.let { c -> scope.launch { repo.delete(c); onBack() } }
                }) { Text("Delete", color = Down) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun FlipCard(card: Card, modifier: Modifier = Modifier) {
    var flipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(450), label = "flip")
    val showingBack = rotation > 90f
    Box(
        modifier
            .aspectRatio(2.5f / 3.5f)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
            .clip(RoundedCornerShape(12.dp))
            .background(NavyRaised)
            .clickable { flipped = !flipped },
        contentAlignment = Alignment.Center,
    ) {
        val path = if (showingBack) card.backImage else card.frontImage
        Box(Modifier.fillMaxSize().graphicsLayer { if (showingBack) rotationY = 180f }, contentAlignment = Alignment.Center) {
            if (path != null) {
                AsyncImage(
                    model = File(path),
                    contentDescription = if (showingBack) "Back of card" else "Front of card",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(if (showingBack) "No photo of the back" else "No photo", color = Muted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NavyCard).padding(16.dp)) {
        Text(title.uppercase(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ValueSection(card: Card, history: List<PricePoint>, pricing: Boolean, onRefresh: () -> Unit, onSetValue: () -> Unit) {
    Section("Value") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatMoney(card.value), color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            change(history, 30)?.let { pct -> ChangeLabel(pct, "30d") }
        }
        val sourceText = when {
            card.manualValue != null -> "Your value" +
                (card.marketValue?.let { " · eBay estimate ${formatMoney(it)}" } ?: "")
            card.marketValue != null -> "eBay asking-price estimate · median of ${card.marketSampleSize} listings" +
                (if (card.marketLow != null && card.marketHigh != null) " (${formatMoney(card.marketLow)}–${formatMoney(card.marketHigh)})" else "")
            card.lastPriced != null -> "No matching eBay listings found"
            else -> "Not priced yet"
        }
        Text(sourceText, color = Muted, fontSize = 13.sp)
        Text("Updated ${formatAgo(card.lastPriced)}", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh, enabled = !pricing) {
                if (pricing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Update price")
            }
            OutlinedButton(onClick = onSetValue) { Text(if (card.manualValue != null) "Change my value" else "Set my value") }
        }
    }
}

@Composable
private fun ChangeLabel(pct: Double, label: String) {
    val up = pct >= 0
    Text(
        "${if (up) "▲" else "▼"} ${"%.1f".format(abs(pct))}% $label",
        color = if (up) Up else Down, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
    )
}

/** Percent change over the last [days] days, or null if there isn't enough history. */
private fun change(history: List<PricePoint>, days: Int?): Double? {
    if (history.size < 2) return null
    val last = history.last()
    val start = if (days == null) history.first()
    else history.lastOrNull { it.day <= last.day - days } ?: history.first()
    if (start === last || start.value <= 0) return null
    return (last.value - start.value) / start.value * 100
}

@Composable
private fun TrendSection(history: List<PricePoint>) {
    var range by remember { mutableStateOf(90) } // days; 0 = all
    Section("Value trend") {
        if (history.size < 2) {
            Text(
                "The trend chart fills in as the app re-prices this card each day.",
                color = Muted, fontSize = 14.sp,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "1M", 90 to "3M", 365 to "1Y", 0 to "All").forEach { (d, label) ->
                    FilterChip(selected = range == d, onClick = { range = d }, label = { Text(label) })
                }
            }
            val today = LocalDate.now().toEpochDay()
            val shown = if (range == 0) history else history.filter { it.day >= today - range }
            Spacer(Modifier.height(8.dp))
            if (shown.size >= 2) PriceChart(shown) else Text("Not enough history in this range yet.", color = Muted)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf<Pair<Int?, String>>(7 to "7d", 30 to "30d", 90 to "90d", null to "all time").forEach { (d, label) ->
                    change(history, d)?.let { ChangeLabel(it, label) }
                }
            }
        }
    }
}

@Composable
private fun DetailsSection(card: Card) {
    Section("Details") {
        val rows = listOf(
            "Player" to card.player,
            "Year" to card.year,
            "Brand" to card.brand,
            "Set" to card.setName,
            "Card #" to card.cardNumber,
            "Variation" to card.variation,
            "Team" to card.team,
            "Position" to card.position,
            "Condition" to card.gradeLabel,
            "PSA cert" to card.certNumber,
            "Added" to formatDate(card.createdAt),
            "Notes" to card.notes,
        ).filter { it.second.isNotBlank() }
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(k, color = Muted, modifier = Modifier.width(96.dp))
                Text(v)
            }
        }
    }
}

@Composable
private fun SoldPriceLinks(card: Card, open: (String) -> Unit) {
    val q = EbayClient.buildQuery(card)
    val enc = Uri.encode(q)
    Section("Check sold prices") {
        Text("eBay's free data only shows asking prices. These open real sold prices in the browser.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LinkRow("eBay sold listings") {
            open("https://www.ebay.com/sch/i.html?_nkw=$enc&_sacat=${EbayClient.SPORTS_CARD_SINGLES}&LH_Sold=1&LH_Complete=1")
        }
        if (card.isGraded && card.certNumber.isNotBlank() && card.grader.ifBlank { "PSA" } == "PSA") {
            LinkRow("PSA cert page (with auction prices)") { open("https://www.psacard.com/cert/${card.certNumber}") }
        }
        LinkRow("Web search for sold prices") { open("https://www.google.com/search?q=${Uri.encode("$q sold price")}") }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Gold, modifier = Modifier.weight(1f))
        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ValueDialog(current: Double?, onDismiss: () -> Unit, onSave: (Double?) -> Unit) {
    var text by remember { mutableStateOf(current?.let { "%.2f".format(it) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set my value") },
        text = {
            Column {
                Text("For example, a recent sold price you looked up. It overrides the eBay estimate.", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.toDoubleOrNull()) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text("Use eBay estimate") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
