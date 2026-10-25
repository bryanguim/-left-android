package com.bruce.left

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.time.LocalDate

/** État partagé de l'app, sauvegardé dans les préférences. */
class AppState(private val ctx: Context) {
    var birth by mutableStateOf(Prefs.birth(ctx))
        private set
    var lifespan by mutableIntStateOf(Prefs.lifespan(ctx))
    var square by mutableStateOf(Prefs.square(ctx))
        private set
    var glass by mutableStateOf(Prefs.glass(ctx))
        private set
    var anim by mutableStateOf(Prefs.anim(ctx))
        private set
    val countdowns = mutableStateListOf<Countdown>().also { it.addAll(Prefs.countdowns(ctx)) }

    fun reload() {
        birth = Prefs.birth(ctx)
        lifespan = Prefs.lifespan(ctx)
        square = Prefs.square(ctx)
        glass = Prefs.glass(ctx)
        anim = Prefs.anim(ctx)
        countdowns.clear()
        countdowns.addAll(Prefs.countdowns(ctx))
    }

    fun updateBirth(d: LocalDate) {
        birth = d
        Prefs.setBirth(ctx, d)
        refresh()
    }

    fun saveLifespan() {
        Prefs.setLifespan(ctx, lifespan)
        refresh()
    }

    fun updateSquare(v: Boolean) {
        square = v
        Prefs.setSquare(ctx, v)
        refresh()
    }

    fun updateGlass(v: Boolean) {
        glass = v
        Prefs.setGlass(ctx, v)
        refresh()
    }

    fun updateAnim(v: Boolean) {
        anim = v
        Prefs.setAnim(ctx, v)
        refresh()
    }

    fun addCountdown(title: String, target: LocalDate): Countdown {
        val cd = Countdown(System.currentTimeMillis(), title, target, LocalDate.now())
        countdowns.add(cd)
        Prefs.saveCountdowns(ctx, countdowns.toList())
        refresh()
        return cd
    }

    fun deleteCountdown(cd: Countdown) {
        countdowns.removeAll { it.id == cd.id }
        Prefs.saveCountdowns(ctx, countdowns.toList())
        refresh()
    }

    private fun refresh() {
        WidgetUpdater.updateAll(ctx)
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        state = AppState(applicationContext)
        setContent {
            LeftTheme { LeftApp(state) }
        }
    }

    override fun onResume() {
        super.onResume()
        state.reload()
        if (WidgetUpdater.updateAll(this) > 0) WidgetUpdater.scheduleNext(this)
    }
}

@Composable
fun LeftApp(state: AppState) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        LiquidBackground()
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(380)) + scaleIn(tween(380), initialScale = 0.97f)) togetherWith fadeOut(tween(180))
            },
            label = "tab",
        ) { t ->
            when (t) {
                0 -> TimeScreen(state) { tab = 2 }
                1 -> CountdownScreen(state)
                else -> SettingsScreen(state)
            }
        }
        GlassNavBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
    }
}

/** Écran ouvert par le launcher quand on pose un widget Décompte. */
class CountdownConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        val state = AppState(applicationContext)
        setContent {
            LeftTheme { ConfigScreen(state) { choose(it) } }
        }
    }

    private fun choose(cd: Countdown) {
        Prefs.setWidgetCountdown(this, widgetId, cd.id)
        WidgetUpdater.updateAll(this)
        WidgetUpdater.scheduleNext(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
