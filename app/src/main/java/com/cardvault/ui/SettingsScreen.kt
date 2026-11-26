package com.cardvault.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardvault.CardVaultApp
import com.cardvault.work.PriceRefreshWorker
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = CardVaultApp.instance
    val settings = app.repo.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var ebayId by remember { mutableStateOf(settings.ebayClientId) }
    var ebaySecret by remember { mutableStateOf(settings.ebayClientSecret) }
    var psaToken by remember { mutableStateOf(settings.psaToken) }
    var autoRefresh by remember { mutableStateOf(settings.autoRefresh) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            try {
                val n = app.backup.export(uri)
                snackbar.showSnackbar("Backed up $n cards.")
            } catch (e: Exception) {
                snackbar.showSnackbar("Backup failed: ${e.message}")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Navy,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Block("eBay prices (free)") {
                Text(
                    "Values come from eBay's free Browse API: the median asking price of matching Buy It Now listings. " +
                        "Sign up at developer.ebay.com, create a Production keyset, and paste its App ID and Cert ID here.",
                    color = Muted, fontSize = 13.sp,
                )
                TextButton(onClick = { open("https://developer.ebay.com/my/keys") }) { Text("Open eBay developer keys") }
                OutlinedTextField(ebayId, { ebayId = it }, label = { Text("App ID (Client ID)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    ebaySecret, { ebaySecret = it }, label = { Text("Cert ID (Client Secret)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    settings.ebayClientId = ebayId
                    settings.ebayClientSecret = ebaySecret
                    scope.launch {
                        try {
                            app.repo.ebay.testKeys()
                            snackbar.showSnackbar("eBay keys work.")
                        } catch (e: Exception) {
                            snackbar.showSnackbar(e.message ?: "eBay keys didn't work.")
                        }
                    }
                }) { Text("Save and test") }
            }

            Block("PSA slab lookups (free)") {
                Text(
                    "For graded cards, the app reads the cert number off the slab and asks PSA for the exact card and grade. " +
                        "Sign in at psacard.com/publicapi to get an access token.",
                    color = Muted, fontSize = 13.sp,
                )
                TextButton(onClick = { open("https://www.psacard.com/publicapi") }) { Text("Open PSA Public API") }
                OutlinedTextField(
                    psaToken, { psaToken = it }, label = { Text("PSA access token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    settings.psaToken = psaToken
                    scope.launch { snackbar.showSnackbar("PSA token saved.") }
                }) { Text("Save") }
            }

            Block("Automatic updates") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Re-price every card daily", fontWeight = FontWeight.SemiBold)
                        Text("Runs in the background when the phone is online.", color = Muted, fontSize = 13.sp)
                    }
                    Switch(checked = autoRefresh, onCheckedChange = {
                        autoRefresh = it
                        settings.autoRefresh = it
                        PriceRefreshWorker.schedule(context, it)
                    })
                }
                val last = settings.lastRefreshAll
                Text(
                    "Last full update: " + if (last == 0L) "not yet" else formatAgo(last),
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }

            Block("Backup") {
                Text(
                    "Everything is stored only on this phone. Save a backup file (cards, photos and price history) " +
                        "to a USB drive, SD card or cloud folder, and restore it on a new phone.",
                    color = Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("todds-card-vault-${LocalDate.now()}.zip") }) { Text("Back up now") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                        Text("Restore")
                    }
                }
            }
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Restore from backup?") },
            text = { Text("This replaces every card currently in the app with the cards in the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = null
                    scope.launch {
                        try {
                            val n = app.backup.import(uri)
                            snackbar.showSnackbar("Restored $n cards.")
                        } catch (e: Exception) {
                            snackbar.showSnackbar("Restore failed: ${e.message}")
                        }
                    }
                }) { Text("Replace", color = Down) }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NavyCard).padding(16.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        content()
    }
}
