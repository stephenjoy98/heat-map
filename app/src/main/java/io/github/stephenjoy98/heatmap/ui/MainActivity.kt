package io.github.stephenjoy98.heatmap.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.cloud.Account
import io.github.stephenjoy98.heatmap.cloud.Friend
import io.github.stephenjoy98.heatmap.cloud.SyncWorker
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.roads.RoadSnapWorker
import io.github.stephenjoy98.heatmap.tracking.TripDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Friend code from an invite link, waiting to be confirmed. */
    private val invite = MutableStateFlow<String?>(null)
    private val requestedTab = MutableStateFlow<Int?>(null)
    private val openFriends = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val dark = isDarkTheme()
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            TickerTheme(dark) {
                val onboarded by Prefs.onboarded.collectAsStateWithLifecycle()
                if (onboarded) AppScaffold(invite, requestedTab, openFriends) else OnboardingScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.data?.takeIf { it.scheme == "locationticker" && it.host == "friend" }?.let { uri ->
            Account.parseCode(uri.toString())?.let {
                invite.value = it
                openFriends.value = true
            }
        }
        if (intent.hasExtra(EXTRA_TAB)) requestedTab.value = intent.getIntExtra(EXTRA_TAB, TAB_MAP)
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_MAP = 0
        const val TAB_TIMELINE = 1
        const val TAB_REVIEW = 2
    }
}

@Composable
private fun AppScaffold(
    invite: MutableStateFlow<String?>,
    requestedTab: MutableStateFlow<Int?>,
    openFriends: MutableStateFlow<Boolean>,
) {
    val context = LocalContext.current
    val store = remember { PointStore.get(context) }
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(MainActivity.TAB_MAP) }
    var comparing by remember { mutableStateOf<Friend?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var friendsOpen by rememberSaveable { mutableStateOf(false) }
    var backupOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val friendsRequested by openFriends.collectAsStateWithLifecycle()
    var resumeTick by remember { mutableIntStateOf(0) }
    val pendingInvite by invite.collectAsStateWithLifecycle()
    val wantedTab by requestedTab.collectAsStateWithLifecycle()
    val user by Account.user.collectAsStateWithLifecycle()
    val changes by store.changes.collectAsStateWithLifecycle()
    val questions by produceState(0, changes, resumeTick) {
        value = withContext(Dispatchers.IO) { store.tripsNeedingReview().size + store.openRoadQuestions().size }
    }

    LaunchedEffect(friendsRequested) {
        if (friendsRequested) {
            friendsOpen = true
            openFriends.value = false
        }
    }

    LaunchedEffect(wantedTab) {
        wantedTab?.let {
            tab = it
            requestedTab.value = null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Catch up on trip detection and sync whenever the app comes to the front.
    LaunchedEffect(resumeTick) {
        withContext(Dispatchers.IO) { TripDetector.process(context) }
        RoadSnapWorker.runNow(context)
        if (user != null) SyncWorker.runNow(context)
    }

    val openProfile = { menuOpen = true }
    if (menuOpen) {
        AccountMenu(
            onDismiss = { menuOpen = false },
            onProfile = { profileOpen = true },
            onFriends = { friendsOpen = true },
            onBackup = { backupOpen = true },
            onSettings = { settingsOpen = true },
            onAbout = { aboutOpen = true },
        )
    }

    if (settingsOpen) {
        SettingsScreen(store = store, resumeTick = resumeTick, onBack = { settingsOpen = false })
        return
    }

    if (aboutOpen) {
        AboutScreen(onBack = { aboutOpen = false })
        return
    }

    comparing?.let { friend ->
        CompareScreen(friend, onBack = { comparing = null })
        return
    }

    if (backupOpen) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
            BackupScreen(contentPadding = padding, snackbar = snackbar, onBack = { backupOpen = false })
        }
        return
    }

    if (friendsOpen) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
            FriendsScreen(
                contentPadding = padding,
                snackbar = snackbar,
                pendingInvite = pendingInvite,
                onInviteHandled = { invite.value = null },
                onCompare = { comparing = it },
                onBack = { friendsOpen = false },
            )
        }
        return
    }

    if (profileOpen) {
        ProfileScreen(
            resumeTick = resumeTick,
            onBack = { profileOpen = false },
            onExplore = {
                profileOpen = false
                tab = MainActivity.TAB_REVIEW
            },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MainActivity.TAB_MAP,
                    onClick = { tab = MainActivity.TAB_MAP },
                    icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                    label = { Text("Map") },
                )
                NavigationBarItem(
                    selected = tab == MainActivity.TAB_TIMELINE,
                    onClick = { tab = MainActivity.TAB_TIMELINE },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text("Timeline") },
                )
                NavigationBarItem(
                    selected = tab == MainActivity.TAB_REVIEW,
                    onClick = { tab = MainActivity.TAB_REVIEW },
                    icon = {
                        BadgedBox(badge = { if (questions > 0) Badge { Text(questions.toString()) } }) {
                            Icon(Icons.Filled.Star, contentDescription = null)
                        }
                    },
                    label = { Text("Review") },
                )
            }
        },
    ) { padding ->
        when (tab) {
            MainActivity.TAB_TIMELINE -> TimelineScreen(padding, resumeTick, onProfile = openProfile)
            MainActivity.TAB_REVIEW -> ReviewScreen(padding, resumeTick, onProfile = openProfile)
            else -> MapScreen(padding, snackbar, resumeTick, onOpenProfile = openProfile)
        }
    }
}
