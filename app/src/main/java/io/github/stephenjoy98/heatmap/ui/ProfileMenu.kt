package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import io.github.stephenjoy98.heatmap.cloud.Account
import io.github.stephenjoy98.heatmap.cloud.FirebaseGate
import io.github.stephenjoy98.heatmap.cloud.SyncWorker
import kotlinx.coroutines.launch

private val avatarColors = listOf(
    Color(0xFF1A73E8), Color(0xFFE8710A), Color(0xFF188038), Color(0xFFA142F4), Color(0xFFD93025), Color(0xFF12B5CB),
)

/** Material's "group" icon (two people); the core icon set doesn't include it. */
val FriendsIcon: ImageVector by lazy {
    ImageVector.Builder("Friends", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes(
                "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3zM8,11c1.66,0 2.99,-1.34 " +
                    "2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3zM8,13c-2.33,0 -7,1.17 -7,3.5L1,19h14v-2.5c0,-2.33 " +
                    "-4.67,-3.5 -7,-3.5zM16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45L17,19h6v-2.5c0,-2.33 " +
                    "-4.67,-3.5 -7,-3.5z"
            ),
            fill = SolidColor(Color.Black),
        )
    }.build()
}

/**
 * A round avatar: the sign-in provider's profile picture, falling back to the account's initial in a
 * colour derived from its name, or a person icon when signed out.
 */
@Composable
fun Avatar(size: Int = 32) {
    val user by Account.user.collectAsStateWithLifecycle()
    val name = user?.displayName ?: user?.email
    val photo = user?.photoUrl?.toString()
    val fallback: @Composable () -> Unit = {
        if (name.isNullOrBlank()) {
            Box(
                Modifier.size(size.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size((size * 0.62f).dp),
                )
            }
        } else {
            Box(
                Modifier.size(size.dp).background(avatarColors[Math.floorMod(name.hashCode(), avatarColors.size)], CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.take(1).uppercase(), color = Color.White, fontSize = (size * 0.45f).sp)
            }
        }
    }
    if (photo == null) {
        fallback()
    } else {
        SubcomposeAsyncImage(
            model = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape),
            loading = { fallback() },
            error = { fallback() },
        )
    }
}

/** The account button for the top-right corner of every tab, like Google's apps. */
@Composable
fun ProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) { Avatar(32) }
}

/**
 * Google-style account menu: who's signed in, then Profile, Friends, Backup, Settings and signing
 * in or out.
 */
@Composable
fun AccountMenu(
    onDismiss: () -> Unit,
    onProfile: () -> Unit,
    onFriends: () -> Unit,
    onBackup: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by Account.user.collectAsStateWithLifecycle()
    val code by Account.friendCode.collectAsStateWithLifecycle()
    val configured = remember { FirebaseGate.isConfigured(context) }
    var signingIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun go(action: () -> Unit) {
        onDismiss()
        action()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                    Text("Location Ticker", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center))
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(64)
                        Spacer(Modifier.height(10.dp))
                        if (user != null) {
                            Text(user?.displayName ?: "Signed in", style = MaterialTheme.typography.titleMedium)
                            user?.email?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            code?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Your ID  ${Account.formatCode(it)}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            Text("Not signed in", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (configured) "Sign in to back up your history and add friends."
                                else "Sign-in isn't set up in this build. Everything else works on this phone.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (configured) {
                                Spacer(Modifier.height(12.dp))
                                Button(enabled = !signingIn, onClick = {
                                    val activity = context.findActivity() ?: return@Button
                                    signingIn = true
                                    error = null
                                    scope.launch {
                                        runCatching { Account.signIn(activity) }
                                            .onSuccess { SyncWorker.runNow(context) }
                                            .onFailure { error = it.message }
                                        signingIn = false
                                    }
                                }) { Text(if (signingIn) "Signing in…" else "Sign in with Google") }
                                error?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                MenuRow(Icons.Filled.AccountCircle, "Profile", "Your stats and the places you've explored") { go(onProfile) }
                MenuRow(FriendsIcon, "Friends", "Add friends and compare routes and explored areas") { go(onFriends) }
                MenuRow(Icons.Filled.Refresh, "Backup", "Back up or restore your location history") { go(onBackup) }
                MenuRow(Icons.Filled.Settings, "Settings", null) { go(onSettings) }
                if (user != null) {
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                    MenuRow(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", null) {
                        scope.launch { Account.signOut(context) }
                    }
                }
            }
        }
    }
}

/** A menu row with an icon, title and optional subtitle. */
@Composable
internal fun MenuRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
