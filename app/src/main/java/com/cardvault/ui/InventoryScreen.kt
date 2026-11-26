package com.cardvault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cardvault.CardVaultApp
import com.cardvault.data.Card
import com.cardvault.data.title
import com.cardvault.data.value
import kotlinx.coroutines.launch
import java.io.File

enum class SortOrder(val label: String) {
    VALUE_DESC("Value: high to low"),
    VALUE_ASC("Value: low to high"),
    PLAYER("Player A–Z"),
    YEAR_OLD("Year: oldest first"),
    YEAR_NEW("Year: newest first"),
    RECENT("Recently added"),
}

/** Filter and sort choices, kept while moving between screens. */
class InventoryViewModel : ViewModel() {
    var query by mutableStateOf("")
    var sort by mutableStateOf(SortOrder.VALUE_DESC)
    var brands by mutableStateOf(setOf<String>())
    var teams by mutableStateOf(setOf<String>())
    var positions by mutableStateOf(setOf<String>())
    var players by mutableStateOf(setOf<String>())
    var years by mutableStateOf(setOf<String>())
    var grading by mutableStateOf(setOf<String>()) // "Raw", "Graded"

    val activeFilterCount: Int
        get() = brands.size + teams.size + positions.size + players.size + years.size + grading.size

    fun clearFilters() {
        brands = emptySet(); teams = emptySet(); positions = emptySet()
        players = emptySet(); years = emptySet(); grading = emptySet()
    }

    fun apply(cards: List<Card>): List<Card> {
        val q = query.trim().lowercase()
        val filtered = cards.filter { c ->
            (q.isEmpty() || listOf(c.player, c.team, c.brand, c.setName, c.year, c.cardNumber, c.variation)
                .any { it.lowercase().contains(q) }) &&
                (brands.isEmpty() || c.brand in brands) &&
                (teams.isEmpty() || c.team in teams) &&
                (positions.isEmpty() || c.position in positions) &&
                (players.isEmpty() || c.player in players) &&
                (years.isEmpty() || c.year in years) &&
                (grading.isEmpty() || (if (c.isGraded) "Graded" else "Raw") in grading)
        }
        return when (sort) {
            SortOrder.VALUE_DESC -> filtered.sortedByDescending { it.value ?: -1.0 }
            SortOrder.VALUE_ASC -> filtered.sortedBy { it.value ?: Double.MAX_VALUE }
            SortOrder.PLAYER -> filtered.sortedBy { it.player.lowercase() }
            SortOrder.YEAR_OLD -> filtered.sortedBy { it.year.ifBlank { "9999" } }
            SortOrder.YEAR_NEW -> filtered.sortedByDescending { it.year }
            SortOrder.RECENT -> filtered.sortedByDescending { it.createdAt }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onScan: () -> Unit,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
    onAddManually: () -> Unit,
    vm: InventoryViewModel = viewModel(),
) {
    val repo = CardVaultApp.instance.repo
    val cardsFlow = remember { repo.observeCards() }
    val allCards by cardsFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val cards = allCards
    val shown = cards?.let { vm.apply(it) } ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Todd's Card Vault", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy),
                actions = {
                    val r = refreshing
                    if (r != null) {
                        Text("${r.first}/${r.second}", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.size(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                    } else {
                        IconButton(onClick = {
                            if (!repo.settings.hasEbay) {
                                scope.launch { snackbar.showSnackbar("Add your free eBay keys in Settings to update prices.") }
                            } else scope.launch {
                                refreshing = 0 to (cards?.size ?: 0)
                                try {
                                    val (ok, failed) = repo.refreshAll { done, total -> refreshing = done to total }
                                    snackbar.showSnackbar(
                                        if (failed == 0) "Updated $ok card values." else "Updated $ok; $failed couldn't be priced."
                                    )
                                } catch (e: Exception) {
                                    snackbar.showSnackbar(e.message ?: "Price update failed.")
                                } finally {
                                    refreshing = null
                                }
                            }
                        }) { Icon(Icons.Default.Refresh, contentDescription = "Update all prices") }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                text = { Text("Scan card") },
                containerColor = Gold,
                contentColor = Navy,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Navy,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                cards == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                cards.isEmpty() -> EmptyState(onScan = onScan, onAddManually = onAddManually)
                else -> {
                    SummaryBar(total = cards.sumOf { it.value ?: 0.0 }, count = cards.size, shownCount = shown.size,
                        shownTotal = shown.sumOf { it.value ?: 0.0 }, filtered = shown.size != cards.size)
                    OutlinedTextField(
                        value = vm.query,
                        onValueChange = { vm.query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        placeholder = { Text("Search player, team, year…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            FilterChip(
                                selected = false,
                                onClick = { showSort = true },
                                label = { Text(vm.sort.label) },
                                leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, Modifier.size(18.dp)) },
                            )
                            DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                                SortOrder.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label, color = if (s == vm.sort) Gold else Color.Unspecified) },
                                        onClick = { vm.sort = s; showSort = false },
                                    )
                                }
                            }
                        }
                        FilterChip(
                            selected = vm.activeFilterCount > 0,
                            onClick = { showFilters = true },
                            label = { Text(if (vm.activeFilterCount > 0) "Filters (${vm.activeFilterCount})" else "Filters") },
                            leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, Modifier.size(18.dp)) },
                        )
                        if (vm.activeFilterCount > 0) {
                            TextButton(onClick = { vm.clearFilters() }) { Text("Clear") }
                        }
                    }
                    if (shown.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                            Text("No cards match these filters.", color = Muted)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(shown, key = { it.id }) { card -> CardTile(card) { onOpen(card.id) } }
                        }
                    }
                }
            }
        }
    }

    if (showFilters && cards != null) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, containerColor = NavyCard) {
            FilterSheet(vm, cards)
        }
    }
}

