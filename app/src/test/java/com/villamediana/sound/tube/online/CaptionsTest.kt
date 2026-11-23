package com.villamediana.sound.tube.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionsTest {
    private val json3 = """
        {"wireMagic":"pb3","events":[
          {"tStartMs":0,"dDurationMs":4000,"id":1,"wpWinPosId":1,"wsWinStyleId":1},
          {"tStartMs":120,"dDurationMs":3900,"wWinId":1,"segs":[{"utf8":"hoje"},{"utf8":" vamos","tOffsetMs":400},{"utf8":" modelar","tOffsetMs":900}]},
          {"tStartMs":2000,"dDurationMs":2000,"wWinId":1,"aAppend":1,"segs":[{"utf8":"\n"}]},
          {"tStartMs":2010,"dDurationMs":4100,"wWinId":1,"segs":[{"utf8":"uma"},{"utf8":" cabeça","tOffsetMs":300},{"utf8":" no blender.","tOffsetMs":700}]},
          {"tStartMs":9000,"dDurationMs":2500,"wWinId":1,"segs":[{"utf8":"Primeiro"},{"utf8":"  o cubo","tOffsetMs":500}]}
        ]}
    """.trimIndent()

    @Test
    fun youTubesJson3BecomesOneLineAtATime() {
        val cues = Captions.parseJson3(json3)
        assertEquals(listOf("hoje vamos modelar", "uma cabeça no blender.", "Primeiro o cubo"), cues.map { it.text })
        // The automatic line stays up while the next is written: here it ends where the next starts.
        assertEquals(2010L, cues[0].endMs)
        assertEquals(6110L, cues[1].endMs)
        assertEquals("uma cabeça no blender.", Captions.at(cues, 3_000)?.text)
        assertNull(Captions.at(cues, 7_000))
        assertEquals("hoje vamos modelar", Captions.at(cues, 120)?.text)
        assertTrue(Captions.parseJson3("not json").isEmpty())
        assertTrue(Captions.parseJson3("""{"events":[]}""").isEmpty())
    }

    @Test
    fun theUrlAsksForJson3AndForTheTranslation() {
        val base = "https://www.youtube.com/api/timedtext?v=abc&lang=en&fmt=vtt&tlang=de"
        assertEquals("https://www.youtube.com/api/timedtext?v=abc&lang=en&fmt=json3", Captions.json3Url(base))
        assertEquals("https://www.youtube.com/api/timedtext?v=abc&lang=en&fmt=json3&tlang=pt", Captions.json3Url(base, "pt"))
    }

    @Test
    fun paragraphsBreakAtPausesAndAfterAWhile() {
        val cues = (0 until 20).map { i -> Cue(i * 3_000L, i * 3_000L + 2_900, "linha $i") } + Cue(70_000, 72_000, "depois da pausa")
        val paragraphs = Captions.paragraphs(cues, maxMs = 25_000)
        assertTrue(paragraphs.all { it.endMs - it.startMs <= 28_000 })
        assertEquals("depois da pausa", paragraphs.last().text)
        assertEquals(0L, paragraphs.first().startMs)
        assertEquals(1, Captions.indexAt(paragraphs, paragraphs[1].startMs + 10))
        assertEquals(-1, Captions.indexAt(paragraphs, -5))
    }

    @Test
    fun theSummaryFollowsTheChaptersOrCutsTheVideoEvenly() {
        val cues = (0 until 60).map { i -> Cue(i * 10_000L, i * 10_000L + 9_000, "frase $i") }
        val chapters = listOf(Chapter("Intro", 0, "https://i.ytimg.com/a.jpg"), Chapter("Modelagem", 200_000, null), Chapter("Render", 450_000, null))
        val byChapter = Captions.sections(cues, chapters, durationMs = 600_000)
        assertEquals(listOf("Intro", "Modelagem", "Render"), byChapter.map { it.title })
        assertEquals(listOf(0L, 200_000L, 450_000L), byChapter.map { it.startMs })
        assertEquals(600_000L, byChapter.last().endMs)
        assertTrue(byChapter[1].text.startsWith("frase 20"))
        assertTrue(byChapter.all { it.text.length <= 281 })

        val even = Captions.sections(cues, emptyList(), durationMs = 600_000, parts = 8)
        assertTrue(even.size in 6..10)
        assertTrue(even.all { it.title == null })
        assertEquals(0L, even.first().startMs)
    }

    @Test
    fun theCaptionsChosenAreTheChannelsOwnInThePersonsLanguageThenYouTubesThenATranslation() {
        val en = CaptionTrack("u-en", "en", "English", auto = false)
        val ptAuto = CaptionTrack("u-pt-auto", "pt", "Português", auto = true)
        val ptBr = CaptionTrack("u-pt-br", "pt-BR", "Português (Brasil)", auto = false)
        assertEquals(CaptionChoice(ptBr), Captions.choose(listOf(en, ptAuto, ptBr), "pt"))
        assertEquals(CaptionChoice(ptAuto), Captions.choose(listOf(en, ptAuto), "pt"))
        assertEquals(CaptionChoice(en, translateTo = "es"), Captions.choose(listOf(en, ptAuto), "es"))
        assertNull(Captions.choose(emptyList(), "pt"))
    }

    @Test
    fun aLongTextIsCutAtAWord() {
        assertEquals("curto", Captions.cut("curto", 20))
        assertEquals("uma frase que é…", Captions.cut("uma frase que é longa demais", 17))
    }
}
