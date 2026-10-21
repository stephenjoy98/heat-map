package io.github.stephenjoy98.heatmap.ui

import android.content.Intent
import android.content.Context
import android.app.PendingIntent
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.stephenjoy98.heatmap.update.UpdateWorker
import io.github.stephenjoy98.heatmap.update.Updater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Friend code from an invite link, waiting to be confirmed. */
    private val invite = MutableStateFlow<String?>(null)
    private val requestedTab = MutableStateFlow<Int?>(null)
    private val openFriends = MutableStateFlow(false)
    /** A page a notification asked for ([PAGE_UPDATES], [PAGE_CHANGELOG]), waiting to be opened. */
    private val requestedPage = MutableStateFlow<String?>(null)
    private var stoppedAt = 0L

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
                if (onboarded) AppScaffold(invite, requestedTab, openFriends, requestedPage) else OnboardingScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Updater.inForeground = true
    }

    override fun onPause() {
        super.onPause()
        Updater.inForeground = false
    }

    override fun onStart() {
        super.onStart()
        // Back after a while away counts as opening the app again: a new version may be offered once more.
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > NEW_SESSION_AFTER_MS) UpdatePrompt.offeredThisSession = false
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
        // An update downloaded while you were using the app installs once you leave it.
        if (Prefs.autoUpdate.value) {
            val app = applicationContext
            CoroutineScope(Dispatchers.IO).launch { Updater.installIfWaiting(app) }
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
        intent.getStringExtra(EXTRA_PAGE)?.let { requestedPage.value = it }
        // Handled once: coming back to the activity later shouldn't open it again.
        intent.removeExtra(EXTRA_PAGE)
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_PAGE = "page"
        private const val NEW_SESSION_AFTER_MS = 30 * 60_000L
        const val PAGE_UPDATES = "updates"
        const val PAGE_CHANGELOG = "changelog"
        const val PAGE_QUESTIONS = "questions"

        /**
         * What a notification opens: the app on [page] (or just the app). Each page gets its own request code
         * and action, so Android keeps their PendingIntents apart; otherwise one notification's page would
         * replace every other's, since extras don't make intents different.
         */
        fun openIntent(context: Context, page: String? = null): PendingIntent {
            val code = when (page) {
                PAGE_UPDATES -> 10
                PAGE_CHANGELOG -> 11
                PAGE_QUESTIONS -> 12
                else -> 13
            }
            val intent = Intent(context, MainActivity::class.java)
                .setAction("${context.packageName}.OPEN${page?.let { ".$it" } ?: ""}")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (page != null) intent.putExtra(EXTRA_PAGE, page)
            return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        const val TAB_MAP = 0
        const val TAB_TIMELINE = 1
        const val TAB_REVIEW = 2
    }
}

/** Questions waiting on the Timeline, shown as a badge on the account button. */
val LocalReviewCount = compositionLocalOf { 0 }

