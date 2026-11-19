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
    /** FreeVideo light: paper white, cool greys, the ember for what is chosen. */
    val Light = SoundPalette(
        isLight = true,
        ink = Color(0xFFFFFFFF),
        surface1 = Color(0xFFF2F2F5),
        surface2 = Color(0xFFFFFFFF),
        surface3 = Color(0xFFE7E7EC),
        hairline = Color(0xFFE4E4EA),
        text = Color(0xFF111114),
        textMuted = Color(0xFF5E5E68),
        textFaint = Color(0xFF9A9AA5),
        accent = Color(0xFFE8304A),
        accentPressed = Color(0xFFD02540),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0x1AE8304A),
        mint = Color(0xFFE8304A),
        onMint = Color(0xFFFFFFFF),
        lyric = Color(0xFFE8304A),
        danger = Color(0xFFD92D20),
        scrim = Color(0x66000000),
        softTop = Color(0xFFFFF1F2),
        softBottom = Color(0xFFFFFAFA),
        washAlpha = 0.34f,
    )

    /** FreeVideo dark: a cinema — near-black, neutral greys (no blue cast), the picture stands out. */
    val Dark = SoundPalette(
        isLight = false,
        ink = Color(0xFF0B0B0E),
        surface1 = Color(0xFF17171C),
        surface2 = Color(0xFF1E1E24),
        surface3 = Color(0xFF29292F),
        hairline = Color(0xFF24242A),
        text = Color(0xFFF4F4F6),
        textMuted = Color(0xFFA4A4AE),
        textFaint = Color(0xFF6C6C76),
        accent = Color(0xFFFF4D62),
        accentPressed = Color(0xFFEE3F55),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0x29FF4D62),
        mint = Color(0xFFFF4D62),
        onMint = Color(0xFFFFFFFF),
        lyric = Color(0xFFF3DD7C),
        danger = Color(0xFFFF6B5E),
        scrim = Color(0xB3000000),
        softTop = Color(0xFF231417),
        softBottom = Color(0xFF121014),
        washAlpha = 0.46f,
    )
}

/** A highlight color the user can pick, tuned for each theme ([play] = what is playing). */
data class AccentDef(val id: String, val light: Color, val dark: Color, val playLight: Color = light, val playDark: Color = dark)

object SoundAccents {
    /** FreeVideo's: the ember first (the brand); FreeSong's ids are kept so a saved choice still reads. */
    val all = listOf(
        AccentDef("ember", Color(0xFFE8304A), Color(0xFFFF4D62)),
        AccentDef("orange", Color(0xFFE8620F), Color(0xFFFF8A3D)),
        AccentDef("gold", Color(0xFFC98A00), Color(0xFFFFC23D)),
        AccentDef("green", Color(0xFF12945A), Color(0xFF4FD68F)),
        AccentDef("teal", Color(0xFF0B8F9C), Color(0xFF45D3DD)),
        AccentDef("blue", Color(0xFF2F6BFF), Color(0xFF6E98FF)),
        AccentDef("purple", Color(0xFF7646F5), Color(0xFFA685FF)),
        AccentDef("pink", Color(0xFFD9327A), Color(0xFFFF6FAE)),
    )

    /** Ids no longer offered ("red", "graphite") fall back to the ember. */
    fun byId(id: String?): AccentDef = all.firstOrNull { it.id == id } ?: all.first()
}

/** [this] palette with [a] as its highlight color (selection, buttons, player, equalizer). */
fun SoundPalette.withAccent(a: AccentDef): SoundPalette {
    val c = if (isLight) a.light else a.dark
    val play = if (isLight) a.playLight else a.playDark
    val onDark = lerp(c, Color.Black, 0f).luminance() > 0.5f
    return copy(
        accent = c,
        accentPressed = lerp(c, if (isLight) Color.Black else Color.White, 0.12f),
        onAccent = if (onDark) Color(0xFF0E0F14) else Color.White,
        accentSoft = c.copy(alpha = if (isLight) 0.12f else 0.16f),
        mint = play,
        onMint = if (play.luminance() > 0.5f) Color(0xFF0E0F14) else Color.White,
        lyric = if (isLight) c else lyric,
        softTop = if (isLight) lerp(Color.White, c, 0.08f) else lerp(Color(0xFF121216), c, 0.14f),
        softBottom = if (isLight) lerp(Color.White, c, 0.025f) else Color(0xFF121216),
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
