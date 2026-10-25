package com.bruce.left

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.sqrt

data class Countdown(
    val id: Long,
    val title: String,
    val target: LocalDate,
    val created: LocalDate,
)

/** Ce qu'affiche une carte ou un widget : une grille de [total] points dont [elapsed] sont passés. */
data class TimeProgress(
    val title: String,
    val subtitle: String,
    val total: Int,
    val elapsed: Int,
    val fraction: Float,
    val columns: Int,
)

enum class WidgetKind(val label: String) {
    DAY("Jour"),
    WEEK("Semaine"),
    MONTH("Mois"),
    YEAR("Année"),
    LIFE("Vie"),
    COUNTDOWN("Décompte");

    val providerClass: Class<*>
        get() = when (this) {
            DAY -> DayWidget::class.java
            WEEK -> WeekWidget::class.java
            MONTH -> MonthWidget::class.java
            YEAR -> YearWidget::class.java
            LIFE -> LifeWidget::class.java
            COUNTDOWN -> CountdownWidget::class.java
        }
}

object TimeCalc {
    private val FR: Locale = Locale.FRENCH

    private fun plural(n: Long, one: String, many: String) = if (n <= 1L) "$n $one" else "$n $many"

    fun day(now: LocalDateTime, withSeconds: Boolean): TimeProgress {
        val sec = now.toLocalTime().toSecondOfDay()
        val left = 86_400 - sec
        val h = left / 3600
        val m = (left % 3600) / 60
        val s = left % 60
        val sub = if (withSeconds) {
            String.format(FR, "%d h %02d min %02d s restantes", h, m, s)
        } else {
            String.format(FR, "%d h %02d restantes", h, m)
        }
        return TimeProgress("Aujourd'hui", sub, 24, now.hour, sec / 86_400f, 12)
    }

    fun week(now: LocalDateTime): TimeProgress {
        val dow = now.dayOfWeek.value // 1 = lundi
        val sec = (dow - 1) * 86_400L + now.toLocalTime().toSecondOfDay()
        val left = (7 - dow).toLong()
        val sub = if (left == 0L) "Dernier jour" else plural(left, "jour restant", "jours restants")
        val title = "Semaine " + now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return TimeProgress(title, sub, 7, dow - 1, sec / 604_800f, 7)
    }

    fun month(now: LocalDateTime): TimeProgress {
        val d = now.dayOfMonth
        val len = now.toLocalDate().lengthOfMonth()
        val sec = (d - 1) * 86_400L + now.toLocalTime().toSecondOfDay()
        val left = (len - d).toLong()
        val sub = if (left == 0L) "Dernier jour" else plural(left, "jour restant", "jours restants")
        val title = now.month.getDisplayName(TextStyle.FULL, FR).replaceFirstChar { it.titlecase(FR) }
        return TimeProgress(title, sub, len, d - 1, sec / (len * 86_400f), 7)
    }

    fun year(now: LocalDateTime): TimeProgress {
        val doy = now.dayOfYear
        val len = now.toLocalDate().lengthOfYear()
        val sec = (doy - 1) * 86_400L + now.toLocalTime().toSecondOfDay()
        val left = (len - doy).toLong()
        val sub = if (left == 0L) "Dernier jour" else plural(left, "jour restant", "jours restants")
        return TimeProgress(now.year.toString(), sub, len, doy - 1, sec / (len * 86_400f), 20)
    }

    fun life(today: LocalDate, birth: LocalDate?, lifespan: Int): TimeProgress {
        if (birth == null) return TimeProgress("Ta vie", "Ajoute ta date de naissance", 0, 0, 0f, 10)
        val age = Period.between(birth, today).years.coerceAtLeast(0)
        val end = birth.plusYears(lifespan.toLong())
        val totalD = ChronoUnit.DAYS.between(birth, end).coerceAtLeast(1L)
        val lived = ChronoUnit.DAYS.between(birth, today).coerceIn(0L, totalD)
        val leftY = (lifespan - age).coerceAtLeast(0).toLong()
        val sub = "$age ans · " + plural(leftY, "an restant", "ans restants")
        return TimeProgress("Ta vie", sub, lifespan, age.coerceAtMost(lifespan), lived.toFloat() / totalD, 10)
    }

