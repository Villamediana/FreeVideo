package com.villamediana.sound.ui.tube

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.villamediana.sound.tube.online.Storyboard
import java.util.Collections

/**
 * One preview frame, cut out of its sheet and drawn to fill [modifier] (cropped to its shape). Sheets
 * are fetched once (through the app's image cache) and the last few are kept in memory, so sliding
 * along the seek bar doesn't wait for the network frame after frame.
 */
@Composable
internal fun StoryboardFrame(frame: Storyboard.Frame?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val url = frame?.url
    var image by remember(url) { mutableStateOf(url?.let(Sheets::get)) }
    LaunchedEffect(url) {
        if (url == null || image != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@LaunchedEffect
        val bitmap: Bitmap = (result.image as? BitmapImage)?.bitmap ?: return@LaunchedEffect
        val loaded = bitmap.asImageBitmap()
        Sheets.put(url, loaded)
        image = loaded
    }
    Canvas(modifier) {
        val f = frame ?: return@Canvas
        val img = image ?: return@Canvas
        // Cover: the frame fills the box, its middle kept.
        val boxRatio = size.width / size.height
        val frameRatio = f.width.toFloat() / f.height
        var sw = f.width
        var sh = f.height
        if (frameRatio > boxRatio) sw = (f.height * boxRatio).toInt().coerceAtLeast(1) else sh = (f.width / boxRatio).toInt().coerceAtLeast(1)
        val sx = f.x + (f.width - sw) / 2
        val sy = f.y + (f.height - sh) / 2
        if (sx >= img.width || sy >= img.height) return@Canvas
        drawImage(
            img,
            srcOffset = IntOffset(sx.coerceIn(0, img.width - 1), sy.coerceIn(0, img.height - 1)),
            srcSize = IntSize(sw.coerceAtMost(img.width - sx), sh.coerceAtMost(img.height - sy)),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** The last sheets drawn (a few hundred KB each at most). */
private object Sheets {
    private val map = Collections.synchronizedMap(object : LinkedHashMap<String, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 6
    })

    fun get(url: String): ImageBitmap? = map[url]
    fun put(url: String, image: ImageBitmap) {
        map[url] = image
    }
}

/**
 * A card waiting in the middle of the screen: after a moment its thumbnail flips through a dozen
 * frames spread over the video (YouTube's preview frames — small pictures, no video downloaded), twice,
 * with a hairline that runs along as it goes. Only on Wi-Fi (or another unmetered network) and never in
 * battery saver. The extraction it needs is the one the page would make: opening the video after it
 * is instant.
 */
@Composable
internal fun Flipbook(video: com.villamediana.sound.tube.online.VideoItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var board by remember(video.id) { mutableStateOf<Storyboard?>(null) }
    var index by remember(video.id) { mutableStateOf(-1) }
    val positions = remember(board) {
        val b = board ?: return@remember emptyList()
        val duration = video.durationMs.takeIf { it > 0 } ?: (b.totalCount.toLong() * b.frameMs)
        (0 until FLIP_FRAMES).map { i -> (duration * (0.06 + 0.88 * i / (FLIP_FRAMES - 1))).toLong() }
    }
    LaunchedEffect(video.id) {
        kotlinx.coroutines.delay(FLIP_WAIT_MS)
        if (!Previews.allowed(context)) return@LaunchedEffect
        board = runCatching { com.villamediana.sound.SoundApplication.graph.tube.source.storyboard(video.id) }.getOrNull() ?: return@LaunchedEffect
        // Two rounds, then it rests on a frame from the middle (the card has had its say).
        for (i in 0 until FLIP_FRAMES * FLIP_ROUNDS) {
            index = i % FLIP_FRAMES
            kotlinx.coroutines.delay(FLIP_FRAME_MS)
        }
        index = FLIP_FRAMES / 2
    }
    val b = board ?: return
    if (index < 0 || positions.isEmpty()) return
    val appear = remember(video.id) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(appear) { appear.animateTo(1f, androidx.compose.animation.core.tween(320)) }
    androidx.compose.foundation.layout.Box(modifier.graphicsLayer { alpha = appear.value }) {
        androidx.compose.animation.Crossfade(index, animationSpec = androidx.compose.animation.core.tween(240), label = "flipFrame") { k ->
            StoryboardFrame(b.frameAt(positions[k]), Modifier.fillMaxSize())
        }
        val accent = com.villamediana.sound.ui.theme.sc.Accent
        Canvas(Modifier.fillMaxSize()) {
            val h = 3.dp.toPx()
            drawRect(Color.White.copy(alpha = 0.3f), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h))
            drawRect(accent, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width * (index + 1f) / FLIP_FRAMES, h))
        }
    }
}

private const val FLIP_FRAMES = 12
private const val FLIP_ROUNDS = 2
private const val FLIP_WAIT_MS = 1_000L
private const val FLIP_FRAME_MS = 750L

/** Whether moving pictures in the lists are welcome: an unmetered network and no battery saver. */
internal object Previews {
    fun allowed(context: android.content.Context): Boolean {
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        if (connectivity.isActiveNetworkMetered) return false
        val power = context.getSystemService(android.os.PowerManager::class.java)
        return power?.isPowerSaveMode != true
    }
}
