package io.github.stephenjoy98.heatmap.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.stephenjoy98.heatmap.cloud.Account
import io.github.stephenjoy98.heatmap.cloud.Backup
import io.github.stephenjoy98.heatmap.cloud.FirebaseGate
import io.github.stephenjoy98.heatmap.cloud.Friend
import io.github.stephenjoy98.heatmap.cloud.Friends
import io.github.stephenjoy98.heatmap.cloud.SyncWorker
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.review.Insights
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date

@Composable
fun FriendsScreen(
    contentPadding: PaddingValues,
    snackbar: SnackbarHostState,
    pendingInvite: String?,
    onInviteHandled: () -> Unit,
    onCompare: (Friend) -> Unit,
) {
    val context = LocalContext.current
    val configured = remember { FirebaseGate.isConfigured(context) }
    val user by Account.user.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Friends", style = MaterialTheme.typography.headlineMedium) }
        when {
            !configured -> item { SetupCard() }
            user == null -> item { SignInCard(snackbar) }
            else -> signedIn(this, snackbar, onCompare)
        }
    }

    if (pendingInvite != null) {
        InviteDialog(pendingInvite, signedIn = user != null, snackbar = snackbar, onDone = onInviteHandled)
    }
}

private fun signedIn(scope: androidx.compose.foundation.lazy.LazyListScope, snackbar: SnackbarHostState, onCompare: (Friend) -> Unit) {
    scope.item { ProfileCard(snackbar) }
    scope.item { BackupCard(snackbar) }
    scope.item { FriendsList(snackbar, onCompare) }
}

@Composable
private fun SetupCard() {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Sign-in isn't set up in this build", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Backup, friends and cloud AI use Firebase. Add your project's google-services.json (see the README) " +
                    "and rebuild to turn them on. Everything else works without it.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SignInCard(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Sign in with Google", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Back up your history so it's safe if you change phones, and add friends to compare routes, " +
                    "distances and the areas you've each explored.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(enabled = !busy, onClick = {
                val activity = context.findActivity() ?: return@Button
                busy = true
                scope.launch {
                    runCatching { Account.signIn(activity) }
                        .onSuccess { SyncWorker.runNow(context) }
                        .onFailure { snackbar.showSnackbar("Sign-in failed: ${it.message}") }
                    busy = false
                }
            }) { Text(if (busy) "Signing in…" else "Sign in with Google") }
        }
    }
}

