package com.bruce.left

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import java.time.LocalDateTime
import kotlin.math.sqrt

abstract class BaseWidget(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetUpdater.updateOne(context, appWidgetManager, it, kind) }
        WidgetUpdater.scheduleNext(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        WidgetUpdater.updateOne(context, appWidgetManager, appWidgetId, kind)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.scheduleNext(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { Prefs.removeWidget(context, it) }
    }
}

class DayWidget : BaseWidget(WidgetKind.DAY)
class WeekWidget : BaseWidget(WidgetKind.WEEK)
class MonthWidget : BaseWidget(WidgetKind.MONTH)
class YearWidget : BaseWidget(WidgetKind.YEAR)
class LifeWidget : BaseWidget(WidgetKind.LIFE)
class CountdownWidget : BaseWidget(WidgetKind.COUNTDOWN)

object WidgetUpdater {
    const val ACTION_TICK = "com.bruce.left.TICK"
    const val ACTION_PINNED = "com.bruce.left.PINNED"
    const val EXTRA_CD = "countdown_id"

    private var emptyBitmap: Bitmap? = null

    private fun empty(): Bitmap =
        emptyBitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also { emptyBitmap = it }

    /** Met à jour tous les widgets posés. Renvoie leur nombre. */
    fun updateAll(c: Context): Int {
        val mgr = AppWidgetManager.getInstance(c)
        var n = 0
        for (k in WidgetKind.values()) {
            val ids = mgr.getAppWidgetIds(ComponentName(c, k.providerClass))
            ids.forEach { updateOne(c, mgr, it, k) }
            n += ids.size
        }
        return n
    }

    private fun progress(c: Context, kind: WidgetKind, widgetId: Int): TimeProgress {
        val now = LocalDateTime.now()
        return when (kind) {
            WidgetKind.DAY -> TimeCalc.day(now, false)
            WidgetKind.WEEK -> TimeCalc.week(now)
            WidgetKind.MONTH -> TimeCalc.month(now)
            WidgetKind.YEAR -> TimeCalc.year(now)
            WidgetKind.LIFE -> TimeCalc.life(now.toLocalDate(), Prefs.birth(c), Prefs.lifespan(c))
            WidgetKind.COUNTDOWN -> {
                val cid = Prefs.widgetCountdown(c, widgetId)
                val cd = Prefs.countdowns(c).firstOrNull { it.id == cid }
                if (cd == null) {
                    TimeProgress("Décompte", "Touche pour choisir", 0, 0, 0f, 1)
                } else {
                    TimeCalc.countdown(cd, now.toLocalDate())
                }
            }
        }
    }

    fun updateOne(c: Context, mgr: AppWidgetManager, widgetId: Int, kind: WidgetKind) {
        try {
            val opts = mgr.getAppWidgetOptions(widgetId)
            var wDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            var hDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
            if (wDp <= 0) wDp = 160
            if (hDp <= 0) hDp = 160

            var density = c.resources.displayMetrics.density
            var w = wDp * density
            var h = hDp * density
            val maxPx = 480_000f
            if (w * h > maxPx) {
                val f = sqrt(maxPx / (w * h))
                w *= f
                h *= f
                density *= f
            }
            val wi = w.toInt().coerceAtLeast(60)
            val hi = h.toInt().coerceAtLeast(60)

            val p = progress(c, kind, widgetId)
            val glass = Prefs.glass(c)
            val square = Prefs.square(c)
            val animate = Prefs.anim(c) && p.total > 0 && p.elapsed < p.total

            val rv = RemoteViews(c.packageName, R.layout.widget_root)
            if (animate) {
                rv.setImageViewBitmap(R.id.base, WidgetRenderer.render(p, wi, hi, density, glass, square, WidgetRenderer.Layer.BASE))
                rv.setImageViewBitmap(R.id.glow_on, WidgetRenderer.render(p, wi, hi, density, glass, square, WidgetRenderer.Layer.GLOW))
            } else {
                rv.setImageViewBitmap(R.id.base, WidgetRenderer.render(p, wi, hi, density, glass, square, WidgetRenderer.Layer.STATIC))
                rv.setImageViewBitmap(R.id.glow_on, empty())
            }
            rv.setImageViewBitmap(R.id.glow_off, empty())

            val intent = if (kind == WidgetKind.COUNTDOWN && p.total <= 0) {
                Intent(c, CountdownConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            } else {
                Intent(c, MainActivity::class.java)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(
                c, widgetId, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            mgr.updateAppWidget(widgetId, rv)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Prochaine mise à jour au prochain quart d'heure pile. */
    fun scheduleNext(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val period = 15 * 60_000L
        val now = System.currentTimeMillis()
        val next = (now / period + 1) * period + 3_000L
        val pi = PendingIntent.getBroadcast(
            c, 1,
            Intent(c, UpdateReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.set(AlarmManager.RTC, next, pi)
    }

    /** Demande au launcher d'épingler un widget sur l'écran d'accueil. */
    fun pin(c: Context, kind: WidgetKind, countdownId: Long? = null): Boolean {
        val mgr = AppWidgetManager.getInstance(c)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        val callback = countdownId?.let {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            PendingIntent.getBroadcast(
                c, (it % 100_000L).toInt() + 100,
                Intent(c, UpdateReceiver::class.java)
                    .setAction(ACTION_PINNED)
                    .putExtra(EXTRA_CD, it),
                flags,
            )
        }
        return mgr.requestPinAppWidget(ComponentName(c, kind.providerClass), null, callback)
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetUpdater.ACTION_PINNED) {
            val wid = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            val cd = intent.getLongExtra(WidgetUpdater.EXTRA_CD, -1L)
            if (wid != -1 && cd != -1L) Prefs.setWidgetCountdown(context, wid, cd)
        }
        val n = WidgetUpdater.updateAll(context)
        if (n > 0) WidgetUpdater.scheduleNext(context)
    }
}
