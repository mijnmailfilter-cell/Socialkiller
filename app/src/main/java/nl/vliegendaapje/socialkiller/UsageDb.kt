package nl.vliegendaapje.socialkiller

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/**
 * Eenvoudige opslag. Alles blijft bewaard (geen automatische opschoning):
 *  - usage:    tijd per dag per app
 *  - sessions: elke sessie (start, totale duur, aantal pop-ups)
 */
class UsageDb private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx.applicationContext, "usage.db", null, 1) {

    companion object {
        @Volatile private var inst: UsageDb? = null
        fun get(ctx: Context): UsageDb =
            inst ?: synchronized(this) { inst ?: UsageDb(ctx).also { inst = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE usage (day TEXT NOT NULL, pkg TEXT NOT NULL, ms INTEGER NOT NULL, PRIMARY KEY(day, pkg))")
        db.execSQL("CREATE TABLE sessions (start INTEGER PRIMARY KEY, day TEXT NOT NULL, ms INTEGER NOT NULL, alerts INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun addUsage(day: LocalDate, pkg: String, ms: Long) {
        if (ms <= 0) return
        val d = day.toString()
        writableDatabase.execSQL(
            "INSERT INTO usage(day, pkg, ms) VALUES(?, ?, ?) ON CONFLICT(day, pkg) DO UPDATE SET ms = ms + excluded.ms",
            arrayOf<Any>(d, pkg, ms)
        )
    }

    fun saveSession(start: Long, day: LocalDate, ms: Long, alerts: Int) {
        if (start <= 0) return
        val cv = ContentValues().apply {
            put("start", start); put("day", day.toString()); put("ms", ms); put("alerts", alerts)
        }
        writableDatabase.insertWithOnConflict("sessions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Totaal per dag, inclusief begin en eind. */
    fun perDay(from: LocalDate, to: LocalDate): Map<LocalDate, Long> {
        val out = HashMap<LocalDate, Long>()
        readableDatabase.rawQuery(
            "SELECT day, SUM(ms) FROM usage WHERE day >= ? AND day <= ? GROUP BY day",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            while (c.moveToNext()) out[LocalDate.parse(c.getString(0))] = c.getLong(1)
        }
        return out
    }

    fun perApp(from: LocalDate, to: LocalDate): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        readableDatabase.rawQuery(
            "SELECT pkg, SUM(ms) AS t FROM usage WHERE day >= ? AND day <= ? GROUP BY pkg ORDER BY t DESC",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(c.getString(0) to c.getLong(1))
        }
        return out
    }

    data class SessionStats(val count: Int, val longestMs: Long, val alerts: Int)

    fun sessionStats(from: LocalDate, to: LocalDate): SessionStats {
        readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(MAX(ms),0), COALESCE(SUM(alerts),0) FROM sessions WHERE day >= ? AND day <= ? AND ms >= 60000",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            if (c.moveToFirst()) return SessionStats(c.getInt(0), c.getLong(1), c.getInt(2))
        }
        return SessionStats(0, 0, 0)
    }

    fun firstDay(): LocalDate? {
        readableDatabase.rawQuery("SELECT MIN(day) FROM usage", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return LocalDate.parse(c.getString(0))
        }
        return null
    }
}
