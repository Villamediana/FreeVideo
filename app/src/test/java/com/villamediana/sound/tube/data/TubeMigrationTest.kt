package com.villamediana.sound.tube.data

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 1.0.0's database opens in 1.1.0 with everything the person had, plus the new (empty) tables. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TubeMigrationTest {
    @get:Rule
    val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TubeDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test
    fun theHistoryAndSubscriptionsOf100SurviveInto110() {
        migrations.createDatabase("tube-migrate.db", 1).apply {
            execSQL("INSERT INTO watch_history (videoId, title, channelName, channelUrl, thumbnailUrl, durationMs, positionMs, watchedMs, plays, firstWatchedAt, lastWatchedAt) VALUES ('abcdefghijk', 'Um vídeo', 'Canal', 'https://www.youtube.com/channel/UCx', NULL, 600000, 300000, 300000, 2, 1, 2)")
            execSQL("INSERT INTO subscriptions (channelUrl, name, avatarUrl, subscribedAt) VALUES ('https://www.youtube.com/channel/UCx', 'Canal', NULL, 1)")
            execSQL("INSERT INTO saved_videos (list, videoId, title, channelName, channelUrl, thumbnailUrl, durationMs, savedAt) VALUES ('liked', 'abcdefghijk', 'Um vídeo', 'Canal', NULL, NULL, 600000, 3)")
            close()
        }
        val db = migrations.runMigrationsAndValidate("tube-migrate.db", 2, true, TubeDatabase.MIGRATION_1_2)
        db.query("SELECT title, plays FROM watch_history").use { c ->
            c.moveToFirst()
            assertEquals("Um vídeo", c.getString(0))
            assertEquals(2, c.getInt(1))
        }
        db.query("SELECT COUNT(*) FROM subscriptions").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM saved_videos").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        for (table in listOf("impressions", "video_facts", "source_stats")) {
            db.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
        db.close()
    }
}
