package com.villamediana.sound.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Tokens of one theme. Screens never read this directly: they read [SoundColors]. */
data class SoundPalette(
    val isLight: Boolean,
    val ink: Color,          // app background
    val surface1: Color,     // pills, cards
    val surface2: Color,     // sheets, raised cards
    val surface3: Color,     // inputs, pressed
    val hairline: Color,     // dividers, outlines
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val accent: Color,       // selection, primary actions
    val accentPressed: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val mint: Color,         // what is playing: player controls, equalizer, liked
    val onMint: Color,
    val lyric: Color,        // the line being sung
    val danger: Color,
    val scrim: Color,
    /** Hero cards (smart shuffle) and the equalizer backdrop: a gentle gradient, never a heavy block. */
    val softTop: Color,
    val softBottom: Color,
    /** How strongly the cover color shows on headers and the player (0..1). */
    val washAlpha: Float,
)

object SoundPalettes {
    /** FreeVideo light — paper: warm off-white, ink-brown text instead of black, pen-blue ink for what is chosen. */
    val Light = SoundPalette(
        isLight = true,
        ink = Color(0xFFFAF7F1),
        surface1 = Color(0xFFF2EDE4),
        surface2 = Color(0xFFFFFDF9),
        surface3 = Color(0xFFE9E3D8),
        hairline = Color(0xFFE7E0D4),
        text = Color(0xFF2E2A25),
        textMuted = Color(0xFF6F675C),
        textFaint = Color(0xFFA59C8F),
        accent = Color(0xFF3F5B83),
        accentPressed = Color(0xFF344C6E),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0x1F3F5B83),
        mint = Color(0xFF3F5B83),
        onMint = Color(0xFFFFFFFF),
        lyric = Color(0xFF3F5B83),
        danger = Color(0xFFC4554A),
        scrim = Color(0x552E2A25),
        softTop = Color(0xFFEEF0F3),
        softBottom = Color(0xFFF8F6F2),
        washAlpha = 0.26f,
    )

    /** FreeVideo dark — paper at night: warm charcoal (not black), cream text, pale pen-blue. */
    val Dark = SoundPalette(
        isLight = false,
        ink = Color(0xFF1C1A17),
        surface1 = Color(0xFF26231F),
        surface2 = Color(0xFF2D2A25),
        surface3 = Color(0xFF38342E),
        hairline = Color(0xFF34302A),
        text = Color(0xFFF1EBE0),
        textMuted = Color(0xFFB7AEA0),
        textFaint = Color(0xFF7E766A),
        accent = Color(0xFFA9BCD8),
        accentPressed = Color(0xFF97ABC9),
        onAccent = Color(0xFF1C1A17),
        accentSoft = Color(0x29A9BCD8),
        mint = Color(0xFFA9BCD8),
        onMint = Color(0xFF1C1A17),
        lyric = Color(0xFFF3DD9C),
        danger = Color(0xFFF08A7E),
        scrim = Color(0xA6000000),
        softTop = Color(0xFF332824),
        softBottom = Color(0xFF211E1A),
        washAlpha = 0.36f,
    )
}

/** A highlight color the user can pick, tuned for each theme ([play] = what is playing). */
data class AccentDef(val id: String, val light: Color, val dark: Color, val playLight: Color = light, val playDark: Color = dark)

object SoundAccents {
    /**
     * FreeVideo's, all quiet: muted mid-tones on paper, light pastels at night. Pen-blue ink ("ink")
     * first; FreeSong's ids are kept so a saved choice still reads.
     */
    val all = listOf(
        AccentDef("ink", Color(0xFF3F5B83), Color(0xFFA9BCD8)),
        AccentDef("ember", Color(0xFFCF6A58), Color(0xFFF2A08F)),
        AccentDef("orange", Color(0xFFC97A3E), Color(0xFFF3B27E)),
        AccentDef("gold", Color(0xFFB38B32), Color(0xFFEACB7E)),
        AccentDef("green", Color(0xFF5E9874), Color(0xFFA3D2B2)),
        AccentDef("teal", Color(0xFF4E9396), Color(0xFF98CFD0)),
        AccentDef("blue", Color(0xFF5C80BE), Color(0xFFA8BEE9)),
        AccentDef("purple", Color(0xFF8A70C2), Color(0xFFC5B3EC)),
        AccentDef("pink", Color(0xFFC46B8E), Color(0xFFEFAEC6)),
    )

    /** Ids no longer offered ("red", "graphite") fall back to the ink. */
    fun byId(id: String?): AccentDef = all.firstOrNull { it.id == id } ?: all.first()
}

/** Soft hues for icon tiles and small marks: muted on paper, light pastels at night. */
object Pastel {
    private fun pick(light: Color, dark: Color) = if (SoundColors.palette.isLight) light else dark
    val Lilac: Color get() = pick(Color(0xFF8E74C2), Color(0xFFC6B5EC))
    val Coral: Color get() = pick(Color(0xFFCF6A58), Color(0xFFF2A592))
    val Sky: Color get() = pick(Color(0xFF5A8DBE), Color(0xFFA9C8EA))
    val Sage: Color get() = pick(Color(0xFF5E9A78), Color(0xFFA6D4B6))
    val Clay: Color get() = pick(Color(0xFFC0594A), Color(0xFFF0958A))
    val Honey: Color get() = pick(Color(0xFFBF8A3A), Color(0xFFEBC788))
}

