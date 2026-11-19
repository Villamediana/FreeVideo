package com.villamediana.sound.ui.tube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.online.CommentItem
import com.villamediana.sound.tube.online.TubePage
import com.villamediana.sound.tube.online.VideoDetails
import com.villamediana.sound.tube.online.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** The watch page's facts: the video's details (+ suggestions) and its comments, loaded once. */
class WatchViewModel(val initial: VideoItem) : ViewModel() {
    private val tube = SoundApplication.graph.tube

    data class UiState(
        val details: VideoDetails? = null,
        val detailsError: Boolean = false,
        val comments: List<CommentItem> = emptyList(),
        val commentsPage: TubePage<CommentItem>? = null,
        val commentsLoading: Boolean = false,
        val commentsError: Boolean = false,
        /** Suggestions minus what was seen or dismissed. */
        val related: List<VideoItem> = emptyList(),
        /** Replies opened under a comment, by comment id. */
        val replies: Map<String, Replies> = emptyMap(),
    ) {
        val commentsTotal: Long get() = commentsPage?.total ?: -1
        val video: VideoItem? get() = details?.video
    }

    /** The replies of one comment: shown or folded, read so far, and whether more are coming. */
    data class Replies(
        val open: Boolean = true,
        val items: List<CommentItem> = emptyList(),
        val page: TubePage<CommentItem>? = null,
        val loading: Boolean = false,
        val error: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadDetails()
        loadComments()
    }

    fun loadDetails() {
        _state.value = _state.value.copy(detailsError = false)
        viewModelScope.launch {
            try {
                val details = tube.source.details(initial.id)
                val dismissed = tube.library.snapshot().dismissals.map { it.key }.toSet()
                val seen = tube.library.watches(details.related.map { it.id })
                val related = details.related.filter { v ->
                    "v:${v.id}" !in dismissed && (seen[v.id]?.completion ?: 0f) < 0.8f
                }
                _state.value = _state.value.copy(details = details, related = related.ifEmpty { details.related })
                tube.player.enrich(details.video.copy(thumbnailUrl = initial.thumbnailUrl ?: details.video.thumbnailUrl))
                tube.player.setUpNext(related.firstOrNull { !it.short && !it.live } ?: related.firstOrNull())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _state.value = _state.value.copy(detailsError = true)
            }
        }
    }

    fun loadComments() {
        if (_state.value.commentsLoading) return
        _state.value = _state.value.copy(commentsLoading = true, commentsError = false)
        viewModelScope.launch {
            try {
                val page = tube.source.comments(initial.id)
                _state.value = _state.value.copy(comments = page.items, commentsPage = page, commentsLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _state.value = _state.value.copy(commentsLoading = false, commentsError = true)
            }
        }
    }

    fun moreComments() {
        val page = _state.value.commentsPage ?: return
        if (!page.hasMore || _state.value.commentsLoading) return
        _state.value = _state.value.copy(commentsLoading = true)
        viewModelScope.launch {
            try {
                val next = tube.source.moreComments(page)
                val known = _state.value.comments.map { it.id }.toSet()
                _state.value = _state.value.copy(
                    comments = _state.value.comments + next.items.filter { it.id !in known },
                    commentsPage = next,
                    commentsLoading = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _state.value = _state.value.copy(commentsLoading = false)
            }
        }
    }

    private fun putReplies(id: String, change: (Replies) -> Replies) {
        val s = _state.value
        _state.value = s.copy(replies = s.replies + (id to change(s.replies[id] ?: Replies())))
    }

    /** Opens (reading the first ones, once) or folds the replies under [comment]. */
    fun toggleReplies(comment: CommentItem) {
        val now = _state.value.replies[comment.id]
        if (now != null && now.page != null) {
            putReplies(comment.id) { it.copy(open = !it.open) }
            return
        }
        if (now?.loading == true) return
        putReplies(comment.id) { it.copy(open = true, loading = true, error = false) }
        viewModelScope.launch {
            try {
                val page = tube.source.replies(comment)
                putReplies(comment.id) { it.copy(items = page.items, page = page, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                putReplies(comment.id) { it.copy(loading = false, error = true) }
            }
        }
    }

    /** More replies under [comment] (YouTube hands them out ten at a time). */
    fun moreReplies(comment: CommentItem) {
        val now = _state.value.replies[comment.id] ?: return
        val page = now.page ?: return
        if (!page.hasMore || now.loading) return
        putReplies(comment.id) { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val next = tube.source.moreComments(page)
                putReplies(comment.id) { old ->
                    val known = old.items.map { it.id }.toSet()
                    old.copy(items = old.items + next.items.filter { it.id !in known }, page = next, loading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                putReplies(comment.id) { it.copy(loading = false) }
            }
        }
    }

    /** "Não curti" on a suggestion or from the menu: it leaves the suggestions at once. */
    fun drop(video: VideoItem) {
        _state.value = _state.value.copy(related = _state.value.related.filter { it.id != video.id })
    }
}
