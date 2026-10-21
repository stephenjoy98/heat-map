package io.github.stephenjoy98.heatmap.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Full-screen pages opened over the tabs. Back walks back through them like breadcrumbs. */
enum class Page { ACCOUNT, PROFILE, FRIENDS, BACKUP, SETTINGS, ABOUT, CHANGELOG, REVIEW, COMPARE, FRIEND }

/** True while a page covers the tabs, so the screens underneath can pause live work such as GPS. */
val LocalCovered = compositionLocalOf { false }

/** The open pages, oldest first. Survives rotation and process death. */
class PageStack(initial: List<Page>) {
    val pages = mutableStateListOf<Page>().apply { addAll(initial) }

    /** The top page while it animates away after a back. */
    var leaving by mutableStateOf<Page?>(null)
        private set

    // Pages that have finished their entrance, so returning to them doesn't replay it.
    internal val entered = HashSet<Page>(initial)

    val top: Page? get() = pages.lastOrNull()

    /** Opens [page] on top; if it's already open further down, it moves to the top instead of repeating. */
    fun push(page: Page) {
        if (pages.lastOrNull() == page) return
        pages.remove(page)
        entered.remove(page)
        pages.add(page)
    }

    /** Animates the top page away, then removes it. */
    fun pop() {
        if (leaving == null) leaving = pages.lastOrNull()
    }

    /** Closes every page at once (no animation), back to the tabs. */
    fun clear() {
        pages.clear()
        entered.clear()
        leaving = null
    }

    internal fun finishPop() {
        leaving?.let {
            pages.remove(it)
            entered.remove(it)
        }
        leaving = null
    }

    companion object {
        val Saver = listSaver<PageStack, String>(
            save = { s -> s.pages.map { it.name } },
            restore = { names -> PageStack(names.mapNotNull { n -> Page.entries.firstOrNull { it.name == n } }) },
        )
    }
}

@Composable
fun rememberPageStack(): PageStack = rememberSaveable(saver = PageStack.Saver) { PageStack(emptyList()) }

/**
 * Shows [base] (the tabs) with the stack's pages on top. Back is a predictive gesture: the top page shrinks,
 * rounds its corners and follows the swipe, revealing the page (or tabs) underneath, then slides away when
 * the gesture completes or springs back when it's cancelled. Pages keep their scroll position when you come
 * back to them.
 */
@Composable
fun PageHost(stack: PageStack, base: @Composable () -> Unit, page: @Composable (Page) -> Unit) {
    val gesture = remember { Animatable(0f) }
    var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val scope = rememberCoroutineScope()
    val saved = rememberSaveableStateHolder()

    PredictiveBackHandler(enabled = stack.pages.isNotEmpty() && stack.leaving == null) { events ->
        try {
            events.collect { e ->
                edge = e.swipeEdge
                gesture.snapTo(e.progress)
            }
            stack.pop()
        } catch (e: CancellationException) {
            scope.launch { gesture.animateTo(0f) }
            throw e
        }
    }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalCovered provides stack.pages.isNotEmpty()) { base() }
        val pages = stack.pages
        pages.forEachIndexed { i, p ->
            val isTop = i == pages.lastIndex
            // The top page, and the one below it kept ready underneath, so going back reveals it straight away
            // rather than flashing the tabs while it's built.
            if (i >= pages.lastIndex - 1) key(p) {
                PageFrame(
                    stack = stack,
                    page = p,
                    gesture = if (isTop) gesture.value else 0f,
                    edge = edge,
                    leaving = isTop && stack.leaving == p,
                    onLeft = {
                        stack.finishPop()
                        // A page that's been closed opens fresh next time.
                        saved.removeState(p.name)
                        scope.launch { gesture.snapTo(0f) }
                    },
                ) {
                    CompositionLocalProvider(LocalCovered provides !isTop) {
                        saved.SaveableStateProvider(p.name) { page(p) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageFrame(
    stack: PageStack,
    page: Page,
    gesture: Float,
    edge: Int,
    leaving: Boolean,
    onLeft: () -> Unit,
    content: @Composable () -> Unit,
) {
    val enter = remember { Animatable(if (page in stack.entered) 1f else 0f) }
    val spring = spatialSpring<Float>()
    LaunchedEffect(leaving) {
        if (leaving) {
            enter.animateTo(0f, tween(220))
            onLeft()
        } else {
            enter.animateTo(1f, spring)
            stack.entered += page
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val e = enter.value
                val scale = 1f - 0.1f * gesture
                scaleX = scale
                scaleY = scale
                // Follows the swipe a little, and slides in from (or out to) the side.
                val towards = if (edge == BackEventCompat.EDGE_LEFT) 1f else -1f
                translationX = towards * gesture * 24.dp.toPx() + (1f - e) * size.width * 0.15f
                alpha = e.coerceIn(0f, 1f)
                shape = RoundedCornerShape((32 * gesture).dp)
                clip = gesture > 0f
            },
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
}

/** Records where a composable sits on screen, so a popup can grow out of it. */
fun Modifier.trackBounds(onBounds: (Rect) -> Unit): Modifier = onGloballyPositioned { c ->
    onBounds(Rect(c.positionOnScreen(), c.size.toSize()))
}

/**
 * A popup that grows out of [source] (the thing that was tapped) and shrinks back into it when dismissed,
 * as Material motion suggests. [content] gets a `dismiss` function that plays the closing animation first.
 */
@Composable
fun GrowFromSource(
    source: Rect?,
    onDismiss: () -> Unit,
    alignment: Alignment = Alignment.Center,
    modifier: Modifier = Modifier,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var target by remember { mutableStateOf<Rect?>(null) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val spring = spatialSpring<Float>()
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, tween(200))
                onDismiss()
            }
        }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, spring) }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss),
            contentAlignment = alignment,
        ) {
            Box(
                modifier
                    .trackBounds { target = it }
                    .graphicsLayer {
                        val p = progress.value
                        val t = target
                        val s = source
                        if (t != null && s != null && t.width > 0f && t.height > 0f) {
                            scaleX = lerp(s.width / t.width, 1f, p)
                            scaleY = lerp(s.height / t.height, 1f, p)
                            translationX = (s.center.x - t.center.x) * (1f - p)
                            translationY = (s.center.y - t.center.y) * (1f - p)
                        } else {
                            scaleX = lerp(0.85f, 1f, p)
                            scaleY = lerp(0.85f, 1f, p)
                        }
                        alpha = (p * 2f).coerceIn(0f, 1f)
                    }
                    // Taps on the popup itself shouldn't fall through and close it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) { content(dismiss) }
        }
    }
}
