package com.villamediana.sound.tube.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.villamediana.sound.R
import com.villamediana.sound.online.OnlineException
import com.villamediana.sound.online.messageRes
import com.villamediana.sound.online.onlineKind
import com.villamediana.sound.playback.stream.ChunkedHttpDataSource
import com.villamediana.sound.playback.stream.StreamDataSources
import com.villamediana.sound.playback.stream.StreamErrors
import com.villamediana.sound.playback.stream.StreamUris
import com.villamediana.sound.playback.stream.StreamUrlCache
import com.villamediana.sound.playback.stream.resolveBlocking
import com.villamediana.sound.tube.data.TubeLibrary
import com.villamediana.sound.tube.data.TubePrefs
import com.villamediana.sound.tube.data.TubeSettings
import com.villamediana.sound.tube.online.TubeSource
import com.villamediana.sound.tube.online.StreamTag
import com.villamediana.sound.tube.online.TubeStream
import com.villamediana.sound.tube.online.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * FreeVideo's one player. A single ExoPlayer for the whole time something is open (the watch page
 * and the mini player render the same one), fed the video-only H.264 stream + the M4A audio merged
 * ([MergingMediaSource]) or a muxed stream — read in 2 MB ranges like FreeSong's songs — or, for a
 * live, YouTube's HLS manifest.
 *
 * While a video plays it keeps the history up to date (time really watched, where it was left):
 * that is what the feed learns from. Opening a video seen halfway resumes it. At the end, with
 * "reprodução automática" on, the next one ([upNext]) starts after a short countdown.
 *
 * Main thread only (the methods hop there).
 */