@Composable
private fun ProfileCard(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by Account.user.collectAsStateWithLifecycle()
    val code by Account.friendCode.collectAsStateWithLifecycle()
    var showQr by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        if (user != null && code == null) {
            runCatching { Account.ensureProfile() }.onFailure { snackbar.showSnackbar("Couldn't load your ID: ${it.message}") }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(user?.displayName ?: "Signed in", style = MaterialTheme.typography.titleMedium)
            user?.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(12.dp))
            Text("Your ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                code?.let { Account.formatCode(it) } ?: "…",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Anyone with this ID can add you as a friend and see your shared routes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = code != null, onClick = { showQr = true }) { Text("QR code") }
                OutlinedButton(enabled = code != null, onClick = { code?.let { shareInvite(context, it) } }) { Text("Share") }
                TextButton(enabled = code != null, onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("Location Ticker ID", code))
                    scope.launch { snackbar.showSnackbar("ID copied") }
                }) { Text("Copy") }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { scope.launch { Account.signOut(context) } }) { Text("Sign out") }
        }
    }

    if (showQr && code != null) {
        val bitmap = remember(code) { QrCode.bitmap(QrCode.inviteLink(code!!)).asImageBitmap() }
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("Scan to add me") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.background(androidx.compose.ui.graphics.Color.White).padding(8.dp)) {
                        Image(bitmap, contentDescription = "Friend QR code", modifier = Modifier.size(240.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(Account.formatCode(code!!), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleLarge)
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun BackupCard(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val last by Prefs.lastBackup.collectAsStateWithLifecycle()
    val auto by Prefs.autoBackup.collectAsStateWithLifecycle()
    val share by Prefs.shareRoutes.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                if (last == 0L) "Not backed up yet" else "Last backup: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = busy == null, onClick = {
                    busy = "Backing up…"
                    scope.launch {
                        runCatching { Backup.backUp(context) }
                            .onSuccess { snackbar.showSnackbar(if (it == 0) "Already up to date" else "Backed up $it day(s)") }
                            .onFailure { snackbar.showSnackbar("Backup failed: ${it.message}") }
                        busy = null
                    }
                }) { Text("Back up now") }
                OutlinedButton(enabled = busy == null, onClick = { confirmRestore = true }) { Text("Restore") }
            }
            busy?.let {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            ToggleRow("Back up automatically", "Once a day when online", auto) { Prefs.setAutoBackup(it) }
            ToggleRow(
                "Share my routes with friends",
                "Friends see your routes and travel modes, never dates or times",
                share,
            ) {
                Prefs.setShareRoutes(it)
                SyncWorker.runNow(context)
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from backup?") },
            text = { Text("Points from your backup are merged into this phone's history. Nothing here is deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    busy = "Restoring…"
                    scope.launch {
                        runCatching { Backup.restore(context) }
                            .onSuccess { snackbar.showSnackbar("Restored ${formatCount(it.toLong())} points") }
                            .onFailure { snackbar.showSnackbar("Restore failed: ${it.message}") }
                        busy = null
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FriendsList(snackbar: SnackbarHostState, onCompare: (Friend) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var showEnter by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Friend?>(null) }
    val friends by produceState<List<Friend>?>(null, reload) {
        value = runCatching { Friends.list() }.getOrElse {
            snackbar.showSnackbar("Couldn't load friends: ${it.message}")
            emptyList()
        }
    }

    fun add(code: String) {
        scope.launch {
            runCatching { Friends.addByCode(code) }
                .onSuccess {
                    snackbar.showSnackbar("Added $it")
                    reload++
                }
                .onFailure { snackbar.showSnackbar(it.message ?: "Couldn't add friend") }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Your friends", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                            GmsBarcodeScanning.getClient(context, options).startScan().await()
                        }.onSuccess { barcode ->
                            val code = barcode.rawValue?.let(Account::parseCode)
                            if (code == null) snackbar.showSnackbar("That QR code isn't a Location Ticker ID") else add(code)
                        }.onFailure { snackbar.showSnackbar("Scan cancelled") }
                    }
                }) { Text("Scan QR") }
                OutlinedButton(onClick = { showEnter = true }) { Text("Enter ID") }
            }
            Spacer(Modifier.height(8.dp))
            when (val list = friends) {
                null -> CircularProgressIndicator(Modifier.size(24.dp))
                else -> if (list.isEmpty()) {
                    Text(
                        "No friends yet. Share your ID or QR code, or scan theirs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    list.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onCompare(f) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(36.dp).background(FriendBlue, CircleShape), contentAlignment = Alignment.Center) {
                                Text(f.name.take(1).uppercase(), color = androidx.compose.ui.graphics.Color.White)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${Insights.km(f.totalKm)} · ${formatCount(f.cells)} areas · ${f.cities.size} cities",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { removing = f }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }

    if (showEnter) {
        var text by remember { mutableStateOf("") }
        val parsed = Account.parseCode(text)
        AlertDialog(
            onDismissRequest = { showEnter = false },
            title = { Text("Add a friend") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Their ID, e.g. ABCD-2345") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = parsed != null, onClick = {
                    showEnter = false
                    parsed?.let(::add)
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showEnter = false }) { Text("Cancel") } },
        )
    }

    removing?.let { f ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${f.name}?") },
            text = { Text("You'll stop seeing each other's routes.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch {
                        runCatching { Friends.remove(context, f.uid) }
                            .onFailure { snackbar.showSnackbar("Couldn't remove: ${it.message}") }
                        reload++
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InviteDialog(code: String, signedIn: Boolean, snackbar: SnackbarHostState, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Add friend?") },
        text = {
            Text(
                if (signedIn) "Add the person with ID ${Account.formatCode(code)} as a friend? You'll be able to see each other's shared routes."
                else "Sign in on the Friends tab first, then open the invite again.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (signedIn) {
                    scope.launch {
                        runCatching { Friends.addByCode(code) }
                            .onSuccess { snackbar.showSnackbar("Added $it") }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Couldn't add friend") }
                    }
                }
                onDone()
            }) { Text(if (signedIn) "Add" else "OK") }
        },
        dismissButton = {
            if (signedIn) TextButton(onClick = onDone) { Text("Cancel") }
        },
    )
}

private fun shareInvite(context: Context, code: String) {
    val text = "Add me on Location Ticker so we can compare routes! My ID is ${Account.formatCode(code)}\n${QrCode.inviteLink(code)}"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share your ID"))
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
