package com.villamediana.sound.tube.online

/**
 * YouTube's preview frames of a video: small pictures every few seconds, laid out in grids on a few
 * sheets (one JPEG holds [columns] × [rows] of them). What the seek bar shows above the finger, and
 * what a card flips through while it waits on screen.
 */
data class Storyboard(
    val urls: List<String>,
    val frameWidth: Int,
    val frameHeight: Int,
    val columns: Int,
    val rows: Int,
    val totalCount: Int,
    /** How long each frame stands for (ms). */
    val frameMs: Int,
) {
    /** One frame: its sheet and its box in it (px of the sheet). */
    data class Frame(val url: String, val x: Int, val y: Int, val width: Int, val height: Int)

    val usable: Boolean get() = urls.isNotEmpty() && frameWidth > 0 && frameHeight > 0 && columns > 0 && rows > 0 && totalCount > 0 && frameMs > 0

    /** The frame shown at [positionMs]; null when there is none. */
    fun frameAt(positionMs: Long): Frame? {
        if (!usable) return null
        val index = (positionMs.coerceAtLeast(0) / frameMs).toInt().coerceIn(0, totalCount - 1)
        val perSheet = columns * rows
        val url = urls.getOrNull(index / perSheet) ?: return null
        val inSheet = index % perSheet
        return Frame(url, (inSheet % columns) * frameWidth, (inSheet / columns) * frameHeight, frameWidth, frameHeight)
    }

    companion object {
        /** The sharpest set of frames that is still small (≤ 320 px wide): YouTube has 2–4 sizes. */
        fun <T> pick(sets: List<T>, width: (T) -> Int): T? =
            sets.filter { width(it) in 1..320 }.maxByOrNull(width) ?: sets.minByOrNull(width)
    }
}