@Composable
private fun SummaryBar(total: Double, count: Int, shownCount: Int, shownTotal: Double, filtered: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            if (filtered) "SHOWING $shownCount OF $count CARDS" else "$count CARDS",
            color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        )
        Text(formatMoney(if (filtered) shownTotal else total), color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (filtered) Text("Whole collection: ${formatMoney(total)}", color = Muted, fontSize = 13.sp)
    }
}

@Composable
fun CardTile(card: Card, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NavyCard)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2.5f / 3.5f).clip(RoundedCornerShape(8.dp)).background(NavyRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (card.frontImage != null) {
                AsyncImage(
                    model = File(card.frontImage),
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Muted)
            }
            if (card.isGraded) {
                Text(
                    listOf(card.grader.ifBlank { "PSA" }, com.cardvault.net.PricingRules.gradeNumber(card.grade) ?: "")
                        .joinToString(" ").trim(),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0xFFC8102E)).padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(card.player.ifBlank { "Unknown player" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(card.year, card.brand, card.cardNumber.takeIf { it.isNotBlank() }?.let { "#$it" })
                .filter { !it.isNullOrBlank() }.joinToString(" · "),
            color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(formatMoney(card.value), color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun EmptyState(onScan: () -> Unit, onAddManually: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No cards yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan a card's front and back and the app reads the details and looks up its value.",
            color = Muted,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Scan your first card") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAddManually, modifier = Modifier.fillMaxWidth()) { Text("Add a card by hand") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(vm: InventoryViewModel, cards: List<Card>) {
    fun options(pick: (Card) -> String) = cards.map(pick).filter { it.isNotBlank() }.distinct().sorted()

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.clearFilters() }) { Text("Clear all") }
        }
        FilterGroup("Graded or raw", listOf("Graded", "Raw"), vm.grading) { vm.grading = it }
        FilterGroup("Brand", options { it.brand }, vm.brands) { vm.brands = it }
        FilterGroup("Team", options { it.team }, vm.teams) { vm.teams = it }
        FilterGroup("Position", options { it.position }, vm.positions) { vm.positions = it }
        FilterGroup("Year", options { it.year }, vm.years) { vm.years = it }
        FilterGroup("Player", options { it.player }, vm.players) { vm.players = it }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, options: List<String>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    if (options.isEmpty()) return
    Text(title.uppercase(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = opt in selected,
                onClick = { onChange(if (opt in selected) selected - opt else selected + opt) },
                label = { Text(opt) },
            )
        }
    }
}
