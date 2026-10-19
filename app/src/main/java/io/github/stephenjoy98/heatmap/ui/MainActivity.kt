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
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.BuildConfig
import io.github.stephenjoy98.heatmap.cloud.Account
import io.github.stephenjoy98.heatmap.cloud.Friend
import io.github.stephenjoy98.heatmap.cloud.SyncWorker
import io.github.stephenjoy98.heatmap.data.Changelog
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

/** Pending review questions, shown as a badge on the account button. */
val LocalReviewCount = compositionLocalOf { 0 }

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
    val stack = rememberPageStack()
    val tabState = rememberSaveableStateHolder()
    var comparing by remember { mutableStateOf<Friend?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    // After an update, the changes since the version the user last saw.
    var whatsNew by remember {
        mutableStateOf(Changelog.whatsNew(Changelog.load(context), Prefs.lastSeenVersion, BuildConfig.VERSION_NAME))
    }
    val friendsRequested by openFriends.collectAsStateWithLifecycle()
    var resumeTick by remember { mutableIntStateOf(0) }
    val pendingInvite by invite.collectAsStateWithLifecycle()
    val wantedTab by requestedTab.collectAsStateWithLifecycle()
    val user by Account.user.collectAsStateWithLifecycle()
    val tracking by Prefs.tracking.collectAsStateWithLifecycle()
    val changes by store.changes.collectAsStateWithLifecycle()
    val questions by produceState(0, changes, resumeTick) {
        value = withContext(Dispatchers.IO) { store.tripsNeedingReview().size + store.openRoadQuestions().size }
    }

    // Back, when no page is open: from another tab go to the map; on the map, ask before leaving.
    // Pages handle their own back (with the predictive animation) inside PageHost.
    BackHandler(enabled = stack.pages.isEmpty() && tab != MainActivity.TAB_MAP) { tab = MainActivity.TAB_MAP }
    BackHandler(enabled = stack.pages.isEmpty() && tab == MainActivity.TAB_MAP) { confirmExit = true }

    LaunchedEffect(friendsRequested) {
        if (friendsRequested) {
            stack.push(Page.FRIENDS)
            openFriends.value = false
        }
    }

    LaunchedEffect(wantedTab) {
        wantedTab?.let {
            if (it == MainActivity.TAB_REVIEW) stack.push(Page.REVIEW) else tab = it
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

    // Catch up on trip detection, sync and back up whenever the app comes to the front.
    LaunchedEffect(resumeTick) {
        withContext(Dispatchers.IO) {
            TripDetector.process(context)
            // Keeps km explored current, so the profile opens without recalculating it.
            store.updateExplored()
        }
        RoadSnapWorker.runNow(context)
        if (user != null) SyncWorker.runNow(context, appOpen = true)
    }

    LaunchedEffect(Unit) {
        if (whatsNew.isEmpty()) Prefs.setLastSeenVersion(BuildConfig.VERSION_NAME)
    }
    if (whatsNew.isNotEmpty()) {
        WhatsNewDialog(
            releases = whatsNew,
            onDismiss = {
                Prefs.setLastSeenVersion(BuildConfig.VERSION_NAME)
                whatsNew = emptyList()
            },
            onFullChangelog = { stack.push(Page.CHANGELOG) },
        )
    }

    if (confirmExit) {
        val activity = context.findActivity()
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Exit Location Ticker?") },
            text = {
                Text(if (tracking) "Recording keeps going in the background." else "Recording is paused.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    activity?.finish()
                }) { Text("Exit") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Stay") } },
        )
    }

    val openMenu = { menuOpen = true }
    if (menuOpen) {
        AccountMenu(
            reviewCount = questions,
            onDismiss = { menuOpen = false },
            onProfile = { stack.push(Page.PROFILE) },
            onFriends = { stack.push(Page.FRIENDS) },
            onBackup = { stack.push(Page.BACKUP) },
            onReview = { stack.push(Page.REVIEW) },
            onSettings = { stack.push(Page.SETTINGS) },
            onAbout = { stack.push(Page.ABOUT) },
        )
    }

    CompositionLocalProvider(LocalReviewCount provides questions) {
        PageHost(
            stack = stack,
            base = {
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
                        }
                    },
                ) { padding ->
                    // No crossfade: map views can't fade like the rest of the UI, so the tabs switch instantly.
                    // Each tab's map stays loaded (see AppMap's cacheKey), so coming back shows it straight away.
                    // Each tab keeps its place (map position, Timeline day) while you're on the other one.
                    tabState.SaveableStateProvider(tab) {
                        when (tab) {
                            MainActivity.TAB_TIMELINE -> TimelineScreen(padding, resumeTick, onProfile = openMenu)
                            else -> MapScreen(padding, snackbar, resumeTick, onOpenProfile = openMenu)
                        }
                    }
                }
            },
            page = { p ->
                val back = { stack.pop() }
                when (p) {
                    Page.PROFILE -> ProfileScreen(
                        resumeTick = resumeTick,
                        onBack = back,
                        onExplore = { stack.push(Page.REVIEW) },
                    )
                    Page.FRIENDS -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
                        FriendsScreen(
                            contentPadding = padding,
                            snackbar = snackbar,
                            pendingInvite = pendingInvite,
                            onInviteHandled = { invite.value = null },
                            onCompare = {
                                comparing = it
                                stack.push(Page.COMPARE)
                            },
                            onBack = back,
                        )
                    }
                    Page.BACKUP -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
                        BackupScreen(contentPadding = padding, snackbar = snackbar, onBack = back)
                    }
                    Page.SETTINGS -> SettingsScreen(store = store, resumeTick = resumeTick, onBack = back)
                    Page.ABOUT -> AboutScreen(onBack = back, onChangelog = { stack.push(Page.CHANGELOG) })
                    Page.CHANGELOG -> ChangelogScreen(onBack = back)
                    Page.REVIEW -> ReviewScreen(WindowInsets.navigationBars.asPaddingValues(), resumeTick, onBack = back)
                    Page.COMPARE -> {
                        val friend = comparing
                        if (friend != null) {
                            CompareScreen(friend, onBack = back)
                        } else {
                            // The friend isn't kept across process death; go back to the list.
                            LaunchedEffect(Unit) { stack.pop() }
                        }
                    }
                }
            },
        )
    }
}
