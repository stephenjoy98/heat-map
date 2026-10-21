package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * The account button for the top-right corner of every tab, like Google's apps. A badge shows how many
 * trips and roads are waiting for an answer on the Timeline.
 */
@Composable
fun ProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier, background: Color? = null) {
    val pending = LocalReviewCount.current
    val waiting = Attention.accountWaiting()
    val tip = when {
        pending > 0 -> "Your account · $pending waiting for your answer on the Timeline"
        waiting -> "Your account · something new to look at"
        else -> "Your account: profile, friends, insights, backup and settings"
    }
    Tip(tip, modifier) {
        // The badge sits beside the button, not inside it, so neither the button's nor its background's round
        // clip can cut it off.
        Box {
            val button: @Composable () -> Unit = {
                IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = "Account" }) { Avatar(32) }
            }
            if (background != null) {
                Surface(shape = CircleShape, color = background, shadowElevation = 3.dp) { button() }
            } else {
                button()
            }
            if (pending > 0) {
                Badge(Modifier.align(Alignment.TopEnd)) {
                    Text(if (pending > 99) "99+" else pending.toString())
                }
            } else if (waiting) {
                AttentionDot(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp))
            }
        }
    }
}

/**
 * The account page, opened from the account button: who's signed in, any questions waiting, then Profile,
 * Friends, Backup, Insights, Settings, About and signing in or out. It's a page like the others, so going back
 * from one of them returns here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onFriends: () -> Unit,
    onBackup: () -> Unit,
    onReview: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onQuestions: () -> Unit = {},
    reviewCount: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by Account.user.collectAsStateWithLifecycle()
    val code by Account.friendCode.collectAsStateWithLifecycle()
    val configured = remember { FirebaseGate.isConfigured(context) }
    var signingIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Account") },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(72)
                    Spacer(Modifier.height(12.dp))
                    if (user != null) {
                        Text(user?.displayName ?: "Signed in", style = MaterialTheme.typography.titleLarge)
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
                        Text("Not signed in", style = MaterialTheme.typography.titleLarge)
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

            // What the badge on the account button is about, highlighted, and a tap away.
            if (reviewCount > 0) {
                Surface(
                    onClick = onQuestions,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Badge { Text(if (reviewCount > 99) "99+" else reviewCount.toString()) }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (reviewCount == 1) "1 question waiting" else "$reviewCount questions waiting",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "How you travelled, which road you took and heights · on the Timeline's Questions tab",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    // A red dot where a notification's subject waits; "New" where something changed since you last looked.
                    MenuRow(Icons.Filled.AccountCircle, "Profile", "Your stats and the places you've explored", new = Attention.anyNew(Attention.PROFILE)) {
                        Attention.seen(Attention.PROFILE)
                        onProfile()
                    }
                    MenuRow(FriendsIcon, "Friends", "Add friends and compare routes and explored areas", new = Attention.anyNew(Attention.FRIENDS)) {
                        Attention.seen(Attention.FRIENDS)
                        onFriends()
                    }
                    MenuRow(Icons.Filled.Refresh, "Backup", "Back up or restore your location history", new = Attention.anyNew(Attention.BACKUP)) {
                        Attention.seen(Attention.BACKUP)
                        onBackup()
                    }
                    MenuRow(Icons.Filled.Star, "Insights", "Your stats, steps and write-ups about your travels", new = Attention.anyNew(Attention.INSIGHTS)) {
                        Attention.seen(Attention.INSIGHTS)
                        onReview()
                    }
                    MenuRow(
                        Icons.Filled.Settings, "Settings", "Recording, places, appearance, map and more",
                        dot = Attention.updateWaiting(), new = Attention.anyNew(Attention.SETTINGS), onClick = onSettings,
                    )
                    MenuRow(
                        Icons.Filled.Info, "About", "Who made it, open source and licences",
                        dot = Attention.changelogUnread(), new = Attention.anyNew(Attention.ABOUT),
                    ) {
                        Attention.seen(Attention.ABOUT)
                        onAbout()
                    }
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
}

/** A menu row with an icon, title and optional subtitle, and a red [dot] or a [new] marker at its end. */
@Composable
internal fun MenuRow(icon: ImageVector, title: String, subtitle: String?, dot: Boolean = false, new: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (new) {
            Spacer(Modifier.width(8.dp))
            NewMarker()
        }
        if (dot) {
            Spacer(Modifier.width(8.dp))
            AttentionDot()
        }
    }
}