/** Contrast ratio of two colors (WCAG). */
private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/** [this] palette with [a] as its highlight color (selection, buttons, player, equalizer). */
fun SoundPalette.withAccent(a: AccentDef): SoundPalette {
    val c = if (isLight) a.light else a.dark
    val play = if (isLight) a.playLight else a.playDark
    // Text on the highlight: white or the warm ink, whichever reads better (pastels take the ink).
    val darkInk = Color(0xFF2E1D18)
    fun on(bg: Color) = if (contrast(bg, Color.White) >= contrast(bg, darkInk)) Color.White else darkInk
    return copy(
        accent = c,
        accentPressed = lerp(c, if (isLight) Color.Black else Color.White, 0.1f),
        onAccent = on(c),
        accentSoft = c.copy(alpha = if (isLight) 0.14f else 0.16f),
        mint = play,
        onMint = on(play),
        lyric = if (isLight) c else lyric,
        softTop = lerp(ink, c, if (isLight) 0.12f else 0.13f),
        softBottom = lerp(ink, c, 0.03f),
    )
}

/**
 * The colors a piece of UI draws with. [SoundColors] is the app's theme; a part of the screen can be
 * handed another scheme through [LocalSoundColors] (the search page draws on its accent color).
 */
interface SoundColorScheme {
    val palette: SoundPalette

    val isLight: Boolean get() = palette.isLight
    val Ink: Color get() = palette.ink
    val Surface1: Color get() = palette.surface1
    val Surface2: Color get() = palette.surface2
    val Surface3: Color get() = palette.surface3
    val Hairline: Color get() = palette.hairline
    val Text: Color get() = palette.text
    val TextMuted: Color get() = palette.textMuted
    val TextFaint: Color get() = palette.textFaint
    val Accent: Color get() = palette.accent
    val AccentPressed: Color get() = palette.accentPressed
    val OnAccent: Color get() = palette.onAccent
    val AccentSoft: Color get() = palette.accentSoft
    val Mint: Color get() = palette.mint
    val OnMint: Color get() = palette.onMint
    val Lyric: Color get() = palette.lyric
    val Liked: Color get() = palette.mint
    val Danger: Color get() = palette.danger
    /** Good news (up to date): green, whatever the highlight color. */
    val Success: Color get() = if (palette.isLight) Color(0xFF12945A) else Color(0xFF4FD68F)
    val Scrim: Color get() = palette.scrim
    val SoftTop: Color get() = palette.softTop
    val SoftBottom: Color get() = palette.softBottom
}

/** A fixed scheme (the search page's). */
class FixedColors(override val palette: SoundPalette) : SoundColorScheme

/** The scheme this part of the screen draws with: the app's theme unless a parent handed another. */
val LocalSoundColors = staticCompositionLocalOf<SoundColorScheme> { SoundColors }

/** Short for [LocalSoundColors] in composables (capture it before a draw lambda). */
val sc: SoundColorScheme
    @Composable @ReadOnlyComposable get() = LocalSoundColors.current

/**
 * The search page's scheme: the highlight color is the page (a little deeper on the dark theme, so
 * text reads on it), and everything on it contrasts — text white or ink by the color's lightness,
 * fields and cards translucent over it, the "selected" color being the text color itself.
 */
fun searchPalette(base: SoundPalette): SoundPalette {
    val bg = if (base.isLight) base.accent else lerp(base.accent, Color.Black, 0.3f)
    val lightBg = bg.luminance() > 0.45f
    val on = if (lightBg) Color(0xFF0E0F14) else Color.White
    return base.copy(
        isLight = lightBg,
        ink = bg,
        surface1 = lerp(bg, on, 0.1f),
        surface2 = lerp(bg, on, 0.14f),
        surface3 = lerp(bg, on, 0.17f),
        hairline = lerp(bg, on, 0.22f),
        text = on,
        textMuted = lerp(bg, on, 0.78f),
        textFaint = lerp(bg, on, 0.58f),
        accent = on,
        accentPressed = lerp(on, bg, 0.12f),
        onAccent = bg,
        accentSoft = on.copy(alpha = 0.16f),
        mint = on,
        onMint = bg,
        lyric = on,
        softTop = lerp(bg, on, 0.1f),
        softBottom = bg,
    )
}

/**
 * The colors of the current theme. Backed by snapshot state: switching the theme recomposes and
 * redraws everything that read a color (composables and draw lambdas alike).
 */
object SoundColors : SoundColorScheme {
    override var palette: SoundPalette by mutableStateOf(SoundPalettes.Light)

    /** Brand colors of the logo — the same in every theme. */
    val BrandRoyal = Color(0xFF2341C9)
    val BrandMint = Color(0xFF8CF5D9)

    /**
     * The search page's color — the highlight color (see [searchPalette]). The circle that opens
     * it from the magnifier is this same color, so opening and closing read as one surface.
     */
    val Search: Color get() = searchPalette(palette).ink

    /** The mini player card: the theme's raised surface with a hint of the cover. */
    fun miniBackground(cover: Color): Color = lerp(palette.surface2, cover, if (palette.isLight) 0.14f else 0.2f)

    /** The full player / lyrics backdrop: the cover color, light and airy or deep and quiet. */
    fun playerBackground(cover: Color): Color =
        if (palette.isLight) lerp(cover, Color.White, 0.84f) else lerp(cover, palette.ink, 0.66f)

    fun lyricsBackground(cover: Color): Color =
        if (palette.isLight) lerp(cover, Color.White, 0.8f) else lerp(cover, palette.ink, 0.6f)
}
