package com.cardvault.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cardvault.CardVaultApp
import com.cardvault.data.Card
import com.cardvault.net.EbayClient
import com.cardvault.scan.Catalog
import com.cardvault.scan.ImageUtils
import com.cardvault.scan.loadPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardScreen(cardId: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val app = CardVaultApp.instance
    val repo = app.repo
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var form by remember { mutableStateOf<Card?>(null) }
    var original by remember { mutableStateOf<Card?>(null) }
    var manualText by remember { mutableStateOf("") }
    var lookingUp by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val isNew = cardId == 0L

    LaunchedEffect(cardId) {
        val loaded = if (isNew) ScanDraft.card ?: Card() else repo.getCard(cardId) ?: Card()
        form = loaded
        original = loaded
        manualText = loaded.manualValue?.let { "%.2f".format(it) } ?: ""
        if (isNew) ScanDraft.message?.let { snackbar.showSnackbar(it) }
        ScanDraft.card = null
        ScanDraft.message = null
    }

    /** Leaving a new card without saving: remove the photos the scan saved. */
    fun discard() {
        if (isNew) {
            form?.frontImage?.let { File(it).delete() }
            form?.backImage?.let { File(it).delete() }
        } else {
            // Remove any photos picked during this edit that weren't saved.
            val o = original
            form?.frontImage?.takeIf { it != o?.frontImage }?.let { File(it).delete() }
            form?.backImage?.takeIf { it != o?.backImage }?.let { File(it).delete() }
        }
        onBack()
    }
    BackHandler { discard() }

    var pickingFront by remember { mutableStateOf(true) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) {
                loadPhoto(context, uri)?.let { ImageUtils.save(it, repo.imageDir) }
            }
            if (path != null) {
                form = form?.let { f ->
                    // Drop an unsaved photo this one replaces.
                    val old = if (pickingFront) f.frontImage else f.backImage
                    val saved = if (pickingFront) original?.frontImage else original?.backImage
                    if (old != null && old != saved) File(old).delete()
                    if (pickingFront) f.copy(frontImage = path) else f.copy(backImage = path)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Confirm card" else "Edit card") },
                navigationIcon = { IconButton(onClick = { discard() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Navy,
    ) { padding ->
        val f = form
        if (f == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoSlot("Front", f.frontImage, Modifier.weight(1f)) {
                    pickingFront = true
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                PhotoSlot("Back", f.backImage, Modifier.weight(1f)) {
                    pickingFront = false
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            Text("Tap a photo to replace it with one from the gallery.", color = Muted, fontSize = 12.sp)

            Field("Player", f.player) { form = f.copy(player = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Year", f.year, Modifier.weight(1f), KeyboardType.Number) { form = f.copy(year = it.filter(Char::isDigit).take(4)) }
                Field("Card #", f.cardNumber, Modifier.weight(1f)) { form = f.copy(cardNumber = it) }
            }
            SuggestField("Brand", f.brand, Catalog.brandNames) { form = f.copy(brand = it) }
            SuggestField("Set (e.g. Prizm, Chrome)", f.setName, Catalog.sets) { form = f.copy(setName = it) }
            Field("Variation / parallel (e.g. Rookie, Refractor)", f.variation) { form = f.copy(variation = it) }
            SuggestField("Team", f.team, Catalog.teamNames) { form = f.copy(team = it) }
            SuggestField("Position", f.position, Catalog.positionNames) { form = f.copy(position = it) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Graded (in a slab)", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(checked = f.isGraded, onCheckedChange = {
                    form = f.copy(isGraded = it, grader = if (it && f.grader.isBlank()) "PSA" else f.grader)
                })
            }
            if (f.isGraded) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SuggestField("Grader", f.grader, Catalog.graders, Modifier.weight(1f)) { form = f.copy(grader = it) }
                    Field("Grade (e.g. MINT 9)", f.grade, Modifier.weight(1.4f)) { form = f.copy(grade = it) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Cert #", f.certNumber, Modifier.weight(1f), KeyboardType.Number) { form = f.copy(certNumber = it.filter(Char::isDigit)) }
                    OutlinedButton(
                        enabled = !lookingUp && f.certNumber.isNotBlank(),
                        onClick = {
                            scope.launch {
                                lookingUp = true
                                try {
                                    val cert = repo.psa.lookup(f.certNumber)
                                    val cur = form ?: f
                                    form = cur.copy(
                                        player = cert.player.ifBlank { cur.player },
                                        year = cert.year.ifBlank { cur.year },
                                        brand = Catalog.brands.firstOrNull { (_, keys) ->
                                            keys.any { cert.brand.lowercase().contains(it) }
                                        }?.first ?: cur.brand,
                                        cardNumber = cert.cardNumber.ifBlank { cur.cardNumber },
                                        variation = cert.variety.ifBlank { cur.variation },
                                        grader = "PSA",
                                        grade = cert.grade.ifBlank { cur.grade },
                                    )
                                    snackbar.showSnackbar("Filled in from PSA: ${cert.year} ${cert.brand} ${cert.player}")
                                } catch (e: Exception) {
                                    snackbar.showSnackbar(e.message ?: "PSA lookup failed.")
                                } finally {
                                    lookingUp = false
                                }
                            }
                        },
                    ) { Text(if (lookingUp) "Looking up…" else "Look up on PSA") }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("VALUE", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            OutlinedTextField(
                value = manualText,
                onValueChange = { manualText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("My value (optional, overrides eBay)") },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Field("eBay search text (optional)", f.searchOverride) { form = f.copy(searchOverride = it) }
            Text(
                "Leave blank to search for: " + EbayClient.buildQuery(f.copy(searchOverride = "")).ifBlank { "(fill in the details above)" },
                color = Muted, fontSize = 12.sp,
            )
            Field("Notes", f.notes, singleLine = false) { form = f.copy(notes = it) }

            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = {
                    saving = true
                    scope.launch {
                        val toSave = f.copy(
                            player = f.player.trim(), brand = f.brand.trim(), team = f.team.trim(),
                            manualValue = manualText.toDoubleOrNull(),
                        )
                        val id = repo.save(toSave)
                        // Look up the price in the background; the detail screen updates when it lands.
                        if (repo.settings.hasEbay && (isNew || EbayClient.buildQuery(toSave) != EbayClient.buildQuery(original ?: toSave) || toSave.lastPriced == null)) {
                            app.appScope.launch { runCatching { repo.refreshPrice(id) } }
                        }
                        saving = false
                        onSaved(id)
                    }
                },
            ) { Text(if (isNew) "Save card" else "Save changes", fontSize = 16.sp) }
            if (!repo.settings.hasEbay) {
                Text("Prices will appear once the free eBay keys are added in Settings.", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PhotoSlot(label: String, path: String?, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2.5f / 3.5f).clip(RoundedCornerShape(10.dp)).background(NavyRaised).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (path != null) {
                AsyncImage(model = File(path), contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("Add $label", color = Muted)
            }
        }
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
}

/** Text field with a dropdown of matching suggestions; free text is still allowed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = options.filter { value.isBlank() || it.contains(value, ignoreCase = true) }.take(12)
    ExposedDropdownMenuBox(expanded = expanded && matches.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
            matches.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onChange(opt); expanded = false })
            }
        }
    }
}
