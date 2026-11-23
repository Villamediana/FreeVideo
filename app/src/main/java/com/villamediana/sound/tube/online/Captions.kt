package com.villamediana.sound.tube.online

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One line of captions, shown from [startMs] to [endMs]. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

/** A captions track YouTube has for a video: written by the channel, or made by YouTube ([auto]). */
data class CaptionTrack(val url: String, val language: String, val name: String, val auto: Boolean)

/** A chapter of a video ("0:00 Intro", "3:12 Modelando a cabeça"…), with YouTube's picture of it when there is one. */
data class Chapter(val title: String, val startMs: Long, val thumbnailUrl: String?)

/** A part of the visual summary: a chapter (or a stretch of the video), its text and the moment to show. */
data class Section(val title: String?, val startMs: Long, val endMs: Long, val text: String, val thumbnailUrl: String?)

/** What captions to use: a track as it is, or translated by YouTube to [translateTo]. */
data class CaptionChoice(val track: CaptionTrack, val translateTo: String? = null) {
    val language: String get() = translateTo ?: track.language
}

/**
 * Captions, the transcript and the visual summary, from YouTube's captions (pure Kotlin: the
 * network is [TubeSource]'s). The captions come as JSON3 — the cleanest form YouTube serves: the
 * automatic ones without the repeated lines of their VTT.
 */
object Captions {
    /** [url] (a captions track) as JSON3, translated by YouTube to [translateTo] when given. */
    fun json3Url(url: String, translateTo: String? = null): String {
        val base = url.replace(Regex("[&?]fmt=[^&]*"), "").replace(Regex("[&?]tlang=[^&]*"), "")
        val sep = if ('?' in base) "&" else "?"
        return base + sep + "fmt=json3" + (translateTo?.takeIf { it.isNotBlank() }?.let { "&tlang=$it" } ?: "")
    }

    /** JSON3 → cues in order; empty when there is nothing usable. A line ends where the next one starts. */
    fun parseJson3(text: String): List<Cue> {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
        val events = runCatching { root["events"]?.jsonArray }.getOrNull() ?: return emptyList()
        val cues = ArrayList<Cue>(events.size)
        for (event in events) {
            val o = runCatching { event.jsonObject }.getOrNull() ?: continue
            val segs = runCatching { o["segs"]?.jsonArray }.getOrNull() ?: continue
            val start = o["tStartMs"]?.jsonPrimitive?.longOrNull ?: continue
            val duration = o["dDurationMs"]?.jsonPrimitive?.longOrNull ?: 0L
            val line = segs.joinToString("") { s -> runCatching { s.jsonObject["utf8"]?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty() }
                .replace('\n', ' ')
                .replace(SPACES, " ")
                .trim()
            if (line.isEmpty()) continue
            cues += Cue(start, start + duration.coerceAtLeast(0), line)
        }
        cues.sortBy { it.startMs }
        // Automatic captions keep a line up while the next is written: one line at a time here.
        return cues.mapIndexed { i, c ->
            val next = cues.getOrNull(i + 1)
            if (next != null && c.endMs > next.startMs) c.copy(endMs = maxOf(c.startMs, next.startMs)) else c
        }
    }

    /** The cue on screen at [positionMs] (null between lines). */
    fun at(cues: List<Cue>, positionMs: Long): Cue? {
        var lo = 0
        var hi = cues.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = cues[mid]
            when {
                positionMs < c.startMs -> hi = mid - 1
                positionMs >= c.endMs -> lo = mid + 1
                else -> return c
            }
        }
        return null
    }

    /** Index of the paragraph that holds [positionMs] (the last one started), -1 before the first. */
    fun indexAt(paragraphs: List<Cue>, positionMs: Long): Int {
        var lo = 0
        var hi = paragraphs.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (paragraphs[mid].startMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    /**
     * The transcript to read: lines joined into paragraphs — a new one after a pause, after a sentence
     * that ends once the paragraph is long enough, or at [maxMs] at most.
     */
    fun paragraphs(cues: List<Cue>, maxMs: Long = 25_000, pauseMs: Long = 1_800): List<Cue> {
        val out = ArrayList<Cue>()
        var start = -1L
        var end = 0L
        val text = StringBuilder()
        fun flush() {
            if (start >= 0 && text.isNotBlank()) out += Cue(start, end, text.toString().trim())
            start = -1
            text.clear()
        }
        for (c in cues) {
            if (start >= 0) {
                val long = c.startMs - start >= maxMs
                val pause = c.startMs - end >= pauseMs
                val sentence = c.startMs - start >= maxMs / 2 && text.trimEnd().lastOrNull()?.let { it in ".!?…" } == true
                if (long || pause || sentence) flush()
            }
            if (start < 0) start = c.startMs
            if (text.isNotEmpty()) text.append(' ')
            text.append(c.text)
            end = c.endMs
        }
        flush()
        return out
    }

    /**
     * The visual summary: one part per chapter, or — without chapters — the video cut in about
     * [parts] even stretches (one to five minutes each). Each part has the words said in it (cut to
     * [maxChars] at a word) and the chapter's picture when YouTube has one.
     */
    fun sections(cues: List<Cue>, chapters: List<Chapter>, durationMs: Long, parts: Int = 8, maxChars: Int = 280): List<Section> {
        val total = durationMs.takeIf { it > 0 } ?: cues.lastOrNull()?.endMs ?: return emptyList()
        val marks: List<Triple<String?, Long, String?>> = if (chapters.size >= 2) {
            chapters.sortedBy { it.startMs }.map { Triple(it.title.ifBlank { null }, it.startMs, it.thumbnailUrl) }
        } else {
            val step = (total / parts).coerceIn(60_000L, 300_000L)
            generateSequence(0L) { it + step }.takeWhile { it < total }.map { Triple(null, it, null) }.toList()
        }
        return marks.mapIndexed { i, (title, start, thumb) ->
            val end = marks.getOrNull(i + 1)?.second ?: total
            val words = cues.filter { it.startMs >= start && it.startMs < end }.joinToString(" ") { it.text }.replace(SPACES, " ").trim()
            Section(title, start, end, cut(words, maxChars), thumb)
        }
    }

    /** [text] cut at a word to about [max] characters, with "…" when it was cut. */
    fun cut(text: String, max: Int): String {
        if (text.length <= max) return text
        val at = text.lastIndexOf(' ', max).takeIf { it > max / 2 } ?: max
        return text.substring(0, at).trimEnd(',', ';', ':', ' ') + "…"
    }

    /**
     * Which captions to show for someone who reads [language]: the channel's own in it, else
     * YouTube's automatic ones in it, else the video's own captions translated to it.
     */
    fun choose(tracks: List<CaptionTrack>, language: String): CaptionChoice? {
        if (tracks.isEmpty()) return null
        fun same(t: CaptionTrack) = t.language.substringBefore('-').equals(language.substringBefore('-'), ignoreCase = true)
        tracks.firstOrNull { !it.auto && same(it) }?.let { return CaptionChoice(it) }
        tracks.firstOrNull { it.auto && same(it) }?.let { return CaptionChoice(it) }
        val original = tracks.firstOrNull { !it.auto } ?: tracks.first()
        return CaptionChoice(original, translateTo = language)
    }

    private val SPACES = Regex("\\s+")
}
