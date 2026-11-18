package com.villamediana.sound.ui.tube

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.online.TubeLink
import com.villamediana.sound.tube.online.VideoItem
import com.villamediana.sound.tube.player.TubePlayer
import com.villamediana.sound.ui.components.pressScale
import com.villamediana.sound.ui.nav.Inbound
import com.villamediana.sound.ui.theme.FixedColors
import com.villamediana.sound.ui.theme.LocalBottomInset
import com.villamediana.sound.ui.theme.LocalSoundColors
import com.villamediana.sound.ui.theme.SoundColors
import com.villamediana.sound.ui.theme.searchPalette
import com.villamediana.sound.ui.theme.Motion
import com.villamediana.sound.ui.theme.sc
import com.villamediana.sound.ui.video.rememberVideoProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** FreeVideo's shell: the pages, the bottom bar and the mini player over them. */
@Composable
fun TubeRoot(incoming: MutableStateFlow<Inbound?>) {
    val controller = rememberNavController()
    val nav = remember(controller) { TubeNavigator(controller) }
    val tube = SoundApplication.graph.tube
    val pState by tube.player.state.collectAsState()
    val entry by controller.currentBackStackEntryAsState()
    val dest = entry?.destination
    val full = dest?.isFull() == true
    val showMini = pState.active && !full
    val background by remember { tube.settings.prefs.map { it.keepPlayingInBackground } }.collectAsState(initial = false)

    // Links shared to FreeVideo or opened from the browser: the video, channel or playlist.
    val inbound by incoming.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(inbound) {
        val item = inbound ?: return@LaunchedEffect
        incoming.value = null
        val text = when (item) {
            is Inbound.Link -> item.text
            is Inbound.Query -> item.text
        }
        when (val link = tube.source.resolve(text)) {
            is TubeLink.Video -> nav.openVideo(VideoItem(link.id, "", ""), startMs = link.startMs.takeIf { it > 0 } ?: -1)
            is TubeLink.Channel -> nav.openChannel(link.url)
            is TubeLink.Playlist -> nav.openPlaylist(link.url)
            null -> {
                SearchInbox.query = text
                nav.openSearch()
            }
        }
    }

    val activity = androidx.activity.compose.LocalActivity.current as? androidx.activity.ComponentActivity
    val pipEnabled by remember { tube.settings.prefs.map { it.pictureInPicture } }.collectAsState(initial = true)
    val inPip = rememberPipMode(activity, tube.player)
    PipParams(activity, pState, pipEnabled)
    // A floating window is "in the background" too, but the video must go on in it.
    PauseInBackground(tube.player, keepPlaying = background || inPip || (pipEnabled && android.os.Build.VERSION.SDK_INT >= 26))

    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The search page is its own colored surface, edge to edge: no bar under it (the mini player stays).
    val barHidden = full || dest?.hasRoute(TubeSearchRoute::class) == true
    val bar = if (barHidden) 0.dp else BAR_HEIGHT
    val mini by animateDpAsState(if (showMini) MINI_HEIGHT + 8.dp else 0.dp, label = "mini")

    CompositionLocalProvider(
        LocalInPip provides inPip,
        LocalTubeNav provides nav,
        LocalBottomInset provides navBar + bar + mini + 12.dp,
    ) {
        Box(Modifier.fillMaxSize().background(sc.Ink)) {
            NavHost(
                navController = controller,
                startDestination = FeedRoute,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    when {
                        targetState.destination.isWatch() -> slideInVertically(tween(PAGE_MS, easing = Motion.Emphasized)) { it } + fadeIn(tween(200))
                        initialState.destination.isTab() && targetState.destination.isTab() -> fadeIn(tween(220))
                        else -> slideInHorizontally(tween(PAGE_MS, easing = Motion.Emphasized)) { it }
                    }
                },
                exitTransition = {
                    when {
                        targetState.destination.isWatch() -> ExitTransition.KeepUntilTransitionsFinished
                        initialState.destination.isTab() && targetState.destination.isTab() -> fadeOut(tween(160))
                        else -> slideOutHorizontally(tween(PAGE_MS, easing = Motion.Emphasized)) { -it / 4 }
                    }
                },
                popEnterTransition = {
                    when {
                        initialState.destination.isWatch() -> EnterTransition.None
                        initialState.destination.isTab() && targetState.destination.isTab() -> fadeIn(tween(220))
                        else -> slideInHorizontally(tween(PAGE_MS, easing = Motion.Emphasized)) { -it / 4 }
                    }
                },
                popExitTransition = {
                    when {
                        initialState.destination.isWatch() -> slideOutVertically(tween(PAGE_MS, easing = Motion.Emphasized)) { it } + fadeOut(tween(260))
                        initialState.destination.isTab() && targetState.destination.isTab() -> fadeOut(tween(160))
                        else -> slideOutHorizontally(tween(PAGE_MS, easing = Motion.Emphasized)) { it }
                    }
                },
            ) {
                composable<FeedRoute> { Page { FeedScreen() } }
                composable<SubsRoute> { Page { SubsScreen() } }
                composable<YouRoute> { Page { LibraryScreen() } }
                composable<TubeSearchRoute> {
                    // Search draws on the highlight color, as FreeSong's does.
                    val colors = remember(SoundColors.palette) { FixedColors(searchPalette(SoundColors.palette)) }
                    CompositionLocalProvider(LocalSoundColors provides colors) { Page(SoundColors.Search) { TubeSearchScreen() } }
                }
                composable<WatchRoute> { Page { WatchScreen(it.toRoute()) } }
                composable<ChannelRoute> { Page { ChannelScreen(it.toRoute()) } }
                composable<TubePlaylistRoute> { Page { PlaylistScreen(it.toRoute()) } }
                composable<VideoListRoute> { Page { VideoListScreen(it.toRoute()) } }
                composable<SubscriptionListRoute> { Page { SubscriptionListScreen() } }
                composable<TubeSettingsRoute> { Page { TubeSettingsScreen() } }
            }

            if (inPip) {
                // Floating window: only the video (the pages stay composed underneath, untouched).
                PipStage(tube.player)
                return@Box
            }
            // A new version on GitHub: the quiet check, the sheet and Android's install screens.
            com.villamediana.sound.ui.update.UpdateHost()
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                AnimatedVisibility(showMini, enter = fadeIn(tween(220)) + slideInVertically { it / 2 }, exit = fadeOut(tween(160))) {
                    MiniPlayer(pState, tube.player, onOpen = { pState.video?.let { nav.openVideo(it) } })
                }
                AnimatedVisibility(!barHidden, enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
                    BottomBar(dest, nav)
                }
                if (barHidden && !full) Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/** A query handed over to the Search page (a shared text that isn't a YouTube link). */
internal object SearchInbox {
    var query: String? = null
}

private const val PAGE_MS = 320
private val BAR_HEIGHT = 64.dp
private val MINI_HEIGHT = 64.dp

@Composable
private fun Page(color: Color = sc.Ink, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(color)) { content() }
}

/**
 * Without "tocar em segundo plano", the video pauses when the app leaves the screen and picks up
 * again when it comes back (as FreeSong's video does).
 */
@Composable
private fun PauseInBackground(player: TubePlayer, keepPlaying: Boolean) {
    val owner = LocalLifecycleOwner.current
    val keep by rememberUpdatedState(keepPlaying)
    DisposableEffect(owner, player) {
        var pausedByUs = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (!keep && player.state.value.playing) {
                    player.pause()
                    pausedByUs = true
                }
                Lifecycle.Event.ON_START -> if (pausedByUs) {
                    pausedByUs = false
                    player.play()
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

// ------------------------------------------------------------------ bottom bar

@Composable
private fun BottomBar(dest: NavDestination?, nav: TubeNavigator) {
    Column(Modifier.fillMaxWidth().background(sc.Ink)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(sc.Hairline))
        Row(
            Modifier.fillMaxWidth().height(BAR_HEIGHT).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val home = dest?.hierarchy()?.any { it.hasRoute(FeedRoute::class) } == true || dest == null
            BarItem(stringResource(R.string.tube_tab_home), Icons.Outlined.Home, Icons.Rounded.Home, home) {
                if (dest?.hasRoute(FeedRoute::class) == true) nav.homeAgain.tryEmit(Unit) else nav.switchTab(FeedRoute)
            }
            BarItem(stringResource(R.string.tube_tab_subs), Icons.Outlined.Subscriptions, Icons.Rounded.Subscriptions, dest?.hasRoute(SubsRoute::class) == true) { nav.switchTab(SubsRoute) }
            BarItem(stringResource(R.string.tube_tab_library), Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary, dest?.hasRoute(YouRoute::class) == true) { nav.switchTab(YouRoute) }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

private fun NavDestination.hierarchy(): Sequence<NavDestination> = generateSequence(this) { it.parent }

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarItem(label: String, icon: ImageVector, selectedIcon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val pill by animateColorAsState(if (selected) sc.AccentSoft else Color.Transparent, tween(240), label = "barPill")
    val tint by animateColorAsState(if (selected) sc.Accent else sc.TextMuted, tween(240), label = "barTint")
    val text by animateColorAsState(if (selected) sc.Text else sc.TextMuted, tween(240), label = "barText")
    val width by animateDpAsState(if (selected) 22.dp else 14.dp, Motion.bouncy(), label = "barWidth")
    val pop = rememberPop(selected)
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics { contentDescription = label }
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .pressScale(interaction)
                .clip(CircleShape)
                .background(pill)
                .padding(horizontal = width, vertical = 4.dp),
        ) {
            Crossfade(selected, animationSpec = tween(200), label = "barIcon") { on ->
                Icon(if (on) selectedIcon else icon, null, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                })
            }
        }
        // Only the chosen tab says its name: the icons speak for the others.
        AnimatedVisibility(selected, enter = fadeIn(tween(200)) + androidx.compose.animation.expandVertically(), exit = fadeOut(tween(120)) + androidx.compose.animation.shrinkVertically()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = text, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

// ------------------------------------------------------------------ mini player

@Composable
private fun MiniPlayer(state: TubePlayer.State, player: TubePlayer, onOpen: () -> Unit) {
    val video = state.video ?: return
    val exo by player.player.collectAsState()
    val progress = rememberVideoProgress(exo)
    Box(
        Modifier
            .padding(horizontal = 8.dp)
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .height(MINI_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(sc.Surface2)
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(48.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(9.dp)).background(Color.Black)) {
                AsyncImage(model = video.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, color = sc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(video.channelName, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MiniButton(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "", player::togglePlayPause)
            MiniButton(Icons.Rounded.Close, stringResource(R.string.tube_close), player::close)
        }
        val fraction = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(sc.Hairline)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(sc.Accent))
        }
    }
}

@Composable
private fun MiniButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(44.dp).pressScale(interaction).clip(CircleShape).clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description.ifEmpty { null }, tint = sc.Text, modifier = Modifier.size(26.dp))
    }
}
