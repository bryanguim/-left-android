package com.bruce.left

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val FR: Locale = Locale.FRENCH
private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", FR)
private val headerDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)

@Composable
private fun screenPadding(): PaddingValues {
    val bars = WindowInsets.systemBars.asPaddingValues()
    return PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = bars.calculateTopPadding() + 28.dp,
        bottom = bars.calculateBottomPadding() + 120.dp,
    )
}

/* ---------------------------------------------------------------- Carte de progression */

@Composable
fun ProgressCard(
    p: TimeProgress,
    square: Boolean,
    decimals: Int = 1,
    columns: Int = p.columns,
    footer: (@Composable () -> Unit)? = null,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(p.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(p.subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
            }
            if (p.total > 0) AnimatedPercent(p.fraction, decimals)
        }
        if (p.total > 0) {
            Spacer(Modifier.height(18.dp))
            DotGrid(p.total, p.elapsed, columns, square)
        }
        if (footer != null) {
            Spacer(Modifier.height(16.dp))
            footer()
        }
    }
}

/* ---------------------------------------------------------------- Onglet Temps */

@Composable
fun TimeScreen(state: AppState, goSettings: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L - System.currentTimeMillis() % 1000L)
            now = LocalDateTime.now()
        }
    }
    var lifeMonths by rememberSaveable { mutableStateOf(false) }
    val birth = state.birth

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Appear(0) {
                Header("Left", now.format(headerDate).replaceFirstChar { it.titlecase(FR) })
            }
        }
        item { Appear(1) { ProgressCard(TimeCalc.day(now, true), state.square, decimals = 2) } }
        item { Appear(2) { ProgressCard(TimeCalc.week(now), state.square) } }
        item { Appear(3) { ProgressCard(TimeCalc.month(now), state.square) } }
        item { Appear(4) { ProgressCard(TimeCalc.year(now), state.square, decimals = 2) } }
        item {
            Appear(5) {
                if (birth == null) {
                    GlassCard(Modifier.fillMaxWidth(), onClick = goSettings) {
                        Text("Ta vie", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ajoute ta date de naissance dans Réglages pour voir ta vie en points.",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    val p = if (lifeMonths) {
                        TimeCalc.lifeMonths(now.toLocalDate(), birth, state.lifespan)
                    } else {
                        TimeCalc.life(now.toLocalDate(), birth, state.lifespan)
                    }
                    ProgressCard(p, state.square, decimals = 2, footer = {
                        GlassSegmented(listOf("Années", "Mois"), if (lifeMonths) 1 else 0) {
                            lifeMonths = it == 1
                        }
                    })
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- Onglet Décomptes */

@Composable
fun CountdownScreen(state: AppState) {
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val list = state.countdowns.sortedBy { it.target }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Appear(0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Header(
                        "Décomptes",
                        if (list.isEmpty()) "Aucun événement" else (if (list.size > 1) "${list.size} événements" else "1 événement"),
                        Modifier.weight(1f),
                    )
                    GlassPill("+ Nouveau", filled = true) { adding = true }
                }
            }
        }
        if (list.isEmpty()) {
            item {
                Appear(1) {
                    GlassCard(Modifier.fillMaxWidth(), onClick = { adding = true }) {
                        Text("Rien à décompter", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Vacances, anniversaire, tournoi… ajoute un événement et regarde les jours s'effacer.",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        itemsIndexed(list, key = { _, cd -> cd.id }) { i, cd ->
            Appear(i + 1) {
                var confirm by remember(cd.id) { mutableStateOf(false) }
                ProgressCard(TimeCalc.countdown(cd, today), state.square, footer = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            cd.target.format(longDate),
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        GlassPill("Widget") {
                            if (!WidgetUpdater.pin(ctx, WidgetKind.COUNTDOWN, cd.id)) {
                                Toast.makeText(ctx, "Ajoute-le depuis l'écran d'accueil (appui long → Widgets)", Toast.LENGTH_LONG).show()
                            }
                        }
                        GlassPill(if (confirm) "Sûr ?" else "Supprimer", filled = confirm) {
                            if (confirm) state.deleteCountdown(cd) else confirm = true
                        }
                    }
                })
            }
        }
    }

    if (adding) {
        AddCountdownDialog(
            onDismiss = { adding = false },
            onAdd = { title, date ->
                state.addCountdown(title, date)
                adding = false
            },
        )
    }
}

@Composable
fun AddCountdownDialog(onDismiss: () -> Unit, onAdd: (String, LocalDate) -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            Modifier
                .fillMaxWidth()
                .background(Color(0xF00A0A0A), RoundedCornerShape(28.dp))
        ) {
            Text("Nouveau décompte", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(40) },
                label = { Text("Nom de l'événement") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth(), onClick = { picking = true }, corner = 16.dp, inner = 16.dp) {
                val d = date
                Text(
                    if (d == null) "Choisir une date" else d.format(longDate),
                    color = if (d == null) Color.White.copy(alpha = 0.55f) else Color.White,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassPill("Annuler", onClick = onDismiss)
                GlassPill("Créer", filled = true) {
                    val d = date
                    if (title.isNotBlank() && d != null) onAdd(title.trim(), d)
                }
            }
        }
    }

    if (picking) {
        LeftDatePicker(date ?: LocalDate.now().plusDays(30), onDismiss = { picking = false }) { date = it }
    }
}

/* ---------------------------------------------------------------- Onglet Réglages */

@Composable
fun SettingsScreen(state: AppState) {
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Appear(0) { Header("Réglages", "Ton temps, ton style") } }

        item {
            Appear(1) {
                GlassCard(Modifier.fillMaxWidth(), onClick = { picking = true }) {
                    Label("Date de naissance")
                    Spacer(Modifier.height(6.dp))
                    val b = state.birth
                    Text(
                        if (b == null) "Touche pour l'ajouter" else b.format(longDate),
                        color = if (b == null) Color.White.copy(alpha = 0.55f) else Color.White,
                        fontSize = 18.sp,
                    )
                }
            }
        }

        item {
            Appear(2) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("Espérance de vie", Modifier.weight(1f))
                        Text("${state.lifespan} ans", color = Color.White, fontSize = 16.sp)
                    }
                    Slider(
                        value = state.lifespan.toFloat(),
                        onValueChange = { state.lifespan = it.roundToInt() },
                        onValueChangeFinished = { state.saveLifespan() },
                        valueRange = 50f..110f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                        ),
                    )
                }
            }
        }

        item {
            Appear(3) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("Forme des points", Modifier.weight(1f))
                        GlassSegmented(listOf("Ronds", "Carrés"), if (state.square) 1 else 0) {
                            state.updateSquare(it == 1)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("Style des widgets", Modifier.weight(1f))
                        GlassSegmented(listOf("Verre", "Noir"), if (state.glass) 0 else 1) {
                            state.updateGlass(it == 0)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("Point qui pulse sur les widgets", Modifier.weight(1f))
                        Switch(
                            checked = state.anim,
                            onCheckedChange = { state.updateAnim(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color.White,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                                uncheckedBorderColor = Color.White.copy(alpha = 0.3f),
                            ),
                        )
                    }
                }
            }
        }

        item {
            Appear(4) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Label("Ajouter un widget")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Touche un widget pour le poser sur ton écran d'accueil.",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    WidgetKind.values().filter { it != WidgetKind.COUNTDOWN }.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { k ->
                                GlassPill(k.label, Modifier.weight(1f)) {
                                    if (!WidgetUpdater.pin(ctx, k)) {
                                        Toast.makeText(ctx, "Appui long sur l'écran d'accueil → Widgets → Left", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        "Le widget Décompte se pose depuis l'onglet Décomptes.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        item {
            Appear(5) {
                GlassPill("Rafraîchir les widgets", Modifier.fillMaxWidth()) {
                    val n = WidgetUpdater.updateAll(ctx)
                    WidgetUpdater.scheduleNext(ctx)
                    Toast.makeText(ctx, if (n == 0) "Aucun widget posé" else "Widgets à jour", Toast.LENGTH_SHORT).show()
                }
            }
        }

        item {
            Text(
                "Left · noir & blanc",
                color = Color.White.copy(alpha = 0.25f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (picking) {
        LeftDatePicker(state.birth ?: LocalDate.of(2000, 1, 1), onDismiss = { picking = false }) {
            state.updateBirth(it)
        }
    }
}

/* ---------------------------------------------------------------- Choix du décompte (widget) */

@Composable
fun ConfigScreen(state: AppState, onPick: (Countdown) -> Unit) {
    var adding by remember { mutableStateOf(state.countdowns.isEmpty()) }
    val today = LocalDate.now()

    Box(Modifier.fillMaxSize()) {
        LiquidBackground()
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = screenPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header("Quel décompte ?", "Il s'affichera dans le widget") }
            items(state.countdowns.sortedBy { it.target }, key = { it.id }) { cd ->
                val p = TimeCalc.countdown(cd, today)
                GlassCard(Modifier.fillMaxWidth(), onClick = { onPick(cd) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text(p.subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                        }
                        AnimatedPercent(p.fraction, 0, 24.sp)
                    }
                }
            }
            item {
                GlassPill("+ Nouveau décompte", Modifier.fillMaxWidth(), filled = true) { adding = true }
            }
        }
    }

    if (adding) {
        AddCountdownDialog(
            onDismiss = { adding = false },
            onAdd = { title, date ->
                val cd = state.addCountdown(title, date)
                adding = false
                onPick(cd)
            },
        )
    }
}