@OptIn(UnstableApi::class)
class TubePlayer(
    private val context: Context,
    scope: CoroutineScope,
    private val source: TubeSource,
    private val library: TubeLibrary,
    private val settings: TubeSettings,
) {
    data class State(
        val video: VideoItem? = null,
        val loading: Boolean = false,
        val playing: Boolean = false,
        val ended: Boolean = false,
        val error: String? = null,
        val aspectRatio: Float = 16f / 9f,
        /** Seconds left before [upNext] starts by itself; null = no countdown. */
        val countdown: Int? = null,
        val upNext: VideoItem? = null,
        /** The stream's height ("720p"), once known. */
        val height: Int = 0,
        /** The speed chosen (kept for the next videos); holding the video plays at 2× for a while without changing it. */
        val speed: Float = 1f,
    ) {
        val active: Boolean get() = video != null
    }

    private val main = CoroutineScope(scope.coroutineContext + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    /** The player to render (media3 PlayerSurface); null when nothing is open. */
    val player: StateFlow<Player?> = _player.asStateFlow()

    @Volatile private var prefs = TubePrefs()

    init {
        main.launch {
            settings.prefs.collect {
                prefs = it
                if (_state.value.speed != it.speed) {
                    _state.value = _state.value.copy(speed = it.speed)
                    applySpeed()
                }
            }
        }
    }

    private var exo: ExoPlayer? = null
    private var loadJob: Job? = null
    private var countdownJob: Job? = null
    private var tickJob: Job? = null

    private val streams = StreamUrlCache<String, TubeStream>(scope, urlOf = { it.videoUrl }) { id, previous ->
        // A second resolution means the urls were refused or expired: extract again, not from memory.
        // …asking for the same files first, so the new urls carry on the very same bytes.
        source.stream(id, Battery.maxHeight(context, prefs.maxHeight), fresh = previous != null, keep = previous)
    }

    /**
     * Placeholder → googlevideo url. A part's uri names its file ([VideoCache.FILE]); if the urls known
     * now serve another one, nothing is read ([FileChangedException]) and the player restarts the video
     * with the new file from the same second: ranges of two files are never spliced.
     */
    private val resolver = ChunkedHttpDataSource.UriResolver { uri, refresh ->
        val (id, part) = StreamUris.videoPart(uri) ?: return@UriResolver uri
        val stream = resolveBlocking { streams.get(id, refresh) }
        val url = if (part == StreamUris.AUDIO_PART) stream.audioUrl ?: stream.videoUrl else stream.videoUrl
        val wanted = VideoCache.wanted(uri)
        if (wanted != null && StreamTag.of(url) != wanted) throw FileChangedException(id)
        Uri.parse(url)
    }

    /** When the player last started over because the file changed (at most once in a while: no loops). */
    private var restartedAt = 0L

    /**
     * Reads through the video cache on disk: what was played (and what [Prefetcher] fetched ahead) is
     * there, so seeking back, and going on when the connection drops, need no network. Video ranges
     * are 4 MB (a 720p second is ~0.3 MB: fewer round trips, fewer stalls).
     */
    private val progressive by lazy {
        ProgressiveMediaSource.Factory(StreamDataSources.factory(context, resolver, VideoCache.get(context), VIDEO_CHUNK))
            .setLoadErrorHandlingPolicy(TubeLoadErrorPolicy())
    }

    private val prefetcher by lazy { Prefetcher(context, resolver) }

    private val hls by lazy { HlsMediaSource.Factory(StreamDataSources.http()) }

    // ------------------------------------------------------------------ watch tracking

    /** Time played since the last note to the history, and whether this visit was noted yet. */
    private var pendingMs = 0L
    private var lastTick = 0L
    private var visitNoted = false
    /** The feed source that brought the video playing (its watch time is credited to it); null = not from the feed. */
    private var fromSource: String? = null

    /**
     * Counts the time played, and notes it to the history every [FLUSH_EVERY_S] s — only while the
     * video plays (a paused video wakes nothing up).
     */
    private fun startTicking() {
        tickJob?.cancel()
        tickJob = null
        if (exo?.isPlaying == true) resumeTicking()
    }

    private fun resumeTicking() {
        if (tickJob?.isActive == true) return
        lastTick = SystemClock.elapsedRealtime()
        tickJob = main.launch {
            while (isActive) {
                delay(FLUSH_EVERY_S * 1_000L)
                countPlayed()
                flush()
            }
        }
    }

    /** Stops counting (paused): what was played until now goes to [pendingMs]. */
    private fun pauseTicking() {
        if (tickJob?.isActive != true) return
        countPlayed()
        tickJob?.cancel()
        tickJob = null
    }

    private fun countPlayed() {
        val now = SystemClock.elapsedRealtime()
        pendingMs += (now - lastTick).coerceAtLeast(0)
        lastTick = now
    }

    /** Writes what was watched to the history (skipped while the history is paused). */
    private fun flush() {
        val video = _state.value.video ?: return
        val p = exo ?: return
        val played = pendingMs
        if (played <= 0 && visitNoted) return
        // A glance (under 3 s) leaves no trace.
        if (!visitNoted && played < 3_000) return
        pendingMs = 0
        if (prefs.historyPaused) return
        val position = p.currentPosition.coerceAtLeast(0)
        val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val newVisit = !visitNoted
        visitNoted = true
        val credit = fromSource
        main.launch {
            runCatching { library.recordWatch(video, position, duration, played, newVisit) }
            if (credit != null) runCatching { library.creditSource(credit, played) }
        }
    }

    // ------------------------------------------------------------------ control

    /**
     * Plays [video]. [startAtMs] null = where it was left (if it was left halfway). [source] = the
     * feed source whose card was tapped, if any.
     */
    fun open(video: VideoItem, startAtMs: Long? = null, source: String? = null) {
        main.launch {
            val current = _state.value
            if (current.video?.id == video.id && exo != null && current.error == null) {
                // The same video again (the watch page came back): carry on, keep the fuller facts.
                _state.value = current.copy(video = merge(current.video, video))
                return@launch
            }
            pauseTicking()
            flush()
            cancelCountdown()
            pendingMs = 0
            visitNoted = false
            fromSource = source
            val player = exo ?: buildPlayer().also {
                exo = it
                _player.value = it
            }
            player.stop()
            player.clearMediaItems()
            _state.value = State(video = video, loading = true, speed = prefs.speed)
            startTicking()
            val start = startAtMs ?: resumePoint(video)
            load(player, video, start)
            if (prefs.keepPlayingInBackground) TubePlaybackService.start(context)
            findUpNext(video)
        }
    }

    /**
     * What plays after [video] when nobody chose yet (the mini player has no watch page to say): the
     * first suggestion not seen. Shares the extraction with the stream, so it costs no extra request.
     */
    private fun findUpNext(video: VideoItem) {
        main.launch {
            val details = runCatching { source.details(video.id) }.getOrNull() ?: return@launch
            // What the video is about (its tags): better words for the feed than the title alone.
            if (!prefs.historyPaused) runCatching { library.saveFacts(video.id, details.tags, details.category) }
            val related = details.related
            if (_state.value.video?.id != video.id || _state.value.upNext != null) return@launch
            val seen = runCatching { library.watches(related.map { it.id }) }.getOrDefault(emptyMap())
            val pick = related.firstOrNull { !it.short && !it.live && (seen[it.id]?.completion ?: 0f) < 0.8f } ?: related.firstOrNull()
            if (pick != null && _state.value.video?.id == video.id && _state.value.upNext == null) setUpNext(pick)
        }
    }

    private suspend fun resumePoint(video: VideoItem): Long {
        val w = runCatching { library.watch(video.id) }.getOrNull() ?: return 0
        return if (w.durationMs > 60_000 && w.progress in 0.03f..0.92f) (w.positionMs - 2_000).coerceAtLeast(0) else 0
    }

    private fun load(player: ExoPlayer, video: VideoItem, startAtMs: Long) {
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true, error = null, ended = false)
        loadJob = main.launch {
            val stream = try {
                streams.get(video.id)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (exo === player && _state.value.video?.id == video.id) fail(message(t.onlineKind()))
                return@launch
            }
            if (exo !== player || _state.value.video?.id != video.id) return@launch
            _state.value = _state.value.copy(height = minOf(stream.width, stream.height).takeIf { it > 0 } ?: stream.height)
            player.setMediaSource(mediaSource(video, stream), if (stream.hlsUrl != null) C.TIME_UNSET else startAtMs)
            applySpeed()
            player.prepare()
            player.playWhenReady = true
            // The rest of the video goes to the disk cache while it plays (all of it on Wi-Fi, a stretch on mobile data).
            nowPlaying = video.id to stream
            if (stream.hlsUrl == null) prefetcher.start(video.id, stream, withVideo = !background, fromMs = startAtMs, durationMs = video.durationMs)
        }
    }

    /** What plays now (for the prefetch to start again when the picture is wanted or not). */
    private var nowPlaying: Pair<String, TubeStream>? = null

    /** Nobody sees the picture (screen off, another app): only the sound is read, decoded and fetched. */
    private var background = false

    /**
     * The app left the screen with the video going on (and no floating window): the video track is
     * switched off — no picture downloaded or decoded — and the sound may go to the phone's audio chip
     * (offload: the processor sleeps while it plays). Back on screen, the picture comes back where it is.
     */
    fun setBackground(on: Boolean) {
        main.launch {
            if (background == on) return@launch
            background = on
            exo?.let { applyBackground(it) }
            val (id, stream) = nowPlaying ?: return@launch
            val p = exo
            if (_state.value.video?.id == id && stream.hlsUrl == null) {
                prefetcher.start(id, stream, withVideo = !on, fromMs = p?.currentPosition ?: 0, durationMs = p?.duration?.takeIf { it > 0 } ?: _state.value.video?.durationMs ?: 0)
            }
        }
    }

    private fun applyBackground(player: ExoPlayer) {
        // Wake and Wi-Fi locks only off screen (with the screen on the phone stays awake by itself;
        // during offload the player lets go of them while the audio chip plays).
        player.setWakeMode(if (background) C.WAKE_MODE_NETWORK else C.WAKE_MODE_NONE)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, background)
            .setAudioOffloadPreferences(
                androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(
                        if (background) androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                        else androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
                    )
                    .setIsGaplessSupportRequired(false)
                    .setIsSpeedChangeSupportRequired(false)
                    .build(),
            )
            .build()
    }

    fun togglePlayPause() {
        main.launch {
            val player = exo ?: return@launch
            if (player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)) {
                player.pause()
                flush()
                return@launch
            }
            cancelCountdown()
            when (player.playbackState) {
                Player.STATE_ENDED -> player.seekToDefaultPosition()
                Player.STATE_IDLE -> {
                    val video = _state.value.video ?: return@launch
                    if (player.mediaItemCount > 0) {
                        _state.value = _state.value.copy(error = null, loading = true)
                        player.prepare()
                    } else {
                        load(player, video, 0)
                        return@launch
                    }
                }
                else -> Unit
            }
            player.play()
        }
    }

    fun pause() {
        main.launch {
            exo?.pause()
            flush()
        }
    }

    fun play() {
        main.launch { exo?.takeIf { !it.isPlaying }?.play() }
    }

    /** Held down on the video: 2× until the finger lifts (the chosen speed stays as it is). */
    private var boosted = false

    /** The speed for this and the next videos (saved). */
    fun setSpeed(speed: Float) {
        main.launch {
            _state.value = _state.value.copy(speed = speed)
            prefs = prefs.copy(speed = speed)
            applySpeed()
            runCatching { settings.setSpeed(speed) }
        }
    }

    /** While held: 2× (or the chosen speed, if faster); let go, back to the chosen one. */
    fun boost(on: Boolean) {
        main.launch {
            boosted = on
            applySpeed()
        }
    }

    private fun applySpeed() {
        val player = exo ?: return
        // A live goes at its own pace.
        val chosen = if (_state.value.video?.live == true) 1f else _state.value.speed
        val speed = if (boosted) maxOf(2f, chosen) else chosen
        if (player.playbackParameters.speed != speed) player.setPlaybackSpeed(speed)
    }

    fun seekTo(positionMs: Long) {
        main.launch {
            cancelCountdown()
            exo?.seekTo(positionMs.coerceAtLeast(0))
        }
    }

    /** The sharpest video wanted (saved): the video goes on from the same second at it. */
    fun setQuality(height: Int) {
        main.launch {
            prefs = prefs.copy(maxHeight = height)
            runCatching { settings.setMaxHeight(height) }
            val player = exo ?: return@launch
            val video = _state.value.video ?: return@launch
            if (video.live) return@launch
            val at = player.currentPosition.coerceAtLeast(0)
            streams.invalidate(video.id)
            load(player, video, at)
        }
    }

    /** Tries again after an error, from where it stopped. */
    fun retry() {
        main.launch {
            val player = exo ?: return@launch
            val video = _state.value.video ?: return@launch
            val at = player.currentPosition.takeIf { it > 0 } ?: 0
            streams.invalidate(video.id)
            load(player, video, at)
        }
    }

    /** What plays after this one (the watch page's first suggestion not seen yet). */
    fun setUpNext(video: VideoItem?) {
        main.launch {
            if (_state.value.upNext?.id != video?.id) {
                _state.value = _state.value.copy(upNext = video)
                // What comes next is resolved ahead: autoplay (or a tap on it) starts at once.
                video?.let { warm(it.id) }
            }
        }
    }

    /** Resolves [id]'s streams in the background (a card pressed, the next video): opening it is then instant. */
    fun warm(id: String) {
        if (streams.peek(id) == null) streams.prefetch(id)
    }

    fun playUpNext() {
        main.launch {
            val next = _state.value.upNext ?: return@launch
            open(next)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        if (_state.value.countdown != null) _state.value = _state.value.copy(countdown = null)
    }

    /** Closes everything (the mini player's ×). */
    fun close() {
        main.launch {
            pauseTicking()
            flush()
            cancelCountdown()
            loadJob?.cancel()
            tickJob?.cancel()
            val player = exo
            exo = null
            _player.value = null
            boosted = false
            _state.value = State(speed = prefs.speed)
            prefetcher.stop()
            nowPlaying = null
            player?.release()
            TubePlaybackService.stop(context)
        }
    }

    /** The ExoPlayer itself (for the media session); null when closed. */
    internal fun exoPlayer(): ExoPlayer? = exo

    // ------------------------------------------------------------------ player

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                // Starts after 0.8 s buffered (2.5 s by default), keeps 50–120 s ahead in memory (the disk cache has the rest).
                .setBufferDurationsMs(50_000, 120_000, 800, 2_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(if (background) C.WAKE_MODE_NETWORK else C.WAKE_MODE_NONE)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()
        .apply {
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            addListener(Events(this))
            if (background) applyBackground(this)
        }

    private fun metadata(video: VideoItem): MediaMetadata = MediaMetadata.Builder()
        .setTitle(video.title)
        .setArtist(video.channelName)
        .setArtworkUri(video.thumbnailUrl?.let(Uri::parse))
        .build()

    private fun mediaSource(video: VideoItem, stream: TubeStream): MediaSource {
        stream.hlsUrl?.let { url ->
            return hls.createMediaSource(
                MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).setMediaId(video.id).setMediaMetadata(metadata(video)).build(),
            )
        }
        val videoSource = progressive.createMediaSource(
            MediaItem.Builder()
                .setUri(VideoCache.uri(video.id, StreamUris.VIDEO_PART, stream))
                .setMediaId(video.id)
                .setCustomCacheKey(VideoCache.key(video.id, StreamUris.VIDEO_PART, stream))
                .setMimeType(stream.videoMimeType.ifBlank { null })
                .setMediaMetadata(metadata(video))
                .build(),
        )
        if (stream.audioUrl == null) return videoSource
        val audioSource = progressive.createMediaSource(
            MediaItem.Builder()
                .setUri(VideoCache.uri(video.id, StreamUris.AUDIO_PART, stream))
                .setCustomCacheKey(VideoCache.key(video.id, StreamUris.AUDIO_PART, stream))
                .setMimeType(stream.audioMimeType?.ifBlank { null })
                .build(),
        )
        return MergingMediaSource(/* adjustPeriodTimeOffsets = */ true, /* clipDurations = */ true, videoSource, audioSource)
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(loading = false, playing = false, error = message)
    }

    private fun message(kind: OnlineException.Kind?): String =
        context.getString(kind?.messageRes() ?: R.string.playback_error_generic)

    private fun onEnded() {
        flush()
        _state.value = _state.value.copy(ended = true, playing = false, loading = false)
        val next = _state.value.upNext ?: return
        if (!prefs.autoplay) return
        countdownJob?.cancel()
        countdownJob = main.launch {
            for (s in AUTOPLAY_SECONDS downTo 1) {
                _state.value = _state.value.copy(countdown = s)
                delay(1_000)
            }
            _state.value = _state.value.copy(countdown = null)
            open(next)
        }
    }

    private inner class Events(private val player: ExoPlayer) : Player.Listener {
        private val current get() = exo === player

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!current) return
            _state.value = _state.value.copy(playing = isPlaying, error = if (isPlaying) null else _state.value.error)
            if (isPlaying) {
                resumeTicking()
            } else {
                pauseTicking()
                flush()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!current) return
            _state.value = _state.value.copy(
                loading = playbackState == Player.STATE_BUFFERING,
                playing = player.isPlaying,
                ended = playbackState == Player.STATE_ENDED,
            )
            if (playbackState == Player.STATE_ENDED) onEnded()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (!current || videoSize.width <= 0 || videoSize.height <= 0) return
            val ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            if (ratio.isFinite() && ratio > 0f) _state.value = _state.value.copy(aspectRatio = ratio)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!current) return
            val video = _state.value.video
            val now = SystemClock.elapsedRealtime()
            if (video != null && fileChanged(error) && now - restartedAt > RESTART_GAP_MS) {
                // The urls now serve another file: the same second, read from the new one.
                restartedAt = now
                load(player, video, player.currentPosition.coerceAtLeast(0))
                return
            }
            fail(message(StreamErrors.kindOf(error.errorCode)))
        }
    }

    private fun merge(old: VideoItem?, new: VideoItem): VideoItem {
        if (old == null) return new
        return old.copy(
            title = new.title.ifBlank { old.title },
            channelName = new.channelName.ifBlank { old.channelName },
            channelUrl = new.channelUrl ?: old.channelUrl,
            channelAvatar = new.channelAvatar ?: old.channelAvatar,
            thumbnailUrl = old.thumbnailUrl ?: new.thumbnailUrl,
            durationMs = new.durationMs.takeIf { it > 0 } ?: old.durationMs,
            views = if (new.views >= 0) new.views else old.views,
            uploadedAt = new.uploadedAt ?: old.uploadedAt,
            uploadedText = new.uploadedText ?: old.uploadedText,
        )
    }

    /** The watch page learnt more about the video (length, channel link…): keep it for the history. */
    fun enrich(video: VideoItem) {
        main.launch {
            val current = _state.value
            if (current.video?.id == video.id) _state.value = current.copy(video = merge(current.video, video))
        }
    }

    companion object {
        const val AUTOPLAY_SECONDS = 5
        private const val RESTART_GAP_MS = 20_000L
        private const val VIDEO_CHUNK = 4L * 1024 * 1024
        private const val FLUSH_EVERY_S = 10
    }
}
