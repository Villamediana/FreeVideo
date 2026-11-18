package com.villamediana.sound.tube.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * What was watched: one row per video, updated while it plays. [watchedMs] is the time really spent
 * on it (all visits), [positionMs] where it was left (to resume), [plays] how many visits.
 */
@Entity(tableName = "watch_history", indices = [Index("lastWatchedAt"), Index("channelUrl")])
data class WatchEntry(
    @androidx.room.PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val channelUrl: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val positionMs: Long,
    val watchedMs: Long,
    val plays: Int,
    val firstWatchedAt: Long,
    val lastWatchedAt: Long,
) {
    /** Share of the video seen, 0..1 (0 when the length is unknown). */
    val completion: Float get() = if (durationMs > 0) (watchedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Where it was left, as a share of its length (the progress line on the thumbnail). */
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** A channel followed (no account: kept on the phone). */
@Entity(tableName = "subscriptions")
data class Subscription(
    @androidx.room.PrimaryKey val channelUrl: String,
    val name: String,
    val avatarUrl: String?,
    val subscribedAt: Long,
)

/** A video in one of the personal lists ([LIST_LIKED], [LIST_LATER]). */
@Entity(tableName = "saved_videos", primaryKeys = ["list", "videoId"], indices = [Index("savedAt")])
data class SavedVideo(
    val list: String,
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelUrl: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val savedAt: Long,
) {
    companion object {
        const val LIST_LIKED = "liked"
        const val LIST_LATER = "later"
    }
}

/** "Não tenho interesse" (a video) and "Não recomendar o canal" (a channel): kept out of the feed. */
@Entity(tableName = "dismissals")
data class Dismissal(
    /** "v:<videoId>" or "c:<channelUrl>". */
    @androidx.room.PrimaryKey val key: String,
    val title: String,
    val createdAt: Long,
) {
    companion object {
        fun video(id: String) = "v:$id"
        fun channel(url: String) = "c:$url"
    }
}

/** Searches typed (recent searches, and what the feed learns the person likes). */
@Entity(tableName = "searches")
data class SearchEntry(
    @androidx.room.PrimaryKey val query: String,
    val count: Int,
    val lastAt: Long,
)

/** Topics picked by hand ("Do que você gosta?") — the feed's start before there is any history. */
@Entity(tableName = "interests")
data class Interest(
    @androidx.room.PrimaryKey val topic: String,
    val createdAt: Long,
)

/**
 * A video the feed put on screen: how many times, and whether it was ever opened from there. Shown
 * again and again without a tap, it tires ([com.villamediana.sound.tube.feed.FeedEngine] lets it go).
 */
@Entity(tableName = "impressions")
data class Impression(
    @androidx.room.PrimaryKey val videoId: String,
    val shown: Int,
    val clicked: Int,
    val lastShownAt: Long,
)

/** What a watched video is about, as its uploader tagged it (better words than a title's). */
@Entity(tableName = "video_facts")
data class VideoFacts(
    @androidx.room.PrimaryKey val videoId: String,
    /** Tags joined by a line break. */
    val tags: String,
    val category: String?,
)

/** How each source of the feed does with this person: cards shown, opened, and time watched from them. */
@Entity(tableName = "source_stats")
data class SourceStat(
    @androidx.room.PrimaryKey val source: String,
    val shown: Long,
    val clicked: Long,
    val watchedMs: Long,
)

@Dao
interface TubeDao {
    // ------------------------------------------------------------ history
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun history(limit: Int = 500): Flow<List<WatchEntry>>

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC LIMIT :limit")
    suspend fun historyNow(limit: Int = 500): List<WatchEntry>

    @Query("SELECT * FROM watch_history WHERE videoId = :id")
    suspend fun watch(id: String): WatchEntry?

    @Query("SELECT * FROM watch_history WHERE videoId IN (:ids)")
    suspend fun watches(ids: List<String>): List<WatchEntry>

    @Upsert
    suspend fun putWatch(entry: WatchEntry)

    @Query("DELETE FROM watch_history WHERE videoId = :id")
    suspend fun deleteWatch(id: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()

    @Query("DELETE FROM watch_history WHERE videoId NOT IN (SELECT videoId FROM watch_history ORDER BY lastWatchedAt DESC LIMIT :keep)")
    suspend fun pruneHistory(keep: Int): Int

    // ------------------------------------------------------------ subscriptions
    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE")
    fun subscriptions(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions")
    suspend fun subscriptionsNow(): List<Subscription>

    @Upsert
    suspend fun putSubscription(s: Subscription)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSubscriptions(s: List<Subscription>): List<Long>

    @Query("DELETE FROM subscriptions WHERE channelUrl = :url")
    suspend fun deleteSubscription(url: String)

    // ------------------------------------------------------------ saved lists
    @Query("SELECT * FROM saved_videos WHERE list = :list ORDER BY savedAt DESC")
    fun saved(list: String): Flow<List<SavedVideo>>

    @Query("SELECT * FROM saved_videos WHERE list = :list ORDER BY savedAt DESC")
    suspend fun savedNow(list: String): List<SavedVideo>

    @Query("SELECT COUNT(*) FROM saved_videos WHERE list = :list AND videoId = :id")
    fun isSaved(list: String, id: String): Flow<Int>

    @Upsert
    suspend fun putSaved(v: SavedVideo)

    @Query("DELETE FROM saved_videos WHERE list = :list AND videoId = :id")
    suspend fun deleteSaved(list: String, id: String)

    // ------------------------------------------------------------ dismissals
    @Query("SELECT * FROM dismissals")
    fun dismissals(): Flow<List<Dismissal>>

    @Query("SELECT * FROM dismissals")
    suspend fun dismissalsNow(): List<Dismissal>

    @Upsert
    suspend fun putDismissal(d: Dismissal)

    @Query("DELETE FROM dismissals WHERE `key` = :key")
    suspend fun deleteDismissal(key: String)

    @Query("DELETE FROM dismissals")
    suspend fun clearDismissals()

    // ------------------------------------------------------------ searches
    @Query("SELECT * FROM searches ORDER BY lastAt DESC LIMIT :limit")
    fun searches(limit: Int = 30): Flow<List<SearchEntry>>

    @Query("SELECT * FROM searches ORDER BY lastAt DESC LIMIT :limit")
    suspend fun searchesNow(limit: Int = 60): List<SearchEntry>

    @Query("SELECT * FROM searches WHERE `query` = :q")
    suspend fun search(q: String): SearchEntry?

    @Upsert
    suspend fun putSearch(s: SearchEntry)

    @Query("DELETE FROM searches WHERE `query` = :q")
    suspend fun deleteSearch(q: String)

    @Query("DELETE FROM searches")
    suspend fun clearSearches()

    // ------------------------------------------------------------ interests
    @Query("SELECT * FROM interests ORDER BY createdAt")
    fun interests(): Flow<List<Interest>>

    @Query("SELECT * FROM interests ORDER BY createdAt")
    suspend fun interestsNow(): List<Interest>

    @Upsert
    suspend fun putInterests(list: List<Interest>)

    @Query("DELETE FROM interests WHERE topic = :topic")
    suspend fun deleteInterest(topic: String)

    @Query("DELETE FROM interests")
    suspend fun clearInterests()

    // ------------------------------------------------------------ what the feed learns from itself
    @Query("SELECT * FROM impressions WHERE lastShownAt >= :since")
    suspend fun impressionsSince(since: Long): List<Impression>

    @Query("SELECT * FROM impressions WHERE videoId IN (:ids)")
    suspend fun impressions(ids: List<String>): List<Impression>

    @Upsert
    suspend fun putImpressions(list: List<Impression>)

    @Query("DELETE FROM impressions WHERE lastShownAt < :before")
    suspend fun pruneImpressions(before: Long)

    @Query("DELETE FROM impressions")
    suspend fun clearImpressions()

    @Query("SELECT * FROM video_facts WHERE videoId IN (:ids)")
    suspend fun facts(ids: List<String>): List<VideoFacts>

    @Upsert
    suspend fun putFacts(f: VideoFacts)

    @Query("DELETE FROM video_facts WHERE videoId NOT IN (SELECT videoId FROM watch_history) AND videoId NOT IN (SELECT videoId FROM saved_videos)")
    suspend fun pruneFacts()

    @Query("SELECT * FROM source_stats")
    suspend fun sourceStats(): List<SourceStat>

    @Query("SELECT * FROM source_stats WHERE source = :source")
    suspend fun sourceStat(source: String): SourceStat?

    @Upsert
    suspend fun putSourceStat(s: SourceStat)

    @Query("DELETE FROM source_stats")
    suspend fun clearSourceStats()
}

@Database(
    entities = [
        WatchEntry::class, Subscription::class, SavedVideo::class, Dismissal::class, SearchEntry::class, Interest::class,
        Impression::class, VideoFacts::class, SourceStat::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class TubeDatabase : RoomDatabase() {
    abstract fun dao(): TubeDao

    companion object {
        fun build(context: Context): TubeDatabase =
            Room.databaseBuilder(context.applicationContext, TubeDatabase::class.java, "tube.db").addMigrations(MIGRATION_1_2).build()

        /** 1.1.0: what the feed learns from itself (impressions, tags of watched videos, how each source does). */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `impressions` (`videoId` TEXT NOT NULL, `shown` INTEGER NOT NULL, `clicked` INTEGER NOT NULL, `lastShownAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `video_facts` (`videoId` TEXT NOT NULL, `tags` TEXT NOT NULL, `category` TEXT, PRIMARY KEY(`videoId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `source_stats` (`source` TEXT NOT NULL, `shown` INTEGER NOT NULL, `clicked` INTEGER NOT NULL, `watchedMs` INTEGER NOT NULL, PRIMARY KEY(`source`))")
            }
        }
    }
}