    fun lifeMonths(today: LocalDate, birth: LocalDate, lifespan: Int): TimeProgress {
        val total = lifespan * 12
        val months = ChronoUnit.MONTHS.between(birth, today).coerceIn(0L, total.toLong()).toInt()
        val end = birth.plusYears(lifespan.toLong())
        val totalD = ChronoUnit.DAYS.between(birth, end).coerceAtLeast(1L)
        val lived = ChronoUnit.DAYS.between(birth, today).coerceIn(0L, totalD)
        val sub = "$months mois vécus · ${total - months} restants"
        return TimeProgress("Ta vie en mois", sub, total, months, lived.toFloat() / totalD, 24)
    }

    fun countdown(cd: Countdown, today: LocalDate): TimeProgress {
        val totalD = ChronoUnit.DAYS.between(cd.created, cd.target).coerceAtLeast(1L)
        val passed = ChronoUnit.DAYS.between(cd.created, today).coerceIn(0L, totalD)
        val remaining = ChronoUnit.DAYS.between(today, cd.target)
        val total: Int
        val elapsed: Int
        when {
            totalD <= 400L -> {
                total = totalD.toInt(); elapsed = passed.toInt()
            }
            totalD <= 2_800L -> {
                total = ceil(totalD / 7.0).toInt(); elapsed = (passed / 7L).toInt()
            }
            else -> {
                total = ceil(totalD / 30.44).toInt(); elapsed = (passed / 30.44).toInt()
            }
        }
        val sub = when {
            remaining > 1L -> "Dans $remaining jours"
            remaining == 1L -> "Demain"
            remaining == 0L -> "C'est aujourd'hui"
            remaining == -1L -> "C'était hier"
            else -> "Il y a ${-remaining} jours"
        }
        val cols = ceil(sqrt(total * 1.8)).toInt().coerceIn(1, 20)
        return TimeProgress(cd.title, sub, total, elapsed, passed.toFloat() / totalD, cols)
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("left", Context.MODE_PRIVATE)

    fun birth(c: Context): LocalDate? {
        val v = sp(c).getLong("birth", Long.MIN_VALUE)
        return if (v == Long.MIN_VALUE) null else LocalDate.ofEpochDay(v)
    }

    fun setBirth(c: Context, d: LocalDate?) {
        sp(c).edit {
            if (d == null) remove("birth") else putLong("birth", d.toEpochDay())
        }
    }

    fun lifespan(c: Context): Int = sp(c).getInt("lifespan", 80)
    fun setLifespan(c: Context, v: Int) = sp(c).edit { putInt("lifespan", v) }

    fun square(c: Context): Boolean = sp(c).getBoolean("square", false)
    fun setSquare(c: Context, v: Boolean) = sp(c).edit { putBoolean("square", v) }

    fun glass(c: Context): Boolean = sp(c).getBoolean("glass", true)
    fun setGlass(c: Context, v: Boolean) = sp(c).edit { putBoolean("glass", v) }

    fun anim(c: Context): Boolean = sp(c).getBoolean("anim", true)
    fun setAnim(c: Context, v: Boolean) = sp(c).edit { putBoolean("anim", v) }

    fun countdowns(c: Context): List<Countdown> {
        val raw = sp(c).getString("countdowns", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Countdown(
                    o.getLong("id"),
                    o.getString("title"),
                    LocalDate.ofEpochDay(o.getLong("target")),
                    LocalDate.ofEpochDay(o.getLong("created")),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCountdowns(c: Context, list: List<Countdown>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("target", it.target.toEpochDay())
                    .put("created", it.created.toEpochDay())
            )
        }
        sp(c).edit { putString("countdowns", arr.toString()) }
    }

    fun widgetCountdown(c: Context, widgetId: Int): Long? {
        val v = sp(c).getLong("w_$widgetId", Long.MIN_VALUE)
        return if (v == Long.MIN_VALUE) null else v
    }

    fun setWidgetCountdown(c: Context, widgetId: Int, countdownId: Long) =
        sp(c).edit { putLong("w_$widgetId", countdownId) }

    fun removeWidget(c: Context, widgetId: Int) = sp(c).edit { remove("w_$widgetId") }
}