@Composable
private fun AppScaffold(
    invite: MutableStateFlow<String?>,
    requestedTab: MutableStateFlow<Int?>,
    openFriends: MutableStateFlow<Boolean>,
    requestedPage: MutableStateFlow<String?>,
) {
    val context = LocalContext.current
    val store = remember { PointStore.get(context) }
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(MainActivity.TAB_MAP) }
    val stack = rememberPageStack()
    val tabState = rememberSaveableStateHolder()
    var comparing by remember { mutableStateOf<Friend?>(null) }
    var viewingFriend by remember { mutableStateOf<Friend?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    // After an update, the changes since the version the user last saw.
    var whatsNew by remember {
        mutableStateOf(Changelog.whatsNew(Changelog.load(context), Prefs.lastSeenVersion, BuildConfig.VERSION_NAME))
    }
    val friendsRequested by openFriends.collectAsStateWithLifecycle()
    var resumeTick by remember { mutableIntStateOf(0) }
    val pendingInvite by invite.collectAsStateWithLifecycle()
    val wantedTab by requestedTab.collectAsStateWithLifecycle()
    val wantedPage by requestedPage.collectAsStateWithLifecycle()
    val user by Account.user.collectAsStateWithLifecycle()
    val tracking by Prefs.tracking.collectAsStateWithLifecycle()
    val changes by store.changes.collectAsStateWithLifecycle()
    val questions by produceState(0, changes, resumeTick) {
        value = withContext(Dispatchers.IO) { store.tripsNeedingReview().size + store.openRoadQuestions().size + store.openLevelQuestions().size }
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

    // From a notification: Settings › Updates for a new version, the changelog after an update.
    LaunchedEffect(wantedPage) {
        when (wantedPage) {
            MainActivity.PAGE_UPDATES -> {
                SettingsNav.openUpdates = true
                stack.push(Page.SETTINGS)
            }
            MainActivity.PAGE_QUESTIONS -> {
                TimelineTabs.questions = true
                tab = MainActivity.TAB_TIMELINE
                // Questions live on a tab, so close any pages covering it.
                stack.clear()
            }
            MainActivity.PAGE_CHANGELOG -> {
                // The full changelog replaces the "What's new" popup.
                Prefs.setLastSeenVersion(BuildConfig.VERSION_NAME)
                whatsNew = emptyList()
                stack.push(Page.CHANGELOG)
            }
        }
        requestedPage.value = null
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
        UpdateWorker.runNow(context)
        if (user != null) SyncWorker.runNow(context, appOpen = true)
    }

    LaunchedEffect(Unit) {
        if (whatsNew.isEmpty()) Prefs.setLastSeenVersion(BuildConfig.VERSION_NAME)
    }
    // Sheets for "What's new" and a new version slide up only on the map screen, never over another page.
    val onHome = stack.pages.isEmpty() && tab == MainActivity.TAB_MAP
    if (whatsNew.isNotEmpty() && onHome) {
        WhatsNewDialog(
            releases = whatsNew,
            onDismiss = {
                Prefs.setLastSeenVersion(BuildConfig.VERSION_NAME)
                Attention.changelogSeen()
                whatsNew = emptyList()
            },
            onFullChangelog = { stack.push(Page.CHANGELOG) },
        )
    }

    // A newer version, offered with its changelog once per app session (not on top of "What's new").
    if (whatsNew.isEmpty()) UpdateSheet(onHome)

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

    val openMenu = { stack.push(Page.ACCOUNT) }
    LaunchedEffect(tab) {
        if (tab == MainActivity.TAB_TIMELINE) Attention.seen(Attention.TIMELINE)
        if (tab != MainActivity.TAB_MAP) MapChrome.fullscreen = false
    }

    CompositionLocalProvider(LocalReviewCount provides questions) {
        PageHost(
            stack = stack,
            base = {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0),
                    snackbarHost = { SwipeSnackbarHost(snackbar) },
                    bottomBar = {
                        // Hidden while the map is full screen.
                        if (!(tab == MainActivity.TAB_MAP && MapChrome.fullscreen)) NavigationBar {
                            NavigationBarItem(
                                selected = tab == MainActivity.TAB_MAP,
                                onClick = { tab = MainActivity.TAB_MAP },
                                icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                                label = { Text("Map") },
                            )
                            NavigationBarItem(
                                selected = tab == MainActivity.TAB_TIMELINE,
                                onClick = { tab = MainActivity.TAB_TIMELINE },
                                icon = {
                                    // Questions wait on the Timeline's Questions tab.
                                    // Otherwise "New" while it has changes you haven't opened it to see.
                                    BadgedBox(
                                        badge = {
                                            if (questions > 0) Badge { Text(if (questions > 99) "99+" else questions.toString()) }
                                            else if (Attention.isNew(Attention.TIMELINE)) NewMarker()
                                        },
                                    ) {
                                        Icon(Icons.Filled.DateRange, contentDescription = null)
                                    }
                                },
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
                    Page.ACCOUNT -> AccountScreen(
                        reviewCount = questions,
                        onBack = back,
                        onProfile = { stack.push(Page.PROFILE) },
                        onFriends = { stack.push(Page.FRIENDS) },
                        onBackup = { stack.push(Page.BACKUP) },
                        onReview = { stack.push(Page.REVIEW) },
                        onSettings = { stack.push(Page.SETTINGS) },
                        onAbout = { stack.push(Page.ABOUT) },
                        onQuestions = {
                            TimelineTabs.questions = true
                            tab = MainActivity.TAB_TIMELINE
                            stack.pop()
                        },
                    )
                    Page.PROFILE -> ProfileScreen(
                        resumeTick = resumeTick,
                        onBack = back,
                        onExplore = { stack.push(Page.REVIEW) },
                    )
                    Page.FRIENDS -> Scaffold(snackbarHost = { SwipeSnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
                        FriendsScreen(
                            contentPadding = padding,
                            snackbar = snackbar,
                            pendingInvite = pendingInvite,
                            onInviteHandled = { invite.value = null },
                            onOpenFriend = {
                                viewingFriend = it
                                stack.push(Page.FRIEND)
                            },
                            onBack = back,
                        )
                    }
                    Page.BACKUP -> Scaffold(snackbarHost = { SwipeSnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
                        BackupScreen(contentPadding = padding, snackbar = snackbar, onBack = back)
                    }
                    Page.SETTINGS -> SettingsScreen(store = store, resumeTick = resumeTick, onBack = back)
                    Page.ABOUT -> AboutScreen(onBack = back, onChangelog = { stack.push(Page.CHANGELOG) })
                    Page.CHANGELOG -> ChangelogScreen(onBack = back)
                    Page.REVIEW -> ReviewScreen(WindowInsets.navigationBars.asPaddingValues(), resumeTick, onBack = back)
                    Page.FRIEND -> {
                        val friend = viewingFriend
                        if (friend != null) {
                            FriendProfileScreen(
                                friend = friend,
                                onBack = back,
                                onCompare = {
                                    comparing = friend
                                    stack.push(Page.COMPARE)
                                },
                            )
                        } else {
                            LaunchedEffect(Unit) { stack.pop() }
                        }
                    }
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
